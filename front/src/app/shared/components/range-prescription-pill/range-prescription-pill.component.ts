import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/**
 * Pastille de prescription en fourchette min–max.
 *
 * Invariant métier Darilab : une prescription est TOUJOURS une fourchette,
 * jamais une valeur sèche. Ce composant impose donc deux bornes. Si une seule
 * valeur est fournie (anti-pattern), il l'affiche en mode dégradé visible
 * (« ⚠ valeur unique ») plutôt que de masquer le problème.
 *
 * Distingue trois registres optionnels :
 *  - prescrit : la fourchette (toujours, lecture seule côté athlète)
 *  - cible    : valeur visée dans la fourchette (`target`)
 *  - réalisé  : valeur effectivement faite (`actual`), comparée à la fourchette
 *
 * @example
 * <app-range-prescription-pill label="Allure" [min]="250" [max]="260" unit="/km" [format]="paceFmt" />
 * <app-range-prescription-pill label="Charge" [min]="70" [max]="75" unit="% 1RM" [target]="72" [actual]="74" />
 */
@Component({
  selector: 'app-range-prescription-pill',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span class="rx" [class.rx--invalid]="invalid()" [attr.aria-label]="ariaLabel()">
      @if (label()) { <span class="rx__label">{{ label() }}</span> }

      <span class="rx__range">
        @if (invalid()) {
          <app-icon class="rx__warn" name="alert-triangle" [size]="12" />
          <span class="rx__val">{{ display(min() ?? max()) }}</span>
        } @else {
          <span class="rx__val">{{ display(min()) }}</span>
          <span class="rx__sep" aria-hidden="true">–</span>
          <span class="rx__val">{{ display(max()) }}</span>
        }
        @if (unit()) { <span class="rx__unit">{{ unit() }}</span> }
      </span>

      @if (target() != null) {
        <span class="rx__chip rx__chip--target" title="Cible">◎ {{ display(target()!) }}</span>
      }
      @if (actual() != null) {
        <span
          class="rx__chip rx__chip--actual"
          [class.rx__chip--in]="actualInRange()"
          [class.rx__chip--out]="!actualInRange()"
          [title]="actualInRange() ? 'Réalisé — dans la fourchette' : 'Réalisé — hors fourchette'"
        >{{ actualInRange() ? '✓' : '!' }} {{ display(actual()!) }}</span>
      }
    </span>
  `,
  styles: [`
    .rx {
      display: inline-flex; align-items: center; gap: var(--sp-2);
      padding: 2px var(--sp-2); border-radius: var(--radius-full);
      background: var(--paper-sunk); white-space: nowrap;
      font-size: var(--text-sm);
    }
    .rx--invalid { background: var(--warning-bg); }
    .rx__label { color: var(--ink-3); font-weight: 600; font-size: var(--text-xs); }
    .rx__range {
      display: inline-flex; align-items: baseline; gap: 2px;
      font-family: var(--font-data); font-variant-numeric: tabular-nums;
      font-weight: 700; color: var(--ink);
    }
    .rx__sep { color: var(--ink-3); }
    .rx__unit { font-family: var(--font-ui); font-weight: 600; color: var(--ink-3); font-size: var(--text-xs); margin-left: 2px; }
    .rx__warn { color: var(--warning); }
    .rx__chip {
      display: inline-flex; align-items: center; gap: 2px;
      font-family: var(--font-data); font-variant-numeric: tabular-nums;
      font-size: var(--text-xs); font-weight: 700;
      padding: 0 var(--sp-2); border-radius: var(--radius-full);
    }
    .rx__chip--target { background: var(--primary-wash); color: var(--primary); }
    .rx__chip--in  { background: var(--success-bg); color: var(--success-text); }
    .rx__chip--out { background: var(--danger-bg); color: var(--danger-text); }
  `],
})
export class RangePrescriptionPillComponent {
  /** Libellé optionnel (« Allure », « Charge », « Reps »…). */
  readonly label = input<string>('');
  /** Borne basse de la fourchette. */
  readonly min = input<number | null>(null);
  /** Borne haute de la fourchette. */
  readonly max = input<number | null>(null);
  /** Unité affichée (« /km », « % 1RM », « bpm »…). */
  readonly unit = input<string>('');
  /** Valeur cible dans la fourchette (optionnelle). */
  readonly target = input<number | null>(null);
  /** Valeur réalisée (optionnelle), comparée à la fourchette. */
  readonly actual = input<number | null>(null);
  /** Formateur d'affichage (ex : secondes → « 4:10 »). Par défaut : nombre brut. */
  readonly format = input<(v: number) => string>((v) => `${v}`);

  /** Anti-pattern détecté : prescription à valeur unique. */
  protected readonly invalid = computed(() => this.min() == null || this.max() == null);

  protected readonly actualInRange = computed(() => {
    const a = this.actual();
    const lo = this.min();
    const hi = this.max();
    if (a == null || lo == null || hi == null) return true;
    return a >= lo && a <= hi;
  });

  protected display(v: number | null): string {
    return v == null ? '—' : this.format()(v);
  }

  protected readonly ariaLabel = computed(() => {
    if (this.invalid()) return `${this.label()} valeur unique (prescription incomplète)`;
    return `${this.label()} de ${this.display(this.min())} à ${this.display(this.max())} ${this.unit()}`.trim();
  });
}
