-- Une base par service, dans une seule instance PostgreSQL.
--
-- « Database per service » veut dire qu'aucun service ne lit les tables d'un
-- autre. Ici la séparation est réelle : une base et un utilisateur distincts
-- par service, chacun sans aucun droit sur les bases des autres. Le passage à
-- trois instances séparées ne demandera que de changer trois URL.
-- Voir docs/architecture.md, décision D5.

CREATE USER rendezvous_app     WITH PASSWORD 'rendezvous';
CREATE USER patients_app       WITH PASSWORD 'patients';
CREATE USER notifications_app  WITH PASSWORD 'notifications';

CREATE DATABASE rendezvous     OWNER rendezvous_app;
CREATE DATABASE patients       OWNER patients_app;
CREATE DATABASE notifications  OWNER notifications_app;

-- Personne d'autre que le propriétaire ne se connecte à une base.
REVOKE CONNECT ON DATABASE rendezvous    FROM PUBLIC;
REVOKE CONNECT ON DATABASE patients      FROM PUBLIC;
REVOKE CONNECT ON DATABASE notifications FROM PUBLIC;
