import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DataOriginTagComponent } from '../data-origin-tag/data-origin-tag.component';
import { CounterComponent } from '../counter/counter.component';

/** Statut ACWR dérivé des seuils (affichage uniquement, valeur calculée backend). */
type AcwrStatus = 'detraining' | 'optimal' | 'caution' | 'risk';

const STATUS_META: Record<AcwrStatus, { label: string; cssVar: string; origin: 'calcule' | 'alerte' }> = {
  detraining: { label: 'Sous-charge',  cssVar: 'var(--ink-3)',     origin: 'calcule' },
  optimal:    { label: 'Optimal',      cssVar: 'var(--form-green)', origin: 'calcule' },
  caution:    { label: 'Surveiller',   cssVar: 'var(--form-orange)', origin: 'calcule' },
  risk:       { label: 'Risque',       cssVar: 'var(--form-red)',   origin: 'alerte' },
};

/**
 * Indicateur ACWR (ratio charge aiguë / chronique).
 *
 * Le seuil EST l'information : la bande de sécurité (par défaut 0,8–1,3) est
 * annotée directement sur la piste, et le marqueur est coloré + libellé.
 * Aucun calcul ici : la valeur et les bornes viennent du backend.
 *
 * Une valeur nulle n'est pas une absence de données mais un ratio volontairement suspendu : le
 * backend ne le publie qu'à partir de 21 jours d'historique et 8 séances sur 28 jours. On affiche
 * alors la progression, pour que le coach sache que le chiffre arrive et pourquoi il n'est pas là.
 *
 * @example
 * <app-acwr-indicator [value]="1.35" />
 * <app-acwr-indicator [value]="null" [historyDays]="12" [windowDays]="28" />
 */
@Component({
  selector: 'app-acwr-indicator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DataOriginTagComponent, CounterComponent],
  template: `
    @if (value() === null) {
      <div class="acwr acwr--building" role="group" [attr.aria-label]="ariaLabel()">
        <div class="acwr__head">
          <span class="acwr__title">ACWR</span>
          <span class="acwr__building">En construction — {{ historyDays() }}/{{ windowDays() }} jours</span>
        </div>
        <div class="acwr__track" aria-hidden="true">
          <span class="acwr__progress" [style.width.%]="progressPct()"></span>
        </div>
        <p class="acwr__hint">
          Le rapport charge aiguë / chronique demande assez d'historique pour vouloir dire quelque
          chose. Il s'affichera automatiquement.
        </p>
      </div>
    } @else {
    <div class="acwr" [style.--ac]="meta().cssVar" role="group" [attr.aria-label]="ariaLabel()">
      <div class="acwr__head">
        <span class="acwr__title">ACWR</span>
        <app-counter class="acwr__value" [value]="value()" [decimals]="2" />
        <span class="acwr__status">{{ meta().label }}</span>
        <app-data-origin-tag class="acwr__origin" [origin]="meta().origin" [compact]="true" />
      </div>

      <div class="acwr__track" aria-hidden="true">
        <!-- Bande de sécurité annotée -->
        <span class="acwr__safe" [style.left.%]="safeLeft()" [style.width.%]="safeWidth()"></span>
        <!-- Marqueur de la valeur courante -->
        <span class="acwr__marker" [style.left.%]="markerLeft()"></span>
      </div>

      <div class="acwr__scale" aria-hidden="true">
        <span>0</span>
        <span class="acwr__tick" [style.left.%]="safeLeft()">{{ lowSafe() }}</span>
        <span class="acwr__tick" [style.left.%]="safeLeft() + safeWidth()">{{ highSafe() }}</span>
        <span>{{ max() }}</span>
      </div>
    </div>
    }
  `,
  styles: [`
    .acwr { display: flex; flex-direction: column; gap: var(--sp-2); }
    .acwr__head { display: flex; align-items: baseline; gap: var(--sp-2); }
    .acwr__title { font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); }
    .acwr__value {
      font-family: var(--font-data); font-variant-numeric: tabular-nums;
      font-weight: 800; font-size: var(--text-xl); color: var(--ac);
    }
    .acwr__status { font-size: var(--text-sm); font-weight: 700; color: var(--ac); }
    .acwr__origin { margin-left: auto; align-self: center; }
    .acwr__track {
      position: relative; height: 8px; border-radius: var(--radius-full);
      background: var(--paper-sunk); overflow: visible;
    }
    .acwr__safe {
      position: absolute; top: 0; bottom: 0;
      background: color-mix(in srgb, var(--form-green) 40%, transparent);
      border-radius: var(--radius-full);
    }
    .acwr__marker {
      position: absolute; top: 50%; width: 14px; height: 14px;
      transform: translate(-50%, -50%); border-radius: 50%;
      background: var(--ac); border: 2px solid var(--paper);
      box-shadow: var(--shadow-xs);
    }
    .acwr__scale { position: relative; height: 14px; font-size: var(--text-2xs); color: var(--ink-3); font-family: var(--font-data); }
    .acwr__scale > span:first-child { position: absolute; left: 0; }
    .acwr__scale > span:last-child { position: absolute; right: 0; }
    .acwr__tick { position: absolute; transform: translateX(-50%); }

    .acwr__building { font-size: var(--text-sm); font-weight: 700; color: var(--ink-2); }
    .acwr__progress {
      display: block; height: 100%; border-radius: var(--radius-full);
      background: color-mix(in srgb, var(--ink-3) 55%, transparent);
      transition: width var(--duration) var(--ease);
    }
    .acwr__hint { margin: 0; font-size: var(--text-xs); color: var(--ink-3); }
  `],
})
export class AcwrIndicatorComponent {
  /** Valeur ACWR calculée (backend), ou `null` tant que l'historique est insuffisant. */
  readonly value = input.required<number | null>();
  /** Jours d'historique accumulés — affichés pendant la construction du ratio. */
  readonly historyDays = input(0);
  /** Échelle de la progression (fenêtre chronique du backend, 28 jours). */
  readonly windowDays = input(28);
  /** Borne basse de la zone optimale. */
  readonly lowSafe = input(0.8);
  /** Borne haute de la zone optimale. */
  readonly highSafe = input(1.3);
  /** Borne au-delà de laquelle on bascule en risque. */
  readonly riskAt = input(1.5);
  /** Échelle max de la piste. */
  readonly max = input(2);

  protected readonly status = computed<AcwrStatus>(() => {
    const v = this.value() ?? 0;
    if (v < this.lowSafe()) return 'detraining';
    if (v <= this.highSafe()) return 'optimal';
    if (v < this.riskAt()) return 'caution';
    return 'risk';
  });
  protected readonly meta = computed(() => STATUS_META[this.status()]);

  private pct(v: number): number {
    return Math.max(0, Math.min(100, (v / this.max()) * 100));
  }
  protected readonly safeLeft = computed(() => this.pct(this.lowSafe()));
  protected readonly safeWidth = computed(() => this.pct(this.highSafe()) - this.pct(this.lowSafe()));
  protected readonly markerLeft = computed(() => this.pct(this.value() ?? 0));

  protected readonly progressPct = computed(() =>
    Math.max(0, Math.min(100, (this.historyDays() / Math.max(1, this.windowDays())) * 100)),
  );

  protected readonly ariaLabel = computed(() => {
    const v = this.value();
    return v === null
      ? `ACWR en construction, ${this.historyDays()} jours d'historique sur ${this.windowDays()}`
      : `ACWR ${v.toFixed(2)}, ${this.meta().label}, zone optimale ${this.lowSafe()} à ${this.highSafe()}`;
  });
}
