package ma.rdvsante.rendezvous.evenement;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Le transport réel : Kafka.
 *
 * <p>C'est l'implémentation utilisée partout sauf dans la démonstration en
 * ligne — en développement, en test, et dans n'importe quel déploiement qui
 * dispose d'un courtier.
 *
 * <p>{@code @Profile("!demo")} plutôt que {@code @Profile("kafka")} :
 * l'implémentation Kafka est le cas NORMAL, et un profil oublié doit rendre le
 * comportement normal, pas le comportement dégradé. L'inverse ferait tourner
 * une production entière sur un transport en mémoire sans que personne s'en
 * aperçoive.
 */
@Component
@Profile("!demo")
class TransportKafka implements TransportEvenements {

    /** Court volontairement : l'envoi se fait en tenant des verrous de ligne. */
    private static final long DELAI_ENVOI_S = 3;

    /** En-tête portant l'identifiant de la ligne d'outbox. */
    static final String EN_TETE_ID = "id-evenement";

    private final KafkaTemplate<String, String> kafka;

    TransportKafka(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void envoyer(String topic, String cle, String charge, UUID idEvenement) throws Exception {
        ProducerRecord<String, String> message = new ProducerRecord<>(topic, cle, charge);

        // En en-tête et non dans la charge utile : c'est une préoccupation de
        // transport, pas un élément du contrat métier. Un consommateur qui ne
        // déduplique pas n'a pas à le lire.
        message.headers().add(EN_TETE_ID,
                idEvenement.toString().getBytes(StandardCharsets.UTF_8));

        kafka.send(message).get(DELAI_ENVOI_S, TimeUnit.SECONDS);
    }
}
