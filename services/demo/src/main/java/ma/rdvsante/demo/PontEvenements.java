package ma.rdvsante.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ma.rdvsante.notifications.evenement.EcouteurEvenements;

/**
 * Remet à « notifications » les messages que les autres services publient.
 *
 * <p>C'est le pendant de {@link TransportLocal} : l'un dépose, l'autre livre.
 * Ensemble, ils remplacent le courtier — et rien d'autre.
 *
 * <p>Il appelle {@link EcouteurEvenements#traiter} : exactement le point
 * d'entrée qu'empruntent les consommateurs Kafka une fois qu'ils ont extrait
 * la clé d'idempotence de l'en-tête. La traduction du message, la règle du
 * consentement et la garde contre les doublons sont donc les mêmes des deux
 * côtés. Il n'y a pas un chemin « vrai » et un chemin « de démonstration » qui
 * pourraient diverger — c'est la seule façon de faire une démonstration qui ne
 * ment pas.
 *
 * <p>Une erreur de traitement est journalisée sans être relancée. Dans un
 * système à courtier, un consommateur qui échoue laisse le message et
 * recommencera ; ici, relancer l'exception remonterait jusqu'au publieur
 * d'outbox, qui compterait une tentative et réessaierait au tour suivant —
 * en boucle sur un message que le consommateur ne saura jamais traiter. Mieux
 * vaut le laisser passer et le voir dans les journaux.
 */
@Component
class PontEvenements {

    private static final Logger log = LoggerFactory.getLogger(PontEvenements.class);

    private final EcouteurEvenements consommateur;

    PontEvenements(EcouteurEvenements consommateur) {
        this.consommateur = consommateur;
    }

    @EventListener
    void livrer(TransportLocal.MessageLocal message) {
        try {
            consommateur.traiter(message.topic(), message.charge(), message.idEvenement());

        } catch (RuntimeException e) {
            log.error("Événement {} sur « {} » non traité : {}",
                    message.idEvenement(), message.topic(), e.getMessage(), e);
        }
    }
}
