package ma.rdvsante.rendezvous.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class ConfigurationApplication {

    /**
     * L'heure passe par un bean, jamais par {@code Instant.now()} au fil du code.
     *
     * <p>Un test qui vérifie qu'un créneau passé n'est plus réservable a besoin
     * de décider de l'heure qu'il est. Avec un appel statique, il faudrait
     * créer des créneaux dans un futur proche et espérer que la machine
     * d'intégration soit assez rapide — un test qui échoue une fois sur vingt.
     * Avec une horloge injectée, {@code Clock.fixed(...)} règle la question.
     */
    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }
}
