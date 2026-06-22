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
}
