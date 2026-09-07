import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy, Component, ElementRef, OnDestroy, OnInit,
  computed, effect, inject, signal, viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Message } from '../../core/models/message.model';
import { SseStream } from '../../core/services/stream-token.service';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { AuthService } from '../../core/services/auth.service';
import {
  ConversationKind, ConversationService, ConversationSummary, Recipient,
} from '../../core/services/conversation.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/** Icône par nature de fil : on reconnaît un groupe d'un binôme sans lire le sous-titre. */
const KIND_ICONS: Record<ConversationKind, string> = {
  ATHLETE_COACH: 'user',
  COACH_COACH: 'users',
  GROUP: 'users-round',
  CLUB: 'building-2',
};

/**
 * La messagerie, pour les deux rôles.
 *
 * <p><b>Ce qu'elle remplace.</b> Il n'existait pas de fil : il existait un athlète. Tous les
 * messages le concernant tombaient dans le même tas, lisible par n'importe quel coach ayant accès
 * à lui — et un responsable de club a lu les échanges du propriétaire avec ses athlètes. Côté
 * athlète, l'écran ne savait afficher qu'une seule conversation, celle de « son » coach.</p>
 *
 * <p>Un seul écran sert les deux rôles parce que le serveur ne fait plus la différence : il rend
 * les fils auxquels on participe. Écrire deux écrans, c'était écrire deux fois chaque règle de
 * cloisonnement — et n'en corriger qu'une, le jour venu.</p>
 */
