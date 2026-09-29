package ma.rdvsante.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Sécurité du profil {@code dev} : tout passe.
 *
 * <p>La passerelle est <strong>réactive</strong> (WebFlux) : la chaîne de
 * filtres est un {@link SecurityWebFilterChain}, pas le {@code SecurityFilterChain}
 * servlet des autres services. Confondre les deux donne un contexte qui démarre
 * sans erreur et une sécurité qui ne s'applique jamais.
 *
 * <p>Annotée {@link Profile} : hors du profil {@code dev}, elle n'est pas
 * chargée et la configuration par défaut de Spring Security reprend la main.
 * Une ouverture par oubli est donc impossible.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("dev")
public class ConfigurationSecuriteDev {

    @Bean
    SecurityWebFilterChain chaineDeFiltres(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(echanges -> echanges.anyExchange().permitAll())
                .build();
    }
}
