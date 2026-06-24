import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  STEP_TYPE_LABELS,
  WORKOUT_TYPE_LABELS,
  Workout,
  WorkoutStatus,
} from '../../core/models/workout.model';
import { WorkoutService } from '../../core/services/workout.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import {
  DataOriginTagComponent,
  IntensityZoneBadgeComponent,
  type IntensityZone as ZoneNum,
} from '../../shared/components/physiology';
import { StickyActionBarComponent } from '../../shared/components/ui';

type State = 'loading' | 'ready' | 'error';

/**
 * Page séance (lecture) — vue coach. Registres distincts : prescription /
 * explication / retour athlète. Réutilise les primitives (zones, origine).
 * L'édition est une action délibérée (lien vers l'éditeur), conforme à la
 * distinction lecture vs édition du blueprint §7.3.
 */
@Component({
  selector: 'app-workout-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IntensityZoneBadgeComponent, DataOriginTagComponent, StickyActionBarComponent],
  templateUrl: './workout-detail.component.html',
  styleUrl: './workout-detail.component.scss',
})
export class WorkoutDetailComponent implements OnInit {
  private readonly workoutService = inject(WorkoutService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly router = inject(Router);

  readonly athleteId = input.required<string>();
  readonly workoutId = input.required<string>();

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;

  readonly state = signal<State>('loading');
  readonly workout = signal<Workout | null>(null);

  /** La séance a-t-elle un retour athlète déclaré ? */
  readonly hasFeedback = computed(() => {
    const w = this.workout();
    return !!w && (w.rpe != null || !!w.athleteComment);
  });

  ngOnInit(): void { this.load(); }

  load(): void {
    this.state.set('loading');
    this.workoutService.get(this.athleteId(), this.workoutId()).subscribe({
      next: (w) => { this.workout.set(w); this.state.set('ready'); },
      error: () => this.state.set('error'),
    });
  }

  setStatus(status: WorkoutStatus): void {
    this.workoutService.updateStatus(this.athleteId(), this.workoutId(), status).subscribe({
      next: (w) => { this.workout.set(w); this.toast.success(`Séance ${this.statusLabels[status].toLowerCase()}.`); },
      error: () => this.toast.error('Mise à jour impossible.'),
    });
  }

  async remove(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer cette séance ?',
      message: 'Cette action est définitive.',
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    this.workoutService.delete(this.athleteId(), this.workoutId()).subscribe({
      next: () => {
        this.toast.success('Séance supprimée.');
        this.router.navigate(['/app/athletes', this.athleteId()]);
      },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  zoneNum(zone: string | null): ZoneNum | null {
    if (!zone) return null;
    const n = Number(zone.replace(/\D/g, ''));
    return n >= 1 && n <= 5 ? (n as ZoneNum) : null;
  }

  stepTarget(distanceM: number | null, durationS: number | null): string {
    if (distanceM) return distanceM >= 1000 ? `${(distanceM / 1000).toFixed(1)} km` : `${distanceM} m`;
    if (durationS) {
      const m = Math.floor(durationS / 60);
      const s = durationS % 60;
      return m ? `${m}:${s.toString().padStart(2, '0')}` : `${s} s`;
    }
    return '';
  }
}
