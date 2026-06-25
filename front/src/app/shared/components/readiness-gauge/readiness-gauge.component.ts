import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DataOriginTagComponent } from '../data-origin-tag/data-origin-tag.component';

/** Niveau d'état de forme, calculé par le backend (source de vérité). */
export type FormLevel = 'green' | 'orange' | 'red';

const LEVEL_META: Record<FormLevel, { label: string; cssVar: string }> = {
  green:  { label: 'Bonne forme',   cssVar: 'var(--form-green)' },
  orange: { label: 'À surveiller',  cssVar: 'var(--form-orange)' },
  red:    { label: 'Vigilance',     cssVar: 'var(--form-red)' },
};

/**
 * Jauge d'état de forme.
 *
 * Invariant métier Darilab : l'état de forme N'EST JAMAIS dérivé du RPE seul.
 * Ce composant reçoit le niveau déjà calculé par le backend (`level`) ET rend
 * explicites ses entrées (fatigue, douleur, charge) avec un marqueur d'origine
 * « calculé ». Il n'effectue aucun calcul métier : il affiche et hiérarchise.
 *
 * @example
 * <app-readiness-gauge level="orange" [fatigue]="6" [pain]="2" [acwr]="1.35" />
 */
@Component({
  selector: 'app-readiness-gauge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataOriginTagComponent],
  template: `
    <div class="rg" [style.--lc]="meta().cssVar" role="group" [attr.aria-label]="'État de forme : ' + meta().label">
      <div class="rg__head">
        <span class="rg__dot" aria-hidden="true"></span>
        <span class="rg__label">{{ meta().label }}</span>
        <app-data-origin-tag class="rg__origin" origin="calcule" />
      </div>

      <!-- Entrées du calcul, toujours visibles : jamais le RPE seul. -->
      <ul class="rg__inputs">
        @if (fatigue() != null) {
          <li><span class="rg__k">Fatigue</span><span class="rg__v">{{ fatigue() }}<small>/10</small></span></li>
        }
        @if (pain() != null) {
          <li [class.rg__alert]="(pain() ?? 0) >= 4">
            <span class="rg__k">Douleur</span><span class="rg__v">{{ pain() }}<small>/10</small></span>
          </li>
        }
        @if (acwr() != null) {
          <li [class.rg__alert]="acwrRisk()">
            <span class="rg__k">ACWR</span>
            <span class="rg__v">{{ acwr() }}</span>
          </li>
        }
      </ul>

      @if (note()) { <p class="rg__note">{{ note() }}</p> }
    </div>
  `,
  styles: [`
    .rg {
      display: flex; flex-direction: column; gap: var(--sp-2);
      padding: var(--sp-3) var(--sp-4);
      border-radius: var(--radius);
      background: var(--paper);
      border: 1px solid var(--hairline);
      border-left: 4px solid var(--lc);
    }
    .rg__head { display: flex; align-items: center; gap: var(--sp-2); }
    .rg__dot { width: 12px; height: 12px; border-radius: 50%; background: var(--lc); flex-shrink: 0; }
    .rg__label { font-weight: 700; color: var(--ink); font-size: var(--text-lg); }
    .rg__origin { margin-left: auto; }
    .rg__inputs {
      display: flex; flex-wrap: wrap; gap: var(--sp-2) var(--sp-4);
      list-style: none; margin: 0; padding: 0;
    }
    .rg__inputs li { display: inline-flex; align-items: baseline; gap: var(--sp-1); }
    .rg__k { color: var(--ink-3); font-size: var(--text-xs); font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em; }
    .rg__v { font-family: var(--font-data); font-variant-numeric: tabular-nums; font-weight: 700; color: var(--ink); }
    .rg__v small { color: var(--ink-3); font-weight: 600; }
    .rg__alert .rg__v, .rg__alert .rg__k { color: var(--danger-text); }
    .rg__note { margin: 0; color: var(--ink-3); font-size: var(--text-sm); }
  `],
})
export class ReadinessGaugeComponent {
  /** Niveau calculé par le backend (source de vérité). */
  readonly level = input.required<FormLevel>();
  /** Fatigue déclarée (0–10), entrée du calcul. */
  readonly fatigue = input<number | null>(null);
  /** Douleur déclarée (0–10), entrée du calcul. */
  readonly pain = input<number | null>(null);
  /** ACWR (charge aiguë/chronique) calculé, entrée de contexte. */
  readonly acwr = input<number | null>(null);
  /** Note explicative optionnelle. */
  readonly note = input<string>('');

  protected readonly meta = computed(() => LEVEL_META[this.level()]);
  protected readonly acwrRisk = computed(() => {
    const a = this.acwr();
    return a != null && (a > 1.3 || a < 0.8);
  });
}
