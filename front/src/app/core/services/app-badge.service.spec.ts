import { AppBadgeService } from './app-badge.service';

interface BadgeNav {
  setAppBadge?: jasmine.Spy;
  clearAppBadge?: jasmine.Spy;
}

describe('AppBadgeService', () => {
  let service: AppBadgeService;
  let nav: BadgeNav;

  beforeEach(() => {
    service = new AppBadgeService();
    nav = {
      setAppBadge: jasmine.createSpy('setAppBadge').and.returnValue(Promise.resolve()),
      clearAppBadge: jasmine.createSpy('clearAppBadge').and.returnValue(Promise.resolve()),
    };
    Object.assign(navigator, nav);
  });

  afterEach(() => {
    delete (navigator as unknown as BadgeNav).setAppBadge;
    delete (navigator as unknown as BadgeNav).clearAppBadge;
  });

  it('pose la pastille quand il reste du travail', () => {
    service.set(3);
    expect(nav.setAppBadge).toHaveBeenCalledWith(3);
    expect(nav.clearAppBadge).not.toHaveBeenCalled();
  });

  it('retire la pastille quand la file est vide', () => {
    service.set(0);
    expect(nav.clearAppBadge).toHaveBeenCalled();
    expect(nav.setAppBadge).not.toHaveBeenCalled();
  });

  it('retire la pastille à la déconnexion', () => {
    service.clear();
    expect(nav.clearAppBadge).toHaveBeenCalled();
  });

  /**
   * Un rejet de promesse — permission refusée — ne doit pas remonter : ce serait une erreur
   * non gérée envoyée à Sentry pour un ornement d'icône.
   */
  it('avale un refus du navigateur', async () => {
    nav.setAppBadge!.and.returnValue(Promise.reject(new Error('not allowed')));
    expect(() => service.set(2)).not.toThrow();
    await Promise.resolve();
  });

  /** Navigateur sans l'API (Firefox, Safari hors application installée) : appel sans effet. */
  it('ne fait rien quand le navigateur ne sait pas poser de pastille', () => {
    delete (navigator as unknown as BadgeNav).setAppBadge;
    delete (navigator as unknown as BadgeNav).clearAppBadge;
    expect(() => service.set(5)).not.toThrow();
  });
});
