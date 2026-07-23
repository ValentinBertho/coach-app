import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { MetricType } from '../../core/models/metric-type.model';
import {
  TrainingZone,
  ZoneAnchor,
  ZoneModel,
  ZoneRule,
  ZoneRuleRequest,
  ZONE_ANCHOR_LABELS,
  ZONE_MODEL_LABELS,
} from '../../core/models/training-zone.model';
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
      <div class="create-row">
        <input type="color" class="swatch-input" [(ngModel)]="draft.color" aria-label="Couleur de la zone" />
        <input class="form-control name-in" [(ngModel)]="draft.name" placeholder="Nom de la zone (ex. Fartlek)" (keyup.enter)="create()" />
        <input class="form-control" [(ngModel)]="draft.description" placeholder="Description (optionnel)" (keyup.enter)="create()" />
        <button type="button" class="btn btn-primary" (click)="create()" [disabled]="!draft.name.trim()">+ Ajouter</button>
      </div>
    </div>

    @if (loading()) {
      <div class="card"><div class="skeleton" style="height: 120px;"></div></div>
    } @else if (zones().length === 0) {
      <div class="card empty-state"><h2>Aucune zone</h2><p class="field-hint">Ajoutez votre première zone de travail.</p></div>
    } @else {
      <!-- Échelles par métrique (façon Nolio) : chaque onglet montre l'échelle d'une métrique. -->
      <div class="scale-tabs" role="tablist">
        <button type="button" class="scale-tab" [class.active]="scaleTab() === null" (click)="scaleTab.set(null)">
          Toutes <span class="scale-count">{{ zones().length }}</span>
        </button>
        @for (m of scaleMetrics(); track m.id) {
          <button type="button" class="scale-tab" [class.active]="scaleTab() === m.id" (click)="scaleTab.set(m.id)">
            {{ m.name }} <span class="scale-count">{{ zoneCountFor(m.id) }}</span>
          </button>
        }
      </div>

      <div class="card ztable">
        <div class="zt-head">
          <span class="zt-order">Ordre</span>
          <span></span>
          <span>Zone</span>
          <span>Métriques</span>
          <span>Description</span>
          <span></span>
        </div>
        <div cdkDropList (cdkDropListDropped)="drop($event)">
          @for (z of displayedZones(); track z.id) {
            <div class="zrow" cdkDrag [cdkDragData]="z">
              <span class="zt-order metric">{{ globalRank(z) }}</span>
              <button type="button" class="drag-handle" cdkDragHandle aria-label="Réordonner">
                <app-icon name="grip-vertical" [size]="16" />
              </button>

              @if (editingId() === z.id) {
                <span class="zcell zcell--name edit">
                  <input type="color" class="swatch-input" [(ngModel)]="editDraft.color" aria-label="Couleur" />
                  <input class="form-control" [(ngModel)]="editDraft.name" (keyup.enter)="saveEdit(z)" />
                </span>
                <span class="zcell zcell--metrics"></span>
                <span class="zcell zcell--desc">
                  <input class="form-control" [(ngModel)]="editDraft.description" placeholder="Description" (keyup.enter)="saveEdit(z)" />
                </span>
                <span class="zcell zcell--actions">
                  <button type="button" class="btn btn-primary btn-sm" (click)="saveEdit(z)">OK</button>
                  <button type="button" class="btn btn-ghost btn-sm" (click)="cancelEdit()">✕</button>
                </span>
              } @else {
                <span class="zcell zcell--name">
                  <span class="dot" [style.background]="z.color || 'var(--ink-3)'"></span>
                  <strong class="zone-name">{{ z.name }}</strong>
                  @if (z.builtin) { <span class="badge badge-neutral">std</span> }
                  @if (z.discipline) { <span class="badge badge-neutral">{{ z.discipline === 'TRAIL' ? 'Trail' : 'Route' }}</span> }
                  @else { <span class="badge badge-neutral sport-all" title="Toutes disciplines">Tous sports</span> }
                </span>
                <span class="zcell zcell--metrics">
                  @for (mid of z.metricTypeIds; track mid) { <span class="metric-chip">{{ metricName(mid) }}</span> }
                  @if (z.metricTypeIds.length === 0) { <span class="field-hint">—</span> }
                </span>
                <span class="zcell zcell--desc">{{ z.description || '—' }}</span>
                <span class="zcell zcell--actions">
                  <button type="button" class="btn btn-ghost btn-sm" (click)="toggleConfig(z.id)" [class.active]="configId() === z.id" title="Métriques portées">
                    <app-icon name="settings" [size]="16" />
                  </button>
                  <button type="button" class="btn btn-ghost btn-sm" (click)="startEdit(z)" aria-label="Modifier">
                    <app-icon name="pencil" [size]="16" />
                  </button>
                  <button type="button" class="btn btn-ghost btn-sm danger" (click)="remove(z)" aria-label="Supprimer">✕</button>
                </span>
              }

              @if (configId() === z.id) {
                <div class="config">
                  <span class="config-label">Métriques portées par cette zone :</span>
                  <div class="metric-toggles">
                    @for (m of metrics(); track m.id) {
                      <button type="button" class="toggle" [class.on]="z.metricTypeIds.includes(m.id)" (click)="toggleMetric(z, m)">{{ m.name }}</button>
                    }
                  </div>

                  <span class="config-label">Règle de calcul par métrique :</span>
                  @for (mid of z.metricTypeIds; track mid) {
                    @if (ruleFor(z, mid); as r) {
                      <div class="rule-row">
                        <span class="rule-metric">{{ metricName(mid) }}</span>
                        <select class="form-control" [ngModel]="r.anchor" (ngModelChange)="saveRule(z, mid, { anchor: $event })" title="Ancre">
                          <option [ngValue]="null">— ancre —</option>
                          @for (a of anchors; track a) { <option [ngValue]="a">{{ anchorLabels[a] }}</option> }
                        </select>
                        <span class="rule-pct">
                          <input type="number" class="form-control mini" [ngModel]="r.lowPct" (ngModelChange)="saveRule(z, mid, { lowPct: $event })" placeholder="min %" />
                          –
                          <input type="number" class="form-control mini" [ngModel]="r.highPct" (ngModelChange)="saveRule(z, mid, { highPct: $event })" placeholder="max %" />
                          <span class="rule-unit">%</span>
                        </span>
                        <select class="form-control" [ngModel]="r.model" (ngModelChange)="saveRule(z, mid, { model: $event })" title="Modèle">
                          @for (mo of models; track mo) { <option [ngValue]="mo">{{ modelLabels[mo] }}</option> }
                        </select>
                      </div>
                    }
                  }
                  <p class="rule-hint field-hint">Les valeurs par athlète sont recalculées depuis ces règles (ancre × %), automatiquement quand une valeur de référence change.</p>
                </div>
              }
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    .create { margin-bottom: var(--sp-5); }
    .create-row { display: flex; align-items: center; gap: var(--sp-3); }
    .create-row .form-control { flex: 1; }
    .create-row .name-in { flex: 0 1 260px; }

    .swatch-input { width: 40px; height: 38px; padding: 2px; border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--paper); cursor: pointer; flex: none; }

    .scale-tabs { display: flex; gap: var(--sp-1); margin-bottom: var(--sp-3); flex-wrap: wrap; }
    .scale-tab { display: inline-flex; align-items: center; gap: var(--sp-1); border: 1px solid var(--line); background: var(--paper); color: var(--ink-2); padding: var(--sp-1) var(--sp-3); border-radius: var(--radius-full); cursor: pointer; font-size: var(--text-sm); font-weight: 700; }
    .scale-tab.active { background: var(--dari-teal); border-color: var(--dari-teal); color: #fff; }
    .scale-count { font-family: var(--font-data); font-size: var(--text-xs); background: rgba(0,0,0,0.08); border-radius: var(--radius-full); padding: 0 var(--sp-1); min-width: 18px; text-align: center; }
    .scale-tab.active .scale-count { background: rgba(255,255,255,0.25); }

    .ztable { padding: 0; overflow: hidden; }
    .zt-head, .zrow {
      display: grid;
      grid-template-columns: 56px 28px minmax(180px, 1.3fr) minmax(160px, 1.4fr) minmax(160px, 2fr) auto;
      align-items: center; gap: var(--sp-2);
      padding: var(--sp-2) var(--sp-4);
    }
    .zt-head { border-bottom: 1px solid var(--line); }
    .zt-head span { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); font-weight: 700; }
    .zt-order { text-align: center; }

    .zrow { border-bottom: 1px solid var(--hairline); background: var(--paper); position: relative; }
    .zrow:last-child { border-bottom: none; }
    .zrow.cdk-drag-preview { box-shadow: var(--shadow-lg); border-radius: var(--radius-md); }
    .zrow.cdk-drag-placeholder { opacity: 0.4; }
    .zt-order.metric { text-align: center; font-weight: 800; color: var(--ink-3); }

    .drag-handle { border: none; background: none; color: var(--ink-4); cursor: grab; display: flex; padding: 0; }
    .drag-handle:active { cursor: grabbing; }

    .zcell { min-width: 0; }
    .zcell--name { display: flex; align-items: center; gap: var(--sp-2); }
    .zcell--name.edit { gap: var(--sp-2); }
    .dot { width: 14px; height: 14px; border-radius: var(--radius-full); flex: none; }
    .zone-name { font-size: var(--text-md); }
    .badge.sport-all { opacity: 0.6; }
    .zcell--metrics { display: flex; gap: var(--sp-1); flex-wrap: wrap; }
    .metric-chip { font-size: var(--text-xs); font-weight: 700; background: var(--paper-sunk); color: var(--ink-2); padding: 0 var(--sp-2); border-radius: var(--radius-full); line-height: 1.6; }
    .zcell--desc { color: var(--ink-2); font-size: var(--text-sm); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .zcell--actions { display: flex; align-items: center; gap: var(--sp-1); justify-content: flex-end; }
    .btn.active { background: var(--paper-sunk); }
    .danger { color: var(--danger); }

    .config { grid-column: 1 / -1; border-top: 1px dashed var(--line); margin-top: var(--sp-2); padding-top: var(--sp-3); display: flex; flex-direction: column; gap: var(--sp-2); }
    .config-label { font-size: var(--text-sm); color: var(--ink-2); font-weight: 700; }
    .metric-toggles { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .toggle { border: 1px solid var(--line); background: var(--paper); color: var(--ink-2); padding: var(--sp-1) var(--sp-3); border-radius: var(--radius-full); cursor: pointer; font-size: var(--text-sm); font-weight: 600; }
    .toggle.on { background: var(--dari-teal); border-color: var(--dari-teal); color: #fff; }

    .rule-row { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
    .rule-metric { min-width: 96px; font-weight: 700; font-size: var(--text-sm); color: var(--ink-2); }
    .rule-row .form-control { min-height: 32px; padding: 2px var(--sp-2); }
    .rule-pct { display: inline-flex; align-items: center; gap: var(--sp-1); }
    .rule-pct .mini { width: 62px; }
    .rule-unit { color: var(--ink-3); font-weight: 700; }
    .rule-hint { margin: var(--sp-1) 0 0; }

    @media (max-width: 760px) {
      .zt-head { display: none; }
      .zrow { grid-template-columns: 40px 24px 1fr auto; row-gap: var(--sp-1); }
      .zcell--metrics { grid-column: 3 / -1; }
      .zcell--desc { grid-column: 3 / -1; white-space: normal; }
    }

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
  /** Onglet d'échelle actif : null = toutes les zones, sinon l'id d'une métrique. */
  readonly scaleTab = signal<string | null>(null);

  /** Métriques portées par au moins une zone (onglets d'échelle), dans l'ordre du catalogue. */
  readonly scaleMetrics = computed<MetricType[]>(() => {
    const carried = new Set<string>();
    for (const z of this.zones()) for (const id of z.metricTypeIds) carried.add(id);
    return this.metrics().filter((m) => carried.has(m.id));
  });

  /** Zones affichées selon l'onglet : toutes, ou celles portant la métrique sélectionnée. */
  readonly displayedZones = computed<TrainingZone[]>(() => {
    const tab = this.scaleTab();
    if (!tab) return this.zones();
    return this.zones().filter((z) => z.metricTypeIds.includes(tab));
  });

  draft = { name: '', color: '#22c55e', description: '' };
  editDraft = { name: '', color: '#22c55e', description: '' };

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

  /** Nombre de zones portant une métrique (badge d'onglet). */
  zoneCountFor(metricId: string): number {
    return this.zones().filter((z) => z.metricTypeIds.includes(metricId)).length;
  }

  /** Rang global (1-based) d'une zone dans l'ordre complet, indépendant du filtre. */
  globalRank(z: TrainingZone): number {
    return this.zones().findIndex((x) => x.id === z.id) + 1;
  }

  create(): void {
    const name = this.draft.name.trim();
    if (!name) return;
    this.zoneService.create({ name, color: this.draft.color, description: this.draft.description || null }).subscribe((z) => {
      this.zones.update((list) => [...list, z]);
      this.draft = { name: '', color: '#22c55e', description: '' };
      this.toast.success('Zone ajoutée.');
    });
  }

  startEdit(z: TrainingZone): void {
    this.editDraft = { name: z.name, color: z.color || '#22c55e', description: z.description || '' };
    this.configId.set(null);
    this.editingId.set(z.id);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(z: TrainingZone): void {
    const name = this.editDraft.name.trim();
    if (!name) return;
    this.zoneService.update(z.id, { name, color: this.editDraft.color, description: this.editDraft.description || null }).subscribe((updated) => {
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
    // Réordonne au sein de la vue filtrée, puis réinjecte dans l'ordre global (les zones hors
    // filtre gardent leur position) avant d'envoyer l'ordre complet au serveur.
    const shown = [...this.displayedZones()];
    moveItemInArray(shown, event.previousIndex, event.currentIndex);
    const shownIds = new Set(shown.map((z) => z.id));
    let k = 0;
    const full = this.zones().map((z) => (shownIds.has(z.id) ? shown[k++] : z));
    this.zones.set(full);
    this.zoneService.reorder(full.map((z) => z.id)).subscribe({
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

  // --- Règles de calcul (chantier zones v2) --------------------------------

  readonly anchors: ZoneAnchor[] = [
    'LT1', 'LT2', 'VC', 'PACE_800M', 'PACE_1500M', 'PACE_3000M', 'PACE_5KM',
    'PACE_10KM', 'PACE_15KM', 'PACE_SEMI', 'PACE_MARATHON', 'FCMAX', 'LTHR', 'HRR',
  ];
  readonly models: ZoneModel[] = ['VMA', 'VC', 'DANIELS_VDOT', 'LACTATE_THRESHOLD', 'PCT_FCMAX', 'CUSTOM'];
  readonly anchorLabels = ZONE_ANCHOR_LABELS;
  readonly modelLabels = ZONE_MODEL_LABELS;

  /** Règle courante d'un couple (zone, métrique), ou une règle vide par défaut. */
  ruleFor(z: TrainingZone, metricId: string): ZoneRule {
    return z.rules?.find((r) => r.metricTypeId === metricId)
      ?? { metricTypeId: metricId, anchor: null, lowPct: null, highPct: null, model: 'CUSTOM' };
  }

  /** Enregistre une modification partielle de règle (fusionnée avec l'existante). */
  saveRule(z: TrainingZone, metricId: string, patch: Partial<ZoneRuleRequest>): void {
    const cur = this.ruleFor(z, metricId);
    const body: ZoneRuleRequest = {
      anchor: patch.anchor !== undefined ? patch.anchor : cur.anchor,
      lowPct: patch.lowPct !== undefined ? patch.lowPct : cur.lowPct,
      highPct: patch.highPct !== undefined ? patch.highPct : cur.highPct,
      model: patch.model !== undefined ? patch.model : (cur.model ?? 'CUSTOM'),
    };
    this.zoneService.setRule(z.id, metricId, body).subscribe((updated) => {
      this.zones.update((list) => list.map((x) => (x.id === z.id ? updated : x)));
      this.toast.success('Règle mise à jour.');
    });
  }
}
