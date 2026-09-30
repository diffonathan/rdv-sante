# La démonstration en ligne, et ce qu'elle n'est pas

> **À lire avant de juger l'architecture sur ce qui est déployé.** Ce n'est
> pas la même chose.

---

## Le problème

Le dépôt contient **quatre services indépendants** qui s'échangent des
événements par Apache Kafka, chacun avec sa base PostgreSQL et son propre
compte. C'est ce que les tests exercent, contre un vrai courtier et de vraies
bases démarrées pour l'occasion.

Mettre cela en ligne gratuitement est impossible :

| Contrainte | Réalité |
|---|---|
| Quatre machines virtuelles Java | ~120 Mo chacune, soit 480 Mo — les offres gratuites en donnent 512 **au total** |
| Un courtier Kafka | plus aucun fournisseur gratuit permanent depuis la fermeture d'Upstash Kafka |
| Quatre bases de données | les offres gratuites en donnent une |

Trois issues possibles : payer, ne rien déployer, ou déployer un repli
honnête. La troisième a été retenue.

---

## Ce qui est déployé

**Un seul processus**, contenant les trois services métier, servant aussi le
front Angular. Les événements passent d'un service à l'autre **en mémoire**
au lieu de traverser un courtier.

### Ce qui reste exactement pareil

- **Le code des services n'est pas modifié.** Le module `services/demo` en
  DÉPEND, il ne les réécrit pas. Ce sont les mêmes classes, les mêmes règles,
  les mêmes migrations.
- **L'outbox fonctionne.** L'événement est toujours écrit dans PostgreSQL dans
  la même transaction que le rendez-vous, relevé par lots, marqué publié
  seulement après remise, avec échecs retenus et tentatives comptées.
- **Les consommateurs restent idempotents.** La clé de déduplication voyage
  toujours avec le message, et c'est le même point d'entrée que celui
  qu'empruntent les consommateurs Kafka — il n'y a pas un chemin « vrai » et un
  chemin « de démonstration » qui pourraient diverger.
- **Les garanties de la base tiennent.** Deux réservations simultanées du même
  créneau : la seconde reçoit un 409, parce qu'un index unique partiel
  l'interdit physiquement. Vérifié sur la démonstration déployée, pas seulement
  en test.

### Ce qui est perdu, et qu'il faut dire

- **La tolérance aux pannes n'est pas démontrable.** Un seul processus tombe en
  entier. L'intérêt de services indépendants — « si les notifications tombent,
  les rendez-vous continuent » — ne se voit pas en ligne. Il se voit en lançant
  le projet complet, et il est éprouvé par les tests.
- **Le cloisonnement par les droits non plus.** Une seule base, un seul compte,
  au lieu d'un compte par service incapable de lire les tables des autres. Le
  dépôt garde cette séparation, et un test la vérifie en tentant l'accès.
- **La durabilité du message une fois sorti de l'outbox.** Kafka le
  conserverait ; ici, une panne entre publication et traitement le perd. Sur un
  processus unique, une telle panne emporte de toute façon tout le reste.
- **Il n'y a pas de passerelle.** Rien à router quand tout est au même endroit.

---

## Comment c'est fait

Trois pièces, et rien d'autre :

**1. Un port de transport.** `PublieurOutbox` ne connaît plus Kafka mais une
interface `TransportEvenements`. Deux implémentations : `TransportKafka`
(`@Profile("!demo")`) et `TransportLocal`, dans le module de démonstration.

Le profil est écrit `!demo` et non `kafka` : **l'implémentation Kafka est le
cas normal**, et un profil oublié doit rendre le comportement normal, pas le
comportement dégradé. L'inverse ferait tourner une production entière sur un
transport en mémoire sans que personne s'en aperçoive.

**2. Un pont.** `PontEvenements` écoute les événements Spring publiés par
`TransportLocal` et appelle `EcouteurEvenements.traiter` — exactement le point
d'entrée qu'empruntent les consommateurs Kafka après avoir extrait la clé
d'idempotence.

**3. Un module qui réunit le tout.** `services/demo` dépend des trois services,
applique leurs migrations avant que Spring démarre, et sert le front.

---

## Ce qu'il a fallu régler pour les faire cohabiter

Réunir trois applications Spring Boot dans un contexte fait apparaître des
collisions qu'on ne voit jamais quand elles tournent séparément. Elles sont
instructives :

| Collision | Réponse |
|---|---|
| Trois `application.yml` à la racine de trois jars | Un seul est chargé, et lequel dépend de l'ordre du chemin de classes. Le module de démonstration a le sien, et redéclare tout ce qu'il faut. |
| Trois jeux de migrations numérotés à partir de V1 | Un historique Flyway par service, dans la même base. C'est ce que Flyway prévoit quand plusieurs composants partagent une base. |
| Deux tables `evenement_sortant` | L'outbox de `patients` porte désormais le nom de son service. La démonstration a révélé le problème ; elle ne l'a pas créé. |
| Deux classes `GestionnaireErreurs`, deux `ConfigurationApplication` | Un générateur de noms de beans par nom de classe complet. Renommer les classes des services pour arranger le déploiement serait le laisser dicter le code. |
| Trois beans `horloge` | Les configurations des services sont écartées du balayage, et une horloge unique est fournie — ce qui est de toute façon la vérité dans un processus. |

Deux pièges méritent d'être notés, parce qu'ils échouent **en silence** :

- `spring.autoconfigure.exclude` visant
  `org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration` n'exclut
  rien : Spring Boot 4 a éclaté son auto-configuration en modules, et le nom est
  désormais `org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration`.
  Une exclusion qui ne désigne rien n'avertit pas. Le symptôme arrive plus tard
  et ailleurs : « No group.id found in consumer config ».
- `@EntityScan` a migré de `org.springframework.boot.autoconfigure.domain` vers
  `org.springframework.boot.persistence.autoconfigure`.

---

## Mettre en ligne

Voir `render.yaml` à la racine. Trois valeurs à saisir à la main : l'adresse de
la base (une base Neon gratuite, permanente, contrairement à celle de Render
qui expire au bout de 30 jours), ses identifiants, et le mot de passe de la
documentation.

Mesuré sur l'image : **372 Mo** au repos, dans une limite de 512.

> **Le service s'endort après 15 minutes sans visite** et met 40 à 60 secondes
> à se réveiller — une machine virtuelle Java est plus lente à démarrer qu'un
> processus PHP. À dire dans le portfolio : une page blanche d'une minute
> passe pour une panne.

---

## Lancer la vraie architecture

Sur un poste, c'est le projet complet qui tourne — quatre services, un
courtier, quatre bases :

```bash
docker compose -f deploy/compose/infra.yml up -d
# puis chaque service, et `cd web && npm start`
```

C'est cette configuration-là qu'il faut regarder pour juger la conception. La
démonstration en ligne sert à montrer que le produit fonctionne, pas comment il
est bâti.
