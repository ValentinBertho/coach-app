import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GroupAnalytics, TrainingGroupService } from '../../core/services/training-group.service';
import { MetricCardComponent, LoaderComponent } from '../../shared/components/ui';
import { IconComponent } from '../../shared/components/icon/icon.component';

const FORM_DOT: Record<string, string> = { GREEN: 'form-dot--green', ORANGE: 'form-dot--orange', RED: 'form-dot--red' };

/**
 * Vue agrégée d'un groupe pour le coach : état de forme, ACWR moyen, volume prévu/réalisé,
 * adhérence, et détail par athlète. Réutilise les calculs par athlète côté backend.
 */
@Component({
  selector: 'app-group-analytics',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, RouterLink, MetricCardComponent, LoaderComponent, IconComponent],
  template: `
    <section class="page-header">
      <div>
        <a routerLink="/app/groups" class="btn btn-ghost btn-sm">← Groupes</a>
        <h1 class="display-sm">{{ data()?.name || 'Groupe' }}</h1>
        <p class="subtitle">Vue d'ensemble du groupe · {{ data()?.athleteCount || 0 }} athlète(s) · 8 dernières semaines</p>
      </div>
    </section>

    @if (loading()) {
      <div class="card"><app-loader label="Agrégation du groupe…" /></div>
    } @else {
      @if (data(); as d) {
      @if (d.athleteCount === 0) {
        <div class="card empty-state"><h2>Groupe vide</h2><p class="field-hint">Ajoutez des athlètes à ce groupe pour voir ses analytics.</p></div>
      } @else {
      <div class="kpi-rail">
        <app-metric-card label="ACWR moyen" [value]="d.totals.avgAcwr ?? '—'" [decimals]="2" origin="calcule"
          [tone]="acwrTone(d.totals.avgAcwr)" />
        <app-metric-card label="Volume prévu" [value]="d.totals.plannedKm" [decimals]="1" unit="km" origin="calcule" />
        <app-metric-card label="Volume réalisé" [value]="d.totals.realizedKm" [decimals]="1" unit="km" origin="calcule" />
        <app-metric-card label="Adhérence" [value]="d.totals.compliancePct ?? '—'"
          [unit]="d.totals.compliancePct != null ? '%' : ''" origin="calcule"
          [tone]="complianceTone(d.totals.compliancePct)" />
      </div>

      <div class="card">
        <span class="stat-label">Répartition de la forme</span>
        <div class="distrib-bar" role="img"
             [attr.aria-label]="d.form.green + ' en forme, ' + d.form.orange + ' à surveiller, ' + d.form.red + ' en vigilance'">
          @if (d.form.green) { <span class="seg seg--green" [style.flex]="d.form.green"></span> }
          @if (d.form.orange) { <span class="seg seg--orange" [style.flex]="d.form.orange"></span> }
          @if (d.form.red) { <span class="seg seg--red" [style.flex]="d.form.red"></span> }
        </div>
        <div class="distrib-legend">
          <span><span class="form-dot form-dot--green"></span> {{ d.form.green }} en forme</span>
          <span><span class="form-dot form-dot--orange"></span> {{ d.form.orange }} à surveiller</span>
          <span><span class="form-dot form-dot--red"></span> {{ d.form.red }} en vigilance</span>
        </div>
      </div>

      <div class="card table-wrap">
        <table class="data-table">
          <thead>
            <tr><th>Athlète</th><th>Forme</th><th>ACWR</th><th>Prévu</th><th>Réalisé</th><th>Adhérence</th><th>Dernier retour</th></tr>
          </thead>
          <tbody>
            @for (r of d.athletes; track r.id) {
              <tr>
                <td><a [routerLink]="['/app/athletes', r.id]">{{ r.firstName }} {{ r.lastName }}</a></td>
                <td><span class="form-dot" [class]="dot(r.formStatus)"></span></td>
                <td class="metric">{{ r.acwr != null ? (r.acwr | number: '1.2-2') : '—' }}</td>
                <td class="metric">{{ r.plannedKm | number: '1.0-1' }}</td>
                <td class="metric">{{ r.realizedKm | number: '1.0-1' }}</td>
                <td class="metric">{{ r.compliancePct != null ? r.compliancePct + '%' : '—' }}</td>
                <td>{{ r.lastFeedbackDate ? (r.lastFeedbackDate | date: 'd MMM') : '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      }
      } @else {
        <div class="card empty-state"><h2>Indisponible</h2><p class="field-hint">Impossible de charger les analytics du groupe.</p></div>
      }
    }
  `,
  styles: [`
    .page-header h1 { margin: var(--sp-1) 0 0; }
    .kpi-rail { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: var(--sp-3); margin-bottom: var(--sp-4); }
    .distrib-bar { display: flex; height: 12px; border-radius: var(--radius-full); overflow: hidden; background: var(--paper-sunk); margin: var(--sp-2) 0; }
    .seg--green { background: var(--form-green); }
    .seg--orange { background: var(--form-orange); }
    .seg--red { background: var(--form-red); }
    .distrib-legend { display: flex; gap: var(--sp-4); flex-wrap: wrap; font-size: var(--text-sm); color: var(--ink-2); }
    .distrib-legend span { display: inline-flex; align-items: center; gap: var(--sp-1); }
  `],
})
export class GroupAnalyticsComponent implements OnInit {
  readonly id = input.required<string>();
  private readonly service = inject(TrainingGroupService);

  readonly data = signal<GroupAnalytics | null>(null);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.service.analytics(this.id()).subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  dot(status: string): string {
    return FORM_DOT[status] ?? 'form-dot--green';
  }

  acwrTone(acwr: number | null): 'neutral' | 'alert' | 'success' {
    if (acwr == null) return 'neutral';
    if (acwr > 1.3 || acwr < 0.8) return 'alert';
    return 'success';
  }

  complianceTone(pct: number | null): 'neutral' | 'alert' | 'success' {
    if (pct == null) return 'neutral';
    if (pct >= 85) return 'success';
    if (pct < 60) return 'alert';
    return 'neutral';
  }
}
