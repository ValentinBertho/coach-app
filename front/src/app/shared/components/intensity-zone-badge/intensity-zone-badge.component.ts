import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Zone d'intensité physiologique (dérivée des domaines LT1/LT2/VC, source backend). */
export type IntensityZone = 1 | 2 | 3 | 4 | 5;

interface ZoneMeta {
  /** Forme redondante (ne pas coder l'info uniquement par la couleur). */
  readonly shape: string;
  readonly name: string;
  /** Texte foncé requis pour contraste AA sur les zones claires (Z3). */
  readonly darkText: boolean;
}

const ZONE_META: Record<IntensityZone, ZoneMeta> = {
  1: { shape: '○', name: 'Récupération',  darkText: false },
  2: { shape: '◔', name: 'Endurance',     darkText: false },
  3: { shape: '◑', name: 'Tempo',         darkText: true  },
  4: { shape: '◕', name: 'Seuil',         darkText: false },
  5: { shape: '●', name: 'VO₂ / VC+',     darkText: false },
};

/**
 * Badge de zone d'intensité Z1–Z5. Sémantique constante dans toute l'app
 * (mêmes couleurs `--zone-1..5` que charts et calendrier). Redondance
 * couleur + forme + libellé « Zx » : jamais la couleur seule (accessibilité).
 *
 * @example
 * <app-intensity-zone-badge [zone]="3" />
 * <app-intensity-zone-badge [zone]="4" range="4:10–4:20 /km" />     <!-- avec plage prescrite -->
 * <app-intensity-zone-badge [zone]="2" [showName]="true" />          <!-- « Z2 · Endurance » -->
 */
@Component({
  selector: 'app-intensity-zone-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      class="zone"
      [class.zone--dark]="meta().darkText"
      [style.--zc]="'var(--zone-' + zone() + ')'"
      [attr.aria-label]="ariaLabel()"
    >
      <span class="zone__shape" aria-hidden="true">{{ meta().shape }}</span>
      <span class="zone__id">Z{{ zone() }}</span>
      @if (showName()) {
        <span class="zone__name">· {{ meta().name }}</span>
      }
      @if (range()) {
        <span class="zone__range">{{ range() }}</span>
      }
    </span>
  `,
  styles: [`
    .zone {
      display: inline-flex; align-items: center; gap: var(--sp-1);
      padding: 2px var(--sp-2); border-radius: var(--radius-full);
      font-size: var(--text-xs); font-weight: 800; line-height: 1.4;
      color: #fff; background: var(--zc); white-space: nowrap;
    }
    .zone--dark { color: var(--ink); }
    .zone__shape { font-size: 0.85em; }
    .zone__id { letter-spacing: 0.02em; }
    .zone__name { font-weight: 600; opacity: 0.92; }
    .zone__range {
      font-family: var(--font-data); font-variant-numeric: tabular-nums;
      font-weight: 600; padding-left: var(--sp-1);
      border-left: 1px solid color-mix(in srgb, currentColor 35%, transparent);
    }
  `],
})
export class IntensityZoneBadgeComponent {
  /** Zone 1–5. */
  readonly zone = input.required<IntensityZone>();
  /** Plage prescrite optionnelle (allure / FC / puissance), affichée en tabular. */
  readonly range = input<string>('');
  /** Afficher le nom de la zone (« Tempo », « Seuil »…). */
  readonly showName = input(false);

  protected readonly meta = computed(() => ZONE_META[this.zone()]);
  protected readonly ariaLabel = computed(() => {
    const m = this.meta();
    const r = this.range();
    return `Zone ${this.zone()} ${m.name}${r ? ', ' + r : ''}`;
  });
}
