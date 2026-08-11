import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Une pastille dans une case : une séance prescrite, une séance de force, ou une sortie faite.
 *
 * <p>L'ordre des champs est l'ordre de lecture. On lit d'abord l'icône (le type), puis la
 * <b>valeur</b> — c'est elle qu'on vient chercher. Le reste ne survit pas à une case de 45 px de
 * large et n'a donc pas à y figurer.</p>
 */
export interface MonthChip {
  /** Couleur (token CSS) portée par le type de séance. */
  color: string;
  icon: string;
  /**
   * Ce qu'on lit en premier : « 12 km », « 55 min », « ≈ 10 km ». Vide quand rien n'est chiffré —
   * la pastille se réduit alors à son icône, ce qui reste une information (« il y a quelque
   * chose ce jour-là »). Jamais un mot tronqué.
   */
  value: string;
  /** Séance clé (seuil, VMA, sortie longue, course) : elle porte la couleur pleine. */
  strong: boolean;
  /** Sortie réellement effectuée — se lit d'un coup d'œil, sans compter les couleurs. */
  done: boolean;
  title: string;
}

export interface MonthDay {
  date: string;
  dayNum: number;
  isToday: boolean;
  /** Faux pour les jours de débordement du mois voisin. */
  inMonth: boolean;
  chips: MonthChip[];
  unavailable: boolean;
}

export interface MonthWeek {
  /** Numéro ISO de semaine, dans la gouttière de gauche comme sur un vrai calendrier. */
  weekNumber: number;
  days: MonthDay[];
  /**
   * Volume de la semaine, déjà mis en forme (« 42 »), affiché sous son numéro. Absent quand la
   * semaine ne porte rien de chiffrable — on n'écrit pas « 0 » sur une semaine vide, ça se voit.
   */
  totalKm?: string;
  /** Ce total est-il du réalisé ? Il prend alors la couleur du fait, jamais celle du prévu. */
  totalDone?: boolean;
  /** Ce que le total compte, en toutes lettres (lecteurs d'écran et appui long). */
  totalTitle?: string;
}

/**
 * Grille mensuelle de l'athlète — l'écran d'ouverture du portail.
 *
 * <p>En arrivant, un athlète veut savoir ce qu'il a devant lui : le mois entier, d'un regard,
 * avec la charge de chaque jour. L'agenda hebdomadaire répondait à « quoi aujourd'hui », jamais
 * à « à quoi ressemble mon mois » — la question qu'on se pose en ouvrant l'application.</p>
 *
 * <p><b>Ce que « d'un regard » impose.</b> Une case de calendrier mensuel fait environ 45 px de
 * large sur un téléphone. Tout ce qui n'y tient pas doit en sortir, pas rétrécir : la grille
 * affichait jusqu'à trois pastilles de deux lignes à 9 px — sous le plancher de lisibilité que le
 * produit s'impose (11 px) — et repliait le type de séance sur ses quatre premières lettres,
 * « Endu », « Récu », « Frac ». On ne lisait plus, on devinait. La case porte désormais une
 * icône (le type se reconnaît sans être lu), une valeur chiffrée, et rien d'autre ; le détail
 * complet reste à un tap, dans la feuille du jour.</p>
 *
 * <p>Composant purement présentationnel : il reçoit des cases prêtes à peindre et n'émet que le
 * jour touché. Toute la logique (chargement, filtres, feuilles) reste dans l'écran hôte.</p>
 */
