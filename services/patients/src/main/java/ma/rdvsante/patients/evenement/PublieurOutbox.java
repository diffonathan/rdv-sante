package ma.rdvsante.patients.evenement;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ma.rdvsante.patients.depot.DepotEvenementSortantPatient;
import ma.rdvsante.patients.domaine.EvenementSortantPatient;

/**
 * Vide l'outbox vers le transport. Même mécanique que dans « rendezvous », y compris
 * l'en-tête {@code id-evenement} qui sert de clé de déduplication au
 * consommateur (décision D3).
 *
 * <p>Il ne connaît pas Kafka, mais un {@link TransportEvenements} : c'est ce
 * qui permet à la démonstration en ligne de faire passer les mêmes événements
 * en mémoire, sans qu'une ligne de la logique ci-dessous change.
 */
@Component
public class PublieurOutbox {

    private static final Logger log = LoggerFactory.getLogger(PublieurOutbox.class);
    private final DepotEvenementSortantPatient depot;
    private final TransportEvenements transport;
    private final Clock horloge;
    private final int tailleLot;

    public PublieurOutbox(DepotEvenementSortantPatient depot, TransportEvenements transport,
                          Clock horloge, @Value("${rdv.outbox.taille-lot:50}") int tailleLot) {
        this.depot = depot;
        this.transport = transport;
        this.horloge = horloge;
        this.tailleLot = tailleLot;
    }

    @Scheduled(fixedDelayString = "${rdv.outbox.periode-ms:1000}")
    @Transactional
    public void publierLeLotSuivant() {
        List<EvenementSortantPatient> lot = depot.lotAPublier(tailleLot);
        if (lot.isEmpty()) {
            return;
        }

        for (EvenementSortantPatient evenement : lot) {
            try {
                transport.envoyer(evenement.getType(), evenement.getClePartition(),
                        evenement.getChargeUtile(), evenement.getId());
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
