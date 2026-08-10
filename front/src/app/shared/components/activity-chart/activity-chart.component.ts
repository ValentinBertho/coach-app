import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { ActivityStream, ActivityStreamPoint } from '../../../core/models/activity.model';
import { ActivityService } from '../../../core/services/activity.service';
import { AthletePortalService } from '../../../core/services/athlete-portal.service';
import { formatPace } from '../../../core/utils/pace';
import { IconComponent } from '../icon/icon.component';

/** Une série tracée : son chemin SVG, ses bornes et sa couleur. */
interface Series {
  path: string;
  min: number;
  max: number;
}

/** Repère d'abscisse (une graduation kilométrique). */
interface Tick {
  x: number;
  label: string;
}

const VIEW_W = 320;
const VIEW_H = 140;
const PAD_L = 30;
const PAD_R = 6;
const PAD_T = 8;
const PAD_B = 18;

/**
 * Courbe d'une sortie : fréquence cardiaque et allure le long de la <b>distance</b>.
 *
 * <p><b>Pourquoi.</b> Le produit savait tout dire d'une séance sauf comment elle s'était passée.
 * Les moyennes donnent le coût (47 min, 4'31/km), les tours donnent la découpe — mais la dérive
 * cardiaque d'une sortie longue, l'échauffement qui monte, les dents de scie d'un fractionné ne
 * se lisent que sur une courbe. C'est le premier écran qu'un coureur ouvre en rentrant, et c'est
 * exactement ce que Nolio met en avant.</p>
 *
 * <p><b>Abscisse en distance, pas en temps.</b> Sur un axe temporel les récupérations s'écrasent
 * et les répétitions s'étirent : la séance n'est plus reconnaissable. La distance est celle qui
 * fait dire « là, c'est mon troisième 1000 ». Elle est calculée serveur, avec la même intégration
 * que les splits kilométriques, pour que la courbe et le tableau des tours placent le km 5 au
 * même endroit.</p>
 *
 * <p>SVG écrit à la main, aucune bibliothèque de graphes ajoutée : deux polylignes et quatre
 * graduations ne justifient pas une dépendance de plusieurs dizaines de kilo-octets sur une PWA
 * mobile — et le tracé hérite ainsi des jetons de couleur du thème, clair comme sombre.</p>
 */
