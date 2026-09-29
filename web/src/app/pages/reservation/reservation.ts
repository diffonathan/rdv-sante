import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { switchMap } from 'rxjs/operators';

import { ErreurApi, RdvApi } from '../../api/rdv-api';
import type { Clinique, Creneau, Praticien, RendezVous } from '../../api/modeles';

@Component({
  selector: 'page-reservation',
  imports: [FormsModule, DatePipe],
  templateUrl: './reservation.html',
})
export class Reservation {
  private readonly api = inject(RdvApi);

  readonly cliniques = signal<Clinique[]>([]);
  readonly praticiens = signal<Praticien[]>([]);
  readonly creneaux = signal<Creneau[]>([]);

  readonly cliniqueId = signal('');
  readonly praticienId = signal('');
  readonly jour = signal(this.demain());
  readonly creneauChoisi = signal<Creneau | null>(null);

  prenom = '';
  nom = '';
  telephone = '';

  readonly chargementCreneaux = signal(false);
  readonly envoiEnCours = signal(false);
  readonly erreur = signal<string | null>(null);
  readonly erreursChamp = signal<Record<string, string>>({});
  readonly confirmation = signal<RendezVous | null>(null);

  /** Le premier jour réservable est demain : les créneaux du jour sont déjà entamés. */
  readonly jourMinimum = this.demain();

  readonly peutEnvoyer = computed(
    () => this.creneauChoisi() !== null && !this.envoiEnCours(),
  );

  constructor() {
    this.api.cliniques().subscribe({
      next: (liste) => {
        this.cliniques.set(liste);
        if (liste.length) {
          this.choisirClinique(liste[0].id);
        }
      },
      error: () =>
        this.erreur.set(
          'Le service est injoignable. Vérifiez que la passerelle et les services sont démarrés.',
        ),
    });
  }

  choisirClinique(id: string): void {
    this.cliniqueId.set(id);
    this.praticienId.set('');
    this.praticiens.set([]);
    this.creneaux.set([]);
    this.creneauChoisi.set(null);
    if (!id) {
      return;
    }
    this.api.praticiens(id).subscribe((liste) => {
      this.praticiens.set(liste);
      if (liste.length) {
        this.choisirPraticien(liste[0].id);
      }
    });
  }

  choisirPraticien(id: string): void {
    this.praticienId.set(id);
    this.creneauChoisi.set(null);
    this.rechargerCreneaux();
  }

  choisirJour(valeur: string): void {
    this.jour.set(valeur);
    this.creneauChoisi.set(null);
    this.rechargerCreneaux();
  }

  rechargerCreneaux(): void {
    const praticien = this.praticienId();
    if (!praticien) {
      this.creneaux.set([]);
      return;
    }
    this.chargementCreneaux.set(true);
    this.api.creneaux(praticien, this.jour()).subscribe({
      next: (liste) => {
        this.creneaux.set(liste);
        this.chargementCreneaux.set(false);
      },
      error: () => {
        this.creneaux.set([]);
        this.chargementCreneaux.set(false);
      },
    });
  }

  /**
   * Deux appels enchaînés : on enregistre le patient, puis on réserve avec
   * l'identifiant qu'il nous rend.
   *
   * <p>Le service « patients » est idempotent sur le numéro de téléphone :
   * réserver trois fois depuis trois navigateurs ne crée pas trois dossiers.
   * Le front n'a donc pas à savoir si le patient existe déjà — il demande, et
   * il reçoit l'identifiant dans les deux cas.
   */
  reserver(): void {
    const creneau = this.creneauChoisi();
    if (!creneau) {
      return;
    }
    this.envoiEnCours.set(true);
    this.erreur.set(null);
    this.erreursChamp.set({});

    this.api
      .enregistrerPatient({
        nom: this.nom.trim(),
        prenom: this.prenom.trim(),
        telephone: this.telephone.trim(),
      })
      .pipe(
        switchMap((patient) =>
          this.api.reserver({
            creneauId: creneau.id,
            patientId: patient.id,
            patientNom: patient.nomComplet,
            patientTelephone: patient.telephone,
          }),
        ),
      )
      .subscribe({
        next: (rdv) => {
          this.confirmation.set(rdv);
          this.envoiEnCours.set(false);
          this.rechargerCreneaux();
        },
        error: (e: ErreurApi) => {
          this.envoiEnCours.set(false);
          this.erreursChamp.set(e.erreursDeChamp);

          if (e.estConflit) {
            // Quelqu'un a réservé pendant qu'on remplissait le formulaire. On
            // recharge la liste et on garde la saisie : il suffit de cliquer
            // sur un autre horaire.
            this.erreur.set(
              'Ce créneau vient d’être pris par quelqu’un d’autre. En voici d’autres.',
            );
            this.creneauChoisi.set(null);
            this.rechargerCreneaux();
          } else {
            this.erreur.set(e.message);
          }
        },
      });
  }

  recommencer(): void {
    this.confirmation.set(null);
    this.creneauChoisi.set(null);
    this.erreur.set(null);
    this.rechargerCreneaux();
  }

  private demain(): string {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().slice(0, 10);
  }
}
