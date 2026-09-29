import { DatePipe } from '@angular/common';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';

import { AideEcran } from '../../aide/aide-ecran';
import { ErreurApi, RdvApi } from '../../api/rdv-api';
import type { Clinique, RendezVous } from '../../api/modeles';

type Abonnement = ReturnType<RdvApi['suivreFile']>;

@Component({
  selector: 'page-secretariat',
  imports: [DatePipe, AideEcran],
  templateUrl: './secretariat.html',
})
export class Secretariat implements OnDestroy {
  private readonly api = inject(RdvApi);

  readonly cliniques = signal<Clinique[]>([]);
  readonly cliniqueId = signal('');
  readonly agenda = signal<RendezVous[]>([]);
  readonly message = signal<string | null>(null);
  readonly erreur = signal<string | null>(null);

  /** Le flux SSE en cours. Ses propres signaux alimentent directement l'écran :
      aucune recopie, aucune scrutation. */
  private readonly abonnement = signal<Abonnement | null>(null);

  readonly file = computed(() => this.abonnement()?.etat() ?? null);
  readonly fluxCoupe = computed(() => this.abonnement()?.erreur() ?? false);

  /** Les rendez-vous du jour qui ne sont pas encore entrés dans la file. */
  readonly aPointer = computed(() => {
    const etat = this.file();
    if (!etat) {
      return this.agenda();
    }
    const dejaLa = new Set([
      ...etat.enAttente.map((e) => e.rendezVousId),
      ...etat.appeles.map((e) => e.rendezVousId),
    ]);
    return this.agenda().filter((r) => !dejaLa.has(r.id));
  });

  constructor() {
    this.api.cliniques().subscribe({
      next: (liste) => {
        this.cliniques.set(liste);
        if (liste.length) {
          this.choisirClinique(liste[0].id);
        }
      },
      error: () =>
        this.erreur.set('Le service de rendez-vous est injoignable.'),
    });
  }

  choisirClinique(id: string): void {
    this.detacherFlux();
    this.cliniqueId.set(id);
    this.rechargerAgenda();
    this.abonnement.set(this.api.suivreFile(id));
  }

  rechargerAgenda(): void {
    const id = this.cliniqueId();
    if (!id) {
      return;
    }
    const aujourdhui = new Date().toISOString().slice(0, 10);
    this.api.agenda(id, aujourdhui).subscribe({
      next: (liste) => this.agenda.set(liste),
      error: () => this.agenda.set([]),
    });
  }

  pointerArrivee(rdv: RendezVous): void {
    this.erreur.set(null);
    this.api.enregistrerArrivee(this.cliniqueId(), rdv.id).subscribe({
      // Rien à rafraîchir à la main : le serveur diffuse la nouvelle file
      // par SSE, et `file` la reçoit.
      next: () => this.message.set(`${rdv.patientNom} est entré dans la file.`),
      error: (e: ErreurApi) => this.erreur.set(e.message),
    });
  }

  appelerSuivant(): void {
    this.erreur.set(null);
    this.api.appelerSuivant(this.cliniqueId()).subscribe({
      next: (entree) =>
        this.message.set(
          entree ? `${entree.patientNom} est appelé.` : 'La salle d’attente est vide.',
        ),
      error: (e: ErreurApi) => this.erreur.set(e.message),
    });
  }

  terminer(entreeId: string): void {
    this.erreur.set(null);
    this.api.terminer(this.cliniqueId(), entreeId).subscribe({
      next: () => this.message.set('Consultation terminée.'),
      error: (e: ErreurApi) => this.erreur.set(e.message),
    });
  }

  private detacherFlux(): void {
    this.abonnement()?.fermer();
    this.abonnement.set(null);
  }

  ngOnDestroy(): void {
    // Sans cela, quitter l'écran laisserait la connexion SSE ouverte côté
    // serveur jusqu'à ce que le navigateur la coupe de lui-même.
    this.detacherFlux();
  }
}
