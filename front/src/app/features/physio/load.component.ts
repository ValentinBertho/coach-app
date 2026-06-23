import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { LactateService } from '../../core/services/lactate.service';
import { Load } from '../../core/models/lactate.model';

/** Charge d'entraînement (ACWR, monotonie, répartition par domaines) — cf. DARI Lab s-data. */
@Component({
  selector: 'app-load',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DecimalPipe],
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

  ratioClass(ratio: number | null): string {
    if (ratio == null) return 'badge-neutral';
    if (ratio > 1.5 || ratio < 0.8) return 'badge-danger';
    if (ratio > 1.3) return 'badge-warning';
    return 'badge-success';
  }
}
