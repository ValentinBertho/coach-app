import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Analytics } from '../../core/services/analytics.service';
import { AnalyticsService } from '../../core/services/analytics.service';
import { IntensityZoneBadgeComponent, type IntensityZone as ZoneNum } from '../../shared/components/physiology';
import { MetricCardComponent, SegmentedControlComponent, type SegmentOption } from '../../shared/components/ui';

interface Bar {
  label: string;
  planned: number;
  realized: number;
  px: number;
  rx: number;
  compliance: number | null;
}

/** Graphes de charge d'un athlète : volume hebdo prévu/réalisé, zones, adhérence (SVG pur). */
@Component({
  selector: 'app-analytics',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IntensityZoneBadgeComponent, MetricCardComponent, SegmentedControlComponent],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.scss',
})
export class AnalyticsComponent implements OnInit {
  readonly athleteId = input.required<string>();
  private readonly analyticsService = inject(AnalyticsService);

  readonly data = signal<Analytics | null>(null);
  readonly loading = signal(true);

  readonly period = signal('8');
  readonly periodOptions: SegmentOption[] = [
    { value: '4', label: '4 sem' },
    { value: '8', label: '8 sem' },
    { value: '12', label: '12 sem' },
  ];

  readonly maxKm = computed(() => {
    const d = this.data();
    if (!d) return 1;
    return Math.max(1, ...d.weeklyVolume.flatMap((w) => [w.plannedKm, w.realizedKm]));
  });

  readonly bars = computed<Bar[]>(() => {
    const d = this.data();
    if (!d) return [];
    const max = this.maxKm();
    return d.weeklyVolume.map((w) => ({
      label: w.weekStart.slice(5),
      planned: w.plannedKm,
      realized: w.realizedKm,
      px: Math.round((w.plannedKm / max) * 100),
      rx: Math.round((w.realizedKm / max) * 100),
      compliance: w.plannedKm > 0 ? Math.round((w.realizedKm / w.plannedKm) * 100) : null,
    }));
  });

  /** Totaux et compliance sur la période. */
  readonly totals = computed(() => {
    const d = this.data();
    if (!d) return null;
    const planned = d.weeklyVolume.reduce((s, w) => s + w.plannedKm, 0);
    const realized = d.weeklyVolume.reduce((s, w) => s + w.realizedKm, 0);
    return {
      planned: Math.round(planned),
      realized: Math.round(realized),
      compliance: planned > 0 ? Math.round((realized / planned) * 100) : null,
    };
  });

  readonly zones = computed(() => {
    const d = this.data();
    if (!d) return [];
    const entries = Object.entries(d.zoneDistribution);
    const max = Math.max(1, ...entries.map(([, v]) => v));
    return entries.map(([zone, count]) => ({
      zone, count, pct: Math.round((count / max) * 100), num: this.zoneNum(zone),
    }));
  });

  readonly statuses = computed(() => Object.entries(this.data()?.statusCounts ?? {}));

  ngOnInit(): void { this.reload(); }

  setPeriod(value: string): void {
    this.period.set(value);
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.analyticsService.get(this.athleteId(), Number(this.period())).subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  zoneNum(zone: string): ZoneNum | null {
    const n = Number(zone.replace(/\D/g, ''));
    return n >= 1 && n <= 5 ? (n as ZoneNum) : null;
  }

  /** Tonalité de compliance : alerte si < 70 %, succès si proche de 100. */
  complianceTone(pct: number | null): 'neutral' | 'alert' | 'success' {
    if (pct == null) return 'neutral';
    if (pct < 70) return 'alert';
    if (pct >= 90 && pct <= 110) return 'success';
    return 'neutral';
  }

  statusLabel(s: string): string {
    return { PLANNED: 'Prévu', COMPLETED: 'Réalisé', PARTIAL: 'Partiel', MISSED: 'Manqué' }[s] ?? s;
  }
  statusBadge(s: string): string {
    return { PLANNED: 'badge-info', COMPLETED: 'badge-success', PARTIAL: 'badge-warning', MISSED: 'badge-danger' }[s] ?? 'badge-neutral';
  }
}
