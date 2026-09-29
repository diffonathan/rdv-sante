import { Injectable } from '@angular/core';

/**
 * L'identifiant du « patient » de démonstration.
 *
 * <p>Le vrai service `patients` n'est pas encore branché : on génère donc un
 * identifiant stable par navigateur et on le garde. C'est suffisant pour
 * démontrer le parcours, et le jour où l'authentification arrive, seul ce
 * fichier change — le reste de l'application ne sait pas d'où vient l'id.
 */
@Injectable({ providedIn: 'root' })
export class IdentitePatient {
  private static readonly CLE = 'rdv-sante:patient-id';

  readonly id: string = this.lireOuCreer();

  private lireOuCreer(): string {
    try {
      const memorise = localStorage.getItem(IdentitePatient.CLE);
      if (memorise) {
        return memorise;
      }
      const nouveau = crypto.randomUUID();
      localStorage.setItem(IdentitePatient.CLE, nouveau);
      return nouveau;
    } catch {
      // Navigation privée ou stockage bloqué : l'identifiant ne vaut que pour
      // la visite en cours, ce qui reste utilisable.
      return crypto.randomUUID();
    }
  }
}
