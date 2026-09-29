import { Routes } from '@angular/router';

/**
 * Trois écrans, trois publics : le patient qui réserve, le secrétariat qui
 * pilote la journée, et l'écran affiché dans la salle d'attente.
 *
 * Chargement différé : l'écran de salle d'attente tourne sur une télévision
 * qu'on n'éteint jamais, il n'a aucune raison d'embarquer le formulaire de
 * réservation.
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
  { path: '**', redirectTo: '' },
];
