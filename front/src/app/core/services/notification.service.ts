import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { Observable, Subscription, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AppNotification } from '../models/notification.model';
import { AuthService } from './auth.service';
import { StreamTokenService } from './stream-token.service';

interface Page<T> { content: T[]; }

/** Familles de notifications réglables. Le centre reste alimenté quoi qu'il arrive. */
export type NotificationCategory = 'PROGRAMME' | 'RAPPELS' | 'MESSAGES' | 'SUIVI';

export interface NotificationPreferences {
  emailEnabled: boolean;
  pushEnabled: boolean;
  /**
   * Heure habituelle de séance de l'athlète, « HH:mm ». Ancre le rappel « Ta séance est
   * finie ? », envoyé 2 h après. Vide ou `null` = ce rappel est désactivé.
   */
  usualSessionTime: string | null;
  /** Familles dont le push est coupé. L'envoi remplace la liste, il ne s'y ajoute pas. */
  mutedCategories: NotificationCategory[];
  /** Heures de silence, « HH:mm ». Deux bornes identiques = pas de silence. */
  quietStart: string | null;
  quietEnd: string | null;
  /** Identifiant IANA, ou `null` pour suivre le fuseau du club. */
  timezone: string | null;
}

/** Centre de notifications de l'utilisateur connecté (coach ou athlète). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly streamTokens = inject(StreamTokenService);
  private readonly base = `${environment.apiUrl}/notifications`;
  private source?: EventSource;
  /**
   * Jeton de SESSION pour lequel le flux courant est ouvert — ou en cours d'ouverture. Sert à
   * détecter qu'il a été renouvelé. À ne pas confondre avec le jeton de flux, qui est à usage
   * unique et n'est jamais conservé.
   */
  private sessionToken?: string;
  /** Demande de jeton de flux en vol, annulable si la session tourne entre-temps. */
  private pending?: Subscription;
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
    //
    // `allowSignalWrites` parce que la déconnexion remet le badge à zéro, et qu'Angular refuse
    // par défaut d'écrire dans un signal depuis un effet. Sans ce drapeau, la déconnexion levait
    // NG0600 au moment précis du `unread.set(0)` : le flux était bien fermé — le nettoyage le
    // précède — mais le compteur restait figé. Le badge de la cloche affichait donc encore les
    // non-lues du compte précédent à qui se connectait ensuite sur le même onglet.
    effect(() => {
      const token = this.auth.token();
      if (token) { this.connect(token); } else { this.disconnect(); }
    }, { allowSignalWrites: true });
  }

  /**
   * Ouvre — ou rouvre — le flux SSE.
   *
   * <p>Deux défauts corrigés ici, tous deux silencieux.</p>
   *
   * <p><b>Le jeton n'était jamais rafraîchi.</b> La méthode sortait immédiatement si un flux
   * existait déjà. L'effet se redéclenchait bien à chaque rotation du jeton, mais ne reconnectait
   * pas : l'URL du flux gardait celui de sa première ouverture. Passé le TTL, la première coupure
   * réseau produisait une reconnexion avec un jeton expiré ; le serveur répond 401, et la
   * spécification `EventSource` interdit alors de réessayer. Le flux mourait, le badge de la
   * cloche se figeait — indiscernable de « aucune notification » — et rien dans le code ne s'en
   * apercevait, faute de gestionnaire d'erreur.</p>
   *
   * <p><b>Aucun garde-fou de reconnexion.</b> Le proxy Vercel coupe mal les connexions longues
   * (constaté et documenté dans le runbook) et `EventSource` rouvre tout seul, sans limite. On
   * reprend donc la main : on ferme, on attend un délai croissant, et on rouvre.</p>
   *
   * <p>L'ouverture est désormais en deux temps : on demande d'abord un jeton de flux — une minute,
   * un seul usage — puis on ouvre avec lui. Le jeton de session, qui vaut une heure sur toute
   * l'API, ne va plus dans l'URL. Le détour coûte un aller-retour à l'ouverture, et rien ensuite :
   * le flux, lui, dure.</p>
   */
  private connect(sessionToken: string): void {
    if (typeof EventSource === 'undefined') return;
    // Flux déjà ouvert — ou déjà en cours d'ouverture — pour cette session : rien à faire.
    if (this.sessionToken === sessionToken && (this.source || this.pending)) return;
    // Jeton de session renouvelé : on referme avant de rouvrir, sinon l'ancien flux survit et le
    // serveur garde un émetteur pour rien.
    this.closeStream();
    this.sessionToken = sessionToken;

    this.pending = this.streamTokens.issue('STREAM').subscribe({
      next: (streamToken) => {
        this.pending = undefined;
        // La session a pu tourner — ou se terminer — pendant l'aller-retour.
        if (this.auth.token() !== sessionToken) return;
        this.open(streamToken);
      },
      error: () => {
        this.pending = undefined;
        this.scheduleReconnect();
      },
    });
  }

  /** Ouvre la connexion elle-même, avec un jeton de flux fraîchement émis. */
  private open(streamToken: string): void {
    const src = new EventSource(
      `${this.base}/stream?stream_token=${encodeURIComponent(streamToken)}`);
    src.addEventListener('unread', (e) => {
      const n = Number((e as MessageEvent).data);
      if (!Number.isNaN(n)) this.unread.set(n);
    });
    src.addEventListener('connected', () => { this.retryAttempt = 0; });
    // On reprend la main dès la première erreur : le jeton de cette URL est brûlé, la
    // reconnexion automatique du navigateur n'obtiendrait qu'un 401.
    src.onerror = () => this.scheduleReconnect();
    this.source = src;
  }

  /**
   * Reprogramme une connexion avec un recul exponentiel. On resynchronise le compteur par une
   * requête HTTP classique au passage : elle porte le jeton de session via l'intercepteur, donc
   * elle bénéficie du rafraîchissement automatique — le badge reste juste même si le flux tarde.
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
    // La demande de jeton en vol est abandonnée : son jeton ouvrirait un flux dont plus personne
    // ne veut, et le suivant sera de toute façon frais.
    this.pending?.unsubscribe();
    this.pending = undefined;
    if (this.source) {
      this.source.onerror = null;
      this.source.close();
    }
    this.source = undefined;
    this.sessionToken = undefined;
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
