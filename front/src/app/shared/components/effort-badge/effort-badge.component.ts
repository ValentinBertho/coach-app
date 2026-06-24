import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DataOriginTagComponent } from '../data-origin-tag/data-origin-tag.component';

/** Référentiel d'effort (aligné sur strength.model EffortRefType). */
export type EffortKind = 'RPE' | 'RIR';

/**
 * Badge d'effort RPE / RIR.
 *
 * Distingue nettement l'effort PRESCRIT (fourchette min–max, cible coach) du
 * ressenti DÉCLARÉ par l'athlète (`actual`, marqué « saisi »). Échelle visuelle
 * + valeur tabulaire. Ne mélange jamais les deux registres.
 *
 * Conventions d'échelle : RPE 0–10 (effort croissant), RIR 0–5 (reps en réserve,
 * 0 = échec → effort max). Le remplissage de l'échelle reflète l'intensité réelle.
 *
 * @example
 * <app-effort-badge kind="RPE" [min]="6" [max]="7" />
 * <app-effort-badge kind="RPE" [min]="6" [max]="7" [actual]="8" />
 * <app-effort-badge kind="RIR" [min]="1" [max]="2" [actual]="0" />
 */
@Component({
  selector: 'app-effort-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataOriginTagComponent],
  template: `
    <span class="eb" [attr.aria-label]="ariaLabel()">
      <span class="eb__kind">{{ kind() }}</span>

      <span class="eb__scale" aria-hidden="true">
        @for (i of ticks(); track i) {
          <span class="eb__tick" [class.eb__tick--on]="i <= prescribedFill()"></span>
        }
      </span>

      <span class="eb__rx">{{ rxLabel() }}</span>

      @if (actual() != null) {
        <span
          class="eb__actual"
          [class.eb__actual--over]="overshoot()"
          [class.eb__actual--in]="!overshoot()"
        >{{ actual() }}</span>
        <app-data-origin-tag origin="saisi" [compact]="true" />
      }
    </span>
  `,
  styles: [`
    .eb {
      display: inline-flex; align-items: center; gap: var(--sp-2);
      padding: 2px var(--sp-2); border-radius: var(--radius-full);
      background: var(--paper-sunk); white-space: nowrap; font-size: var(--text-sm);
    }
    .eb__kind { font-size: var(--text-xs); font-weight: 800; color: var(--ink-3); letter-spacing: 0.03em; }
    .eb__scale { display: inline-flex; align-items: center; gap: 2px; }
    .eb__tick { width: 5px; height: 12px; border-radius: 2px; background: color-mix(in srgb, var(--ink-4) 45%, transparent); }
    .eb__tick--on { background: var(--energy); }
    .eb__rx {
      font-family: var(--font-data); font-variant-numeric: tabular-nums;
      font-weight: 700; color: var(--ink);
    }
    .eb__actual {
      font-family: var(--font-data); font-variant-numeric: tabular-nums; font-weight: 800;
      padding: 0 var(--sp-2); border-radius: var(--radius-full);
    }
    .eb__actual--in { background: var(--success-bg); color: var(--success-text); }
    .eb__actual--over { background: var(--danger-bg); color: var(--danger-text); }
  `],
})
export class EffortBadgeComponent {
  /** RPE ou RIR. */
  readonly kind = input.required<EffortKind>();
  /** Borne basse prescrite. */
  readonly min = input.required<number>();
  /** Borne haute prescrite. */
  readonly max = input.required<number>();
  /** Effort ressenti déclaré (optionnel). */
  readonly actual = input<number | null>(null);

  /** Échelle max selon le référentiel. */
  protected readonly scaleMax = computed(() => (this.kind() === 'RPE' ? 10 : 5));
  protected readonly ticks = computed(() => Array.from({ length: this.scaleMax() }, (_, i) => i + 1));

  /**
   * Remplissage de l'échelle = intensité d'effort. RPE : remplit jusqu'à max.
   * RIR : inversé (0 RIR = effort max), on remplit (scaleMax − min).
   */
  protected readonly prescribedFill = computed(() =>
    this.kind() === 'RPE' ? this.max() : this.scaleMax() - this.min(),
  );

  protected readonly rxLabel = computed(() =>
    this.min() === this.max() ? `${this.min()}` : `${this.min()}–${this.max()}`,
  );

  /** Dépassement : RPE au-dessus du max, ou RIR en-dessous du min (plus dur que prévu). */
  protected readonly overshoot = computed(() => {
    const a = this.actual();
    if (a == null) return false;
    return this.kind() === 'RPE' ? a > this.max() : a < this.min();
  });

  protected readonly ariaLabel = computed(() => {
    const base = `${this.kind()} prescrit ${this.rxLabel()}`;
    return this.actual() == null ? base : `${base}, ressenti ${this.actual()}`;
  });
}
