import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit, inject, input, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import * as L from 'leaflet';
import { ActivityService } from '../../core/services/activity.service';

/** Affiche le tracé GPS d'une activité importée (Leaflet + tuiles OpenStreetMap). */
@Component({
  selector: 'app-activity-map',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="page-header">
      <div><h1 class="display-sm">Tracé de l'activité</h1></div>
      <a [routerLink]="['/app/athletes', athleteId(), 'activities']" class="btn btn-ghost">← Activités</a>
    </section>
    @if (empty()) {
      <div class="card empty-state"><h2>Pas de tracé</h2><p class="field-hint">Cette activité n'a pas de données GPS.</p></div>
    }
    <div class="card map-card" [hidden]="empty()"><div #map class="map"></div></div>
  `,
  styles: [`
    .map-card { padding: 0; overflow: hidden; }
    .map { height: 70vh; width: 100%; border-radius: var(--radius-lg); }
  `],
})
export class ActivityMapComponent implements OnInit, OnDestroy {
  readonly athleteId = input.required<string>();
  readonly activityId = input.required<string>();
  readonly mapEl = viewChild<ElementRef<HTMLDivElement>>('map');
  readonly empty = signal(false);

  private readonly activityService = inject(ActivityService);
  private map?: L.Map;

  ngOnInit(): void {
    this.activityService.route(this.athleteId(), this.activityId()).subscribe({
      next: (pts) => {
        if (!pts || pts.length === 0) { this.empty.set(true); return; }
        setTimeout(() => this.draw(pts.map((p) => [p[0], p[1]] as L.LatLngTuple)));
      },
      error: () => this.empty.set(true),
    });
  }

  private draw(latlngs: L.LatLngTuple[]): void {
    const el = this.mapEl()?.nativeElement;
    if (!el) return;
    this.map = L.map(el);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap',
      maxZoom: 18,
    }).addTo(this.map);
    const line = L.polyline(latlngs, { color: '#4361ff', weight: 4 }).addTo(this.map);
    L.circleMarker(latlngs[0], { color: '#11c08b', radius: 7 }).addTo(this.map);
    L.circleMarker(latlngs[latlngs.length - 1], { color: '#ff5a3c', radius: 7 }).addTo(this.map);
    this.map.fitBounds(line.getBounds(), { padding: [24, 24] });
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
