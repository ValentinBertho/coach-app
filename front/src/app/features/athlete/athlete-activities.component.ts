import {
  ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit,
  computed, inject, signal, viewChild,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import * as L from 'leaflet';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SegmentedControlComponent } from '../../shared/components/ui';
import {
  ACTIVITY_STATUS_BADGE, ACTIVITY_STATUS_LABELS, Activity,
} from '../../core/models/activity.model';
import { formatPace, paceFrom } from '../../core/utils/pace';
import { ActivityLapsComponent } from '../../shared/components/activity-laps/activity-laps.component';
import { TimeInZoneBarComponent } from '../../shared/components/time-in-zone-bar/time-in-zone-bar.component';

/** Groupe mensuel, même découpage que « Mon historique » : un athlète relit son mois, pas son flux. */
interface MonthGroup {
  label: string;
  items: Activity[];
}

/**
 * Mes activités réalisées (athlète, lecture seule) — antéchronologiques et groupées par mois
 * (même lecture que « Mon historique »), avec tracé GPS, détail tour par tour et temps en zone.
 * Câblé sur /me/activities, /me/activities/{id}/route et /me/activities/{id}/laps. CDC §10.
 */
@Component({
  selector: 'app-athlete-activities',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, DecimalPipe, FormsModule, RouterLink, IconComponent,
    SegmentedControlComponent, ActivityLapsComponent, TimeInZoneBarComponent,
  ],
  template: `
    <div class="acts">
      <header class="acts-top">
        <a routerLink="/athlete/progress" class="btn btn-ghost btn-sm">← Progrès</a>
        <h1 class="display-sm">Mes activités</h1>
        <p class="subtitle">Tes sorties réelles (importées ou ajoutées).</p>
        <app-segmented-control [options]="rangeOptions" [value]="weeks()"
          (valueChange)="setWeeks($event)" ariaLabel="Période" />
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
          <p class="field-hint">
            Ajoute une sortie à la main, importe un fichier GPX, ou connecte ta montre :
            tes sorties remonteront ensuite toutes seules.
          </p>
          <a routerLink="/athlete/sync" class="btn btn-primary btn-sm">
            <app-icon name="zap" [size]="15" /> Connecter Strava
          </a>
        </div>
      } @else if (groups().length === 0) {
        <div class="card empty">
          <h2>Rien sur cette période</h2>
          <p class="field-hint">Élargis la période pour retrouver tes sorties plus anciennes.</p>
        </div>
      } @else {
        <!-- Même lecture que « Mon historique » : antéchronologique, groupé par mois, avec le
             rail de date à gauche. On relit son mois d'entraînement, pas un flux sans repères. -->
        @for (g of groups(); track g.label) {
          <section class="month">
            <h2 class="month-h">{{ g.label }}</h2>
            @for (a of g.items; track a.id) {
              <article class="row card">
                <button type="button" class="row-hd" (click)="toggle(a)" [attr.aria-expanded]="selected() === a.id">
                  <span class="row-date">
                    <span class="d metric">{{ a.activityDate | date: 'dd' }}</span>
                    <span class="m">{{ a.activityDate | date: 'MMM' }}</span>
                  </span>
                  <span class="row-id">
                    <strong>{{ a.title || 'Sortie' }}</strong>
                    <span class="field-hint">{{ a.activityDate | date: 'EEEE' }}</span>
                  </span>
                  <span class="badge" [class]="statusBadge(a.status)">{{ statusLabel(a.status) }}</span>
                  <app-icon class="row-caret" [name]="selected() === a.id ? 'chevron-up' : 'chevron-down'" [size]="18" />
                </button>
                <div class="row-kpis">
                  @if (a.distanceM != null) {
                    <span><app-icon name="footprints" [size]="14" /> {{ (a.distanceM / 1000) | number: '1.0-2' }} km</span>
                  }
                  @if (a.durationS != null) {
                    <span><app-icon name="timer" [size]="14" /> {{ fmtDuration(a.durationS) }}</span>
                  }
                  @if (pace(a); as p) {
                    <span><app-icon name="gauge" [size]="14" /> {{ p }} /km</span>
                  }
                  @if (a.elevationGainM != null) {
                    <span><app-icon name="mountain" [size]="14" /> {{ a.elevationGainM }} m D+</span>
                  }
                  @if (a.avgHr != null) {
                    <span><app-icon name="heart-pulse" [size]="14" /> {{ a.avgHr }} bpm</span>
                  }
                  @if (a.maxHr != null) {
                    <span><app-icon name="flame" [size]="14" /> {{ a.maxHr }} bpm max</span>
                  }
                  @if (a.avgCadence != null) {
                    <span><app-icon name="activity" [size]="14" /> {{ a.avgCadence }} ppm</span>
                  }
                  @if (a.avgPowerW != null) {
                    <span><app-icon name="zap" [size]="14" /> {{ a.avgPowerW }} W</span>
                  }
                </div>
                @if (selected() === a.id) {
                  @if (routeEmpty()) {
                    <p class="field-hint map-empty">Pas de tracé GPS pour cette sortie.</p>
                  } @else {
                    <div class="map" #map></div>
                  }
                  <!-- Le détail par tour : c'est là qu'une séance se décortique. Les moyennes
                       au-dessus ne distinguent pas un 10 × 400 d'un footing de même durée. -->
                  <div class="row-laps">
                    <app-activity-laps [activityId]="a.id" />
                  </div>
                  <!-- Temps en zone : le coach l'avait déjà, l'athlète le voit enfin sur ses propres
                       sorties (l'endpoint /me/ manquait, le composant existait). -->
                  <div class="row-zones">
                    <app-time-in-zone-bar [activityId]="a.id" />
                  </div>
                }
              </article>
            }
          </section>
        }
      }
    </div>
  `,
  styles: [`
    /* padding-top : safe-area de la coquille athlète (PWA) — sinon le titre passe sous l'heure. */
    .acts { max-width: 560px; margin-inline: auto; padding: var(--sp-4); padding-top: max(var(--sp-4), var(--safe-top, 0px)); display: flex; flex-direction: column; gap: var(--sp-3); }
    .acts-top { display: flex; flex-direction: column; gap: var(--sp-2); align-items: flex-start; }
    .acts-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: 0; }
    .empty { text-align: center; display: flex; flex-direction: column; align-items: center; gap: var(--sp-2); }
    .acts-actions { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .log-form { display: flex; flex-direction: column; gap: var(--sp-3); }
    .lf-row { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .lf-row label { flex: 1; min-width: 110px; display: flex; flex-direction: column; gap: 4px; font-size: var(--text-sm); color: var(--ink-2); font-weight: 600; }

    /* Mois : mêmes intitulés et même rythme que « Mon historique ». */
    .month { display: flex; flex-direction: column; gap: var(--sp-2); }
    .month-h { font-size: var(--text-md); color: var(--ink-3); text-transform: capitalize; margin: var(--sp-2) 0 0; }

    .row { padding: 0; overflow: hidden; }
    .row-hd { display: flex; align-items: center; gap: var(--sp-3); width: 100%; padding: var(--sp-3) var(--sp-4); background: transparent; border: none; cursor: pointer; text-align: left; }
    /* Rail de date repris de l'historique : le jour se lit d'un coup d'œil en descendant. */
    .row-date { display: flex; flex-direction: column; align-items: center; min-width: 34px; flex-shrink: 0; }
    .row-date .d { font-size: var(--text-xl); font-weight: 800; color: var(--ink); line-height: 1; }
    .row-date .m { font-size: var(--text-xs); color: var(--ink-3); text-transform: uppercase; }
    .row-id { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
    .row-id strong { color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .row-id .field-hint { text-transform: capitalize; }
    .row-caret { color: var(--ink-4); flex-shrink: 0; }
    .row-kpis { display: flex; flex-wrap: wrap; gap: var(--sp-3); padding: 0 var(--sp-4) var(--sp-3); color: var(--ink-2); font-size: var(--text-sm); }
    .row-kpis span { display: inline-flex; align-items: center; gap: 4px; font-variant-numeric: tabular-nums; }
    .map { height: 260px; width: 100%; }
    .map-empty { padding: 0 var(--sp-4) var(--sp-4); }
    .row-laps, .row-zones { padding: var(--sp-3) var(--sp-4); border-top: 1px solid var(--hairline); }
  `],
})
export class AthleteActivitiesComponent implements OnInit, OnDestroy {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly route = inject(ActivatedRoute);
  readonly mapEl = viewChild<ElementRef<HTMLDivElement>>('map');

