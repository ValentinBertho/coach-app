import {
  ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit,
  computed, effect, inject, input, signal, viewChild,
} from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Message } from '../../core/models/message.model';
import { AuthService } from '../../core/services/auth.service';
import { MessageService } from '../../core/services/message.service';
import { SseStream } from '../../core/services/stream-token.service';
import { ToastService } from '../../core/services/toast.service';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

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
  imports: [SkeletonComponent, IconComponent, FormsModule, RouterLink, PaginatorComponent],
  templateUrl: './chat.component.html',
  styleUrl: './chat.component.scss',
})
export class ChatComponent implements OnInit, OnDestroy {
  readonly athleteId = input<string>();

  private readonly messageService = inject(MessageService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  private readonly threadRef = viewChild<ElementRef<HTMLElement>>('thread');
  private readonly composerRef = viewChild<ElementRef<HTMLInputElement>>('composer');
  private readonly route = inject(ActivatedRoute);

  readonly messages = signal<Message[]>([]);
  readonly loading = signal(true);

  /**
   * Page du fil affichée. **0 = les messages les plus récents**, les suivantes remontent le temps.
   *
   * <p>Le fil rendait auparavant les cent derniers messages et rien derrière : une conversation
   * qui dure une saison commençait au milieu d'une phrase, sans aucun moyen d'en voir le
   * début.</p>
   */
  readonly page = signal(0);
  readonly totalPages = signal(1);
  /** Vrai dès qu'on remonte dans l'historique : le fil affiché n'est plus le fil vivant. */
  readonly viewingHistory = computed(() => this.page() > 0);
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

  private stream?: SseStream;

  /**
   * Vignettes des pièces jointes : identifiant de message → URL locale (`blob:`).
   *
   * <p>Elles sont chargées par le code applicatif, avec l'en-tête `Authorization`. L'URL de la
   * pièce jointe portait auparavant le jeton de session en paramètre, ce qui le déposait dans les
   * journaux d'accès du relais et dans l'historique du navigateur pour une heure de validité sur
   * toute l'API. Une URL locale ne sort pas de l'onglet.</p>
   */
  private readonly imageUrls = signal<Record<string, string>>({});
  /** Ce qui a déjà été demandé, pour ne pas retélécharger à chaque passe de rendu. */
  private readonly requestedImages = new Set<string>();

  constructor() {
    // Auto-scroll en bas à chaque nouveau message / chargement.
    effect(() => {
      this.messages();
      const el = this.threadRef()?.nativeElement;
      if (el) setTimeout(() => (el.scrollTop = el.scrollHeight));
    });
  }

  ngOnInit(): void {
    this.load(0);
    this.stream = this.messageService.stream(this.athleteId(), (m) => this.append(m));
    // Ouvrir le fil vaut accusé de lecture : le badge de la boîte de réception se met à jour.
    if (this.athleteId()) {
      this.messageService.markThreadRead(this.athleteId()!).subscribe({
        next: () => this.messageService.refreshUnread().subscribe({ error: () => { /* badge best-effort */ } }),
        error: () => { /* le fil reste lisible même si l'accusé échoue */ },
      });
    }
    this.focusIfAskedTo();
  }

  /**
   * `?reply=1` — on arrive du bouton « Répondre » d'une notification : le champ de saisie prend
   * le focus, clavier ouvert.
   *
   * <p>Sans ça, l'action rapide déposait le coach devant un fil qu'il devait encore toucher pour
   * écrire : le tap gagné sur la notification était rendu à l'arrivée. Le délai laisse au fil le
   * temps de se peindre et de dérouler jusqu'en bas — donner le focus avant, c'est le voir sauter
   * pendant qu'on tape.</p>
   */
  private focusIfAskedTo(): void {
    if (this.route.snapshot.queryParamMap.get('reply') !== '1') return;
    setTimeout(() => this.composerRef()?.nativeElement.focus(), 250);
  }

  ngOnDestroy(): void {
    this.stream?.close();
    // Les URL locales retiennent les octets tant qu'on ne les révoque pas : un fil chargé de
    // photos garderait plusieurs mégaoctets en mémoire après sa fermeture.
    for (const url of Object.values(this.imageUrls())) URL.revokeObjectURL(url);
  }

  /**
   * Ajoute un message reçu en temps réel, en évitant les doublons (écho de notre envoi).
   *
   * <p>Rien n'est ajouté tant qu'on lit l'historique : un message neuf n'a rien à faire au bas
   * d'une page d'archives — il y apparaîtrait hors de son ordre, et le retour au fil vivant le
   * ferait « disparaître ». Le badge de non-lus, lui, continue de compter.</p>
   */
  private append(m: Message): void {
    if (this.viewingHistory()) {
      return;
    }
    this.messages.update((list) => (list.some((x) => x.id === m.id) ? list : [...list, m]));
    this.loadImage(m);
  }

  /** Télécharge la vignette d'un message, une seule fois, si c'en est une. */
  private loadImage(m: Message): void {
    if (!m.attachmentId || !this.isImage(m) || this.requestedImages.has(m.id)) return;
    this.requestedImages.add(m.id);
    this.messageService.attachmentBlob(this.athleteId(), m.id).subscribe({
      next: (blob) => this.imageUrls.update(
        (urls) => ({ ...urls, [m.id]: URL.createObjectURL(blob) })),
      // Une vignette manquante ne doit pas casser le fil : le lien « ouvrir » reste disponible.
      error: () => this.requestedImages.delete(m.id),
    });
  }

  load(page = this.page()): void {
    this.loading.set(true);
    const obs = this.athleteId()
      ? this.messageService.coachThread(this.athleteId()!, page)
      : this.messageService.myThread(page);
    obs.subscribe({
      next: (result) => {
        this.messages.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(Math.max(1, result.totalPages));
        this.loading.set(false);
        for (const message of result.content) this.loadImage(message);
      },
      error: () => this.loading.set(false),
    });
  }

  /** Change de page. Le flux temps réel reste ouvert : cf. `append`. */
  goToPage(page: number): void {
    this.load(page);
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
      // Écrire depuis une page d'archives ramène au fil vivant : c'est là que le message part.
      next: (m) => { this.draft = ''; if (this.viewingHistory()) { this.load(0); } else { this.append(m); } },
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
        this.draft = '';
        input.value = '';
        if (this.viewingHistory()) { this.load(0); } else { this.append(m); }
      },
      error: () => this.toast.error('Pièce jointe refusée (image ou PDF, max 10 Mo).'),
    });
  }

  /** URL locale de la vignette, ou `null` tant qu'elle n'est pas arrivée. */
  imageUrl(m: Message): string | null {
    return this.imageUrls()[m.id] ?? null;
  }

  /**
   * Ouvre la pièce jointe dans un onglet, avec un jeton à usage unique demandé au moment du clic.
   *
   * <p>L'onglet est ouvert <b>dans le geste</b>, avant l'aller-retour : ouvert après, il serait
   * bloqué comme une fenêtre surgissante. Il n'affiche rien tant que l'URL n'est pas connue —
   * c'est l'affaire d'un aller-retour.</p>
   */
  openAttachment(m: Message, event: Event): void {
    event.preventDefault();
    const tab = window.open('', '_blank');
    if (tab) tab.opener = null;
    this.messageService.attachmentUrl(this.athleteId(), m.id).subscribe({
      next: (url) => {
        if (tab) { tab.location.href = url; } else { window.location.href = url; }
      },
      error: () => {
        tab?.close();
        this.toast.error('Pièce jointe indisponible.');
      },
    });
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
