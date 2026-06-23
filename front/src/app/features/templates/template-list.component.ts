import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AthleteSummary } from '../../core/models/athlete.model';
import { WorkoutTemplate, WorkoutTemplateRequest } from '../../core/models/workout-template.model';
import { STEP_TYPE_LABELS, WORKOUT_TYPE_LABELS } from '../../core/models/workout.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';

@Component({
  selector: 'app-template-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, FormsModule, RouterLink],
  templateUrl: './template-list.component.html',
  styleUrl: './template-list.component.scss',
})
export class TemplateListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly athleteService = inject(AthleteService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly types = Object.keys(WORKOUT_TYPE_LABELS) as (keyof typeof WORKOUT_TYPE_LABELS)[];
  readonly stepTypes = Object.keys(STEP_TYPE_LABELS) as (keyof typeof STEP_TYPE_LABELS)[];
  readonly zones = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5'] as const;

  readonly templates = signal<WorkoutTemplate[]>([]);
  readonly athletes = signal<AthleteSummary[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);

  // état d'application par modèle
  applyFor: Record<string, { athleteId: string; date: string }> = {};

  readonly form = this.fb.group({
    name: ['', Validators.required],
    type: ['ENDURANCE', Validators.required],
    title: ['', Validators.required],
    targetDistanceM: [null as number | null],
    targetDurationS: [null as number | null],
    notes: [''],
    steps: this.fb.array<ReturnType<TemplateListComponent['newStep']>>([]),
  });

  get steps(): FormArray {
    return this.form.get('steps') as FormArray;
  }

  ngOnInit(): void {
    this.load();
    this.athleteService.list({ status: 'ACTIVE' }).subscribe((p) => this.athletes.set(p.content));
  }

  load(): void {
    this.loading.set(true);
    this.templateService.list().subscribe({
      next: (p) => {
        this.templates.set(p.content);
        p.content.forEach((t) => (this.applyFor[t.id] ??= { athleteId: '', date: '' }));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  newStep() {
    return this.fb.group({
      stepType: ['REPETITION', Validators.required],
      repetitions: [1, [Validators.required, Validators.min(1)]],
      zone: [null as string | null],
      distanceM: [null as number | null],
      durationS: [null as number | null],
      notes: [''],
    });
  }
  addStep(): void { this.steps.push(this.newStep()); }
  removeStep(i: number): void { this.steps.removeAt(i); }

  toggleForm(): void {
    this.showForm.update((v) => !v);
    if (this.showForm() && this.steps.length === 0) this.addStep();
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.templateService.create(this.form.getRawValue() as unknown as WorkoutTemplateRequest).subscribe(() => {
      this.toast.success('Modèle créé 📚');
      this.form.reset({ type: 'ENDURANCE' });
      this.steps.clear();
      this.showForm.set(false);
      this.load();
    });
  }

  apply(t: WorkoutTemplate): void {
    const sel = this.applyFor[t.id];
    if (!sel?.athleteId || !sel?.date) { this.toast.warning('Choisissez un athlète et une date.'); return; }
    this.templateService.apply(t.id, sel.athleteId, sel.date).subscribe(() => {
      this.toast.success('Séance ajoutée au calendrier ✅');
      this.applyFor[t.id] = { athleteId: '', date: '' };
    });
  }

  async remove(t: WorkoutTemplate): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Supprimer le modèle', message: `Supprimer « ${t.name} » ?`, confirmLabel: 'Supprimer', danger: true });
    if (!ok) return;
    this.templateService.delete(t.id).subscribe(() => { this.toast.success('Supprimé.'); this.load(); });
  }
}
