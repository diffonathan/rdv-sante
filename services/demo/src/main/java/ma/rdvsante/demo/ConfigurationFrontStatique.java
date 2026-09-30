package ma.rdvsante.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sert le front Angular depuis la même origine que l'API.
 *
 * <p>Pas de passerelle dans la démonstration : il n'y a qu'un processus, donc
 * plus rien à router. Le front est empaqueté dans {@code static/} et servi par
 * le même serveur que {@code /api}, ce qui règle aussi la question des
 * origines croisées — il n'y en a plus qu'une.
 *
 * <h2>Le repli sur index.html</h2>
 *
 * <p>Angular gère la navigation dans le navigateur : {@code /secretariat} et
 * {@code /salle-attente} n'existent pas côté serveur. Tant qu'on navigue par
 * les liens, tout va bien — le navigateur ne demande rien au serveur.
 *
 * <p>Mais un visiteur qui ouvre un lien direct, recharge la page (F5) ou
 * revient par un favori demande {@code /secretariat} au serveur, qui répond
 * 404. On renvoie donc {@code index.html} : Angular lit alors l'adresse et
 * affiche le bon écran.
 *
 * <p>Les chemins sont déclarés un par un plutôt que par un motif attrape-tout.
 * Un {@code /**} renverrait aussi {@code index.html} pour une ressource
 * réellement absente — une image mal nommée arriverait sous forme de page
 * HTML, et l'erreur serait beaucoup plus difficile à comprendre qu'un 404.
 */
@Configuration
class ConfigurationFrontStatique implements WebMvcConfigurer {

    /** Les écrans d'Angular. À tenir aligné sur {@code app.routes.ts}. */
    private static final String[] ECRANS = {
            "/secretariat", "/salle-attente", "/technique", "/documentation",
    };

    @Override
    public void addViewControllers(ViewControllerRegistry registre) {
        for (String ecran : ECRANS) {
            registre.addViewController(ecran).setViewName("forward:/index.html");
        }
    }
}
