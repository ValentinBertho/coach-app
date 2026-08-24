import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Message } from '../models/message.model';
import { AuthService } from './auth.service';

export type ConversationKind = 'ATHLETE_COACH' | 'COACH_COACH' | 'GROUP' | 'CLUB';

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
  private readonly auth = inject(AuthService);
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

  messages(conversationId: string): Observable<Message[]> {
    return this.http.get<Message[]>(`${this.base}/${conversationId}/messages`);
  }

  send(conversationId: string, body: string, workoutId?: string): Observable<Message> {
    return this.http.post<Message>(`${this.base}/${conversationId}/messages`, { body, workoutId });
  }

  markRead(conversationId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${conversationId}/read`, {});
  }

  /**
   * Flux temps réel du fil. Le jeton passe en paramètre d'URL — `EventSource` ne porte pas
   * d'en-tête. L'appelant referme la source.
   */
  stream(conversationId: string, onMessage: (m: Message) => void): EventSource {
    const url = `${this.base}/${conversationId}/stream`
      + `?access_token=${encodeURIComponent(this.auth.token() ?? '')}`;
    const source = new EventSource(url);
    source.addEventListener('message', (ev) => {
      try {
        onMessage(JSON.parse((ev as MessageEvent).data) as Message);
      } catch {
        /* événement malformé : ignoré */
      }
    });
    return source;
  }
}
