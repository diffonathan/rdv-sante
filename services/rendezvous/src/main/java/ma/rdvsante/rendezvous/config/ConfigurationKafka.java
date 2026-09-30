package ma.rdvsante.rendezvous.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.context.annotation.Profile;

@Configuration
// Pas de courtier en mode démonstration : les services y sont réunis
// dans un seul processus et s'échangent leurs événements en mémoire.
// Sans ceci, ce bean réclamerait un KafkaAdmin qui n'existe pas, et le
// contexte refuserait de démarrer.
@Profile("!demo")
class ConfigurationKafka {

    /**
     * Un seul {@link AdminClient}, partagé.
     *
     * <p>En créer un à chaque appel de la sonde de santé ouvrirait une
     * connexion au courtier toutes les quelques secondes — un client
     * d'administration est coûteux à construire, pas à réutiliser.
     *
     * <p>La configuration vient de {@link KafkaAdmin}, donc de
     * {@code spring.kafka.*} : l'adresse du courtier n'est écrite qu'une fois.
     */
    @Bean(destroyMethod = "close")
    AdminClient adminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }
}
