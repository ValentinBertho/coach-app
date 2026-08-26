import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { AdminService } from './admin.service';

/**
 * Ce que ces cas protègent : des filtres que le front <b>envoie</b> réellement.
 *
 * <p>Le filtre par statut existait côté serveur depuis toujours et n'était jamais transmis : la
 * liste déroulante affichée dans l'écran des comptes ne filtrait donc rien, et personne ne
 * pouvait s'en apercevoir en lisant le composant seul. Un paramètre oublié ne casse aucun
 * écran — il rend juste une réponse fausse, ce qui est pire.</p>
 */
describe('AdminService', () => {
  let service: AdminService;
  let http: HttpTestingController;
  const base = `${environment.apiUrl}/admin`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AdminService],
    });
    service = TestBed.inject(AdminService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('transmet le statut, le club et la vérification dans la recherche de comptes', () => {
    service.users({ status: 'SUSPENDED', clubId: 'club-1', verified: false, q: 'jean' }).subscribe();

    const req = http.expectOne((r) => r.url === `${base}/users`);
    expect(req.request.params.get('status')).toBe('SUSPENDED');
    expect(req.request.params.get('clubId')).toBe('club-1');
    expect(req.request.params.get('verified')).toBe('false');
    expect(req.request.params.get('q')).toBe('jean');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  /** `verified` est un booléen : `false` doit partir, seul `undefined` vaut « indifférent ». */
  it('n’envoie pas le filtre de vérification quand il n’est pas posé', () => {
    service.users({ q: 'jean' }).subscribe();

    const req = http.expectOne((r) => r.url === `${base}/users`);
    expect(req.request.params.has('verified')).toBeFalse();
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('demande une page de clubs assez large pour les sélecteurs', () => {
    // Le sélecteur ne chargeait que la première page : au-delà de 20 clubs, les suivants étaient
    // introuvables et le filtre devenait faux sans rien signaler.
    service.clubs(undefined, 0, undefined, 200).subscribe();

    const req = http.expectOne((r) => r.url === `${base}/clubs`);
    expect(req.request.params.get('size')).toBe('200');
    req.flush({ content: [], page: 0, size: 200, totalElements: 0, totalPages: 0 });
  });

  it('transmet le statut des athlètes', () => {
    service.athletes({ status: 'ARCHIVED', clubId: 'club-9' }).subscribe();

    const req = http.expectOne((r) => r.url === `${base}/athletes`);
    expect(req.request.params.get('status')).toBe('ARCHIVED');
    expect(req.request.params.get('clubId')).toBe('club-9');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('filtre le journal d’audit sur une ressource précise', () => {
    service.audit({ targetId: 'user-7', action: 'USER_DELETED', days: 90 }).subscribe();

    const req = http.expectOne((r) => r.url === `${base}/audit`);
    expect(req.request.params.get('targetId')).toBe('user-7');
    expect(req.request.params.get('action')).toBe('USER_DELETED');
    expect(req.request.params.get('days')).toBe('90');
    req.flush({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('suspend un compte en transmettant le motif', () => {
    service.suspendUser('user-3', 'Compte compromis').subscribe();

    const req = http.expectOne(`${base}/users/user-3/suspend`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Compte compromis' });
    req.flush({});
  });
});
