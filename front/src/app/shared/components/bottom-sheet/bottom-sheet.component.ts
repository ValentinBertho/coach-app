import { animate, style, transition, trigger } from '@angular/animations';
import { A11yModule } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  input,
  model,
  signal,
} from '@angular/core';

/**
 * Bottom sheet mobile (détails, sélecteurs). Backdrop + slide-up, fermeture
 * par backdrop / Échap / swipe-down sur la poignée. Focus piégé (CDK a11y).
 * Contenu projeté ; titre optionnel. Two-way via `open`.
 *
 * @example
 * <app-bottom-sheet [(open)]="sheetOpen" title="Déplacer la séance">
 *   ...contenu...
 * </app-bottom-sheet>
 */
@Component({
  selector: 'app-bottom-sheet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [A11yModule],
  animations: [
    trigger('backdrop', [
      transition(':enter', [style({ opacity: 0 }), animate('200ms cubic-bezier(0.32,0.72,0,1)', style({ opacity: 1 }))]),
      transition(':leave', [animate('160ms cubic-bezier(0.32,0.72,0,1)', style({ opacity: 0 }))]),
    ]),
    trigger('sheet', [
      transition(':enter', [
        style({ transform: 'translateY(100%)' }),
        animate('260ms cubic-bezier(0.32,0.72,0,1)', style({ transform: 'translateY(0)' })),
      ]),
      transition(':leave', [animate('200ms cubic-bezier(0.32,0.72,0,1)', style({ transform: 'translateY(100%)' }))]),
    ]),
  ],
  template: `
    @if (open()) {
      <div class="bs__backdrop" @backdrop (click)="dismiss()" aria-hidden="true"></div>
      <div
        class="bs__panel"
        @sheet
        cdkTrapFocus
        [cdkTrapFocusAutoCapture]="true"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="title() || 'Panneau'"
        [style.transform]="dragY() > 0 ? 'translateY(' + dragY() + 'px)' : null"
        [style.transition]="dragging() ? 'none' : null"
      >
        <button
          type="button"
          class="bs__handle"
          aria-label="Glisser pour fermer"
          (pointerdown)="onDragStart($event)"
        ><span></span></button>

        @if (title()) {
          <header class="bs__head">
            <h3 class="bs__title">{{ title() }}</h3>
            @if (dismissable()) {
              <button type="button" class="bs__close" aria-label="Fermer" (click)="dismiss()">✕</button>
            }
          </header>
        }

        <div class="bs__body"><ng-content /></div>
      </div>
    }
  `,
  styles: [`
    .bs__backdrop { position: fixed; inset: 0; z-index: 500; background: var(--bg-overlay); }
    .bs__panel {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 500;
      max-height: 88vh; display: flex; flex-direction: column;
      background: var(--paper); border-radius: var(--radius-xl) var(--radius-xl) 0 0;
      box-shadow: var(--shadow-lg);
      padding-bottom: env(safe-area-inset-bottom, 0px);
      touch-action: none;
    }
    .bs__handle {
      align-self: center; padding: var(--sp-3); border: none; background: transparent; cursor: grab;
    }
    .bs__handle span { display: block; width: 40px; height: 5px; border-radius: var(--radius-full); background: var(--ink-4); }
    .bs__head {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3);
      padding: 0 var(--sp-5) var(--sp-3);
    }
    .bs__title { margin: 0; font-size: var(--text-xl); }
    .bs__close {
      width: 32px; height: 32px; border-radius: var(--radius-full); border: none;
      background: var(--paper-sunk); color: var(--ink-2); cursor: pointer; font-size: var(--text-md);
    }
    .bs__body { overflow-y: auto; padding: 0 var(--sp-5) var(--sp-5); }
    @media (min-width: 768px) {
      /* Sur desktop, centré comme un dialog ancré bas (panneau secondaire ailleurs). */
      .bs__panel { left: 50%; right: auto; transform: translateX(-50%); width: min(560px, 92vw); border-radius: var(--radius-xl); bottom: var(--sp-6); }
    }
  `],
})
export class BottomSheetComponent {
  readonly open = model(false);
  readonly title = input<string>('');
  readonly dismissable = input(true);

  protected readonly dragging = signal(false);
  protected readonly dragY = signal(0);
  private startY = 0;
  /** Seuil de fermeture (px de glissement vers le bas). */
  private static readonly CLOSE_THRESHOLD = 120;

  protected dismiss(): void {
    if (!this.dismissable()) return;
    this.dragY.set(0);
    this.open.set(false);
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open()) this.dismiss();
  }

  protected onDragStart(ev: PointerEvent): void {
    this.startY = ev.clientY;
    this.dragging.set(true);
    (ev.target as HTMLElement).setPointerCapture(ev.pointerId);
  }

  @HostListener('document:pointermove', ['$event'])
  protected onDragMove(ev: PointerEvent): void {
    if (!this.dragging()) return;
    this.dragY.set(Math.max(0, ev.clientY - this.startY));
  }

  @HostListener('document:pointerup')
  protected onDragEnd(): void {
    if (!this.dragging()) return;
    this.dragging.set(false);
    if (this.dragY() > BottomSheetComponent.CLOSE_THRESHOLD) {
      this.dismiss();
    } else {
      this.dragY.set(0); // snap back
    }
  }
}
