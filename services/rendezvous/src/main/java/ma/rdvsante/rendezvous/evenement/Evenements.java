package ma.rdvsante.rendezvous.evenement;

import java.time.Instant;
import java.util.UUID;

/**
 * Les contrats d'événements publiés par ce service.
 *
 * <p>Ce sont des {@code record} : immuables, sérialisés tels quels en JSON. Ils
 * constituent l'interface publique du service au même titre que son API REST —
 * **on n'y retire jamais un champ**, on en ajoute (un consommateur qui ne
 * connaît pas un champ nouveau l'ignore ; un consommateur privé d'un champ
 * qu'il attend casse).
 *
 * <p>Chaque événement porte de quoi être traité SANS rappeler ce service : le
 * nom du patient, celui du praticien, l'horaire. C'est le principe de
 * l'intégration par événements — sinon le consommateur ferait un appel HTTP en
 * retour, et on aurait reconstruit le couplage qu'on voulait éviter.
 */
public final class Evenements {

    private Evenements() {
    }

    /** Nom du topic Kafka correspondant. */
    public static final String TOPIC_RESERVE = "rendezvous.reserve";
    public static final String TOPIC_ANNULE = "rendezvous.annule";
    public static final String TOPIC_FILE = "file.avancee";

    public record RendezVousReserve(
            UUID rendezVousId,
            UUID patientId,
            String patientNom,
            String patientTelephone,
            UUID creneauId,
            Instant debut,
            Instant fin,
            UUID praticienId,
            String praticienNom,
            String specialite,
            UUID cliniqueId,
            String cliniqueNom,
            String cliniqueVille) {
    }

    public record RendezVousAnnule(
            UUID rendezVousId,
            UUID patientId,
            String patientNom,
            String patientTelephone,
            Instant debut,
            String motif,
            Instant annuleLe) {
    }

    /**
     * Un patient vient d'être appelé ; les suivants avancent d'un rang.
     *
     * @param restants nombre de patients encore en attente après cet appel —
     *                 c'est ce qui permet au service de notifications de
     *                 prévenir « vous êtes le prochain ».
     */
    public record FileAvancee(
            UUID cliniqueId,
            String cliniqueNom,
            UUID rendezVousId,
            UUID patientId,
            String patientNom,
            String patientTelephone,
            Instant appeleLe,
            int restants) {
    }
}
