import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmService } from '../../../core/services/confirm.service';

/** Modale de confirmation globale (montée une fois dans le shell). */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    @if (confirm.pending(); as p) {
      <div class="overlay" (click)="cancel()">
        <div class="dialog card" (click)="$event.stopPropagation()">
          <h2>{{ p.title }}</h2>
          <p>{{ p.message }}</p>
          <!--
            Confirmation à recopie : réservée aux actions irréversibles et sans recours. Le
            champ n'apparaît que si l'appelant a demandé un mot, pour que la confirmation
            ordinaire reste en un clic.
          -->
          @if (p.requiredText; as required) {
            <div class="field">
              <label class="field-label" for="confirm-text">
                Écris <strong>{{ required }}</strong> pour confirmer
              </label>
              <input id="confirm-text" type="text" class="input" autocomplete="off"
                     autocapitalize="characters" spellcheck="false"
                     [ngModel]="typed()" (ngModelChange)="typed.set($event)"
                     [attr.aria-label]="'Écris ' + required + ' pour confirmer'" />
            </div>
          }
          <div class="actions">
            <button type="button" class="btn btn-ghost" (click)="cancel()">Annuler</button>
            <button type="button" class="btn" [class.btn-danger]="p.danger" [class.btn-primary]="!p.danger"
                    [disabled]="!canConfirm()" (click)="accept()">
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
    .field { display: flex; flex-direction: column; gap: var(--sp-1); margin: 0 0 var(--sp-5); }
    .actions { display: flex; justify-content: flex-end; gap: var(--sp-3); }
  `],
})
export class ConfirmDialogComponent {
  readonly confirm = inject(ConfirmService);

  /** Saisie de la confirmation à recopie, remise à zéro à chaque ouverture et fermeture. */
  readonly typed = signal('');

  /**
   * Comparaison insensible à la casse et aux espaces de bord : le clavier mobile capitalise et
   * ajoute volontiers une espace. On demande une intention, pas une dictée.
   */
  canConfirm(): boolean {
    const required = this.confirm.pending()?.requiredText;
    if (!required) return true;
    return this.typed().trim().toUpperCase() === required.trim().toUpperCase();
  }

  accept(): void {
    if (!this.canConfirm()) return;
    this.typed.set('');
    this.confirm.answer(true);
  }

  cancel(): void {
    this.typed.set('');
    this.confirm.answer(false);
  }
}
