import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CelebrationService } from '../../../core/services/celebration.service';

/**
 * Rendu de la célébration (cf. `CelebrationService`). Monté une fois dans la coquille
 * applicative, comme les toasts.
 *
 * Non interactif (`pointer-events: none`) : la célébration ne doit jamais retarder le geste
 * suivant — l'athlète qui enchaîne sur sa deuxième séance ne doit pas avoir à fermer quoi que
 * ce soit. Elle s'efface seule.
 */
@Component({
  selector: 'app-celebration-overlay',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (celebration.current(); as c) {
      <div class="cel" role="status" aria-live="polite">
        <div class="cel__card celebration">
          <span class="cel__emoji" aria-hidden="true">{{ c.emoji }}</span>
          <strong class="cel__title">{{ c.title }}</strong>
          @if (c.detail) { <span class="cel__detail">{{ c.detail }}</span> }
        </div>
      </div>
    }
  `,
  styles: [`
    .cel {
      position: fixed; inset: 0; z-index: 600;
      display: flex; align-items: center; justify-content: center;
      pointer-events: none;
    }
    .cel__card {
      position: relative;
      display: flex; flex-direction: column; align-items: center; gap: var(--sp-1);
      padding: var(--sp-5) var(--sp-6);
      border-radius: var(--radius-lg);
      background: var(--paper);
      border: 1px solid var(--hairline);
      box-shadow: var(--shadow-lg);
      color: var(--accent);
      text-align: center;
      max-width: 80vw;
    }
    .cel__emoji { font-size: 40px; line-height: 1; }
    .cel__title { font-size: var(--text-lg); color: var(--ink); }
    .cel__detail {
      font-size: var(--text-sm); font-weight: 700; color: var(--accent);
      font-variant-numeric: tabular-nums;
    }
  `],
})
export class CelebrationOverlayComponent {
  protected readonly celebration = inject(CelebrationService);
}
