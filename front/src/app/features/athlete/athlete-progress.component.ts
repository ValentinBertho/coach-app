import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { DatePipe, DecimalPipe } from '@angular/common';
import { E1rmHistory, MyOneRm } from '../../core/models/strength.model';
import { Load } from '../../core/models/lactate.model';
import { Performance } from '../../core/models/physio.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { Analytics } from '../../core/services/analytics.service';
import { AcwrIndicatorComponent, DataOriginTagComponent, type DataOrigin } from '../../shared/components/physiology';
import { MetricCardComponent } from '../../shared/components/ui';

const SOURCE_ORIGIN: Record<string, DataOrigin> = {
  tested: 'mesure', estimated: 'calcule', manual: 'saisi',
};
const SOURCE_LABEL: Record<string, string> = {
  tested: 'Testé', estimated: 'Estimé', manual: 'Saisi',
};

/**
 * Mes progrès (athlète, mobile-first) — progression e1RM par exercice (CDC §10,
 * « Mon historique », lecture seule). Câblé sur /me/pp/1rm et /me/pp/1rm/{id}/history.
 */
@Component({
  selector: 'app-athlete-progress',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink, DatePipe, DecimalPipe, DataOriginTagComponent, AcwrIndicatorComponent, MetricCardComponent],
  template: `
    <div class="prog">
      <header class="prog-top">
        <h1 class="display-sm">Mes progrès</h1>
        <p class="subtitle">Ta charge et l'évolution de ta force.</p>
      </header>

      <!-- Accès rapides : Historique & Activités -->
      <nav class="quick">
        <a routerLink="/athlete/history" class="quick-l card">
          <app-icon name="calendar-days" [size]="20" /><span>Mon historique</span>
          <app-icon name="chevron-right" [size]="18" />
        </a>
        <a routerLink="/athlete/activities" class="quick-l card">
          <app-icon name="footprints" [size]="20" /><span>Mes activités</span>
          <app-icon name="chevron-right" [size]="18" />
        </a>
      </nav>

      <!-- Mon volume (analytics) -->
      @if (analytics(); as a) {
        <section class="sect">
          <h2 class="sect-h">Mon volume</h2>
          @if (totals(); as t) {
            <div class="charge-kpis">
              <app-metric-card label="Prévu (8 sem.)" [value]="t.planned" unit="km" origin="calcule" />
              <app-metric-card label="Réalisé (8 sem.)" [value]="t.realized" unit="km" origin="calcule" />
              <app-metric-card label="Adhérence" [value]="t.compliance != null ? t.compliance : '—'"
                [unit]="t.compliance != null ? '%' : ''" origin="calcule"
                [tone]="t.compliance != null && t.compliance >= 85 ? 'success' : (t.compliance != null && t.compliance < 60 ? 'alert' : 'neutral')" />
            </div>
          }
          @if (bars().length > 0) {
            <div class="card chart">
              <div class="chart-hd">
                <span class="field-hint">Volume hebdomadaire (km)</span>
                <span class="legend"><i class="sw planned"></i>Prévu <i class="sw realized"></i>Réalisé</span>
              </div>
              <div class="bars">
                @for (b of bars(); track b.label) {
                  <div class="bgrp" [title]="b.label + ' — prévu ' + b.planned + ' / réalisé ' + b.realized + ' km'">
                    <div class="bpair">
                      <span class="bar planned" [style.height.%]="b.px"></span>
                      <span class="bar realized" [style.height.%]="b.rx"></span>
                    </div>
                    <span class="blab">{{ b.label }}</span>
                  </div>
                }
              </div>
            </div>
          }
          @if (zones().length > 0) {
            <div class="card">
              <span class="field-hint">Répartition par zone</span>
              @for (z of zones(); track z.zone) {
                <div class="zline">
                  <span class="ztag">{{ z.zone }}</span>
                  <span class="ztrack"><span class="zfill" [style.width.%]="z.pct"></span></span>
                  <span class="metric zval">{{ z.count }}</span>
                </div>
              }
            </div>
          }
        </section>
      }

      <!-- Mes records -->
      @if (performances(); as ps) {
        @if (ps.length > 0) {
          <section class="sect">
            <h2 class="sect-h">Mes records</h2>
            <div class="card">
              <ul class="perf">
                @for (p of ps; track p.id) {
                  <li>
                    <span class="perf-d">{{ p.distanceCode }}</span>
                    <span class="perf-t metric">{{ fmtTime(p.timeSeconds) }}</span>
                    @if (p.vdot != null) { <span class="perf-v field-hint">VDOT {{ p.vdot | number: '1.0-1' }}</span> }
                    @if (p.dateSet) { <span class="perf-dt field-hint">{{ p.dateSet | date: 'MM/yy' }}</span> }
                  </li>
                }
              </ul>
            </div>
          </section>
        }
      }

      <!-- Ma charge d'entraînement -->
      @if (load(); as l) {
        <section class="charge">
          <h2 class="sect-h">Ma charge</h2>
          @if (l.ratio != null) {
            <div class="card"><app-acwr-indicator [value]="l.ratio" /></div>
          }
          <div class="charge-kpis">
            <app-metric-card label="Charge aiguë (7 j)" [value]="round(l.acuteLoad7d)" unit="UA" origin="calcule" />
            <app-metric-card label="Charge chronique (28 j)" [value]="round(l.chronicLoad28d)" unit="UA" origin="calcule" />
            <app-metric-card label="Monotonie" [value]="l.monotony != null ? l.monotony.toFixed(2) : '—'" origin="calcule"
              [tone]="(l.monotony ?? 0) >= 2 ? 'alert' : 'neutral'" />
          </div>
        </section>
      }

      <h2 class="sect-h">Ma force</h2>
      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 64px;"></div></div>
      } @else if (profiles().length === 0) {
        <div class="card empty">
          <h2>Pas encore de données</h2>
          <p class="field-hint">Tes 1RM apparaîtront ici après tes premières séances de force.</p>
        </div>
      } @else {
        @for (p of profiles(); track p.exerciseId) {
          <article class="ex card">
            <button type="button" class="ex-hd" (click)="toggle(p.exerciseId)" [attr.aria-expanded]="expanded() === p.exerciseId">
              <div class="ex-id">
                <strong>{{ p.exerciseName }}</strong>
                <span class="ex-src">
                  <app-data-origin-tag [origin]="origin(p.source)" [label]="sourceLabel(p.source)" />
                </span>
              </div>
              <div class="ex-val">
                <span class="metric">{{ p.rmKg | number: '1.0-1' }}</span><small>kg</small>
                <span class="ex-caret">{{ expanded() === p.exerciseId ? '▾' : '▸' }}</span>
              </div>
            </button>

            @if (expanded() === p.exerciseId) {
              @if (history()[p.exerciseId]; as h) {
                @if (h.length >= 2) {
                  @if (spark(h); as sp) {
                    <div class="spark">
                      <svg viewBox="0 0 240 48" preserveAspectRatio="none" class="spark-svg">
                        <polyline [attr.points]="sp.points" fill="none" stroke="var(--dari-violet)" stroke-width="2" />
                      </svg>
                      <span class="spark-delta" [class.up]="sp.delta >= 0" [class.down]="sp.delta < 0">
                        {{ sp.delta >= 0 ? '▲' : '▼' }} {{ sp.delta | number: '1.0-1' }} kg
                      </span>
                    </div>
                  }
                }
                <ul class="hist">
                  @for (e of h.slice().reverse(); track e.calculatedAt) {
                    <li>
                      <span class="hist-date metric">{{ e.calculatedAt | date: 'dd/MM/yy' }}</span>
                      <span class="hist-e metric">{{ e.e1rmKg | number: '1.0-1' }} kg</span>
                      <span class="hist-detail field-hint">{{ e.chargeKg }}kg × {{ e.reps }}@if (e.rpeOrRir) { · {{ e.rpeOrRir }} }</span>
                    </li>
                  } @empty {
                    <li class="field-hint">Aucun historique pour cet exercice.</li>
                  }
                </ul>
              } @else {
                <p class="field-hint">Chargement…</p>
              }
            }
          </article>
        }
      }
    </div>
  `,
  styles: [`
    .prog { max-width: 560px; margin-inline: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .prog-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: var(--sp-1) 0 0; }
    .empty { text-align: center; }

    .ex { padding: 0; overflow: hidden; }
    .ex-hd {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3);
      width: 100%; padding: var(--sp-4); background: transparent; border: none; cursor: pointer; text-align: left;
    }
    .ex-id { display: flex; flex-direction: column; gap: var(--sp-1); min-width: 0; }
    .ex-id strong { color: var(--ink); }
    .ex-val { display: flex; align-items: baseline; gap: 3px; flex-shrink: 0; }
    .ex-val .metric { font-size: var(--text-2xl); font-weight: 800; color: var(--ink); }
    .ex-val small { color: var(--ink-3); font-weight: 600; }
    .ex-caret { color: var(--ink-4); margin-left: var(--sp-2); font-size: var(--text-md); }

    .spark { display: flex; align-items: center; gap: var(--sp-3); padding: 0 var(--sp-4) var(--sp-2); }
    .spark-svg { width: 100%; height: 48px; flex: 1; }
    .spark-delta { font-size: var(--text-sm); font-weight: 800; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .spark-delta.up { color: var(--success-text); }
    .spark-delta.down { color: var(--danger-text); }

    .hist { list-style: none; margin: 0; padding: 0 var(--sp-4) var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-1); }
    .hist li { display: flex; align-items: baseline; gap: var(--sp-3); padding: var(--sp-1) 0; border-top: 1px solid var(--hairline); }
    .hist li:first-child { border-top: none; }
    .hist-date { color: var(--ink-3); font-size: var(--text-sm); width: 64px; }
    .hist-e { font-weight: 800; color: var(--ink); }
    .hist-detail { margin-left: auto; }

    .sect-h { font-size: var(--text-lg); margin: var(--sp-2) 0; }
    .sect { display: flex; flex-direction: column; gap: var(--sp-3); }
    .charge { display: flex; flex-direction: column; gap: var(--sp-3); }
    .charge-kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: var(--sp-3); }

    .quick { display: grid; gap: var(--sp-2); }
    .quick-l { display: flex; align-items: center; gap: var(--sp-3); padding: var(--sp-3) var(--sp-4); text-decoration: none; color: var(--ink); font-weight: 700; }
    .quick-l span { flex: 1; }
    .quick-l :last-child { color: var(--ink-4); }

    .chart { display: flex; flex-direction: column; gap: var(--sp-2); }
    .chart-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .legend { display: flex; align-items: center; gap: 4px; font-size: var(--text-xs); color: var(--ink-3); }
    .legend .sw { width: 10px; height: 10px; border-radius: 2px; display: inline-block; }
    .sw.planned { background: var(--ink-4); }
    .sw.realized { background: var(--dari-violet); }
    .bars { display: flex; align-items: flex-end; gap: 6px; height: 110px; }
    .bgrp { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; height: 100%; justify-content: flex-end; }
    .bpair { display: flex; align-items: flex-end; gap: 2px; height: 100%; width: 100%; justify-content: center; }
    .bar { width: 40%; min-height: 2px; border-radius: 3px 3px 0 0; }
    .bar.planned { background: var(--ink-4); }
    .bar.realized { background: var(--dari-violet); }
    .blab { font-size: 9px; color: var(--ink-4); white-space: nowrap; }

    .zline { display: flex; align-items: center; gap: var(--sp-2); padding: var(--sp-1) 0; }
    .ztag { width: 28px; font-size: var(--text-sm); font-weight: 700; color: var(--ink-3); }
    .ztrack { flex: 1; height: 8px; background: var(--paper-sunk); border-radius: var(--radius-full); overflow: hidden; }
    .zfill { display: block; height: 100%; background: var(--dari-violet); border-radius: var(--radius-full); }
    .zval { width: 32px; text-align: right; font-weight: 700; }

    .perf { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .perf li { display: flex; align-items: baseline; gap: var(--sp-3); padding: var(--sp-2) 0; border-top: 1px solid var(--hairline); }
    .perf li:first-child { border-top: none; }
    .perf-d { flex: 1; font-weight: 700; color: var(--ink); }
    .perf-t { font-weight: 800; color: var(--ink); font-variant-numeric: tabular-nums; }
    .perf-dt { width: 48px; text-align: right; }
  `],
})
export class AthleteProgressComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);

  readonly profiles = signal<MyOneRm[]>([]);
  readonly loading = signal(true);
  readonly expanded = signal<string | null>(null);
  readonly history = signal<Record<string, E1rmHistory[]>>({});
  readonly load = signal<Load | null>(null);
  readonly analytics = signal<Analytics | null>(null);
  readonly performances = signal<Performance[] | null>(null);

  /** Totaux volume + adhérence (% séances réalisées) sur la période. */
  readonly totals = computed(() => {
    const a = this.analytics();
    if (!a) return null;
    const planned = Math.round(a.weeklyVolume.reduce((s, w) => s + w.plannedKm, 0));
    const realized = Math.round(a.weeklyVolume.reduce((s, w) => s + w.realizedKm, 0));
    const counts = a.statusCounts ?? {};
    const total = Object.values(counts).reduce((s, n) => s + n, 0);
    const done = (counts['COMPLETED'] ?? 0) + (counts['PARTIAL'] ?? 0);
    const compliance = total > 0 ? Math.round((done / total) * 100) : null;
    return { planned, realized, compliance };
  });

  /** Barres de volume hebdo (hauteur en % du max prévu/réalisé). */
  readonly bars = computed(() => {
    const a = this.analytics();
    if (!a) return [];
    const max = Math.max(1, ...a.weeklyVolume.flatMap((w) => [w.plannedKm, w.realizedKm]));
    return a.weeklyVolume.map((w) => ({
      label: w.weekStart.slice(5),
      planned: Math.round(w.plannedKm * 10) / 10,
      realized: Math.round(w.realizedKm * 10) / 10,
      px: Math.round((w.plannedKm / max) * 100),
      rx: Math.round((w.realizedKm / max) * 100),
    }));
  });

  /** Répartition par zone (largeur en % du max). */
  readonly zones = computed(() => {
    const a = this.analytics();
    if (!a) return [];
    const entries = Object.entries(a.zoneDistribution ?? {}).filter(([, n]) => n > 0);
    const max = Math.max(1, ...entries.map(([, n]) => n));
    return entries.map(([zone, count]) => ({ zone, count, pct: Math.round((count / max) * 100) }));
  });

  round(v: number): number { return Math.round(v); }

  /** Chrono « h:mm:ss » ou « m:ss ». */
  fmtTime(s: number): string {
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    const pad = (n: number) => n.toString().padStart(2, '0');
    return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${m}:${pad(sec)}`;
  }

  ngOnInit(): void {
    this.portal.load().subscribe({ next: (l) => this.load.set(l), error: () => this.load.set(null) });
    this.portal.analytics(8).subscribe({ next: (a) => this.analytics.set(a), error: () => this.analytics.set(null) });
    this.portal.performances().subscribe({ next: (p) => this.performances.set(p), error: () => this.performances.set(null) });
    this.portal.my1rm().subscribe({
      next: (p) => { this.profiles.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  toggle(exerciseId: string): void {
    if (this.expanded() === exerciseId) { this.expanded.set(null); return; }
    this.expanded.set(exerciseId);
    if (!this.history()[exerciseId]) {
      this.portal.my1rmHistory(exerciseId).subscribe({
        next: (h) => this.history.update((m) => ({ ...m, [exerciseId]: h })),
        error: () => this.history.update((m) => ({ ...m, [exerciseId]: [] })),
      });
    }
  }

  origin(source: string): DataOrigin { return SOURCE_ORIGIN[source] ?? 'calcule'; }
  sourceLabel(source: string): string { return SOURCE_LABEL[source] ?? source; }

  /** Sparkline de l'évolution e1RM (points ordonnés croissants par le backend). */
  spark(h: E1rmHistory[]): { points: string; delta: number } {
    const vals = h.map((e) => e.e1rmKg);
    const min = Math.min(...vals);
    const max = Math.max(...vals);
    const span = max - min || 1;
    const n = vals.length;
    const points = vals
      .map((v, i) => {
        const x = n === 1 ? 120 : (i / (n - 1)) * 240;
        const y = 46 - ((v - min) / span) * 44;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
    return { points, delta: vals[n - 1] - vals[0] };
  }
}
