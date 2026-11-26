import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import {
  RunDrill,
  RunDrillCategory,
  RUN_DRILL_CATEGORY_LABELS,
} from '../../core/models/run-drill.model';
import { RunDrillService } from '../../core/services/run-drill.service';
import { SessionCategory } from '../../core/models/session-category.model';
import { SessionCategoryService } from '../../core/services/session-category.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { SidePanelComponent } from '../../shared/components/ui';
import { VideoEmbedComponent } from '../../shared/components/video-embed/video-embed.component';

/**
 * Éducatifs de course (gammes techniques / amplitude) — bibliothèque coach.
 * Distinct du renforcement / gainage (→ prépa physique). CDC §9 s-exercises.
 */
@Component({
  selector: 'app-run-drills',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, FormsModule, SidePanelComponent, VideoEmbedComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Éducatifs course</h1>
        <p class="subtitle">
          Gammes techniques et d'amplitude — la prépa physique (force/gainage)
          est dans son propre module.
        </p>
      </div>
      <!-- Le formulaire de création occupait le haut de la page, au-dessus de la liste qu'on vient
           consulter : il s'ouvre désormais à la demande (et d'office quand la liste est vide). -->
      @if (!showCreate()) {
        <button type="button" class="btn btn-primary" (click)="createOpen.set(true)">
          <app-icon name="plus" [size]="15" /> Nouvel éducatif
        </button>
      }
    </section>

    @if (showCreate()) {
    <div class="card create">
      <h2>Nouvel éducatif</h2>
      <div class="form-row-3">
        <div class="form-group">
          <label>Nom</label
          ><input
            class="form-control"
            [(ngModel)]="draft.name"
            placeholder="Montées de genoux"
          />
        </div>
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
            @for (c of cats(); track c.id) {
              <option [value]="c.id">{{ c.name }}</option>
            }
          </select>
        </div>
      </div>
      <div class="form-row-2">
        <div class="form-group">
          <label>Lien vidéo (optionnel)</label
          ><input
            class="form-control"
            [(ngModel)]="draft.videoUrl"
            placeholder="https://…"
          />
        </div>
        <div class="form-group">
          <label>Consigne (optionnel)</label
          ><input
            class="form-control"
            [(ngModel)]="draft.description"
            placeholder="Points clés d'exécution…"
          />
        </div>
      </div>
      <div class="create-actions">
        <button type="button" class="btn btn-primary" (click)="create()">
          + Ajouter
        </button>
        @if (drills().length) {
          <button type="button" class="btn btn-ghost" (click)="createOpen.set(false)">Fermer</button>
        }
      </div>
    </div>
    }

    <div class="card cat-manage">
      <div class="cat-filter">
        <span class="cf-lb">Catégorie :</span>
        <button
          type="button"
          class="chip"
          [class.on]="catFilter() === ''"
          (click)="catFilter.set('')"
        >
          Toutes
        </button>
        @for (c of cats(); track c.id) {
          <button
            type="button"
            class="chip"
            [class.on]="catFilter() === c.id"
            (click)="catFilter.set(c.id)"
          >
            {{ c.name }}
          </button>
        }
        <button
          type="button"
          class="chip"
          [class.on]="catFilter() === '__none__'"
          (click)="catFilter.set('__none__')"
        >
          Sans catégorie
        </button>
      </div>
      <div class="cat-new">
        <input
          class="form-control"
          [(ngModel)]="newCatName"
          placeholder="Nouvelle catégorie…"
          (keyup.enter)="createCategory()"
        />
        <button
          type="button"
          class="btn btn-ghost btn-sm"
          (click)="createCategory()"
        >
          + Catégorie
        </button>
      </div>
    </div>

    @if (loading()) {
      <div class="card"><div class="skeleton" style="height: 60px;"></div></div>
    } @else if (drills().length === 0) {
      <div class="card empty-state">
        <h2>Aucun éducatif</h2>
        <p class="field-hint">Ajoute tes gammes techniques de course.</p>
      </div>
    } @else {
      @for (cat of categories; track cat) {
        @if (byCategory()[cat].length) {
          <section class="cat">
            <h2 class="cat-title">
              {{ labels[cat] }}
              <span class="cat-count">{{ byCategory()[cat].length }}</span>
            </h2>
            <div class="drill-grid">
              @for (d of byCategory()[cat]; track d.id) {
                <!-- La carte s'ouvre comme une séance de course : fiche, puis modifier. -->
                <article
                  class="card drill"
                  [class.drill--amplitude]="d.category === 'AMPLITUDE'"
                  (click)="open(d)"
                >
                  <div class="drill-hd">
                    @if (d.categoryId) {
                      <span class="cat-badge">{{ catName(d.categoryId) }}</span>
                    }
                    <div class="drill-actions" (click)="$event.stopPropagation()">
                      <button type="button" class="icon-btn" (click)="rename(d)" title="Renommer"><app-icon name="type" [size]="15" /></button>
                      <button type="button" class="icon-btn" (click)="open(d, true)" title="Modifier"><app-icon name="pencil" [size]="15" /></button>
                      <button type="button" class="icon-btn" (click)="duplicate(d)" [disabled]="busy()" title="Dupliquer"><app-icon name="copy" [size]="15" /></button>
                      <button type="button" class="icon-btn danger" (click)="remove(d)" title="Supprimer"><app-icon name="trash-2" [size]="15" /></button>
                    </div>
                  </div>
                  <strong class="drill-name">{{ d.name }}</strong>
                  @if (d.description) {
                    <p class="drill-desc">{{ d.description }}</p>
                  }
                  <div (click)="$event.stopPropagation()">
                    <app-video-embed [url]="d.videoUrl" [title]="d.name" [compact]="true" />
                  </div>
                </article>
              }
            </div>
          </section>
        }
      }
    }

    <app-side-panel [(open)]="panelOpen" [title]="editing() ? 'Modifier l’éducatif' : (current()?.name || '')" [width]="480">
      @if (current(); as d) {
        @if (editing()) {
          <div class="sheet">
            <div class="form-group"><label>Nom</label><input class="form-control" [(ngModel)]="edit.name" /></div>
            <div class="form-row-2">
              <div class="form-group">
                <label>Type</label>
                <select class="form-control" [(ngModel)]="edit.category">
                  <option value="TECHNIQUE">Technique</option>
                  <option value="AMPLITUDE">Amplitude</option>
                </select>
              </div>
              <div class="form-group">
                <label>Catégorie</label>
                <select class="form-control" [(ngModel)]="edit.categoryId">
                  <option value="">Sans catégorie</option>
                  @for (c of cats(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
                </select>
              </div>
            </div>
            <div class="form-group"><label>Lien vidéo</label><input class="form-control" [(ngModel)]="edit.videoUrl" placeholder="https://…" /></div>
            <div class="form-group"><label>Consigne</label><textarea class="form-control" rows="4" [(ngModel)]="edit.description" placeholder="Points clés d'exécution…"></textarea></div>
            <div class="sheet-foot">
              <button type="button" class="btn btn-ghost" (click)="editing.set(false)">Annuler</button>
              <button type="button" class="btn btn-primary" (click)="save()" [disabled]="busy() || !edit.name.trim()">Enregistrer</button>
            </div>
          </div>
        } @else {
          <div class="sheet">
            <div class="sheet-actions">
              <button type="button" class="btn btn-primary btn-sm" (click)="startEdit()"><app-icon name="pencil" [size]="15" /> Modifier</button>
              <button type="button" class="btn btn-ghost btn-sm" (click)="duplicate(d)" [disabled]="busy()"><app-icon name="copy" [size]="15" /> Dupliquer</button>
              <button type="button" class="btn btn-ghost btn-sm danger-text" (click)="remove(d)"><app-icon name="trash-2" [size]="15" /> Supprimer</button>
            </div>
            <app-video-embed [url]="d.videoUrl" [title]="d.name" />
            <dl class="facts">
              <dt>Type</dt><dd>{{ labels[d.category] }}</dd>
              <dt>Catégorie</dt><dd>{{ d.categoryId ? catName(d.categoryId) : 'Sans catégorie' }}</dd>
            </dl>
            @if (d.description) {
              <h3 class="sheet-h">Consigne</h3>
              <p class="sheet-text">{{ d.description }}</p>
            } @else {
              <p class="field-hint">Pas encore de consigne : « Modifier » pour en ajouter une.</p>
            }
          </div>
        }
      }
    </app-side-panel>
  `,
  styles: [
    `
      .create {
        margin-bottom: var(--sp-4);
        display: flex;
        flex-direction: column;
        gap: var(--sp-2);
      }
      .create h2 {
        margin: 0 0 var(--sp-2);
        font-size: var(--text-lg);
      }
      .create-actions {
        display: flex;
        gap: var(--sp-2);
      }
      .form-row-2 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: var(--sp-3);
      }

      .cat-manage {
        margin-bottom: var(--sp-6);
        display: flex;
        flex-direction: column;
        gap: var(--sp-3);
      }
      .cat-filter {
        display: flex;
        align-items: center;
        gap: var(--sp-2);
        flex-wrap: wrap;
      }
      .cf-lb {
        font-size: var(--text-sm);
        color: var(--ink-3);
        font-weight: 700;
      }
      .chip {
        border: 1px solid var(--line);
        background: var(--paper);
        color: var(--ink-2);
        padding: var(--sp-1) var(--sp-3);
        border-radius: var(--radius-full);
        cursor: pointer;
        font-size: var(--text-sm);
        font-weight: 600;
      }
      .chip.on {
        background: var(--dari-teal);
        border-color: var(--dari-teal);
        color: #fff;
      }
      .cat-new {
        display: flex;
        gap: var(--sp-2);
        max-width: 360px;
      }
      .cat-badge {
        align-self: flex-start;
        font-size: var(--text-xs);
        font-weight: 700;
        background: var(--paper-sunk);
        color: var(--ink-2);
        padding: 1px var(--sp-2);
        border-radius: var(--radius-full);
      }
      .drill-assign {
        display: flex;
        align-items: center;
        gap: var(--sp-2);
        margin-top: auto;
        font-size: var(--text-xs);
        color: var(--ink-3);
      }
      .drill-assign .form-control {
        padding: 2px var(--sp-2);
        min-height: 30px;
      }

      .cat {
        margin-bottom: var(--sp-6);
      }
      .cat-title {
        display: flex;
        align-items: center;
        gap: var(--sp-2);
        font-size: var(--text-lg);
        margin: 0 0 var(--sp-3);
      }
      .cat-count {
        font-family: var(--font-data);
        font-size: var(--text-sm);
        font-weight: 800;
        background: var(--paper-sunk);
        color: var(--ink-3);
        padding: 0 var(--sp-2);
        border-radius: var(--radius-full);
      }

      .drill-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
        gap: var(--sp-4);
      }
      .drill {
        display: flex;
        flex-direction: column;
        gap: var(--sp-2);
        border-left: 4px solid var(--dari-teal);
      }
      .drill--amplitude {
        border-left-color: var(--dari-violet);
      }
      .drill-hd {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--sp-2);
      }
      .drill {
        cursor: pointer;
        transition: box-shadow var(--duration) var(--ease);
      }
      .drill:hover {
        box-shadow: var(--shadow-md);
      }
      .drill-name {
        color: var(--ink);
        font-size: var(--text-md);
      }
      .drill-hd .cat-badge {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .cat-badge.muted {
        opacity: 0.6;
      }
      .drill-actions {
        margin-left: auto;
        display: inline-flex;
        gap: 4px;
        flex-shrink: 0;
      }
      .icon-btn { width: 34px; height: 34px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid var(--hairline); background: var(--paper); border-radius: var(--radius-sm); cursor: pointer; flex-shrink: 0; color: var(--ink-2); text-decoration: none; transition: background var(--duration) var(--ease), color var(--duration) var(--ease); }
      .icon-btn:hover { background: var(--paper-sunk); color: var(--ink-1); }
      .icon-btn.danger { color: var(--danger); }
      .icon-btn.danger:hover { background: color-mix(in srgb, var(--danger) 12%, transparent); }
      .sheet { display: flex; flex-direction: column; gap: var(--sp-3); }
      .sheet-actions { display: flex; flex-wrap: wrap; gap: var(--sp-2); }
      .sheet-foot { display: flex; justify-content: flex-end; gap: var(--sp-2); }
      .facts { display: grid; grid-template-columns: max-content 1fr; gap: 6px var(--sp-3); margin: 0; font-size: var(--text-sm); }
      .facts dt { color: var(--ink-3); }
      .facts dd { margin: 0; }
      .sheet-h { font-size: var(--text-sm); margin: var(--sp-2) 0 0; }
      .sheet-text { margin: 0; white-space: pre-line; line-height: 1.5; }
      .danger-text { color: var(--danger); }
      .drill-desc {
        margin: 0;
        color: var(--ink-2);
        font-size: var(--text-sm);
        line-height: 1.5;
      }
      .drill-video {
        font-size: var(--text-sm);
        font-weight: 700;
        color: var(--primary);
        width: fit-content;
      }
    `,
  ],
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
  readonly createOpen = signal(false);
  readonly showCreate = computed(
    () => this.createOpen() || (!this.loading() && this.drills().length === 0),
  );
  draft = {
    name: '',
    category: 'TECHNIQUE' as RunDrillCategory,
    categoryId: '' as string,
    description: '',
    videoUrl: '',
  };

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
    return id ? (this.catNameById().get(id) ?? '') : '';
  }

  readonly byCategory = computed<Record<RunDrillCategory, RunDrill[]>>(() => {
    const filter = this.catFilter();
    const drills = this.drills().filter(
      (d) =>
        !filter ||
        (filter === '__none__' ? !d.categoryId : d.categoryId === filter),
    );
    const out: Record<RunDrillCategory, RunDrill[]> = {
      TECHNIQUE: [],
      AMPLITUDE: [],
    };
    for (const d of drills) out[d.category]?.push(d);
    return out;
  });

  ngOnInit(): void {
    this.load();
    this.loadCategories();
  }

  private loadCategories(): void {
    this.categoryService
      .list('DRILL')
      .subscribe({
        next: (c) => this.cats.set(c),
        error: () => this.cats.set([]),
      });
  }

  load(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (d) => {
        this.drills.set(d);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  create(): void {
    if (!this.draft.name.trim()) {
      this.toast.warning('Le nom est requis.');
      return;
    }
    this.service
      .create({
        name: this.draft.name.trim(),
        category: this.draft.category,
        categoryId: this.draft.categoryId || null,
        description: this.draft.description || null,
        videoUrl: this.draft.videoUrl || null,
      })
      .subscribe(() => {
        this.toast.success('Éducatif ajouté');
        this.createOpen.set(false);
        this.draft = {
          name: '',
          category: this.draft.category,
          categoryId: this.draft.categoryId,
          description: '',
          videoUrl: '',
        };
        this.load();
      });
  }

  /** Réassigne la catégorie unifiée d'un éducatif existant. */
  assignCategory(d: RunDrill, categoryId: string): void {
    this.service
      .update(d.id, {
        name: d.name,
        category: d.category,
        categoryId: categoryId || null,
        description: d.description,
        videoUrl: d.videoUrl,
      })
      .subscribe((updated) => {
        this.drills.update((list) =>
          list.map((x) => (x.id === d.id ? updated : x)),
        );
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

  // --- Fiche d'un éducatif : consulter, modifier, renommer, dupliquer ------------------
  // Un éducatif créé ne se corrigeait plus : seule sa catégorie se changeait.
  readonly panelOpen = signal(false);
  readonly current = signal<RunDrill | null>(null);
  readonly editing = signal(false);
  readonly busy = signal(false);
  edit = { name: '', category: 'TECHNIQUE' as RunDrillCategory, categoryId: '', description: '', videoUrl: '' };

  open(d: RunDrill, edit = false): void {
    this.current.set(d);
    this.editing.set(false);
    if (edit) this.startEdit();
    this.panelOpen.set(true);
  }

  startEdit(): void {
    const d = this.current();
    if (!d) return;
    this.edit = {
      name: d.name, category: d.category, categoryId: d.categoryId ?? '',
      description: d.description ?? '', videoUrl: d.videoUrl ?? '',
    };
    this.editing.set(true);
  }

  save(): void {
    const d = this.current();
    if (!d || !this.edit.name.trim() || this.busy()) return;
    this.busy.set(true);
    this.service
      .update(d.id, {
        name: this.edit.name.trim(),
        category: this.edit.category,
        categoryId: this.edit.categoryId || null,
        description: this.edit.description.trim() || null,
        videoUrl: this.edit.videoUrl.trim() || null,
      })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.replace(updated);
          this.current.set(updated);
          this.editing.set(false);
          this.toast.success('Éducatif enregistré');
        },
        error: () => {
          this.busy.set(false);
          this.toast.error('Enregistrement impossible.');
        },
      });
  }

  async rename(d: RunDrill): Promise<void> {
    const answer = await this.confirm.prompt({
      title: 'Renommer l’éducatif',
      message: 'Le nom range l’éducatif dans ta bibliothèque.',
      promptLabel: 'Nom de l’éducatif',
      initialValue: d.name,
      confirmLabel: 'Renommer',
    });
    const name = answer?.trim();
    if (!name || name === d.name) return;
    this.service
      .update(d.id, { name, category: d.category, categoryId: d.categoryId, description: d.description, videoUrl: d.videoUrl })
      .subscribe({
        next: (updated) => {
          this.replace(updated);
          if (this.current()?.id === d.id) this.current.set(updated);
          this.toast.success('Éducatif renommé');
        },
        error: () => this.toast.error('Renommage impossible.'),
      });
  }

  duplicate(d: RunDrill): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.service
      .create({ name: `${d.name} (copie)`, category: d.category, categoryId: d.categoryId, description: d.description, videoUrl: d.videoUrl })
      .subscribe({
        next: (copy) => {
          this.busy.set(false);
          this.drills.update((list) => [...list, copy]);
          this.toast.success(`« ${copy.name} » créé`);
          this.open(copy, true);
        },
        error: () => {
          this.busy.set(false);
          this.toast.error('Duplication impossible.');
        },
      });
  }

  private replace(updated: RunDrill): void {
    this.drills.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
  }

  async remove(d: RunDrill): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer l’éducatif',
      message: `Supprimer « ${d.name} » ?`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    this.service.delete(d.id).subscribe(() => {
      this.toast.info('Supprimé.');
      if (this.current()?.id === d.id) this.panelOpen.set(false);
      this.load();
    });
  }
}