@Component({
  selector: 'app-athlete-month-grid',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="mg">
      <div class="mg-head" aria-hidden="true">
        <span class="mg-gutter"></span>
        @for (d of weekdays; track d) { <span class="mg-wd">{{ d }}</span> }
      </div>

      @for (w of weeks(); track w.weekNumber) {
        <div class="mg-week">
          <span class="mg-gut" [title]="w.totalTitle || ('Semaine ' + w.weekNumber)">
            <span class="mg-wn">S{{ w.weekNumber }}</span>
            @if (w.totalKm) {
              <span class="mg-km" [class.mg-km--done]="w.totalDone">{{ w.totalKm }}</span>
            }
          </span>
          @for (day of w.days; track day.date) {
            <button type="button" class="cell"
                    [class.cell--out]="!day.inMonth"
                    [class.cell--today]="day.isToday"
                    [class.cell--empty]="day.chips.length === 0"
                    [attr.aria-label]="ariaFor(day)"
                    (click)="dayPicked.emit(day.date)">
              <span class="cell-n">{{ day.dayNum }}</span>
              @if (day.unavailable) { <span class="cell-off"><app-icon name="ban" [size]="11" /></span> }
              <span class="cell-items">
                @for (c of day.chips.slice(0, 2); track $index) {
                  <span class="chip" [class.chip--done]="c.done" [class.chip--strong]="c.strong"
                        [style.--c]="c.color" [title]="c.title">
                    <app-icon class="chip-i" [name]="c.done ? 'check' : c.icon" [size]="11" />
                    @if (c.value) { <span class="chip-v">{{ c.value }}</span> }
                  </span>
                }
                @if (day.chips.length > 2) {
                  <span class="chip chip--more">+{{ day.chips.length - 2 }}</span>
                }
              </span>
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    /* Gouttière de semaine + sept colonnes égales. minmax(0,1fr) et non 1fr : sans lui, une
       pastille un peu large pousse sa colonne et le mois cesse d'être une grille. */
    .mg-head, .mg-week { display: grid; grid-template-columns: 26px repeat(7, minmax(0, 1fr)); gap: 3px; }
    .mg { display: flex; flex-direction: column; gap: 3px; }
    .mg-head { margin-bottom: 2px; }
    .mg-wd { text-align: center; font-size: var(--text-2xs); font-weight: 700; color: var(--ink-3); text-transform: lowercase; }

    /* Gouttière : le numéro de semaine, et sous lui son volume. Le mois répond alors à « quelle
       semaine ai-je chargée » sans ouvrir un seul jour. */
    .mg-gut { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 1px; }
    .mg-wn { font-size: var(--text-2xs); color: var(--ink-4); font-variant-numeric: tabular-nums; line-height: 1; }
    .mg-km { font-size: var(--text-2xs); font-weight: 800; color: var(--ink-2); font-variant-numeric: tabular-nums; line-height: 1; }
    /* Réalisé et prévu ne portent pas le même poids : un total qui compte ce qui a été couru se
       lit dans la couleur du fait, sinon les deux se confondent d'une semaine à l'autre. */
    .mg-km--done { color: var(--dari-teal); }

    .cell {
      display: flex; flex-direction: column; align-items: stretch; gap: 3px;
      min-height: 68px; padding: 3px; position: relative;
      background: var(--paper); border: 1px solid var(--hairline); border-radius: var(--radius-sm);
      cursor: pointer; text-align: left; -webkit-tap-highlight-color: transparent;
    }
    .cell:active { transform: scale(0.97); }
    .cell:focus-visible { outline: 2px solid var(--primary); outline-offset: 1px; }
    .cell--out { opacity: 0.45; }
    .cell--empty { background: var(--canvas); }
    .cell--today { border-color: var(--primary); box-shadow: 0 0 0 1px var(--primary); }
    .cell-n {
      font-size: var(--text-2xs); font-weight: 700; color: var(--ink-3);
      text-align: center; line-height: 1.2; font-variant-numeric: tabular-nums;
    }
    /* Aujourd'hui : une pastille pleine. Un liseré seul se perd dès qu'un jour voisin porte une
       séance colorée — or c'est la case qu'on cherche en ouvrant l'application. */
    .cell--today .cell-n {
      color: var(--paper); background: var(--primary);
      border-radius: var(--radius-full); align-self: center;
      min-width: 18px; padding: 0 5px;
    }
    .cell-off { position: absolute; top: 3px; right: 3px; color: var(--ink-4); line-height: 0; }
    .cell-items { display: flex; flex-direction: column; gap: 2px; }

    /* Pastille : l'icône, puis la valeur sous elle.
       Elles ne tiennent pas côte à côte — une colonne de mois fait ~40 px de large sur un
       téléphone, et « 12 km » en occupe déjà 30. C'est ce qui avait conduit à écrire les deux
       lignes en 9 px ; le défaut n'était pas d'empiler, il était de rétrécir. On empile donc, au
       plancher de lisibilité du produit. */
    .chip {
      display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 0;
      padding: 2px 3px; border-radius: 4px; overflow: hidden; min-height: 17px;
      background: color-mix(in srgb, var(--c, var(--primary)) 16%, var(--paper));
      border-left: 3px solid var(--c, var(--primary));
      color: var(--ink); line-height: 1.15;
    }
    .chip-i { flex: none; color: var(--c, var(--primary)); }
    .chip-v { font-size: var(--text-2xs); font-weight: 800; font-variant-numeric: tabular-nums; white-space: nowrap; }
    /* Séance clé : la couleur remplit la pastille. Le mois montre alors ses points durs sans
       qu'on ait à lire un seul mot. */
    .chip--strong { background: color-mix(in srgb, var(--c, var(--primary)) 34%, var(--paper)); }
    /* Réalisé : coche à la place de l'icône de type, et trame pleine. Le fait et le prévu ne se
       distinguaient que par une nuance de fond, invisible sur un écran au soleil. */
    .chip--done { background: color-mix(in srgb, var(--c, var(--primary)) 30%, var(--paper)); }
    .chip--done .chip-i { color: var(--ink); }
    .chip--more {
      font-size: var(--text-2xs); font-weight: 800; color: var(--ink-3);
      background: var(--paper-sunk); border-left-color: var(--ink-4);
    }

    /* Écran étroit (≤ 360 px) : la colonne tombe sous 40 px, et « 12,4 km » n'y tient plus sans
       être rogné. Mieux vaut ne rien écrire qu'écrire un chiffre coupé — l'icône dit le type, et
       la feuille du jour, à un tap, dit le reste. */
    @media (max-width: 360px) {
      .chip-v { display: none; }
    }

    /* À partir d'une tablette, la grille respire : cases plus hautes et texte lisible de loin. */
    @media (min-width: 600px) {
      .cell { min-height: 92px; padding: 4px; }
      .chip { min-height: 20px; padding: 3px 5px; }
      .chip-v { font-size: var(--text-xs); }
      .cell-n { font-size: var(--text-xs); }
    }
  `],
})
export class AthleteMonthGridComponent {
  readonly weeks = input.required<MonthWeek[]>();
  readonly dayPicked = output<string>();

  readonly weekdays = ['lun', 'mar', 'mer', 'jeu', 'ven', 'sam', 'dim'];

  /** Le contenu d'une case tient en une phrase pour les lecteurs d'écran. */
  ariaFor(day: MonthDay): string {
    const date = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
      .format(new Date(day.date + 'T00:00:00'));
    if (day.chips.length === 0) {
      return `${date} — rien de prévu`;
    }
    return `${date} — ${day.chips.map((c) => c.title).join(', ')}`;
  }
}
