import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';

/** Une pastille dans une case : une séance prescrite, une séance de force, ou une sortie faite. */
export interface MonthChip {
  /** Couleur (token CSS) portée par le type de séance. */
  color: string;
  icon: string;
  /** Ligne du haut : durée (« 50m », « 1h07 »). */
  duration: string;
  /** Ligne du bas : volume (« 12 km »). */
  volume: string;
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
}

/**
 * Grille mensuelle de l'athlète — l'écran d'ouverture du portail.
 *
 * <p>En arrivant, un athlète veut savoir ce qu'il a devant lui : le mois entier, d'un regard,
 * avec la charge de chaque jour. L'agenda hebdomadaire répondait à « quoi aujourd'hui », jamais
 * à « à quoi ressemble mon mois » — la question qu'on se pose en ouvrant l'application.</p>
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
          <span class="mg-wn">{{ w.weekNumber }}</span>
          @for (day of w.days; track day.date) {
            <button type="button" class="cell"
                    [class.cell--out]="!day.inMonth"
                    [class.cell--today]="day.isToday"
                    [class.cell--empty]="day.chips.length === 0"
                    [attr.aria-label]="ariaFor(day)"
                    (click)="dayPicked.emit(day.date)">
              <span class="cell-n">{{ day.dayNum }}</span>
              @if (day.unavailable) { <span class="cell-off"><app-icon name="ban" [size]="10" /></span> }
              <span class="cell-items">
                @for (c of day.chips.slice(0, 3); track $index) {
                  <span class="chip" [class.chip--done]="c.done" [style.--c]="c.color" [title]="c.title">
                    <span class="chip-d">{{ c.duration }}</span>
                    @if (c.volume) { <span class="chip-k">{{ c.volume }}</span> }
                  </span>
                }
                @if (day.chips.length > 3) {
                  <span class="chip chip--more">+{{ day.chips.length - 3 }}</span>
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
    /* Gouttière de numéros de semaine + sept colonnes égales. minmax(0,1fr) et non 1fr : sans
       lui, une pastille un peu large pousse sa colonne et le mois cesse d'être une grille. */
    .mg-head, .mg-week { display: grid; grid-template-columns: 18px repeat(7, minmax(0, 1fr)); gap: 3px; }
    .mg { display: flex; flex-direction: column; gap: 3px; }
    .mg-head { margin-bottom: 2px; }
    .mg-wd { text-align: center; font-size: var(--text-2xs); font-weight: 700; color: var(--ink-3); text-transform: lowercase; }
    .mg-wn { display: flex; align-items: center; justify-content: center; font-size: var(--text-2xs); color: var(--ink-4); font-variant-numeric: tabular-nums; }

    .cell {
      display: flex; flex-direction: column; align-items: stretch; gap: 2px;
      min-height: 62px; padding: 2px; position: relative;
      background: var(--paper); border: 1px solid var(--hairline); border-radius: var(--radius-sm);
      cursor: pointer; text-align: left; -webkit-tap-highlight-color: transparent;
    }
    .cell:active { transform: scale(0.97); }
    .cell:focus-visible { outline: 2px solid var(--primary); outline-offset: 1px; }
    .cell--out { opacity: 0.45; }
    .cell--empty { background: var(--canvas); }
    /* Le jour même se repère au liseré, pas à un aplat : les pastilles gardent leurs couleurs. */
    .cell--today { border-color: var(--primary); box-shadow: 0 0 0 1px var(--primary); }
    .cell-n { font-size: var(--text-2xs); font-weight: 700; color: var(--ink-3); text-align: center; line-height: 1.1; font-variant-numeric: tabular-nums; }
    .cell--today .cell-n { color: var(--primary); }
    .cell-off { position: absolute; top: 2px; right: 2px; color: var(--ink-4); line-height: 0; }
    .cell-items { display: flex; flex-direction: column; gap: 2px; }

    .chip {
      display: flex; flex-direction: column; align-items: center; gap: 0;
      padding: 2px 1px; border-radius: 4px; overflow: hidden;
      background: color-mix(in srgb, var(--c, var(--primary)) 16%, var(--paper));
      border-left: 3px solid var(--c, var(--primary));
      color: var(--ink); line-height: 1.15;
    }
    .chip-d { font-size: 9px; font-weight: 800; font-variant-numeric: tabular-nums; }
    .chip-k { font-size: 9px; color: var(--ink-2); font-variant-numeric: tabular-nums; }
    /* Réalisé : trame pleine, pour distinguer le fait du prévu sans lire l'étiquette. */
    .chip--done { background: color-mix(in srgb, var(--c, var(--primary)) 30%, var(--paper)); }
    .chip--more { font-size: 9px; font-weight: 800; color: var(--ink-3); background: var(--paper-sunk); border-left-color: var(--ink-4); }

    /* À partir d'une tablette, la grille respire : cases plus hautes et texte lisible de loin. */
    @media (min-width: 600px) {
      .cell { min-height: 84px; padding: 4px; }
      .chip-d, .chip-k { font-size: var(--text-2xs); }
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
