import { ZoneBarComponent } from '../../shared/components/zone-bar/zone-bar.component';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { WorkoutTemplate, WorkoutTemplateRequest } from '../../core/models/workout-template.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { SessionCategoryService } from '../../core/services/session-category.service';
import {
  CategoryOption, SessionCategory, categoryOptions, categoryPath, categoryWithDescendants,
} from '../../core/models/session-category.model';
import { SessionDetailModalComponent } from '../../shared/components/session-detail-modal/session-detail-modal.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'app-template-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, IconComponent, ReactiveFormsModule, FormsModule, RouterLink, SessionDetailModalComponent, ZoneBarComponent],
  templateUrl: './template-list.component.html',
  styleUrl: './template-list.component.scss',
})
export class TemplateListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly categoryService = inject(SessionCategoryService);

  readonly templates = signal<WorkoutTemplate[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);

  // Catégories personnalisées de bibliothèque (scopées club) — seul axe de classement (plus de « type »).
  readonly categories = signal<SessionCategory[]>([]);
  readonly showCategories = signal(false);
  newCategoryName = '';
  readonly savingCategory = signal(false);

  // Affichage : cards ↔ liste dense, recherche + filtre par catégorie.
  readonly viewMode = signal<'cards' | 'list'>('cards');
  readonly search = signal('');
  /** '' = toutes · 'none' = sans catégorie · sinon id de catégorie. */
  readonly categoryFilter = signal<string>('');

  /** Séance dont on consulte le détail (modale) ; null = fermée. */
  readonly detail = signal<{ id: string; name: string } | null>(null);

  /**
   * Modèles filtrés (recherche nom/titre + catégorie). Filtrer sur une catégorie parente inclut
   * ses sous-catégories : sinon « Seuil » n'afficherait rien dès que tout est rangé dans
   * « Seuil › Seuil long ».
   */
  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const cat = this.categoryFilter();
    const branch = cat && cat !== 'none' ? categoryWithDescendants(this.categories(), cat) : null;
    return this.templates().filter((t) => {
      const matchesCat = !cat
        || (cat === 'none' ? !t.categoryId : !!t.categoryId && branch!.has(t.categoryId));
      const matchesQ = !q || t.name.toLowerCase().includes(q) || (t.title ?? '').toLowerCase().includes(q);
      return matchesCat && matchesQ;
    });
  });

  readonly uncategorizedCount = computed(() => this.templates().filter((t) => !t.categoryId).length);

  setView(mode: 'cards' | 'list'): void { this.viewMode.set(mode); }
  toggleCategories(): void { this.showCategories.update((v) => !v); }
  openDetail(t: WorkoutTemplate): void { this.detail.set({ id: t.id, name: t.name }); }

  /**
   * Création minimale : le <b>titre</b> suffit. Exiger en plus un « nom du modèle » revenait à
   * saisir deux fois la même chose ; le nom de rangement reprend le titre s'il reste vide, et
   * reste modifiable dans l'éditeur.
   */
  readonly form = this.fb.group({
    title: ['', Validators.required],
    name: [''],
    notes: [''],
    categoryId: [''],
  });

  /** Champ « nom du modèle » replié : ouvert seulement si le coach veut un nom distinct du titre. */
  readonly showName = signal(false);
  toggleName(): void { this.showName.update((v) => !v); }

  ngOnInit(): void {
    this.load();
    this.loadCategories();
  }

  loadCategories(): void {
    this.categoryService.list().subscribe({
      next: (c) => this.categories.set(c),
      error: () => this.categories.set([]),
    });
  }

  load(): void {
    this.loading.set(true);
    this.templateService.listAll().subscribe({
      next: (t) => { this.templates.set(t); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  toggleForm(): void { this.showForm.update((v) => !v); }

  /** Crée le modèle (métadonnées) puis ouvre l'éditeur de structure. Le « type » technique est
   * conservé par défaut côté modèle (l'UI ne raisonne plus qu'en catégories). */
  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const raw = this.form.getRawValue();
    const body: WorkoutTemplateRequest = {
      // Nom de rangement : celui saisi, à défaut le titre (le serveur applique la même règle).
      name: (raw.name || '').trim() || raw.title!,
      type: 'ENDURANCE',
      title: raw.title!,
      notes: raw.notes || null,
      categoryId: raw.categoryId || null,
      steps: [],
    };
    this.templateService.create(body).subscribe({
      next: (created) => {
        this.toast.success('Modèle créé — construis la structure');
        this.form.reset({ categoryId: '' });
        this.showName.set(false);
        this.showForm.set(false);
        this.router.navigate(['/app/templates', created.id, 'structure']);
      },
      error: () => this.toast.error('Création impossible.'),
    });
  }

  categoryName(id: string | null): string {
    if (!id) return '';
    return this.categories().find((c) => c.id === id)?.name ?? '';
  }

  /** Chemin complet d'une catégorie (« VMA › VMA courte ») — les sous-catégories restent situables. */
  categoryPathOf(id: string | null): string {
    return categoryPath(this.categories(), id);
  }

  /** Catégories à plat, sous-catégories indentées sous leur parent (menus déroulants). */
  readonly categoryChoices = computed<CategoryOption[]>(() => categoryOptions(this.categories()));

  /**
   * Renomme une séance sans passer par l'éditeur : après une duplication, « … (copie) » n'était
   * modifiable nulle part depuis la bibliothèque.
   */
  rename(t: WorkoutTemplate): void {
    const name = window.prompt('Renommer la séance', t.name)?.trim();
    if (!name || name === t.name) return;
    this.templateService.update(t.id, {
      name, type: t.type, title: t.title, notes: t.notes,
      targetDistanceM: t.targetDistanceM, targetDurationS: t.targetDurationS,
      categoryId: t.categoryId, steps: t.steps,
    }).subscribe({
      next: (updated) => {
        this.templates.update((list) => list.map((x) => (x.id === t.id ? updated : x)));
        this.toast.success('Séance renommée');
      },
      error: () => this.toast.error('Renommage impossible.'),
    });
  }

  /**
   * (Ré)affecte la catégorie d'une séance sans quitter l'écran (menu déroulant de la modale).
   * Préserve la structure et les métadonnées : seul le rangement change.
   */
  assignCategory(templateId: string, categoryId: string): void {
    const t = this.templates().find((x) => x.id === templateId);
    if (!t) return;
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
        this.toast.success(categoryId ? `Rangée dans « ${this.categoryName(categoryId)} »` : 'Catégorie retirée.');
      },
      error: () => this.toast.error('Changement de catégorie impossible.'),
    });
  }

  /** Catégorie courante de la séance consultée (pour le menu déroulant de la modale). */
  detailCategoryId(): string {
    const d = this.detail();
    return (d && this.templates().find((x) => x.id === d.id)?.categoryId) || '';
  }

  // --- Gestion des catégories (créer / renommer / supprimer) -----------------

  /** Catégorie parente de la prochaine création ('' = catégorie racine). */
  newCategoryParentId = '';

  createCategory(): void {
    const name = this.newCategoryName.trim();
    if (!name || this.savingCategory()) return;
    this.savingCategory.set(true);
    this.categoryService.create({ name, parentId: this.newCategoryParentId || null }).subscribe({
      next: (c) => {
        this.categories.update((list) => [...list, c].sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name)));
        this.newCategoryName = '';
        this.savingCategory.set(false);
        this.toast.success(this.newCategoryParentId ? 'Sous-catégorie créée' : 'Catégorie créée');
      },
      error: () => { this.savingCategory.set(false); this.toast.error('Création impossible.'); },
    });
  }

  /** Range une catégorie sous une autre (ou la remonte à la racine avec ''). */
  setCategoryParent(c: SessionCategory, parentId: string): void {
    const next = parentId || null;
    if (next === c.parentId) return;
    this.categoryService.update(c.id, {
      name: c.name, parentId: next, discipline: c.discipline, sortOrder: c.sortOrder,
    }).subscribe({
      next: (updated) => {
        this.categories.update((list) => list.map((x) => (x.id === c.id ? updated : x)));
        this.toast.success(next ? 'Sous-catégorie rangée.' : 'Catégorie remontée à la racine.');
      },
      error: () => this.toast.error('Déplacement impossible.'),
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
        // Le serveur détache (FK SET NULL) : les sous-catégories remontent à la racine.
        this.categories.update((list) => list
          .filter((x) => x.id !== c.id)
          .map((x) => (x.parentId === c.id ? { ...x, parentId: null } : x)));
        this.templates.update((list) => list.map((t) => (t.categoryId === c.id ? { ...t, categoryId: null, categoryName: null } : t)));
        if (this.categoryFilter() === c.id) this.categoryFilter.set('');
        this.toast.info('Catégorie supprimée.');
      },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  /** Duplication en cours (évite le double clic, qui créerait deux copies). */
  readonly duplicating = signal(false);

  /**
   * Duplique un modèle et ouvre directement l'éditeur de structure : on duplique pour créer une
   * variante, donc l'étape suivante est toujours l'édition.
   */
  duplicate(t: WorkoutTemplate): void {
    if (this.duplicating()) return;
    this.duplicating.set(true);
    this.templateService.duplicate(t.id).subscribe({
      next: (copy) => {
        this.duplicating.set(false);
        this.toast.success(`« ${copy.name} » créée — ajuste la structure`);
        this.router.navigate(['/app/templates', copy.id, 'structure']);
      },
      error: () => { this.duplicating.set(false); this.toast.error('Duplication impossible.'); },
    });
  }

  async remove(t: WorkoutTemplate): Promise<void> {
    const ok = await this.confirm.ask({ title: 'Supprimer le modèle', message: `Supprimer « ${t.name} » ?`, confirmLabel: 'Supprimer', danger: true });
    if (!ok) return;
    this.templateService.delete(t.id).subscribe(() => { this.toast.success('Supprimé.'); this.load(); });
  }
}
