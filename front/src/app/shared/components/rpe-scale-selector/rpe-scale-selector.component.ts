import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { RPE_SCALE, rpeLabel } from '../rpe-scale';

/**
 * Correspondance RPE → zone d'intensité (2 crans par zone).
 * Le produit parle Z1→Z5 partout ailleurs ; l'échelle d'effort doit se lire dans le même
 * vocabulaire de couleur, sinon l'athlète apprend deux systèmes pour la même idée.
 */
function zoneOf(rpe: number): 1 | 2 | 3 | 4 | 5 {
  return Math.min(5, Math.ceil(rpe / 2)) as 1 | 2 | 3 | 4 | 5;
}

/**
 * Échelle d'effort perçu CR10 (1–10), saisie au pouce.
 *
 * Deux lignes de 5 : sur 375 px, dix colonnes donnaient ~29 px par bouton, très en dessous
 * des 44 px exigés par le design system — on tape à côté, après l'effort, les mains moites.
 * Le dégradé Z1→Z5 fait lire l'échelle comme un effort qui monte, et le repère verbal CR10
 * reste affiché : la couleur ne porte jamais l'information seule (daltonisme).
 *
 * @example <app-rpe-scale-selector [(value)]="rpe" label="Effort perçu (RPE)" />
 */
@Component({
  selector: 'app-rpe-scale-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rpe">
      <span class="rpe__label">{{ label() }}</span>
      <div class="rpe__grid">
        @for (n of scale; track n) {
          <button type="button" class="rpe__dot" [class.is-active]="value() === n"
                  [class.rpe__dot--dark]="zone(n) === 3"
                  [style.--zc]="'var(--zone-' + zone(n) + ')'"
                  [title]="labelOf(n)" [attr.aria-pressed]="value() === n"
                  [attr.aria-label]="'RPE ' + n + ' — ' + labelOf(n)"
                  (click)="value.set(n)">{{ n }}</button>
        }
      </div>
      <!-- Un chiffre nu ne veut rien dire : le repère verbal CR10 accompagne le choix. -->
      @if (labelOf(value()); as word) { <span class="rpe__word">{{ word }}</span> }
    </div>
  `,
  styles: [`
    .rpe { display: flex; flex-direction: column; gap: var(--sp-2); }
    .rpe__label { font-size: var(--text-sm); color: var(--ink-2); font-weight: 700; }

    /* 2 × 5 : chaque cible dépasse 44 px même sur un iPhone SE (375 px). */
    .rpe__grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: var(--sp-1); }
    .rpe__dot {
      min-height: 44px; min-width: 44px;
      display: flex; align-items: center; justify-content: center;
      border: 1px solid color-mix(in srgb, var(--zc) 45%, transparent);
      background: color-mix(in srgb, var(--zc) 12%, var(--paper));
      color: var(--ink);
      border-radius: var(--radius-sm);
      cursor: pointer;
      font-weight: 700;
      font-size: var(--text-md);
      font-family: var(--font-data);
      font-variant-numeric: tabular-nums;
    }
    .rpe__dot:active { transform: scale(0.96); }
    .rpe__dot.is-active { background: var(--zc); border-color: var(--zc); color: #fff; }
    /* Z3 (jaune) est trop clair pour du blanc : texte encre, comme le badge de zone. */
    .rpe__dot--dark.is-active { color: var(--ink); }

    .rpe__word {
      display: block; margin-top: var(--sp-1);
      font-size: var(--text-sm); font-weight: 700; color: var(--ink-2); text-align: right;
    }
  `],
})
export class RpeScaleSelectorComponent {
  readonly value = model<number | null>(null);
  readonly label = input('Effort perçu (RPE)');

  protected readonly scale = RPE_SCALE;

  protected zone(rpe: number): number { return zoneOf(rpe); }
  protected labelOf(value: number | null | undefined): string { return rpeLabel(value); }
}
