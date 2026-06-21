import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ConfirmService } from '../../../core/services/confirm.service';

/** Modale de confirmation globale (montée une fois dans le shell). */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (confirm.pending(); as p) {
      <div class="overlay" (click)="confirm.answer(false)">
        <div class="dialog card" (click)="$event.stopPropagation()">
          <h2>{{ p.title }}</h2>
          <p>{{ p.message }}</p>
          <div class="actions">
            <button type="button" class="btn btn-ghost" (click)="confirm.answer(false)">Annuler</button>
            <button type="button" class="btn" [class.btn-danger]="p.danger" [class.btn-primary]="!p.danger"
                    (click)="confirm.answer(true)">
              {{ p.confirmLabel || 'Confirmer' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .overlay {
      position: fixed; inset: 0; z-index: 1100;
      background: var(--bg-overlay);
      display: flex; align-items: center; justify-content: center;
      padding: var(--sp-4);
      animation: fadeIn var(--duration) var(--ease);
    }
    .dialog { max-width: 440px; width: 100%; animation: slideInUp var(--duration) var(--ease); }
    .dialog h2 { margin: 0 0 var(--sp-2); }
    .dialog p { color: var(--ink-2); margin: 0 0 var(--sp-5); }
    .actions { display: flex; justify-content: flex-end; gap: var(--sp-3); }
  `],
})
export class ConfirmDialogComponent {
  readonly confirm = inject(ConfirmService);
}
