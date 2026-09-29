package ma.rdvsante.rendezvous.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.rendezvous.depot.DepotCreneau;
import ma.rdvsante.rendezvous.depot.DepotRendezVous;
import ma.rdvsante.rendezvous.domaine.Creneau;
import ma.rdvsante.rendezvous.domaine.Praticien;
import ma.rdvsante.rendezvous.domaine.RendezVous;
import ma.rdvsante.rendezvous.domaine.StatutRendezVous;
import ma.rdvsante.rendezvous.evenement.Evenements;

/**
 * La règle de réservation.
 *
 * <p>C'est le cœur du service : quinze lignes utiles, et deux pièges que la
 * plupart des implémentations manquent — la course entre deux réservations
 * simultanées, et l'écart possible entre la base et Kafka.
 */
@Service
public class ServiceReservation {

    private final DepotCreneau depotCreneau;
    private final DepotRendezVous depotRendezVous;
    private final ServiceOutbox outbox;
    private final Clock horloge;

    public ServiceReservation(DepotCreneau depotCreneau, DepotRendezVous depotRendezVous,
                              ServiceOutbox outbox, Clock horloge) {
        this.depotCreneau = depotCreneau;
        this.depotRendezVous = depotRendezVous;
        this.outbox = outbox;
        this.horloge = horloge;
    }

    /**
     * Réserve un créneau pour un patient.
     *
     * @throws ErreursMetier.Introuvable         le créneau n'existe pas
     * @throws ErreursMetier.ReglementNonRespecte le créneau est déjà commencé
     * @throws ErreursMetier.CreneauDejaPris     quelqu'un a réservé entre-temps
     */
    @Transactional
    public RendezVous reserver(UUID creneauId, UUID patientId, String nom, String telephone) {
        Creneau creneau = depotCreneau.findById(creneauId)
                .orElseThrow(() -> new ErreursMetier.Introuvable("Créneau", creneauId));

        Instant maintenant = horloge.instant();
        if (creneau.estPasse(maintenant)) {
            throw new ErreursMetier.ReglementNonRespecte(
                    "Ce créneau a commencé, il ne peut plus être réservé.");
        }

        RendezVous rdv = new RendezVous(UUID.randomUUID(), creneau, patientId, nom, telephone);

        try {
            // saveAndFlush, et non save : sans le flush, l'INSERT part au
            // commit de la transaction, c'est-à-dire APRÈS la sortie de ce
            // bloc try. La violation d'index remonterait alors en
            // DataIntegrityViolationException non rattrapée, donc en 500,
            // au lieu du 409 qui décrit vraiment la situation.
            depotRendezVous.saveAndFlush(rdv);

        } catch (DataIntegrityViolationException e) {
            // L'index unique partiel a parlé : un autre rendez-vous non annulé
            // occupe déjà ce créneau (décision D4). Aucune vérification
            // préalable n'aurait pu l'éviter — entre le SELECT et l'INSERT, la
            // fenêtre existe toujours.
            throw new ErreursMetier.CreneauDejaPris(creneauId);
        }

        Praticien praticien = creneau.getPraticien();
        outbox.deposer(Evenements.TOPIC_RESERVE, "RendezVous", rdv.getId(),
                new Evenements.RendezVousReserve(
                        rdv.getId(), patientId, nom, telephone,
                        creneau.getId(), creneau.getDebut(), creneau.getFin(),
                        praticien.getId(), praticien.nomComplet(), praticien.getSpecialite(),
                        praticien.getClinique().getId(), praticien.getClinique().getNom(),
                        praticien.getClinique().getVille()));

        return rdv;
    }

    /** Annule un rendez-vous ; le créneau redevient réservable. */
    @Transactional
    public RendezVous annuler(UUID rendezVousId, String motif) {
        RendezVous rdv = depotRendezVous.parIdComplet(rendezVousId)
                .orElseThrow(() -> new ErreursMetier.Introuvable("Rendez-vous", rendezVousId));

        if (rdv.getStatut() == StatutRendezVous.ANNULE) {
            throw new ErreursMetier.ReglementNonRespecte("Ce rendez-vous est déjà annulé.");
        }

        Instant maintenant = horloge.instant();
        rdv.annuler(motif, maintenant);
        depotRendezVous.save(rdv);

        outbox.deposer(Evenements.TOPIC_ANNULE, "RendezVous", rdv.getId(),
                new Evenements.RendezVousAnnule(
                        rdv.getId(), rdv.getPatientId(), rdv.getPatientNom(),
                        rdv.getPatientTelephone(), rdv.getCreneau().getDebut(),
                        motif, maintenant));

        return rdv;
    }

    @Transactional(readOnly = true)
    public RendezVous parId(UUID id) {
        return depotRendezVous.parIdComplet(id)
                .orElseThrow(() -> new ErreursMetier.Introuvable("Rendez-vous", id));
    }
}
