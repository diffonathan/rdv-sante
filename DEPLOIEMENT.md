# Mettre la démonstration en ligne sur Back4App

> ⚠️ **Ce qui est déployé n'est pas l'architecture du projet.** Les trois
> services métier y tournent dans un seul processus, sans Kafka. Lire
> `deploy/DEMONSTRATION.md` avant de juger la conception sur ce qui est en
> ligne.

Back4App Containers a été retenu parce qu'**aucune carte bancaire n'est
demandée**. En 2026, c'est devenu rare : Render, Koyeb et Fly.io exigent tous
une vérification par carte, Hugging Face Docker Spaces n'est plus gratuit, et
Zeabur a cessé d'accepter de nouveaux projets sur son cluster partagé.

L'offre libre donne **256 Mo**. L'image a été réglée pour y tenir — voir les
commentaires du `Dockerfile`, qui expliquent chaque paramètre de la machine
virtuelle Java et pourquoi il est là.

---

## 1. Le service

**back4app.com** → inscription → **Containers** → **Deploy a Web App** →
connecter GitHub → dépôt **`diffonathan/rdv-sante`**.

Back4App trouve le `Dockerfile` de la racine tout seul. Laissez le répertoire
de construction à la racine du dépôt.

Le premier déploiement prend **dix à quinze minutes** : il construit le front
Angular, puis cinq modules Java. Les suivants sont plus rapides — Docker
réutilise les couches de dépendances tant que les `pom.xml` ne changent pas.

## 2. Les quatre variables

Tout le reste vit dans `application.yml`. Dans **Variables** :

| Variable | Valeur |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://VOTRE-HOTE.neon.tech/rdvsante?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | le mot de passe Neon |
| `RDV_DOCUMENTATION_MOT_DE_PASSE` | celui de votre choix |

⚠️ **Le format n'est pas celui que Neon affiche.** Java attend un préfixe
`jdbc:` et **ne veut pas les identifiants dans l'adresse** : ils vont dans
leurs propres variables. Coller la chaîne de Neon telle quelle donne une
erreur de pilote au démarrage.

## 3. Le domaine

Back4App attribue une adresse en `*.b4a.run` dès le déploiement. Aucune
variable à mettre à jour ensuite : le front appelle l'API en chemins relatifs,
et les deux sont servis par le même processus.

---

## Ce que l'offre gratuite implique

**256 Mo de mémoire, et c'est serré.** Premier essai sous plafond strict :
tué par le système, code 137. Après réglage de la machine virtuelle et
initialisation paresseuse de Spring, l'application démarre et occupe **249 Mo
sur 256, soit 97 %** — parcours complet validé, réservation comprise, et
trente requêtes d'affilée sans incident.

Ça tient, sans marge. Si le processus est un jour tué faute de mémoire, il
sort proprement (`ExitOnOutOfMemoryError`) et l'hébergeur le relance : une
minute d'indisponibilité, pas une panne silencieuse.

L'initialisation paresseuse a un effet visible : la première visite de chaque
écran est un peu plus lente, le temps que Spring construise ce dont il a
besoin. Les suivantes sont normales.

**Le service s'endort après une période d'inactivité.** Le réveil prend 40 à
60 secondes — une machine virtuelle Java démarre plus lentement qu'un
processus PHP. Le portfolio l'annonce en toutes lettres : une minute d'écran
blanc non annoncée passe pour une panne.

**Tout est ouvert.** N'importe qui peut réserver, annuler, faire avancer la
file. Les cliniques, les praticiens et les patients sont entièrement fictifs,
et aucune donnée de santé réelle n'est traitée — c'est la seule raison pour
laquelle cette ouverture est acceptable.

---

## Vérifier

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://VOTRE-ADRESSE.b4a.run/actuator/health
```

`200` signifie que l'application répond **et** que la base est jointe.
