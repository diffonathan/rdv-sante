-- Socle technique du service : la table d'outbox.
--
-- Pourquoi elle existe : enregistrer un rendez-vous et publier l'événement
-- correspondant touchent deux systèmes (PostgreSQL et Kafka) sans transaction
-- commune. Si la base réussit et Kafka échoue, le patient a un rendez-vous
-- dont personne n'est prévenu ; dans l'autre sens, on notifie un rendez-vous
-- qui n'existe pas.
--
-- L'événement est donc écrit ICI, dans la même transaction que la donnée
-- métier. Un publieur périodique lit les lignes non publiées et les envoie à
-- Kafka. Rien ne se perd, rien ne s'invente.
-- Voir docs/architecture.md, décision D2.

CREATE TABLE evenement_sortant (
    id              UUID         PRIMARY KEY,
    agregat_type    VARCHAR(60)  NOT NULL,
    agregat_id      UUID         NOT NULL,
    -- Sert de clé de partition Kafka : tous les événements d'un même
    -- rendez-vous atterrissent dans la même partition, donc leur ordre est
    -- garanti. Sans cela une annulation pourrait être traitée avant la
    -- réservation qu'elle annule.
    cle_partition   VARCHAR(120) NOT NULL,
    type            VARCHAR(80)  NOT NULL,
    charge_utile    JSONB        NOT NULL,
    cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publie_le       TIMESTAMPTZ,
    tentatives      INTEGER      NOT NULL DEFAULT 0,
    derniere_erreur TEXT
);

-- Le publieur ne lit que les lignes en attente. L'index partiel ne contient
-- donc que celles-là : il reste minuscule même quand la table a des millions
-- de lignes publiées derrière elle.
CREATE INDEX idx_evenement_sortant_en_attente
    ON evenement_sortant (cree_le)
    WHERE publie_le IS NULL;

COMMENT ON TABLE evenement_sortant IS
    'Outbox transactionnel : événements écrits avec la donnée métier, publiés ensuite vers Kafka.';
