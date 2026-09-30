# Mettre la démonstration en ligne sur Zeabur

> ⚠️ **Ce qui est déployé n'est pas l'architecture du projet.** Les trois
> services métier y tournent dans un seul processus, sans Kafka. Lire
> `deploy/DEMONSTRATION.md` avant de juger la conception sur ce qui est en
> ligne.

Zeabur a été retenu parce qu'**aucune carte bancaire n'est demandée** : Render,
Koyeb et Fly.io exigent tous une vérification par carte depuis 2026, et
Hugging Face Docker Spaces n'est plus gratuit.

`zbpack.json` désigne explicitement le `Dockerfile` de la racine. Il serait
trouvé tout seul, mais le dépôt contient six `pom.xml` : sans cette indication,
Zeabur pourrait le prendre pour un projet Maven ordinaire et tenter de le
construire autrement.

---

## 1. Le service

**zeabur.com** → **New Project** → région **Frankfurt**, la même que la base
Neon. Une base à Francfort et une application ailleurs, ce sont 200 ms perdues
à chaque requête.

**Add Service → Git → `diffonathan/rdv-sante`**.

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

**Networking → Generate Domain**. Aucune variable à mettre à jour ensuite :
le front appelle l'API en chemins relatifs, et les deux sont servis par le
même processus.

---

## Ce que l'offre gratuite implique

**512 Mo de mémoire.** Mesuré sur cette image : **372 Mo** au repos. Ça tient,
mais sans marge confortable — la machine virtuelle Java est réglée pour ne pas
dépasser 70 % de la limite du conteneur, et le ramasse-miettes série est
choisi parce que, sur un seul cœur partagé, les fils supplémentaires coûtent
plus qu'ils ne rapportent.

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
curl -s -o /dev/null -w "%{http_code}\n" https://VOTRE-ADRESSE.zeabur.app/actuator/health
```

`200` signifie que l'application répond **et** que la base est jointe.
