import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Message } from '../models/message.model';
import { AuthService } from './auth.service';

/** Messagerie : fil coach (scopé club/athlète) et fil athlète (/me/messages). */
@Injectable({ providedIn: 'root' })
export class MessageService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

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
