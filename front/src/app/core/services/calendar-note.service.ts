import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CalendarNote, CalendarNoteRequest } from '../models/calendar-note.model';
import { AuthService } from './auth.service';

/** Notes libres du coach sur le calendrier d'un athlète (CDC §8). Scoping tenant. */
@Injectable({ providedIn: 'root' })
export class CalendarNoteService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/notes`;
  }

  list(athleteId: string, from: string, to: string): Observable<CalendarNote[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<CalendarNote[]>(this.base(athleteId), { params });
  }
  create(athleteId: string, body: CalendarNoteRequest): Observable<CalendarNote> {
    return this.http.post<CalendarNote>(this.base(athleteId), body);
  }
  delete(athleteId: string, noteId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(athleteId)}/${noteId}`);
  }
}
