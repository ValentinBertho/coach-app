import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Message } from '../models/message.model';
import { SseStream, StreamTokenService } from './stream-token.service';

export type ConversationKind = 'ATHLETE_COACH' | 'COACH_COACH' | 'GROUP' | 'CLUB';

/** Une page de fil, telle que la rend l'API (page 0 = les messages les plus récents). */
export interface MessagePage {
  content: Message[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Une ligne de boîte de réception : de quel fil s'agit-il, et qu'y a-t-il de neuf. */
export interface ConversationSummary {
  id: string;
  kind: ConversationKind;
  title: string;
  subtitle: string | null;
  athleteId: string | null;
  groupId: string | null;
  lastMessage: string | null;
  lastSenderName: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
  canPost: boolean;
}

/** Un destinataire proposé par « Nouveau message ». */
export interface Recipient {
  kind: 'COACH' | 'ATHLETE';
  id: string;
  name: string;
  subtitle: string | null;
}

/**
 * Messagerie : une seule API pour les deux rôles.
 *
 * <p>Coach et athlète avaient chacun leurs routes, ce qui obligeait à réécrire deux fois toute
 * règle de cloisonnement. Ici un fil est un fil : le serveur décide qui en voit quoi.</p>
 */
@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly http = inject(HttpClient);
  private readonly streamTokens = inject(StreamTokenService);
  private readonly base = `${environment.apiUrl}/me/conversations`;

  /** Total de non-lus, partagé par les pastilles de navigation. */
  readonly unread = signal(0);

  inbox(): Observable<ConversationSummary[]> {
    return this.http.get<ConversationSummary[]>(this.base).pipe(
      tap((list) => this.unread.set(list.reduce((n, c) => n + c.unreadCount, 0))),
    );
  }

  refreshUnread(): Observable<number> {
    return this.http.get<{ count: number }>(`${this.base}/unread-count`).pipe(
      map((r) => r.count),
      tap((n) => this.unread.set(n)),
    );
  }

  recipients(): Observable<Recipient[]> {
    return this.http.get<Recipient[]>(`${this.base}/recipients`);
  }

  /** Ouvre — ou retrouve — un fil. Le serveur revérifie qu'on avait le droit de le demander. */
  open(kind: 'COACH' | 'ATHLETE' | 'GROUP' | 'CLUB', targetId: string): Observable<ConversationSummary> {
    return this.http.post<ConversationSummary>(`${this.base}/open`, { kind, targetId });
  }

  /**
   * Une page du fil. La page 0 porte les messages les plus récents ; les suivantes remontent le
   * temps, et l'ordre reste chronologique à l'intérieur d'une page.
   *
   * Le fil rendait auparavant les cent derniers messages, sans rien derrière ni aucune indication :
   * une conversation qui dure une saison commençait au milieu d'une phrase, et son début était
   * inatteignable.
   */
  messages(conversationId: string, page = 0): Observable<MessagePage> {
    return this.http.get<MessagePage>(`${this.base}/${conversationId}/messages`,
      { params: { page } });
  }

  send(conversationId: string, body: string, workoutId?: string): Observable<Message> {
    return this.http.post<Message>(`${this.base}/${conversationId}/messages`, { body, workoutId });
  }

  markRead(conversationId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${conversationId}/read`, {});
  }

  /**
   * Flux temps réel du fil. `EventSource` ne porte pas d'en-tête : l'authentification passe par un
   * jeton de flux à usage unique, valable une minute, renouvelé à chaque (re)connexion — jamais
   * par le jeton de session, qui vaut une heure sur toute l'API et fuirait dans les journaux
   * d'accès comme dans l'historique du navigateur. L'appelant referme le flux.
   */
  stream(conversationId: string, onMessage: (m: Message) => void): SseStream {
    return this.streamTokens.openSse(`${this.base}/${conversationId}/stream`, {
      message: (ev) => {
        try {
          onMessage(JSON.parse(ev.data) as Message);
        } catch {
          /* événement malformé : ignoré */
        }
      },
    });
  }
}
