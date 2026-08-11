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
   * Durée, en tête du bloc : « 55 min », « 1h07 ». Vide si la séance n'est écrite qu'en distance,
   * ou si sa durée est un résidu sous le seuil d'une vraie séance.
   */
  sub: string;
  /**
   * Distance, sous la durée et en gras : « 12 km », « ≈ 10 km ». Vide quand rien n'est chiffré —
   * le bloc se réduit alors à son icône, ce qui reste une information (« il y a quelque chose ce
   * jour-là »). Jamais un mot tronqué.
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
                    @if (c.sub) { <span class="chip-s">{{ c.sub }}</span> }
                    @if (c.value) {
                      <span class="chip-v">{{ c.value }}</span>
                    } @else if (!c.sub) {
                      <app-icon class="chip-i" [name]="c.done ? 'check' : c.icon" [size]="13" />
                    }
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
       pastille un peu large pousse sa colonne et le mois cesse d'être une grille.

       <p>Chaque pixel pris ici est pris au texte des blocs : sur un écran de 390 px, la gouttière
       et les sept espaces décident à eux seuls si « 12,4 km » s'écrit en entier ou se termine par
       une lettre coupée. D'où une gouttière au plus juste (le numéro de semaine et deux chiffres
       de volume) et des espaces de 2 px.</p> */
    .mg-head, .mg-week { display: grid; grid-template-columns: 22px repeat(7, minmax(0, 1fr)); gap: 2px; }
    .mg { display: flex; flex-direction: column; gap: 2px; }
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

    /* Une case vide n'a rien à montrer et ne mérite pas la hauteur d'une case pleine : la
       hauteur minimale ne tient plus que le numéro du jour et un bloc, les jours chargés
       poussent leur ligne d'eux-mêmes. Un mois calme tient alors dans l'écran. */
    .cell {
      display: flex; flex-direction: column; align-items: stretch; gap: 2px;
      min-height: 54px; padding: 3px 2px; position: relative;
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

    /* Bloc de séance : la durée, puis la distance sous elle.
       <p>Il occupe toute la largeur de la case et n'a pas de rail : la couleur du bloc dit le
       type, un liseré de 3 px ne faisait que voler la largeur du texte. C'est ce qui coupait les
       chiffres — « 1,3 km » demande 36 px, et la case, une fois retirés le rail et les marges,
       n'en offrait que 35 : on lisait « 1,3 kı ». Un chiffre tronqué est pire qu'absent.</p> */
    .chip {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: 0; padding: 3px 2px; border-radius: 5px; min-height: 18px;
      background: color-mix(in srgb, var(--c, var(--primary)) 24%, var(--paper));
      color: var(--ink); line-height: 1.2;
    }
    .chip-i { flex: none; color: var(--c, var(--primary)); }
    /* La durée en second rôle : c'est la distance qu'on compare d'un jour à l'autre. */
    .chip-s { font-size: var(--text-2xs); font-weight: 600; color: var(--ink-2); font-variant-numeric: tabular-nums; white-space: nowrap; }
    /* La distance passe à la ligne plutôt que d'être rognée. Calculer si « 12,4 km » tient dans
       une colonne, c'est parier sur la largeur d'un écran et sur une police ; le repli à la ligne
       ne parie sur rien : il ne s'applique que là où la place manque vraiment. */
    .chip-v {
      font-size: var(--text-2xs); font-weight: 800; font-variant-numeric: tabular-nums;
      text-align: center; line-height: 1.15;
    }
    /* Séance clé : la couleur remplit le bloc. Le mois montre alors ses points durs sans qu'on
       ait à lire un seul mot.

       <p><b>Pourquoi 40 % et pas davantage.</b> Un aplat plus saturé serait plus beau, et
       illisible : en thème sombre, l'encre claire (#eaf2f0) sur un bloc de zone 3 (le jaune)
       tombe à 4,1:1 dès 55 % — sous le 4,5:1 exigé. À 40 % elle tient 5,9:1 sur le jaune et
       6,4:1 sur le vert. La saturation s'arrête là où le texte cesse de se lire.</p> */
    .chip--strong { background: color-mix(in srgb, var(--c, var(--primary)) 40%, var(--paper)); }
    /* Réalisé : coche à la place de l'icône de type, et trame pleine. Le fait et le prévu ne se
       distinguaient que par une nuance de fond, invisible sur un écran au soleil. */
    .chip--done { background: color-mix(in srgb, var(--c, var(--primary)) 34%, var(--paper)); }
    .chip--done .chip-i { color: var(--ink); }
    .chip--more {
      font-size: var(--text-2xs); font-weight: 800; color: var(--ink-3);
      background: var(--paper-sunk); border-left-color: var(--ink-4);
    }

    /* Écran étroit (≤ 340 px) : la colonne tombe sous 40 px et la durée ne tient plus en plus de
       la distance. C'est la durée qui cède — la distance est ce qu'on compare d'un jour à
       l'autre, et la feuille du jour, à un tap, porte les deux. */
    @media (max-width: 340px) {
      /* Seulement quand une distance suit : une séance écrite en durée seule perdrait sinon sa
         seule information et laisserait un bloc muet. Un navigateur sans le sélecteur :has()
         garde la durée — le bloc est un peu serré, il n'est jamais vide. */
      .chip-s:has(+ .chip-v) { display: none; }
    }

    /* À partir d'une tablette, la grille respire : cases plus hautes et texte lisible de loin. */
    @media (min-width: 600px) {
      .cell { min-height: 92px; padding: 4px; }
      .chip { min-height: 20px; padding: 4px 5px; }
      .chip-v, .chip-s { font-size: var(--text-xs); }
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
