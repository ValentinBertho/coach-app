import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Barre d'action collante (bas d'écran mobile). Projette les actions ;
 * un contexte court optionnel à gauche via `[slot=info]`.
 *
 * Règle UX : 1 action dominante + 1 secondaire max. Respecte la safe-area iOS
 * et ne masque jamais de contenu critique (le conteneur hôte doit prévoir un
 * padding-bottom équivalent).
 *
 * @example
 * <app-sticky-action-bar>
 *   <div slot="info">Séance du jour</div>
 *   <button class="btn btn-ghost btn-sm">Reporter</button>
 *   <button class="btn btn-primary">Démarrer</button>
 * </app-sticky-action-bar>
 */
@Component({
  selector: 'app-sticky-action-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sab" [class.sab--float]="floating()" role="group">
      <div class="sab__info"><ng-content select="[slot=info]" /></div>
      <div class="sab__actions"><ng-content /></div>
    </div>
  `,
  styles: [`
    :host { position: sticky; bottom: 0; z-index: 100; display: block; }
    .sab {
      display: flex; align-items: center; gap: var(--sp-3);
      padding: var(--sp-3) var(--sp-4);
      padding-bottom: calc(var(--sp-3) + env(safe-area-inset-bottom, 0px));
      background: var(--glass); backdrop-filter: saturate(180%) blur(16px);
      -webkit-backdrop-filter: saturate(180%) blur(16px);
      border-top: 1px solid var(--hairline);
    }
    .sab--float {
      margin: var(--sp-3); border-radius: var(--radius-lg);
      border: 1px solid var(--hairline); box-shadow: var(--shadow);
    }
    .sab__info { flex: 1; min-width: 0; font-size: var(--text-sm); font-weight: 600; color: var(--ink-2);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .sab__info:empty { display: none; }
    .sab__actions { display: flex; align-items: center; gap: var(--sp-2); flex-shrink: 0; }
    /* L'action dominante (dernier bouton) prend la priorité visuelle en mobile étroit. */
    @media (max-width: 420px) {
      .sab__actions { flex: 1; }
      .sab__actions ::ng-deep > .btn:last-child { flex: 1; }
    }
  `],
})
export class StickyActionBarComponent {
  /** Variante flottante (carte détachée) plutôt que barre pleine largeur. */
  readonly floating = input(false);
}
