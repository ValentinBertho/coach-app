import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  STEP_TYPE_LABELS,
  WORKOUT_TYPE_LABELS,
  WorkoutRequest,
  WorkoutStatus,
  WorkoutStep,
} from '../../core/models/workout.model';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';

@Component({
  selector: 'app-workout-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './workout-form.component.html',
  styleUrl: './workout-form.component.scss',
})
export class WorkoutFormComponent implements OnInit {
  /** Liés aux paramètres de route / query. */
  readonly athleteId = input.required<string>();
  readonly workoutId = input<string>();
  readonly date = input<string>();

  private readonly fb = inject(FormBuilder);
  private readonly workoutService = inject(WorkoutService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  readonly types = Object.keys(WORKOUT_TYPE_LABELS) as (keyof typeof WORKOUT_TYPE_LABELS)[];
  readonly stepTypes = Object.keys(STEP_TYPE_LABELS) as (keyof typeof STEP_TYPE_LABELS)[];
  readonly zones = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5'] as const;

  readonly submitting = signal(false);
  readonly status = signal<WorkoutStatus | null>(null);

  readonly form = this.fb.group({
    scheduledDate: ['', Validators.required],
    type: ['ENDURANCE', Validators.required],
    title: ['', [Validators.required, Validators.maxLength(255)]],
    targetDistanceM: [null as number | null, Validators.min(0)],
    targetDurationS: [null as number | null, Validators.min(0)],
    notes: [''],
    steps: this.fb.array<ReturnType<WorkoutFormComponent['newStep']>>([]),
  });

  get steps(): FormArray {
    return this.form.get('steps') as FormArray;
  }

  get isEdit(): boolean {
    return !!this.workoutId();
  }

  ngOnInit(): void {
    const id = this.workoutId();
    if (id) {
      this.workoutService.get(this.athleteId(), id).subscribe({
        next: (w) => {
          this.status.set(w.status);
          this.form.patchValue({
            scheduledDate: w.scheduledDate,
            type: w.type,
            title: w.title,
            targetDistanceM: w.targetDistanceM,
            targetDurationS: w.targetDurationS,
            notes: w.notes ?? '',
          });
          w.steps.forEach((s) => this.steps.push(this.newStep(s)));
        },
        error: () => this.router.navigate(['/app/calendar']),
      });
    } else {
      this.form.patchValue({ scheduledDate: this.date() ?? '' });
      this.steps.push(this.newStep({ stepType: 'WARMUP', repetitions: 1, zone: 'Z2' } as WorkoutStep));
    }
  }

  newStep(s?: Partial<WorkoutStep>) {
    return this.fb.group({
      stepType: [s?.stepType ?? 'REPETITION', Validators.required],
      repetitions: [s?.repetitions ?? 1, [Validators.required, Validators.min(1)]],
      zone: [s?.zone ?? null],
      distanceM: [s?.distanceM ?? null, Validators.min(0)],
      durationS: [s?.durationS ?? null, Validators.min(0)],
      notes: [s?.notes ?? ''],
    });
  }

  addStep(): void {
    this.steps.push(this.newStep());
  }

  removeStep(i: number): void {
    this.steps.removeAt(i);
  }

  move(i: number, delta: number): void {
    const target = i + delta;
    if (target < 0 || target >= this.steps.length) return;
    const ctrl = this.steps.at(i);
    this.steps.removeAt(i);
    this.steps.insert(target, ctrl);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const payload = this.form.getRawValue() as unknown as WorkoutRequest;
    const id = this.workoutId();
    const call = id
      ? this.workoutService.update(this.athleteId(), id, payload)
      : this.workoutService.create(this.athleteId(), payload);

    call.subscribe({
      next: () => {
        this.toast.success(id ? 'Séance mise à jour' : 'Séance planifiée');
        this.router.navigate(['/app/calendar']);
      },
      error: () => this.submitting.set(false),
    });
  }

  setStatus(status: WorkoutStatus): void {
    const id = this.workoutId();
    if (!id) return;
    this.workoutService.updateStatus(this.athleteId(), id, status).subscribe({
      next: (w) => {
        this.status.set(w.status);
        this.toast.success('Statut mis à jour.');
      },
    });
  }

  remove(): void {
    const id = this.workoutId();
    if (!id) return;
    this.workoutService.delete(this.athleteId(), id).subscribe({
      next: () => {
        this.toast.success('Séance supprimée.');
        this.router.navigate(['/app/calendar']);
      },
    });
  }
}
