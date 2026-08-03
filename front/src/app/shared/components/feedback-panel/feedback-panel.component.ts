import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FeedbackKind, FeedbackService } from '../../../core/services/feedback.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Panneau « Signaler un problème », monté une fois dans chaque coquille (coach et athlète) et
 * ouvert depuis n'importe quel écran.
 *
 * <p>Trois partis pris. Le formulaire est <b>court</b> — un type et un message : demander
 * davantage au moment où quelqu'un signale un problème est le meilleur moyen de ne rien recevoir.
 * Le contexte technique (page, version, navigateur, dernière erreur serveur) part
 * <b>automatiquement</b>, et on le dit, plutôt que de le redemander au premier échange. Enfin il
 * s'ouvre <b>là où le problème se produit</b> : le lien mailto n'existait que dans la navigation,
 * c'est-à-dire jamais au bon moment.</p>
 */
@Component({
  selector: 'app-feedback-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    @if (feedback.panelOpen()) {
      <div class="overlay" (click)="close()">
        <div class="panel card" (click)="$event.stopPropagation()" role="dialog"
             aria-labelledby="fb-title" aria-modal="true">
          <div class="head">
            <h2 id="fb-title">Un retour à nous faire ?</h2>
            <button type="button" class="btn-icon" (click)="close()" aria-label="Fermer">
              <app-icon name="x" [size]="18" />
            </button>
          </div>

          <div class="kinds" role="group" aria-label="Type de retour">
            @for (k of kinds; track k.value) {
              <button type="button" class="kind" [class.active]="kind() === k.value"
                      [attr.aria-pressed]="kind() === k.value" (click)="kind.set(k.value)">
                {{ k.label }}
              </button>
            }
          </div>

          <div class="field">
            <label class="field-label" for="fb-message">{{ placeholderLabel() }}</label>
            <textarea id="fb-message" class="input" rows="5" maxlength="4000"
                      [ngModel]="message()" (ngModelChange)="message.set($event)"
                      [placeholder]="placeholderText()"></textarea>
          </div>

          <p class="field-hint">
            <app-icon name="lock" [size]="13" />
            La page où tu es, la version de l'app et ton navigateur sont joints automatiquement —
            tu n'as pas à les décrire.
          </p>

          <div class="actions">
            <button type="button" class="btn btn-ghost" (click)="close()">Annuler</button>
            <button type="button" class="btn btn-primary"
                    [disabled]="sending() || message().trim().length === 0" (click)="send()">
              {{ sending() ? 'Envoi…' : 'Envoyer' }}
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
    .panel { max-width: 520px; width: 100%; animation: slideInUp var(--duration) var(--ease); }
    .head { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); margin-bottom: var(--sp-4); }
    .head h2 { margin: 0; }
    .btn-icon { background: none; border: 0; color: var(--ink-3); cursor: pointer; padding: var(--sp-2); min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; }
    .kinds { display: flex; gap: var(--sp-2); margin-bottom: var(--sp-4); flex-wrap: wrap; }
    .kind {
      flex: 1 1 auto; min-height: 44px; padding: var(--sp-2) var(--sp-3);
      border: 1px solid var(--line); border-radius: var(--radius-md);
      background: var(--paper); color: var(--ink-2);
      font-size: var(--text-sm); font-weight: 600; cursor: pointer;
    }
    .kind.active { border-color: var(--brand); color: var(--ink-1); background: var(--brand-subtle, var(--paper)); }
    .field { display: flex; flex-direction: column; gap: var(--sp-1); margin-bottom: var(--sp-3); }
    .field-hint { display: flex; align-items: flex-start; gap: var(--sp-2); color: var(--ink-3); font-size: var(--text-xs); margin: 0 0 var(--sp-5); }
    .actions { display: flex; justify-content: flex-end; gap: var(--sp-3); }
  `],
})
export class FeedbackPanelComponent {
  readonly feedback = inject(FeedbackService);
  private readonly toast = inject(ToastService);

  readonly kind = signal<FeedbackKind>('BUG');
  readonly message = signal('');
  readonly sending = signal(false);

  readonly kinds: { value: FeedbackKind; label: string }[] = [
    { value: 'BUG', label: 'Ça ne marche pas' },
    { value: 'IDEA', label: 'J’ai une idée' },
    { value: 'QUESTION', label: 'J’ai une question' },
  ];

  /** L'intitulé suit le type choisi : « décris le bug » n'aide pas à poser une question. */
  placeholderLabel(): string {
    switch (this.kind()) {
      case 'IDEA': return 'Ton idée';
      case 'QUESTION': return 'Ta question';
      default: return 'Ce qui s’est passé';
    }
  }

  placeholderText(): string {
    switch (this.kind()) {
      case 'IDEA': return 'Ce qui te manque, et à quel moment tu en aurais besoin…';
      case 'QUESTION': return 'Ce que tu cherches à faire…';
      default: return 'Ce que tu faisais, et ce qui s’est passé à la place…';
    }
  }

  send(): void {
    const text = this.message().trim();
    if (!text || this.sending()) return;
    this.sending.set(true);
    this.feedback.send({ kind: this.kind(), message: text }).subscribe({
      next: () => {
        this.sending.set(false);
        this.message.set('');
        this.kind.set('BUG');
        this.feedback.close();
        this.toast.success('Merci — ton retour est bien arrivé.');
      },
      error: () => this.sending.set(false),
    });
  }

  close(): void {
    this.feedback.close();
  }
}
