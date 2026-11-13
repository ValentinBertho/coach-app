import { Route } from '@angular/router';
import { routes } from '../../app.routes';
import {
  clearPendingGarminAuth,
  readPendingGarminAuth,
  savePendingGarminAuth,
} from '../../core/services/garmin-pending.storage';

/**
 * Retour du consentement Garmin. Mêmes invariants que pour Strava : l'« OAuth redirect URL »
 * déclarée chez Garmin (`/app/garmin/callback`) doit être servie hors de la coquille coach et
 * sans garde de rôle, sinon un athlète serait renvoyé ailleurs avant l'échange du code.
 */
describe('retour du consentement Garmin', () => {
  function firstMatch(url: string): { route: Route; index: number } {
    const index = routes.findIndex((r) => url === r.path || url.startsWith(`${r.path}/`));
    expect(index).withContext(`aucune route pour ${url}`).toBeGreaterThanOrEqual(0);
    return { route: routes[index], index };
  }

  it("sert l'URL de redirection déclarée chez Garmin hors de la coquille coach", () => {
    const { route, index } = firstMatch('app/garmin/callback');
    expect(route.path).toBe('app/garmin/callback');
    expect(index).toBeLessThan(routes.findIndex((r) => r.path === 'app'));
  });

  it('ne place aucun garde de rôle sur le callback', () => {
    for (const path of ['app/garmin/callback', 'garmin/callback']) {
      expect(firstMatch(path).route.canActivate).withContext(path).toBeUndefined();
    }
  });

  describe('autorisation mise en attente', () => {
    beforeEach(() => clearPendingGarminAuth());
    afterEach(() => clearPendingGarminAuth());

    it('se relit après un détour par la connexion', () => {
      savePendingGarminAuth('code-7', 'athlete.9999.sig');
      expect(readPendingGarminAuth()).toEqual(
        jasmine.objectContaining({ code: 'code-7', state: 'athlete.9999.sig' }),
      );
    });

    it('est écartée au-delà de dix minutes', () => {
      localStorage.setItem(
        'darilab.garmin.pending',
        JSON.stringify({ code: 'c', state: 's', storedAt: Date.now() - 11 * 60 * 1000 }),
      );
      expect(readPendingGarminAuth()).toBeNull();
    });
  });
});