  readonly loading = signal(true);
  readonly activities = signal<Activity[]>([]);
  readonly selected = signal<string | null>(null);
  readonly routeEmpty = signal(false);
  private map?: L.Map;

  /** Périodes de l'historique, à l'identique — plus « Tout », la liste n'étant pas bornée côté API. */
  readonly rangeOptions = [
    { label: '4 sem.', value: '4' },
    { label: '12 sem.', value: '12' },
    { label: '26 sem.', value: '26' },
    { label: 'Tout', value: 'all' },
  ];
  readonly weeks = signal('12');

  /**
   * Sorties de la période, de la plus récente à la plus ancienne, groupées par mois — le même
   * découpage que « Mon historique », pour que les deux écrans se lisent de la même façon.
   */
  readonly groups = computed<MonthGroup[]>(() => {
    const from = this.rangeStart();
    const items = this.activities()
      .filter((a) => !from || a.activityDate >= from)
      .slice()
      .sort((a, b) => b.activityDate.localeCompare(a.activityDate));

    const out: MonthGroup[] = [];
    const fmt = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' });
    for (const a of items) {
      const label = fmt.format(new Date(a.activityDate + 'T00:00:00'));
      let g = out.find((x) => x.label === label);
      if (!g) { g = { label, items: [] }; out.push(g); }
      g.items.push(a);
    }
    return out;
  });

