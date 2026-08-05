import { Route } from '@angular/router';
import { routes } from '../../app.routes';
import {
  clearPendingStravaAuth,
  readPendingStravaAuth,
  savePendingStravaAuth,
} from '../../core/services/strava-pending.storage';

/**
 * Retour d'autorisation Strava : la connexion d'un athlète ne pouvait pas aboutir.
 *
 * <p>L'écran de callback était déclaré comme enfant de `/app`, protégé par `coachGuard`. Un
 * athlète revenant de Strava — donc <b>tous</b> les athlètes, l'intégration étant côté athlète —
 * était renvoyé vers `/athlete/today` avant l'échange du code d'autorisation. Le parcours
 * s'arrêtait là, silencieusement, sans jamais connecter la montre.</p>
 *
 * <p>Ces tests fixent les deux invariants du correctif : l'URL de redirection (figée côté Strava)
 * est servie hors de la coquille coach et sans garde de rôle, et le code mis de côté pendant un
 * détour par la connexion reste rejouable — le second cas du même parcours, quand la redirection
 * retombe hors de la PWA.</p>
 */
describe('retour d’autorisation Strava', () => {
  /** Première route dont le chemin absorbe l'URL donnée — c'est celle que le routeur retient. */
  function firstMatch(url: string): { route: Route; index: number } {
    const index = routes.findIndex((r) => url === r.path || url.startsWith(`${r.path}/`));
    expect(index).withContext(`aucune route pour ${url}`).toBeGreaterThanOrEqual(0);
    return { route: routes[index], index };
  }

  describe('routage', () => {
    it("sert l'URL de redirection historique (/app/strava/callback) hors de la coquille coach", () => {
      const { route, index } = firstMatch('app/strava/callback');
      expect(route.path).toBe('app/strava/callback');
      // Déclarée AVANT `app`, sinon le routeur retiendrait la coquille coach et son garde.
      expect(index).toBeLessThan(routes.findIndex((r) => r.path === 'app'));
    });

    it('ne place aucun garde de rôle sur le callback', () => {
      for (const path of ['app/strava/callback', 'strava/callback']) {
        const { route } = firstMatch(path);
        expect(route.canActivate)
          .withContext(`${path} doit rester atteignable par un athlète`)
          .toBeUndefined();
      }
    });

    it("n'expose plus de callback sous la coquille coach", () => {
      const app = routes.find((r) => r.path === 'app');
      expect(app?.children?.some((c) => c.path === 'strava/callback')).toBeFalsy();
    });
  });

  describe('autorisation mise en attente', () => {
    beforeEach(() => clearPendingStravaAuth());
    afterEach(() => clearPendingStravaAuth());

    it('se relit telle quelle après un détour par la connexion', () => {
      savePendingStravaAuth('code-42', 'athlete.9999.sig');
      expect(readPendingStravaAuth()).toEqual(
        jasmine.objectContaining({ code: 'code-42', state: 'athlete.9999.sig' }),
      );
    });

    it('est écartée passé la durée de vie du state signé (10 min côté serveur)', () => {
      localStorage.setItem(
        'darilab.strava.pending',
        JSON.stringify({ code: 'c', state: 's', storedAt: Date.now() - 11 * 60 * 1000 }),
      );
      expect(readPendingStravaAuth()).toBeNull();
      // Périmée, donc purgée : on ne rejoue jamais un code que Strava a déjà invalidé.
      expect(localStorage.getItem('darilab.strava.pending')).toBeNull();
    });

    it('ignore une entrée corrompue sans lever', () => {
      localStorage.setItem('darilab.strava.pending', '{pas du json');
      expect(readPendingStravaAuth()).toBeNull();
    });
  });
});
