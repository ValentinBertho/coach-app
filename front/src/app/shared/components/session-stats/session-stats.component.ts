import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { formatPace } from '../../../core/utils/pace';
import { IconComponent } from '../icon/icon.component';

/** Chiffres mesurés d'une sortie (montre ou saisie). */
export interface RealizedStats {
  durationS: number | null;
  distanceM: number | null;
  elevationGainM: number | null;
  paceSPerKm: number | null;
  avgHr: number | null;
  maxHr: number | null;
  avgCadence: number | null;
  avgPowerW: number | null;
  calories: number | null;
}

/** Chiffres prescrits, à confronter aux précédents. */
export interface PlannedStats {
  durationS: number | null;
  distanceM: number | null;
  /**
   * La distance prévue n'est qu'un ordre de grandeur, faute d'allure prescrite : elle s'affiche
   * alors précédée de « ≈ », et aucun écart n'est calculé contre elle — comparer un réalisé
   * mesuré à une estimation produirait un écart qui ne dit rien.
   */
  distanceIndicative?: boolean;
}

/** Une case du bandeau : ce qui a été fait, et — repliable — ce qui était prévu. */
interface Stat {
  label: string;
  value: string;
  planned: string | null;
  /** Écart signé lisible (« +1,8 km »), quand les deux valeurs existent. */
  delta: string | null;
  deltaSign: 0 | 1 | -1;
}

/**
 * Bandeau de chiffres d'une séance réalisée : le <b>réalisé en grand</b>, le <b>prévu juste en
 * dessous</b>, et une bascule pour masquer le second.
 *
 * <p><b>Pourquoi cette forme.</b> Le produit affichait le prévu et le réalisé dans deux blocs
 * séparés, l'un au-dessus de l'autre : comparer 16,5 km à 18,3 km demandait de faire l'aller-retour
 * entre deux cartes, et l'écart — la seule information qui compte — n'était jamais écrit. Nolio
 * met la valeur prévue entre parenthèses sous la valeur réalisée, et laisse la masquer d'un tap.
 * C'est ce qui est repris ici, avec l'écart calculé en toutes lettres.</p>
 *
 * <p>La bascule n'est pas cosmétique : le jour d'une course ou d'une sortie libre, il n'y a rien
 * à comparer, et une colonne de parenthèses vides fait du bruit.</p>
 */
