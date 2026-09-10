import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Message } from '../../../core/models/message.model';
import { IconComponent } from '../icon/icon.component';

/**
 * Le fil d'une séance : la question du coach, les réponses, et de quoi répondre — le tout sur la
 * séance dont il est question.
 *
 * <h2>Pourquoi ce composant existe</h2>
 *
 * <p>Le coach commentait une séance pour demander un ressenti ; l'athlète répondait depuis sa
 * fiche ; la réponse partait dans la messagerie et <b>n'y laissait plus rien</b>. Côté athlète,
 * le champ de réponse disparaissait une fois envoyé — impossible de se relire, impossible de
 * voir que le coach avait répondu. Côté coach, la séance commentée ne portait aucune trace de la
 * réponse : il fallait ouvrir la boîte de réception au bon moment, retrouver la conversation,
 * puis deviner de quelle sortie on parlait. En bêta, une réponse envoyée le soir même a été
 * découverte trois jours plus tard, et l'échange s'est terminé sur WhatsApp.</p>
 *
 * <p><b>Ce n'est pas un second canal.</b> Ce sont exactement les messages de la messagerie, lus
 * par leur rattachement à la séance ({@code Message.workoutId}). L'échange reste entier dans la
 * boîte de réception ; la séance en est une <b>vue</b>, celle qui porte le contexte. Ouvrir deux
 * conversations parallèles aurait donné au coach deux endroits où regarder, c'est-à-dire un de
 * trop.</p>
 *
 * <p>Le composant ne parle à personne : il reçoit les messages et émet le texte saisi. Les deux
 * portails l'utilisent tel quel, ce qui garantit qu'ils affichent le même fil.</p>
 */
@Component({
  selector: 'app-session-thread',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, IconComponent],
  template: `
    <div class="st">
      <!-- L'ouverture du fil : le mot du coach, quand il y en a un. Il vit sur la séance
           (workout.coachComment) et non dans la messagerie ; l'afficher ici est ce qui donne
           aux réponses le sens d'être des réponses. -->
      @if (headText) {
        <article class="st-msg st-msg--coach st-msg--head">
          <header class="st-msg__hd">
            <strong>{{ headAuthor }}</strong>
            @if (headAt) { <span class="field-hint metric">{{ headAt | date: 'd MMM, HH:mm' }}</span> }
          </header>
          <p class="st-msg__body">{{ headText }}</p>
        </article>
      }

      @for (m of messages; track m.id) {
        <article class="st-msg"
                 [class.st-msg--coach]="m.senderRole !== 'ATHLETE'"
                 [class.st-msg--athlete]="m.senderRole === 'ATHLETE'"
                 [class.st-msg--mine]="isMine(m)">
          <header class="st-msg__hd">
            <strong>{{ m.senderName }}</strong>
            <span class="field-hint metric">{{ m.createdAt | date: 'd MMM, HH:mm' }}</span>
          </header>
          <p class="st-msg__body">{{ m.body }}</p>
          @if (m.attachmentFilename) {
            <p class="field-hint st-msg__att">
              <app-icon name="paperclip" [size]="13" /> {{ m.attachmentFilename }}
              <span class="st-msg__att-hint">— à ouvrir depuis la messagerie</span>
            </p>
          }
        </article>
      }

      @if (!headText && messages.length === 0) {
        <p class="field-hint st-empty">{{ emptyLabel }}</p>
      }

      @if (canReply) {
        <div class="st-reply">
          <label class="st-reply__lb" [attr.for]="inputId">{{ replyLabel }}</label>
          <textarea [id]="inputId" class="form-control" rows="2" maxlength="2000"
                    [ngModel]="draft()" (ngModelChange)="draft.set($event)"
                    [placeholder]="placeholder"></textarea>
          <div class="st-reply__actions">
            <button type="button" class="btn btn-primary btn-sm"
                    [disabled]="!draft().trim() || sending" (click)="submit()">
              {{ sending ? 'Envoi…' : 'Envoyer' }}
            </button>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .st { display: flex; flex-direction: column; gap: .6rem; }

    .st-msg {
      border: 1px solid var(--border);
      border-radius: var(--radius-md, 10px);
      padding: .6rem .75rem;
      background: var(--surface, transparent);
    }
    /* La couleur dit qui parle, jamais le seul alignement : sur un téléphone étroit, deux bulles
       alignées à droite et à gauche se distinguent mal, et un fil de trois messages se relit. */
    .st-msg--coach { border-left: 3px solid var(--accent, #0b7); }
    .st-msg--athlete { border-left: 3px solid var(--brand, #36c); }
    .st-msg--head { background: color-mix(in srgb, var(--accent, #0b7) 6%, transparent); }

    .st-msg__hd { display: flex; align-items: baseline; gap: .5rem; margin-bottom: .2rem; }
    .st-msg__hd strong { font-size: .85rem; }
    .st-msg__body { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .st-msg__att { margin: .35rem 0 0; display: flex; align-items: center; gap: .3rem; }
    .st-msg__att-hint { opacity: .75; }

    .st-empty { margin: 0; }

    .st-reply { display: flex; flex-direction: column; gap: .4rem; margin-top: .2rem; }
    .st-reply__lb { font-size: .85rem; font-weight: 600; }
    .st-reply__actions { display: flex; justify-content: flex-end; }
  `],
})
export class SessionThreadComponent {
  /** Messages rattachés à la séance, du plus ancien au plus récent. */
  @Input() messages: Message[] = [];

  /** Ouverture du fil : le mot du coach porté par la séance elle-même (facultatif). */
  @Input() headText: string | null = null;
  @Input() headAt: string | null = null;
  @Input() headAuthor = 'Ton coach';

  /** Qui regarde : sert à distinguer ses propres messages de ceux d'en face. */
  @Input() viewer: 'COACH' | 'ATHLETE' = 'ATHLETE';

  @Input() canReply = true;
  @Input() sending = false;
  @Input() replyLabel = 'Répondre';
  @Input() placeholder = 'Ta réponse…';
  @Input() emptyLabel = 'Rien n’a encore été écrit sur cette séance.';
  /** Identifiant du champ, pour que deux fils sur une même page gardent des labels distincts. */
  @Input() inputId = 'session-thread-reply';

  @Output() send = new EventEmitter<string>();

  readonly draft = signal('');

  isMine(m: Message): boolean {
    return this.viewer === 'ATHLETE' ? m.senderRole === 'ATHLETE' : m.senderRole !== 'ATHLETE';
  }

  submit(): void {
    const text = this.draft().trim();
    if (!text || this.sending) {
      return;
    }
    this.send.emit(text);
  }

  /** Vide la saisie — appelé par le parent quand l'envoi a réussi, jamais avant. */
  clear(): void {
    this.draft.set('');
  }
}
