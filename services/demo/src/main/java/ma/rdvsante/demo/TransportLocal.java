package ma.rdvsante.demo;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Le transport de la démonstration : les événements passent en mémoire.
 *
 * <p>Une seule classe implémente les deux interfaces, parce que « rendezvous »
 * et « patients » déclarent chacun la sienne. Ce n'est pas une duplication à
 * corriger : deux services indépendants ne partagent pas leurs contrats, et
 * c'est précisément ce qui leur permet d'évoluer séparément. Le module de
 * démonstration, lui, connaît les deux — il est le seul à avoir le droit.
 *
 * <h2>Ce qui reste vrai malgré le raccourci</h2>
 *
 * <p>L'événement est toujours passé par l'outbox : écrit dans PostgreSQL dans
 * la même transaction que le rendez-vous, puis relevé par lots, et marqué
 * publié seulement après que cette méthode a rendu la main. L'identifiant de la
 * ligne d'outbox voyage toujours avec lui, et c'est encore lui qui sert de clé
 * de déduplication au consommateur.
 *
 * <p>Ce qui disparaît : la durabilité du message une fois sorti de l'outbox.
 * Kafka le conserverait ; ici, si le processus tombe entre la publication et le
 * traitement, le message est perdu. Sur une démonstration à un seul processus,
 * une telle panne emporte de toute façon l'application entière.
 */
@Component
class TransportLocal
        implements ma.rdvsante.rendezvous.evenement.TransportEvenements,
                   ma.rdvsante.patients.evenement.TransportEvenements {

    private static final Logger log = LoggerFactory.getLogger(TransportLocal.class);

    private final ApplicationEventPublisher publieur;

    TransportLocal(ApplicationEventPublisher publieur) {
        this.publieur = publieur;
    }

    @Override
    public void envoyer(String topic, String cle, String charge, UUID idEvenement) {
        log.debug("Événement {} sur « {} » remis en mémoire", idEvenement, topic);

        // Publication SYNCHRONE, volontairement : le publieur d'outbox ne
        // marque la ligne comme publiée qu'après le retour de cet appel. Un
        // envoi asynchrone ferait marquer « publié » un message encore en vol,
        // et le perdrait sans laisser de trace si le traitement échouait
        // ensuite. C'est exactement la garantie que l'attente sur Kafka donne.
        publieur.publishEvent(new MessageLocal(topic, cle, charge, idEvenement));
    }

    /** Un message en transit. Les mêmes champs que celui de Kafka, pour que le
     *  consommateur n'ait pas à savoir par où il est arrivé. */
    record MessageLocal(String topic, String cle, String charge, UUID idEvenement) {}
}
