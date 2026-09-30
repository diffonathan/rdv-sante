package ma.rdvsante.documentation;

import java.util.List;

/**
 * Le contenu de la documentation technique.
 *
 * <p>Classe SIMPLE, sans Spring et sans dépendance : deux applications la
 * servent — la passerelle quand les services tournent séparément, le module de
 * démonstration quand ils sont réunis dans un seul processus. Sans ce partage,
 * il y aurait deux copies du même texte, et au bout de trois corrections, deux
 * textes différents.
 *
 * Écrit en données plutôt qu'en gabarit : le front construit sa table des
 * matières tout seul, et corriger un texte ne demande de toucher à aucune mise
 * en page.
 *
 * Il vit côté serveur, et non dans le front, pour une raison précise : c'est
 * ce qui fait que le mot de passe protège quelque chose. Un texte embarqué
 * dans le paquet JavaScript se lit sans jamais taper le mot de passe.
 *
 * On y dit ce qui a été difficile, ce qui a été choisi, et surtout ce qui a
 * été raté — un projet dont la documentation ne mentionne aucune erreur n'a
 * pas été relu, ou n'a rien appris.
 */
public final class Documentation {

    private Documentation() {
    }

    public static List<Section> sections() {
        return List.of(
                new Section("probleme", "Le problème, vu de la salle d'attente", List.of(
                        Bloc.p("""
                                Au Maroc, on prend rendez-vous chez le médecin par téléphone. \
                                Le patient arrive, s'assoit, et attend sans savoir combien de \
                                personnes passent avant lui — ni si celle qui vient d'entrer \
                                avait rendez-vous ou non."""),
                        Bloc.p("""
                                Le secrétariat vit l'autre moitié du problème : un cahier, un \
                                téléphone qui sonne, et des patients debout devant le comptoir \
                                qui demandent tous la même chose. Le rang dans la file existe \
                                dans sa tête, et nulle part ailleurs."""),
                        Bloc.p("""
                                L'application traite les deux côtés. Mais le cœur du sujet est \
                                ailleurs : dès qu'une file d'attente et des réservations \
                                simultanées entrent en jeu, ce sont des problèmes de \
                                concurrence, et ils ne se règlent pas dans l'interface.""")
                )),

                new Section("creneaux", "Deux patients, un seul créneau", List.of(
                        Bloc.p("""
                                Deux personnes ouvrent la même page, voient le même créneau \
                                libre, et cliquent à la même seconde. Vérifier « le créneau \
                                est-il libre ? » avant d'écrire ne suffit pas : les deux \
                                requêtes lisent avant que l'une écrive, et les deux passent."""),
                        Bloc.p("""
                                La garantie est posée dans PostgreSQL, pas dans le code. Un \
                                index unique partiel interdit physiquement un second \
                                rendez-vous actif sur le même créneau. Le service tente \
                                l'écriture et traduit le refus en conflit — il ne demande pas \
                                la permission, il essaie."""),
                        Bloc.code("""
                                CREATE UNIQUE INDEX idx_rendez_vous_creneau_actif
                                    ON rendez_vous (creneau_id)
                                 WHERE statut <> 'ANNULE';"""),
                        Bloc.p("""
                                Partiel, parce qu'un rendez-vous annulé doit libérer son \
                                créneau tout en restant lisible dans l'historique. Un index \
                                complet interdirait de réserver un créneau dont le précédent \
                                rendez-vous a été annulé."""),
                        Bloc.p("""
                                Les créneaux eux-mêmes ne peuvent pas se chevaucher, par une \
                                contrainte d'exclusion : deux plages d'un même praticien qui \
                                se recouvrent sont refusées à l'écriture. Une vérification \
                                applicative aurait le même défaut que ci-dessus."""),
                        Bloc.code("""
                                ALTER TABLE creneau ADD CONSTRAINT creneau_sans_chevauchement
                                    EXCLUDE USING gist (
                                        praticien_id WITH =,
                                        tstzrange(debut, fin) WITH &&
                                    );""")
                )),

                new Section("jpa", "Le piège qui a rendu une garantie inutile", List.of(
                        Bloc.p("""
                                Voici l'erreur que j'ai commise, et comment je l'ai trouvée. \
                                Elle mérite plus de place que les parties réussies."""),
                        Bloc.p("""
                                Le service de notifications doit ignorer un message déjà traité \
                                — une file d'attente promet « au moins une fois », pas « au plus \
                                une fois ». La garde consistait à enregistrer l'identifiant du \
                                message dans une table à clé unique : si l'insertion échoue, \
                                c'est un doublon."""),
                        Bloc.p("""
                                Elle n'a rien empêché pendant des semaines. L'entité portait un \
                                identifiant ASSIGNÉ et non généré. Dans ce cas, `save()` ne fait \
                                pas un INSERT : il fait un `merge()`, donc un SELECT suivi d'un \
                                UPDATE. La contrainte d'unicité n'était jamais sollicitée, et \
                                aucune exception n'était levée."""),
                        Bloc.p("""
                                Rien ne le signalait. Le code se lisait correctement, les tests \
                                passaient — ils ne rejouaient aucun message. Je l'ai découvert \
                                en remettant volontairement le curseur de lecture au début : \
                                onze messages relus, onze notifications envoyées en double."""),
                        Bloc.code("""
                                @Modifying
                                @Query(value = \"""
                                        INSERT INTO message_traite (evenement_id, topic, traite_le)
                                        VALUES (:evenementId, :topic, now())
                                        ON CONFLICT (evenement_id) DO NOTHING
                                        \""", nativeQuery = true)
                                int marquerSiNouveau(UUID evenementId, String topic);"""),
                        Bloc.p("""
                                La réécriture est explicite : on dit à la base ce qu'on veut, et \
                                le nombre de lignes touchées répond à la question « était-ce \
                                nouveau ? ». Trois tests de non-régression rejouent désormais les \
                                messages pour que ce défaut ne puisse pas revenir sans se voir."""),
                        Bloc.p("""
                                La leçon retenue n'est pas « attention à JPA ». C'est qu'une \
                                garde qui ne se déclenche jamais est indiscernable d'une garde \
                                qui fonctionne — il faut donc la déclencher exprès, dans un \
                                test, sinon on ne sait rien.""")
                )),

                new Section("evenements", "Quatre services qui ne s'appellent jamais", List.of(
                        Bloc.p("""
                                Les services ne s'appellent pas directement : ils publient des \
                                événements. Si le service de notifications tombe, les rendez-vous \
                                continuent d'être pris ; quand il redémarre, il rattrape ce qu'il \
                                a manqué."""),
                        Bloc.p("""
                                Un appel direct aurait fait l'inverse : une panne du service le \
                                moins critique aurait empêché de réserver."""),
                        Bloc.p("""
                                L'événement n'est pas publié au moment de l'écriture, mais \
                                enregistré dans la même transaction que le rendez-vous, puis \
                                relayé après validation. Sans cela, deux issues fausses \
                                deviennent possibles : un message envoyé pour une transaction \
                                finalement annulée, ou une transaction validée dont le message \
                                s'est perdu."""),
                        Bloc.p("""
                                Le relais lit les événements en attente avec un verrou qui saute \
                                les lignes déjà prises par une autre instance. Plusieurs relais \
                                peuvent donc tourner en parallèle sans se marcher dessus ni \
                                traiter deux fois le même événement.""")
                )),

                new Section("temps-reel", "La file d'attente qui se met à jour seule", List.of(
                        Bloc.p("""
                                L'écran de la salle d'attente tourne sur une télévision qu'on \
                                n'éteint jamais. Il se met à jour dès que le secrétariat agit, \
                                sans rechargement."""),
                        Bloc.p("""
                                Le flux est ouvert par le navigateur et tenu par le serveur. \
                                Interroger le serveur toutes les deux secondes aurait coûté des \
                                milliers de requêtes par jour pour n'apprendre presque jamais \
                                rien de neuf."""),
                        Bloc.p("""
                                Le message n'est émis qu'APRÈS validation de la transaction. \
                                Émis pendant, il annoncerait un appel qu'une annulation vient \
                                d'effacer — et l'écran afficherait un nom que personne n'a \
                                appelé."""),
                        Bloc.p("""
                                Le rang dans la file n'est jamais stocké. Il se déduit de \
                                l'heure d'arrivée, à la lecture. Une colonne « position » aurait \
                                demandé de renuméroter toutes les lignes suivantes à chaque \
                                départ — et la moindre mise à jour manquée aurait laissé deux \
                                patients au même rang.""")
                )),

                new Section("tests", "Ce que les tests prouvent, et comment", List.of(
                        Bloc.p("""
                                Les tests tournent contre une vraie base PostgreSQL et un vrai \
                                bus de messages, démarrés pour l'occasion. Rien n'est simulé : \
                                tout ce que ce projet garantit est écrit dans la base, et une \
                                base simplifiée ne connaîtrait ni les index partiels, ni les \
                                contraintes d'exclusion, ni les verrous."""),
                        Bloc.p("""
                                La course sur un créneau est éprouvée par huit tentatives \
                                simultanées de réservation du même créneau. Une seule réussit, \
                                sept reçoivent un conflit — vérifié, pas espéré."""),
                        Bloc.p("""
                                L'idempotence est éprouvée en rejouant les messages depuis le \
                                début, comme le ferait une file après un incident. C'est le test \
                                qui aurait attrapé le défaut décrit plus haut, et il n'existait \
                                pas.""")
                )),

                new Section("lancer", "Faire tourner le projet", List.of(
                        Bloc.code("""
                                docker compose -f deploy/compose/docker-compose.yml up -d
                                cd web && npm install && npm start"""),
                        Bloc.p("""
                                Quatre services, une base par service, un bus de messages, et le \
                                front Angular. Chaque service possède SA base et son propre \
                                compte : aucun ne peut lire les tables d'un autre, et un test le \
                                vérifie en tentant l'accès."""),
                        Bloc.p("""
                                La documentation est ouverte par la variable \
                                RDV_DOCUMENTATION_MOT_DE_PASSE, lue par la passerelle. Non \
                                définie, l'accès est refusé — plutôt que d'ouvrir avec une \
                                chaîne vide si la variable est oubliée au déploiement.""")
                ))
        );
    }

    public record Section(String id, String titre, List<Bloc> blocs) {}

    /** Un paragraphe ou un extrait de code. Deux champs plutôt que deux types :
     *  le front n'a qu'à regarder lequel est renseigné. */
    public record Bloc(String p, String code) {

        public static Bloc p(String texte) {
            return new Bloc(texte, null);
        }

        public static Bloc code(String extrait) {
            return new Bloc(null, extrait);
        }
    }
}
