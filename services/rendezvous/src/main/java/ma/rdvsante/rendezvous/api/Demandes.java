package ma.rdvsante.rendezvous.api;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Ce que le client envoie. Validé avant d'atteindre le service métier. */
public final class Demandes {

    private Demandes() {
    }

    /**
     * @param patientTelephone au format marocain : 06/07 suivi de 8 chiffres,
     *                         ou +212 suivi de 9. Validé ici parce qu'un
     *                         numéro faux ne se découvre sinon qu'au moment du
     *                         SMS de rappel, quand le patient ne vient pas.
     */
    public record Reservation(
            @NotNull(message = "Le créneau est obligatoire.")
            UUID creneauId,

            @NotNull(message = "L'identifiant du patient est obligatoire.")
            UUID patientId,

            @NotBlank(message = "Le nom du patient est obligatoire.")
            @Size(max = 160)
            String patientNom,

            @NotBlank(message = "Le téléphone est obligatoire.")
            @Pattern(regexp = "^(0[67]\\d{8}|\\+212[67]\\d{8})$",
                     message = "Numéro attendu : 06XXXXXXXX, 07XXXXXXXX ou +2126XXXXXXXX.")
            String patientTelephone) {
    }

    public record Annulation(
            @NotBlank(message = "Le motif d'annulation est obligatoire.")
            @Size(max = 255)
            String motif) {
    }

    public record Arrivee(
            @NotNull(message = "Le rendez-vous est obligatoire.")
            UUID rendezVousId) {
    }
}
