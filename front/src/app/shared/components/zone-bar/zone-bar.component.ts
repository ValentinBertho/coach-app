import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Une étape porteuse d'une zone et d'un volume — forme commune aux séances et aux modèles. */
export interface ZoneBarStep {
  zone: string | null;
  distanceM?: number | null;
  durationS?: number | null;
  repetitions?: number | null;
}

interface Segment { zone: string; pct: number; label: string; }

const ZONE_ORDER = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5'];

/**
 * Aperçu de la structure d'une séance sous forme de barre de zones, chaque segment
 * proportionnel au volume passé dans la zone.
 *
 * « 5 étape(s) » ne dit rien du contenu d'une séance : deux modèles à cinq étapes peuvent être
 * un footing et une séance VMA. La barre se lit d'un coup d'œil, et utilise les mêmes couleurs
 * de zone que le calendrier et l'éditeur.
 *
 * La couleur ne porte pas seule l'information : l'aria-label et l'infobulle décrivent la
 * répartition en toutes lettres.
 */
@Component({
  selector: 'app-zone-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (segments().length) {
      <span class="zb" role="img" [attr.aria-label]="description()" [title]="description()">
        @for (s of segments(); track s.zone) {
          <span class="zb__seg" [class]="'zb__seg--' + s.zone.toLowerCase()" [style.flex]="s.pct"></span>
        }
      </span>
    }
  `,
  styles: [`
    .zb { display: flex; gap: 2px; width: 100%; }
    .zb__seg { height: 4px; border-radius: var(--radius-full); min-width: 3px; }
    .zb__seg--z1 { background: var(--zone-1); }
    .zb__seg--z2 { background: var(--zone-2); }
    .zb__seg--z3 { background: var(--zone-3); }
    .zb__seg--z4 { background: var(--zone-4); }
    .zb__seg--z5 { background: var(--zone-5); }
  `],
})
export class ZoneBarComponent {
  readonly steps = input<ZoneBarStep[]>([]);

  readonly segments = computed<Segment[]>(() => {
    const byZone = new Map<string, number>();
    for (const s of this.steps()) {
      if (!s.zone) continue;
      const reps = Math.max(1, s.repetitions ?? 1);
      // Distance et durée ne sont pas commensurables : on prend le volume disponible et on
      // raisonne en proportion, ce qui suffit à donner la forme de la séance.
      const volume = (s.distanceM ?? 0) * reps || (s.durationS ?? 0) * reps;
      if (volume <= 0) continue;
      byZone.set(s.zone, (byZone.get(s.zone) ?? 0) + volume);
    }
    const total = [...byZone.values()].reduce((a, b) => a + b, 0);
    if (!total) {
      // Aucun volume exploitable : on retombe sur la présence des zones, à parts égales.
      const present = ZONE_ORDER.filter((z) => this.steps().some((s) => s.zone === z));
      return present.map((zone) => ({ zone, pct: 1, label: zone }));
    }
    return ZONE_ORDER
      .filter((z) => byZone.has(z))
      .map((zone) => {
        const pct = Math.round((byZone.get(zone)! / total) * 100);
        return { zone, pct: Math.max(pct, 2), label: `${zone} ${pct} %` };
      });
  });

  readonly description = computed(() => {
    const segs = this.segments();
    if (!segs.length) return '';
    return 'Répartition par zone : ' + segs.map((s) => s.label).join(', ');
  });
}
