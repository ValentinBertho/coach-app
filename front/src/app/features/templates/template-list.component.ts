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
import { TrainingGroupService } from '../../core/services/training-group.service';
import { TrainingGroup } from '../../core/models/training-group.model';
import { SessionCategoryService } from '../../core/services/session-category.service';
import { SessionCategory } from '../../core/models/session-category.model';

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
  private readonly groupService = inject(TrainingGroupService);
  private readonly categoryService = inject(SessionCategoryService);

  readonly groups = signal<TrainingGroup[]>([]);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly types = Object.keys(WORKOUT_TYPE_LABELS) as (keyof typeof WORKOUT_TYPE_LABELS)[];

  readonly templates = signal<WorkoutTemplate[]>([]);
  readonly athletes = signal<AthleteSummary[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);

  // Catégories personnalisées de bibliothèque (scopées club).
  readonly categories = signal<SessionCategory[]>([]);
  readonly showCategories = signal(false);
  newCategoryName = '';
  readonly savingCategory = signal(false);

  // Affichage : cards compactes ↔ liste dense, recherche + filtre par type + catégorie.
  readonly viewMode = signal<'cards' | 'list'>('cards');
  readonly search = signal('');
  readonly typeFilter = signal<string>('');
  /** '' = toutes · 'none' = sans catégorie · sinon id de catégorie. */
  readonly categoryFilter = signal<string>('');

  /** Modèles filtrés (recherche nom/titre + type + catégorie), pour densifier la navigation. */
  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const type = this.typeFilter();
    const cat = this.categoryFilter();
    return this.templates().filter((t) => {
      const matchesType = !type || t.type === type;
      const matchesCat = !cat || (cat === 'none' ? !t.categoryId : t.categoryId === cat);
      const matchesQ = !q
        || t.name.toLowerCase().includes(q)
        || (t.title ?? '').toLowerCase().includes(q);
      return matchesType && matchesCat && matchesQ;
    });
  });

  /** Nombre de modèles sans catégorie (pour l'option de filtre). */
  readonly uncategorizedCount = computed(() => this.templates().filter((t) => !t.categoryId).length);

  setView(mode: 'cards' | 'list'): void { this.viewMode.set(mode); }
  toggleCategories(): void { this.showCategories.update((v) => !v); }

  // état d'application par modèle. `target` = "a:<athleteId>" ou "g:<groupId>".
  applyFor: Record<string, { target: string; date: string }> = {};

  // Création minimale : la prescription en fourchettes se construit ensuite dans l'éditeur
  // de structure (modèle unique DARI Lab — plus de saisie « par zones » en valeur sèche).
  readonly form = this.fb.group({
    name: ['', Validators.required],
    type: ['ENDURANCE', Validators.required],
    title: ['', Validators.required],
    notes: [''],
    categoryId: [''],
  });

  ngOnInit(): void {
    this.load();
    this.loadCategories();
    this.athleteService.list({ status: 'ACTIVE' }).subscribe((p) => this.athletes.set(p.content));
    this.groupService.list().subscribe((g) => this.groups.set(g));
  }

  loadCategories(): void {
    this.categoryService.list().subscribe({
      next: (c) => this.categories.set(c),
      error: () => this.categories.set([]),
    });
  }

  load(): void {
    this.loading.set(true);
    this.templateService.list().subscribe({
      next: (p) => {
        this.templates.set(p.content);
        p.content.forEach((t) => (this.applyFor[t.id] ??= { target: '', date: '' }));
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
    const raw = this.form.getRawValue();
    const body: WorkoutTemplateRequest = {
      name: raw.name!,
      type: raw.type as WorkoutTemplateRequest['type'],
      title: raw.title!,
      notes: raw.notes || null,
      categoryId: raw.categoryId || null,
      steps: [],
    };
    this.templateService.create(body).subscribe({
      next: (created) => {
        this.toast.success('Modèle créé — construis la structure en fourchettes');
        this.form.reset({ type: 'ENDURANCE', categoryId: '' });
        this.showForm.set(false);
        this.router.navigate(['/app/templates', created.id, 'structure']);
      },
      error: () => this.toast.error('Création impossible.'),
    });
  }

  /** (Ré)affecte la catégorie d'un modèle sans quitter la bibliothèque (préserve la structure). */
  assignCategory(t: WorkoutTemplate, categoryId: string): void {
    const body: WorkoutTemplateRequest = {
      name: t.name,
      type: t.type,
      title: t.title,
      notes: t.notes,
      targetDistanceM: t.targetDistanceM,
      targetDurationS: t.targetDurationS,
      categoryId: categoryId || null,
      steps: t.steps,
    };
    this.templateService.update(t.id, body).subscribe({
      next: (updated) => {
        this.templates.update((list) => list.map((x) => (x.id === t.id ? updated : x)));
      },
      error: () => this.toast.error('Changement de catégorie impossible.'),
    });
  }

  categoryName(id: string | null): string {
    if (!id) return '';
    return this.categories().find((c) => c.id === id)?.name ?? '';
  }

  // --- Gestion des catégories (créer / renommer / supprimer) -----------------
  createCategory(): void {
    const name = this.newCategoryName.trim();
    if (!name || this.savingCategory()) return;
    this.savingCategory.set(true);
    this.categoryService.create({ name }).subscribe({
      next: (c) => {
        this.categories.update((list) => [...list, c].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name)));
        this.newCategoryName = '';
        this.savingCategory.set(false);
        this.toast.success('Catégorie créée');
      },
      error: () => { this.savingCategory.set(false); this.toast.error('Création impossible.'); },
    });
  }

  async renameCategory(c: SessionCategory): Promise<void> {
    const name = window.prompt('Renommer la catégorie', c.name)?.trim();
    if (!name || name === c.name) return;
    this.categoryService.update(c.id, { name, parentId: c.parentId, discipline: c.discipline, sortOrder: c.sortOrder }).subscribe({
      next: (updated) => {
        this.categories.update((list) => list.map((x) => (x.id === c.id ? updated : x)));
        this.toast.success('Catégorie renommée');
      },
      error: () => this.toast.error('Renommage impossible.'),
    });
  }

  async deleteCategory(c: SessionCategory): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer la catégorie',
      message: `Supprimer « ${c.name} » ? Les séances rattachées deviennent « sans catégorie ».`,
      confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.categoryService.delete(c.id).subscribe({
      next: () => {
        this.categories.update((list) => list.filter((x) => x.id !== c.id));
        // Détacher localement les modèles rattachés (le back a fait un SET NULL).
        this.templates.update((list) => list.map((t) => (t.categoryId === c.id ? { ...t, categoryId: null, categoryName: null } : t)));
        if (this.categoryFilter() === c.id) this.categoryFilter.set('');
        this.toast.info('Catégorie supprimée.');
      },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  apply(t: WorkoutTemplate): void {
    const sel = this.applyFor[t.id];
    if (!sel?.target || !sel?.date) { this.toast.warning('Choisis une cible (athlète ou groupe) et une date.'); return; }
    const [kind, id] = sel.target.split(':');
    if (kind === 'g') {
      this.templateService.applyGroup(t.id, id, sel.date).subscribe({
        next: (r) => {
          this.toast.success(r.created > 0
            ? `Séance ajoutée à ${r.created} athlète(s)${r.skipped ? ` (${r.skipped} ignoré·s : lecture seule)` : ''}`
            : 'Aucun athlète modifiable dans ce groupe');
          this.applyFor[t.id] = { target: '', date: '' };
        },
        error: () => this.toast.error('Application au groupe impossible.'),
      });
      return;
    }
    this.templateService.apply(t.id, id, sel.date).subscribe({
      next: () => {
        this.toast.success('Séance ajoutée au calendrier');
        this.applyFor[t.id] = { target: '', date: '' };
      },
      error: () => this.toast.error('Application impossible.'),
    });
  }

  async remove(t: WorkoutTemplate): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Supprimer le modèle', message: `Supprimer « ${t.name} » ?`, confirmLabel: 'Supprimer', danger: true });
    if (!ok) return;
    this.templateService.delete(t.id).subscribe(() => { this.toast.success('Supprimé.'); this.load(); });
  }
}
