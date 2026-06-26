import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DataOriginTagComponent, type DataOrigin } from '../data-origin-tag/data-origin-tag.component';
import { CounterComponent } from '../counter/counter.component';

/** Tonalité sémantique de la carte (encadre la décision, pas la déco). */
export type MetricTone = 'neutral' | 'alert' | 'success';

/**
 * Carte métrique réutilisable : 1 métrique dominante (label + valeur tabulaire
 * + delta + origine). Brique de base des dashboards.
 *
 * Règle : jamais deux métriques d'égale importance dans une même carte.
 *
 * @example
 * <app-metric-card label="VDOT" [value]="54" origin="estime" />
 * <app-metric-card label="Charge aiguë" [value]="412" unit="UA" [delta]="8" goodWhen="down" tone="alert" />
 * <app-metric-card label="Compliance" [loading]="true" />
 */
@Component({
  selector: 'app-metric-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataOriginTagComponent, CounterComponent],
  template: `
    <article class="mc" [class.mc--alert]="tone() === 'alert'" [class.mc--success]="tone() === 'success'">
      <header class="mc__head">
        <span class="mc__label">{{ label() }}</span>
        @if (origin()) { <app-data-origin-tag [origin]="origin()!" [compact]="true" /> }
      </header>

      @if (loading()) {
        <div class="skeleton mc__skeleton"></div>
      } @else {
        <div class="mc__value">
          <app-counter class="mc__num" [value]="value()" [decimals]="decimals()" />
          @if (unit()) { <span class="mc__unit">{{ unit() }}</span> }
        </div>

        @if (delta() != null) {
          <div class="mc__delta" [class.mc__delta--good]="deltaTone() === 'good'"
               [class.mc__delta--bad]="deltaTone() === 'bad'">
            <span class="mc__arrow" aria-hidden="true">{{ deltaUp() ? '▲' : '▼' }}</span>
            <span class="metric">{{ deltaAbs() }}</span>
            @if (deltaUnit()) { <span class="mc__deltaunit">{{ deltaUnit() }}</span> }
            @if (deltaLabel()) { <span class="mc__deltalabel">{{ deltaLabel() }}</span> }
          </div>
        }
      }
    </article>
  `,
  styles: [`
    .mc {
      display: flex; flex-direction: column; gap: var(--sp-2);
      padding: var(--sp-4) var(--sp-5);
      background: var(--paper); border: 1px solid var(--hairline);
      border-radius: var(--radius-lg); box-shadow: var(--shadow-sm);
    }
    .mc--alert { border-left: 4px solid var(--form-orange); }
    .mc--success { border-left: 4px solid var(--form-green); }
    .mc__head { display: flex; align-items: center; gap: var(--sp-2); }
    .mc__label {
      font-size: var(--text-xs); font-weight: 700; text-transform: uppercase;
      letter-spacing: 0.04em; color: var(--ink-3);
    }
    .mc__head app-data-origin-tag { margin-left: auto; }
    .mc__value { display: flex; align-items: baseline; gap: var(--sp-1); }
    .mc__num { font-size: var(--text-3xl); font-weight: 800; color: var(--ink); line-height: 1.05; }
    .mc__unit { font-size: var(--text-md); font-weight: 600; color: var(--ink-3); }
    .mc__delta {
      display: inline-flex; align-items: baseline; gap: var(--sp-1);
      font-size: var(--text-sm); font-weight: 700; color: var(--ink-3);
    }
    .mc__delta--good { color: var(--success-text); }
    .mc__delta--bad { color: var(--danger-text); }
    .mc__arrow { font-size: 0.7em; }
    .mc__deltaunit, .mc__deltalabel { color: var(--ink-3); font-weight: 600; }
    .mc__skeleton { height: 36px; width: 60%; }
  `],
})
export class MetricCardComponent {
  readonly label = input.required<string>();
  readonly value = input<string | number | null>(null);
  /** Décimales pour l'animation du compteur (les valeurs numériques sont animées). */
  readonly decimals = input(0);
  readonly unit = input<string>('');
  readonly origin = input<DataOrigin | null>(null);
  readonly tone = input<MetricTone>('neutral');
  readonly loading = input(false);

  /** Variation par rapport à la période précédente (signée). */
  readonly delta = input<number | null>(null);
  readonly deltaUnit = input<string>('');
  readonly deltaLabel = input<string>('');
  /** Sens « positif » de la métrique : une hausse est-elle bonne ou mauvaise ? */
  readonly goodWhen = input<'up' | 'down'>('up');

  protected readonly deltaUp = computed(() => (this.delta() ?? 0) >= 0);
  protected readonly deltaAbs = computed(() => Math.abs(this.delta() ?? 0));
  protected readonly deltaTone = computed<'good' | 'bad' | 'neutral'>(() => {
    const d = this.delta();
    if (d == null || d === 0) return 'neutral';
    const up = d > 0;
    const goodUp = this.goodWhen() === 'up';
    return up === goodUp ? 'good' : 'bad';
  });
}
