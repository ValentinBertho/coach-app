import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
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
export class ChatComponent implements OnInit {
  readonly athleteId = input<string>();

  private readonly messageService = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly messages = signal<Message[]>([]);
  readonly loading = signal(true);
  readonly coachMode = computed(() => !!this.athleteId());
  readonly backLink = computed(() => (this.athleteId() ? ['/app/athletes', this.athleteId()!] : ['/athlete/today']));
  draft = '';

  ngOnInit(): void {
    this.load();
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
        this.messages.update((list) => [...list, m]);
        this.draft = '';
      },
      error: () => this.toast.error('Envoi impossible.'),
    });
  }
}
