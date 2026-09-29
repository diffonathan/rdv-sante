package ma.rdvsante.patients.evenement;

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

import ma.rdvsante.patients.depot.DepotEvenementSortant;
import ma.rdvsante.patients.domaine.EvenementSortant;

/**
 * Vide l'outbox vers Kafka. Même mécanique que dans « rendezvous », y compris
 * l'en-tête {@code id-evenement} qui sert de clé de déduplication au
 * consommateur (décision D3).
 */
@Component
public class PublieurOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublieurOutbox.class);
    private static final long DELAI_ENVOI_S = 3;

    public static final String EN_TETE_ID = "id-evenement";

    private final DepotEvenementSortant depot;
    private final KafkaTemplate<String, String> kafka;
    private final Clock horloge;
    private final int tailleLot;

    public PublieurOutbox(DepotEvenementSortant depot, KafkaTemplate<String, String> kafka,
                          Clock horloge, @Value("${rdv.outbox.taille-lot:50}") int tailleLot) {
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

        for (EvenementSortant evenement : lot) {
            try {
                ProducerRecord<String, String> message = new ProducerRecord<>(
                        evenement.getType(), evenement.getClePartition(),
                        evenement.getChargeUtile());
                message.headers().add(EN_TETE_ID,
                        evenement.getId().toString().getBytes(StandardCharsets.UTF_8));

                kafka.send(message).get(DELAI_ENVOI_S, TimeUnit.SECONDS);
                evenement.marquerPublie(horloge.instant());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                evenement.marquerEchec("envoi interrompu");
                break;

            } catch (Exception e) {
                evenement.marquerEchec(e.getClass().getSimpleName() + " : " + e.getMessage());
                log.warn("Événement {} non publié, tentative {} : {}",
                        evenement.getId(), evenement.getTentatives(), e.getMessage());
            }
        }
        depot.saveAll(lot);
    }
}
