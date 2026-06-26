import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Chargement « télémétrie » : une ligne de base parcourue par un balayage (oscilloscope)
 * avec un point qui pulse, plus instrument qu'un spinner générique. Respecte
 * `prefers-reduced-motion` (le balayage se fige). À utiliser pour un état de chargement
 * de section.
 *
 * @example
 * <app-loader label="Lecture des relevés…" />
 */
@Component({
  selector: 'app-loader',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ld" role="status" [attr.aria-label]="label() || 'Chargement'">
      <div class="ld__trace" aria-hidden="true">
        <span class="ld__base"></span>
        <span class="ld__sweep"></span>
        <span class="ld__dot"></span>
      </div>
      @if (label()) { <span class="ld__label">{{ label() }}</span> }
    </div>
  `,
  styles: [`
    .ld { display: flex; flex-direction: column; align-items: center; gap: var(--sp-3); padding: var(--sp-8) var(--sp-4); }
    .ld__trace { position: relative; width: min(220px, 70%); height: 24px; overflow: hidden; }
    .ld__base {
      position: absolute; left: 0; right: 0; top: 50%; height: 2px; transform: translateY(-50%);
      background: var(--hairline); border-radius: var(--radius-full);
    }
    .ld__sweep {
      position: absolute; top: 0; bottom: 0; width: 38%;
      background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--primary) 65%, transparent), transparent);
      animation: ld-sweep 1.25s var(--ease) infinite;
    }
    .ld__dot {
      position: absolute; top: 50%; left: 0; width: 8px; height: 8px; transform: translate(-50%, -50%);
      border-radius: 50%; background: var(--primary); box-shadow: 0 0 0 0 color-mix(in srgb, var(--primary) 45%, transparent);
      animation: ld-dot 1.25s var(--ease) infinite;
    }
    .ld__label { font-size: var(--text-sm); color: var(--ink-3); font-family: var(--font-mono); letter-spacing: 0.02em; }
    @keyframes ld-sweep { from { transform: translateX(-110%); } to { transform: translateX(360%); } }
    @keyframes ld-dot {
      0%, 100% { left: 8%; box-shadow: 0 0 0 0 color-mix(in srgb, var(--primary) 45%, transparent); }
      50% { left: 92%; box-shadow: 0 0 0 5px color-mix(in srgb, var(--primary) 0%, transparent); }
    }
    @media (prefers-reduced-motion: reduce) {
      .ld__sweep, .ld__dot { animation: none; }
      .ld__sweep { transform: translateX(120%); }
      .ld__dot { left: 50%; }
    }
  `],
})
export class LoaderComponent {
  readonly label = input<string>('');
}
