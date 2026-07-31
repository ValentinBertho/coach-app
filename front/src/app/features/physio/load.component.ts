import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { AnalyticsComponent } from '../analytics/analytics.component';
import { LactateService } from '../../core/services/lactate.service';
import { Load, LoadSeries } from '../../core/models/lactate.model';
import { AcwrIndicatorComponent } from '../../shared/components/physiology';
import { MetricCardComponent } from '../../shared/components/ui';
import { LoadChartComponent } from '../../shared/components/load-chart/load-chart.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Onglet « Charge » de l'athlète : ACWR, monotonie et répartition d'intensité, puis le panneau
 * volume/progression. Les deux écrans « Charge d'entraînement » et « Charge & progression » n'en
 * font plus qu'un — ce dernier était routé mais sans aucun lien entrant.
 */
@Component({
  selector: 'app-load',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, DecimalPipe, AnalyticsComponent, AcwrIndicatorComponent, MetricCardComponent, LoadChartComponent],
  templateUrl: './load.component.html',
  styleUrl: './load.component.scss',
})
export class LoadComponent implements OnInit {
  readonly athleteId = input.required<string>();
  private readonly lactate = inject(LactateService);
  readonly load = signal<Load | null>(null);
  /** Courbe ATL/CTL/ACWR : la valeur instantanée ne dit pas si la charge monte ou redescend. */
  readonly series = signal<LoadSeries | null>(null);

  ngOnInit(): void {
    this.lactate.load(this.athleteId()).subscribe((l) => this.load.set(l));
    this.lactate.loadSeries(this.athleteId(), 12).subscribe({
      next: (s) => this.series.set(s),
      error: () => this.series.set(null),
    });
  }
}
