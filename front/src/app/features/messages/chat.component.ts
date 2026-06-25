import {
  ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit,
  computed, effect, inject, input, signal, viewChild,
} from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Message } from '../../core/models/message.model';
import { AuthService } from '../../core/services/auth.service';
import { MessageService } from '../../core/services/message.service';
import { ToastService } from '../../core/services/toast.service';

/** Élément de fil avec séparateur de jour calculé. */
interface ThreadItem { m: Message; showDay: boolean; dayLabel: string; }

/**
 * Fil de discussion coach ↔ athlète. Mode coach si [athleteId] fourni (route /app),
 * sinon mode athlète (route /athlete, /me/messages). Temps réel via SSE.
 */
@Component({
  selector: 'app-chat',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, FormsModule, RouterLink],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit, OnDestroy {
  readonly athleteId = input<string>();

  private readonly messageService = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  private readonly threadRef = viewChild<ElementRef<HTMLElement>>('thread');

  readonly messages = signal<Message[]>([]);
  readonly loading = signal(true);
  readonly coachMode = computed(() => !!this.athleteId());
  readonly backLink = computed(() => (this.athleteId() ? ['/app/athletes', this.athleteId()!] : ['/athlete/today']));
  draft = '';

  /** Fil enrichi de séparateurs de jour. */
  readonly items = computed<ThreadItem[]>(() => {
    const out: ThreadItem[] = [];
    let prevDay = '';
    for (const m of this.messages()) {
      const day = m.createdAt.slice(0, 10);
      out.push({ m, showDay: day !== prevDay, dayLabel: this.dayLabel(m.createdAt) });
      prevDay = day;
    }
    return out;
  });

  /** Réponses rapides contextuelles selon le rôle. */
  readonly quickReplies = computed<string[]>(() =>
    this.coachMode()
      ? ['Bien joué 👏', 'Repose-toi bien', 'On en parle ?', "Ajuste l'allure"]
      : ['Bien reçu 👍', "J'ai une question", 'Un peu fatigué·e', 'Léger inconfort'],
  );

  private stream?: EventSource;

  constructor() {
    // Auto-scroll en bas à chaque nouveau message / chargement.
    effect(() => {
      this.messages();
      const el = this.threadRef()?.nativeElement;
      if (el) setTimeout(() => (el.scrollTop = el.scrollHeight));
    });
  }

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

  send(text?: string): void {
    const body = (text ?? this.draft).trim();
    if (!body) return;
    const obs = this.athleteId()
      ? this.messageService.coachSend(this.athleteId()!, body)
      : this.messageService.mySend(body);
    obs.subscribe({
      next: (m) => { this.append(m); this.draft = ''; },
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
      next: (m) => { this.append(m); this.draft = ''; input.value = ''; },
      error: () => this.toast.error('Pièce jointe refusée (image ou PDF, max 10 Mo).'),
    });
  }

  attachmentUrl(m: Message): string {
    return this.messageService.attachmentUrl(this.athleteId(), m.id);
  }

  isImage(m: Message): boolean {
    return (m.attachmentContentType ?? '').startsWith('image/');
  }

  /** Lien vers la séance rattachée (coach uniquement ; l'athlète n'a pas de vue séance dédiée). */
  workoutLink(m: Message): unknown[] | null {
    return this.coachMode() && m.workoutId
      ? ['/app/athletes', this.athleteId()!, 'workouts', m.workoutId]
      : null;
  }

  timeLabel(iso: string): string {
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? '' : new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' }).format(d);
  }

  private dayLabel(iso: string): string {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '';
    const today = new Date();
    const yest = new Date(); yest.setDate(today.getDate() - 1);
    const same = (a: Date, b: Date) => a.toDateString() === b.toDateString();
    if (same(d, today)) return "Aujourd'hui";
    if (same(d, yest)) return 'Hier';
    return new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }).format(d);
  }
}
