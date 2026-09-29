/**
 * Les contrats du service `rendezvous`, côté navigateur.
 *
 * Écrits à la main plutôt que générés depuis OpenAPI : le service n'expose que
 * neuf points d'entrée, et une génération ajouterait une étape de build pour
 * une centaine de lignes. Au-delà, la génération deviendrait le bon choix.
 */

export interface Clinique {
  id: string;
  nom: string;
  ville: string;
  adresse: string;
  telephone: string;
}

export interface Praticien {
  id: string;
  nomComplet: string;
  specialite: string;
  cliniqueId: string;
  cliniqueNom: string;
}

export interface Creneau {
  id: string;
  debut: string;
  fin: string;
  dureeMinutes: number;
  praticienId: string;
  praticienNom: string;
}

export type StatutRendezVous = 'CONFIRME' | 'ANNULE' | 'HONORE' | 'ABSENT';

export interface RendezVous {
  id: string;
  statut: StatutRendezVous;
  debut: string;
  fin: string;
  patientId: string;
  patientNom: string;
  praticienNom: string;
  specialite: string;
  cliniqueNom: string;
  cliniqueVille: string;
  motifAnnulation: string | null;
}

export type EtatFile = 'EN_ATTENTE' | 'APPELE' | 'EN_CONSULTATION' | 'TERMINE';

export interface EntreeFile {
  id: string;
  /** Rang dans la file, à partir de 1. Vaut 0 dès que le patient est appelé. */
  rang: number;
  rendezVousId: string;
  patientNom: string;
  praticienNom: string;
  arriveLe: string;
  appeleLe: string | null;
  etat: EtatFile;
}

export interface EtatFileVue {
  cliniqueId: string;
  enAttente: EntreeFile[];
  appeles: EntreeFile[];
  nombreEnAttente: number;
  genereLe: string;
}

export interface DemandeReservation {
  creneauId: string;
  patientId: string;
  patientNom: string;
  patientTelephone: string;
}

/**
 * Le format d'erreur du service (RFC 9457).
 *
 * `champs` n'est présent que sur les erreurs de validation ; `creneauId`
 * seulement sur un conflit de réservation. C'est ce qui permet à l'interface
 * de proposer un autre créneau au lieu d'afficher « une erreur est survenue ».
 */
export interface ProblemeApi {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  champs?: Record<string, string>;
  creneauId?: string;
}
