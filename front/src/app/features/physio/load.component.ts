import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LactateService } from '../../core/services/lactate.service';
import { Load } from '../../core/models/lactate.model';
import { AcwrIndicatorComponent } from '../../shared/components/physiology';
import { MetricCardComponent } from '../../shared/components/ui';

/** Charge d'entraînement (ACWR, monotonie, répartition par domaines) — cf. Darilab s-data. */
@Component({
  selector: 'app-load',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DecimalPipe, AcwrIndicatorComponent, MetricCardComponent],
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
