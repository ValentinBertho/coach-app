import {
  ChangeDetectionStrategy, Component, ElementRef, OnDestroy,
  effect, inject, input, signal, viewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { ActivityService } from '../../../core/services/activity.service';
import { AthletePortalService } from '../../../core/services/athlete-portal.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Tracé GPS d'une sortie (Leaflet + tuiles OpenStreetMap), en bloc réutilisable.
 *
 * <p>La carte existait trois fois : en page pleine côté coach, en accordéon dans « Mes
 * activités », et nulle part sur la fiche de séance — où elle manque le plus, puisque c'est là
 * qu'on relit la sortie. Un composant unique évite qu'un troisième appel Leaflet oublie de
 * détruire son instance en quittant l'écran, ce qui laisse la carte accrochée à un élément
 * disparu et fige le défilement.</p>
 *
 * <p>Même convention que les tours et le temps-en-zone : `athleteId` renseigné = route coach,
 * vide = route `/me/` du portail athlète.</p>
 */
@Component({
  selector: 'app-activity-route-map',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="rm">
      @if (title()) {
        <span class="rm-title"><app-icon name="map-pin" [size]="14" /> Carte</span>
      }
      @if (empty()) {
        <p class="field-hint rm-empty">Pas de tracé GPS pour cette sortie.</p>
      } @else {
        <div class="rm-canvas" #map [style.height.px]="height()"></div>
      }
    </div>
  `,
  styles: [`
    .rm { display: flex; flex-direction: column; gap: var(--sp-2); }
    .rm-title { display: inline-flex; align-items: center; gap: var(--sp-1); font-weight: 700; font-size: var(--text-sm); color: var(--ink); }
    .rm-canvas { width: 100%; border-radius: var(--radius-md); overflow: hidden; }
    .rm-empty { margin: 0; }
  `],
})
export class ActivityRouteMapComponent implements OnDestroy {
  readonly athleteId = input<string | null>(null);
  readonly activityId = input.required<string>();
  readonly height = input(240);
  /** Titre « Carte » affiché au-dessus ; à couper quand l'appelant pose déjà le sien. */
  readonly title = input(true);

  readonly mapEl = viewChild<ElementRef<HTMLDivElement>>('map');
  readonly empty = signal(false);

  private readonly activityService = inject(ActivityService);
  private readonly portal = inject(AthletePortalService);
  private map?: L.Map;

  constructor() {
    effect(() => {
      const a = this.athleteId();
      const id = this.activityId();
      if (id) this.load(a, id);
    }, { allowSignalWrites: true });
  }

  private load(athleteId: string | null, activityId: string): void {
    this.destroy();
    this.empty.set(false);
    const request = athleteId
      ? this.activityService.route(athleteId, activityId)
      : this.portal.activityRoute(activityId);
    request.subscribe({
      next: (pts) => {
        if (!pts?.length) { this.empty.set(true); return; }
        // Le canevas n'existe qu'une fois `empty` retombé à faux et le rendu passé : Leaflet
        // exige un élément déjà dans le document, avec une hauteur.
        setTimeout(() => this.draw(pts.map((p) => [p[0], p[1]] as L.LatLngTuple)));
      },
      error: () => this.empty.set(true),
    });
  }

  private draw(latlngs: L.LatLngTuple[]): void {
    const el = this.mapEl()?.nativeElement;
    if (!el) return;
    this.map = L.map(el, { attributionControl: true });
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap', maxZoom: 18,
    }).addTo(this.map);
    const line = L.polyline(latlngs, { color: '#0e6e78', weight: 4 }).addTo(this.map);
    // Départ et arrivée nommés par leur couleur : sur une boucle, les deux extrémités se
    // superposent et le sens du parcours devient indevinable sans repère.
    L.circleMarker(latlngs[0], { color: '#0e9e74', radius: 7 }).addTo(this.map);
    L.circleMarker(latlngs[latlngs.length - 1], { color: '#e25e3a', radius: 7 }).addTo(this.map);
    this.map.fitBounds(line.getBounds(), { padding: [24, 24] });
  }

  private destroy(): void {
    this.map?.remove();
    this.map = undefined;
  }

  ngOnDestroy(): void { this.destroy(); }
}
