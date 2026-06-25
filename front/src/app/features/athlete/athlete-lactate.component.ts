import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { DataOriginTagComponent } from '../../shared/components/physiology';
import { LactateTest } from '../../core/models/lactate.model';

interface CurvePoint { x: number; y: number; speed: number; lactate: number; }
interface Curve {
  points: string;
  dots: CurvePoint[];
  lt1x: number | null;
  lt2x: number | null;
  xMin: number; xMax: number; yMax: number;
}

const W = 320, H = 180, PL = 34, PR = 10, PT = 12, PB = 26;

/**
 * Mes tests lactate (athlète, lecture seule) — historique + courbe de profil
 * (lactatémie en fonction de la vitesse, seuils LT1/LT2). Câblé sur /me/lactate-tests.
 * CDC §4 « Tests & seuils ».
 */
@Component({
  selector: 'app-athlete-lactate',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, RouterLink, DataOriginTagComponent],
  template: `
    <div class="lac">
      <header class="lac-top">
        <a routerLink="/athlete/progress" class="btn btn-ghost btn-sm">← Progrès</a>
        <h1 class="display-sm">Mes tests lactate</h1>
        <p class="subtitle">Tes tests labo et tes seuils mesurés.</p>
      </header>

      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 80px;"></div></div>
      } @else if (tests().length === 0) {
        <div class="card empty">
          <h2>Aucun test</h2>
          <p class="field-hint">Tes tests lactate réalisés avec ton coach apparaîtront ici.</p>
        </div>
      } @else {
        @for (t of tests(); track t.id) {
          <article class="card test">
            <button type="button" class="test-hd" (click)="toggle(t.id)" [attr.aria-expanded]="selected() === t.id">
              <div class="test-id">
                <strong>{{ t.testDate | date: 'd MMM yyyy' }}</strong>
                <span class="field-hint">{{ t.testType }}</span>
              </div>
              <div class="test-thr">
                @if (t.lt1Ms != null) { <span class="thr thr-1">LT1 {{ kmh(t.lt1Ms) | number: '1.1-1' }}</span> }
                @if (t.lt2Ms != null) { <span class="thr thr-2">LT2 {{ kmh(t.lt2Ms) | number: '1.1-1' }}</span> }
                <span class="caret">{{ selected() === t.id ? '▾' : '▸' }}</span>
              </div>
            </button>

            @if (selected() === t.id) {
              @if (detail(); as d) {
                <div class="detail">
                  <div class="meta">
                    <app-data-origin-tag origin="mesure" label="Mesuré" />
                    @if (d.lactateRest != null) { <span class="field-hint">Repos {{ d.lactateRest }} mmol/L</span> }
                    @if (d.hrMax != null) { <span class="field-hint">FC max {{ d.hrMax }} bpm</span> }
                  </div>

                  @if (curve(); as c) {
                    <svg class="chart" [attr.viewBox]="'0 0 ' + W + ' ' + H" preserveAspectRatio="xMidYMid meet" role="img"
                      aria-label="Courbe de lactatémie en fonction de la vitesse">
                      <!-- axes -->
                      <line [attr.x1]="PL" [attr.y1]="PT" [attr.x2]="PL" [attr.y2]="H - PB" class="axis" />
                      <line [attr.x1]="PL" [attr.y1]="H - PB" [attr.x2]="W - PR" [attr.y2]="H - PB" class="axis" />
                      <!-- seuils -->
                      @if (c.lt1x != null) {
                        <line [attr.x1]="c.lt1x" [attr.y1]="PT" [attr.x2]="c.lt1x" [attr.y2]="H - PB" class="thrline thrline-1" />
                      }
                      @if (c.lt2x != null) {
                        <line [attr.x1]="c.lt2x" [attr.y1]="PT" [attr.x2]="c.lt2x" [attr.y2]="H - PB" class="thrline thrline-2" />
                      }
                      <!-- courbe -->
                      <polyline [attr.points]="c.points" class="curve" fill="none" />
                      @for (p of c.dots; track p.speed) {
                        <circle [attr.cx]="p.x" [attr.cy]="p.y" r="3" class="dot" />
                      }
                      <!-- labels axes -->
                      <text [attr.x]="PL" [attr.y]="H - 8" class="lbl">{{ c.xMin | number: '1.1-1' }}</text>
                      <text [attr.x]="W - PR" [attr.y]="H - 8" class="lbl" text-anchor="end">{{ c.xMax | number: '1.1-1' }} km/h</text>
                      <text [attr.x]="4" [attr.y]="PT + 6" class="lbl">{{ c.yMax }}</text>
                      <text [attr.x]="4" [attr.y]="H - PB" class="lbl">0 mmol</text>
                    </svg>
                    <div class="legend">
                      <span><i class="sw sw-1"></i> LT1 (aérobie)</span>
                      <span><i class="sw sw-2"></i> LT2 (anaérobie)</span>
                    </div>
                  }

                  <ul class="steps">
                    <li class="steps-h">
                      <span>Vitesse</span><span>Lactate</span><span>FC</span>
                    </li>
                    @for (s of d.steps; track $index) {
                      <li>
                        <span class="metric">{{ kmh(s.speedMs) | number: '1.1-1' }} km/h</span>
                        <span class="metric">{{ s.lactate != null ? (s.lactate + ' mmol') : '—' }}</span>
                        <span class="metric">{{ s.hr != null ? (s.hr + ' bpm') : '—' }}</span>
                      </li>
                    }
                  </ul>
                </div>
              } @else {
                <p class="field-hint loading-d">Chargement…</p>
              }
            }
          </article>
        }
      }
    </div>
  `,
  styles: [`
    .lac { max-width: 560px; margin-inline: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .lac-top { display: flex; flex-direction: column; gap: var(--sp-1); align-items: flex-start; }
    .lac-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: 0; }
    .empty { text-align: center; }

    .test { padding: 0; overflow: hidden; }
    .test-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); width: 100%; padding: var(--sp-3) var(--sp-4); background: transparent; border: none; cursor: pointer; text-align: left; }
    .test-id { display: flex; flex-direction: column; gap: 2px; }
    .test-id strong { color: var(--ink); }
    .test-thr { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; justify-content: flex-end; }
    .thr { font-size: var(--text-sm); font-weight: 800; padding: 2px 8px; border-radius: var(--radius-full); font-variant-numeric: tabular-nums; }
    .thr-1 { background: var(--form-green-wash, var(--paper-sunk)); color: var(--form-green, var(--ink-2)); }
    .thr-2 { background: var(--form-orange-wash, var(--paper-sunk)); color: var(--form-orange, var(--ink-2)); }
    .caret { color: var(--ink-4); }

    .detail { padding: 0 var(--sp-4) var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .meta { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; }
    .chart { width: 100%; height: auto; background: var(--paper-sunk); border-radius: var(--radius); }
    .axis { stroke: var(--ink-4); stroke-width: 1; }
    .curve { stroke: var(--dari-violet); stroke-width: 2.5; }
    .dot { fill: var(--dari-violet); }
    .thrline { stroke-width: 1.5; stroke-dasharray: 4 3; }
    .thrline-1 { stroke: var(--form-green, #11c08b); }
    .thrline-2 { stroke: var(--form-orange, #ff8a3c); }
    .lbl { fill: var(--ink-3); font-size: 9px; font-family: var(--font-data, monospace); }
    .legend { display: flex; gap: var(--sp-4); font-size: var(--text-xs); color: var(--ink-3); }
    .legend .sw { display: inline-block; width: 14px; height: 0; border-top: 2px dashed; vertical-align: middle; }
    .sw-1 { border-color: var(--form-green, #11c08b); }
    .sw-2 { border-color: var(--form-orange, #ff8a3c); }

    .steps { list-style: none; margin: 0; padding: 0; }
    .steps li { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: var(--sp-2); padding: var(--sp-1) 0; border-top: 1px solid var(--hairline); font-size: var(--text-sm); }
    .steps li:first-child { border-top: none; }
    .steps-h { color: var(--ink-3); font-weight: 700; }
    .loading-d { padding: 0 var(--sp-4) var(--sp-4); }
  `],
})
export class AthleteLactateComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);

  protected readonly W = W; protected readonly H = H;
  protected readonly PL = PL; protected readonly PR = PR;
  protected readonly PT = PT; protected readonly PB = PB;

  readonly loading = signal(true);
  readonly tests = signal<LactateTest[]>([]);
  readonly selected = signal<string | null>(null);
  readonly detail = signal<LactateTest | null>(null);

  readonly curve = computed<Curve | null>(() => {
    const d = this.detail();
    if (!d) return null;
    const pts = d.steps
      .filter((s) => s.speedMs != null && s.lactate != null)
      .map((s) => ({ speed: this.kmh(s.speedMs)!, lactate: s.lactate as number }))
      .sort((a, b) => a.speed - b.speed);
    if (pts.length < 2) return null;

    const xMin = pts[0].speed;
    const xMax = pts[pts.length - 1].speed;
    const yMax = Math.max(2, Math.ceil(Math.max(...pts.map((p) => p.lactate))));
    const xspan = xMax - xMin || 1;
    const sx = (v: number) => PL + ((v - xMin) / xspan) * (W - PL - PR);
    const sy = (v: number) => (H - PB) - (v / yMax) * (H - PT - PB);

    const dots: CurvePoint[] = pts.map((p) => ({ x: sx(p.speed), y: sy(p.lactate), speed: p.speed, lactate: p.lactate }));
    const points = dots.map((p) => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ');
    const lt1 = this.kmh(d.lt1Ms);
    const lt2 = this.kmh(d.lt2Ms);
    return {
      points, dots, xMin, xMax, yMax,
      lt1x: lt1 != null ? sx(lt1) : null,
      lt2x: lt2 != null ? sx(lt2) : null,
    };
  });

  ngOnInit(): void {
    this.portal.lactateTests().subscribe({
      next: (t) => { this.tests.set(t); this.loading.set(false); },
      error: () => { this.tests.set([]); this.loading.set(false); },
    });
  }

  toggle(testId: string): void {
    if (this.selected() === testId) { this.selected.set(null); return; }
    this.selected.set(testId);
    this.detail.set(null);
    this.portal.lactateTest(testId).subscribe({
      next: (d) => { if (this.selected() === testId) this.detail.set(d); },
      error: () => this.detail.set(null),
    });
  }

  /** m/s → km/h, arrondi au dixième. */
  kmh(ms: number | null): number | null {
    return ms == null ? null : Math.round(ms * 3.6 * 10) / 10;
  }
}
