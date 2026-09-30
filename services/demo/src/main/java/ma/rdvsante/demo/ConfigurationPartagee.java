package ma.rdvsante.demo;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ce que les trois services réclament chacun de leur côté, fourni une fois.
 *
 * <p>Chaque service déclare une horloge — {@code Clock.systemUTC()}, la même
 * dans les trois — pour que ses tests puissent décider de l'heure qu'il est
 * plutôt que de courir après la vraie. Réunis dans un processus, ils
 * déclareraient trois beans du même nom, et le contexte refuse de démarrer.
 *
 * <p>Leurs classes de configuration sont donc écartées du balayage, et
 * l'horloge est posée ici. Ce n'est pas une perte : dans un seul processus, il
 * n'y a de toute façon qu'une horloge. Et les services, eux, gardent la leur
 * lorsqu'ils tournent séparément — c'est le module de démonstration qui
 * s'adapte, jamais l'inverse.
 */
@Configuration
class ConfigurationPartagee {

    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }
}
