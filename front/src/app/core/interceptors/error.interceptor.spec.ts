import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { FeedbackService } from '../services/feedback.service';
import { NetworkStatusService } from '../services/network-status.service';
import { ToastService } from '../services/toast.service';
import { errorInterceptor } from './error.interceptor';

/**
 * Intercepteur d'erreurs — jamais couvert jusqu'ici, alors qu'il porte la boucle
 * refresh/rejeu de toute l'application.
 *
 * <p>Ces cas visent d'abord un risque structurel : l'intercepteur injecte désormais
 * `NetworkStatusService` et `FeedbackService`, et ce dernier injecte `HttpClient` — donc le
 * client HTTP dont l'intercepteur fait partie. Angular résout cette boucle parce que le
 * `inject()` d'un intercepteur fonctionnel a lieu au moment de la requête, mais rien ne le
 * garantit à la compilation : seul un appel réel le prouve. `AuthService`, injecté de la même
 * façon, suit déjà ce schéma.</p>
 */
describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let network: NetworkStatusService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    network = TestBed.inject(NetworkStatusService);
    // Les toasts ne sont pas l'objet du test ; on évite d'empiler des messages parasites.
    spyOn(TestBed.inject(ToastService), 'error');
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('se construit et laisse passer une requête sans erreur (pas de dépendance circulaire)', () => {
    let received: unknown;
    http.get('/api/ping').subscribe((r) => { received = r; });
    httpMock.expectOne('/api/ping').flush({ ok: true });
    expect(received).toEqual({ ok: true } as never);
  });

  it('marque l’API injoignable après deux échecs réseau consécutifs', () => {
    for (let i = 0; i < 2; i++) {
      http.get('/api/ping').subscribe({ next: () => {}, error: () => {} });
      httpMock.expectOne('/api/ping').error(new ProgressEvent('error'), { status: 0 });
    }
    expect(network.apiUnreachable()).toBe(true);
  });

  it('considère un 503 comme une indisponibilité du service, pas comme une erreur métier', () => {
    for (let i = 0; i < 2; i++) {
      http.get('/api/ping').subscribe({ next: () => {}, error: () => {} });
      httpMock.expectOne('/api/ping').flush(null, { status: 503, statusText: 'Unavailable' });
    }
    expect(network.apiUnreachable()).toBe(true);
  });

  it('efface l’état d’indisponibilité dès qu’une réponse arrive', () => {
    for (let i = 0; i < 2; i++) {
      http.get('/api/ping').subscribe({ next: () => {}, error: () => {} });
      httpMock.expectOne('/api/ping').error(new ProgressEvent('error'), { status: 0 });
    }
    http.get('/api/ping').subscribe();
    httpMock.expectOne('/api/ping').flush({ ok: true });
    expect(network.apiUnreachable()).toBe(false);
  });

  /**
   * Le serveur joint un identifiant de corrélation à chaque erreur interne ; il n'était
   * récupéré nulle part. Mémorisé, il part avec le prochain retour de bêta et relie « ça a
   * planté » à une trace précise.
   */
  it('mémorise l’identifiant de corrélation d’une erreur serveur', () => {
    const feedback = TestBed.inject(FeedbackService);
    http.get('/api/boom').subscribe({ next: () => {}, error: () => {} });
    httpMock.expectOne('/api/boom').flush(
      { message: 'Une erreur interne est survenue', correlationId: 'abc-123' },
      { status: 500, statusText: 'Server Error' },
    );
    expect(feedback.lastCorrelationId()).toBe('abc-123');
  });

  /** Un 4xx métier ne doit pas être pris pour une panne d'infrastructure. */
  it('ne signale pas l’API injoignable sur un 403', () => {
    for (let i = 0; i < 2; i++) {
      http.get('/api/nope').subscribe({ next: () => {}, error: () => {} });
      httpMock.expectOne('/api/nope').flush(null, { status: 403, statusText: 'Forbidden' });
    }
    expect(network.apiUnreachable()).toBe(false);
  });

  /**
   * Fin de session — la partie la plus coûteuse de l'intercepteur quand elle se trompe.
   *
   * <p>Le rejeu était couvert par le même `catchError` que le rafraîchissement : dès lors qu'une
   * requête rejouée échouait — un 500, un 404, une coupure d'une demi-seconde en mobilité — la
   * session prenait fin alors que le rafraîchissement venait de réussir. Et cette fin de session
   * appelait `logout()`, donc `POST /auth/logout`, qui <b>révoque tous les jetons du compte sur
   * tous les appareils</b> : le téléphone qui perd le réseau déconnectait l'ordinateur.</p>
   */
  describe('401 et fin de session', () => {
    let auth: AuthService;

    /** Session complète en place, comme après une connexion réussie. */
    function signIn(): void {
      localStorage.setItem('darilab.accessToken', 'jeton-expire');
      localStorage.setItem('darilab.refreshToken', 'refresh-valide');
      auth.token.set('jeton-expire');
    }

    beforeEach(() => {
      auth = TestBed.inject(AuthService);
      signIn();
    });

    it('rafraîchit puis rejoue la requête refusée', () => {
      let received: unknown;
      http.get('/api/me/today').subscribe({ next: (r) => { received = r; }, error: () => {} });

      httpMock.expectOne('/api/me/today').flush(null, { status: 401, statusText: 'Unauthorized' });
      httpMock.expectOne((r) => r.url.endsWith('/auth/refresh'))
        .flush({ accessToken: 'neuf', refreshToken: 'neuf-r', user: { id: 'u1' } });
      httpMock.expectOne('/api/me/today').flush({ ok: true });

      expect(received).toEqual({ ok: true } as never);
      expect(auth.token()).toBe('neuf');
    });

    /** Le cœur du correctif : le rejeu échoue, mais la session reste ouverte. */
    it('garde la session quand c’est la requête rejouée qui échoue', () => {
      http.get('/api/me/today').subscribe({ next: () => {}, error: () => {} });

      httpMock.expectOne('/api/me/today').flush(null, { status: 401, statusText: 'Unauthorized' });
      httpMock.expectOne((r) => r.url.endsWith('/auth/refresh'))
        .flush({ accessToken: 'neuf', refreshToken: 'neuf-r', user: { id: 'u1' } });
      httpMock.expectOne('/api/me/today').error(new ProgressEvent('error'), { status: 0 });

      expect(auth.isAuthenticated()).toBe(true);
      expect(auth.token()).toBe('neuf');
    });

    /** Réseau coupé pendant le rafraîchissement : on ne sait rien des jetons, on ne jette rien. */
    it('garde la session quand le rafraîchissement n’aboutit pas faute de réseau', () => {
      http.get('/api/me/today').subscribe({ next: () => {}, error: () => {} });

      httpMock.expectOne('/api/me/today').flush(null, { status: 401, statusText: 'Unauthorized' });
      httpMock.expectOne((r) => r.url.endsWith('/auth/refresh'))
        .error(new ProgressEvent('error'), { status: 0 });

      expect(auth.isAuthenticated()).toBe(true);
    });

    it('met fin à la session quand le serveur refuse explicitement le rafraîchissement', () => {
      http.get('/api/me/today').subscribe({ next: () => {}, error: () => {} });

      httpMock.expectOne('/api/me/today').flush(null, { status: 401, statusText: 'Unauthorized' });
      httpMock.expectOne((r) => r.url.endsWith('/auth/refresh'))
        .flush(null, { status: 401, statusText: 'Unauthorized' });

      expect(auth.isAuthenticated()).toBe(false);
      // Aucune révocation serveur : les autres appareils du compte n'ont rien demandé.
      httpMock.expectNone((r) => r.url.endsWith('/auth/logout'));
    });

    /**
     * La tuyauterie push se réenregistre toute seule au démarrage. Son échec ne doit ni parler
     * à l'utilisateur, ni toucher à la session — sur l'écran de connexion, il affichait
     * « Session expirée, reconnecte-toi » avant même la saisie du mot de passe.
     */
    it('reste muet et sans effet sur un échec des routes push', () => {
      const toast = TestBed.inject(ToastService);
      http.post('/api/push/subscribe', {}).subscribe({ next: () => {}, error: () => {} });
      httpMock.expectOne('/api/push/subscribe').flush(null, { status: 500, statusText: 'Server Error' });

      expect(toast.error).not.toHaveBeenCalled();
      expect(auth.isAuthenticated()).toBe(true);
    });
  });
});
