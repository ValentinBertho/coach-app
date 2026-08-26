import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { MetricType } from '../../../core/models/metric-type.model';
import { AthleteZoneValue } from '../../../core/models/athlete-zone-value.model';
import { TrainingZone, ZONE_ANCHOR_LABELS } from '../../../core/models/training-zone.model';
import { formatMetricRange } from '../../../core/utils/metric-format';

/** Cible pré-calculée d'une zone pour l'athlète courant (lue depuis ses valeurs). */
interface ZoneHint {
  zone: TrainingZone;
  rule: string | null;   // « 80–92 % » (règle allure)
  pace: string | null;   // « 5:00–5:25/km »
  hr: string | null;     // « 132–148 bpm »
}

/**
 * Sélecteur de zone « façon Nolio » : au lieu d'un menu déroulant nu, il montre pour chaque zone
 * sa <b>règle</b> (ancre + %) et la <b>cible concrète</b> de l'athlète (allure + FC), pré-lues depuis
 * ses valeurs de zone. Le coach voit ce qu'il prescrit au moment du choix → création rapide (E9,
 * PROPOSITION-ZONES-ET-EDITEUR-V2 §3.7). Réutilisé pour l'effort et pour la récupération.
 */
@Component({
  selector: 'app-zone-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="zp" [class.zp--open]="open()">
      <button type="button" class="zp-trigger" (click)="toggle()" [title]="selectedHint()?.zone?.name || 'Choisir une zone'">
        <span class="dot" [style.background]="selectedHint()?.zone?.color || 'var(--ink-3)'"></span>
        <span class="zp-name">{{ selectedHint()?.zone?.name || 'Zone…' }}</span>
        @if (!dense()) {
          @if (compact(selectedHint()); as t) {
            <span class="zp-target metric">{{ t }}</span>
          } @else if (selectedHint()?.rule) {
            <span class="zp-rule">{{ selectedHint()?.rule }}</span>
          }
        }
        <app-icon name="chevron-down" [size]="14" />
      </button>

      @if (open()) {
        <div class="zp-backdrop" (click)="close()"></div>
        <div class="zp-menu" role="listbox">
          @for (h of hints(); track h.zone.id) {
            <button type="button" class="zp-opt" [class.sel]="h.zone.id === selectedId()" (click)="pick(h.zone.id)" role="option">
              <span class="dot" [style.background]="h.zone.color || 'var(--ink-3)'"></span>
              <span class="zp-opt-name">{{ h.zone.name }}</span>
              @if (h.rule) { <span class="zp-rule">{{ h.rule }}</span> }
              <span class="zp-opt-target metric">
                @if (h.pace) { {{ h.pace }} }
                @if (h.pace && h.hr) { · }
                @if (h.hr) { {{ h.hr }} }
                @if (!h.pace && !h.hr) { <span class="zp-nada">—</span> }
              </span>
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .zp { position: relative; display: inline-flex; min-width: 0; }
    .zp-trigger { display: inline-flex; align-items: center; gap: var(--sp-2); background: var(--paper); border: 1px solid var(--line); border-radius: var(--radius-md); padding: var(--sp-1) var(--sp-2); cursor: pointer; color: var(--ink-1); font: inherit; min-height: 34px; max-width: 100%; }
    .zp-trigger:hover { border-color: var(--ink-4); }
    .zp--open .zp-trigger { border-color: var(--dari-teal); }
    .dot { width: 12px; height: 12px; border-radius: var(--radius-full); flex: none; }
    .zp-name { font-weight: 700; font-size: var(--text-sm); white-space: nowrap; }
    .zp-target { font-size: var(--text-xs); color: var(--ink-2); white-space: nowrap; }
    .zp-rule { font-size: var(--text-xs); color: var(--ink-3); font-weight: 700; white-space: nowrap; }
    .zp-trigger app-icon { color: var(--ink-3); margin-left: auto; }

    .zp-backdrop { position: fixed; inset: 0; z-index: 40; }
    .zp-menu { position: absolute; z-index: 41; top: calc(100% + 4px); left: 0; min-width: 260px; max-width: 340px; max-height: 320px; overflow-y: auto; background: var(--paper); border: 1px solid var(--line); border-radius: var(--radius-md); box-shadow: var(--shadow-lg); padding: var(--sp-1); }
    .zp-opt { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: var(--sp-2); width: 100%; background: none; border: none; cursor: pointer; padding: var(--sp-2); border-radius: var(--radius-sm); color: var(--ink-1); font: inherit; text-align: left; }
    .zp-opt:hover { background: var(--paper-sunk); }
    .zp-opt.sel { background: color-mix(in srgb, var(--dari-teal) 12%, transparent); }
    .zp-opt-name { font-weight: 700; font-size: var(--text-sm); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .zp-opt .zp-rule { grid-column: 2; justify-self: end; }
    .zp-opt-target { grid-column: 1 / -1; font-size: var(--text-xs); color: var(--ink-2); padding-left: 20px; }
    .zp-nada { color: var(--ink-4); }
  `],
})
export class ZonePickerComponent {
  readonly zones = input.required<TrainingZone[]>();
  readonly metrics = input.required<MetricType[]>();
  readonly values = input<AthleteZoneValue[]>([]);
  readonly selectedId = input<string | null | undefined>(null);
  /** Mode dense : le déclencheur n'affiche que la pastille + le nom (la cible est lue ailleurs). */
  readonly dense = input(false);
  /** Émis quand le coach choisit une zone (id). */
  readonly zoneChange = output<string>();

  readonly open = signal(false);

  toggle(): void { this.open.update((v) => !v); }
  close(): void { this.open.set(false); }
  pick(id: string): void { this.zoneChange.emit(id); this.close(); }

  private readonly metricByCode = computed(() => {
    const map = new Map<string, MetricType>();
    for (const m of this.metrics()) map.set(m.code, m);
    return map;
  });

  private readonly valueMap = computed(() => {
    const map = new Map<string, AthleteZoneValue>();
    for (const v of this.values()) map.set(`${v.zoneId}:${v.metricTypeId}`, v);
    return map;
  });

  /** Une entrée par zone, avec règle + cible concrète (allure/FC) de l'athlète. */
  readonly hints = computed<ZoneHint[]>(() => {
    const pace = this.metricByCode().get('PACE');
    const hr = this.metricByCode().get('HR');
    return this.zones().map((zone) => ({
      zone,
      rule: this.ruleLabel(zone, pace?.id),
      pace: pace ? this.targetLabel(zone.id, pace) : null,
      hr: hr ? this.targetLabel(zone.id, hr) : null,
    }));
  });

  readonly selectedHint = computed<ZoneHint | null>(() => {
    const id = this.selectedId();
    return id ? this.hints().find((h) => h.zone.id === id) ?? null : null;
  });

  compact(h: ZoneHint | null | undefined): string {
    if (!h) return '';
    return [h.pace, h.hr].filter(Boolean).join(' · ');
  }

  /** Règle allure d'une zone : « 80–92 % ». */
  private ruleLabel(zone: TrainingZone, paceMetricId: string | undefined): string | null {
    const r = zone.rules?.find((x) => x.metricTypeId === paceMetricId && x.lowPct != null && x.highPct != null)
      ?? zone.rules?.find((x) => x.lowPct != null && x.highPct != null);
    if (!r) return null;
    const anchor = r.anchor ? ` ${ZONE_ANCHOR_LABELS[r.anchor].replace(/ \(.*\)/, '')}` : '';
    return `${r.lowPct}–${r.highPct} %${anchor ? ' ·' + anchor : ''}`.trim();
  }

  /** Cible concrète d'une zone pour une métrique donnée, formatée selon son unité. */
  private targetLabel(zoneId: string, m: MetricType): string | null {
    const v = this.valueMap().get(`${zoneId}:${m.id}`);
    if (!v || (v.valueMin == null && v.valueMax == null)) return null;
    return formatMetricRange(m, v.valueMin, v.valueMax, '–');
  }

}
