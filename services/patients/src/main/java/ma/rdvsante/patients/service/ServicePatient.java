package ma.rdvsante.patients.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.patients.depot.DepotEvenementSortantPatient;
import ma.rdvsante.patients.depot.DepotPatient;
import ma.rdvsante.patients.domaine.EvenementSortantPatient;
import ma.rdvsante.patients.domaine.Patient;
import ma.rdvsante.patients.evenement.Evenements;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Enregistre un patient, ou met à jour celui qui existe déjà.
 *
 * <p>Le point important est l'<strong>idempotence de la création</strong> :
 * réserver un rendez-vous trois fois depuis trois navigateurs ne doit pas
 * créer trois patients. Le téléphone tranche, et c'est un index unique en base
 * qui le garantit — pas une vérification applicative, qui laisserait une
 * fenêtre entre le SELECT et l'INSERT.
 */
@Service
public class ServicePatient {

    /** Erreur métier : la demande est comprise mais refusée. → 422 */
    public static class ReglementNonRespecte extends RuntimeException {
        public ReglementNonRespecte(String message) {
            super(message);
        }
    }

    /** → 404 */
    public static class Introuvable extends RuntimeException {
        public Introuvable(UUID id) {
            super("Patient " + id + " : introuvable.");
        }
    }

    private final DepotPatient depot;
    private final DepotEvenementSortantPatient outbox;
    private final ObjectMapper json;
    private final Clock horloge;

    public ServicePatient(DepotPatient depot, DepotEvenementSortantPatient outbox,
                          ObjectMapper json, Clock horloge) {
        this.depot = depot;
        this.outbox = outbox;
        this.json = json;
        this.horloge = horloge;
    }

    /**
     * Enregistre le patient, ou actualise ses informations s'il est déjà connu.
     *
     * @return le patient, créé ou retrouvé
     */
    @Transactional
    public Patient enregistrer(String nom, String prenom, String telephone, String email,
                               Patient.Canal canal, boolean consentement) {

        if (!consentement) {
            // On accepte de créer le dossier, mais on refuse de faire semblant :
            // sans consentement, aucun rappel ne pourra partir, et le patient
            // manquera son rendez-vous en croyant être prévenu.
            throw new ReglementNonRespecte(
                    "Le consentement au contact est nécessaire pour recevoir les rappels.");
        }

        Instant maintenant = horloge.instant();
        var existant = depot.findByTelephone(telephone);

        Patient patient;
        boolean creation;
        if (existant.isPresent()) {
            patient = existant.get();
            patient.actualiser(nom, prenom, email, canal, consentement, maintenant);
            creation = false;
        } else {
            patient = new Patient(UUID.randomUUID(), nom, prenom, telephone, email,
                    canal, consentement);
            creation = true;
        }
        depot.save(patient);

        deposer(patient, creation);
        return patient;
    }

    @Transactional(readOnly = true)
    public Patient parId(UUID id) {
        return depot.findById(id).orElseThrow(() -> new Introuvable(id));
    }

    @Transactional(readOnly = true)
    public Optional<Patient> parTelephone(String telephone) {
        return depot.findByTelephone(telephone);
    }

    /** Écrit l'événement dans la même transaction que le patient (décision D2). */
    @Transactional(propagation = Propagation.MANDATORY)
    void deposer(Patient patient, boolean creation) {
        var charge = new Evenements.PatientEnregistre(
                patient.getId(), patient.nomComplet(), patient.getTelephone(),
                patient.getEmail(), patient.getCanalPrefere().name(),
                patient.isConsentContact(), creation);

        String corps;
        try {
            corps = json.writeValueAsString(charge);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Événement patient.enregistre non sérialisable : " + e.getMessage(), e);
        }

        outbox.save(new EvenementSortantPatient(UUID.randomUUID(), "Patient", patient.getId(),
                Evenements.TOPIC_ENREGISTRE, corps));
    }
}
