import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { MetricType } from '../../core/models/metric-type.model';
import { TrainingZone } from '../../core/models/training-zone.model';
import { MetricTypeService } from '../../core/services/metric-type.service';
import { TrainingZoneService } from '../../core/services/training-zone.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Écran « Zones & métriques » (niveau club) — chantier Z1.
 * Le coach définit ses zones de travail (nom, couleur, ordre) et les métriques que chaque zone
 * porte. Les valeurs concrètes par athlète viendront sur la fiche athlète (chantier Z2).
 */
@Component({
  selector: 'app-training-zones',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, FormsModule, DragDropModule],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Zones &amp; métriques</h1>
        <p class="subtitle">
          Définissez vos zones de travail et les métriques qu'elles portent. Les valeurs concrètes
          (allure, FC…) se règlent sur chaque fiche athlète.
        </p>
      </div>
    </section>

    <div class="card create">
      <h2>Nouvelle zone</h2>
      <div class="create-row">
        <input type="color" class="swatch-input" [(ngModel)]="draft.color" aria-label="Couleur de la zone" />
        <input class="form-control" [(ngModel)]="draft.name" placeholder="Nom de la zone (ex. Fartlek)"
               (keyup.enter)="create()" />
        <button type="button" class="btn btn-primary" (click)="create()" [disabled]="!draft.name.trim()">+ Ajouter</button>
      </div>
    </div>

    @if (loading()) {
      <div class="card"><div class="skeleton" style="height: 120px;"></div></div>
    } @else if (zones().length === 0) {
      <div class="card empty-state"><h2>Aucune zone</h2><p class="field-hint">Ajoutez votre première zone de travail.</p></div>
    } @else {
      <div class="card zones" cdkDropList (cdkDropListDropped)="drop($event)">
        @for (z of zones(); track z.id) {
          <article class="zone" cdkDrag [cdkDragData]="z">
            <div class="zone-main">
              <button type="button" class="drag-handle" cdkDragHandle aria-label="Réordonner">
                <app-icon name="grip-vertical" [size]="18" />
              </button>
              <span class="dot" [style.background]="z.color || 'var(--ink-3)'"></span>

              @if (editingId() === z.id) {
                <input type="color" class="swatch-input" [(ngModel)]="editDraft.color" aria-label="Couleur" />
                <input class="form-control edit-name" [(ngModel)]="editDraft.name" (keyup.enter)="saveEdit(z)" />
                <button type="button" class="btn btn-primary btn-sm" (click)="saveEdit(z)">Enregistrer</button>
                <button type="button" class="btn btn-ghost btn-sm" (click)="cancelEdit()">Annuler</button>
              } @else {
                <strong class="zone-name">{{ z.name }}</strong>
                @if (z.builtin) { <span class="badge badge-neutral">standard</span> }

                <div class="zone-metrics">
                  @for (mid of z.metricTypeIds; track mid) {
                    <span class="metric-chip">{{ metricName(mid) }}</span>
                  }
                  @if (z.metricTypeIds.length === 0) {
                    <span class="field-hint">Aucune métrique</span>
                  }
                </div>

                <div class="zone-actions">
                  <button type="button" class="btn btn-ghost btn-sm" (click)="toggleConfig(z.id)"
                          [class.active]="configId() === z.id" title="Métriques portées">
                    <app-icon name="settings" [size]="16" /> Métriques
                  </button>
                  <button type="button" class="btn btn-ghost btn-sm" (click)="startEdit(z)" aria-label="Renommer">
                    <app-icon name="pencil" [size]="16" />
                  </button>
                  <button type="button" class="btn btn-ghost btn-sm danger" (click)="remove(z)" aria-label="Supprimer">✕</button>
                </div>
              }
            </div>

            @if (configId() === z.id) {
              <div class="config">
                <span class="config-label">Métriques portées par cette zone :</span>
                <div class="metric-toggles">
                  @for (m of metrics(); track m.id) {
                    <button type="button" class="toggle" [class.on]="z.metricTypeIds.includes(m.id)"
                            (click)="toggleMetric(z, m)">
                      {{ m.name }}
                    </button>
                  }
                </div>
              </div>
            }
          </article>
        }
      </div>
    }
  `,
  styles: [`
    .create { margin-bottom: var(--sp-6); display: flex; flex-direction: column; gap: var(--sp-2); }
    .create h2 { margin: 0 0 var(--sp-2); font-size: var(--text-lg); }
    .create-row { display: flex; align-items: center; gap: var(--sp-3); }
    .create-row .form-control { flex: 1; }

    .swatch-input { width: 40px; height: 38px; padding: 2px; border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--paper); cursor: pointer; flex: none; }

    .zones { display: flex; flex-direction: column; gap: var(--sp-2); }
    .zone { border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--paper); }
    .zone.cdk-drag-preview { box-shadow: var(--shadow-lg); }
    .zone.cdk-drag-placeholder { opacity: 0.4; }

    .zone-main { display: flex; align-items: center; gap: var(--sp-3); padding: var(--sp-3); flex-wrap: wrap; }
    .drag-handle { border: none; background: none; color: var(--ink-3); cursor: grab; display: flex; padding: 0; }
    .drag-handle:active { cursor: grabbing; }
    .dot { width: 14px; height: 14px; border-radius: var(--radius-full); flex: none; }
    .zone-name { font-size: var(--text-md); }
    .edit-name { flex: 1; min-width: 160px; }

    .zone-metrics { display: flex; gap: var(--sp-1); flex-wrap: wrap; margin-left: var(--sp-2); }
    .metric-chip { font-size: var(--text-xs); font-weight: 700; background: var(--paper-sunk); color: var(--ink-2); padding: 0 var(--sp-2); border-radius: var(--radius-full); line-height: 1.6; }

    .zone-actions { display: flex; align-items: center; gap: var(--sp-1); margin-left: auto; }
    .btn.active { background: var(--paper-sunk); }
    .danger { color: var(--danger); }

    .config { border-top: 1px solid var(--line); padding: var(--sp-3); display: flex; flex-direction: column; gap: var(--sp-2); }
    .config-label { font-size: var(--text-sm); color: var(--ink-2); font-weight: 700; }
    .metric-toggles { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .toggle { border: 1px solid var(--line); background: var(--paper); color: var(--ink-2); padding: var(--sp-1) var(--sp-3); border-radius: var(--radius-full); cursor: pointer; font-size: var(--text-sm); font-weight: 600; }
    .toggle.on { background: var(--dari-teal); border-color: var(--dari-teal); color: #fff; }

    @media (max-width: 640px) {
      .zone-actions { margin-left: 0; width: 100%; justify-content: flex-end; }
    }
  `],
})
export class TrainingZonesComponent implements OnInit {
  private readonly zoneService = inject(TrainingZoneService);
  private readonly metricService = inject(MetricTypeService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly zones = signal<TrainingZone[]>([]);
  readonly metrics = signal<MetricType[]>([]);
  readonly loading = signal(true);
  readonly editingId = signal<string | null>(null);
  readonly configId = signal<string | null>(null);

  draft = { name: '', color: '#22c55e' };
  editDraft = { name: '', color: '#22c55e' };

  private readonly metricMap = computed(() => {
    const map = new Map<string, MetricType>();
    for (const m of this.metrics()) map.set(m.id, m);
    return map;
  });

  ngOnInit(): void {
    forkJoin({ zones: this.zoneService.list(), metrics: this.metricService.list() }).subscribe({
      next: ({ zones, metrics }) => {
        this.metrics.set(metrics);
        this.zones.set(zones);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  metricName(id: string): string {
    return this.metricMap().get(id)?.name ?? '—';
  }

  create(): void {
    const name = this.draft.name.trim();
    if (!name) return;
    this.zoneService.create({ name, color: this.draft.color }).subscribe((z) => {
      this.zones.update((list) => [...list, z]);
      this.draft = { name: '', color: '#22c55e' };
      this.toast.success('Zone ajoutée.');
    });
  }

  startEdit(z: TrainingZone): void {
    this.editDraft = { name: z.name, color: z.color || '#22c55e' };
    this.configId.set(null);
    this.editingId.set(z.id);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(z: TrainingZone): void {
    const name = this.editDraft.name.trim();
    if (!name) return;
    this.zoneService.update(z.id, { name, color: this.editDraft.color }).subscribe((updated) => {
      this.zones.update((list) => list.map((x) => (x.id === z.id ? updated : x)));
      this.editingId.set(null);
      this.toast.success('Zone mise à jour.');
    });
  }

  async remove(z: TrainingZone): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer la zone',
      message: `Supprimer « ${z.name} » ? Les séances qui la référencent perdront leur cible.`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    this.zoneService.delete(z.id).subscribe(() => {
      this.zones.update((list) => list.filter((x) => x.id !== z.id));
      this.toast.success('Zone supprimée.');
    });
  }

  drop(event: CdkDragDrop<TrainingZone[]>): void {
    if (event.previousIndex === event.currentIndex) return;
    const list = [...this.zones()];
    moveItemInArray(list, event.previousIndex, event.currentIndex);
    this.zones.set(list);
    this.zoneService.reorder(list.map((z) => z.id)).subscribe({
      error: () => this.toast.error('Réordonnancement non enregistré.'),
    });
  }

  toggleConfig(id: string): void {
    this.configId.update((cur) => (cur === id ? null : id));
  }

  toggleMetric(z: TrainingZone, m: MetricType): void {
    const has = z.metricTypeIds.includes(m.id);
    const ids = has ? z.metricTypeIds.filter((x) => x !== m.id) : [...z.metricTypeIds, m.id];
    this.zoneService.setMetrics(z.id, { metricTypeIds: ids }).subscribe((updated) => {
      this.zones.update((list) => list.map((x) => (x.id === z.id ? updated : x)));
    });
  }
}