@Component({
  selector: 'app-activity-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (loading()) {
      <div class="ch-skel"></div>
    } @else if (!points().length) {
      <p class="field-hint ch-empty">
        Pas de courbe pour cette sortie — il y faut un enregistrement de montre (FC ou allure
        seconde par seconde), qu'une saisie manuelle n'a pas.
      </p>
    } @else {
      <div class="ch">
        <div class="ch-hd">
          <span class="ch-title"><app-icon name="line-chart" [size]="14" /> Graphique</span>
          <div class="ch-legend">
            @if (hr(); as h) {
              <button type="button" class="ch-key ch-key--hr" [class.is-off]="!showHr()"
                      (click)="showHr.set(!showHr())">
                FC <span class="metric">{{ h.min }}–{{ h.max }}</span> bpm
              </button>
            }
            @if (pace(); as p) {
              <button type="button" class="ch-key ch-key--pace" [class.is-off]="!showPace()"
                      (click)="showPace.set(!showPace())">
                Allure <span class="metric">{{ paceLabel(p.min) }}–{{ paceLabel(p.max) }}</span>
              </button>
            }
          </div>
        </div>

        <svg [attr.viewBox]="'0 0 ' + viewW + ' ' + viewH" preserveAspectRatio="none"
             role="img" [attr.aria-label]="ariaLabel()">
          <!-- Graduations kilométriques : sans elles, un pic « vers le milieu » ne se rattache à
               aucun moment de la séance. -->
          @for (t of ticks(); track t.x) {
            <line class="ch-grid" [attr.x1]="t.x" [attr.x2]="t.x" [attr.y1]="padT" [attr.y2]="viewH - padB" />
            <text class="ch-tick" [attr.x]="t.x" [attr.y]="viewH - 5">{{ t.label }}</text>
          }
          <line class="ch-axis" [attr.x1]="padL" [attr.x2]="viewW - padR"
                [attr.y1]="viewH - padB" [attr.y2]="viewH - padB" />

          @if (showPace() && pace(); as p) {
            <path class="ch-line ch-line--pace" [attr.d]="p.path" />
          }
          @if (showHr() && hr(); as h) {
            <path class="ch-line ch-line--hr" [attr.d]="h.path" />
          }

          <!-- Bornes de l'axe FC, à gauche : une courbe sans échelle ne se compare à rien. -->
          @if (showHr() && hr(); as h) {
            <text class="ch-ax-lb" [attr.x]="4" [attr.y]="padT + 8">{{ h.max }}</text>
            <text class="ch-ax-lb" [attr.x]="4" [attr.y]="viewH - padB - 2">{{ h.min }}</text>
          }
        </svg>
      </div>
    }
  `,
  styles: [`
    .ch { display: flex; flex-direction: column; gap: var(--sp-2); }
    .ch-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); flex-wrap: wrap; }
    .ch-title { display: inline-flex; align-items: center; gap: var(--sp-1); font-weight: 700; font-size: var(--text-sm); color: var(--ink); }
    .ch-legend { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .ch-key {
      display: inline-flex; align-items: center; gap: 4px;
      padding: 2px var(--sp-2); border-radius: var(--radius-full);
      border: 1px solid var(--hairline); background: var(--paper);
      font-size: var(--text-xs); font-weight: 700; color: var(--ink-2); cursor: pointer;
    }
    .ch-key::before { content: ''; width: 10px; height: 3px; border-radius: 2px; background: currentColor; }
    .ch-key--hr { color: var(--zone-5); }
    .ch-key--pace { color: var(--primary); }
    /* Éteindre une série plutôt que la retirer : la place réservée ne bouge pas sous le doigt. */
    .ch-key.is-off { color: var(--ink-4); opacity: 0.7; }

    .ch svg { width: 100%; height: 160px; overflow: visible; }
    .ch-grid { stroke: var(--hairline); stroke-width: 1; }
    .ch-axis { stroke: var(--hairline); stroke-width: 1; }
    .ch-tick, .ch-ax-lb { fill: var(--ink-4); font-size: 9px; font-family: var(--font-data); }
    .ch-tick { text-anchor: middle; }
    .ch-line { fill: none; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; vector-effect: non-scaling-stroke; }
    .ch-line--hr { stroke: var(--zone-5); }
    .ch-line--pace { stroke: var(--primary); }

    .ch-skel { height: 160px; border-radius: var(--radius-md); background: var(--paper-sunk); animation: ch-pulse 1.2s ease-in-out infinite; }
    @keyframes ch-pulse { 0%,100% { opacity: 0.5; } 50% { opacity: 0.9; } }
    .ch-empty { margin: 0; }
  `],
})
export class ActivityChartComponent {
  /**
   * Athlète ciblé (écrans coach). Laissé vide sur le portail athlète : la route `/me/` est alors
   * utilisée — même convention que le temps-en-zone et les tours.
   */
  readonly athleteId = input<string | null>(null);
  readonly activityId = input.required<string>();

  private readonly activityService = inject(ActivityService);
  private readonly portal = inject(AthletePortalService);

  readonly loading = signal(true);
  readonly data = signal<ActivityStream | null>(null);
  readonly showHr = signal(true);
  readonly showPace = signal(true);

  protected readonly viewW = VIEW_W;
  protected readonly viewH = VIEW_H;
  protected readonly padL = PAD_L;
  protected readonly padR = PAD_R;
  protected readonly padT = PAD_T;
  protected readonly padB = PAD_B;

  readonly points = computed<ActivityStreamPoint[]>(() => this.data()?.points ?? []);

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
      ? this.activityService.stream(athleteId, activityId)
      : this.portal.activityStream(activityId);
    request.subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => { this.data.set(null); this.loading.set(false); },
    });
  }

  /** Courbe de FC : la plus lue, tracée par-dessus l'allure. */
  readonly hr = computed<Series | null>(() => this.series((p) => p.hr, false));

  /**
   * Courbe d'allure, <b>axe inversé</b> : une allure basse en secondes au kilomètre est une
   * allure rapide, et doit monter sur le graphique. Sans cette inversion, un fractionné se lit
   * à l'envers — les répétitions plongent et les récupérations culminent.
   */
  readonly pace = computed<Series | null>(() => this.series((p) => p.paceSPerKm, true));

  private series(pick: (p: ActivityStreamPoint) => number | null, invert: boolean): Series | null {
    const pts = this.points();
    const values = pts.map(pick).filter((v): v is number => v != null && v > 0);
    if (values.length < 2) return null;

    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const total = this.data()?.totalDistanceM || 1;
    const width = VIEW_W - PAD_L - PAD_R;
    const height = VIEW_H - PAD_T - PAD_B;

    // Les trous (arrêt, capteur muet) coupent le tracé au lieu d'être reliés en ligne droite :
    // une interpolation à travers une pause inventerait une FC qui n'a jamais été mesurée.
    let path = '';
    let pen = false;
    for (const p of pts) {
      const v = pick(p);
      if (v == null || v <= 0) { pen = false; continue; }
      const x = PAD_L + (Math.min(p.distanceM, total) / total) * width;
      const ratio = (v - min) / span;
      const y = invert
        ? PAD_T + ratio * height
        : PAD_T + (1 - ratio) * height;
      path += `${pen ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)} `;
      pen = true;
    }
    return path ? { path: path.trim(), min, max } : null;
  }

  /**
   * Graduations kilométriques, espacées pour rester lisibles : tous les kilomètres sur 10 km,
   * tous les 2 km sur 20, tous les 5 au-delà. Une graduation par kilomètre sur un marathon
   * donnerait quarante-deux étiquettes illisibles.
   */
  readonly ticks = computed<Tick[]>(() => {
    const total = this.data()?.totalDistanceM ?? 0;
    if (total < 1000) return [];
    const km = total / 1000;
    const stepKm = km <= 10 ? Math.max(1, Math.round(km / 5)) : km <= 25 ? 5 : 10;
    const width = VIEW_W - PAD_L - PAD_R;
    const out: Tick[] = [];
    for (let d = stepKm; d <= km; d += stepKm) {
      out.push({ x: PAD_L + ((d * 1000) / total) * width, label: `${d}` });
    }
    return out;
  });

  protected paceLabel(secPerKm: number): string {
    return formatPace(secPerKm) ?? '—';
  }

  /** Description textuelle du graphique — la courbe seule n'est lisible d'aucune autre façon. */
  protected ariaLabel(): string {
    const parts: string[] = [];
    const h = this.hr();
    const p = this.pace();
    if (h) parts.push(`fréquence cardiaque de ${h.min} à ${h.max} battements par minute`);
    if (p) parts.push(`allure de ${this.paceLabel(p.min)} à ${this.paceLabel(p.max)} par kilomètre`);
    const km = ((this.data()?.totalDistanceM ?? 0) / 1000).toFixed(1);
    return parts.length
      ? `Courbe sur ${km} km : ${parts.join(', ')}.`
      : 'Courbe de la sortie.';
  }
}
