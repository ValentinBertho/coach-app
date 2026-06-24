import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Message } from '../../core/models/message.model';
import { AuthService } from '../../core/services/auth.service';
import { MessageService } from '../../core/services/message.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Fil de discussion coach ↔ athlète. Mode coach si [athleteId] fourni (route /app),
 * sinon mode athlète (route /athlete, /me/messages).
 */
@Component({
  selector: 'app-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit, OnDestroy {
  readonly athleteId = input<string>();

  private readonly messageService = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly messages = signal<Message[]>([]);
  readonly loading = signal(true);
  readonly coachMode = computed(() => !!this.athleteId());
  readonly backLink = computed(() => (this.athleteId() ? ['/app/athletes', this.athleteId()!] : ['/athlete/today']));
  draft = '';

  private stream?: EventSource;

  ngOnInit(): void {
    this.load();
    this.stream = this.messageService.stream(this.athleteId(), (m) => this.append(m));
  }

  ngOnDestroy(): void {
    this.stream?.close();
  }

  /** Ajoute un message reçu en temps réel, en évitant les doublons (écho de notre envoi). */
  private append(m: Message): void {
    this.messages.update((list) => (list.some((x) => x.id === m.id) ? list : [...list, m]));
  }

  load(): void {
    this.loading.set(true);
    const obs = this.athleteId()
      ? this.messageService.coachThread(this.athleteId()!)
      : this.messageService.myThread();
    obs.subscribe({
      next: (m) => { this.messages.set(m); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  mine(m: Message): boolean {
    return m.senderUserId === this.auth.currentUser()?.id;
  }

  send(): void {
    const body = this.draft.trim();
    if (!body) return;
    const obs = this.athleteId()
      ? this.messageService.coachSend(this.athleteId()!, body)
      : this.messageService.mySend(body);
    obs.subscribe({
      next: (m) => {
        this.append(m);
        this.draft = '';
      },
      error: () => this.toast.error('Envoi impossible.'),
    });
  }

  /** Envoi d'une pièce jointe (image ou PDF) sélectionnée. */
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    const obs = this.athleteId()
      ? this.messageService.coachSendAttachment(this.athleteId()!, file, this.draft.trim() || undefined)
      : this.messageService.mySendAttachment(file, this.draft.trim() || undefined);
    obs.subscribe({
      next: (m) => {
        this.append(m);
        this.draft = '';
        input.value = '';
      },
      error: () => this.toast.error('Pièce jointe refusée (image ou PDF, max 10 Mo).'),
    });
  }

  attachmentUrl(m: Message): string {
    return this.messageService.attachmentUrl(this.athleteId(), m.id);
  }

  isImage(m: Message): boolean {
    return (m.attachmentContentType ?? '').startsWith('image/');
  }
}
