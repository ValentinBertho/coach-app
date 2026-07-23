import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { MetricType } from '../../core/models/metric-type.model';
import { TrainingZone } from '../../core/models/training-zone.model';
import { AthleteZoneValue } from '../../core/models/athlete-zone-value.model';
import { MetricTypeService } from '../../core/services/metric-type.service';
import { TrainingZoneService } from '../../core/services/training-zone.service';
import { AthleteZoneValueService } from '../../core/services/athlete-zone-value.service';
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
        <a [routerLink]="['/app/athletes', athleteId()]" class="back-link">
          <app-icon name="chevron-right" [size]="14" /> Retour à la fiche
        </a>
        <h1 class="display-sm">Zones &amp; métriques</h1>
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
      <div class="card legend">
        <span><app-icon name="refresh-cw" [size]="14" /> auto</span>
        <span><app-icon name="pencil" [size]="14" /> manuel</span>
        <span><app-icon name="lock" [size]="14" /> verrouillé</span>
      </div>

      <div class="card table-wrap">
        <table class="zv">
          <thead>
            <tr>
              <th class="zone-col">Zone</th>
              @for (m of columns(); track m.id) { <th>{{ m.name }}</th> }
            </tr>
          </thead>
          <tbody>
            @for (z of zones(); track z.id) {
              <tr>
                <th class="zone-col">
                  <span class="dot" [style.background]="z.color || 'var(--ink-3)'"></span> {{ z.name }}
                </th>
                @for (m of columns(); track m.id) {
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
                        <button type="button" class="cell" (click)="startEdit(z, m, v)" [title]="v.source === 'MANUAL' ? 'Valeur manuelle' : 'Valeur auto'">
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
    .back-link { display: inline-flex; align-items: center; gap: 2px; font-size: var(--text-sm); color: var(--ink-3); text-decoration: none; }
    .back-link app-icon { transform: rotate(180deg); }

    .legend { display: flex; gap: var(--sp-4); font-size: var(--text-sm); color: var(--ink-3); padding: var(--sp-2) var(--sp-3); margin-bottom: var(--sp-3); }
    .legend span { display: inline-flex; align-items: center; gap: var(--sp-1); }

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
