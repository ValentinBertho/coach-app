import { TestBed } from '@angular/core/testing';
import { NetworkStatusService } from './network-status.service';

/**
 * Distinction « hors ligne » / « API injoignable ».
 *
 * <p>Le service ne regardait que `navigator.onLine`. Pendant un redéploiement — Railway redémarre
 * l'instance une à deux minutes à chaque poussée sur `main`, donc plusieurs fois par semaine en
 * bêta — l'utilisateur est « en ligne » : aucun bandeau, et à la place une pluie de toasts
 * « Connexion impossible — vérifie ton réseau » qui accusent son wifi d'une panne serveur.</p>
 */
describe('NetworkStatusService', () => {
  let service: NetworkStatusService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(NetworkStatusService);
  });

  it('ne signale rien tant que tout va bien', () => {
    expect(service.apiUnreachable()).toBe(false);
  });

  it('tolère un échec isolé sans crier à la panne', () => {
    service.reportApiFailure();
    expect(service.apiUnreachable())
      .withContext('une requête annulée ne doit pas afficher de bandeau')
      .toBe(false);
  });

  it('signale l’API injoignable après deux échecs consécutifs', () => {
    service.reportApiFailure();
    service.reportApiFailure();
    expect(service.apiUnreachable()).toBe(true);
  });

  it('retombe dès qu’une réponse arrive', () => {
    service.reportApiFailure();
    service.reportApiFailure();
    service.reportApiSuccess();
    expect(service.apiUnreachable()).toBe(false);
  });

  it('émet reconnected$ au retour du serveur, pour relancer les synchronisations', (done) => {
    service.reportApiFailure();
    service.reportApiFailure();
    service.reconnected$.subscribe(() => done());
    service.reportApiSuccess();
  });

  /** Hors ligne, c'est l'appareil : le bandeau « service indisponible » n'aurait aucun sens. */
  it('ne prétend pas que l’API est en panne quand l’appareil est hors ligne', () => {
    service.reportApiFailure();
    service.reportApiFailure();
    service.online.set(false);
    expect(service.apiUnreachable()).toBe(false);
  });
});
