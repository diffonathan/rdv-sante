-- Le patient, et rien d'autre.
--
-- Ce service ne connaît ni les créneaux ni les rendez-vous : il détient
-- l'identité, et il l'annonce. C'est le service « rendezvous » qui garde une
-- copie du nom et du téléphone dont il a besoin, pour continuer d'afficher sa
-- file quand celui-ci est arrêté (décision D5).

CREATE TABLE patient (
    id           UUID         PRIMARY KEY,
    nom          VARCHAR(80)  NOT NULL,
    prenom       VARCHAR(80)  NOT NULL,
    telephone    VARCHAR(30)  NOT NULL,
    email        VARCHAR(160),
    -- Canal choisi par le patient. Le service « notifications » s'en sert
    -- plutôt que de supposer que tout le monde veut des SMS.
    canal_prefere VARCHAR(20) NOT NULL DEFAULT 'SMS',
    -- Consentement explicite au contact. Sans lui, on ne notifie pas :
    -- un rappel non consenti est une sollicitation non désirée.
    consent_contact BOOLEAN   NOT NULL DEFAULT TRUE,
    cree_le      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    maj_le       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT patient_canal_connu CHECK (canal_prefere IN ('SMS', 'EMAIL'))
);

-- Le téléphone identifie le patient : au Maroc, c'est le seul identifiant
-- qu'une clinique demande et que le patient connaît par cœur. L'unicité évite
-- qu'un même patient existe en trois exemplaires parce qu'il a réservé trois
-- fois depuis trois navigateurs.
CREATE UNIQUE INDEX idx_patient_telephone ON patient (telephone);

-- Outbox, identique dans son principe à celle du service « rendezvous »
-- (décision D2). Volontairement RECOPIÉE plutôt que partagée dans une
-- bibliothèque commune : un module partagé obligerait les deux services à
-- évoluer et à se redéployer ensemble, ce que le découpage cherche à éviter.
CREATE TABLE evenement_sortant (
    id              UUID         PRIMARY KEY,
    agregat_type    VARCHAR(60)  NOT NULL,
    agregat_id      UUID         NOT NULL,
    cle_partition   VARCHAR(120) NOT NULL,
    type            VARCHAR(80)  NOT NULL,
    charge_utile    JSONB        NOT NULL,
    cree_le         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publie_le       TIMESTAMPTZ,
    tentatives      INTEGER      NOT NULL DEFAULT 0,
    derniere_erreur TEXT
);

CREATE INDEX idx_evenement_sortant_en_attente
    ON evenement_sortant (cree_le)
    WHERE publie_le IS NULL;

COMMENT ON TABLE patient IS
    'Identité des patients. Données de démonstration : personnes fictives.';