@Component({
  selector: 'app-session-stats',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="ss">
      @if (hasPlanned()) {
        <button type="button" class="ss-toggle" (click)="showPlanned.set(!showPlanned())"
                [attr.aria-pressed]="showPlanned()">
          <app-icon [name]="showPlanned() ? 'eye' : 'eye-off'" [size]="15" /> Prévu
        </button>
      }

      <div class="ss-grid">
        @for (s of stats(); track s.label) {
          <div class="ss-cell">
            <span class="ss-lb">{{ s.label }}</span>
            <span class="ss-v metric">{{ s.value }}</span>
            @if (showPlanned() && s.planned) {
              <span class="ss-planned metric">({{ s.planned }})</span>
            }
            @if (showPlanned() && s.delta) {
              <span class="ss-delta" [class.pos]="s.deltaSign > 0" [class.neg]="s.deltaSign < 0">{{ s.delta }}</span>
            }
          </div>
        }
      </div>

      @if (secondary().length) {
        <div class="ss-sec">
          @for (s of secondary(); track s.label) {
            <span class="ss-sec-item"><span class="ss-sec-lb">{{ s.label }}</span> <span class="metric">{{ s.value }}</span></span>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .ss { display: flex; flex-direction: column; gap: var(--sp-3); }
    .ss-toggle {
      align-self: flex-end;
      display: inline-flex; align-items: center; gap: var(--sp-1);
      min-height: 36px; padding: 0 var(--sp-2);
      border: none; background: transparent; color: var(--primary);
      font-size: var(--text-sm); font-weight: 700; cursor: pointer;
    }

    .ss-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--sp-3) var(--sp-2); }
    @media (min-width: 640px) { .ss-grid { grid-template-columns: repeat(4, 1fr); } }
    .ss-cell { display: flex; flex-direction: column; align-items: center; gap: 1px; text-align: center; }
    .ss-lb { font-size: var(--text-xs); color: var(--ink-3); font-weight: 600; }
    .ss-v { font-size: var(--text-xl); font-weight: 800; color: var(--ink); line-height: 1.15; }
    /* Le prévu vit sous le réalisé, en retrait : présent pour comparer, jamais concurrent. */
    .ss-planned { font-size: var(--text-sm); color: var(--ink-4); font-weight: 600; }
    .ss-delta { font-size: var(--text-xs); font-weight: 700; color: var(--ink-3); }
    .ss-delta.pos { color: var(--form-green); }
    .ss-delta.neg { color: var(--form-orange); }

    .ss-sec {
      display: flex; flex-wrap: wrap; gap: var(--sp-2) var(--sp-4);
      padding-top: var(--sp-3); border-top: 1px solid var(--hairline);
      justify-content: center;
    }
    .ss-sec-item { font-size: var(--text-sm); color: var(--ink); font-weight: 700; }
    .ss-sec-lb { color: var(--ink-3); font-weight: 600; }
  `],
})
export class SessionStatsComponent {
  readonly realized = input.required<RealizedStats>();
  readonly planned = input<PlannedStats | null>(null);

  /** Le prévu est visible par défaut : c'est la lecture que l'athlète vient chercher. */
  readonly showPlanned = signal(true);

  readonly hasPlanned = computed(() => {
    const p = this.planned();
    return !!p && (p.durationS != null || p.distanceM != null);
  });

  /** Les quatre chiffres de tête, ceux que Nolio met en grand : durée, distance, D+, allure. */
  readonly stats = computed<Stat[]>(() => {
    const r = this.realized();
    const p = this.planned();
    // L'allure prévue n'a de sens que si la distance prévue en a une : quand celle-ci est
    // estimée depuis la durée, la diviser par la durée redonnerait l'allure de référence, qui
    // n'a jamais été prescrite.
    const plannedPace = p?.durationS && p?.distanceM && !p.distanceIndicative
      ? Math.round((p.durationS * 1000) / p.distanceM) : null;

    return [
      {
        label: 'Durée',
        value: r.durationS ? duration(r.durationS) : '—',
        planned: p?.durationS ? duration(p.durationS) : null,
        ...delta(r.durationS, p?.durationS ?? null, (d) => signedDuration(d)),
      },
      {
        label: 'Distance',
        value: r.distanceM ? km(r.distanceM) : '—',
        planned: p?.distanceM ? (p.distanceIndicative ? `≈ ${km(p.distanceM)}` : km(p.distanceM)) : null,
        // Pas d'écart contre une estimation : « −1,4 km » sous une distance prévue elle-même
        // approchée ferait passer une incertitude pour un manquement.
        ...(p?.distanceIndicative
          ? { delta: null, deltaSign: 0 as const }
          : delta(r.distanceM, p?.distanceM ?? null, (d) => `${d > 0 ? '+' : '−'}${(Math.abs(d) / 1000).toFixed(2).replace('.', ',')} km`)),
      },
      {
        label: 'Dénivelé +',
        value: r.elevationGainM != null ? `${r.elevationGainM} m` : '—',
        planned: null,
        delta: null,
        deltaSign: 0,
      },
      {
        label: 'Allure',
        value: pace(r.paceSPerKm),
        planned: plannedPace ? `${formatPace(plannedPace)}/km` : null,
        delta: null,
        deltaSign: 0,
      },
    ];
  });

  /** Capteurs : présents seulement s'ils ont été mesurés — une case « — » n'apprend rien. */
  readonly secondary = computed(() => {
    const r = this.realized();
    const out: { label: string; value: string }[] = [];
    if (r.avgHr != null) out.push({ label: 'FC moy.', value: `${r.avgHr} bpm` });
    if (r.maxHr != null) out.push({ label: 'FC max', value: `${r.maxHr} bpm` });
    if (r.avgCadence != null) out.push({ label: 'Cadence', value: `${r.avgCadence} ppm` });
    if (r.avgPowerW != null) out.push({ label: 'Puissance', value: `${r.avgPowerW} W` });
    if (r.calories != null) out.push({ label: 'Calories', value: `${r.calories} kcal` });
    return out;
  });
}

/** « 1:14:32 » / « 42:10 » — la durée d'une séance se lit à la seconde. */
function duration(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.round(seconds % 60);
  return h
    ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    : `${m}:${String(s).padStart(2, '0')}`;
}

function signedDuration(deltaS: number): string {
  const sign = deltaS > 0 ? '+' : '−';
  const abs = Math.abs(deltaS);
  const m = Math.floor(abs / 60);
  const s = Math.round(abs % 60);
  return `${sign}${m}:${String(s).padStart(2, '0')}`;
}

function km(meters: number): string {
  return `${(meters / 1000).toFixed(2).replace('.', ',')} km`;
}

function pace(secPerKm: number | null): string {
  const p = formatPace(secPerKm);
  return p ? `${p}/km` : '—';
}

/**
 * Écart réalisé − prévu, sous forme lisible. Sous 2 % on ne l'affiche pas : une montre coupée
 * trois secondes après la ligne ne constitue pas un écart, et l'annoncer apprend à l'ignorer.
 */
function delta(
  actual: number | null,
  planned: number | null,
  fmt: (d: number) => string,
): { delta: string | null; deltaSign: 0 | 1 | -1 } {
  if (actual == null || planned == null || planned <= 0) {
    return { delta: null, deltaSign: 0 };
  }
  const d = actual - planned;
  if (Math.abs(d) / planned < 0.02) {
    return { delta: null, deltaSign: 0 };
  }
  return { delta: fmt(d), deltaSign: d > 0 ? 1 : -1 };
}
