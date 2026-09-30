package ma.rdvsante.demo;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applique les migrations des trois services, avant que Spring démarre.
 *
 * <h2>Pourquoi depuis {@code main()} et non par un bean</h2>
 *
 * <p>Les migrations doivent être passées avant que JPA ne valide son
 * correspondance objet-relationnel. Avec une seule source de migrations,
 * Spring Boot s'en charge. Avec trois, il faut soit orchestrer des dépendances
 * entre beans dont l'ordre n'est pas évident à lire, soit faire la chose
 * simple : migrer avant de démarrer quoi que ce soit.
 *
 * <p>C'est aussi ce que fait un déploiement sérieux — une étape de migration,
 * puis l'application. Le faire ici rend cet ordre visible en trois lignes.
 *
 * <h2>Trois historiques, une seule base</h2>
 *
 * <p>Chaque service numérote ses migrations à partir de V1. Réunis dans une
 * même base, ils déclareraient trois fois la version 1 dans le même historique
 * et Flyway refuserait de démarrer.
 *
 * <p>Chacun garde donc SA table d'historique. Ce n'est pas un contournement :
 * c'est ce que Flyway prévoit quand plusieurs composants partagent une base, et
 * cela préserve la propriété essentielle — chaque service reste maître de ses
 * migrations, sans jamais avoir à connaître celles des autres.
 */
final class Migrations {

    private static final Logger log = LoggerFactory.getLogger(Migrations.class);

    /** Les trois services, et l'emplacement de leurs migrations. */
    private static final String[][] SERVICES = {
            // Le jeu de démonstration s'ajoute ICI, et nulle part ailleurs :
            // cliniques, praticiens et créneaux fictifs. Sans lui, la
            // démonstration en ligne s'ouvrirait sur une liste vide, ce qui ne
            // montre rien. Une base de production ne verra jamais ce dossier,
            // parce qu'aucune autre configuration ne le mentionne.
            {"rendezvous", "classpath:db/migration/rendezvous,classpath:db/demo"},
            {"patients", "classpath:db/migration/patients"},
            {"notifications", "classpath:db/migration/notifications"},
    };

    private Migrations() {
    }

    static void appliquer(String url, String utilisateur, String motDePasse) {
        for (String[] service : SERVICES) {
            String nom = service[0];

            var resultat = Flyway.configure()
                    .dataSource(url, utilisateur, motDePasse)
                    .locations(service[1].split(","))
                    .table("flyway_historique_" + nom)
                    // La base d'une démonstration peut déjà contenir des tables
                    // — celles d'un autre service migré juste avant. Sans ceci,
                    // Flyway refuserait de poser son premier historique dans un
                    // schéma qu'il n'a pas créé lui-même.
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();

            log.info("Migrations {} : {} appliquée(s), schéma en version {}",
                    nom, resultat.migrationsExecuted,
                    resultat.targetSchemaVersion == null ? "inchangée" : resultat.targetSchemaVersion);
        }
    }
}
