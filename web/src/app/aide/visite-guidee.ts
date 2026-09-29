import { Component, computed, signal } from '@angular/core';

export interface EtapeVisite {
  titre: string;
  /** Un paragraphe par élément. Pas de HTML : le contenu reste du texte. */
  paragraphes: string[];
  /** Encadré optionnel, pour l'essentiel à retenir. */
  aRetenir?: string;
}

/**
 * La visite de première connexion.
 *
 * <p>Elle s'ouvre toute seule la première fois, puis plus jamais — sauf si on
 * clique sur « Aide ». Un outil qu'on doit expliquer de vive voix à chaque
 * nouvel utilisateur n'est pas fini ; celui-ci porte son mode d'emploi.
 *
 * <p>La première étape n'explique pas l'écran : elle explique le
 * <strong>problème</strong>. Quelqu'un qui ne sait pas pourquoi l'outil existe
 * ne retient pas comment il marche.
 */
@Component({
  selector: 'visite-guidee',
  imports: [],
  templateUrl: './visite-guidee.html',
})
export class VisiteGuidee {
  private static readonly CLE = 'rdv-sante:visite-vue';

  readonly ouverte = signal(false);
  readonly index = signal(0);

  readonly etapes: EtapeVisite[] = [
    {
      titre: 'Le problème',
      paragraphes: [
        'Au Maroc, prendre rendez-vous chez un spécialiste passe encore par un appel au secrétariat, un carnet papier ou un groupe WhatsApp. Le patient se déplace, puis attend sans savoir combien de personnes le précèdent — parfois deux heures pour une consultation de quinze minutes.',
        'Côté accueil, la secrétaire jongle entre le téléphone qui sonne et la salle qui se remplit. Elle est le seul endroit où l’information existe, et elle ne peut pas être à deux endroits à la fois.',
      ],
      aRetenir:
        'Le cœur du produit n’est pas la réservation — c’est la file d’attente en temps réel. C’est elle qui supprime l’attente debout dans un couloir.',
    },
    {
      titre: 'Prendre rendez-vous',
      paragraphes: [
        'Le patient choisit une clinique, un praticien, un jour, puis un horaire parmi ceux qui restent libres. La confirmation est immédiate.',
        'Si quelqu’un réserve le même créneau pendant qu’il remplit le formulaire, l’application le lui dit et lui propose les horaires encore disponibles — sa saisie n’est pas perdue.',
      ],
    },
    {
      titre: 'Le secrétariat',
      paragraphes: [
        'L’accueil voit les rendez-vous du jour à gauche, la file d’attente à droite. Un clic sur « Arrivé » fait entrer le patient dans la file ; un clic sur « Appeler le suivant » le fait passer en consultation.',
        'L’écran se met à jour tout seul : aucune touche à presser pour rafraîchir, même si une collègue agit depuis un autre poste.',
      ],
      aRetenir:
        'Le rang d’un patient n’est jamais saisi à la main. Il se déduit de l’heure d’arrivée, donc il ne peut pas se désynchroniser.',
    },
    {
      titre: 'La salle d’attente',
      paragraphes: [
        'Cet écran est fait pour une télévision accrochée au mur : gros caractères, une seule information importante — qui est appelé — et les trois suivants en dessous.',
        'Aucune interaction : personne ne touche cet écran. Il se contente d’afficher ce que le secrétariat décide.',
      ],
    },
    {
      titre: 'Essayez la démonstration',
      paragraphes: [
        'Ouvrez « Secrétariat » et « Salle d’attente » dans deux fenêtres côte à côte. Cliquez sur « Appeler le suivant » d’un côté : l’autre écran change immédiatement, sans rechargement.',
        'Les cliniques, les praticiens et les patients sont inventés. Aucune donnée de santé réelle n’est traitée.',
      ],
    },
    {
      titre: 'Ce qu’il y a derrière',
      paragraphes: [
        'L’application n’est pas un bloc unique. Ce sont quatre programmes séparés qui tournent chacun de leur côté et se passent des messages : l’un gère les rendez-vous, un autre les patients, un troisième les rappels, et le dernier sert de porte d’entrée.',
        'L’intérêt est concret : si celui qui envoie les rappels s’arrête, on continue de prendre des rendez-vous. Il rattrape son retard à son retour, sans rien perdre.',
      ],
      aRetenir:
        'Trois difficultés y sont traitées : deux personnes qui réservent la même seconde, un rappel qui se perd, et un message envoyé deux fois.',
    },
    {
      titre: 'Vous recrutez ?',
      paragraphes: [
        'L’onglet « Sous le capot » liste les technologies utilisées et raconte, en mots simples, ce qui a été difficile et comment je m’y suis pris.',
        'Le code est public sur GitHub, avec ses tests et la vérification automatique qui se lance à chaque modification.',
      ],
      aRetenir:
        'Y compris une erreur que j’ai commise, comment je l’ai trouvée et ce que j’ai mis en place pour qu’elle ne revienne pas.',
    },
  ];

  readonly etape = computed(() => this.etapes[this.index()]);
  readonly premiere = computed(() => this.index() === 0);
  readonly derniere = computed(() => this.index() === this.etapes.length - 1);

  constructor() {
    if (!this.dejaVue()) {
      this.ouverte.set(true);
    }
  }

  ouvrir(): void {
    this.index.set(0);
    this.ouverte.set(true);
  }

  fermer(): void {
    this.ouverte.set(false);
    this.marquerVue();
  }

  suivant(): void {
    if (this.derniere()) {
      this.fermer();
    } else {
      this.index.update((i) => i + 1);
    }
  }

  precedent(): void {
    this.index.update((i) => Math.max(0, i - 1));
  }

  allerA(i: number): void {
    this.index.set(i);
  }

  surTouche(evenement: KeyboardEvent): void {
    if (evenement.key === 'Escape') {
      this.fermer();
    } else if (evenement.key === 'ArrowRight') {
      this.suivant();
    } else if (evenement.key === 'ArrowLeft') {
      this.precedent();
    }
  }

  private dejaVue(): boolean {
    try {
      return localStorage.getItem(VisiteGuidee.CLE) === 'oui';
    } catch {
      // Navigation privée : la visite se rouvrira à chaque session. C'est
      // moins gênant que de ne jamais l'afficher.
      return false;
    }
  }

  private marquerVue(): void {
    try {
      localStorage.setItem(VisiteGuidee.CLE, 'oui');
    } catch {
      /* stockage indisponible */
    }
  }
}
