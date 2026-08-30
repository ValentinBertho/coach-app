import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
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
import { ZoneSet } from '../../core/models/zone-set.model';
import { ZoneSetService } from '../../core/services/zone-set.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Écran « Zones du club » (niveau club) — chantier Z1. Le modèle de zones ; les valeurs par
 * athlète vivent dans l'onglet « Zones » de la coquille athlète.
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
        <h1 class="display-sm">Mes zones</h1>
        <p class="subtitle">
          Définis tes zones de travail et les métriques qu'elles portent. Les valeurs concrètes
          (allure, FC…) se règlent sur chaque fiche athlète.
        </p>
      </div>
    </section>

    <!-- Modèles de zones : plusieurs échelles (route / trail, débutant / confirmé…), chacune
         applicable aux athlètes de son choix depuis leur onglet « Zones ». -->
    <div class="card sets">
      <div class="sets-row">
        <span class="sets-lb">Modèle de zones</span>
        <select class="form-control set-select" [ngModel]="activeSetId()" (ngModelChange)="onSetChange($event)">
          @for (s of sets(); track s.id) {
            <option [value]="s.id">{{ s.name }} ({{ s.zoneCount }} zones)</option>
          }
        </select>
        <button type="button" class="btn btn-ghost btn-sm" (click)="duplicateSet()" [disabled]="setBusy()">
          <app-icon name="copy" [size]="15" /> Dupliquer
        </button>
        <button type="button" class="btn btn-ghost btn-sm" (click)="renameSet()" [disabled]="setBusy() || !activeSet()">
          <app-icon name="pencil" [size]="15" /> Renommer
        </button>
        <button type="button" class="btn btn-ghost btn-sm danger" (click)="deleteSet()"
                [disabled]="setBusy() || !activeSet() || activeSet()!.isDefault" title="Le modèle par défaut n'est pas supprimable">
          <app-icon name="x" [size]="15" /> Supprimer
        </button>
      </div>
      <p class="field-hint">
        Les zones ci-dessous sont celles de ce modèle. Un athlète reçoit le modèle par défaut tant
        qu'un autre ne lui est pas appliqué depuis son onglet « Zones ».
      </p>
    </div>

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
      <div class="card empty-state"><h2>Aucune zone</h2><p class="field-hint">Ajoute ta première zone de travail.</p></div>
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
          <span>Règle d'allure</span>
          <span></span>
        </div>
        <div cdkDropList (cdkDropListDropped)="drop($event)">
          @for (z of displayedZones(); track z.id) {
            <div class="zrow" [class.zrow--target]="highlighted() === z.id" [attr.data-zone]="z.id"
                 cdkDrag [cdkDragData]="z">
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
                  <button type="button" class="btn btn-ghost btn-sm" (click)="cancelEdit()" aria-label="Annuler"><app-icon name="x" [size]="15" /></button>
                </span>
              } @else {
                <span class="zcell zcell--name">
                  <span class="zn-top">
                    <span class="dot" [style.background]="z.color || 'var(--ink-3)'"></span>
                    <strong class="zone-name">{{ z.name }}</strong>
                  </span>
                  <span class="zn-meta">
                    @if (z.builtin) { <span class="badge badge-neutral">std</span> }
                    @if (z.discipline) { <span class="badge badge-neutral">{{ z.discipline === 'TRAIL' ? 'Trail' : 'Route' }}</span> }
                    @else { <span class="badge badge-neutral sport-all" title="Toutes disciplines">Tous sports</span> }
                  </span>
                </span>
                <span class="zcell zcell--metrics">
                  @for (mid of z.metricTypeIds; track mid) { <span class="metric-chip">{{ metricName(mid) }}</span> }
                  @if (z.metricTypeIds.length === 0) { <span class="field-hint">—</span> }
                </span>
                <!-- La règle se lit en liste : « 90 – 100 % LT1 », « 100 % LT1 → 93 % LT2 ». C'est
                     l'échelle entière qui devient lisible d'un coup d'œil, du footing au 800 m. -->
                <span class="zcell zcell--rule">
                  @if (paceBounds(z); as b) {
                    <!-- Les deux pourcentages se règlent ici même. C'est le geste courant — décaler
                         un seuil de deux points — et il demandait jusqu'ici d'ouvrir l'engrenage,
                         de passer le paragraphe sur les métriques, puis d'éditer bornes et
                         références. Les références restent là-bas : elles disent CE QU'ON MESURE,
                         le pourcentage n'est que le cran auquel on le règle.
                         (change) et non (ngModelChange) : on enregistre au relâchement, pas à
                         chaque frappe — sinon « 92 » part en trois requêtes dont deux fausses. -->
                    <span class="rule-edit">
                      <input type="number" class="form-control mini" [ngModel]="b.low" min="0" max="200"
                             (change)="savePct(z, 'low', $any($event.target).value)"
                             [title]="'Borne basse, en % de ' + b.lowRef" aria-label="Pourcentage de la borne basse" />
                      <span class="rule-unit">–</span>
                      <input type="number" class="form-control mini" [ngModel]="b.high" min="0" max="200"
                             (change)="savePct(z, 'high', $any($event.target).value)"
                             [title]="'Borne haute, en % de ' + b.highRef" aria-label="Pourcentage de la borne haute" />
                      <span class="rule-unit">%</span>
                      <span class="rule-ref field-hint">{{ b.refLabel }}</span>
                    </span>
                    @if (gapWith(z); as gap) {
                      <span class="rule-warn" [title]="gap.detail">
                        <app-icon name="alert-triangle" [size]="13" /> {{ gap.label }}
                      </span>
                    }
                  } @else {
                    <span class="field-hint">—</span>
                  }
                </span>
                <span class="zcell zcell--actions">
                  <button type="button" class="btn btn-ghost btn-sm" (click)="toggleConfig(z.id)" [class.active]="configId() === z.id" title="Métriques &amp; règle de calcul">
                    <app-icon name="settings" [size]="16" />
                  </button>
                  <button type="button" class="btn btn-ghost btn-sm" (click)="startEdit(z)" aria-label="Modifier">
                    <app-icon name="pencil" [size]="16" />
                  </button>
                  <button type="button" class="btn btn-ghost btn-sm danger" (click)="remove(z)" aria-label="Supprimer"><app-icon name="x" [size]="15" /></button>
                </span>
              }

              @if (configId() === z.id) {
                <div class="config">
                  <span class="config-label">Métriques portées par cette zone :</span>
                  <p class="config-hint field-hint">Ajoute les métriques pertinentes (allure, vitesse, FC, % FC max, puissance). Chacune s'ancre à une valeur de référence de l'athlète (LT1, LT2, VC, VMA, allures VDOT…) dans la règle ci-dessous, et se recalcule quand cette référence change. L'effort perçu (RPE) ne se règle pas ici : il se saisit sur le contenu de la séance.</p>
                  <div class="metric-toggles">
                    @for (m of metrics(); track m.id) {
                      <button type="button" class="toggle" [class.on]="z.metricTypeIds.includes(m.id)" (click)="toggleMetric(z, m)">{{ m.name }}</button>
                    }
                  </div>

                  <span class="config-label">Règle de calcul par métrique :</span>
                  @for (mid of z.metricTypeIds; track mid) {
                    @if (ruleFor(z, mid); as r) {
                      <!-- Une borne = un pourcentage + sa référence. Les deux bornes partagent la
                           même référence dans le cas courant ; la zone qui enjambe la frontière
                           LT1 → LT2 est la seule à en avoir deux différentes. -->
                      <div class="rule-row">
                        <span class="rule-metric">{{ metricName(mid) }}</span>

                        <span class="rule-edge">
                          <span class="edge-lb">de</span>
                          <input type="number" class="form-control mini" [ngModel]="r.lowPct"
                                 (ngModelChange)="saveRule(z, mid, { lowPct: $event })" placeholder="min" />
                          <span class="rule-unit">%</span>
                          <select class="form-control" [ngModel]="r.anchor"
                                  (ngModelChange)="saveRule(z, mid, { anchor: $event })" title="Référence de la borne basse">
                            <option [ngValue]="null">— référence —</option>
                            @for (a of anchors; track a) { <option [ngValue]="a">{{ anchorLabels[a] }}</option> }
                          </select>
                        </span>

                        <span class="rule-edge">
                          <span class="edge-lb">à</span>
                          <input type="number" class="form-control mini" [ngModel]="r.highPct"
                                 (ngModelChange)="saveRule(z, mid, { highPct: $event })" placeholder="max" />
                          <span class="rule-unit">%</span>
                          <select class="form-control" [ngModel]="r.highAnchor ?? r.anchor"
                                  (ngModelChange)="saveRule(z, mid, { highAnchor: $event })" title="Référence de la borne haute">
                            @for (a of anchors; track a) { <option [ngValue]="a">{{ anchorLabels[a] }}</option> }
                          </select>
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
    .sets { margin-bottom: var(--sp-4); }
    .sets-row { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
    .sets-lb { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); font-weight: 700; }
    .set-select { flex: 0 1 280px; }
    .sets .field-hint { margin: var(--sp-2) 0 0; }

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
      grid-template-columns: 44px 24px minmax(210px, 1.5fr) minmax(190px, 1.5fr) minmax(120px, 1.4fr) auto;
      align-items: center; gap: var(--sp-3);
      padding: var(--sp-3) var(--sp-4);
    }
    .zt-head { border-bottom: 1px solid var(--line); }
    .zt-head span { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); font-weight: 700; }
    .zt-order { text-align: center; }

    .zrow { border-bottom: 1px solid var(--hairline); background: var(--paper); position: relative; }
    .zrow:last-child { border-bottom: none; }
    /* « C'est cette ligne-là » : on arrive d'une fiche athlète pour régler une zone précise. Le
       halo s'efface — il désigne, il ne sélectionne pas. */
    .zrow--target { background: color-mix(in srgb, var(--dari-teal) 10%, var(--paper));
      box-shadow: inset 3px 0 0 var(--dari-teal); transition: background var(--duration) var(--ease); }
    .zrow.cdk-drag-preview { box-shadow: var(--shadow-lg); border-radius: var(--radius-md); }
    .zrow.cdk-drag-placeholder { opacity: 0.4; }
    .zt-order.metric { text-align: center; font-weight: 800; color: var(--ink-3); }

    .drag-handle { border: none; background: none; color: var(--ink-4); cursor: grab; display: flex; padding: 0; }
    .drag-handle:active { cursor: grabbing; }

    .zcell { min-width: 0; }
    .zcell--name { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
    .zcell--name.edit { flex-direction: row; align-items: center; gap: var(--sp-2); }
    .zn-top { display: flex; align-items: center; gap: var(--sp-2); min-width: 0; }
    .zn-meta { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
    .dot { width: 14px; height: 14px; border-radius: var(--radius-full); flex: none; }
    .zone-name { font-size: var(--text-md); overflow: hidden; text-overflow: ellipsis; }
    .badge.sport-all { opacity: 0.6; }
    .zcell--metrics { display: flex; gap: var(--sp-1); flex-wrap: wrap; align-content: center; }
    .metric-chip { font-size: var(--text-xs); font-weight: 700; background: var(--paper-sunk); color: var(--ink-2); padding: 0 var(--sp-2); border-radius: var(--radius-full); line-height: 1.6; }
    .zcell--desc { color: var(--ink-2); font-size: var(--text-sm); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .zcell--actions { display: flex; align-items: center; gap: var(--sp-1); justify-content: flex-end; }
    .btn.active { background: var(--paper-sunk); }
    .danger { color: var(--danger); }

    .config { grid-column: 1 / -1; border-top: 1px dashed var(--line); margin-top: var(--sp-2); padding-top: var(--sp-3); display: flex; flex-direction: column; gap: var(--sp-2); }
    .config-label { font-size: var(--text-sm); color: var(--ink-2); font-weight: 700; }
    .config-hint { margin: 0 0 var(--sp-1); }
    .metric-toggles { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .toggle { border: 1px solid var(--line); background: var(--paper); color: var(--ink-2); padding: var(--sp-1) var(--sp-3); border-radius: var(--radius-full); cursor: pointer; font-size: var(--text-sm); font-weight: 600; }
    .toggle.on { background: var(--dari-teal); border-color: var(--dari-teal); color: #fff; }

    .rule-row { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; }
    .rule-edge { display: inline-flex; align-items: center; gap: var(--sp-1); }
    .edge-lb { font-size: var(--text-xs); color: var(--ink-3); text-transform: uppercase; letter-spacing: .05em; font-weight: 700; }

    /* Règle lue en liste + alerte de chaîne rompue. */
    .zcell--rule { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .rule-read { font-size: var(--text-sm); color: var(--ink-2); white-space: nowrap; }
    /* Réglage en ligne des deux pourcentages. Champs courts et collés à leur unité : ce sont deux
       crans à tourner, pas un formulaire. */
    .rule-edit { display: inline-flex; align-items: center; gap: 4px; flex-wrap: wrap; }
    .rule-edit .mini { width: 56px; padding-inline: 6px; text-align: right; }
    .rule-ref { white-space: nowrap; }
    .rule-warn {
      display: inline-flex; align-items: center; gap: 3px;
      font-size: var(--text-xs); color: var(--warning); font-weight: 700; cursor: help;
    }
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
  private readonly setService = inject(ZoneSetService);
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

  // --- Modèles de zones (plusieurs échelles par club) -----------------------

  readonly sets = signal<ZoneSet[]>([]);
  readonly activeSetId = signal<string>('');
  readonly setBusy = signal(false);
  readonly activeSet = computed(() => this.sets().find((s) => s.id === this.activeSetId()) ?? null);

  /**
   * Zone à mettre en évidence à l'arrivée, et son modèle.
   *
   * <p>On y arrive depuis la fiche d'un athlète, avec l'intention de régler CETTE zone-là. Ouvrir
   * l'écran sur le jeu par défaut et laisser chercher dans seize lignes reviendrait à perdre en
   * route ce que le lien portait — d'autant que l'athlète peut travailler sur un autre modèle,
   * où la zone visée n'apparaîtrait même pas.</p>
   */
  readonly zone = input<string | undefined>();
  readonly set = input<string | undefined>();

  /** Ligne signalée à l'arrivée ; le halo s'efface, la sélection ne doit pas devenir un état. */
  readonly highlighted = signal<string | null>(null);

  ngOnInit(): void {
    forkJoin({
      sets: this.setService.list(),
      metrics: this.metricService.list(),
    }).subscribe({
      next: ({ sets, metrics }) => {
        this.metrics.set(metrics);
        this.sets.set(sets);
        // Le modèle demandé prime : c'est celui de l'athlète d'où l'on vient.
        const wanted = this.set() && sets.some((s) => s.id === this.set()) ? this.set()! : null;
        this.activeSetId.set(wanted ?? (sets.find((s) => s.isDefault) ?? sets[0])?.id ?? '');
        this.loadZones();
      },
      error: () => this.loading.set(false),
    });
  }

  private loadZones(): void {
    this.loading.set(true);
    this.zoneService.list({ setId: this.activeSetId() }).subscribe({
      next: (zones) => { this.zones.set(zones); this.loading.set(false); this.revealRequestedZone(); },
      error: () => this.loading.set(false),
    });
  }

  /**
   * Amène sous les yeux la zone qu'on venait régler, et la signale brièvement.
   *
   * <p>Deux trames : la première laisse Angular poser les lignes, la seconde laisse le navigateur
   * les disposer — défiler avant, c'est défiler vers un élément sans hauteur. Le halo s'efface
   * ensuite : il dit « c'est ici », il ne sélectionne rien.</p>
   */
  private revealRequestedZone(): void {
    const id = this.zone();
    if (!id || !this.zones().some((z) => z.id === id)) return;
    this.highlighted.set(id);
    requestAnimationFrame(() => requestAnimationFrame(() => {
      document.querySelector(`[data-zone="${id}"]`)?.scrollIntoView({ block: 'center' });
    }));
    setTimeout(() => this.highlighted.set(null), 2600);
  }

  onSetChange(id: string): void {
    this.activeSetId.set(id);
    this.editingId.set(null);
    this.configId.set(null);
    this.scaleTab.set(null);
    this.loadZones();
  }

  /**
   * Duplique le modèle courant : on part presque toujours d'une échelle existante pour en faire
   * une variante (trail, débutants…), pas d'une page blanche.
   */
  duplicateSet(): void {
    const source = this.activeSet();
    const name = window.prompt('Nom du nouveau modèle de zones', `${source?.name ?? 'Zones'} (copie)`)?.trim();
    if (!name || this.setBusy()) return;
    this.setBusy.set(true);
    this.setService.create({ name, copyFromSetId: source?.id ?? null }).subscribe({
      next: (created) => {
        this.sets.update((list) => [...list, created]);
        this.setBusy.set(false);
        this.onSetChange(created.id);
        this.toast.success(`Modèle « ${created.name} » créé — ajuste ses zones.`);
      },
      error: () => { this.setBusy.set(false); this.toast.error('Création impossible.'); },
    });
  }

  renameSet(): void {
    const set = this.activeSet();
    if (!set) return;
    const name = window.prompt('Renommer le modèle de zones', set.name)?.trim();
    if (!name || name === set.name) return;
    this.setService.rename(set.id, name).subscribe({
      next: (updated) => {
        this.sets.update((list) => list.map((s) => (s.id === updated.id ? updated : s)));
        this.toast.success('Modèle renommé.');
      },
      error: () => this.toast.error('Renommage impossible.'),
    });
  }

  async deleteSet(): Promise<void> {
    const set = this.activeSet();
    if (!set || set.isDefault) return;
    const ok = await this.confirm.ask({
      title: 'Supprimer le modèle de zones',
      message: `Supprimer « ${set.name} » et ses ${set.zoneCount} zones ? `
        + 'Les athlètes concernés reviennent au modèle par défaut.',
      confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.setService.delete(set.id).subscribe({
      next: () => {
        this.sets.update((list) => list.filter((s) => s.id !== set.id));
        this.onSetChange((this.sets().find((s) => s.isDefault) ?? this.sets()[0])?.id ?? '');
        this.toast.info('Modèle supprimé.');
      },
      error: () => this.toast.error('Suppression impossible.'),
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
    this.zoneService.create({
      name, color: this.draft.color, description: this.draft.description || null,
      // La zone naît dans le modèle affiché, pas dans le jeu par défaut.
      zoneSetId: this.activeSetId() || null,
    }).subscribe((z) => {
      this.zones.update((list) => [...list, z]);
      this.sets.update((list) => list.map((s) => (s.id === this.activeSetId() ? { ...s, zoneCount: s.zoneCount + 1 } : s)));
      this.draft = { name: '', color: '#22c55e', description: '' };
      // Une zone neuve n'a ni métrique ni règle : sans enchaîner sur sa configuration, elle
      // reste vide et le coach ne comprend pas pourquoi elle ne calcule rien.
      this.configId.set(z.id);
      this.toast.success('Zone ajoutée — choisis ses métriques et sa règle de calcul.');
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
      this.sets.update((list) => list.map((s) => (s.id === this.activeSetId() ? { ...s, zoneCount: Math.max(0, s.zoneCount - 1) } : s)));
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
    'LT1', 'LT2', 'VC', 'VMA', 'PACE_800M', 'PACE_1500M', 'PACE_3000M', 'PACE_5KM',
    'PACE_10KM', 'PACE_15KM', 'PACE_SEMI', 'PACE_MARATHON', 'FCMAX', 'LTHR', 'HRR',
  ];
  readonly models: ZoneModel[] = ['VMA', 'VC', 'DANIELS_VDOT', 'LACTATE_THRESHOLD', 'PCT_FCMAX', 'CUSTOM'];
  readonly anchorLabels = ZONE_ANCHOR_LABELS;
  readonly modelLabels = ZONE_MODEL_LABELS;

  /** Règle courante d'un couple (zone, métrique), ou une règle vide par défaut. */
  ruleFor(z: TrainingZone, metricId: string): ZoneRule {
    return z.rules?.find((r) => r.metricTypeId === metricId)
      ?? { metricTypeId: metricId, anchor: null, highAnchor: null, lowPct: null, highPct: null, model: 'CUSTOM' };
  }

  // --- Lecture de l'échelle d'allure ---------------------------------------

  /** Id de la métrique Allure : c'est elle qui porte l'échelle contiguë. */
  private readonly paceMetricId = computed(() => this.metrics().find((m) => m.code === 'PACE')?.id ?? null);

  /** Zones portant l'allure, dans l'ordre — la chaîne du footing facile au 800 m. */
  private readonly paceChain = computed(() => {
    const pid = this.paceMetricId();
    if (!pid) return [];
    return this.zones()
      .filter((z) => z.metricTypeIds.includes(pid))
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder);
  });

  private paceRule(z: TrainingZone): ZoneRule | null {
    const pid = this.paceMetricId();
    if (!pid) return null;
    const r = z.rules?.find((x) => x.metricTypeId === pid);
    return r && r.anchor && r.lowPct != null && r.highPct != null ? r : null;
  }

  /** Vrai si les deux bornes de la règle s'ancrent sur un seuil lactique (LT1 / LT2). */
  private isThresholdAnchored(r: ZoneRule): boolean {
    const refs: (ZoneAnchor | null)[] = [r.anchor, r.highAnchor ?? r.anchor];
    return refs.every((a) => a === 'LT1' || a === 'LT2');
  }

  /** Libellé court d'une ancre : « LT1 » plutôt que « Seuil aérobie (LT1) ». */
  private shortAnchor(a: ZoneAnchor | null): string {
    if (!a) return '—';
    const label = ZONE_ANCHOR_LABELS[a];
    const paren = /\(([^)]+)\)/.exec(label);
    return paren ? paren[1] : label;
  }

  /**
   * Règle d'allure en une ligne. Deux formes : « 90 – 100 % LT1 » quand les deux bornes partagent
   * leur référence, « 100 % LT1 → 93 % LT2 » pour la zone qui enjambe la frontière.
   */
  paceRuleLabel(z: TrainingZone): string | null {
    const r = this.paceRule(z);
    if (!r) return null;
    const lowRef = this.shortAnchor(r.anchor);
    const highRef = this.shortAnchor(r.highAnchor ?? r.anchor);
    return lowRef === highRef
      ? `${r.lowPct} – ${r.highPct} % ${lowRef}`
      : `${r.lowPct} % ${lowRef} → ${r.highPct} % ${highRef}`;
  }

  /**
   * Les deux pourcentages de la règle d'allure, avec de quoi les étiqueter.
   *
   * <p>{@code refLabel} rappelle la référence — « LT1 », ou « LT1 → LT2 » pour la zone qui enjambe
   * la frontière : sans elle, deux nombres nus ne diraient pas de quoi ils sont le pourcentage.</p>
   */
  paceBounds(z: TrainingZone): { low: number; high: number; lowRef: string; highRef: string; refLabel: string } | null {
    const r = this.paceRule(z);
    if (!r || r.lowPct == null || r.highPct == null || !r.anchor) return null;
    const lowRef = this.shortAnchor(r.anchor);
    const highRef = this.shortAnchor(r.highAnchor ?? r.anchor);
    return {
      low: r.lowPct, high: r.highPct, lowRef, highRef,
      refLabel: lowRef === highRef ? lowRef : `${lowRef} → ${highRef}`,
    };
  }

  /**
   * Change un pourcentage depuis la ligne de la zone.
   *
   * <p>Appliqué à <b>toutes les métriques de la zone qui partagent la même référence</b>, et à
   * elles seules. Une zone est une définition physiologique unique exprimée en plusieurs unités :
   * l'allure et la vitesse d'un même seuil valent le même pourcentage de la même ancre, et n'en
   * corriger qu'une les ferait diverger sans que rien ne le signale. La fréquence cardiaque, elle,
   * s'ancre ailleurs — sur la FC max — avec ses propres pourcentages : lui appliquer ceux de
   * l'allure serait faux.</p>
   */
  savePct(z: TrainingZone, edge: 'low' | 'high', raw: string): void {
    const value = Number(String(raw).replace(',', '.'));
    if (!Number.isFinite(value) || value < 0) { this.toast.error('Pourcentage invalide.'); return; }

    const bounds = this.paceBounds(z);
    if (!bounds) return;
    const low = edge === 'low' ? value : bounds.low;
    const high = edge === 'high' ? value : bounds.high;

    this.zoneService.setPercentages(z, low, high).subscribe({
      next: (updated) => {
        this.zones.update((list) => list.map((x) => (x.id === z.id ? updated : x)));
        this.toast.success('Zone « ' + z.name + ' » mise à jour.');
      },
      error: () => { this.toast.error('Mise à jour impossible.'); this.loadZones(); },
    });
  }

  /**
   * Rupture de chaîne avec la zone précédente. La chaîne est intacte si la borne haute d'une zone
   * est <b>exactement</b> la borne basse de la suivante (même référence, même pourcentage) — un
   * critère indépendant de l'athlète, là où comparer deux allures dépendrait de son rapport LT1/LT2.
   */
  gapWith(z: TrainingZone): { label: string; detail: string } | null {
    const chain = this.paceChain();
    const i = chain.findIndex((x) => x.id === z.id);
    if (i <= 0) return null;
    const prev = this.paceRule(chain[i - 1]);
    const cur = this.paceRule(z);
    if (!prev || !cur) return null;
    // La chaîne ne concerne que les zones ancrées sur les seuils. Une zone ancrée sur une allure
    // de compétition suit les records de l'athlète : elle est hors chaîne à dessein, pas en erreur.
    if (!this.isThresholdAnchored(prev) || !this.isThresholdAnchored(cur)) return null;
    const prevRef = prev.highAnchor ?? prev.anchor;
    if (prevRef === cur.anchor && prev.highPct === cur.lowPct) return null;
    return {
      label: 'chaîne rompue',
      detail: `« ${chain[i - 1].name} » s'arrête à ${prev.highPct} % ${this.shortAnchor(prevRef)}`
        + ` mais « ${z.name} » démarre à ${cur.lowPct} % ${this.shortAnchor(cur.anchor)} :`
        + ' selon l\'athlète, il restera un trou ou un chevauchement entre les deux.',
    };
  }

  /** Enregistre une modification partielle de règle (fusionnée avec l'existante). */
  saveRule(z: TrainingZone, metricId: string, patch: Partial<ZoneRuleRequest>): void {
    const cur = this.ruleFor(z, metricId);
    const body: ZoneRuleRequest = {
      anchor: patch.anchor !== undefined ? patch.anchor : cur.anchor,
      highAnchor: patch.highAnchor !== undefined ? patch.highAnchor : cur.highAnchor,
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
