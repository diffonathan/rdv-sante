package ma.rdvsante.rendezvous.config;

import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/**
 * Ajoute Kafka à {@code /actuator/health}.
 *
 * <p>Spring Boot fournit un indicateur pour la base de données, aucun pour
 * {@code spring-kafka} seul. Sans celui-ci, un service dont le courtier est
 * injoignable se déclare {@code UP} : il accepte les réservations, les écrit
 * en base, et l'outbox s'accumule sans que personne ne le voie. C'est
 * exactement la panne silencieuse que la sonde de disponibilité doit attraper.
 *
 * <p>Le nom du bean — {@code kafka} — devient la clé du composant dans la
 * réponse JSON.
 */
@Component("kafka")
// Pas de courtier en mode démonstration : les services y sont réunis dans un
// seul processus et s'échangent leurs événements en mémoire. Sonder un Kafka
// absent ferait clignoter l'état de santé en rouge sans qu'il y ait de panne.
@Profile("!demo")
class SanteKafka implements HealthIndicator {

    /** Au-delà, on considère le courtier injoignable plutôt que lent. */
    private static final int DELAI_MS = 3_000;

    private final AdminClient client;

    SanteKafka(AdminClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            DescribeClusterResult grappe =
                    client.describeCluster(new DescribeClusterOptions().timeoutMs(DELAI_MS));
            String identifiant = grappe.clusterId().get(DELAI_MS, TimeUnit.MILLISECONDS);
            int noeuds = grappe.nodes().get(DELAI_MS, TimeUnit.MILLISECONDS).size();

            return Health.up()
                    .withDetail("grappe", identifiant)
                    .withDetail("noeuds", noeuds)
                    .build();

        } catch (InterruptedException e) {
            // Ne jamais avaler une interruption : on repose le drapeau pour que
            // l'arrêt du service ne soit pas bloqué par ce fil.
            Thread.currentThread().interrupt();
            return Health.down().withDetail("erreur", "interrogation interrompue").build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("erreur", e.getClass().getSimpleName() + " : " + e.getMessage())
                    .build();
        }
    }
}
