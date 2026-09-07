import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { StreamTokenService } from './stream-token.service';

/**
 * Le jeton de flux, côté navigateur.
 *
 * Ce qui circulait dans l'URL d'un `EventSource` — et dans celle d'une pièce jointe ouverte dans
 * un onglet — était le **jeton de session** : une heure de validité sur toute l'API, déposée dans
 * les journaux d'accès du relais, dans l'historique de navigation et dans le `Referer` de la page
 * suivante. Ce service le remplace par un jeton demandé juste avant l'usage, qui ne vaut qu'une
 * fois et que pour sa portée.
 */
describe('jetons de flux', () => {
  let service: StreamTokenService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StreamTokenService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('demande un jeton pour la portée voulue', () => {
    let received: string | undefined;
    service.issue('STREAM').subscribe((t) => (received = t));

    const req = http.expectOne(`${environment.apiUrl}/auth/stream-token`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ scope: 'STREAM' });
    req.flush({ token: 'jeton-neuf', expiresIn: 60 });

    expect(received).toBe('jeton-neuf');
  });

  it('accroche le jeton à l\'URL, sans écraser ses paramètres', () => {
    let url: string | undefined;
    service.urlWithToken('/api/me/messages/stream?since=12', 'STREAM').subscribe((u) => (url = u));
    http.expectOne(`${environment.apiUrl}/auth/stream-token`).flush({ token: 'a b', expiresIn: 60 });

    expect(url).toBe('/api/me/messages/stream?since=12&stream_token=a%20b');
  });

  it('n\'utilise jamais le jeton de session dans l\'URL', () => {
    let url: string | undefined;
    service.urlWithToken('/api/notifications/stream', 'ATTACHMENT').subscribe((u) => (url = u));
    const req = http.expectOne(`${environment.apiUrl}/auth/stream-token`);
    req.flush({ token: 'jeton-de-flux', expiresIn: 60 });

    expect(url).not.toContain('access_token');
    expect(url).toContain('stream_token=jeton-de-flux');
  });

  /**
   * Un jeton étant brûlé à la première ouverture, une reconnexion doit en demander un neuf —
   * sinon le flux mourait pour de bon à la première coupure du relais, et le badge de la cloche
   * se figeait sans que rien ne le signale.
   */
  it('redemande un jeton à chaque ouverture', () => {
    const first = service.openSse('/api/notifications/stream', {});
    http.expectOne(`${environment.apiUrl}/auth/stream-token`).flush({ token: 'un', expiresIn: 60 });
    first.close();

    const second = service.openSse('/api/notifications/stream', {});
    http.expectOne(`${environment.apiUrl}/auth/stream-token`).flush({ token: 'deux', expiresIn: 60 });
    second.close();
  });

  /** Fermer avant l'arrivée du jeton ne doit pas ouvrir un flux dont plus personne ne veut. */
  it('abandonne l\'ouverture si l\'appelant referme entre-temps', () => {
    const opened: string[] = [];
    const original = window.EventSource;
    (window as unknown as { EventSource: unknown }).EventSource = class {
      constructor(url: string) { opened.push(url); }
      addEventListener(): void { /* rien */ }
      close(): void { /* rien */ }
    };

    try {
      const stream = service.openSse('/api/notifications/stream', {});
      const req = http.expectOne(`${environment.apiUrl}/auth/stream-token`);
      stream.close();

      expect(req.cancelled).withContext('la demande de jeton est abandonnée').toBeTrue();
      expect(opened).toEqual([]);
    } finally {
      (window as unknown as { EventSource: unknown }).EventSource = original;
    }
  });
});
