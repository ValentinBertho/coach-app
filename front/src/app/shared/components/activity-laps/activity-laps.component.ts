import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { ActivityLap, ActivityLaps } from '../../../core/models/activity.model';
import { ActivityService } from '../../../core/services/activity.service';
import { AthletePortalService } from '../../../core/services/athlete-portal.service';
import { formatPace } from '../../../core/utils/pace';
import { IconComponent } from '../icon/icon.component';

/**
 * Détail tour par tour d'une activité — de quoi décortiquer une séance au lieu de la lire en une
 * moyenne. Sur un 10 × 400, la moyenne est justement le seul chiffre qui ne dit rien : ni l'allure
 * des répétitions, ni leur dérive, ni ce qui se passe pendant la récupération.
 *
 * <p>Deux natures de tours, jamais confondues : ceux relevés par la montre (`DEVICE`, les vraies
 * répétitions) et les splits kilométriques calculés quand elle n'a rien découpé (`SPLIT`). La
 * barre d'allure est <b>relative au tour le plus rapide</b> : c'est le contraste entre efforts et
 * récupérations qu'on veut voir d'un coup d'œil, pas une échelle absolue partant de zéro.</p>
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
            {{ isDevice() ? 'Tours de la montre' : 'Splits au kilomètre' }}
          </span>
          <span class="field-hint">{{ laps().length }} {{ isDevice() ? 'tours' : 'km' }}</span>
        </div>

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

        @if (!isDevice()) {
          <p class="laps-foot field-hint">
            Ta montre n'a pas découpé cette sortie en tours : voici la découpe au kilomètre,
            calculée depuis le tracé.
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

  readonly laps = computed<ActivityLap[]>(() => this.data()?.laps ?? []);
  readonly isDevice = computed(() => this.data()?.kind === 'DEVICE');

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

  constructor() {
    effect(() => {
      const a = this.athleteId();
      const id = this.activityId();
      if (id) this.fetch(a, id);
    }, { allowSignalWrites: true });
  }

  private fetch(athleteId: string | null, activityId: string): void {
    this.loading.set(true);
    const request = athleteId
      ? this.activityService.laps(athleteId, activityId)
      : this.portal.activityLaps(activityId);
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

  durLabel(l: ActivityLap): string {
    const s = l.durationS;
    if (s == null || s <= 0) return '—';
    const m = Math.floor(s / 60);
    const sec = s % 60;
    if (m >= 60) return `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}`;
    return `${m}:${String(sec).padStart(2, '0')}`;
  }
}
