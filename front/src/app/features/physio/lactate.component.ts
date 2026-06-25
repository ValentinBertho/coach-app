import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { LactateService } from '../../core/services/lactate.service';
import { ToastService } from '../../core/services/toast.service';
import { LactateStep, LactateTest, LtDetection } from '../../core/models/lactate.model';
import { DataOriginTagComponent } from '../../shared/components/physiology';
import { SegmentedControlComponent, type SegmentOption } from '../../shared/components/ui';

interface ChartPoint { cx: number; cy: number; speed: string; lactate: number; }
interface Chart { points: ChartPoint[]; polyline: string; lt1x: number | null; lt2x: number | null; }
interface Series { polyline: string; color: string; label: string; lt2x: number | null; lt2Kmh: string | null; }
interface MultiChart { series: Series[]; }

/** Tests lactate + détection LT1/LT2 (Dmax modifié) + profil lactate (courbe). */
@Component({
  selector: 'app-lactate',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, DatePipe, DataOriginTagComponent, SegmentedControlComponent],
  templateUrl: './lactate.component.html',
  styleUrl: './lactate.component.scss',
})
export class LactateComponent implements OnInit {
  readonly athleteId = input.required<string>();

  private readonly lactate = inject(LactateService);
  private readonly toast = inject(ToastService);

  readonly tests = signal<LactateTest[]>([]);
  readonly profileTests = signal<LactateTest[]>([]);
  readonly detection = signal<LtDetection | null>(null);
  private readonly version = signal(0);

  private readonly palette = ['#2dd4bf', '#a78bfa', '#f97316', '#10b981', '#ef4444', '#fbbf24'];
  readonly multiChart = computed<MultiChart | null>(() => this.buildMulti(this.profileTests()));

  /** Unité d'affichage des vitesses (toggle km/h ↔ allure min/km). */
  readonly speedUnit = signal<'kmh' | 'pace'>('kmh');
  readonly speedOptions: SegmentOption[] = [
    { value: 'kmh', label: 'km/h' },
    { value: 'pace', label: 'min/km' },
  ];

  /** Formate une vitesse (km/h) selon l'unité choisie. */
  fmtSpeed(kmh: number | null | undefined): string {
    if (kmh == null) return '—';
    if (this.speedUnit() === 'kmh') return `${kmh.toFixed(1)} km/h`;
    const secPerKm = 3600 / kmh;
    const m = Math.floor(secPerKm / 60);
    const s = Math.round(secPerKm % 60);
    return `${m}:${s.toString().padStart(2, '0')} /km`;
  }

