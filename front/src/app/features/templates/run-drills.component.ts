import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { RunDrill, RunDrillCategory, RUN_DRILL_CATEGORY_LABELS } from '../../core/models/run-drill.model';
import { RunDrillService } from '../../core/services/run-drill.service';
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
          <label>Catégorie</label>
          <select class="form-control" [(ngModel)]="draft.category">
            <option value="TECHNIQUE">Technique</option>
            <option value="AMPLITUDE">Amplitude</option>
          </select>
        </div>
        <div class="form-group"><label>Lien vidéo (optionnel)</label><input class="form-control" [(ngModel)]="draft.videoUrl" placeholder="https://…" /></div>
      </div>
      <div class="form-group"><label>Consigne (optionnel)</label><textarea class="form-control" rows="2" [(ngModel)]="draft.description" placeholder="Points clés d'exécution…"></textarea></div>
      <button type="button" class="btn btn-primary" (click)="create()">+ Ajouter</button>
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
                  @if (d.description) { <p class="drill-desc">{{ d.description }}</p> }
                  @if (d.videoUrl) { <a class="drill-video" [href]="d.videoUrl" target="_blank" rel="noopener">▶ Voir la démo</a> }
                </article>
              }
            </div>
          </section>
        }
      }
    }
  `,
  styles: [`
    .create { margin-bottom: var(--sp-6); display: flex; flex-direction: column; gap: var(--sp-2); }
    .create h2 { margin: 0 0 var(--sp-2); font-size: var(--text-lg); }

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
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly labels = RUN_DRILL_CATEGORY_LABELS;
  readonly categories: RunDrillCategory[] = ['TECHNIQUE', 'AMPLITUDE'];

  readonly drills = signal<RunDrill[]>([]);
  readonly loading = signal(true);
  draft = { name: '', category: 'TECHNIQUE' as RunDrillCategory, description: '', videoUrl: '' };

  readonly byCategory = computed<Record<RunDrillCategory, RunDrill[]>>(() => {
    const out: Record<RunDrillCategory, RunDrill[]> = { TECHNIQUE: [], AMPLITUDE: [] };
    for (const d of this.drills()) out[d.category]?.push(d);
    return out;
  });

  ngOnInit(): void { this.load(); }

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
      description: this.draft.description || null,
      videoUrl: this.draft.videoUrl || null,
    }).subscribe(() => {
      this.toast.success('Éducatif ajouté');
      this.draft = { name: '', category: this.draft.category, description: '', videoUrl: '' };
      this.load();
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
