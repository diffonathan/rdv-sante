-- Nouveau type de notification : le message de bienvenue à l'inscription.
--
-- La contrainte CHECK de V1 énumère les types acceptés ; ajouter une valeur à
-- l'enum Java sans toucher à la base ferait échouer l'insertion. C'est voulu :
-- un enum et une contrainte qui divergent, c'est une donnée invalide qui passe.
ALTER TABLE notification DROP CONSTRAINT notification_type_connu;

ALTER TABLE notification ADD CONSTRAINT notification_type_connu
    CHECK (type IN ('BIENVENUE', 'CONFIRMATION', 'ANNULATION', 'BIENTOT_VOTRE_TOUR'));