  /** Export CSV de l'historique des tests (date, LT1, LT2, FC LT2). */
  exportCsv(): void {
    const rows: string[][] = [['Date', 'LT1 (km/h)', 'LT2 (km/h)', 'FC LT2']];
    for (const t of this.tests()) {
      rows.push([
        t.testDate,
        t.lt1Ms ? (t.lt1Ms * 3.6).toFixed(1) : '',
        t.lt2Ms ? (t.lt2Ms * 3.6).toFixed(1) : '',
        t.fcLt2 != null ? String(t.fcLt2) : '',
      ]);
    }
    const csv = rows.map((r) => r.join(';')).join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'tests-lactate.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  rest = { lactateRest: 0.8, hrRest: 60, hrMax: 188 };
  steps: LactateStep[] = [
    { speedMs: 3.0, hr: 130, lactate: 1.0 },
    { speedMs: 3.3, hr: 140, lactate: 1.2 },
    { speedMs: 3.6, hr: 150, lactate: 1.8 },
    { speedMs: 3.9, hr: 160, lactate: 3.0 },
    { speedMs: 4.2, hr: 170, lactate: 5.5 },
    { speedMs: 4.5, hr: 178, lactate: 8.0 },
  ];

  readonly chart = computed<Chart | null>(() => {
    this.version();
    return this.buildChart(this.steps, this.detection());
  });

  ngOnInit(): void {
    this.reload();
    this.refresh();
  }

  reload(): void {
    this.lactate.list(this.athleteId()).subscribe((summaries) => {
      this.tests.set(summaries);
      if (summaries.length) {
        forkJoin(summaries.slice(0, 6).map((t) => this.lactate.get(this.athleteId(), t.id)))
          .subscribe((full) => this.profileTests.set(full));
      } else {
        this.profileTests.set([]);
      }
    });
  }

  addStep(): void {
    this.steps = [...this.steps, { speedMs: null, hr: null, lactate: null }];
    this.refresh();
  }

  removeStep(i: number): void {
    this.steps = this.steps.filter((_, idx) => idx !== i);
    this.refresh();
  }

  refresh(): void {
    this.version.update((v) => v + 1);
    const steps = this.steps.filter((s) => s.speedMs && s.lactate != null);
    if (steps.length < 3) {
      this.detection.set(null);
      return;
    }
    this.lactate.detect(this.athleteId(), { lactateRest: this.rest.lactateRest, steps })
      .subscribe((d) => this.detection.set(d));
  }

  save(): void {
    this.lactate
      .create(this.athleteId(), {
        testDate: new Date().toISOString().slice(0, 10),
        lactateRest: this.rest.lactateRest,
        hrRest: this.rest.hrRest,
        hrMax: this.rest.hrMax,
        applyToProfile: true,
        steps: this.steps.filter((s) => s.speedMs),
      })
      .subscribe(() => {
        this.toast.success('Test enregistré — profil physio mis à jour');
        this.reload();
      });
  }

  loadTest(t: LactateTest): void {
    this.rest = {
      lactateRest: t.lactateRest ?? 0.8,
      hrRest: t.hrRest ?? 60,
      hrMax: t.hrMax ?? 188,
    };
    this.steps = t.steps.map((s) => ({ speedMs: s.speedMs, hr: s.hr, lactate: s.lactate }));
    this.refresh();
  }

  /** Superpose les courbes lactate/vitesse de plusieurs tests (échelle commune). */
  private buildMulti(tests: LactateTest[]): MultiChart | null {
    const usable = tests
      .map((t) => ({ test: t, steps: t.steps.filter((s) => s.speedMs && s.lactate != null) }))
      .filter((e) => e.steps.length >= 2);
    if (usable.length === 0) return null;

    const allKmh: number[] = [];
    const allLact: number[] = [];
    for (const e of usable) {
      for (const s of e.steps) {
        allKmh.push((s.speedMs as number) * 3.6);
        allLact.push(s.lactate as number);
      }
    }
    const minS = Math.min(...allKmh);
    const maxS = Math.max(...allKmh);
    const maxL = Math.max(2, ...allLact);
    const x = (s: number) => (maxS === minS ? 30 : 30 + ((s - minS) / (maxS - minS)) * 280);
    const y = (l: number) => 10 + (1 - l / maxL) * 150;
    const clampX = (kmh: number | null): number | null =>
      kmh == null ? null : Math.max(30, Math.min(310, x(kmh)));

    const series: Series[] = usable.map((e, i) => {
      const pts = [...e.steps].sort((a, b) => (a.speedMs as number) - (b.speedMs as number));
      const lt2Kmh = e.test.lt2Ms ? e.test.lt2Ms * 3.6 : null;
      return {
        polyline: pts.map((s) => `${x((s.speedMs as number) * 3.6)},${y(s.lactate as number)}`).join(' '),
        color: this.palette[i % this.palette.length],
        label: e.test.testDate,
        lt2x: clampX(lt2Kmh),
        lt2Kmh: lt2Kmh ? lt2Kmh.toFixed(1) : null,
      };
    });
    return { series };
  }

  private buildChart(steps: LactateStep[], det: LtDetection | null): Chart | null {
    const valid = steps.filter((s) => s.speedMs && s.lactate != null);
    if (valid.length < 2) return null;
    const kmh = valid.map((s) => (s.speedMs as number) * 3.6);
    const lacts = valid.map((s) => s.lactate as number);
    const minS = Math.min(...kmh);
    const maxS = Math.max(...kmh);
    const maxL = Math.max(2, ...lacts);
    const x = (s: number) => (maxS === minS ? 30 : 30 + ((s - minS) / (maxS - minS)) * 280);
    const y = (l: number) => 10 + (1 - l / maxL) * 150;
    const points: ChartPoint[] = valid.map((s, i) => ({
      cx: x(kmh[i]), cy: y(lacts[i]), speed: kmh[i].toFixed(1), lactate: lacts[i],
    }));
    const clampX = (kmhVal: number | null): number | null =>
      kmhVal == null ? null : Math.max(30, Math.min(310, x(kmhVal)));
    return {
      points,
      polyline: points.map((p) => `${p.cx},${p.cy}`).join(' '),
      lt1x: clampX(det?.lt1Kmh ?? null),
      lt2x: clampX(det?.lt2Kmh ?? null),
    };
  }
}
