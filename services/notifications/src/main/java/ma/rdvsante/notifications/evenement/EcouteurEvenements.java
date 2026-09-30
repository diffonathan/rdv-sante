package ma.rdvsante.notifications.evenement;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import ma.rdvsante.notifications.domaine.Notification;
import ma.rdvsante.notifications.service.ServiceNotification;
import tools.jackson.databind.ObjectMapper;

/**
 * Les quatre consommateurs du service.
 *
 * <p>Ils ne décident rien : ils traduisent un événement en message lisible et
 * délèguent l'écriture — et la garde contre les doublons — à
 * {@link ServiceNotification}.
 *
 * <h2>Pourquoi un point d'entrée séparé de l'annotation</h2>
 *
 * <p>Les méthodes annotées {@link KafkaListener} ne font qu'une chose :
 * extraire la clé d'idempotence, puis appeler {@link #traiter}. Toute la
 * traduction vit dans des méthodes qui ne connaissent ni Kafka ni ses
 * en-têtes.
 *
 * <p>C'est ce qui permet à la démonstration en ligne — un seul processus, sans
 * courtier — de livrer exactement les mêmes messages en appelant
 * {@link #traiter} directement. La garde contre les doublons, la traduction et
 * la règle du consentement sont les mêmes des deux côtés : il n'y a pas un
 * chemin « vrai » et un chemin « de démonstration » qui pourraient diverger.
 */
@Component
public class EcouteurEvenements {

    private static final Logger log = LoggerFactory.getLogger(EcouteurEvenements.class);

    /** Doit correspondre à l'en-tête posé par le transport. */
    private static final String EN_TETE_ID = "id-evenement";

    private static final ZoneId MAROC = ZoneId.of("Africa/Casablanca");
    private static final DateTimeFormatter QUAND =
            DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH'h'mm", Locale.FRENCH).withZone(MAROC);

    private final ServiceNotification service;
    private final ObjectMapper json;

