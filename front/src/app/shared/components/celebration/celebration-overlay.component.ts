import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CelebrationService } from '../../../core/services/celebration.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Overlay de célébration, à monter une fois dans la coquille athlète.
 *
 * Non bloquant et non cliquable : il félicite, il ne demande rien. Il s'efface tout seul —
 * une célébration qu'il faut congédier n'est plus une récompense, c'est une interruption.
 *
 * @example <app-celebration-overlay />
 */
@Component({
  selector: 'app-celebration-overlay',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (celebration.current(); as c) {
      <div class="celebration" role="status" aria-live="polite">
        <div class="celebration__card">
          <span class="celebration__burst" aria-hidden="true">
            <app-icon name="party-popper" [size]="26" />
          </span>
          <strong class="celebration__title">{{ c.title }}</strong>
          @if (c.detail) { <span class="celebration__detail">{{ c.detail }}</span> }
        </div>
      </div>
    }
  `,
  styles: [`
    .celebration {
      position: fixed; inset: 0; z-index: 700;
      display: flex; align-items: center; justify-content: center;
      pointer-events: none; /* elle félicite, elle ne demande rien */
    }
    .celebration__card {
      display: flex; flex-direction: column; align-items: center; gap: var(--sp-2);
      padding: var(--sp-5) var(--sp-6);
      background: var(--paper); border: 1px solid var(--hairline);
      border-radius: var(--radius-xl); box-shadow: var(--shadow-lg);
      animation: celebrationIn var(--duration-slow) var(--ease-spring, var(--ease)) both;
    }
    .celebration__burst {
      display: inline-flex; align-items: center; justify-content: center;
      width: 56px; height: 56px; border-radius: var(--radius-full);
      background: var(--gradient-accent, var(--accent)); color: #fff;
      animation: celebrationBurst var(--duration-slow) var(--ease) both;
    }
    .celebration__title { font-size: var(--text-lg); color: var(--ink); text-align: center; }
    .celebration__detail { font-size: var(--text-sm); font-weight: 700; color: var(--accent); }

    @keyframes celebrationIn {
      from { opacity: 0; transform: translateY(12px) scale(0.94); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }
    @keyframes celebrationBurst {
      0% { transform: scale(0.4) rotate(-18deg); }
      60% { transform: scale(1.12) rotate(6deg); }
      100% { transform: scale(1) rotate(0); }
    }
    /* Le réglage système fait foi : on garde le message, on retire le mouvement. */
    @media (prefers-reduced-motion: reduce) {
      .celebration__card, .celebration__burst { animation: none; }
    }
  `],
})
export class CelebrationOverlayComponent {
  protected readonly celebration = inject(CelebrationService);
}
