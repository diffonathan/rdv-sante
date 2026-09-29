-- Schéma métier : cliniques, praticiens, créneaux, rendez-vous, file d'attente.
--
-- Parti pris général : les règles qui ne doivent JAMAIS être violées sont
-- posées dans la base, pas dans le code. Une contrainte applicative se
-- contourne par un script, un import, un second processus ; une contrainte
-- PostgreSQL, non.

-- Nécessaire pour la contrainte d'exclusion sur les créneaux : elle combine
-- une égalité (le praticien) et un chevauchement d'intervalles.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE clinique (
    id        UUID         PRIMARY KEY,
    nom       VARCHAR(160) NOT NULL,
    ville     VARCHAR(80)  NOT NULL,
    adresse   VARCHAR(255) NOT NULL,
    telephone VARCHAR(30)  NOT NULL,
    cree_le   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE praticien (
    id          UUID         PRIMARY KEY,
    clinique_id UUID         NOT NULL REFERENCES clinique (id),
    civilite    VARCHAR(10)  NOT NULL,
    nom         VARCHAR(80)  NOT NULL,
    prenom      VARCHAR(80)  NOT NULL,
    specialite  VARCHAR(120) NOT NULL,
    cree_le     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_praticien_clinique ON praticien (clinique_id);

CREATE TABLE creneau (
    id           UUID        PRIMARY KEY,
    praticien_id UUID        NOT NULL REFERENCES praticien (id),
    debut        TIMESTAMPTZ NOT NULL,
    fin          TIMESTAMPTZ NOT NULL,
    CONSTRAINT creneau_bornes_coherentes CHECK (fin > debut)
);

-- Un praticien ne peut pas avoir deux créneaux qui se chevauchent.
-- Une vérification applicative (« y a-t-il déjà un créneau ici ? » puis
-- « je l'insère ») laisse une fenêtre entre les deux requêtes ; deux
-- secrétaires qui saisissent l'agenda en même temps la trouvent.
-- La contrainte d'exclusion ferme la fenêtre : PostgreSQL refuse
-- l'insertion, quel que soit le chemin emprunté pour y arriver.
ALTER TABLE creneau
    ADD CONSTRAINT creneau_sans_chevauchement
    EXCLUDE USING gist (praticien_id WITH =, tstzrange(debut, fin) WITH &&);

CREATE INDEX idx_creneau_praticien_debut ON creneau (praticien_id, debut);

CREATE TABLE rendez_vous (
    id                UUID         PRIMARY KEY,
    creneau_id        UUID         NOT NULL REFERENCES creneau (id),
    -- Identifiant venu du service « patients ». Aucune clé étrangère : un
    -- service ne référence pas les tables d'un autre (décision D5). Le nom et
    -- le téléphone sont une COPIE volontaire — ce service doit pouvoir
    -- afficher la file même si « patients » est arrêté.
    patient_id        UUID         NOT NULL,
    patient_nom       VARCHAR(160) NOT NULL,
    patient_telephone VARCHAR(30)  NOT NULL,
    statut            VARCHAR(20)  NOT NULL,
    motif_annulation  VARCHAR(255),
    cree_le           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    annule_le         TIMESTAMPTZ,
    CONSTRAINT rendez_vous_statut_connu
        CHECK (statut IN ('CONFIRME', 'ANNULE', 'HONORE', 'ABSENT'))
);

-- Décision D4 : la double réservation est arbitrée ici.
-- Deux patients cliquent sur le même créneau à la même seconde ; le second
-- INSERT viole cet index et remonte en 409 Conflict. L'index est PARTIEL :
-- un créneau annulé redevient réservable, ce qu'un index unique ordinaire
-- aurait interdit à jamais.
CREATE UNIQUE INDEX idx_rendez_vous_creneau_actif
    ON rendez_vous (creneau_id)
    WHERE statut <> 'ANNULE';

CREATE INDEX idx_rendez_vous_patient ON rendez_vous (patient_id);

CREATE TABLE entree_file (
    id             UUID        PRIMARY KEY,
    clinique_id    UUID        NOT NULL REFERENCES clinique (id),
    rendez_vous_id UUID        NOT NULL UNIQUE REFERENCES rendez_vous (id),
    etat           VARCHAR(20) NOT NULL,
    arrive_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
    appele_le      TIMESTAMPTZ,
    termine_le     TIMESTAMPTZ,
    CONSTRAINT entree_file_etat_connu
        CHECK (etat IN ('EN_ATTENTE', 'APPELE', 'EN_CONSULTATION', 'TERMINE'))
);

-- Il n'y a VOLONTAIREMENT pas de colonne « position ».
-- Une position stockée doit être renumérotée à chaque départ de la file :
-- une transaction qui décale quinze lignes, et deux arrivées simultanées qui
-- se marchent dessus. Le rang se déduit de l'heure d'arrivée, qui ne change
-- jamais. L'index partiel ci-dessous rend ce calcul immédiat.
CREATE INDEX idx_entree_file_attente
    ON entree_file (clinique_id, arrive_le)
    WHERE etat = 'EN_ATTENTE';

COMMENT ON TABLE entree_file IS
    'File d''attente du jour. Le rang se calcule par arrive_le, il n''est jamais stocké.';
