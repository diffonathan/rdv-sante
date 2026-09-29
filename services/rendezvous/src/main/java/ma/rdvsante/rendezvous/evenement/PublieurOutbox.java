package ma.rdvsante.rendezvous.evenement;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.rendezvous.depot.DepotEvenementSortant;
import ma.rdvsante.rendezvous.domaine.EvenementSortant;

/**
 * Vide l'outbox vers Kafka, périodiquement.
 *
 * <p>C'est la seconde moitié de la décision D2. Le service métier n'écrit que
 * dans PostgreSQL ; ce composant est le seul à parler à Kafka. Si le courtier
 * est injoignable, les lignes restent en attente et repartent au tour suivant :
 * la panne retarde la notification, elle ne perd rien.
 *
 * <p>Conséquence à connaître : un message peut partir <strong>deux fois</strong>
 * — envoyé à Kafka, puis le service tombe avant d'avoir noté la publication.
 * C'est pourquoi les consommateurs sont idempotents (décision D3), et pourquoi
 * l'identifiant de la ligne d'outbox voyage avec le message.
 */
@Component
public class PublieurOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublieurOutbox.class);

    /** Court volontairement : l'envoi se fait en tenant des verrous de ligne. */
    private static final long DELAI_ENVOI_S = 3;

    /** En-tête portant l'identifiant de la ligne d'outbox. */
    public static final String EN_TETE_ID = "id-evenement";

    private final DepotEvenementSortant depot;
    private final KafkaTemplate<String, String> kafka;
    private final Clock horloge;
    private final int tailleLot;

    public PublieurOutbox(DepotEvenementSortant depot,
                          KafkaTemplate<String, String> kafka,
                          Clock horloge,
                          @Value("${rdv.outbox.taille-lot:50}") int tailleLot) {
        this.depot = depot;
        this.kafka = kafka;
        this.horloge = horloge;
        this.tailleLot = tailleLot;
    }

    @Scheduled(fixedDelayString = "${rdv.outbox.periode-ms:1000}")
    @Transactional
    public void publierLeLotSuivant() {
        List<EvenementSortant> lot = depot.lotAPublier(tailleLot);
        if (lot.isEmpty()) {
            return;
        }

        int partis = 0;
        for (EvenementSortant evenement : lot) {
            try {
                kafka.send(enregistrer(evenement)).get(DELAI_ENVOI_S, TimeUnit.SECONDS);
                evenement.marquerPublie(horloge.instant());
                partis++;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                evenement.marquerEchec("envoi interrompu");
                break;

            } catch (Exception e) {
                // On n'interrompt pas le lot : un topic mal configuré ne doit
                // pas bloquer les événements qui suivent. La ligne reste en
                // attente, son compteur de tentatives monte, et elle repart au
                // tour d'après.
                evenement.marquerEchec(e.getClass().getSimpleName() + " : " + e.getMessage());
                log.warn("Événement {} ({}) non publié, tentative {} : {}",
                        evenement.getId(), evenement.getType(),
                        evenement.getTentatives(), e.getMessage());
            }
        }

        depot.saveAll(lot);
        if (partis > 0) {
            log.debug("{} événement(s) publié(s) sur {}", partis, lot.size());
        }
    }

    /**
     * Construit le message Kafka, avec l'identifiant de la ligne d'outbox en
     * en-tête.
     *
     * <p>Cet en-tête est la <strong>clé de déduplication</strong> du
     * consommateur : puisqu'un message peut partir deux fois, c'est lui qui
     * permet de reconnaître le doublon. Le placer en en-tête plutôt que dans
     * la charge utile évite de mêler une préoccupation de transport au contrat
     * métier — un consommateur qui ne déduplique pas n'a pas à le lire.
     */
    private ProducerRecord<String, String> enregistrer(EvenementSortant evenement) {
        ProducerRecord<String, String> message = new ProducerRecord<>(
                evenement.getType(), evenement.getClePartition(), evenement.getChargeUtile());
        message.headers().add(EN_TETE_ID,
                evenement.getId().toString().getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
