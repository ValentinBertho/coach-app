import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LoadSeries } from '../../../core/models/lactate.model';

/** Géométrie prête à peindre (le template ne calcule rien). */
interface Geometry {
  /** Aire de la charge chronique (CTL) : le « fond de forme ». */
  ctlArea: string;
  /** Ligne de la charge aiguë (ATL) : ce que l'athlète encaisse là, maintenant. */
  atlLine: string;
  /** Bande de sécurité ACWR projetée sur l'échelle de charge (0,8 × CTL → 1,3 × CTL). */
  safeBand: string;
  maxLoad: number;
  gridY: { y: number; label: string }[];
  ticks: { x: number; label: string }[];
  last: { acute: number; chronic: number; ratio: number | null } | null;
}

const W = 720;
const H = 200;
const PAD_L = 40;
const PAD_B = 22;
const PAD_T = 10;

/**
 * Courbe temporelle de charge d'entraînement : aire CTL (charge chronique), ligne ATL (charge
 * aiguë) et bande de sécurité ACWR annotée (blueprint §Erreurs UX).
 *
 * Les écrans n'affichaient que des valeurs instantanées : on voyait un ACWR à 1,4 sans savoir
 * s'il montait depuis trois semaines ou s'il venait de décrocher. La bande de sécurité est
 * dessinée dans l'échelle de charge (0,8 × CTL à 1,3 × CTL) : quand la ligne ATL en sort par le
 * haut, la charge aiguë est trop forte pour le fond de forme du moment.
 *
 * SVG maison, comme les autres graphes du produit — aucune dépendance ajoutée.
 */
