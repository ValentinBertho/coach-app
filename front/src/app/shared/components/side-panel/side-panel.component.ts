import { animate, style, transition, trigger } from '@angular/animations';
import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, HostListener, input, model } from '@angular/core';

/**
 * Panneau latéral (drawer droit) pour l'édition contextuelle sans quitter la
 * vue (blueprint §4/§7.6). Pleine hauteur, scrollable ; fermeture backdrop /
 * Échap. Focus piégé (CDK a11y). Contenu projeté ; titre optionnel.
 * Two-way via `open`.
 *
 * @example
 * <app-side-panel [(open)]="panelOpen" title="Éditer l'exercice">…</app-side-panel>
 */
@Component({
  selector: 'app-side-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [A11yModule],
  animations: [
    trigger('backdrop', [
      transition(':enter', [style({ opacity: 0 }), animate('200ms cubic-bezier(0.32,0.72,0,1)', style({ opacity: 1 }))]),
      transition(':leave', [animate('160ms cubic-bezier(0.32,0.72,0,1)', style({ opacity: 0 }))]),
    ]),
    trigger('panel', [
      transition(':enter', [
        style({ transform: 'translateX(100%)' }),
        animate('260ms cubic-bezier(0.32,0.72,0,1)', style({ transform: 'translateX(0)' })),
      ]),
      transition(':leave', [animate('200ms cubic-bezier(0.32,0.72,0,1)', style({ transform: 'translateX(100%)' }))]),
    ]),
  ],
  template: `
    @if (open()) {
      <div class="sp__backdrop" @backdrop (click)="dismiss()" aria-hidden="true"></div>
      <aside
        class="sp__panel"
        @panel
        cdkTrapFocus
        [cdkTrapFocusAutoCapture]="true"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="title() || 'Panneau'"
        [style.width.px]="width()"
      >
        <header class="sp__head">
          <h3 class="sp__title">{{ title() }}</h3>
          <button type="button" class="sp__close" aria-label="Fermer" (click)="dismiss()">✕</button>
        </header>
        <div class="sp__body"><ng-content /></div>
      </aside>
    }
  `,
  styles: [`
    .sp__backdrop { position: fixed; inset: 0; z-index: 400; background: var(--bg-overlay); }
    .sp__panel {
      position: fixed; top: 0; right: 0; bottom: 0; z-index: 400;
      max-width: 92vw; display: flex; flex-direction: column;
      background: var(--paper); box-shadow: var(--shadow-lg);
      border-left: 1px solid var(--hairline);
      padding-bottom: env(safe-area-inset-bottom, 0px);
    }
    .sp__head {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3);
      padding: var(--sp-4) var(--sp-5); border-bottom: 1px solid var(--hairline);
      position: sticky; top: 0; background: var(--paper); z-index: 1;
    }
    .sp__title { margin: 0; font-size: var(--text-xl); }
    .sp__close {
      width: 32px; height: 32px; border-radius: var(--radius-full); border: none;
      background: var(--paper-sunk); color: var(--ink-2); cursor: pointer; font-size: var(--text-md);
    }
    .sp__body { overflow-y: auto; padding: var(--sp-5); flex: 1; }
    @media (max-width: 480px) { .sp__panel { width: 100vw !important; max-width: 100vw; } }
  `],
})
export class SidePanelComponent {
  readonly open = model(false);
  readonly title = input<string>('');
  /** Largeur du panneau en px (desktop). */
  readonly width = input(440);

  protected dismiss(): void {
    this.open.set(false);
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open()) this.dismiss();
  }
}
