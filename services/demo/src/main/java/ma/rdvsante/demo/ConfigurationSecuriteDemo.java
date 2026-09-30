package ma.rdvsante.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité de la démonstration publique : tout est ouvert.
 *
 * <p>C'est un choix, pas un oubli. L'application est là pour être essayée par
 * quelqu'un qui découvre le projet ; lui demander de créer un compte pour
 * voir une file d'attente reviendrait à ne rien montrer.
 *
 * <p><strong>Ce que cela implique, et qu'il faut assumer :</strong> n'importe
 * qui peut réserver, annuler, faire avancer la file. Les cliniques, les
 * praticiens et les patients sont entièrement fictifs, et aucune donnée de
 * santé réelle n'est traitée — c'est la seule raison pour laquelle cette
 * ouverture est acceptable.
 *
 * <p>Le dépôt, lui, garde une configuration où chaque service exige un jeton
 * hors du profil de développement. Ce fichier ne remplace pas cette
 * configuration : il ne s'applique qu'au profil {@code demo}, et il est le
 * seul de son espèce.
 */
@Configuration
@EnableWebSecurity
class ConfigurationSecuriteDemo {

    @Bean
    SecurityFilterChain chaineDeFiltres(HttpSecurity http) throws Exception {
        return http
                // Aucune session, aucun formulaire : il n'y a rien à protéger
                // contre une soumission forgée quand tout est déjà public.
                // Laisser la protection active bloquerait en revanche les
                // requêtes du front, qui ne porte aucun jeton.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requetes -> requetes.anyRequest().permitAll())
                .build();
    }
}
