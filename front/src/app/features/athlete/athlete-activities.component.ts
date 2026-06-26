import {
  ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit,
  inject, signal, viewChild,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import * as L from 'leaflet';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import {
  ACTIVITY_STATUS_BADGE, ACTIVITY_STATUS_LABELS, Activity,
} from '../../core/models/activity.model';

/**
 * Mes activités réalisées (athlète, lecture seule) — liste + tracé GPS (Leaflet).
 * Câblé sur /me/activities et /me/activities/{id}/route. CDC §10 « Mes sorties ».
 */
@Component({
  selector: 'app-athlete-activities',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, FormsModule, RouterLink, IconComponent],
  template: `
    <div class="acts">
      <header class="acts-top">
        <a routerLink="/athlete/progress" class="btn btn-ghost btn-sm">← Progrès</a>
        <h1 class="display-sm">Mes activités</h1>
        <p class="subtitle">Tes sorties réelles (importées ou ajoutées).</p>
      </header>

      <div class="acts-actions">
        <button type="button" class="btn btn-primary btn-sm" (click)="toggleLog()">
          <app-icon name="pencil" [size]="15" /> {{ showLog() ? 'Fermer' : 'Ajouter une sortie' }}
        </button>
        <button type="button" class="btn btn-ghost btn-sm" (click)="file.click()" [disabled]="fileBusy()">
          <app-icon name="download" [size]="15" /> {{ fileBusy() ? 'Import…' : 'Importer un GPX' }}
        </button>
        <input #file type="file" accept=".gpx,.tcx" hidden (change)="onFile($event)" />
      </div>

      @if (showLog()) {
        <form class="card log-form" (ngSubmit)="submitLog()">
          <div class="lf-row">
            <label>Date<input type="date" class="form-control" [(ngModel)]="draft.activityDate" name="d" required /></label>
            <label>Titre<input class="form-control" [(ngModel)]="draft.title" name="t" placeholder="Sortie libre" /></label>
          </div>
          <div class="lf-row">
            <label>Distance (km)<input type="number" min="0" step="0.1" class="form-control" [(ngModel)]="draft.km" name="km" /></label>
            <label>Durée (min)<input type="number" min="0" class="form-control" [(ngModel)]="draft.min" name="mn" /></label>
            <label>D+ (m)<input type="number" min="0" class="form-control" [(ngModel)]="draft.dplus" name="dp" /></label>
          </div>
          <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy()">
            {{ busy() ? 'Enregistrement…' : 'Enregistrer la sortie' }}
          </button>
        </form>
      }

      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 80px;"></div></div>
      } @else if (activities().length === 0) {
        <div class="card empty">
          <h2>Aucune activité</h2>
          <p class="field-hint">Ajoute une sortie, importe un GPX, ou connecte Strava/Garmin (via ton coach) — tes sorties apparaîtront ici.</p>
        </div>
      } @else {
        @for (a of activities(); track a.id) {
          <article class="row card">
            <button type="button" class="row-hd" (click)="toggle(a)" [attr.aria-expanded]="selected() === a.id">
              <div class="row-id">
                <strong>{{ a.title || 'Sortie' }}</strong>
                <span class="field-hint">{{ a.activityDate | date: 'EEE d MMM yyyy' }}</span>
              </div>
              <span class="badge" [class]="statusBadge(a.status)">{{ statusLabel(a.status) }}</span>
            </button>
            <div class="row-kpis">
              @if (a.distanceM != null) {
                <span><app-icon name="footprints" [size]="14" /> {{ (a.distanceM / 1000) | number: '1.0-2' }} km</span>
              }
              @if (a.durationS != null) {
                <span><app-icon name="timer" [size]="14" /> {{ fmtDuration(a.durationS) }}</span>
              }
              @if (a.elevationGainM != null) {
                <span><app-icon name="mountain" [size]="14" /> {{ a.elevationGainM }} m D+</span>
              }
              @if (a.avgHr != null) {
                <span><app-icon name="heart-pulse" [size]="14" /> {{ a.avgHr }} bpm</span>
              }
            </div>
            @if (selected() === a.id) {
              @if (routeEmpty()) {
                <p class="field-hint map-empty">Pas de tracé GPS pour cette sortie.</p>
              } @else {
                <div class="map" #map></div>
              }
            }
          </article>
        }
      }
    </div>
  `,
  styles: [`
    .acts { max-width: 560px; margin-inline: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .acts-top { display: flex; flex-direction: column; gap: var(--sp-1); align-items: flex-start; }
    .acts-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: 0; }
    .empty { text-align: center; }
    .acts-actions { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .log-form { display: flex; flex-direction: column; gap: var(--sp-3); }
    .lf-row { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .lf-row label { flex: 1; min-width: 110px; display: flex; flex-direction: column; gap: 4px; font-size: var(--text-sm); color: var(--ink-2); font-weight: 600; }

    .row { padding: 0; overflow: hidden; }
    .row-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); width: 100%; padding: var(--sp-3) var(--sp-4); background: transparent; border: none; cursor: pointer; text-align: left; }
    .row-id { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .row-id strong { color: var(--ink); }
    .row-kpis { display: flex; flex-wrap: wrap; gap: var(--sp-3); padding: 0 var(--sp-4) var(--sp-3); color: var(--ink-2); font-size: var(--text-sm); }
    .row-kpis span { display: inline-flex; align-items: center; gap: 4px; font-variant-numeric: tabular-nums; }
    .map { height: 260px; width: 100%; }
    .map-empty { padding: 0 var(--sp-4) var(--sp-4); }
  `],
})
export class AthleteActivitiesComponent implements OnInit, OnDestroy {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  readonly mapEl = viewChild<ElementRef<HTMLDivElement>>('map');

