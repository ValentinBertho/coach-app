import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { MetricType } from '../../../core/models/metric-type.model';
import { AthleteZoneValue } from '../../../core/models/athlete-zone-value.model';
import { TrainingZone, ZoneAnchor, ZONE_ANCHOR_LABELS, referenceRule } from '../../../core/models/training-zone.model';
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
            <div class="zp-row" [class.sel]="h.zone.id === selectedId()">
              <button type="button" class="zp-opt" (click)="pick(h.zone.id)" role="option">
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
              <!-- Régler les % sans quitter la séance : c'est ici qu'on s'aperçoit qu'un seuil
                   est deux points trop haut, pas dans l'écran des zones. -->
              @if (bounds(h.zone)) {
                <button type="button" class="zp-tune" (click)="openTune(h.zone.id)"
                        [title]="'Régler les % de ' + h.zone.name" aria-label="Régler les pourcentages">
                  <app-icon name="settings" [size]="14" />
                </button>
              }
            </div>

            @if (tuneId() === h.zone.id) {
              @if (bounds(h.zone); as b) {
                <div class="zp-tune-box">
                  <div class="zp-tune-row">
                    <input type="number" class="form-control mini" [value]="b.low" min="0" max="200" #lo
                           aria-label="Pourcentage de la borne basse" />
                    <span class="zp-tune-dash">–</span>
                    <input type="number" class="form-control mini" [value]="b.high" min="0" max="200" #hi
                           aria-label="Pourcentage de la borne haute" />
                    <span class="zp-tune-unit">% {{ b.refLabel }}</span>
                  </div>
                  <!-- La portée doit être dite : l'échelle appartient au club. Un coach qui croit
                       ajuster SA séance déplacerait les allures de tous ses athlètes. -->
                  <p class="zp-tune-warn">
                    Vaut pour tout le club : les allures et FC de tous tes athlètes se recalculent.
                    Un ajustement verrouillé sur un athlète est conservé.
                  </p>
                  <div class="zp-tune-actions">
                    <button type="button" class="btn btn-ghost btn-sm" (click)="closeTune()">Annuler</button>
                    <button type="button" class="btn btn-primary btn-sm"
                            (click)="applyTune(h.zone.id, lo.value, hi.value)">Appliquer</button>
                  </div>
                </div>
              }
            }
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
    /* La ligne porte l'option et son réglage : l'engrenage ne doit pas déclencher la sélection. */
    .zp-row { display: flex; align-items: center; gap: 2px; }
    .zp-row.sel .zp-opt { background: color-mix(in srgb, var(--dari-teal) 12%, transparent); }
    .zp-row .zp-opt { flex: 1; min-width: 0; }
    .zp-tune { flex: none; display: inline-flex; align-items: center; justify-content: center;
      width: 30px; height: 30px; border: none; background: none; border-radius: var(--radius-sm);
      color: var(--ink-3); cursor: pointer; }
    .zp-tune:hover { background: var(--paper-sunk); color: var(--ink-1); }
    .zp-tune-box { padding: var(--sp-2) var(--sp-3) var(--sp-3); display: flex; flex-direction: column;
      gap: var(--sp-2); background: var(--paper-sunk); border-radius: var(--radius-sm); }
    .zp-tune-row { display: flex; align-items: center; gap: 6px; }
    .zp-tune-row .mini { width: 62px; text-align: right; }
    .zp-tune-dash, .zp-tune-unit { color: var(--ink-3); font-weight: 700; white-space: nowrap; }
    .zp-tune-warn { margin: 0; font-size: var(--text-2xs); color: var(--ink-3); }
    .zp-tune-actions { display: flex; justify-content: flex-end; gap: var(--sp-2); }
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

  /**
   * Nouveau réglage demandé pour une zone. Le sélecteur n'appelle rien lui-même : il présente, et
   * c'est l'écran qui possède les données qui écrit puis recharge.
   */
  readonly pctChange = output<{ zoneId: string; lowPct: number; highPct: number }>();

  readonly open = signal(false);
  /** Zone dont on règle les % ; une seule à la fois, sinon le menu devient un formulaire. */
  readonly tuneId = signal<string | null>(null);

  toggle(): void { this.open.update((v) => !v); }
  close(): void { this.open.set(false); this.tuneId.set(null); }
  pick(id: string): void { this.zoneChange.emit(id); this.close(); }

  openTune(zoneId: string): void { this.tuneId.update((v) => (v === zoneId ? null : zoneId)); }
  closeTune(): void { this.tuneId.set(null); }

  /** Les deux pourcentages de la règle de référence, avec l'ancre à rappeler. */
  bounds(zone: TrainingZone): { low: number; high: number; refLabel: string } | null {
    const r = referenceRule(zone);
    if (!r || r.lowPct == null || r.highPct == null || !r.anchor) return null;
    const low = this.shortAnchor(r.anchor);
    const high = this.shortAnchor(r.highAnchor ?? r.anchor);
    return { low: r.lowPct, high: r.highPct, refLabel: low === high ? low : `${low} → ${high}` };
  }

  applyTune(zoneId: string, lowRaw: string, highRaw: string): void {
    const lowPct = Number(String(lowRaw).replace(',', '.'));
    const highPct = Number(String(highRaw).replace(',', '.'));
    if (!Number.isFinite(lowPct) || !Number.isFinite(highPct) || lowPct < 0 || highPct < 0) return;
    this.pctChange.emit({ zoneId, lowPct, highPct });
    this.closeTune();
  }

  /** Libellé court d'une ancre : « LT2 » plutôt que « Seuil lactique (LT2) ». */
  private shortAnchor(a: ZoneAnchor): string {
    const label = ZONE_ANCHOR_LABELS[a];
    const paren = /\(([^)]+)\)/.exec(label);
    return paren ? paren[1] : label;
  }

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
