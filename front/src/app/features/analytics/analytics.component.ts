import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Analytics } from '../../core/services/analytics.service';
import { AnalyticsService } from '../../core/services/analytics.service';

interface Bar {
  label: string;
  planned: number;
  realized: number;
  px: number;
  rx: number;
}

/** Graphes de charge d'un athlète : volume hebdo prévu/réalisé, zones, adhérence (SVG pur). */
@Component({
  selector: 'app-analytics',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './analytics.component.html',
  styleUrl: './analytics.component.scss',
})
export class AnalyticsComponent implements OnInit {
  readonly athleteId = input.required<string>();
  private readonly analyticsService = inject(AnalyticsService);

  readonly data = signal<Analytics | null>(null);
  readonly loading = signal(true);

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
    }));
  });

  readonly zones = computed(() => {
    const d = this.data();
    if (!d) return [];
    const entries = Object.entries(d.zoneDistribution);
    const max = Math.max(1, ...entries.map(([, v]) => v));
    return entries.map(([zone, count]) => ({ zone, count, pct: Math.round((count / max) * 100) }));
  });

  readonly statuses = computed(() => Object.entries(this.data()?.statusCounts ?? {}));

  ngOnInit(): void {
    this.analyticsService.get(this.athleteId(), 8).subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  statusLabel(s: string): string {
    return { PLANNED: 'Prévu', COMPLETED: 'Réalisé', PARTIAL: 'Partiel', MISSED: 'Manqué' }[s] ?? s;
  }
  statusBadge(s: string): string {
    return { PLANNED: 'badge-info', COMPLETED: 'badge-success', PARTIAL: 'badge-warning', MISSED: 'badge-danger' }[s] ?? 'badge-neutral';
  }
}
