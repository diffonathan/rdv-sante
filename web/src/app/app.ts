import { Component, viewChild } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { VisiteGuidee } from './aide/visite-guidee';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, VisiteGuidee],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  /** La visite s'ouvre seule à la première visite ; ce bouton la rouvre. */
  private readonly visite = viewChild.required(VisiteGuidee);

  ouvrirAide(): void {
    this.visite().ouvrir();
  }
}