  readonly loading = signal(true);
  readonly activities = signal<Activity[]>([]);
  readonly selected = signal<string | null>(null);
  readonly routeEmpty = signal(false);
  private map?: L.Map;

  // Saisie / import par l'athlète
  readonly showLog = signal(false);
  readonly busy = signal(false);
  readonly fileBusy = signal(false);
  draft: { activityDate: string; title: string; km: number | null; min: number | null; dplus: number | null } = {
    activityDate: new Date().toISOString().slice(0, 10), title: '', km: null, min: null, dplus: null,
  };

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.portal.activities().subscribe({
      next: (a) => { this.activities.set(a); this.loading.set(false); },
      error: () => { this.activities.set([]); this.loading.set(false); },
    });
  }

  toggleLog(): void {
    this.showLog.update((v) => !v);
  }

  submitLog(): void {
    if (!this.draft.activityDate || this.busy()) { return; }
    this.busy.set(true);
    this.portal.logActivity({
      activityDate: this.draft.activityDate,
      title: this.draft.title?.trim() || 'Sortie libre',
      distanceM: this.draft.km != null ? Math.round(this.draft.km * 1000) : null,
      durationS: this.draft.min != null ? Math.round(this.draft.min * 60) : null,
      elevationGainM: this.draft.dplus,
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.showLog.set(false);
        this.draft = { activityDate: new Date().toISOString().slice(0, 10), title: '', km: null, min: null, dplus: null };
        this.toast.success('Sortie ajoutée');
        this.load();
      },
      error: () => { this.busy.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) { return; }
    this.fileBusy.set(true);
    this.portal.importActivityFile(file).subscribe({
      next: () => {
        this.fileBusy.set(false);
        input.value = '';
        this.toast.success('Trace importée');
        this.load();
      },
      error: () => { this.fileBusy.set(false); input.value = ''; this.toast.error('Import impossible (fichier GPX/TCX attendu).'); },
    });
  }

  toggle(a: Activity): void {
    this.destroyMap();
    if (this.selected() === a.id) { this.selected.set(null); return; }
    this.selected.set(a.id);
    this.routeEmpty.set(false);
    this.portal.activityRoute(a.id).subscribe({
      next: (pts) => {
        if (!pts || pts.length === 0) { this.routeEmpty.set(true); return; }
        setTimeout(() => this.draw(pts.map((p) => [p[0], p[1]] as L.LatLngTuple)));
      },
      error: () => this.routeEmpty.set(true),
    });
  }

  private draw(latlngs: L.LatLngTuple[]): void {
    const el = this.mapEl()?.nativeElement;
    if (!el) return;
    this.map = L.map(el);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap', maxZoom: 18,
    }).addTo(this.map);
    const line = L.polyline(latlngs, { color: '#0e6e78', weight: 4 }).addTo(this.map);
    L.circleMarker(latlngs[0], { color: '#0e9e74', radius: 7 }).addTo(this.map);
    L.circleMarker(latlngs[latlngs.length - 1], { color: '#e25e3a', radius: 7 }).addTo(this.map);
    this.map.fitBounds(line.getBounds(), { padding: [24, 24] });
  }

  private destroyMap(): void { this.map?.remove(); this.map = undefined; }
  ngOnDestroy(): void { this.destroyMap(); }

  fmtDuration(s: number): string {
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    return h > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${m} min`;
  }

  statusLabel(s: Activity['status']): string { return ACTIVITY_STATUS_LABELS[s]; }
  statusBadge(s: Activity['status']): string { return ACTIVITY_STATUS_BADGE[s]; }
}
