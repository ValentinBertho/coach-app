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
  /**
   * Heure habituelle de séance de l'athlète, « HH:mm ». Ancre le rappel « Ta séance est
   * finie ? », envoyé 2 h après. Vide ou `null` = ce rappel est désactivé.
   */
  usualSessionTime: string | null;
}

/** Centre de notifications de l'utilisateur connecté (coach ou athlète). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = `${environment.apiUrl}/notifications`;
  private source?: EventSource;
  /** Jeton avec lequel le flux courant a été ouvert : sert à détecter qu'il a été renouvelé. */
  private streamToken?: string;
  private retryTimer?: ReturnType<typeof setTimeout>;
  private retryAttempt = 0;

  /** Premier délai de reconnexion, doublé à chaque échec (2 s, 4 s, 8 s… plafonné). */
  private static readonly RETRY_BASE_MS = 2_000;
  private static readonly RETRY_MAX_MS = 60_000;

  /** Compteur de non-lues, partagé (badge de la cloche). */
  readonly unread = signal(0);

  constructor() {
    // Flux SSE temps réel piloté par l'état d'authentification (connexion ↔ déconnexion).
    // Une rotation du jeton rouvre le flux : cf. `connect`.
    effect(() => {
      const token = this.auth.token();
      if (token) { this.connect(token); } else { this.disconnect(); }
    });
  }

  /**
   * Ouvre — ou rouvre — le flux SSE.
   *
   * <p>Deux défauts corrigés ici, tous deux silencieux.</p>
   *
   * <p><b>Le jeton n'était jamais rafraîchi.</b> La méthode sortait immédiatement si un flux
   * existait déjà. L'effet se redéclenchait bien à chaque rotation du jeton, mais ne reconnectait
   * pas : l'URL du flux gardait l'`access_token` de sa première ouverture. Passé le TTL, la
   * première coupure réseau produisait une reconnexion avec un jeton expiré ; le serveur répond
   * 401, et la spécification `EventSource` interdit alors de réessayer. Le flux mourait, le badge
   * de la cloche se figeait — indiscernable de « aucune notification » — et rien dans le code ne
   * s'en apercevait, faute de gestionnaire d'erreur.</p>
   *
   * <p><b>Aucun garde-fou de reconnexion.</b> Le proxy Vercel coupe mal les connexions longues
   * (constaté et documenté dans le runbook) et `EventSource` rouvre tout seul, sans limite. On
   * reprend donc la main : on ferme, on attend un délai croissant, et on rouvre avec un jeton
   * frais.</p>
   */
  private connect(token: string): void {
    if (typeof EventSource === 'undefined') return;
    // Flux déjà ouvert avec ce jeton-là : rien à faire.
    if (this.source && this.streamToken === token) return;
    // Jeton renouvelé : on referme avant de rouvrir, sinon l'ancien flux survit avec un jeton
    // périmé et le serveur garde un émetteur pour rien.
    this.closeStream();

    const src = new EventSource(`${this.base}/stream?access_token=${encodeURIComponent(token)}`);
    src.addEventListener('unread', (e) => {
      const n = Number((e as MessageEvent).data);
      if (!Number.isNaN(n)) this.unread.set(n);
    });
    src.addEventListener('connected', () => { this.retryAttempt = 0; });
    src.onerror = () => this.scheduleReconnect();
    this.source = src;
    this.streamToken = token;
  }

  /**
   * Reprogramme une connexion avec un recul exponentiel. On resynchronise le compteur par une
   * requête HTTP classique au passage : elle porte le jeton via l'intercepteur, donc elle
   * bénéficie du rafraîchissement automatique — le badge reste juste même si le flux tarde.
   */
  private scheduleReconnect(): void {
    this.closeStream();
    if (!this.auth.token() || this.retryTimer) return;

    const delay = Math.min(
      NotificationService.RETRY_BASE_MS * 2 ** this.retryAttempt,
      NotificationService.RETRY_MAX_MS,
    );
    this.retryAttempt++;
    this.retryTimer = setTimeout(() => {
      this.retryTimer = undefined;
      const token = this.auth.token();
      if (!token) return;
      this.refreshUnread().subscribe({ next: () => {}, error: () => {} });
      this.connect(token);
    }, delay);
  }

  /** Ferme le flux sans toucher au compteur ni au calendrier de reconnexion. */
  private closeStream(): void {
    if (this.source) {
      this.source.onerror = null;
      this.source.close();
    }
    this.source = undefined;
    this.streamToken = undefined;
  }

  private disconnect(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = undefined;
    }
    this.retryAttempt = 0;
    this.closeStream();
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
