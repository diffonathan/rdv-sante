package ma.rdvsante.patients.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité du profil {@code dev} : tout est ouvert.
 *
 * <p>Hors de ce profil la classe n'est pas chargée, et la configuration par
 * défaut de Spring Security reprend la main : une ouverture par oubli est
 * impossible.
 */
@Configuration
@Profile("dev")
public class ConfigurationSecuriteDev {

    @Bean
    SecurityFilterChain chaineDeFiltres(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requetes -> requetes.anyRequest().permitAll())
                .build();
    }
}
