import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Analytics } from '../../core/services/analytics.service';
import { AnalyticsService } from '../../core/services/analytics.service';
import {
  IntensityZoneBadgeComponent,
  type IntensityZone as ZoneNum,
} from '../../shared/components/physiology';
import {
  MetricCardComponent,
  SegmentedControlComponent,
  type SegmentOption,
} from '../../shared/components/ui';

interface Bar {
  label: string;
  planned: number;
  realized: number;
  px: number;
  rx: number;
  compliance: number | null;
}

/**
 * Panneau « Volume & progression » de l'onglet Charge : volume hebdo prévu/réalisé, zones,
 * adhérence (SVG pur). Fusionné dans {@link LoadComponent} — il n'a plus de route propre.
 */
@Component({
  selector: 'app-analytics',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IntensityZoneBadgeComponent,
    MetricCardComponent,
    SegmentedControlComponent,
  ],
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
    return Math.max(
      1,
      ...d.weeklyVolume.flatMap((w) => [w.plannedKm, w.realizedKm]),
    );
  });

  readonly bars = computed<Bar[]>(() => {
    const d = this.data();
    if (!d) return [];
    const max = this.maxKm();
    // Les semaines vides en tête (avant le premier plan ou la première sortie) n'apprennent
    // rien et tassaient les barres utiles à droite du graphe.
    const first = d.weeklyVolume.findIndex((w) => w.plannedKm > 0 || w.realizedKm > 0);
    const weeks = first > 0 ? d.weeklyVolume.slice(first) : d.weeklyVolume;
    return weeks.map((w) => ({
      label: w.weekStart.slice(5),
      planned: w.plannedKm,
      realized: w.realizedKm,
      px: Math.round((w.plannedKm / max) * 100),
      rx: Math.round((w.realizedKm / max) * 100),
      compliance:
        w.plannedKm > 0 ? Math.round((w.realizedKm / w.plannedKm) * 100) : null,
    }));
  });

  /** Totaux et compliance sur la période. */
  readonly totals = computed(() => {
    const d = this.data();
    if (!d) return null;
    const planned = d.weeklyVolume.reduce((s, w) => s + w.plannedKm, 0);
    const realized = d.weeklyVolume.reduce((s, w) => s + w.realizedKm, 0);
    // L'adhérence ne compte que les semaines terminées ET planifiées : la semaine en cours
    // (ses séances de demain sont « prévues » mais pas encore « manquées ») et les semaines
    // sans plan faisaient tomber le taux à 36 % chez un athlète qui faisait tout.
    const today = new Date();
    const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
    const closed = d.weeklyVolume.filter((w) => w.plannedKm > 0 && this.weekEnd(w.weekStart) < todayIso);
    const cPlanned = closed.reduce((s, w) => s + w.plannedKm, 0);
    const cRealized = closed.reduce((s, w) => s + w.realizedKm, 0);
    return {
      planned: Math.round(planned),
      realized: Math.round(realized),
      compliance: cPlanned > 0 ? Math.round((cRealized / cPlanned) * 100) : null,
      closedWeeks: closed.length,
    };
  });

  readonly zones = computed(() => {
    const d = this.data();
    if (!d) return [];
    const entries = Object.entries(d.zoneDistribution);
    const max = Math.max(1, ...entries.map(([, v]) => v));
    return entries.map(([zone, count]) => ({
      zone,
      count,
      pct: Math.round((count / max) * 100),
      num: this.zoneNum(zone),
      color: d.zoneColors?.[zone] ?? null,
    }));
  });

  readonly statuses = computed(() =>
    Object.entries(this.data()?.statusCounts ?? {}),
  );

  ngOnInit(): void {
    this.reload();
  }

  setPeriod(value: string): void {
    this.period.set(value);
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.analyticsService
      .get(this.athleteId(), Number(this.period()))
      .subscribe({
        next: (d) => {
          this.data.set(d);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  /** Dimanche (ISO) de la semaine qui commence le lundi `weekStart`. */
  private weekEnd(weekStart: string): string {
    const d = new Date(weekStart + 'T00:00:00');
    d.setDate(d.getDate() + 6);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  zoneNum(zone: string): ZoneNum | null {
    // Seules les zones génériques Z1–Z5 ont un badge numéroté : « Seuil 1 » est une zone
    // nommée du club, pas la Z1.
    const m = /^Z([1-5])$/.exec(zone);
    return m ? (Number(m[1]) as ZoneNum) : null;
  }

  /** Tonalité de compliance : alerte si < 70 %, succès si proche de 100. */
  complianceTone(pct: number | null): 'neutral' | 'alert' | 'success' {
    if (pct == null) return 'neutral';
    if (pct < 70) return 'alert';
    if (pct >= 90 && pct <= 110) return 'success';
    return 'neutral';
  }

  statusLabel(s: string): string {
    return (
      {
        PLANNED: 'Prévu',
        COMPLETED: 'Réalisé',
        PARTIAL: 'Partiel',
        MISSED: 'Manqué',
      }[s] ?? s
    );
  }
  statusBadge(s: string): string {
    return (
      {
        PLANNED: 'badge-info',
        COMPLETED: 'badge-success',
        PARTIAL: 'badge-warning',
        MISSED: 'badge-danger',
      }[s] ?? 'badge-neutral'
    );
  }
}
