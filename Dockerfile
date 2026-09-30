# Image de la démonstration en ligne : les trois services et le front,
# dans un seul conteneur.
#
# Ce n'est PAS l'architecture du projet — celle-ci sépare quatre services et
# un courtier Kafka, et c'est ce que le dépôt contient et que les tests
# exercent. C'est son déploiement replié, pour un hébergement gratuit de
# 512 Mo où quatre machines virtuelles Java ne tiennent pas, et où aucun
# fournisseur ne propose de Kafka gratuit permanent.
#
# Ce qui est préservé : le code des services, leurs outbox, leurs migrations,
# l'idempotence des consommateurs, les garanties posées dans PostgreSQL.
# Ce qui est perdu : la tolérance aux pannes et le cloisonnement par les
# droits de base. Voir `deploy/DEMONSTRATION.md`.

# ---------------------------------------------------------------------------
# Étape 1 — le front Angular
# ---------------------------------------------------------------------------
FROM node:22-alpine AS front

WORKDIR /build

# Les manifestes d'abord : tant qu'ils ne changent pas, Docker réutilise la
# couche d'installation. Copier tout le projet d'emblée réinstallerait les
# dépendances à chaque modification d'un composant.
COPY web/package.json web/package-lock.json ./
RUN npm ci

COPY web/ ./
RUN npm run build

# ---------------------------------------------------------------------------
# Étape 2 — les services Java
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS java

WORKDIR /build

# Les poms seuls, pour que le téléchargement des dépendances ne recommence pas
# à chaque modification du code. `-o` échouerait ici : c'est la seule étape qui
# a le droit d'aller sur le réseau.
COPY services/pom.xml ./
COPY services/documentation/pom.xml documentation/
COPY services/rendezvous/pom.xml rendezvous/
COPY services/patients/pom.xml patients/
COPY services/notifications/pom.xml notifications/
COPY services/gateway/pom.xml gateway/
COPY services/demo/pom.xml demo/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY services/ ./

# Le front rejoint les ressources du module de démonstration AVANT
# l'empaquetage : il sera servi par le même serveur que l'API, ce qui supprime
# la question des origines croisées.
COPY --from=front /build/dist/web/browser/ demo/src/main/resources/static/

# `-pl demo -am` : le module de démonstration et CE DONT IL DÉPEND, rien de
# plus. La passerelle n'en fait pas partie — elle est réactive et ce
# déploiement n'a rien à router — donc elle n'est pas compilée, sans avoir à
# l'exclure nommément.
RUN mvn -B -q package -DskipTests -pl demo -am

# ---------------------------------------------------------------------------
# Étape 3 — l'image servie
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=java /build/demo/target/demo-0.1.0-SNAPSHOT.jar app.jar

# La JVM ne voit pas la limite du conteneur comme une limite : sans consigne,
# elle dimensionne son tas sur la mémoire de la MACHINE et se fait tuer par
# l'hébergeur. 70 % lui laisse de quoi respirer pour les piles, le
# ramasse-miettes et le code compilé.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

# SerialGC et non le ramasse-miettes parallèle : sur un seul cœur partagé, les
# fils supplémentaires coûtent plus qu'ils ne rapportent.

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java -jar app.jar"]
