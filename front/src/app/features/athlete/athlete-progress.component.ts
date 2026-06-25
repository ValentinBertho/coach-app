import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { E1rmHistory, MyOneRm } from '../../core/models/strength.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { DataOriginTagComponent, type DataOrigin } from '../../shared/components/physiology';

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
  imports: [DatePipe, DecimalPipe, DataOriginTagComponent],
  template: `
    <div class="prog">
      <header class="prog-top">
        <h1 class="display-sm">Mes progrès</h1>
        <p class="subtitle">Évolution de ta force par exercice.</p>
      </header>

      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 64px;"></div></div>
      } @else if (profiles().length === 0) {
        <div class="card empty">
          <h2>Pas encore de données 💪</h2>
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
  `],
})
export class AthleteProgressComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);

  readonly profiles = signal<MyOneRm[]>([]);
  readonly loading = signal(true);
  readonly expanded = signal<string | null>(null);
  readonly history = signal<Record<string, E1rmHistory[]>>({});

  ngOnInit(): void {
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
