import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

interface Bloc {
  p: string | null;
  code: string | null;
}

interface Section {
  id: string;
  titre: string;
  blocs: Bloc[];
}

/**
 * La documentation technique, ouverte par un mot de passe.
 *
 * <p>Elle va plus loin que « Sous le capot », qui reste volontairement sans
 * jargon parce qu'il s'adresse à quelqu'un qui trie des candidatures. Ici on
 * peut nommer les choses.
 *
 * <p><strong>Le contenu n'est pas dans ce fichier, et c'est le point.</strong>
 * La tentation serait de l'écrire ici et de masquer la page tant que le mot de
 * passe n'est pas saisi. Cela ne protège rien : le texte partirait dans le
 * paquet JavaScript, et il suffirait de l'ouvrir pour tout lire sans jamais
 * taper le mot de passe. Le serveur ne le rend qu'après vérification.
 *
 * <p>Rien n'est gardé entre deux visites : recharger la page redemande le mot
 * de passe. C'est volontaire — conserver un jeton dans le navigateur
 * ajouterait une durée de vie à gérer et une chose de plus à révoquer, pour un
 * confort dont personne n'a besoin sur une page qu'on lit une fois.
 */
@Component({
  selector: 'page-documentation',
  imports: [FormsModule],
  templateUrl: './documentation.html',
})
export class Documentation {
  private readonly http = inject(HttpClient);

  readonly motDePasse = signal('');
  readonly sections = signal<Section[] | null>(null);
  readonly erreur = signal<string | null>(null);
  readonly enCours = signal(false);

  readonly depot = 'https://github.com/diffonathan/rdv-sante';

  ouvrir(): void {
    if (this.enCours()) return;

    this.enCours.set(true);
    this.erreur.set(null);

    this.http
      .post<Section[]>('/api/documentation', { motDePasse: this.motDePasse() })
      .subscribe({
        next: (sections) => {
          this.sections.set(sections);
          this.enCours.set(false);

          // Vidé dès que possible : il n'a plus de raison d'être en mémoire.
          this.motDePasse.set('');
        },
        error: (erreur: HttpErrorResponse) => {
          this.enCours.set(false);
          this.motDePasse.set('');

          // 0 signifie « la requête n'est jamais partie » : passerelle
          // éteinte, réseau coupé. Le dire évite de chercher un mot de passe
          // qui était peut-être le bon.
          this.erreur.set(
            erreur.status === 0
              ? 'Le serveur ne répond pas. La passerelle est-elle démarrée ?'
              : (erreur.error?.message ?? 'Mot de passe incorrect.'),
          );
        },
      });
  }

  numero(index: number): string {
    return String(index + 1).padStart(2, '0');
  }
}
