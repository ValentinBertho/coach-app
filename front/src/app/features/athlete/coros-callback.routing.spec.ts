import { Route } from '@angular/router';
import { routes } from '../../app.routes';
import {
  clearPendingCorosAuth,
  readPendingCorosAuth,
  savePendingCorosAuth,
} from '../../core/services/coros-pending.storage';

/**
 * Retour du consentement COROS. Mêmes invariants que pour Strava : l'« OAuth redirect URL »
 * déclarée chez COROS (`/app/coros/callback`) doit être servie hors de la coquille coach et
 * sans garde de rôle, sinon un athlète serait renvoyé ailleurs avant l'échange du code.
 */
describe('retour du consentement COROS', () => {
  function firstMatch(url: string): { route: Route; index: number } {
    const index = routes.findIndex((r) => url === r.path || url.startsWith(`${r.path}/`));
    expect(index).withContext(`aucune route pour ${url}`).toBeGreaterThanOrEqual(0);
    return { route: routes[index], index };
  }

  it("sert l'URL de redirection déclarée chez COROS hors de la coquille coach", () => {
    const { route, index } = firstMatch('app/coros/callback');
    expect(route.path).toBe('app/coros/callback');
    expect(index).toBeLessThan(routes.findIndex((r) => r.path === 'app'));
  });

  it('ne place aucun garde de rôle sur le callback', () => {
    for (const path of ['app/coros/callback', 'coros/callback']) {
      expect(firstMatch(path).route.canActivate).withContext(path).toBeUndefined();
    }
  });

  describe('autorisation mise en attente', () => {
    beforeEach(() => clearPendingCorosAuth());
    afterEach(() => clearPendingCorosAuth());

    it('se relit après un détour par la connexion', () => {
      savePendingCorosAuth('code-7', 'athlete.9999.sig');
      expect(readPendingCorosAuth()).toEqual(
        jasmine.objectContaining({ code: 'code-7', state: 'athlete.9999.sig' }),
      );
    });

    it('est écartée au-delà de dix minutes', () => {
      localStorage.setItem(
        'darilab.coros.pending',
        JSON.stringify({ code: 'c', state: 's', storedAt: Date.now() - 11 * 60 * 1000 }),
      );
      expect(readPendingCorosAuth()).toBeNull();
    });
  });
});
