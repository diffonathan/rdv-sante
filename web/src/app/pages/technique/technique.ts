import { Component } from '@angular/core';

interface Defi {
  titre: string;
  situation: string;
  reponse: string;
}

/**
 * L'écran qui montre le travail.
 *
 * <p>Il s'adresse d'abord à quelqu'un qui lit une candidature, pas à un
 * architecte : les technologies sont visibles au premier coup d'œil, et les
 * difficultés sont racontées en mots de tous les jours. Une explication qu'il
 * faut déjà être développeur pour comprendre ne prouve rien à celui qui trie
 * les CV.
 */
@Component({
  selector: 'page-technique',
  imports: [],
  templateUrl: './technique.html',
})
export class Technique {
  readonly depot = 'https://github.com/diffonathan/rdv-sante';

  /** Le premier bloc de la page : c'est ce qu'on vient chercher. */
  readonly technologies = [
    {
      groupe: 'Langage et serveur',
      items: ['Java 21', 'Spring Boot', 'Spring Data JPA', 'Spring Cloud Gateway', 'API REST'],
    },
    {
      groupe: 'Architecture',
      items: ['Microservices', 'Apache Kafka', 'Messagerie asynchrone', 'Passerelle d’API'],
    },
    {
      groupe: 'Base de données',
      items: ['PostgreSQL', 'Flyway', 'SQL', 'Migrations versionnées'],
    },
    {
      groupe: 'Interface',
      items: ['Angular', 'TypeScript', 'HTML', 'CSS', 'Temps réel (SSE)'],
    },
    {
      groupe: 'Qualité',
      items: ['JUnit', 'Mockito', 'Testcontainers', 'Tests d’intégration'],
    },
    {
      groupe: 'Mise en production',
      items: ['Docker', 'Docker Compose', 'GitHub Actions', 'Intégration continue', 'Supervision'],
    },
  ];

  readonly chiffres = [
    { valeur: '4', libelle: 'services indépendants' },
    { valeur: '20', libelle: 'tests automatisés' },
    { valeur: '3', libelle: 'écrans, trois publics' },
    { valeur: '100 %', libelle: 'du code sur GitHub' },
  ];

  readonly defis: Defi[] = [
    {
      titre: 'Deux personnes qui réservent la même seconde',
      situation:
        'Deux patients cliquent sur le même horaire en même temps. Si l’application se contente de regarder si le créneau est libre avant de l’attribuer, il reste un court instant où les deux passent — et ce jour-là, deux personnes se présentent pour le même rendez-vous.',
      reponse:
        'C’est la base de données elle-même qui tranche, et non le code. Elle refuse la seconde réservation, l’application s’en aperçoit et propose aussitôt un autre horaire, sans que le patient ait à tout ressaisir.',
    },
    {
      titre: 'Ne jamais perdre un rappel',
      situation:
        'Enregistrer le rendez-vous et prévenir le patient sont deux opérations distinctes. Si la seconde échoue — réseau coupé, service arrêté — le patient a un rendez-vous dont il n’a jamais entendu parler.',
      reponse:
        'Le message à envoyer est enregistré en même temps que le rendez-vous, puis envoyé ensuite. Si l’envoi échoue, il repart tout seul au tour suivant. Rien ne se perd, et rien n’est envoyé pour un rendez-vous qui n’existe pas.',
    },
    {
      titre: 'Ne pas envoyer deux fois le même message',
      situation:
        'Un message peut être livré deux fois — c’est normal dans ce type de système. Sans précaution, le patient reçoit deux confirmations pour un seul rendez-vous.',
      reponse:
        'Chaque message reçu laisse une trace. Le second passage voit la trace et s’arrête là. Une première version de ce garde-fou ne fonctionnait pas, et rien ne le montrait : je ne l’ai découvert qu’en rejouant volontairement tous les messages. C’est corrigé, et trois tests l’empêchent désormais de revenir.',
    },
    {
      titre: 'Un écran qui se met à jour tout seul',
      situation:
        'L’écran de la salle d’attente doit suivre la file sans qu’on y touche. Interroger le serveur toutes les deux secondes fonctionne, mais gaspille et réagit avec du retard.',
      reponse:
        'C’est le serveur qui prévient l’écran dès que quelque chose change. Ouvrez « Secrétariat » et « Salle d’attente » côte à côte : appelez un patient d’un côté, l’autre écran change immédiatement.',
    },
  ];

  readonly services = [
    { nom: 'Rendez-vous', role: 'Créneaux, réservations, file d’attente' },
    { nom: 'Patients', role: 'Identité et coordonnées' },
    { nom: 'Notifications', role: 'Confirmations et rappels' },
    { nom: 'Passerelle', role: 'Point d’entrée unique de l’application' },
  ];
}
