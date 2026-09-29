import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, NgZone, inject, signal } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

import type {
  Clinique,
  Inscription,
  Patient,
  Creneau,
  DemandeReservation,
  EntreeFile,
  EtatFileVue,
  Praticien,
  ProblemeApi,
  RendezVous,
} from './modeles';

/** Erreur métier remontée telle quelle à l'interface, avec son corps RFC 9457. */
export class ErreurApi extends Error {
  constructor(
    readonly statut: number,
    readonly probleme: ProblemeApi,
  ) {
    super(probleme.detail ?? probleme.title ?? `HTTP ${statut}`);
  }

  /** Le créneau vient d'être pris : l'interface doit proposer autre chose. */
  get estConflit(): boolean {
    return this.statut === 409;
  }

  get erreursDeChamp(): Record<string, string> {
    return this.probleme.champs ?? {};
  }
}

@Injectable({ providedIn: 'root' })
export class RdvApi {
  private readonly http = inject(HttpClient);
  private readonly zone = inject(NgZone);

  cliniques(): Observable<Clinique[]> {
    return this.http.get<Clinique[]>('/api/cliniques').pipe(catchError(traduire));
  }

  praticiens(cliniqueId: string): Observable<Praticien[]> {
    return this.http
      .get<Praticien[]>(`/api/cliniques/${cliniqueId}/praticiens`)
      .pipe(catchError(traduire));
  }

  /** @param jour au format `2026-10-03`. */
  creneaux(praticienId: string, jour: string): Observable<Creneau[]> {
    return this.http
      .get<Creneau[]>(`/api/praticiens/${praticienId}/creneaux`, { params: { jour } })
      .pipe(catchError(traduire));
  }

  /**
   * Enregistre le patient, ou retrouve celui qui porte déjà ce numéro.
   *
   * <p>Le service est idempotent : le front l'appelle avant chaque
   * réservation sans avoir à savoir si le dossier existe.
   */
  enregistrerPatient(demande: Inscription): Observable<Patient> {
    return this.http.post<Patient>('/api/patients', demande).pipe(catchError(traduire));
  }

  /** L'agenda du jour d'une clinique — l'écran du secrétariat. */
  agenda(cliniqueId: string, jour: string): Observable<RendezVous[]> {
    return this.http
      .get<RendezVous[]>(`/api/cliniques/${cliniqueId}/rendez-vous`, { params: { jour } })
      .pipe(catchError(traduire));
  }

  reserver(demande: DemandeReservation): Observable<RendezVous> {
    return this.http.post<RendezVous>('/api/rendez-vous', demande).pipe(catchError(traduire));
  }

  annuler(rendezVousId: string, motif: string): Observable<RendezVous> {
    return this.http
      .post<RendezVous>(`/api/rendez-vous/${rendezVousId}/annulation`, { motif })
      .pipe(catchError(traduire));
  }

  file(cliniqueId: string): Observable<EtatFileVue> {
    return this.http
      .get<EtatFileVue>(`/api/cliniques/${cliniqueId}/file`)
      .pipe(catchError(traduire));
  }

  enregistrerArrivee(cliniqueId: string, rendezVousId: string): Observable<EntreeFile> {
    return this.http
      .post<EntreeFile>(`/api/cliniques/${cliniqueId}/file/arrivees`, { rendezVousId })
      .pipe(catchError(traduire));
  }

  appelerSuivant(cliniqueId: string): Observable<EntreeFile | null> {
    return this.http
      .post<EntreeFile | null>(`/api/cliniques/${cliniqueId}/file/suivant`, {})
      .pipe(catchError(traduire));
  }

  terminer(cliniqueId: string, entreeId: string): Observable<EntreeFile> {
    return this.http
      .post<EntreeFile>(`/api/cliniques/${cliniqueId}/file/${entreeId}/fin`, {})
      .pipe(catchError(traduire));
  }

  /**
   * Le flux temps réel de la file (SSE).
   *
   * <p>`EventSource` est natif : la reconnexion après coupure réseau est gérée
   * par le navigateur, sans une ligne de code. C'est tout l'intérêt de SSE ici
   * plutôt qu'un WebSocket, qu'il aurait fallu reconnecter à la main.
   *
   * <p>`zone.run` est nécessaire : l'événement arrive hors du contexte Angular,
   * et sans lui le signal changerait sans que l'écran ne se redessine.
   *
   * @returns un signal de l'état courant, et de quoi fermer le flux.
   */
  suivreFile(cliniqueId: string) {
    const etat = signal<EtatFileVue | null>(null);
    const erreur = signal(false);

    const source = new EventSource(`/api/cliniques/${cliniqueId}/file/flux`);

    source.addEventListener('file', (evenement) => {
      const donnees = JSON.parse((evenement as MessageEvent).data) as EtatFileVue;
      this.zone.run(() => {
        etat.set(donnees);
        erreur.set(false);
      });
    });

    // Le navigateur retentera tout seul ; on signale juste la coupure à l'écran
    // pour que personne ne lise une file figée en la croyant à jour.
    source.onerror = () => this.zone.run(() => erreur.set(true));

    return { etat, erreur, fermer: () => source.close() };
  }
}

function traduire(reponse: HttpErrorResponse) {
  const corps = (reponse.error ?? {}) as ProblemeApi;
  return throwError(() => new ErreurApi(reponse.status, corps));
}