@Component({
  selector: 'app-load-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    @if (geometry(); as g) {
      <figure class="lc">
        <svg class="lc__svg" [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img"
             [attr.aria-label]="ariaLabel()">
          <!-- Repères horizontaux de charge -->
          @for (gy of g.gridY; track gy.y) {
            <line class="lc__grid" [attr.x1]="padL" [attr.y1]="gy.y" [attr.x2]="W" [attr.y2]="gy.y" />
            <text class="lc__axis" [attr.x]="padL - 6" [attr.y]="gy.y + 3" text-anchor="end">{{ gy.label }}</text>
          }

          <!-- Bande de sécurité ACWR : zone où la charge aiguë reste tenable -->
          <path class="lc__band" [attr.d]="g.safeBand" />

          <!-- Fond de forme (CTL) en aire, effort du moment (ATL) en ligne -->
          <path class="lc__ctl" [attr.d]="g.ctlArea" />
          <path class="lc__atl" [attr.d]="g.atlLine" />

          <!-- Repères de dates -->
          @for (t of g.ticks; track t.x) {
            <text class="lc__axis" [attr.x]="t.x" [attr.y]="H - 6" text-anchor="middle">{{ t.label }}</text>
          }
        </svg>

        <figcaption class="lc__legend">
          <span class="lc__lg"><span class="lc__swatch lc__swatch--ctl"></span> Charge chronique (CTL)</span>
          <span class="lc__lg"><span class="lc__swatch lc__swatch--atl"></span> Charge aiguë (ATL)</span>
          <span class="lc__lg"><span class="lc__swatch lc__swatch--band"></span> Bande de sécurité ACWR {{ series()!.safeRatioMin }}–{{ series()!.safeRatioMax }}</span>
          @if (g.last; as l) {
            <span class="lc__now metric">
              Aujourd'hui : {{ l.acute | number: '1.0-0' }} UA aiguë · {{ l.chronic | number: '1.0-0' }} UA chronique
              @if (l.ratio != null) { · ACWR {{ l.ratio }} }
            </span>
          }
        </figcaption>
      </figure>
    } @else {
      <p class="field-hint">
        Pas encore assez de retours de séance pour tracer la charge. La courbe apparaîtra dès que
        l'athlète aura renseigné quelques RPE.
      </p>
    }
  `,
  styles: [`
    .lc { margin: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .lc__svg { width: 100%; height: auto; overflow: visible; }
    .lc__grid { stroke: var(--hairline); stroke-width: 1; }
    .lc__axis { fill: var(--ink-3); font-size: var(--text-2xs); font-family: var(--font-data); }
    .lc__band { fill: var(--zone-2); opacity: 0.16; }
    .lc__ctl { fill: var(--primary); opacity: 0.18; }
    .lc__atl { fill: none; stroke: var(--energy); stroke-width: 2; stroke-linejoin: round; }
    .lc__legend { display: flex; flex-wrap: wrap; gap: var(--sp-3); font-size: var(--text-xs); color: var(--ink-3); }
    .lc__lg { display: inline-flex; align-items: center; gap: var(--sp-1); }
    .lc__swatch { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
    .lc__swatch--ctl { background: var(--primary); opacity: 0.4; }
    .lc__swatch--atl { background: var(--energy); }
    .lc__swatch--band { background: var(--zone-2); opacity: 0.35; }
    .lc__now { margin-left: auto; color: var(--ink-2); }
  `],
})
export class LoadChartComponent {
  readonly series = input<LoadSeries | null>(null);

  protected readonly W = W;
  protected readonly H = H;
  protected readonly padL = PAD_L;

  readonly geometry = computed<Geometry | null>(() => {
    const s = this.series();
    const pts = s?.points ?? [];
    // Une courbe sur deux points ne raconte rien ; et sans charge, tout serait plat à zéro.
    if (!s || pts.length < 3) return null;
    const maxRaw = Math.max(...pts.map((p) => Math.max(p.acute, p.chronic * s.safeRatioMax)));
    if (maxRaw <= 0) return null;

    const maxLoad = this.niceCeil(maxRaw);
    const x = (i: number) => PAD_L + (i / (pts.length - 1)) * (W - PAD_L);
    const y = (v: number) => PAD_T + (1 - v / maxLoad) * (H - PAD_T - PAD_B);

    const ctlArea =
      `M ${x(0)} ${y(0)} ` +
      pts.map((p, i) => `L ${x(i)} ${y(p.chronic)}`).join(' ') +
      ` L ${x(pts.length - 1)} ${y(0)} Z`;

    const atlLine = 'M ' + pts.map((p, i) => `${x(i)} ${y(p.acute)}`).join(' L ');

    // La bande suit le CTL : elle se resserre quand le fond de forme baisse.
    const safeBand =
      'M ' + pts.map((p, i) => `${x(i)} ${y(p.chronic * s.safeRatioMax)}`).join(' L ') +
      ' L ' + [...pts].reverse().map((p, i) =>
        `${x(pts.length - 1 - i)} ${y(p.chronic * s.safeRatioMin)}`).join(' L ') + ' Z';

    const gridY = [0, 0.5, 1].map((f) => ({
      y: y(maxLoad * f),
      label: String(Math.round(maxLoad * f)),
    }));

    const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
    const tickCount = Math.min(5, pts.length);
    const ticks = Array.from({ length: tickCount }, (_, k) => {
      const i = Math.round((k / (tickCount - 1)) * (pts.length - 1));
      return { x: x(i), label: fmt.format(this.parse(pts[i].date)) };
    });

    const lastPoint = pts[pts.length - 1];
    return {
      ctlArea, atlLine, safeBand, maxLoad, gridY, ticks,
      last: { acute: lastPoint.acute, chronic: lastPoint.chronic, ratio: lastPoint.ratio },
    };
  });

  ariaLabel(): string {
    const g = this.geometry();
    if (!g?.last) return 'Courbe de charge d’entraînement';
    return `Courbe de charge : charge aiguë ${Math.round(g.last.acute)} UA, `
      + `charge chronique ${Math.round(g.last.chronic)} UA`
      + (g.last.ratio != null ? `, ACWR ${g.last.ratio}` : '');
  }

  /** Date ISO → Date locale (pas de décalage de fuseau sur une date sans heure). */
  private parse(iso: string): Date {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  /** Borne haute « ronde » pour que l'axe se lise (250, 500, 1000…). */
  private niceCeil(v: number): number {
    const mag = Math.pow(10, Math.floor(Math.log10(v)));
    return Math.ceil(v / (mag / 2)) * (mag / 2);
  }
}
