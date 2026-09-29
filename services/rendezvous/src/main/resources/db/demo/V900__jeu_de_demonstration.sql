-- Jeu de démonstration — profil « dev » uniquement.
--
-- Ce fichier n'est PAS dans db/migration : il vit dans db/demo, un
-- emplacement que seul le profil dev ajoute à Flyway (voir application.yml).
-- Une base de production ne le verra jamais, même par erreur de configuration,
-- puisqu'il n'est pas sur le chemin par défaut.
--
-- Cliniques, praticiens et patients sont INVENTÉS. Aucune donnée réelle.

-- Identifiants fixes : le front de démonstration et les tests y font
-- référence. Des UUID aléatoires obligeraient à les relire à chaque
-- réinitialisation de la base.
INSERT INTO clinique (id, nom, ville, adresse, telephone) VALUES
  ('11111111-1111-4111-8111-111111111111', 'Clinique Al Amal',        'Marrakech', '12, avenue Mohammed VI',      '+212524430001'),
  ('22222222-2222-4222-8222-222222222222', 'Centre Médical Atlas',    'Marrakech', '48, rue Ibn Sina, Guéliz',    '+212524430002'),
  ('33333333-3333-4333-8333-333333333333', 'Polyclinique Ennakhil',   'Casablanca','7, boulevard Zerktouni',      '+212522430003');

INSERT INTO praticien (id, clinique_id, civilite, nom, prenom, specialite) VALUES
  ('aaaaaaaa-0001-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'Dr', 'Benali',   'Amina',   'Médecine générale'),
  ('aaaaaaaa-0002-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'Dr', 'Tazi',     'Youssef', 'Cardiologie'),
  ('aaaaaaaa-0003-4000-8000-000000000003', '22222222-2222-4222-8222-222222222222', 'Dr', 'El Fassi', 'Nadia',   'Pédiatrie'),
  ('aaaaaaaa-0004-4000-8000-000000000004', '22222222-2222-4222-8222-222222222222', 'Dr', 'Ouazzani', 'Karim',  'Dermatologie'),
  ('aaaaaaaa-0005-4000-8000-000000000005', '33333333-3333-4333-8333-333333333333', 'Dr', 'Chraibi',  'Salma',   'Ophtalmologie'),
  ('aaaaaaaa-0006-4000-8000-000000000006', '33333333-3333-4333-8333-333333333333', 'Dr', 'Bennani',  'Omar',    'Médecine générale');

-- Créneaux de 20 minutes, de 9 h à 13 h, sur les sept prochains jours.
--
-- Les horaires sont calculés à partir de now() plutôt qu'écrits en dur : un
-- jeu de démonstration figé au 3 octobre n'a plus aucun créneau réservable le
-- 4. La contrainte d'exclusion de la table garantit au passage qu'aucun
-- chevauchement ne se glisse ici — si ce script en produisait un, la migration
-- échouerait au lieu de créer un agenda incohérent.
INSERT INTO creneau (id, praticien_id, debut, fin)
SELECT
    gen_random_uuid(),
    p.id,
    base + (pas * INTERVAL '20 minutes'),
    base + ((pas + 1) * INTERVAL '20 minutes')
FROM praticien p
CROSS JOIN generate_series(1, 7) AS jour
CROSS JOIN generate_series(0, 11) AS pas
CROSS JOIN LATERAL (
    SELECT date_trunc('day', now()) + (jour * INTERVAL '1 day') + INTERVAL '9 hours'
) AS calcul(base);

COMMENT ON TABLE clinique IS
    'Cliniques de démonstration — établissements fictifs, aucune donnée réelle.';
