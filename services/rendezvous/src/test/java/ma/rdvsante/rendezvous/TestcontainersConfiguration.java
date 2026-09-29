package ma.rdvsante.rendezvous;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Un vrai PostgreSQL et un vrai Kafka pour les tests d'intégration.
 *
 * <p>Pas de base H2 : elle accepte du SQL que PostgreSQL refuse, ignore les
 * index partiels et ne connaît pas les contraintes d'exclusion — c'est-à-dire
 * précisément les deux mécanismes sur lesquels repose ce service. Un test vert
 * sur H2 n'aurait rien prouvé.
 *
 * <p>Les versions sont <strong>figées</strong>, et identiques à
 * {@code deploy/compose/infra.yml}. Spring Initializr génère {@code :latest} :
 * un test qui change de socle tout seul un matin échoue pour une raison qui
 * n'est pas dans le dépôt.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        // Surtout pas « kafka » : ce nom est déjà celui de l'indicateur de
        // santé (SanteKafka), et c'est lui qui donne sa clé au composant dans
        // /actuator/health. Deux beans homonymes font échouer le contexte.
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        // Non générique dans le module org.testcontainers.postgresql :
        // l'ancien PostgreSQLContainer<SELF> vivait dans .containers.
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }
}
