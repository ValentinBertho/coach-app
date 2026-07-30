import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { MetricType } from '../../core/models/metric-type.model';
import { TrainingZone, ZONE_ANCHOR_LABELS } from '../../core/models/training-zone.model';
import { AthleteZoneValue } from '../../core/models/athlete-zone-value.model';
import { PhysioProfile } from '../../core/models/physio.model';
import { MetricTypeService } from '../../core/services/metric-type.service';
import { TrainingZoneService } from '../../core/services/training-zone.service';
import { AthleteZoneValueService } from '../../core/services/athlete-zone-value.service';
import { PhysioService } from '../../core/services/physio.service';
import { ToastService } from '../../core/services/toast.service';

interface EditState {
  key: string;
  min: string;
  max: string;
}

/**
 * Écran « Zones & métriques » de la fiche athlète (chantier Z2). La fiche athlète porte les valeurs
 * (façon Nolio) : pré-remplies AUTO depuis le moteur physio, ajustables/verrouillables par le coach.
 */
@Component({
  selector: 'app-athlete-zones',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, FormsModule, RouterLink],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Zones de l'athlète</h1>
        <p class="subtitle">
          Valeurs pré-remplies depuis le profil (allure/FC). Ajustez et verrouillez les cibles ;
          le resync respecte vos valeurs manuelles et verrouillées.
        </p>
      </div>
      <button type="button" class="btn btn-accent btn-sm" (click)="resync()" [disabled]="busy()">
        <app-icon name="refresh-cw" [size]="16" /> Resynchroniser depuis le profil
      </button>
    </section>

    @if (loading()) {
      <div class="card"><div class="skeleton" style="height: 200px;"></div></div>
    } @else if (zones().length === 0) {
      <div class="card empty-state">
        <h2>Aucune zone</h2>
        <p class="field-hint">Définissez d'abord vos zones dans <a routerLink="/app/training-zones">Zones &amp; métriques</a> (club).</p>
      </div>
    } @else {
      <!-- Références (ancres) : pilotent le calcul des zones. -->
      @if (references().length) {
        <div class="card refs">
          <span class="refs-lb">Références</span>
          @for (r of references(); track r.label) {
            <span class="ref-chip"><span class="ref-k">{{ r.label }}</span><span class="ref-v metric">{{ r.value }}</span></span>
          }
          <a class="refs-edit" [routerLink]="['/app/athletes', athleteId(), 'tests']">Modifier →</a>
        </div>
      }

      <!-- Échelles par métrique (façon Nolio) : chaque onglet isole l'échelle d'une métrique. -->
      <div class="scale-tabs" role="tablist">
        <button type="button" class="scale-tab" [class.active]="scaleTab() === null" (click)="scaleTab.set(null)">Toutes</button>
        @for (m of columns(); track m.id) {
          <button type="button" class="scale-tab" [class.active]="scaleTab() === m.id" (click)="scaleTab.set(m.id)">{{ m.name }}</button>
        }
      </div>

      <!-- Échelle contiguë (façon Nolio) : bandes accolées de la plus lente à la plus rapide. -->
      @if (scaleTab() && scaleStripCells().length) {
        <div class="card scale-strip">
          @for (c of scaleStripCells(); track c.zone.id) {
            <div class="ss-seg" [style.background]="c.zone.color || 'var(--ink-3)'" [title]="c.zone.name + ' · ' + c.label">
              <span class="ss-name">{{ c.zone.name }}</span>
              <span class="ss-val metric">{{ c.label }}</span>
            </div>
          }
        </div>
      }

      <div class="card legend">
        <span><app-icon name="refresh-cw" [size]="14" /> auto</span>
        <span><app-icon name="pencil" [size]="14" /> manuel</span>
        <span><app-icon name="lock" [size]="14" /> verrouillé</span>
        <span class="legend-hint">Survolez une cible pour voir sa règle.</span>
      </div>

      <div class="card table-wrap">
        <table class="zv">
          <thead>
            <tr>
              <th class="zone-col">Zone</th>
              @for (m of displayedColumns(); track m.id) { <th>{{ m.name }}</th> }
            </tr>
          </thead>
          <tbody>
            @for (z of displayedZones(); track z.id) {
              <tr>
                <th class="zone-col">
                  <span class="dot" [style.background]="z.color || 'var(--ink-3)'"></span> {{ z.name }}
                </th>
                @for (m of displayedColumns(); track m.id) {
                  <td>
                    @if (!z.metricTypeIds.includes(m.id)) {
                      <span class="na">—</span>
                    } @else if (editing()?.key === cellKey(z.id, m.id)) {
                      <div class="edit">
                        <input class="form-control mini" [(ngModel)]="editing()!.min" [placeholder]="ph(m)" />
                        <span>–</span>
                        <input class="form-control mini" [(ngModel)]="editing()!.max" [placeholder]="ph(m)" />
                        <button type="button" class="btn btn-primary btn-sm" (click)="saveEdit(z, m)">OK</button>
                        <button type="button" class="btn btn-ghost btn-sm" (click)="editing.set(null)">✕</button>
                      </div>
                    } @else {
                      @if (value(z.id, m.id); as v) {
                        <button type="button" class="cell" (click)="startEdit(z, m, v)" [title]="cellTitle(z, m, v)">
                          <span class="val">{{ formatPair(m, v) }}</span>
                          <span class="marks">
                            <app-icon [name]="v.source === 'MANUAL' ? 'pencil' : 'refresh-cw'" [size]="13" />
                            @if (v.locked) { <app-icon name="lock" [size]="13" /> }
                          </span>
                        </button>
                        <button type="button" class="lock-btn" (click)="toggleLock(z, m, v)"
                                [class.on]="v.locked" [attr.aria-label]="v.locked ? 'Déverrouiller' : 'Verrouiller'">
                          <app-icon name="lock" [size]="13" />
                        </button>
                      } @else {
                        <button type="button" class="cell empty" (click)="startEdit(z, m, null)">à renseigner</button>
                      }
                    }
                  </td>
                }
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    .page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-3); flex-wrap: wrap; }

    .refs { display: flex; align-items: center; gap: var(--sp-3); flex-wrap: wrap; padding: var(--sp-3); margin-bottom: var(--sp-3); }
    .refs-lb { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); font-weight: 700; }
    .ref-chip { display: inline-flex; align-items: baseline; gap: var(--sp-1); padding: 2px var(--sp-2); background: var(--paper-sunk); border-radius: var(--radius-full); }
    .ref-k { font-size: var(--text-xs); color: var(--ink-3); }
    .ref-v { font-family: var(--font-data); font-weight: 700; font-size: var(--text-sm); }
    .refs-edit { margin-left: auto; font-size: var(--text-sm); color: var(--dari-teal); text-decoration: none; }
    .refs-edit:hover { text-decoration: underline; }

    .scale-tabs { display: flex; gap: var(--sp-1); margin-bottom: var(--sp-3); flex-wrap: wrap; }
    .scale-tab { border: 1px solid var(--line); background: var(--paper); color: var(--ink-2); padding: var(--sp-1) var(--sp-3); border-radius: var(--radius-full); cursor: pointer; font-size: var(--text-sm); font-weight: 700; }
    .scale-tab.active { background: var(--dari-teal); border-color: var(--dari-teal); color: #fff; }

    .scale-strip { display: flex; gap: 2px; padding: var(--sp-2); margin-bottom: var(--sp-3); overflow-x: auto; }
    .ss-seg { flex: 1 0 auto; min-width: 82px; display: flex; flex-direction: column; gap: 2px; padding: var(--sp-2); border-radius: var(--radius-sm); color: #fff; }
    .ss-name { font-size: var(--text-xs); font-weight: 700; text-shadow: 0 1px 2px rgba(0,0,0,0.35); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .ss-val { font-size: var(--text-xs); text-shadow: 0 1px 2px rgba(0,0,0,0.35); white-space: nowrap; }

    .legend { display: flex; align-items: center; gap: var(--sp-4); font-size: var(--text-sm); color: var(--ink-3); padding: var(--sp-2) var(--sp-3); margin-bottom: var(--sp-3); }
    .legend span { display: inline-flex; align-items: center; gap: var(--sp-1); }
    .legend-hint { margin-left: auto; font-style: italic; }

    .table-wrap { overflow-x: auto; padding: 0; }
    table.zv { border-collapse: collapse; width: 100%; min-width: 480px; }
    .zv th, .zv td { padding: var(--sp-2) var(--sp-3); text-align: left; border-bottom: 1px solid var(--line); vertical-align: middle; }
    .zv thead th { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); }
    .zone-col { font-weight: 700; white-space: nowrap; }
    .dot { display: inline-block; width: 12px; height: 12px; border-radius: var(--radius-full); vertical-align: -1px; }

    .cell { display: inline-flex; align-items: center; gap: var(--sp-2); background: none; border: none; cursor: pointer; color: var(--ink-1); font: inherit; padding: 2px 4px; border-radius: var(--radius-sm); }
    .cell:hover { background: var(--paper-sunk); }
    .val { font-family: var(--font-data); font-weight: 700; }
    .marks { display: inline-flex; gap: 2px; color: var(--ink-3); }
    .cell.empty { color: var(--ink-3); font-style: italic; }
    .na { color: var(--ink-3); }

    .lock-btn { background: none; border: none; cursor: pointer; color: var(--ink-3); padding: 2px; opacity: 0.4; }
    .lock-btn.on { color: var(--dari-teal); opacity: 1; }

    .edit { display: inline-flex; align-items: center; gap: var(--sp-1); }
    .mini { width: 68px; padding: 2px var(--sp-2); }
  `],
})
export class AthleteZonesComponent implements OnInit {
  readonly athleteId = input.required<string>();

  private readonly zoneService = inject(TrainingZoneService);
  private readonly metricService = inject(MetricTypeService);
  private readonly valueService = inject(AthleteZoneValueService);
  private readonly physio = inject(PhysioService);
  private readonly toast = inject(ToastService);

  readonly zones = signal<TrainingZone[]>([]);
  readonly metrics = signal<MetricType[]>([]);
  readonly values = signal<AthleteZoneValue[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly editing = signal<EditState | null>(null);

  private readonly metricMap = computed(() => {
    const map = new Map<string, MetricType>();
    for (const m of this.metrics()) map.set(m.id, m);
    return map;
  });

  private readonly valueMap = computed(() => {
    const map = new Map<string, AthleteZoneValue>();
    for (const v of this.values()) map.set(`${v.zoneId}:${v.metricTypeId}`, v);
    return map;
  });

  /** Colonnes = union ordonnée des métriques portées par au moins une zone. */
  readonly columns = computed<MetricType[]>(() => {
    const ids = new Set<string>();
    for (const z of this.zones()) for (const id of z.metricTypeIds) ids.add(id);
    return this.metrics().filter((m) => ids.has(m.id));
  });

  /** Onglet d'échelle actif : null = toutes les métriques, sinon l'id d'une métrique. */
  readonly scaleTab = signal<string | null>(null);

  /** Colonnes affichées selon l'onglet (toutes, ou la seule métrique sélectionnée). */
  readonly displayedColumns = computed<MetricType[]>(() => {
    const tab = this.scaleTab();
    return tab ? this.columns().filter((m) => m.id === tab) : this.columns();
  });

  /** Zones affichées : toutes, ou celles portant la métrique de l'onglet actif. */
  readonly displayedZones = computed<TrainingZone[]>(() => {
    const tab = this.scaleTab();
    return tab ? this.zones().filter((z) => z.metricTypeIds.includes(tab)) : this.zones();
  });

  /** Bandes de l'échelle contiguë (onglet métrique actif) : zone + valeur formatée de l'athlète. */
  readonly scaleStripCells = computed<{ zone: TrainingZone; label: string }[]>(() => {
    const tab = this.scaleTab();
    if (!tab) return [];
    const m = this.metricMap().get(tab);
    if (!m) return [];
    return this.displayedZones().map((z) => {
      const v = this.valueMap().get(`${z.id}:${tab}`);
      return { zone: z, label: v ? this.formatPair(m, v) : '—' };
    });
  });

  readonly physioProfile = signal<PhysioProfile | null>(null);

  /** Valeurs de référence (ancres) qui pilotent le calcul des zones. */
  readonly references = computed(() => {
    const p = this.physioProfile();
    if (!p) return [];
    const out: { label: string; value: string }[] = [];
    if (p.vdot != null) out.push({ label: 'VDOT', value: p.vdot.toFixed(1) });
    if (p.fcMax != null) out.push({ label: 'FC max', value: `${p.fcMax} bpm` });
    if (p.fcLt2 != null) out.push({ label: 'FC seuil', value: `${p.fcLt2} bpm` });
    if (p.lt2Kmh != null) out.push({ label: 'LT2', value: `${p.lt2Kmh.toFixed(1)} km/h` });
    if (p.vcKmh != null) out.push({ label: 'VC', value: `${p.vcKmh.toFixed(1)} km/h` });
    if (p.lt1Kmh != null) out.push({ label: 'LT1', value: `${p.lt1Kmh.toFixed(1)} km/h` });
    return out;
  });

  ngOnInit(): void {
    forkJoin({
      zones: this.zoneService.list(),
      metrics: this.metricService.list(),
      values: this.valueService.list(this.athleteId()),
    }).subscribe({
      next: ({ zones, metrics, values }) => {
        this.zones.set(zones);
        this.metrics.set(metrics);
        this.values.set(values);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.physio.profile(this.athleteId()).subscribe({
      next: (p) => this.physioProfile.set(p),
      error: () => this.physioProfile.set(null),
    });
  }

  /** Libellé de la règle d'un couple (zone, métrique) : « 95–102 % · Seuil lactique (LT2) ». */
  ruleLabel(z: TrainingZone, metricId: string): string | null {
    const r = z.rules?.find((x) => x.metricTypeId === metricId);
    if (!r || r.anchor == null || r.lowPct == null || r.highPct == null) return null;
    return `${r.lowPct}–${r.highPct} % · ${ZONE_ANCHOR_LABELS[r.anchor]}`;
  }

  /** Infobulle d'une cible : source + règle de calcul (traçabilité « d'où vient la cible »). */
  cellTitle(z: TrainingZone, m: MetricType, v: AthleteZoneValue): string {
    const src = v.source === 'MANUAL' ? 'Valeur manuelle' : 'Valeur auto';
    const rule = this.ruleLabel(z, m.id);
    return rule ? `${src} — ${rule}` : src;
  }

  cellKey(zoneId: string, metricId: string): string {
    return `${zoneId}:${metricId}`;
  }

  value(zoneId: string, metricId: string): AthleteZoneValue | undefined {
    return this.valueMap().get(this.cellKey(zoneId, metricId));
  }

  ph(m: MetricType): string {
    return m.unit === 'S_PER_KM' ? 'm:ss' : '0';
  }

  formatPair(m: MetricType, v: AthleteZoneValue): string {
    if (v.valueMin == null && v.valueMax == null) return '—';
    const lo = this.fmt(m, v.valueMin);
    const hi = this.fmt(m, v.valueMax);
    const body = lo === hi ? lo : `${lo} – ${hi}`;
    return `${body}${this.suffix(m)}`;
  }

  private fmt(m: MetricType, v: number | null): string {
    if (v == null) return '—';
    if (m.unit === 'S_PER_KM' || m.format === 'MMSS') return this.secToMmss(v);
    if (m.format === 'DEC1') return (Math.round(v * 10) / 10).toString().replace('.', ',');
    return Math.round(v).toString();
  }

  private suffix(m: MetricType): string {
    switch (m.unit) {
      case 'S_PER_KM': return '/km';
      case 'BPM': return ' bpm';
      case 'KMH': return ' km/h';
      case 'PCT': return ' %';
      case 'W': return ' W';
      default: return '';
    }
  }

  private secToMmss(sec: number): string {
    const s = Math.round(sec);
    const m = Math.floor(s / 60);
    const r = s % 60;
    return `${m}:${r.toString().padStart(2, '0')}`;
  }

  private parse(m: MetricType, raw: string): number | null {
    const t = raw.trim();
    if (!t) return null;
    if (m.unit === 'S_PER_KM' || m.format === 'MMSS') {
      const parts = t.split(':');
      if (parts.length === 2) return (+parts[0] || 0) * 60 + (+parts[1] || 0);
      return +t.replace(',', '.') || null;
    }
    return +t.replace(',', '.') || null;
  }

  startEdit(z: TrainingZone, m: MetricType, v: AthleteZoneValue | null): void {
    this.editing.set({
      key: this.cellKey(z.id, m.id),
      min: v?.valueMin != null ? this.fmt(m, v.valueMin) : '',
      max: v?.valueMax != null ? this.fmt(m, v.valueMax) : '',
    });
  }

  saveEdit(z: TrainingZone, m: MetricType): void {
    const e = this.editing();
    if (!e) return;
    const valueMin = this.parse(m, e.min);
    const valueMax = this.parse(m, e.max);
    this.valueService.upsert(this.athleteId(), z.id, m.id, { valueMin, valueMax }).subscribe((updated) => {
      this.upsertLocal(updated);
      this.editing.set(null);
      this.toast.success('Valeur enregistrée.');
    });
  }

  toggleLock(z: TrainingZone, m: MetricType, v: AthleteZoneValue): void {
    this.valueService.upsert(this.athleteId(), z.id, m.id, { locked: !v.locked }).subscribe((updated) => {
      this.upsertLocal(updated);
    });
  }

  resync(): void {
    this.busy.set(true);
    this.valueService.resync(this.athleteId()).subscribe({
      next: (values) => {
        this.values.set(values);
        this.busy.set(false);
        this.toast.success('Valeurs resynchronisées.');
      },
      error: () => this.busy.set(false),
    });
  }

  private upsertLocal(updated: AthleteZoneValue): void {
    const key = `${updated.zoneId}:${updated.metricTypeId}`;
    this.values.update((list) => {
      const idx = list.findIndex((x) => `${x.zoneId}:${x.metricTypeId}` === key);
      if (idx === -1) return [...list, updated];
      const copy = [...list];
      copy[idx] = updated;
      return copy;
    });
  }
}
