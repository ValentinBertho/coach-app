import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { RunDrill, RunDrillCategory, RUN_DRILL_CATEGORY_LABELS } from '../../core/models/run-drill.model';
import { RunDrillService } from '../../core/services/run-drill.service';
import { SessionCategory } from '../../core/models/session-category.model';
import { SessionCategoryService } from '../../core/services/session-category.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Éducatifs de course (gammes techniques / amplitude) — bibliothèque coach.
 * Distinct du renforcement / gainage (→ prépa physique). CDC §9 s-exercises.
 */
@Component({
  selector: 'app-run-drills',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, FormsModule],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Éducatifs course</h1>
        <p class="subtitle">Gammes techniques et d'amplitude — la prépa physique (force/gainage) est dans son propre module.</p>
      </div>
    </section>

    <div class="card create">
      <h2>Nouvel éducatif</h2>
      <div class="form-row-3">
        <div class="form-group"><label>Nom</label><input class="form-control" [(ngModel)]="draft.name" placeholder="Montées de genoux" /></div>
        <div class="form-group">
          <label>Type</label>
          <select class="form-control" [(ngModel)]="draft.category">
            <option value="TECHNIQUE">Technique</option>
            <option value="AMPLITUDE">Amplitude</option>
          </select>
        </div>
        <div class="form-group">
          <label>Catégorie</label>
          <select class="form-control" [(ngModel)]="draft.categoryId">
            <option value="">Sans catégorie</option>
            @for (c of cats(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
          </select>
        </div>
      </div>
      <div class="form-row-2">
        <div class="form-group"><label>Lien vidéo (optionnel)</label><input class="form-control" [(ngModel)]="draft.videoUrl" placeholder="https://…" /></div>
        <div class="form-group"><label>Consigne (optionnel)</label><input class="form-control" [(ngModel)]="draft.description" placeholder="Points clés d'exécution…" /></div>
      </div>
      <button type="button" class="btn btn-primary" (click)="create()">+ Ajouter</button>
    </div>

    <div class="card cat-manage">
      <div class="cat-filter">
        <span class="cf-lb">Catégorie :</span>
        <button type="button" class="chip" [class.on]="catFilter() === ''" (click)="catFilter.set('')">Toutes</button>
        @for (c of cats(); track c.id) {
          <button type="button" class="chip" [class.on]="catFilter() === c.id" (click)="catFilter.set(c.id)">{{ c.name }}</button>
        }
        <button type="button" class="chip" [class.on]="catFilter() === '__none__'" (click)="catFilter.set('__none__')">Sans catégorie</button>
      </div>
      <div class="cat-new">
        <input class="form-control" [(ngModel)]="newCatName" placeholder="Nouvelle catégorie…" (keyup.enter)="createCategory()" />
        <button type="button" class="btn btn-ghost btn-sm" (click)="createCategory()">+ Catégorie</button>
      </div>
    </div>

    @if (loading()) {
      <div class="card"><div class="skeleton" style="height: 60px;"></div></div>
    } @else if (drills().length === 0) {
      <div class="card empty-state"><h2>Aucun éducatif</h2><p class="field-hint">Ajoutez vos gammes techniques de course.</p></div>
    } @else {
      @for (cat of categories; track cat) {
        @if (byCategory()[cat].length) {
          <section class="cat">
            <h2 class="cat-title">{{ labels[cat] }} <span class="cat-count">{{ byCategory()[cat].length }}</span></h2>
            <div class="drill-grid">
              @for (d of byCategory()[cat]; track d.id) {
                <article class="card drill" [class.drill--amplitude]="d.category === 'AMPLITUDE'">
                  <div class="drill-hd">
                    <strong>{{ d.name }}</strong>
                    <button type="button" class="btn btn-ghost btn-sm danger" (click)="remove(d)" aria-label="Supprimer">✕</button>
                  </div>
                  @if (d.categoryId) { <span class="cat-badge">{{ catName(d.categoryId) }}</span> }
                  @if (d.description) { <p class="drill-desc">{{ d.description }}</p> }
                  @if (d.videoUrl) { <a class="drill-video" [href]="d.videoUrl" target="_blank" rel="noopener">▶ Voir la démo</a> }
                  <label class="drill-assign">
                    <span>Catégorie</span>
                    <select class="form-control" [ngModel]="d.categoryId ?? ''" (ngModelChange)="assignCategory(d, $event)">
                      <option value="">Sans catégorie</option>
                      @for (c of cats(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
                    </select>
                  </label>
                </article>
              }
            </div>
          </section>
        }
      }
    }
  `,
  styles: [`
    .create { margin-bottom: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-2); }
    .create h2 { margin: 0 0 var(--sp-2); font-size: var(--text-lg); }
    .form-row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-3); }

    .cat-manage { margin-bottom: var(--sp-6); display: flex; flex-direction: column; gap: var(--sp-3); }
    .cat-filter { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
    .cf-lb { font-size: var(--text-sm); color: var(--ink-3); font-weight: 700; }
    .chip { border: 1px solid var(--line); background: var(--paper); color: var(--ink-2); padding: var(--sp-1) var(--sp-3); border-radius: var(--radius-full); cursor: pointer; font-size: var(--text-sm); font-weight: 600; }
    .chip.on { background: var(--dari-teal); border-color: var(--dari-teal); color: #fff; }
    .cat-new { display: flex; gap: var(--sp-2); max-width: 360px; }
    .cat-badge { align-self: flex-start; font-size: var(--text-xs); font-weight: 700; background: var(--paper-sunk); color: var(--ink-2); padding: 1px var(--sp-2); border-radius: var(--radius-full); }
    .drill-assign { display: flex; align-items: center; gap: var(--sp-2); margin-top: auto; font-size: var(--text-xs); color: var(--ink-3); }
    .drill-assign .form-control { padding: 2px var(--sp-2); min-height: 30px; }

    .cat { margin-bottom: var(--sp-6); }
    .cat-title { display: flex; align-items: center; gap: var(--sp-2); font-size: var(--text-lg); margin: 0 0 var(--sp-3); }
    .cat-count { font-family: var(--font-data); font-size: var(--text-sm); font-weight: 800; background: var(--paper-sunk); color: var(--ink-3); padding: 0 var(--sp-2); border-radius: var(--radius-full); }

    .drill-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: var(--sp-4); }
    .drill { display: flex; flex-direction: column; gap: var(--sp-2); border-left: 4px solid var(--dari-teal); }
    .drill--amplitude { border-left-color: var(--dari-violet); }
    .drill-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .drill-hd strong { color: var(--ink); }
    .drill-desc { margin: 0; color: var(--ink-2); font-size: var(--text-sm); line-height: 1.5; }
    .drill-video { font-size: var(--text-sm); font-weight: 700; color: var(--primary); width: fit-content; }
  `],
})
export class RunDrillsComponent implements OnInit {
  private readonly service = inject(RunDrillService);
  private readonly categoryService = inject(SessionCategoryService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly labels = RUN_DRILL_CATEGORY_LABELS;
  readonly categories: RunDrillCategory[] = ['TECHNIQUE', 'AMPLITUDE'];

  readonly drills = signal<RunDrill[]>([]);
  readonly loading = signal(true);
  draft = { name: '', category: 'TECHNIQUE' as RunDrillCategory, categoryId: '' as string, description: '', videoUrl: '' };

  /** Catégories unifiées du domaine éducatifs (QA1). */
  readonly cats = signal<SessionCategory[]>([]);
  /** Filtre par catégorie unifiée : '' = toutes, '__none__' = sans catégorie, sinon id. */
  readonly catFilter = signal<string>('');
  newCatName = '';

  private readonly catNameById = computed(() => {
    const map = new Map<string, string>();
    for (const c of this.cats()) map.set(c.id, c.name);
    return map;
  });

  catName(id: string | null): string {
    return id ? this.catNameById().get(id) ?? '' : '';
  }

  readonly byCategory = computed<Record<RunDrillCategory, RunDrill[]>>(() => {
    const filter = this.catFilter();
    const drills = this.drills().filter((d) =>
      !filter || (filter === '__none__' ? !d.categoryId : d.categoryId === filter));
    const out: Record<RunDrillCategory, RunDrill[]> = { TECHNIQUE: [], AMPLITUDE: [] };
    for (const d of drills) out[d.category]?.push(d);
    return out;
  });

  ngOnInit(): void {
    this.load();
    this.loadCategories();
  }

  private loadCategories(): void {
    this.categoryService.list('DRILL').subscribe({ next: (c) => this.cats.set(c), error: () => this.cats.set([]) });
  }

  load(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (d) => { this.drills.set(d); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  create(): void {
    if (!this.draft.name.trim()) { this.toast.warning('Le nom est requis.'); return; }
    this.service.create({
      name: this.draft.name.trim(),
      category: this.draft.category,
      categoryId: this.draft.categoryId || null,
      description: this.draft.description || null,
      videoUrl: this.draft.videoUrl || null,
    }).subscribe(() => {
      this.toast.success('Éducatif ajouté');
      this.draft = { name: '', category: this.draft.category, categoryId: this.draft.categoryId, description: '', videoUrl: '' };
      this.load();
    });
  }

  /** Réassigne la catégorie unifiée d'un éducatif existant. */
  assignCategory(d: RunDrill, categoryId: string): void {
    this.service.update(d.id, {
      name: d.name, category: d.category, categoryId: categoryId || null,
      description: d.description, videoUrl: d.videoUrl,
    }).subscribe((updated) => {
      this.drills.update((list) => list.map((x) => (x.id === d.id ? updated : x)));
    });
  }

  createCategory(): void {
    const name = this.newCatName.trim();
    if (!name) return;
    this.categoryService.create({ name }, 'DRILL').subscribe((c) => {
      this.cats.update((list) => [...list, c]);
      this.newCatName = '';
      this.toast.success('Catégorie ajoutée.');
    });
  }

  async remove(d: RunDrill): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer l’éducatif', message: `Supprimer « ${d.name} » ?`, confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.service.delete(d.id).subscribe(() => { this.toast.info('Supprimé.'); this.load(); });
  }
}
