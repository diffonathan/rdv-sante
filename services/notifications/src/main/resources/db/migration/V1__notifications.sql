-- Deux tables, deux rôles bien distincts.

-- 1. La garde contre les doublons (décision D3).
--
-- Kafka garantit « au moins une fois », pas « exactement une fois » : un
-- message peut arriver deux fois, notamment quand le producteur a envoyé puis
-- est tombé avant de noter l'envoi. Sans cette table, le patient recevrait
-- deux SMS de confirmation pour un seul rendez-vous.
--
-- La clé primaire EST la protection : le second INSERT viole la contrainte, le
-- message est ignoré. Pas de « SELECT puis INSERT », qui laisserait une
-- fenêtre entre les deux à deux consommateurs concurrents.
CREATE TABLE message_traite (
    evenement_id UUID        PRIMARY KEY,
    topic        VARCHAR(80) NOT NULL,
    traite_le    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Purge : au-delà de quelques jours, un doublon n'arrive plus. Garder
-- l'intégralité de l'historique ferait grossir la table sans rien protéger.
CREATE INDEX idx_message_traite_date ON message_traite (traite_le);

-- 2. La boîte d'envoi.
--
-- Aucun contrat SMS n'est souscrit pour un projet de démonstration : les
-- messages sont écrits ici et consultables à l'écran, au lieu de partir.
-- Le point de bascule vers un vrai fournisseur tient dans une interface —
-- `canal` dit déjà par où le message serait parti.
CREATE TABLE notification (
    id             UUID         PRIMARY KEY,
    evenement_id   UUID         NOT NULL,
    type           VARCHAR(40)  NOT NULL,
    canal          VARCHAR(20)  NOT NULL,
    destinataire   VARCHAR(30)  NOT NULL,
    patient_nom    VARCHAR(160) NOT NULL,
    contenu        TEXT         NOT NULL,
    cree_le        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    envoye_le      TIMESTAMPTZ,
    CONSTRAINT notification_type_connu
        CHECK (type IN ('CONFIRMATION', 'ANNULATION', 'BIENTOT_VOTRE_TOUR')),
    CONSTRAINT notification_canal_connu
        CHECK (canal IN ('SMS', 'EMAIL'))
);

CREATE INDEX idx_notification_recent ON notification (cree_le DESC);

COMMENT ON TABLE message_traite IS
    'Clés d''idempotence : un événement déjà vu ne produit pas de seconde notification.';
COMMENT ON TABLE notification IS
    'Boîte d''envoi. En démonstration rien ne part réellement : les messages sont affichés.';
