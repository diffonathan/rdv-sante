package ma.rdvsante.patients.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.rdvsante.patients.domaine.Patient;
import ma.rdvsante.patients.service.ServicePatient;

@RestController
@RequestMapping("/api/patients")
class ControleurPatients {

    private final ServicePatient service;

    ControleurPatients(ServicePatient service) {
        this.service = service;
    }

    record Inscription(
            @NotBlank @Size(max = 80) String nom,
            @NotBlank @Size(max = 80) String prenom,
            @NotBlank
            @Pattern(regexp = "^(0[67]\\d{8}|\\+212[67]\\d{8})$",
                     message = "Numéro attendu : 06XXXXXXXX, 07XXXXXXXX ou +2126XXXXXXXX.")
            String telephone,
            @Email @Size(max = 160) String email,
            String canalPrefere,
            Boolean consentContact) {
    }

    record PatientVue(UUID id, String nom, String prenom, String nomComplet,
                      String telephone, String email, String canalPrefere,
                      boolean consentContact) {
        static PatientVue de(Patient p) {
            return new PatientVue(p.getId(), p.getNom(), p.getPrenom(), p.nomComplet(),
                    p.getTelephone(), p.getEmail(), p.getCanalPrefere().name(),
                    p.isConsentContact());
        }
    }

    /**
     * Enregistre un patient, ou actualise celui qui porte déjà ce numéro.
     *
     * <p>Volontairement <strong>idempotent</strong> : le front appelle ce point
     * d'entrée avant chaque réservation sans avoir à savoir si le patient
     * existe. Réserver trois fois depuis trois navigateurs ne crée pas trois
     * dossiers.
     */
    @PostMapping
    ResponseEntity<PatientVue> enregistrer(@Valid @RequestBody Inscription demande) {
        boolean nouveau = service.parTelephone(demande.telephone()).isEmpty();

        Patient patient = service.enregistrer(
                demande.nom(), demande.prenom(), demande.telephone(), demande.email(),
                canal(demande.canalPrefere()),
                demande.consentContact() == null || demande.consentContact());

        PatientVue vue = PatientVue.de(patient);
        // 201 quand le dossier vient d'être créé, 200 quand il existait :
        // le client sait ainsi s'il regarde un nouveau patient ou un ancien.
        return nouveau
                ? ResponseEntity.created(URI.create("/api/patients/" + vue.id())).body(vue)
                : ResponseEntity.ok(vue);
    }

    @GetMapping("/{id}")
    PatientVue consulter(@PathVariable UUID id) {
        return PatientVue.de(service.parId(id));
    }

    @GetMapping
    ResponseEntity<PatientVue> rechercher(@RequestParam String telephone) {
        return service.parTelephone(telephone)
                .map(p -> ResponseEntity.ok(PatientVue.de(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Patient.Canal canal(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return Patient.Canal.SMS;
        }
        try {
            return Patient.Canal.valueOf(valeur.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ServicePatient.ReglementNonRespecte(
                    "Canal inconnu : " + valeur + ". Valeurs acceptées : SMS, EMAIL.");
        }
    }
}
