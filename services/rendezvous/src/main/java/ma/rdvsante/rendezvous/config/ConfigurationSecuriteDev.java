package ma.rdvsante.rendezvous.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité du profil {@code dev} : tout est ouvert.
 *
 * <p>Travailler sur la règle de réservation ne doit pas obliger à lancer
 * Keycloak, à obtenir un jeton et à le coller dans chaque appel. Le profil
 * complet, lui, valide de vrais jetons OIDC — voir
 * {@code docs/architecture.md}, décision D8.
 *
 * <p>Cette classe est volontairement annotée {@link Profile} : sans le profil
 * {@code dev}, elle n'est pas chargée et la configuration par défaut de Spring
 * Security reprend la main. Une ouverture par oubli est donc impossible.
 */
@Configuration
@Profile("dev")
public class ConfigurationSecuriteDev {

    @Bean
    SecurityFilterChain chaineDeFiltres(HttpSecurity http) throws Exception {
        return http
                // Pas de session, pas de formulaire : l'API est sans état.
                // Le jeton CSRF n'aurait rien à protéger.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requetes -> requetes.anyRequest().permitAll())
                .build();
    }
}
