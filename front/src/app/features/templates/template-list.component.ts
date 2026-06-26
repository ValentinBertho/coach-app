import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteSummary } from '../../core/models/athlete.model';
import { WorkoutTemplate, WorkoutTemplateRequest } from '../../core/models/workout-template.model';
import { WORKOUT_TYPE_LABELS } from '../../core/models/workout.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';

@Component({
  selector: 'app-template-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, ReactiveFormsModule, FormsModule, RouterLink],
  templateUrl: './template-list.component.html',
  styleUrl: './template-list.component.scss',
})
export class TemplateListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly athleteService = inject(AthleteService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly types = Object.keys(WORKOUT_TYPE_LABELS) as (keyof typeof WORKOUT_TYPE_LABELS)[];

  readonly templates = signal<WorkoutTemplate[]>([]);
  readonly athletes = signal<AthleteSummary[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);

  // Affichage : cards compactes ↔ liste dense, recherche + filtre par type.
  readonly viewMode = signal<'cards' | 'list'>('cards');
  readonly search = signal('');
  readonly typeFilter = signal<string>('');

  /** Modèles filtrés (recherche nom/titre + type), pour densifier la navigation. */
  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const type = this.typeFilter();
    return this.templates().filter((t) => {
      const matchesType = !type || t.type === type;
      const matchesQ = !q
        || t.name.toLowerCase().includes(q)
        || (t.title ?? '').toLowerCase().includes(q);
      return matchesType && matchesQ;
    });
  });

  setView(mode: 'cards' | 'list'): void { this.viewMode.set(mode); }

  // état d'application par modèle
  applyFor: Record<string, { athleteId: string; date: string }> = {};

  // Création minimale : la prescription en fourchettes se construit ensuite dans l'éditeur
  // de structure (modèle unique DARI Lab — plus de saisie « par zones » en valeur sèche).
  readonly form = this.fb.group({
    name: ['', Validators.required],
    type: ['ENDURANCE', Validators.required],
    title: ['', Validators.required],
    notes: [''],
  });

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

  toggleForm(): void {
    this.showForm.update((v) => !v);
  }

  /** Crée le modèle (métadonnées) puis ouvre l'éditeur de structure (blocs en fourchettes). */
  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.templateService.create(this.form.getRawValue() as unknown as WorkoutTemplateRequest).subscribe({
      next: (created) => {
        this.toast.success('Modèle créé — construis la structure en fourchettes');
        this.form.reset({ type: 'ENDURANCE' });
        this.showForm.set(false);
        this.router.navigate(['/app/templates', created.id, 'structure']);
      },
      error: () => this.toast.error('Création impossible.'),
    });
  }

  apply(t: WorkoutTemplate): void {
    const sel = this.applyFor[t.id];
    if (!sel?.athleteId || !sel?.date) { this.toast.warning('Choisissez un athlète et une date.'); return; }
    this.templateService.apply(t.id, sel.athleteId, sel.date).subscribe(() => {
      this.toast.success('Séance ajoutée au calendrier');
      this.applyFor[t.id] = { athleteId: '', date: '' };
    });
  }

  async remove(t: WorkoutTemplate): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Supprimer le modèle', message: `Supprimer « ${t.name} » ?`, confirmLabel: 'Supprimer', danger: true });
    if (!ok) return;
    this.templateService.delete(t.id).subscribe(() => { this.toast.success('Supprimé.'); this.load(); });
  }
}
