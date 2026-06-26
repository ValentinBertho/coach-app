import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppNotification } from '../models/notification.model';
import { AuthService } from './auth.service';

interface Page<T> { content: T[]; }

export interface NotificationPreferences {
  emailEnabled: boolean;
  pushEnabled: boolean;
}

/** Centre de notifications de l'utilisateur connecté (coach ou athlète). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = `${environment.apiUrl}/notifications`;
  private source?: EventSource;

  /** Compteur de non-lues, partagé (badge de la cloche). */
  readonly unread = signal(0);

  constructor() {
    // Flux SSE temps réel piloté par l'état d'authentification (connexion ↔ déconnexion).
    effect(() => {
      const token = this.auth.token();
      if (token) { this.connect(token); } else { this.disconnect(); }
    });
  }

  private connect(token: string): void {
    if (this.source || typeof EventSource === 'undefined') return;
    const src = new EventSource(`${this.base}/stream?access_token=${encodeURIComponent(token)}`);
    src.addEventListener('unread', (e) => {
      const n = Number((e as MessageEvent).data);
      if (!Number.isNaN(n)) this.unread.set(n);
    });
    this.source = src;
  }

  private disconnect(): void {
    this.source?.close();
    this.source = undefined;
    this.unread.set(0);
  }

  list(): Observable<AppNotification[]> {
    return this.http.get<Page<AppNotification>>(this.base).pipe(map((p) => p.content ?? []));
  }

  refreshUnread(): Observable<number> {
    return this.http.get<{ count: number }>(`${this.base}/unread-count`).pipe(
      map((r) => r.count),
      tap((c) => this.unread.set(c)),
    );
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/read`, {}).pipe(
      tap(() => this.unread.update((n) => Math.max(0, n - 1))),
    );
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/read-all`, {}).pipe(tap(() => this.unread.set(0)));
  }

  preferences(): Observable<NotificationPreferences> {
    return this.http.get<NotificationPreferences>(`${this.base}/preferences`);
  }

  savePreferences(prefs: Partial<NotificationPreferences>): Observable<NotificationPreferences> {
    return this.http.put<NotificationPreferences>(`${this.base}/preferences`, prefs);
  }
}