@Component({
  selector: 'app-conversations',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DatePipe, IconComponent, SkeletonComponent, PaginatorComponent],
  templateUrl: './conversations.component.html',
  styleUrl: './conversations.component.scss',
  // Un fil ouvert change la nature de l'écran au téléphone : il passe en plein écran. La classe
  // est portée par l'hôte pour que la feuille de style le sache sans qu'aucun enfant n'ait à
  // connaître la géométrie de la coquille.
  host: { '[class.thread-open]': 'openId() !== null' },
})
export class ConversationsComponent implements OnInit, OnDestroy {
  private readonly conversations = inject(ConversationService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly threadRef = viewChild<ElementRef<HTMLElement>>('thread');

  readonly list = signal<ConversationSummary[]>([]);
  readonly loading = signal(true);
  readonly openId = signal<string | null>(null);
  readonly messages = signal<Message[]>([]);
  readonly loadingThread = signal(false);

  /**
   * Page du fil ouvert. **0 = les messages les plus récents**, les suivantes remontent le temps.
   *
   * <p>Le fil rendait auparavant les cent derniers messages et rien derrière : le début d'une
   * conversation longue était inatteignable, sans même une indication qu'il manquait quelque
   * chose.</p>
   */
  readonly page = signal(0);
  readonly totalPages = signal(1);
  /** Vrai dès qu'on remonte dans l'historique : le fil affiché n'est plus le fil vivant. */
  readonly viewingHistory = computed(() => this.page() > 0);
  readonly sending = signal(false);
  draft = '';

  /** Sélecteur de destinataire (« Nouveau message »). */
  readonly pickerOpen = signal(false);
  readonly recipients = signal<Recipient[]>([]);
  search = '';

  readonly kindIcons = KIND_ICONS;
  readonly me = this.auth.currentUser;

  readonly current = computed(() => this.list().find((c) => c.id === this.openId()) ?? null);
  readonly canPost = computed(() => this.current()?.canPost ?? false);

  readonly filteredRecipients = computed(() => {
    const q = this.searchTerm().trim().toLowerCase();
    const all = this.recipients();
    return q ? all.filter((r) => r.name.toLowerCase().includes(q)) : all;
  });
  private readonly searchTerm = signal('');

  onSearch(value: string): void {
    this.search = value;
    this.searchTerm.set(value);
  }

  private stream?: SseStream;

  /**
   * Écran étroit : la liste et le fil n'y tiennent pas côte à côte.
   *
   * <p>Ce n'est pas qu'une affaire de largeur. Sur deux colonnes, ouvrir d'office la conversation
   * la plus chaude remplit un panneau qui serait vide ; au téléphone, cela escamotait la liste —
   * l'athlète arrivait sur « Messages » et tombait à l'intérieur d'un fil, sans jamais voir qu'il
   * en avait d'autres.</p>
   */
  private isNarrow(): boolean {
    return typeof window !== 'undefined' && window.matchMedia('(max-width: 860px)').matches;
  }

  constructor() {
    // Le fil colle au bas à chaque arrivée : une conversation se lit par la fin.
    effect(() => {
      this.messages();
      const el = this.threadRef()?.nativeElement;
      if (el) setTimeout(() => (el.scrollTop = el.scrollHeight));
    });
  }

  ngOnInit(): void {
    this.reload(this.route.snapshot.queryParamMap.get('c'));
    if (this.route.snapshot.queryParamMap.get('reply') === '1') {
      // Arrivée depuis l'action « Répondre » d'une notification.
      setTimeout(() => document.querySelector<HTMLInputElement>('.composer input')?.focus(), 200);
    }
  }

  ngOnDestroy(): void {
    this.stream?.close();
  }

  private reload(openId: string | null): void {
    this.conversations.inbox().subscribe({
      next: (list) => {
        this.list.set(list);
        this.loading.set(false);
        // Un fil explicitement demandé (notification, lien partagé) s'ouvre toujours. Sinon, on
        // n'ouvre d'office que sur deux colonnes, là où le panneau resterait vide.
        const asked = openId && list.some((c) => c.id === openId) ? openId : null;
        const target = asked ?? (this.isNarrow()
          ? null
          : list.find((c) => c.unreadCount > 0)?.id ?? list[0]?.id ?? null);
        if (target) {
          this.select(target);
        }
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('Chargement des conversations impossible.');
      },
    });
  }

  /** Ouvre un fil : messages, accusé de lecture, flux temps réel. */
  select(id: string): void {
    if (this.openId() === id && this.messages().length) {
      return;
    }
    this.openId.set(id);
    this.loadingThread.set(true);
    this.messages.set([]);
    this.page.set(0);
    this.totalPages.set(1);
    this.stream?.close();

    // L'URL porte le fil ouvert : rafraîchir la page, ou revenir depuis une notification,
    // retombe sur la même conversation.
    this.router.navigate([], { relativeTo: this.route, queryParams: { c: id }, replaceUrl: true });

    this.loadPage(id, 0);
    this.conversations.markRead(id).subscribe({
      next: () => {
        this.list.update((list) =>
          list.map((c) => (c.id === id ? { ...c, unreadCount: 0 } : c)));
        this.conversations.refreshUnread().subscribe({ error: () => {} });
      },
      error: () => {},
    });
    this.stream = this.conversations.stream(id, (m) => this.append(m));
  }

  /** Charge une page du fil ouvert. Page 0 = les messages les plus récents. */
  private loadPage(conversationId: string, page: number): void {
    this.loadingThread.set(true);
    this.conversations.messages(conversationId, page).subscribe({
      next: (result) => {
        this.messages.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(Math.max(1, result.totalPages));
        this.loadingThread.set(false);
      },
      error: () => {
        this.loadingThread.set(false);
        this.toast.error('Conversation indisponible.');
      },
    });
  }

  /** Change de page dans le fil ouvert. Le flux temps réel reste ouvert : cf. `append`. */
  goToPage(page: number): void {
    const id = this.openId();
    if (id) {
      this.loadPage(id, page);
    }
  }

  /**
   * Referme le fil et revient à la liste.
   *
   * <p>Le paramètre d'URL part avec : sans cela, un rafraîchissement rouvrait la conversation
   * qu'on venait justement de quitter.</p>
   */
  closeThread(): void {
    this.stream?.close();
    this.openId.set(null);
    this.messages.set([]);
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  /**
   * Ajoute un message reçu en temps réel.
   *
   * <p>Rien n'est ajouté tant qu'on lit l'historique : un message neuf n'a rien à faire au bas
   * d'une page d'archives — il y apparaîtrait hors de son ordre, et le retour au fil vivant le
   * ferait « disparaître ». Le badge de non-lus, lui, continue de compter.</p>
   */
  private append(m: Message): void {
    if (this.viewingHistory() || this.messages().some((x) => x.id === m.id)) {
      return;
    }
    this.messages.update((list) => [...list, m]);
  }

  send(): void {
    const body = this.draft.trim();
    const id = this.openId();
    if (!body || !id || this.sending()) {
      return;
    }
    this.sending.set(true);
    this.conversations.send(id, body).subscribe({
      next: (m) => {
        // Écrire depuis une page d'archives ramène au fil vivant : c'est là que le message part.
        if (this.viewingHistory()) { this.loadPage(id, 0); } else { this.append(m); }
        this.draft = '';
        this.sending.set(false);
        this.list.update((list) => list.map((c) => (c.id === id
          ? { ...c, lastMessage: m.body, lastMessageAt: m.createdAt, lastSenderName: m.senderName }
          : c)));
      },
      error: () => {
        this.sending.set(false);
        this.toast.error("Message non envoyé.");
      },
    });
  }

  // --- Nouveau message ------------------------------------------------------

  openPicker(): void {
    this.pickerOpen.set(true);
    this.onSearch('');
    this.conversations.recipients().subscribe({
      next: (list) => this.recipients.set(list),
      error: () => this.toast.error('Destinataires indisponibles.'),
    });
  }

  /** Ouvre le fil vers ce destinataire — existant ou nouveau, c'est le serveur qui tranche. */
  startWith(r: Recipient): void {
    this.pickerOpen.set(false);
    this.conversations.open(r.kind, r.id).subscribe({
      next: (conversation) => {
        this.list.update((list) => list.some((c) => c.id === conversation.id)
          ? list
          : [conversation, ...list]);
        this.select(conversation.id);
      },
      error: () => this.toast.error('Conversation impossible à ouvrir.'),
    });
  }

  mine(m: Message): boolean {
    return m.senderUserId === this.me()?.id;
  }

  /** Dans un fil collectif, l'auteur ne se devine pas : on le nomme au-dessus du message. */
  showsAuthor(): boolean {
    const kind = this.current()?.kind;
    return kind === 'GROUP' || kind === 'CLUB';
  }

  excerpt(body: string | null): string {
    if (!body) return 'Aucun message';
    return body.length > 90 ? body.slice(0, 90).trimEnd() + '…' : body;
  }
}
