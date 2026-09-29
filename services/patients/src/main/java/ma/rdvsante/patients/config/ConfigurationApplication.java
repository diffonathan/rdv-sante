package ma.rdvsante.patients.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class ConfigurationApplication {

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }
}
