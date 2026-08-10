import { ChangeDetectionStrategy, Component, computed, input, model } from '@angular/core';
import { FEEL_COLORS, FEEL_LABELS, FEEL_SCALE, feelLabel } from '../feel-scale';

/**
 * Sensation générale d'une séance, en cinq visages (1 = excellente … 5 = très mauvaise).
 *
 * <p>Un visage se choisit sans réfléchir et se relit sans légende — c'est ce que fait Nolio, et
 * c'est ce qui manquait au débrief : l'athlète pouvait dire combien la séance avait été dure,
 * jamais comment il l'avait vécue. Les deux ne se déduisent pas l'un de l'autre.</p>
 *
 * <p>La couleur ne porte jamais l'information seule : le libellé verbal reste affiché sous
 * l'échelle, et chaque cible expose son <code>aria-label</code> complet. Cibles ≥ 56 px : la
 * saisie se fait au pouce, dehors, après l'effort.</p>
 *
 * @example <app-feel-selector [(value)]="feel" />
 */
@Component({
  selector: 'app-feel-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="feel" role="radiogroup" [attr.aria-label]="label()">
      <span class="feel__label">{{ label() }}</span>

      <div class="feel__row">
        @for (n of scale; track n) {
          <button type="button" class="feel__face" role="radio"
                  [class.is-active]="value() === n"
                  [style.--fc]="color(n)"
                  [attr.aria-checked]="value() === n"
                  [attr.aria-label]="'Sensation ' + labelOf(n)"
                  [title]="labelOf(n)"
                  (click)="select(n)">
            <svg viewBox="0 0 40 40" aria-hidden="true">
              <circle cx="20" cy="20" r="17" />
              @if (n === 5) {
                <!-- Le cinquième cran n'est pas « un peu pire » : c'est la séance qui s'est mal
                     passée. Des yeux barrés le disent d'un coup d'œil, là où une bouche encore
                     plus tombante se confondrait avec le quatrième. -->
                <path d="M12 15 l5 5 M17 15 l-5 5" />
                <path d="M23 15 l5 5 M28 15 l-5 5" />
              } @else {
                <circle class="eye" cx="14.5" cy="17" r="1.9" />
                <circle class="eye" cx="25.5" cy="17" r="1.9" />
              }
              <path [attr.d]="mouth(n)" />
            </svg>
          </button>
        }
      </div>

      @if (value(); as v) {
        <span class="feel__word" [style.--fc]="color(v)">Séance {{ labelOf(v) }}</span>
      } @else {
        <span class="feel__word feel__word--empty">Non renseignée</span>
      }
    </div>
  `,
  styles: [`
    .feel { display: flex; flex-direction: column; gap: var(--sp-2); }
    .feel__label { font-size: var(--text-sm); color: var(--ink-2); font-weight: 700; }

    .feel__row { display: grid; grid-template-columns: repeat(5, 1fr); gap: var(--sp-1); }
    .feel__face {
      display: flex; align-items: center; justify-content: center;
      min-height: 56px; padding: var(--sp-1);
      border: 1px solid transparent; border-radius: var(--radius-md);
      background: transparent; cursor: pointer;
      transition: transform var(--duration-fast) var(--ease),
        background var(--duration-fast) var(--ease), border-color var(--duration-fast) var(--ease);
    }
    .feel__face:active { transform: scale(0.94); }
    .feel__face:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
    .feel__face.is-active {
      background: color-mix(in srgb, var(--fc) 14%, var(--paper));
      border-color: var(--fc);
    }

    .feel__face svg { width: 100%; max-width: 44px; height: auto; }
    .feel__face svg circle, .feel__face svg path {
      fill: none; stroke: var(--ink-4); stroke-width: 2;
      stroke-linecap: round; stroke-linejoin: round;
    }
    .feel__face svg .eye { fill: var(--ink-4); stroke: none; }
    .feel__face.is-active svg circle, .feel__face.is-active svg path { stroke: var(--fc); }
    .feel__face.is-active svg .eye { fill: var(--fc); }

    .feel__word { font-size: var(--text-sm); font-weight: 700; color: var(--fc); text-align: right; }
    .feel__word--empty { color: var(--ink-3); font-weight: 600; }
  `],
})
export class FeelSelectorComponent {
  /** Sensation déclarée 1–5 (two-way). */
  readonly value = model<number | null>(null);
  readonly label = input('Sensation');

  protected readonly scale = FEEL_SCALE;

  /** Re-tap sur le cran actif = désélection : un choix posé par erreur reste rattrapable. */
  protected select(n: number): void {
    this.value.set(this.value() === n ? null : n);
  }

  protected labelOf(n: number): string { return FEEL_LABELS[n] ?? ''; }
  protected color(n: number): string { return FEEL_COLORS[n] ?? 'var(--ink-3)'; }

  /**
   * Bouche du visage : un arc dont la courbure s'inverse progressivement (sourire → droite →
   * moue). Les cinq crans sont ainsi distinguables sans couleur, et à petite taille.
   */
  protected mouth(n: number): string {
    const curve = [8, 4, 0, -4, -6][n - 1] ?? 0;
    return `M13 ${26 - curve / 4} Q20 ${26 + curve} 27 ${26 - curve / 4}`;
  }

  protected readonly readout = computed(() => feelLabel(this.value()));
}
