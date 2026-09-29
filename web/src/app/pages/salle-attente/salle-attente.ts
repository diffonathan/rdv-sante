import { Component, OnDestroy, computed, inject, signal } from '@angular/core';

import { RdvApi } from '../../api/rdv-api';
import type { Clinique } from '../../api/modeles';

type Abonnement = ReturnType<RdvApi['suivreFile']>;

/**
 * L'écran affiché dans la salle d'attente.
 *
 * <p>Pensé pour être lu debout, à trois mètres, par quelqu'un qui jette un
 * coup d'œil toutes les deux minutes : gros caractères, une seule information
 * importante — qui est appelé — et la suite en dessous. Aucune interaction :
 * personne ne touche cet écran.
 */
@Component({
  selector: 'page-salle-attente',
  imports: [],
  templateUrl: './salle-attente.html',
})
export class SalleAttente implements OnDestroy {
  private readonly api = inject(RdvApi);

  readonly cliniques = signal<Clinique[]>([]);
  readonly cliniqueId = signal('');

  private readonly abonnement = signal<Abonnement | null>(null);

  readonly file = computed(() => this.abonnement()?.etat() ?? null);
  readonly fluxCoupe = computed(() => this.abonnement()?.erreur() ?? false);

  readonly cliniqueNom = computed(
    () => this.cliniques().find((c) => c.id === this.cliniqueId())?.nom ?? '',
  );

  /** Le dernier patient appelé : c'est lui qui occupe le grand cartouche. */
  readonly appele = computed(() => this.file()?.appeles.at(-1) ?? null);

  /** Les trois suivants, pas plus : au-delà, plus personne ne lit. */
  readonly suivants = computed(() => this.file()?.enAttente.slice(0, 3) ?? []);

  constructor() {
    this.api.cliniques().subscribe((liste) => {
      this.cliniques.set(liste);
      if (liste.length) {
        this.choisirClinique(liste[0].id);
      }
    });
  }

  choisirClinique(id: string): void {
    this.abonnement()?.fermer();
    this.cliniqueId.set(id);
    this.abonnement.set(this.api.suivreFile(id));
  }

  ngOnDestroy(): void {
    this.abonnement()?.fermer();
  }
}
