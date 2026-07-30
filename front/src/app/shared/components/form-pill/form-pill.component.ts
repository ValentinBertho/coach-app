import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { FormLevel } from '../readiness-gauge/readiness-gauge.component';

const LEVEL_META: Record<FormLevel, { label: string; cssVar: string }> = {
  green: { label: 'En forme', cssVar: 'var(--form-green)' },
  orange: { label: 'À surveiller', cssVar: 'var(--form-orange)' },
  red: { label: 'Vigilance', cssVar: 'var(--form-red)' },
};

/**
 * Pastille compacte d'état de forme, pour les listes et bandeaux où la jauge complète
 * (`app-readiness-gauge`) prendrait trop de place.
 *
 * Invariant métier : le niveau vient du backend (fatigue + douleur, jamais le RPE) — ce
 * composant n'effectue aucun calcul. La couleur ne porte JAMAIS l'information seule : elle est
 * toujours doublée du libellé (visible ou en aria-label selon la densité) et le détail
 * fatigue/douleur est dans l'infobulle.
 */
@Component({
  selector: 'app-form-pill',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="fp" [class.fp--compact]="compact()" [style.--lc]="meta().cssVar"
          [attr.aria-label]="ariaLabel()" [title]="tooltip()">
      <span class="fp__dot" aria-hidden="true"></span>
      @if (!compact()) { <span class="fp__label">{{ meta().label }}</span> }
    </span>
  `,
  styles: [`
    .fp {
      display: inline-flex; align-items: center; gap: var(--sp-1);
      padding: 2px var(--sp-2); border-radius: var(--radius-full);
      background: color-mix(in srgb, var(--lc) 14%, transparent);
      color: var(--ink-2); font-size: var(--text-xs); font-weight: 700;
      white-space: nowrap;
    }
    .fp__dot { width: 8px; height: 8px; border-radius: 50%; background: var(--lc); flex-shrink: 0; }
    /* En mode compact, le libellé passe en aria-label + title : la couleur reste doublée
       par une information textuelle accessible, jamais seule. */
    .fp--compact { padding: 0; background: none; }
    .fp--compact .fp__dot { width: 10px; height: 10px; }
  `],
})
export class FormPillComponent {
  readonly level = input.required<FormLevel>();
  readonly fatigue = input<number | null>(null);
  readonly pain = input<number | null>(null);
  /** Aucun retour récent : la pastille reste affichée mais l'infobulle le dit. */
  readonly stale = input(false);
  /** Pastille seule (listes denses) ; le libellé reste porté par aria-label et title. */
  readonly compact = input(false);

  readonly meta = computed(() => LEVEL_META[this.level()]);

  readonly tooltip = computed(() => {
    if (this.stale()) return `${this.meta().label} — aucun retour récent`;
    const parts: string[] = [];
    if (this.fatigue() != null) parts.push(`fatigue ${this.fatigue()}/10`);
    if (this.pain() != null) parts.push(`douleur ${this.pain()}/10`);
    return parts.length ? `${this.meta().label} — ${parts.join(', ')}` : this.meta().label;
  });

  readonly ariaLabel = computed(() => `État de forme : ${this.tooltip()}`);
}
