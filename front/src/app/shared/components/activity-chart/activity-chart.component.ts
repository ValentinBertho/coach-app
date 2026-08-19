import {
  ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, signal,
  viewChild,
} from '@angular/core';
import { ActivityStream, ActivityStreamPoint } from '../../../core/models/activity.model';
import { ActivityService } from '../../../core/services/activity.service';
import { AthletePortalService } from '../../../core/services/athlete-portal.service';
import { formatPace } from '../../../core/utils/pace';
import { IconComponent } from '../icon/icon.component';
import { SelectionStats, selectionStats } from './activity-chart-selection';

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

/**
 * Largeur minimale d'une sélection, en unités du viewBox (320 de large). En deçà, le geste est
 * un appui, pas un brossage : c'est ce qui permet d'effacer la sélection d'un clic, et cela
 * évite qu'un doigt hésitant n'ouvre un bandeau de moyennes portant sur quelques mètres.
 */
const MIN_BRUSH_X = 4;

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

        <svg #svg [attr.viewBox]="'0 0 ' + viewW + ' ' + viewH" preserveAspectRatio="none"
             role="img" [attr.aria-label]="ariaLabel()"
             class="ch-svg" [class.is-brushing]="brushing()"
             (pointerdown)="onBrushStart($event)" (pointermove)="onBrushMove($event)"
             (pointerup)="onBrushEnd($event)" (pointercancel)="onBrushEnd($event)">
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

          <!-- Portion sélectionnée : un voile plutôt qu'un cadre, pour que la courbe reste
               lisible dessous — c'est elle qu'on regarde, la sélection ne fait que la borner. -->
          @if (brush(); as b) {
            <rect class="ch-brush" [attr.x]="b.x1" [attr.width]="b.x2 - b.x1"
                  [attr.y]="padT" [attr.height]="viewH - padT - padB" />
            <line class="ch-brush-edge" [attr.x1]="b.x1" [attr.x2]="b.x1"
                  [attr.y1]="padT" [attr.y2]="viewH - padB" />
            <line class="ch-brush-edge" [attr.x1]="b.x2" [attr.x2]="b.x2"
                  [attr.y1]="padT" [attr.y2]="viewH - padB" />
          }

          <!-- Bornes de l'axe FC, à gauche : une courbe sans échelle ne se compare à rien. -->
          @if (showHr() && hr(); as h) {
            <text class="ch-ax-lb" [attr.x]="4" [attr.y]="padT + 8">{{ h.max }}</text>
            <text class="ch-ax-lb" [attr.x]="4" [attr.y]="viewH - padB - 2">{{ h.min }}</text>
          }
        </svg>

        <!--
          Ce que la portion sélectionnée dit. La consigne d'usage n'apparaît que tant que rien
          n'est sélectionné : une fois le geste connu, la répéter sous chaque graphique revient à
          occuper la place des chiffres qu'on est venu lire.
        -->
        @if (stats(); as st) {
          <div class="ch-sel">
            <span class="ch-sel__range metric">{{ rangeLabel() }}</span>
            <span class="ch-sel__vals metric">
              {{ kmLabel(st.distanceM) }}<small>km</small>
              · {{ durationLabel(st.durationS) }}
              @if (st.paceSPerKm !== null) { · {{ paceLabel(st.paceSPerKm) }}<small>/km</small> }
              @if (st.avgHr !== null) { · {{ st.avgHr }}<small>bpm</small> }
            </span>
            <button type="button" class="ch-sel__x" (click)="clearBrush()"
                    aria-label="Effacer la sélection">
              <app-icon name="x" [size]="14" />
            </button>
          </div>
        } @else {
          <p class="ch-hint field-hint">
            Sélectionne une portion de la courbe pour en connaître les moyennes.
          </p>
        }
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
    /* 11 px : le plancher de lisibilité du produit vaut aussi pour les axes d'un graphique. */
    .ch-tick, .ch-ax-lb { fill: var(--ink-4); font-size: 11px; font-family: var(--font-data); }
    .ch-tick { text-anchor: middle; }
    .ch-line { fill: none; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; vector-effect: non-scaling-stroke; }
    .ch-line--hr { stroke: var(--zone-5); }
    .ch-line--pace { stroke: var(--primary); }

    /* pan-y : le brossage est horizontal, le défilement de la page reste vertical. Sans cela,
       sur mobile, tout glissement sur le graphique ferait défiler la page au lieu de sélectionner
       — ou, avec touch-action: none, la page deviendrait impossible à faire défiler depuis le
       graphique. */
    .ch-svg { touch-action: pan-y; cursor: crosshair; }
    .ch-svg.is-brushing { cursor: col-resize; }
    .ch-brush { fill: var(--primary); opacity: 0.14; }
    .ch-brush-edge { stroke: var(--primary); stroke-width: 1; vector-effect: non-scaling-stroke; }

    .ch-sel {
      display: flex; align-items: center; gap: var(--sp-2);
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius-sm);
      background: var(--paper-sunk); border: 1px solid var(--hairline);
      font-size: var(--text-sm);
    }
    .ch-sel__range { color: var(--ink-3); flex-shrink: 0; }
    .ch-sel__vals { color: var(--ink); font-weight: 600; flex: 1; min-width: 0; }
    .ch-sel__vals small { font-weight: 500; color: var(--ink-3); margin-left: 1px; }
    .ch-sel__x {
      display: inline-flex; align-items: center; justify-content: center;
      min-width: 28px; min-height: 28px; flex-shrink: 0;
      border: none; background: transparent; color: var(--ink-3); cursor: pointer;
    }
    .ch-hint { margin: 0; }

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

  private readonly svg = viewChild<ElementRef<SVGSVGElement>>('svg');

  /** Bornes de la sélection, en coordonnées du viewBox. Nulles tant que rien n'est sélectionné. */
  readonly brush = signal<{ x1: number; x2: number } | null>(null);
  /** Un brossage est en cours : le curseur change, et le relâchement décidera de le garder. */
  readonly brushing = signal(false);
  private brushAnchorX: number | null = null;

  /** Statistiques de la portion sélectionnée, ou `null` si la sélection ne couvre rien. */
  readonly stats = computed<SelectionStats | null>(() => {
    const b = this.brush();
    if (!b) return null;
    return selectionStats(this.points(), this.distanceAt(b.x1), this.distanceAt(b.x2));
  });

  /** « km 2,4 → 5,1 » : où l'on est dans la sortie, pas seulement combien on y a couru. */
  protected rangeLabel(): string {
    const b = this.brush();
    if (!b) return '';
    const from = this.distanceAt(b.x1) / 1000;
    const to = this.distanceAt(b.x2) / 1000;
    return `km ${this.km(from)} → ${this.km(to)}`;
  }

  private km(value: number): string {
    return value.toFixed(1).replace('.', ',');
  }

  protected kmLabel(metres: number): string {
    return this.km(metres / 1000);
  }

  protected durationLabel(seconds: number): string {
    const m = Math.floor(seconds / 60);
    const s = Math.round(seconds % 60);
    const h = Math.floor(m / 60);
    return h > 0
      ? `${h} h ${String(m % 60).padStart(2, '0')}`
      : `${m}'${String(s).padStart(2, '0')}`;
  }

  // --- Brossage ------------------------------------------------------------------------------

  onBrushStart(event: PointerEvent): void {
    const x = this.viewX(event);
    if (x === null) return;
    this.brushAnchorX = x;
    this.brushing.set(true);
    this.brush.set(null);
    (event.target as Element).setPointerCapture?.(event.pointerId);
  }

  onBrushMove(event: PointerEvent): void {
    if (this.brushAnchorX === null) return;
    const x = this.viewX(event);
    if (x === null) return;
    this.brush.set({
      x1: Math.min(this.brushAnchorX, x),
      x2: Math.max(this.brushAnchorX, x),
    });
  }

  /**
   * Fin du geste. Une sélection plus étroite que {@link MIN_BRUSH_PX} est traitée comme un
   * simple appui : c'est ainsi qu'on efface la sélection, et cela évite qu'un clic maladroit
   * n'ouvre un bandeau de moyennes portant sur trois mètres.
   */
  onBrushEnd(event: PointerEvent): void {
    this.brushAnchorX = null;
    this.brushing.set(false);
    (event.target as Element).releasePointerCapture?.(event.pointerId);
    const b = this.brush();
    if (b && b.x2 - b.x1 < MIN_BRUSH_X) {
      this.brush.set(null);
    }
  }

  clearBrush(): void {
    this.brush.set(null);
  }

  /** Abscisse de l'événement dans le repère du viewBox, ou `null` hors du tracé. */
  private viewX(event: PointerEvent): number | null {
    const el = this.svg()?.nativeElement;
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    if (rect.width <= 0) return null;
    const raw = ((event.clientX - rect.left) / rect.width) * VIEW_W;
    // Bornée au tracé : la marge de gauche porte les étiquettes de l'axe, pas de la donnée.
    return Math.min(VIEW_W - PAD_R, Math.max(PAD_L, raw));
  }

  /** Distance (mètres) correspondant à une abscisse du viewBox. */
  private distanceAt(x: number): number {
    const total = this.data()?.totalDistanceM ?? 0;
    const width = VIEW_W - PAD_L - PAD_R;
    if (total <= 0 || width <= 0) return 0;
    return ((x - PAD_L) / width) * total;
  }

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
