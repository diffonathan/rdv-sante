package ma.rdvsante.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Spring Boot 4 a déplacé EntityScan hors de `autoconfigure.domain` :
// il vit désormais dans le module `spring-boot-persistence`.
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

import ma.rdvsante.notifications.NotificationsApplication;
import ma.rdvsante.patients.PatientsApplication;
import ma.rdvsante.rendezvous.RendezvousApplication;

/**
 * Les trois services, dans un seul processus.
 *
 * <p>Le balayage part de {@code ma.rdvsante} pour ramasser les contrôleurs,
 * les services et les dépôts des trois modules — mais il EXCLUT leurs classes
 * d'application. C'est le point à ne pas manquer : une classe annotée
 * {@code @SpringBootApplication} est aussi une {@code @Configuration}, et la
 * ramasser déclencherait une seconde fois toute l'auto-configuration, avec son
 * propre balayage. Le contexte démarrerait — plus lentement, avec des beans en
 * double, et des messages d'erreur impossibles à rattacher à leur cause.
 *
 * <p>{@link EnableScheduling} est posé ici parce que les publieurs d'outbox
 * des services en dépendent, et que leurs classes d'application — qui le
 * portaient — sont justement exclues.
 */
@SpringBootApplication(scanBasePackages = "ma.rdvsante")
@ComponentScan(
        basePackages = "ma.rdvsante",
        // Les beans sont nommés par leur nom de classe COMPLET.
        //
        // Par défaut, Spring nomme un bean d'après le nom simple de sa classe.
        // Trois services écrits par la même main ont naturellement des classes
        // homonymes — `GestionnaireErreurs`, `ConfigurationApplication` — qui
        // ne se gênaient pas tant qu'ils vivaient dans des contextes séparés.
        // Réunis, le contexte refuse de démarrer sur un conflit de nom.
        //
        // Renommer les classes des services pour arranger la démonstration
        // serait laisser le déploiement dicter le code. Le générateur de noms
        // règle la question là où elle se pose, et nulle part ailleurs.
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                                RendezvousApplication.class,
                                PatientsApplication.class,
                                NotificationsApplication.class,
                        }),

                // Les configurations des services déclarent chacune un bean
                // `horloge`. Trois beans du même nom, et le contexte refuse de
                // démarrer. Elles sont écartées, et `ConfigurationPartagee` en
                // fournit une seule — ce qui est de toute façon la vérité dans
                // un processus unique.
                //
                // Filtre par expression régulière et non par type : ces classes
                // sont de visibilité paquet, donc invisibles d'ici. C'est la
                // bonne visibilité pour elles ; c'est à ce module de composer
                // avec, pas à elles de s'ouvrir.
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "ma\\.rdvsante\\..*\\.config\\.ConfigurationApplication"),
        })
@EnableScheduling
//
// Les entités et les dépôts vivent sous `ma.rdvsante.<service>`, jamais sous
// `ma.rdvsante.demo`. Or l'auto-configuration de Spring Data et d'Hibernate
// part du paquet de la classe d'application, pas de `scanBasePackages` — qui
// ne gouverne que le balayage des composants.
//
// Sans ces deux annotations, les contrôleurs sont bien trouvés et les dépôts
// ne le sont pas : le contexte échoue sur « required a bean of type
// DepotMessageTraite », un message qui ne dit pas que c'est une question de
// paquet de base.
@EntityScan("ma.rdvsante")
// `nameGenerator` ici aussi : Spring Data nomme un dépôt d'après le nom
// simple de son interface, et « rendezvous » comme « patients » déclarent un
// DepotEvenementSortant. Les services s'injectent par TYPE, jamais par nom :
// changer les noms ne casse rien.
@EnableJpaRepositories(
        basePackages = "ma.rdvsante",
        nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class)
public class DemoApplication {

    public static void main(String[] args) {
        // Les migrations des trois services, AVANT tout contexte Spring : JPA
        // valide sa correspondance objet-relationnel au démarrage, et se
        // plaindrait de tables absentes.
        Migrations.appliquer(
                variable("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/rdvsante"),
                variable("SPRING_DATASOURCE_USERNAME", "rdvsante"),
                variable("SPRING_DATASOURCE_PASSWORD", "rdvsante"));

        SpringApplication.run(DemoApplication.class, args);
    }

    /** Une variable d'environnement, ou sa valeur par défaut si elle est
     *  absente OU vide — un hébergeur qui déclare une variable sans lui donner
     *  de valeur produit une chaîne vide, pas un null. */
    private static String variable(String nom, String defaut) {
        String valeur = System.getenv(nom);

        return valeur == null || valeur.isBlank() ? defaut : valeur;
    }
}
