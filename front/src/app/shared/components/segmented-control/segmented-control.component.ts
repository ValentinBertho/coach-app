import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

export interface SegmentOption {
  readonly value: string;
  readonly label: string;
  /** Pastille numérique optionnelle (ex : nb d'éléments). */
  readonly badge?: number | null;
}

/**
 * Contrôle segmenté pour choix mutuellement exclusifs (jour/semaine/mois,
 * simplifié/avancé, scope…). Two-way via `model()`. Accessible (radiogroup).
 *
 * @example
 * <app-segmented-control [options]="views" [(value)]="view" />
 */
@Component({
  selector: 'app-segmented-control',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="seg" role="radiogroup" [attr.aria-label]="ariaLabel()">
      @for (opt of options(); track opt.value) {
        <button
          type="button"
          role="radio"
          class="seg__btn"
          [class.seg__btn--active]="value() === opt.value"
          [attr.aria-checked]="value() === opt.value"
          (click)="value.set(opt.value)"
        >
          <span>{{ opt.label }}</span>
          @if (opt.badge != null) { <span class="seg__badge">{{ opt.badge }}</span> }
        </button>
      }
    </div>
  `,
  styles: [`
    .seg {
      display: inline-flex; gap: 2px; padding: 3px;
      background: var(--paper-sunk); border-radius: var(--radius-full);
    }
    .seg__btn {
      display: inline-flex; align-items: center; gap: var(--sp-1);
      min-height: 36px; padding: 0 var(--sp-4);
      border: none; background: transparent; border-radius: var(--radius-full);
      font-family: var(--font-ui); font-size: var(--text-sm); font-weight: 700;
      color: var(--ink-3); cursor: pointer; white-space: nowrap;
      transition: background var(--duration-fast) var(--ease), color var(--duration-fast) var(--ease);
    }
    .seg__btn:hover { color: var(--ink-2); }
    .seg__btn:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
    .seg__btn--active { background: var(--paper); color: var(--ink); box-shadow: var(--shadow-xs); }
    .seg__badge {
      font-family: var(--font-data); font-variant-numeric: tabular-nums;
      font-size: var(--text-xs); font-weight: 800;
      padding: 0 var(--sp-1); border-radius: var(--radius-full);
      background: var(--primary-wash); color: var(--primary);
    }
  `],
})
export class SegmentedControlComponent {
  readonly options = input.required<readonly SegmentOption[]>();
  readonly value = model.required<string>();
  readonly ariaLabel = input<string>('Sélection');
}
