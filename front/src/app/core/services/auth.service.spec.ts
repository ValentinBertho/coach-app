import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthService } from './auth.service';
import { FEEDBACK_QUEUE_KEY, feedbackQueuePending, writeFeedbackQueue } from './feedback-queue.storage';

const ACCESS_KEY = 'darilab.accessToken';
const REFRESH_KEY = 'darilab.refreshToken';
const USER_KEY = 'darilab.user';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  afterEach(() => localStorage.clear());

  /** Instancie le service APRÈS avoir posé le stockage : les signals se lisent au constructeur. */
  function create(): void {
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  }

  describe('logout', () => {
    beforeEach(() => {
      localStorage.setItem(ACCESS_KEY, 'jeton-a');
      localStorage.setItem(REFRESH_KEY, 'refresh-a');
      localStorage.setItem(USER_KEY, JSON.stringify({ id: 'a', email: 'a@darilab.app' }));
      create();
    });

    it('purge les jetons et l’utilisateur', () => {
      service.logout();
      http.expectOne((r) => r.url.endsWith('/auth/logout')).flush({});

      expect(localStorage.getItem(ACCESS_KEY)).toBeNull();
      expect(localStorage.getItem(REFRESH_KEY)).toBeNull();
      expect(localStorage.getItem(USER_KEY)).toBeNull();
      expect(service.isAuthenticated()).toBeFalse();
    });

    it('purge la file de retours hors ligne (données de santé)', () => {
      // Retours de l'athlète A restés en file : sur un appareil partagé, ils étaient rejoués
      // par le compte suivant, sous SON jeton.
      writeFeedbackQueue([{ workoutId: 'w1', body: { rpe: 8, pain: 4, fatigue: 3 } }]);
      expect(feedbackQueuePending()).toBe(1);

      service.logout();
      http.expectOne((r) => r.url.endsWith('/auth/logout')).flush({});

      expect(localStorage.getItem(FEEDBACK_QUEUE_KEY)).toBeNull();
      expect(feedbackQueuePending()).toBe(0);
    });

    /**
     * Le service worker met désormais en cache les réponses du tableau de bord coach — dont la
     * file de retours, qui porte douleur et blessures déclarées. Sur un appareil partagé, le
     * compte suivant serait servi depuis ces réponses-là. La purge existait ; elle devient un
     * invariant tenu par un test, puisqu'on s'appuie dessus pour cacher de la donnée de santé.
     */
    it('vide les caches du service worker (données de santé mises en cache)', async () => {
      // `caches` n'est pas assignable (accesseur en lecture seule sur window) : on espionne
      // l'objet réel, que le contexte sécurisé de localhost fournit.
      const deleted: string[] = [];
      spyOn(caches, 'keys').and.returnValue(
        Promise.resolve(['ngsw:1:data:dynamic:coach-day', 'ngsw:1:assets']));
      spyOn(caches, 'delete').and.callFake((key: string) => {
        deleted.push(key);
        return Promise.resolve(true);
      });

      service.logout();
      http.expectOne((r) => r.url.endsWith('/auth/logout')).flush({});
      // La purge est asynchrone et volontairement non attendue par `logout` (la déconnexion ne
      // dépend jamais du réseau ni du stockage) : on laisse la file de microtâches se vider.
      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(deleted).toContain('ngsw:1:data:dynamic:coach-day');
      expect(deleted).toContain('ngsw:1:assets');
    });
  });

  it('démarre sans utilisateur quand le stockage est corrompu, au lieu de jeter', () => {
    localStorage.setItem(USER_KEY, 'pas du JSON');

    // Sans le try/catch, l'exception remontait à l'instanciation d'un service `providedIn: root`
    // et l'application affichait un écran blanc, sans moyen de se déconnecter.
    expect(() => create()).not.toThrow();
    expect(service.currentUser()).toBeNull();
    expect(localStorage.getItem(USER_KEY)).toBeNull();
  });
});
