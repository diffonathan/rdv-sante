package ma.rdvsante.rendezvous.evenement;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.rendezvous.depot.DepotEvenementSortant;
import ma.rdvsante.rendezvous.domaine.EvenementSortant;

/**
 * Vide l'outbox vers Kafka, périodiquement.
 *
 * <p>C'est la seconde moitié de la décision D2. Le service métier n'écrit que
 * dans PostgreSQL ; ce composant est le seul à parler au transport. Si celui-ci
 * est injoignable, les lignes restent en attente et repartent au tour suivant :
 * la panne retarde la notification, elle ne perd rien.
 *
 * <p>Il ne connaît plus Kafka directement, mais un {@link TransportEvenements}.
 * C'est ce qui permet à la démonstration en ligne de faire passer les mêmes
 * événements en mémoire, sur un hébergement où un courtier ne tiendrait pas —
 * sans qu'une seule ligne de la logique ci-dessous change.
 *
 * <p>Conséquence à connaître : un message peut partir <strong>deux fois</strong>
 * — envoyé à Kafka, puis le service tombe avant d'avoir noté la publication.
 * C'est pourquoi les consommateurs sont idempotents (décision D3), et pourquoi
 * l'identifiant de la ligne d'outbox voyage avec le message.
 */
@Component
public class PublieurOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublieurOutbox.class);

    private final DepotEvenementSortant depot;
    private final TransportEvenements transport;
    private final Clock horloge;
    private final int tailleLot;

    public PublieurOutbox(DepotEvenementSortant depot,
                          TransportEvenements transport,
                          Clock horloge,
                          @Value("${rdv.outbox.taille-lot:50}") int tailleLot) {
        this.depot = depot;
        this.transport = transport;
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
                transport.envoyer(evenement.getType(), evenement.getClePartition(),
                        evenement.getChargeUtile(), evenement.getId());
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

}
