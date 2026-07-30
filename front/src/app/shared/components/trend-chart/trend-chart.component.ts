import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Un point de la série : date ISO (ou horodatage) + valeur mesurée. */
export interface TrendPoint {
  date: string;
  value: number;
}

interface Geometry {
  line: string;
  dots: { cx: number; cy: number; title: string }[];
  gridY: { y: number; label: string }[];
  ticks: { x: number; label: string }[];
  first: number;
  last: number;
  delta: number;
}

const W = 640;
const H = 170;
const PAD_L = 44;
const PAD_B = 20;
const PAD_T = 10;

/**
 * Courbe d'évolution d'une mesure dans le temps (e1RM, VDOT, records…).
 *
 * L'audit relevait que la donnée de progression existait partout mais n'était jamais montrée :
 * une liste de valeurs ne dit pas si l'athlète progresse. Affiche la tendance, les points
 * mesurés et l'écart entre le premier et le dernier relevé.
 *
 * SVG maison, cohérent avec les autres graphes du produit — aucune dépendance ajoutée.
 */
@Component({
  selector: 'app-trend-chart',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    @if (geometry(); as g) {
      <figure class="tc">
        <svg class="tc__svg" [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="ariaLabel()">
          @for (gy of g.gridY; track gy.y) {
            <line class="tc__grid" [attr.x1]="padL" [attr.y1]="gy.y" [attr.x2]="W" [attr.y2]="gy.y" />
            <text class="tc__axis" [attr.x]="padL - 6" [attr.y]="gy.y + 3" text-anchor="end">{{ gy.label }}</text>
          }
          <path class="tc__line" [attr.d]="g.line" [attr.stroke]="color()" />
          @for (d of g.dots; track $index) {
            <circle class="tc__dot" [attr.cx]="d.cx" [attr.cy]="d.cy" r="3.5" [attr.fill]="color()">
              <title>{{ d.title }}</title>
            </circle>
          }
          @for (t of g.ticks; track t.x) {
            <text class="tc__axis" [attr.x]="t.x" [attr.y]="H - 5" text-anchor="middle">{{ t.label }}</text>
          }
        </svg>
        <figcaption class="tc__legend">
          <span>{{ label() }}</span>
          <span class="tc__delta metric" [class.tc__delta--up]="g.delta > 0" [class.tc__delta--down]="g.delta < 0">
            {{ g.delta > 0 ? '+' : '' }}{{ g.delta | number: '1.0-1' }} {{ unit() }} depuis le premier relevé
          </span>
        </figcaption>
      </figure>
    } @else {
      <p class="field-hint">{{ emptyHint() }}</p>
    }
  `,
  styles: [`
    .tc { margin: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .tc__svg { width: 100%; height: auto; overflow: visible; }
    .tc__grid { stroke: var(--hairline); stroke-width: 1; }
    .tc__axis { fill: var(--ink-3); font-size: 10px; font-family: var(--font-data); }
    .tc__line { fill: none; stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .tc__legend {
      display: flex; gap: var(--sp-3); flex-wrap: wrap; align-items: baseline;
      font-size: var(--text-xs); color: var(--ink-3);
    }
    .tc__delta { margin-left: auto; color: var(--ink-2); }
    .tc__delta--up { color: var(--success-text, var(--dari-teal)); }
    .tc__delta--down { color: var(--danger-text, var(--dari-red)); }
  `],
})
export class TrendChartComponent {
  readonly points = input<TrendPoint[]>([]);
  readonly label = input('Évolution');
  readonly unit = input('');
  readonly color = input('var(--primary)');
  readonly emptyHint = input('Pas encore assez de mesures pour tracer une évolution.');

  protected readonly W = W;
  protected readonly H = H;
  protected readonly padL = PAD_L;

  readonly geometry = computed<Geometry | null>(() => {
    // Deux points suffisent à faire une tendance ; un seul ne dit rien.
    const pts = [...this.points()].sort((a, b) => a.date.localeCompare(b.date));
    if (pts.length < 2) return null;

    const values = pts.map((p) => p.value);
    const rawMin = Math.min(...values);
    const rawMax = Math.max(...values);
    // Échelle resserrée autour des valeurs (une progression de 2 kg sur 100 doit rester lisible),
    // avec une marge pour que les points ne collent pas aux bords.
    const span = rawMax - rawMin || Math.max(1, rawMax * 0.1);
    const min = rawMin - span * 0.15;
    const max = rawMax + span * 0.15;

    const x = (i: number) => PAD_L + (i / (pts.length - 1)) * (W - PAD_L);
    const y = (v: number) => PAD_T + (1 - (v - min) / (max - min)) * (H - PAD_T - PAD_B);

    const fmtDate = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
    const line = 'M ' + pts.map((p, i) => `${x(i)} ${y(p.value)}`).join(' L ');
    const dots = pts.map((p, i) => ({
      cx: x(i),
      cy: y(p.value),
      title: `${fmtDate.format(this.parse(p.date))} — ${p.value} ${this.unit()}`.trim(),
    }));

    const gridY = [min, (min + max) / 2, max].map((v) => ({
      y: y(v),
      label: this.short(v),
    }));

    const tickCount = Math.min(4, pts.length);
    const ticks = Array.from({ length: tickCount }, (_, k) => {
      const i = Math.round((k / (tickCount - 1)) * (pts.length - 1));
      return { x: x(i), label: fmtDate.format(this.parse(pts[i].date)) };
    });

    const first = values[0];
    const last = values[values.length - 1];
    return { line, dots, gridY, ticks, first, last, delta: Math.round((last - first) * 10) / 10 };
  });

  ariaLabel(): string {
    const g = this.geometry();
    if (!g) return this.label();
    return `${this.label()} : de ${g.first} à ${g.last} ${this.unit()}`.trim();
  }

  /** Accepte une date ISO seule (2026-07-30) comme un horodatage complet. */
  private parse(value: string): Date {
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
      const [y, m, d] = value.split('-').map(Number);
      return new Date(y, m - 1, d);
    }
    return new Date(value);
  }

  private short(v: number): string {
    return Math.abs(v) >= 100 ? String(Math.round(v)) : String(Math.round(v * 10) / 10);
  }
}