    EcouteurEvenements(ServiceNotification service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    // ------------------------------------------------------------------
    // Entrée par Kafka
    // ------------------------------------------------------------------

    @KafkaListener(topics = EvenementsRecus.TOPIC_PATIENT)
    void surPatientEnregistre(@Payload String charge,
                              @Header(name = EN_TETE_ID, required = false) byte[] enTete) {
        traiter(EvenementsRecus.TOPIC_PATIENT, charge,
                cleDe(enTete, EvenementsRecus.TOPIC_PATIENT, charge));
    }

    @KafkaListener(topics = EvenementsRecus.TOPIC_RESERVE)
    void surReservation(@Payload String charge,
                        @Header(name = EN_TETE_ID, required = false) byte[] enTete) {
        traiter(EvenementsRecus.TOPIC_RESERVE, charge,
                cleDe(enTete, EvenementsRecus.TOPIC_RESERVE, charge));
    }

    @KafkaListener(topics = EvenementsRecus.TOPIC_ANNULE)
    void surAnnulation(@Payload String charge,
                       @Header(name = EN_TETE_ID, required = false) byte[] enTete) {
        traiter(EvenementsRecus.TOPIC_ANNULE, charge,
                cleDe(enTete, EvenementsRecus.TOPIC_ANNULE, charge));
    }

    @KafkaListener(topics = EvenementsRecus.TOPIC_FILE)
    void surFileAvancee(@Payload String charge,
                        @Header(name = EN_TETE_ID, required = false) byte[] enTete) {
        traiter(EvenementsRecus.TOPIC_FILE, charge,
                cleDe(enTete, EvenementsRecus.TOPIC_FILE, charge));
    }

    // ------------------------------------------------------------------
    // Le traitement, quel que soit le chemin emprunté
    // ------------------------------------------------------------------

    /**
     * Traduit un événement et le confie au service.
     *
     * <p>Un topic inconnu est ignoré, pas rejeté : un consommateur qui échoue
     * sur un message qu'il ne comprend pas bloque tout ce qui suit, et un
     * service qui publie un nouveau type d'événement ne devrait pas pouvoir
     * arrêter celui-ci.
     *
     * @param cle la clé d'idempotence — c'est elle qui empêche un message
     *            rejoué d'envoyer une seconde notification
     */
    public void traiter(String topic, String charge, UUID cle) {
        switch (topic) {
            case EvenementsRecus.TOPIC_PATIENT -> patientEnregistre(charge, cle);
            case EvenementsRecus.TOPIC_RESERVE -> reservation(charge, cle);
            case EvenementsRecus.TOPIC_ANNULE -> annulation(charge, cle);
            case EvenementsRecus.TOPIC_FILE -> fileAvancee(charge, cle);
            default -> log.warn("Topic inconnu, message ignoré : {}", topic);
        }
    }

    /**
     * Un patient vient de s'inscrire.
     *
     * <p>Deux refus explicites ici : on ne souhaite pas la bienvenue à chaque
     * mise à jour du dossier, et on n'écrit jamais à quelqu'un qui n'a pas
     * consenti — même si l'événement nous parvient.
     */
    private void patientEnregistre(String charge, UUID cle) {
        var e = json.readValue(charge, EvenementsRecus.PatientEnregistre.class);
        if (!e.creation() || !e.consentContact()) {
            return;
        }

        service.enregistrer(cle, EvenementsRecus.TOPIC_PATIENT, Notification.Type.BIENVENUE,
                e.telephone(), e.nomComplet(),
                "Bienvenue %s. Votre dossier RDV Santé est créé : vous recevrez ici vos confirmations et rappels."
                        .formatted(e.nomComplet()));
    }

    private void reservation(String charge, UUID cle) {
        var e = json.readValue(charge, EvenementsRecus.RendezVousReserve.class);

        service.enregistrer(cle, EvenementsRecus.TOPIC_RESERVE, Notification.Type.CONFIRMATION,
                e.patientTelephone(), e.patientNom(),
                "Votre rendez-vous avec %s est confirmé le %s, %s (%s)."
                        .formatted(e.praticienNom(), QUAND.format(e.debut()),
                                e.cliniqueNom(), e.cliniqueVille()));
    }

    private void annulation(String charge, UUID cle) {
        var e = json.readValue(charge, EvenementsRecus.RendezVousAnnule.class);

        service.enregistrer(cle, EvenementsRecus.TOPIC_ANNULE, Notification.Type.ANNULATION,
                e.patientTelephone(), e.patientNom(),
                "Votre rendez-vous du %s est annulé (%s). Vous pouvez en reprendre un en ligne."
                        .formatted(QUAND.format(e.debut()), e.motif()));
    }

    /**
     * Le patient vient d'être appelé.
     *
     * <p>Un vrai service préviendrait plutôt les <em>suivants</em> (« vous êtes
     * le prochain ») ; c'est à cela que sert le champ {@code restants}. Ici on
     * notifie l'appelé, ce qui se démontre plus simplement à l'écran.
     */
    private void fileAvancee(String charge, UUID cle) {
        var e = json.readValue(charge, EvenementsRecus.FileAvancee.class);

        service.enregistrer(cle, EvenementsRecus.TOPIC_FILE, Notification.Type.BIENTOT_VOTRE_TOUR,
                e.patientTelephone(), e.patientNom(),
                "%s : c'est à vous, présentez-vous à l'accueil. (%d personne(s) après vous)"
                        .formatted(e.cliniqueNom(), e.restants()));
    }

    /**
     * La clé d'idempotence du message.
     *
     * <p>Normalement l'en-tête posé par le transport. S'il manque — message
     * produit par un outil externe, ou par une version antérieure du service —
     * on dérive une clé <strong>déterministe</strong> du topic et de la charge
     * utile : deux copies du même message donnent la même clé, donc la
     * protection contre les doublons continue de fonctionner. Un UUID
     * aléatoire, lui, aurait silencieusement désactivé la garde.
     */
    private UUID cleDe(byte[] enTete, String topic, String charge) {
        if (enTete != null && enTete.length > 0) {
            try {
                return UUID.fromString(new String(enTete, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                log.warn("En-tête {} illisible, repli sur une clé dérivée.", EN_TETE_ID);
            }
        }
        return UUID.nameUUIDFromBytes((topic + '|' + charge).getBytes(StandardCharsets.UTF_8));
    }
}