  /** Première date affichée (ISO), ou `null` en « Tout ». */
  private rangeStart(): string | null {
    const weeks = Number(this.weeks());
    if (!Number.isFinite(weeks) || weeks <= 0) return null;
    const d = new Date();
    d.setDate(d.getDate() - weeks * 7);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

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
      next: (a) => { this.activities.set(a); this.loading.set(false); this.openRequested(); },
      error: () => { this.activities.set([]); this.loading.set(false); },
    });
  }

  /**
   * `?open=<id>` : l'agenda renvoie ici sur une sortie précise. Sans cette reprise, un athlète
   * qui tape une sortie réalisée dans son agenda atterrit en haut d'une liste et doit la
   * retrouver à la main — et la période affichée s'élargit si elle est plus ancienne.
   */
  private openRequested(): void {
    const id = this.route.snapshot.queryParamMap.get('open');
    if (!id) return;
    const target = this.activities().find((a) => a.id === id);
    if (!target) return;
    const from = this.rangeStart();
    if (from && target.activityDate < from) {
      this.weeks.set('all');
    }
    this.toggle(target);
  }

  /**
   * Changer de période peut faire disparaître la sortie ouverte : on referme le détail (et sa
   * carte Leaflet, qui survivrait autrement à la disparition de son élément hôte).
   */
  setWeeks(weeks: string): void {
    this.destroyMap();
    this.selected.set(null);
    this.weeks.set(weeks);
  }

  toggleLog(): void {
    this.showLog.update((v) => !v);
  }

  submitLog(confirmDuplicate = false): void {
    if (!this.draft.activityDate || this.busy()) { return; }
    this.busy.set(true);
    this.portal.logActivity({
      activityDate: this.draft.activityDate,
      title: this.draft.title?.trim() || 'Sortie libre',
      distanceM: this.draft.km != null ? Math.round(this.draft.km * 1000) : null,
      durationS: this.draft.min != null ? Math.round(this.draft.min * 60) : null,
      elevationGainM: this.draft.dplus,
      confirmDuplicate,
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.showLog.set(false);
        this.draft = { activityDate: new Date().toISOString().slice(0, 10), title: '', km: null, min: null, dplus: null };
        this.toast.success('Sortie ajoutée');
        this.load();
      },
      error: async (err: { status?: number; error?: { message?: string } }) => {
        this.busy.set(false);
        if (err.status !== 409 || confirmDuplicate) {
          this.toast.error('Enregistrement impossible.');
          return;
        }
        if (await this.askDuplicate(err.error?.message)) { this.submitLog(true); }
      },
    });
  }

  /**
   * Doublon détecté côté serveur : on montre le message tel quel — il nomme la sortie déjà
   * enregistrée — et on laisse l'athlète trancher. Deux séances le même jour, ça existe.
   */
  private askDuplicate(message?: string): Promise<boolean> {
    return this.confirm.ask({
      title: 'Sortie déjà enregistrée ?',
      message: message ?? 'Une sortie très proche existe déjà ce jour-là.',
      confirmLabel: 'Enregistrer quand même',
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) { return; }
    this.sendFile(file, false, input);
  }

  private sendFile(file: File, confirmDuplicate: boolean, input?: HTMLInputElement): void {
    this.fileBusy.set(true);
    this.portal.importActivityFile(file, confirmDuplicate).subscribe({
      next: () => {
        this.fileBusy.set(false);
        if (input) { input.value = ''; }
        this.toast.success('Trace importée');
        this.load();
      },
      error: async (err: { status?: number; error?: { message?: string } }) => {
        this.fileBusy.set(false);
        if (err.status !== 409 || confirmDuplicate) {
          if (input) { input.value = ''; }
          this.toast.error('Import impossible (fichier GPX/TCX attendu).');
          return;
        }
        // Le cas courant : la trace de la montre a déjà été remontée par Strava.
        if (await this.askDuplicate(err.error?.message)) {
          this.sendFile(file, true, input);
        } else if (input) {
          input.value = '';
        }
      },
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

  /** Allure moyenne « m'ss » de la sortie, ou `null` si distance ou durée manquent. */
  pace(a: Activity): string | null {
    return formatPace(a.paceSPerKm) ?? paceFrom(a.distanceM, a.durationS);
  }

  statusLabel(s: Activity['status']): string { return ACTIVITY_STATUS_LABELS[s]; }
  statusBadge(s: Activity['status']): string { return ACTIVITY_STATUS_BADGE[s]; }
}
