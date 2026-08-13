import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from '../../../core/services/message.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/** À qui l'on répond. `null` ferme la feuille. */
export interface ReplyTarget {
  athleteId: string;
  name: string;
  /** Contexte affiché en tête, s'il y en a un : « Séance manquée », « Douleur signalée »… */
  about?: string;
}

/**
 * Répondre à un athlète sans quitter l'écran où l'on est.
 *
 * <p><b>Pourquoi.</b> Le seul geste qu'un coach fait vraiment depuis son téléphone en voyant une
 * alerte, c'est écrire un mot. Il fallait pour cela ouvrir la fiche de l'athlète, trouver son
 * onglet Messages, attendre le fil, puis écrire : quatre écrans pour une phrase, et l'on perdait
 * au passage la file qu'on était en train de traiter. Cette feuille écrit dans le même fil —
 * c'est la même route d'API que la messagerie, pas un second canal.</p>
 *
 * <p>Les réponses toutes faites ne sont pas un gadget : au bord d'une piste, « Bien joué 👏 »
 * envoyé en un tap vaut mieux qu'un message qu'on se promet d'écrire plus tard.</p>
 */
@Component({
  selector: 'app-reply-sheet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    @if (target(); as t) {
      <div class="rs" (click)="close()">
        <div class="rs__panel" (click)="$event.stopPropagation()" role="dialog"
             [attr.aria-label]="'Répondre à ' + t.name">
          <div class="rs__hd">
            <div class="rs__id">
              <strong>Répondre à {{ t.name }}</strong>
              @if (t.about) { <span class="field-hint">{{ t.about }}</span> }
            </div>
            <button type="button" class="btn btn-ghost btn-sm" (click)="close()" aria-label="Fermer">
              <app-icon name="x" [size]="18" />
            </button>
          </div>

          <div class="rs__quick">
            @for (q of quickReplies; track q) {
              <button type="button" class="rs__chip" (click)="sendNow(q)" [disabled]="sending()">{{ q }}</button>
            }
          </div>

          <textarea #box class="form-control rs__box" rows="3" [(ngModel)]="draft"
                    placeholder="Ton message…"></textarea>

          <div class="rs__act">
            <button type="button" class="btn btn-ghost" (click)="close()">Annuler</button>
            <button type="button" class="btn btn-primary" [disabled]="sending() || !draft.trim()"
                    (click)="sendNow(draft)">
              {{ sending() ? 'Envoi…' : 'Envoyer' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .rs {
      position: fixed; inset: 0; z-index: 320;
      background: var(--bg-overlay, rgba(8, 16, 18, 0.62));
      display: flex; align-items: flex-end; justify-content: center;
    }
    .rs__panel {
      width: 100%; max-width: 34rem;
      display: flex; flex-direction: column; gap: var(--sp-3);
      padding: var(--sp-4);
      padding-bottom: calc(var(--sp-4) + env(safe-area-inset-bottom, 0px));
      background: var(--paper);
      border-top-left-radius: var(--radius-lg); border-top-right-radius: var(--radius-lg);
      box-shadow: var(--shadow-lg);
    }
    .rs__hd { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-2); }
    .rs__id { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .rs__quick { display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    .rs__chip {
      min-height: 44px; padding: 0 var(--sp-3);
      border: 1px solid var(--hairline); border-radius: var(--radius-full);
      background: var(--paper); color: var(--ink-2);
      font-family: inherit; font-size: var(--text-sm); font-weight: 600; cursor: pointer;
    }
    .rs__chip:hover { background: var(--primary-wash); border-color: var(--primary-light); color: var(--primary); }
    .rs__box { min-height: 88px; resize: vertical; }
    .rs__act { display: flex; gap: var(--sp-2); }
    .rs__act .btn { flex: 1; min-height: 48px; justify-content: center; }

    @media (min-width: 901px) {
      .rs { align-items: center; }
      .rs__panel { border-radius: var(--radius-lg); padding-bottom: var(--sp-4); }
    }
  `],
})
export class ReplySheetComponent {
  /** Cible courante ; `null` = feuille fermée. */
  readonly target = input<ReplyTarget | null>(null);

  /** Émis à la fermeture (envoi réussi ou annulation) : au parent de remettre sa cible à `null`. */
  readonly closed = output<void>();
  /** Émis après un envoi réussi, pour rafraîchir ce qui doit l'être. */
  readonly sent = output<void>();

  private readonly messages = inject(MessageService);
  private readonly toast = inject(ToastService);
  private readonly box = viewChild<ElementRef<HTMLTextAreaElement>>('box');

  readonly sending = signal(false);
  draft = '';

  readonly quickReplies = ['Bien joué 👏', 'Repose-toi bien', 'On en parle ?', "Ajuste l'allure"];

  constructor() {
    // Le clavier s'ouvre avec la feuille : on vient d'appuyer sur « Répondre », il n'y a rien
    // d'autre à faire ici que d'écrire.
    effect(() => {
      if (this.target()) setTimeout(() => this.box()?.nativeElement.focus(), 120);
    });
  }

  sendNow(body: string): void {
    const t = this.target();
    const text = body.trim();
    if (!t || !text || this.sending()) return;
    this.sending.set(true);
    this.messages.coachSend(t.athleteId, text).subscribe({
      next: () => {
        this.sending.set(false);
        this.draft = '';
        this.toast.success(`Message envoyé à ${t.name}`);
        this.sent.emit();
        this.closed.emit();
      },
      error: () => { this.sending.set(false); this.toast.error('Envoi impossible.'); },
    });
  }

  close(): void {
    this.draft = '';
    this.closed.emit();
  }
}
