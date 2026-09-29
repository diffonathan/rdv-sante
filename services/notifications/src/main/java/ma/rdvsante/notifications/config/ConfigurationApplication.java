package ma.rdvsante.notifications.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ConfigurationApplication {

    /** Injectée plutôt qu'appelée en statique : un test doit pouvoir décider
        de l'heure qu'il est. */
    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }
}
