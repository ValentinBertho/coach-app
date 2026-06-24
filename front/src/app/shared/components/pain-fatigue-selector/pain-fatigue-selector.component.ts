import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';

/** Nature de la donnée déclarée. */
export type FeedbackKind = 'fatigue' | 'pain';

/**
 * Sélecteur tactile 0–10 pour le retour athlète (fatigue / douleur).
 *
 * Conçu pour le check « 10 secondes » mobile : grandes cibles tactiles (≥ 40px),
 * lisible en extérieur (couleur + chiffre + libellé de sévérité, jamais la
 * couleur seule). Donnée DÉCLARÉE par l'athlète (registre « saisi »).
 * Two-way binding via `model()`.
 *
 * @example
 * <app-pain-fatigue-selector kind="fatigue" [(value)]="fatigue" />
 * <app-pain-fatigue-selector kind="pain" [(value)]="pain" />
 */
@Component({
  selector: 'app-pain-fatigue-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pf" role="radiogroup" [attr.aria-label]="label()">
      <div class="pf__head">
        <span class="pf__label">{{ label() }}</span>
        @if (value() != null) {
          <span class="pf__readout" [style.--sv]="severityVar()">
            {{ value() }}<small>/10</small> · {{ severityLabel() }}
          </span>
        } @else {
          <span class="pf__readout pf__readout--empty">Non renseigné</span>
        }
      </div>

      <div class="pf__scale">
        @for (n of steps; track n) {
          <button
            type="button"
            class="pf__step"
            role="radio"
            [class.pf__step--active]="value() === n"
            [style.--sv]="severityVarFor(n)"
            [attr.aria-checked]="value() === n"
            [attr.aria-label]="kind() + ' ' + n + ' sur 10'"
            (click)="select(n)"
          >{{ n }}</button>
        }
      </div>
    </div>
  `,
  styles: [`
    .pf { display: flex; flex-direction: column; gap: var(--sp-2); }
    .pf__head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2); }
    .pf__label { font-weight: 700; color: var(--ink); }
    .pf__readout {
      font-family: var(--font-data); font-variant-numeric: tabular-nums;
      font-weight: 800; color: var(--sv);
    }
    .pf__readout small { color: var(--ink-3); font-weight: 600; }
    .pf__readout--empty { font-family: var(--font-ui); font-weight: 600; color: var(--ink-3); }
    .pf__scale {
      display: grid; grid-template-columns: repeat(11, 1fr); gap: var(--sp-1);
    }
    .pf__step {
      min-height: 44px; border-radius: var(--radius-sm);
      border: 1px solid var(--hairline); background: var(--paper);
      font-family: var(--font-data); font-variant-numeric: tabular-nums;
      font-weight: 700; font-size: var(--text-md); color: var(--ink-2);
      cursor: pointer; transition: transform var(--duration-fast) var(--ease),
        background var(--duration-fast) var(--ease), border-color var(--duration-fast) var(--ease);
    }
    .pf__step:active { transform: scale(0.94); }
    .pf__step:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
    .pf__step--active {
      background: var(--sv); border-color: var(--sv);
      color: #fff; box-shadow: var(--shadow-xs);
    }
    @media (max-width: 420px) {
      .pf__step { min-height: 40px; font-size: var(--text-sm); }
    }
  `],
})
export class PainFatigueSelectorComponent {
  /** fatigue | pain. */
  readonly kind = input.required<FeedbackKind>();
  /** Valeur déclarée 0–10 (two-way). */
  readonly value = model<number | null>(null);

  protected readonly steps = Array.from({ length: 11 }, (_, i) => i);

  protected readonly label = computed(() => (this.kind() === 'pain' ? 'Douleur' : 'Fatigue'));

  protected select(n: number): void {
    // Re-tap sur la valeur active = désélection (évite un état figé par erreur).
    this.value.set(this.value() === n ? null : n);
  }

  /** Sévérité : 0–3 ok, 4–6 modéré, 7–10 élevé (redondance via libellé). */
  private severity(n: number): 'green' | 'orange' | 'red' {
    if (n <= 3) return 'green';
    if (n <= 6) return 'orange';
    return 'red';
  }
  protected severityVarFor(n: number): string {
    return `var(--form-${this.severity(n)})`;
  }
  protected readonly severityVar = computed(() =>
    this.value() == null ? 'var(--ink-3)' : `var(--form-${this.severity(this.value()!)})`,
  );
  protected readonly severityLabel = computed(() => {
    const v = this.value();
    if (v == null) return '';
    const s = this.severity(v);
    return s === 'green' ? 'léger' : s === 'orange' ? 'modéré' : 'élevé';
  });
}
