import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { ActivityLap, ActivityLaps, LAP_KIND_LABELS, LapKind } from '../../../core/models/activity.model';
import { ActivityService } from '../../../core/services/activity.service';
import { AthletePortalService } from '../../../core/services/athlete-portal.service';
import { formatPace } from '../../../core/utils/pace';
import { IconComponent } from '../icon/icon.component';

/** Ligne de moyennes du bas de tableau : ce que le tableau dit une fois agrégé. */
interface LapAverages {
  paceSPerKm: number | null;
  avgHr: number | null;
  elevationGainM: number | null;
  distanceM: number;
  durationS: number;
}

/**
 * Détail tour par tour d'une activité — de quoi décortiquer une séance au lieu de la lire en une
 * moyenne. Sur un 10 × 400, la moyenne est justement le seul chiffre qui ne dit rien : ni l'allure
 * des répétitions, ni leur dérive, ni ce qui se passe pendant la récupération.
 *
 * <p>Deux natures de tours, jamais confondues : ceux relevés par la montre (`DEVICE`, les vraies
 * répétitions) et les splits kilométriques calculés (`SPLIT`). Quand la sortie porte les deux, un
 * onglet bascule de l'une à l'autre — c'est la lecture de Nolio, et sur un fractionné en côte la
 * comparaison est justement l'information : les répétitions disent l'effort produit, les
 * kilomètres disent le terrain parcouru.</p>
 *
 * <p>La barre d'allure est <b>relative au tour le plus rapide</b> : c'est le contraste entre
 * efforts et récupérations qu'on veut voir d'un coup d'œil, pas une échelle absolue partant de
 * zéro. La ligne de moyennes ferme le tableau, comme sur une montre.</p>
 */
