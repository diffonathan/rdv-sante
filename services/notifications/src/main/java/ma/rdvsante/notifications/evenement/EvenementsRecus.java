package ma.rdvsante.notifications.evenement;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Les contrats d'événements <strong>consommés</strong> par ce service.
 *
 * <p>Ils sont volontairement <em>recopiés</em> depuis le service émetteur au
 * lieu d'être partagés dans une bibliothèque commune. Un module partagé
 * recréerait le couplage que les événements servent à défaire : le jour où
 * « rendezvous » ajoute un champ, tous les consommateurs devraient recompiler
 * et redéployer avant lui.
 *
 * <p>{@link JsonIgnoreProperties} est la contrepartie indispensable de ce
 * choix : un champ ajouté par l'émetteur et inconnu ici est ignoré, au lieu de
 * faire échouer la désérialisation et de bloquer la consommation.
 *
 * <p>Ce service ne déclare que les champs dont il a besoin — le téléphone, le
 * nom, l'horaire. Le reste de la charge utile ne l'intéresse pas.
 */
public final class EvenementsRecus {

    private EvenementsRecus() {
    }

    public static final String TOPIC_PATIENT = "patient.enregistre";
    public static final String TOPIC_RESERVE = "rendezvous.reserve";
    public static final String TOPIC_ANNULE = "rendezvous.annule";
    public static final String TOPIC_FILE = "file.avancee";

    /**
     * @param creation vrai à la première inscription, faux lors d'une simple
     *                 mise à jour du dossier. Sans ce drapeau, un patient
     *                 recevrait un message de bienvenue à chaque réservation.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PatientEnregistre(
            UUID patientId,
            String nomComplet,
            String telephone,
            boolean consentContact,
            boolean creation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RendezVousReserve(
            UUID rendezVousId,
            String patientNom,
            String patientTelephone,
            Instant debut,
            String praticienNom,
            String cliniqueNom,
            String cliniqueVille) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RendezVousAnnule(
            UUID rendezVousId,
            String patientNom,
            String patientTelephone,
            Instant debut,
            String motif) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileAvancee(
            UUID rendezVousId,
            String patientNom,
            String patientTelephone,
            String cliniqueNom,
            int restants) {
    }
}
