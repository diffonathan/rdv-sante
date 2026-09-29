import { Routes } from '@angular/router';

/**
 * Quatre écrans, quatre publics : le patient qui réserve, le secrétariat qui
 * pilote la journée, l'écran de la salle d'attente, et le lecteur technique.
 *
 * Chargement différé : l'écran de salle d'attente tourne sur une télévision
 * qu'on n'éteint jamais, il n'a aucune raison d'embarquer le formulaire de
 * réservation ni la page d'architecture.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/reservation/reservation').then((m) => m.Reservation),
    title: 'Prendre rendez-vous — RDV Santé',
  },
  {
    path: 'secretariat',
    loadComponent: () => import('./pages/secretariat/secretariat').then((m) => m.Secretariat),
    title: 'Secrétariat — RDV Santé',
  },
  {
    path: 'salle-attente',
    loadComponent: () =>
      import('./pages/salle-attente/salle-attente').then((m) => m.SalleAttente),
    title: 'Salle d’attente — RDV Santé',
  },
  {
    path: 'technique',
    loadComponent: () => import('./pages/technique/technique').then((m) => m.Technique),
    title: 'Sous le capot — RDV Santé',
  },
  { path: '**', redirectTo: '' },
];
