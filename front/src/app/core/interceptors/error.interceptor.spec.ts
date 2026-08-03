import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
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
});
