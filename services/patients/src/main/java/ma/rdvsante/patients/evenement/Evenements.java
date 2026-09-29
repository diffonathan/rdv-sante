package ma.rdvsante.patients.evenement;

import java.util.UUID;

/**
 * Le contrat publié par ce service.
 *
 * <p>Il porte tout ce qu'un consommateur doit savoir pour agir sans rappeler
 * « patients » : le nom, le canal choisi, et surtout le consentement. Le
 * service de notification n'a pas à deviner s'il a le droit d'écrire au
 * patient — l'événement le lui dit.
 */
public final class Evenements {

    private Evenements() {
    }

    public static final String TOPIC_ENREGISTRE = "patient.enregistre";

    public record PatientEnregistre(
            UUID patientId,
            String nomComplet,
            String telephone,
            String email,
            String canalPrefere,
            boolean consentContact,
            boolean creation) {
    }
}