@Component({
  selector: 'app-activity-laps',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (loading()) {
      <div class="lap-skel"></div>
    } @else if (laps().length) {
      <div class="laps">
        <div class="laps-hd">
          <span class="laps-title">
            <app-icon [name]="isDevice() ? 'timer' : 'footprints'" [size]="14" />
            Sections
          </span>
          <span class="field-hint">{{ laps().length }} {{ isDevice() ? 'tours' : 'km' }}</span>
        </div>

        <!-- Onglets uniquement quand la sortie sait produire les deux découpes : un onglet qui
             ouvre sur du vide vaut moins que pas d'onglet du tout. -->
        @if (kinds().length > 1) {
          <div class="laps-tabs" role="tablist">
            @for (k of kinds(); track k) {
              <button type="button" class="laps-tab" role="tab"
                      [class.is-on]="current() === k" [attr.aria-selected]="current() === k"
                      (click)="switchTo(k)">{{ kindLabels[k] }}</button>
            }
          </div>
        }

        <!-- Deux lignes par tour plutôt qu'un tableau à six colonnes. Les deux tiennent sur
             375 px, mais aligner distance, allure, temps, FC et cadence sur une seule ligne ne
             laisse qu'une soixantaine de pixels à la barre d'allure — or c'est elle qui porte
             toute la lecture. En deux lignes elle en gagne plus de cent cinquante : le haut
             compare (distance · barre · allure), le bas contextualise. -->
        <ol class="lap-list">
          @for (l of laps(); track l.index) {
            <li class="lap" [class.lap--fastest]="l.paceSPerKm === fastest()">
              <span class="lap-n metric">{{ l.index }}</span>
              <span class="lap-body">
                <span class="lap-top">
                  <span class="lap-dist metric">{{ distLabel(l) }}</span>
                  <span class="lap-bar" aria-hidden="true">
                    <span class="lap-fill" [style.width.%]="barWidth(l)"></span>
                  </span>
                  <span class="lap-pace metric">{{ paceLabel(l) }}<small>/km</small></span>
                </span>
                <span class="lap-meta">
                  <span class="metric">{{ durLabel(l) }}</span>
                  @if (l.avgHr != null) {
                    <span class="metric"><app-icon name="heart-pulse" [size]="11" /> {{ l.avgHr }}</span>
                  }
                  @if (l.avgCadence != null) {
                    <span class="metric">{{ l.avgCadence }} ppm</span>
                  }
                  @if (l.elevationGainM) {
                    <span class="metric">+{{ l.elevationGainM }} m</span>
                  }
                </span>
              </span>
            </li>
          }
        </ol>

        <!-- Moyennes : le tableau seul ne dit pas où se situe un tour par rapport à l'ensemble. -->
        @if (averages(); as avg) {
          <div class="lap-avg">
            <span class="lap-avg-lb">Moyennes</span>
            <span class="lap-avg-vals metric">
              @if (avg.paceSPerKm) { {{ paceOf(avg.paceSPerKm) }}<small>/km</small> }
              @if (avg.avgHr != null) { · {{ avg.avgHr }} bpm }
              @if (avg.elevationGainM) { · +{{ avg.elevationGainM }} m }
            </span>
          </div>
        }

        @if (!isDevice()) {
          <p class="laps-foot field-hint">
            @if (kinds().length > 1) {
              Découpe au kilomètre, calculée depuis le tracé.
            } @else {
              Ta montre n'a pas découpé cette sortie en tours : voici la découpe au kilomètre,
              calculée depuis le tracé.
            }
          </p>
        }
      </div>
    } @else {
      <p class="laps-empty field-hint">
        Pas de découpage disponible (sortie saisie à la main, ou trop courte pour un kilomètre).
      </p>
    }
  `,
  styles: [`
    .laps { display: flex; flex-direction: column; gap: var(--sp-2); }
    .laps-hd { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2); }
    .laps-title { display: inline-flex; align-items: center; gap: var(--sp-1); font-weight: 700; font-size: var(--text-sm); color: var(--ink); }

    .laps-tabs { display: flex; gap: var(--sp-1); border-bottom: 1px solid var(--hairline); }
    .laps-tab {
      min-height: 40px; padding: var(--sp-1) var(--sp-3);
      border: none; border-bottom: 2px solid transparent; background: transparent;
      color: var(--ink-3); font-size: var(--text-sm); font-weight: 600; cursor: pointer;
    }
    .laps-tab.is-on { color: var(--primary); border-bottom-color: var(--primary); font-weight: 700; }

    .lap-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .lap {
      display: flex; align-items: flex-start; gap: var(--sp-2);
      padding: var(--sp-2) 0; border-top: 1px solid var(--hairline);
      font-size: var(--text-sm); color: var(--ink-2);
    }
    .lap:first-child { border-top: none; }
    .lap-n { color: var(--ink-4); font-weight: 700; min-width: 16px; text-align: right; line-height: 1.5; }
    .lap-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
    .lap-top { display: flex; align-items: center; gap: var(--sp-2); }
    .lap-dist { color: var(--ink-3); min-width: 54px; flex-shrink: 0; }
    .lap-bar { flex: 1; min-width: 24px; height: 8px; border-radius: var(--radius-full); background: var(--paper-sunk); overflow: hidden; }
    .lap-fill { display: block; height: 100%; border-radius: var(--radius-full); background: var(--primary); }
    .lap-pace { font-weight: 800; color: var(--ink); flex-shrink: 0; white-space: nowrap; }
    .lap-pace small { font-weight: 600; color: var(--ink-4); font-size: var(--text-2xs); }
    .lap-meta { display: flex; flex-wrap: wrap; gap: var(--sp-1) var(--sp-3); color: var(--ink-3); font-size: var(--text-xs); }
    .lap-meta span { display: inline-flex; align-items: center; gap: 3px; }
    /* Le tour le plus rapide se repère sans compter : c'est le repère de lecture d'un fractionné. */
    .lap--fastest .lap-fill { background: var(--accent); }
    .lap--fastest .lap-pace { color: var(--accent); }

    .lap-avg {
      display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2);
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius);
      background: var(--paper-sunk);
    }
    .lap-avg-lb { font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); }
    .lap-avg-vals { font-weight: 800; color: var(--ink); font-size: var(--text-sm); }
    .lap-avg-vals small { font-weight: 600; color: var(--ink-4); font-size: var(--text-2xs); }

    .laps-foot, .laps-empty { margin: 0; }
    .lap-skel { height: 96px; border-radius: var(--radius-md); background: var(--paper-sunk); animation: lap-pulse 1.2s ease-in-out infinite; }
    @keyframes lap-pulse { 0%,100% { opacity: 0.5; } 50% { opacity: 0.9; } }
  `],
})
export class ActivityLapsComponent {
  /**
   * Athlète ciblé (écrans coach). Laissé vide sur le portail athlète : la route `/me/` est alors
   * utilisée, le composant restant identique des deux côtés (même convention que le temps-en-zone).
   */
  readonly athleteId = input<string | null>(null);
  readonly activityId = input.required<string>();

  private readonly activityService = inject(ActivityService);
  private readonly portal = inject(AthletePortalService);

  readonly loading = signal(true);
  readonly data = signal<ActivityLaps | null>(null);
  /** Découpe demandée par l'athlète ; `null` = celle que le serveur choisit (montre d'abord). */
  private readonly requested = signal<LapKind | null>(null);

  protected readonly kindLabels = LAP_KIND_LABELS;

  readonly laps = computed<ActivityLap[]>(() => this.data()?.laps ?? []);
  readonly isDevice = computed(() => this.data()?.kind === 'DEVICE');
  readonly current = computed<LapKind>(() => this.data()?.kind ?? 'SPLIT');

  /** Découpes proposables. Une réponse ancienne (sans le champ) n'en annonce qu'une : la sienne. */
  readonly kinds = computed<LapKind[]>(() => {
    const available = this.data()?.availableKinds;
    return available?.length ? available : this.data() ? [this.current()] : [];
  });

  /** Allure du tour le plus rapide (s/km) : origine de l'échelle des barres. */
  readonly fastest = computed(() => {
    const paces = this.laps().map((l) => l.paceSPerKm).filter((p): p is number => p != null && p > 0);
    return paces.length ? Math.min(...paces) : null;
  });

  /** Allure du tour le plus lent : borne haute de l'échelle. */
  private readonly slowest = computed(() => {
    const paces = this.laps().map((l) => l.paceSPerKm).filter((p): p is number => p != null && p > 0);
    return paces.length ? Math.max(...paces) : null;
  });

  /**
   * Moyennes du tableau. L'allure est calculée sur les <b>totaux</b> (distance cumulée sur temps
   * cumulé), pas en moyennant les allures des tours : une moyenne d'allures donne autant de poids
   * à une récupération de 200 m qu'à une répétition d'un kilomètre, et fait mentir la ligne.
   */
  readonly averages = computed<LapAverages | null>(() => {
    const laps = this.laps();
    if (laps.length < 2) return null;

    let distanceM = 0;
    let durationS = 0;
    let hrSum = 0;
    let hrCount = 0;
    let elevation = 0;
    for (const l of laps) {
      if (l.distanceM) distanceM += l.distanceM;
      if (l.durationS) durationS += l.durationS;
      if (l.avgHr != null) { hrSum += l.avgHr; hrCount++; }
      if (l.elevationGainM) elevation += l.elevationGainM;
    }
    return {
      distanceM,
      durationS,
      paceSPerKm: distanceM > 0 && durationS > 0 ? Math.round((durationS * 1000) / distanceM) : null,
      avgHr: hrCount ? Math.round(hrSum / hrCount) : null,
      elevationGainM: elevation || null,
    };
  });

  constructor() {
    effect(() => {
      const a = this.athleteId();
      const id = this.activityId();
      const kind = this.requested();
      if (id) this.fetch(a, id, kind);
    }, { allowSignalWrites: true });
  }

  /** Bascule d'onglet : c'est le serveur qui recalcule la découpe, pas l'écran. */
  protected switchTo(kind: LapKind): void {
    if (this.current() === kind) return;
    this.requested.set(kind);
  }

  private fetch(athleteId: string | null, activityId: string, kind: LapKind | null): void {
    this.loading.set(true);
    const request = athleteId
      ? this.activityService.laps(athleteId, activityId, kind ?? undefined)
      : this.portal.activityLaps(activityId, kind ?? undefined);
    request.subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => { this.data.set(null); this.loading.set(false); },
    });
  }

  /**
   * Largeur de la barre : 100 % au tour le plus rapide, 25 % au plus lent. Une échelle partant
   * de zéro écraserait tout l'écart utile — entre 3'20 et 5'40 il n'y a que 40 % de différence,
   * mais c'est toute la séance.
   */
  barWidth(l: ActivityLap): number {
    const fast = this.fastest();
    const slow = this.slowest();
    if (!l.paceSPerKm || fast == null || slow == null) return 0;
    if (slow === fast) return 100;
    return Math.round(100 - ((l.paceSPerKm - fast) / (slow - fast)) * 75);
  }

  /** « 400 m », « 1 km », « 1,25 km » — décimales inutiles supprimées, virgule française. */
  distLabel(l: ActivityLap): string {
    if (l.distanceM == null) return '—';
    if (l.distanceM < 1000) return `${l.distanceM} m`;
    const km = (l.distanceM / 1000).toFixed(2).replace(/\.?0+$/, '').replace('.', ',');
    return `${km} km`;
  }

  paceLabel(l: ActivityLap): string {
    const p = formatPace(l.paceSPerKm);
    return p ? `${p}` : '—';
  }

  paceOf(secPerKm: number): string {
    return formatPace(secPerKm) ?? '—';
  }

  durLabel(l: ActivityLap): string {
    const s = l.durationS;
    if (s == null || s <= 0) return '—';
    const m = Math.floor(s / 60);
    const sec = s % 60;
    if (m >= 60) return `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}`;
    return `${m}:${String(sec).padStart(2, '0')}`;
  }
}
