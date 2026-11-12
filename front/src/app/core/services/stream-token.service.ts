import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, Subscription, map } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Ce à quoi un jeton de flux donne droit. Le serveur refuse un jeton présenté hors de sa portée. */
export type StreamTokenScope = 'STREAM' | 'ATTACHMENT';

/**
 * Jetons dédiés aux deux seules requêtes qui ne peuvent pas porter d'en-tête `Authorization` :
 * l'ouverture d'un flux `EventSource` et l'ouverture d'une pièce jointe dans un onglet.
 *
 * Le jeton de session n'y a plus sa place. Placé dans une URL, il se retrouvait dans les journaux
 * d'accès du relais, dans l'historique du navigateur — `EventSource` comme un onglet créent une
 * entrée de navigation — et dans le `Referer` de la page suivante, alors qu'il vaut une heure sur
 * *toute* l'API. Les jetons émis ici valent une minute, un seul usage, et une seule portée.
 *
 * La demande, elle, est une requête ordinaire : l'intercepteur y pose l'en-tête, et elle profite
 * donc du rafraîchissement automatique du jeton de session.
 */
@Injectable({ providedIn: 'root' })
export class StreamTokenService {
  private readonly http = inject(HttpClient);

  /** Un jeton neuf. À demander juste avant l'usage — il ne survit pas à la minute qui suit. */
  issue(scope: StreamTokenScope): Observable<string> {
    return this.http
      .post<{ token: string; expiresIn: number }>(
        `${environment.apiUrl}/auth/stream-token`, { scope })
      .pipe(map((r) => r.token));
  }

  /**
   * L'URL demandée, complétée d'un jeton neuf. Le paramètre s'ajoute à ceux déjà présents :
   * les routes de flux n'en portent pas aujourd'hui, mais rien ne l'interdit demain.
   */
  urlWithToken(url: string, scope: StreamTokenScope): Observable<string> {
    return this.issue(scope).pipe(
      map((token) => `${url}${url.includes('?') ? '&' : '?'}stream_token=${encodeURIComponent(token)}`),
    );
  }

  /**
   * Ouvre un flux SSE sur cette adresse, avec un jeton neuf, et le rouvre tant que l'appelant ne
   * l'a pas fermé. `listeners` associe un nom d'événement SSE à son gestionnaire.
   */
  openSse(url: string, listeners: Record<string, (event: MessageEvent) => void>): SseStream {
    return new SseStream(this, url, listeners);
  }
}

/**
 * Un flux SSE qui se rouvre tout seul, avec un jeton neuf à chaque fois.
 *
 * Le navigateur sait rouvrir un `EventSource` de lui-même, mais il rejoue *la même URL* — donc
 * un jeton déjà brûlé, donc un 401, et la spécification lui interdit alors de réessayer : le flux
 * mourait pour de bon. On reprend donc la main sur la reconnexion, avec un recul exponentiel — ce
 * qui règle au passage le cas déjà connu du relais qui coupe les connexions longues et faisait
 * rouvrir le navigateur sans aucune limite.
 *
 * `close()` est définitif : le flux ne se rouvrira plus.
 */
export class SseStream {
  private source?: EventSource;
  private pending?: Subscription;
  private timer?: ReturnType<typeof setTimeout>;
  private attempt = 0;
  private closed = false;

  /** Premier délai de reconnexion, doublé à chaque échec (2 s, 4 s, 8 s… plafonné à 60 s). */
  private static readonly RETRY_BASE_MS = 2_000;
  private static readonly RETRY_MAX_MS = 60_000;

  constructor(
    private readonly tokens: StreamTokenService,
    private readonly url: string,
    private readonly listeners: Record<string, (event: MessageEvent) => void>,
  ) {
    this.open();
  }

  /** Ferme le flux pour de bon : plus de reconnexion, plus de demande de jeton en vol. */
  close(): void {
    this.closed = true;
    this.pending?.unsubscribe();
    this.pending = undefined;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = undefined;
    }
    if (this.source) {
      this.source.onerror = null;
      this.source.close();
      this.source = undefined;
    }
  }

  private open(): void {
    if (this.closed || typeof EventSource === 'undefined') return;
    this.pending = this.tokens.urlWithToken(this.url, 'STREAM').subscribe({
      next: (url) => {
        this.pending = undefined;
        if (this.closed) return;
        const source = new EventSource(url);
        for (const [name, handler] of Object.entries(this.listeners)) {
          source.addEventListener(name, (event) => handler(event as MessageEvent));
        }
        // Une ouverture qui aboutit remet le recul à zéro : la prochaine coupure ne doit pas
        // hériter du délai de la précédente.
        source.addEventListener('open', () => { this.attempt = 0; });
        source.onerror = () => this.retry();
        this.source = source;
      },
      error: () => { this.pending = undefined; this.retry(); },
    });
  }

  private retry(): void {
    if (this.closed || this.timer) return;
    if (this.source) {
      this.source.onerror = null;
      this.source.close();
      this.source = undefined;
    }
    const delay = Math.min(
      SseStream.RETRY_BASE_MS * 2 ** this.attempt, SseStream.RETRY_MAX_MS);
    this.attempt++;
    this.timer = setTimeout(() => { this.timer = undefined; this.open(); }, delay);
  }
}
