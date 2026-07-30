import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { AnalyticsComponent } from '../analytics/analytics.component';
import { LactateService } from '../../core/services/lactate.service';
import { Load } from '../../core/models/lactate.model';
import { AcwrIndicatorComponent } from '../../shared/components/physiology';
import { MetricCardComponent } from '../../shared/components/ui';

/**
 * Onglet « Charge » de l'athlète : ACWR, monotonie et répartition d'intensité, puis le panneau
 * volume/progression. Les deux écrans « Charge d'entraînement » et « Charge & progression » n'en
 * font plus qu'un — ce dernier était routé mais sans aucun lien entrant.
 */
@Component({
  selector: 'app-load',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, AnalyticsComponent, AcwrIndicatorComponent, MetricCardComponent],
  templateUrl: './load.component.html',
  styleUrl: './load.component.scss',
})
export class LoadComponent implements OnInit {
  readonly athleteId = input.required<string>();
  private readonly lactate = inject(LactateService);
  readonly load = signal<Load | null>(null);

  ngOnInit(): void {
    this.lactate.load(this.athleteId()).subscribe((l) => this.load.set(l));
  }
}
