import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Message } from '../models/message.model';
import { AuthService } from './auth.service';

/** Une conversation de la boîte de réception coach (agrégat tous athlètes confondus). */
export interface Conversation {
  athleteId: string;
  firstName: string;
  lastName: string;
  lastMessage: string;
  lastSenderRole: 'COACH' | 'ATHLETE' | string;
  lastMessageAt: string;
  unreadCount: number;
}

/** Messagerie : fil coach (scopé club/athlète) et fil athlète (/me/messages). */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  /** Total de non-lus coach, partagé (badge de la navigation latérale). */
  readonly unread = signal(0);

  // --- Boîte de réception coach ---
  conversations(): Observable<Conversation[]> {
    return this.http
      .get<Conversation[]>(`${environment.apiUrl}/clubs/${this.auth.clubId()}/messages/conversations`)
      .pipe(tap((list) => this.unread.set(list.reduce((n, c) => n + c.unreadCount, 0))));
  }

  refreshUnread(): Observable<number> {
    return this.http
      .get<{ count: number }>(`${environment.apiUrl}/clubs/${this.auth.clubId()}/messages/unread-count`)
      .pipe(map((r) => r.count), tap((c) => this.unread.set(c)));
  }

  /** Accusé de lecture à l'ouverture d'un fil : les non-lus de cet athlète repassent à zéro. */
  markThreadRead(athleteId: string): Observable<void> {
    return this.http.post<void>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages/read`, {});
  }

  // Coach
  coachThread(athleteId: string): Observable<Message[]> {
    return this.http.get<Message[]>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages`
    );
  }
  coachSend(athleteId: string, body: string): Observable<Message> {
    return this.http.post<Message>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages`,
      { body }
    );
  }

  // Athlète
  myThread(): Observable<Message[]> {
    return this.http.get<Message[]>(`${environment.apiUrl}/me/messages`);
  }
  mySend(body: string): Observable<Message> {
    return this.http.post<Message>(`${environment.apiUrl}/me/messages`, { body });
  }

  // --- Pièces jointes ---
  coachSendAttachment(athleteId: string, file: File, body?: string): Observable<Message> {
    const form = new FormData();
    form.append('file', file);
    if (body) form.append('body', body);
    return this.http.post<Message>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages/attachment`, form);
  }

  mySendAttachment(file: File, body?: string): Observable<Message> {
    const form = new FormData();
    form.append('file', file);
    if (body) form.append('body', body);
    return this.http.post<Message>(`${environment.apiUrl}/me/messages/attachment`, form);
  }

  /** URL de téléchargement d'une pièce jointe (token en query param pour <img>/<a>). */
  attachmentUrl(athleteId: string | undefined, messageId: string): string {
    const base = athleteId
      ? `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages/${messageId}/attachment`
      : `${environment.apiUrl}/me/messages/${messageId}/attachment`;
    return `${base}?access_token=${encodeURIComponent(this.auth.token() ?? '')}`;
  }

  /**
   * Flux temps réel (SSE) des nouveaux messages. Côté coach si {@code athleteId} fourni,
   * sinon côté athlète. Le token est passé en query param ({@code EventSource} ne porte
   * pas d'en-tête). Retourne la source ouverte ; l'appelant doit la fermer.
   */
  stream(athleteId: string | undefined, onMessage: (m: Message) => void): EventSource {
    const base = athleteId
      ? `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/messages/stream`
      : `${environment.apiUrl}/me/messages/stream`;
    const url = `${base}?access_token=${encodeURIComponent(this.auth.token() ?? '')}`;
    const source = new EventSource(url);
    source.addEventListener('message', (ev) => {
      try {
        onMessage(JSON.parse((ev as MessageEvent).data) as Message);
      } catch {
        /* ignore malformed event */
      }
    });
    return source;
  }
}
