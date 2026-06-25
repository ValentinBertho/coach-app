import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';

/**
 * Origine d'une donnée affichée. Invariant UX DARI Lab : une donnée
 * physiologique ne doit jamais être ambiguë sur sa provenance.
 *  - saisi   : déclaré par l'athlète (RPE, ressenti, commentaire)
 *  - calcule : produit par un moteur backend (ACWR, état de forme) — source de vérité
 *  - estime  : dérivé/estimé (ex : VDOT depuis une course)
 *  - mesure  : mesuré en test (LT1 / LT2 / VC)
 *  - alerte  : seuil critique dépassé
 *  - fait    : complété / validé
 */
export type DataOrigin = 'saisi' | 'calcule' | 'estime' | 'mesure' | 'alerte' | 'fait';

interface OriginMeta {
  readonly icon: string;
  readonly label: string;
  readonly cssVar: string;
}

const ORIGIN_META: Record<DataOrigin, OriginMeta> = {
  saisi:   { icon: 'pencil',         label: 'Saisi',   cssVar: 'var(--origin-saisi)' },
  calcule: { icon: 'cog',            label: 'Calculé', cssVar: 'var(--origin-calcule)' },
  estime:  { icon: 'activity',       label: 'Estimé',  cssVar: 'var(--origin-estime)' },
  mesure:  { icon: 'flask-conical',  label: 'Mesuré',  cssVar: 'var(--origin-mesure)' },
  alerte:  { icon: 'alert-triangle', label: 'Alerte',  cssVar: 'var(--origin-alerte)' },
  fait:    { icon: 'check',          label: 'Fait',    cssVar: 'var(--origin-fait)' },
};

/**
 * Marqueur d'origine d'une donnée. Redondance sémantique : couleur + icône +
 * libellé (jamais la couleur seule). Composant purement présentationnel.
 *
 * @example
 * <app-data-origin-tag origin="calcule" />
 * <app-data-origin-tag origin="mesure" [compact]="true" />   <!-- icône seule + aria-label -->
 */
@Component({
  selector: 'app-data-origin-tag',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <span
      class="origin"
      [class.origin--compact]="compact()"
      [style.--c]="meta().cssVar"
      [attr.aria-label]="'Origine : ' + meta().label"
      [attr.title]="meta().label"
    >
      <app-icon class="origin__icon" [name]="meta().icon" [size]="12" />
      @if (!compact()) {
        <span class="origin__label">{{ label() || meta().label }}</span>
      }
    </span>
  `,
  styles: [`
    .origin {
      display: inline-flex; align-items: center; gap: var(--sp-1);
      padding: 1px var(--sp-2); border-radius: var(--radius-full);
      font-size: var(--text-xs); font-weight: 700; line-height: 1.5;
      letter-spacing: 0.01em;
      color: var(--c);
      background: color-mix(in srgb, var(--c) 12%, transparent);
      border: 1px solid color-mix(in srgb, var(--c) 30%, transparent);
      white-space: nowrap;
    }
    .origin--compact { padding: 1px; width: 18px; height: 18px; justify-content: center; }
    .origin__icon { font-size: 0.8em; }
    .origin__label { font-variant-caps: all-small-caps; }
  `],
})
export class DataOriginTagComponent {
  /** Provenance de la donnée. */
  readonly origin = input.required<DataOrigin>();
  /** Libellé personnalisé (sinon libellé par défaut de l'origine). */
  readonly label = input<string>('');
  /** Mode compact : icône seule (libellé porté par aria-label). */
  readonly compact = input(false);

  protected readonly meta = computed(() => ORIGIN_META[this.origin()]);
}
