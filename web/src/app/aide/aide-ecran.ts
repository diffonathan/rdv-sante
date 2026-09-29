import { Component, input, signal } from '@angular/core';

/**
 * Le mode d'emploi d'un écran, replié par défaut.
 *
 * <p>Replié et non affiché : celui qui connaît l'outil ne doit pas relire
 * l'explication tous les jours, et celui qui le découvre doit la trouver sans
 * demander à personne. Un bandeau d'aide permanent finit par être ignoré —
 * y compris quand il devient faux.
 */
@Component({
  selector: 'aide-ecran',
  imports: [],
  template: `
    <div class="aide">
      <button
        class="aide-bascule"
        [attr.aria-expanded]="ouvert()"
        (click)="ouvert.set(!ouvert())"
      >
        {{ ouvert() ? '−' : '?' }} Comment ça marche
      </button>

      @if (ouvert()) {
        <div class="aide-corps">
          @for (ligne of lignes(); track ligne) {
            <p>{{ ligne }}</p>
          }
        </div>
      }
    </div>
  `,
})
export class AideEcran {
  readonly lignes = input.required<string[]>();
  readonly ouvert = signal(false);
}
