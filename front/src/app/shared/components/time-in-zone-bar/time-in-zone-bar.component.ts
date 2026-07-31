import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { TimeInZone } from '../../../core/models/activity.model';
import { ActivityService } from '../../../core/services/activity.service';
import { AthletePortalService } from '../../../core/services/athlete-portal.service';

/**
 * Barre « Zones d'entraînement » (temps-en-zone) d'une activité réalisée, façon Nolio : une barre
 * empilée par métrique (Allure, FC…), chaque segment coloré = part du temps passé dans une zone.
 * Charge la répartition à la demande depuis l'API. Cf. PROPOSITION-ZONES §3.7 / E7 (V2-7).
 */
@Component({
  selector: 'app-time-in-zone-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <div class="tiz-skel"></div>
    } @else if (data()?.scales?.length) {
      <div class="tiz">
        @for (s of data()!.scales; track s.metricCode) {
          <div class="tiz-scale">
            <div class="tiz-head">
              <span class="tiz-metric">{{ s.metricName }}</span>
              <span class="tiz-total">{{ fmtDuration(s.totalS) }}</span>
            </div>
            <div class="tiz-bar" role="img" [attr.aria-label]="'Répartition ' + s.metricName">
              @for (b of s.buckets; track b.zoneId) {
                <span class="tiz-seg" [style.width.%]="b.pct" [style.background]="b.color || 'var(--ink-3)'"
                      [title]="b.zoneName + ' · ' + b.pct + ' % · ' + fmtDuration(b.seconds)"></span>
              }
            </div>
            <div class="tiz-legend">
              @for (b of s.buckets; track b.zoneId) {
                <span class="tiz-key">
                  <span class="tiz-dot" [style.background]="b.color || 'var(--ink-3)'"></span>
                  {{ b.zoneName }} <span class="tiz-pct metric">{{ b.pct }} %</span>
                </span>
              }
            </div>
          </div>
        }
      </div>
    } @else {
      <p class="tiz-empty field-hint">Pas de flux FC/allure sur cette activité (saisie manuelle, ou montre sans capteur).</p>
    }
  `,
  styles: [`
    .tiz { display: flex; flex-direction: column; gap: var(--sp-4); }
    .tiz-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: var(--sp-1); }
    .tiz-metric { font-weight: 700; font-size: var(--text-sm); }
    .tiz-total { font-family: var(--font-data); font-size: var(--text-xs); color: var(--ink-3); }
    .tiz-bar { display: flex; height: 14px; border-radius: var(--radius-full); overflow: hidden; background: var(--paper-sunk); }
    .tiz-seg { height: 100%; min-width: 2px; }
    .tiz-legend { display: flex; flex-wrap: wrap; gap: var(--sp-1) var(--sp-3); margin-top: var(--sp-2); }
    .tiz-key { display: inline-flex; align-items: center; gap: var(--sp-1); font-size: var(--text-xs); color: var(--ink-2); }
    .tiz-dot { width: 10px; height: 10px; border-radius: var(--radius-full); }
    .tiz-pct { color: var(--ink-3); }
    .tiz-skel { height: 60px; border-radius: var(--radius-md); background: var(--paper-sunk); animation: tiz-pulse 1.2s ease-in-out infinite; }
    @keyframes tiz-pulse { 0%,100% { opacity: 0.5; } 50% { opacity: 0.9; } }
    .tiz-empty { margin: 0; }
  `],
})
export class TimeInZoneBarComponent {
  /**
   * Athlète ciblé (écrans coach). Laissé vide sur le portail athlète : la route `/me/` est alors
   * utilisée, le composant restant identique des deux côtés.
   */
  readonly athleteId = input<string | null>(null);
  readonly activityId = input.required<string>();

  private readonly activityService = inject(ActivityService);
  private readonly portal = inject(AthletePortalService);

  readonly loading = signal(true);
  readonly data = signal<TimeInZone | null>(null);

  constructor() {
    // Charge (et recharge) la répartition dès que l'activité ciblée change.
    effect(() => {
      const a = this.athleteId();
      const id = this.activityId();
      if (id) this.fetch(a, id);
    }, { allowSignalWrites: true });
  }

  private fetch(athleteId: string | null, activityId: string): void {
    this.loading.set(true);
    const request = athleteId
      ? this.activityService.timeInZone(athleteId, activityId)
      : this.portal.activityTimeInZone(activityId);
    request.subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => { this.data.set(null); this.loading.set(false); },
    });
  }

  fmtDuration(sec: number): string {
    const m = Math.round(sec / 60);
    if (m >= 60) return `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}`;
    return `${m} min`;
  }
}
