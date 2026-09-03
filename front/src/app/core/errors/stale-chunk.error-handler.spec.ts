import { ErrorHandler } from '@angular/core';
import { StaleChunkErrorHandler } from './stale-chunk.error-handler';

/**
 * Un écran dont le fichier a disparu du serveur recharge la page ; tout le reste passe au
 * gestionnaire en aval. Le rechargement lui-même est espionné : un test ne peut pas laisser le
 * navigateur de Karma se recharger sous ses pieds.
 */
describe('stale-chunk — rechargement après un déploiement', () => {
  let delegate: jasmine.SpyObj<ErrorHandler>;
  let handler: StaleChunkErrorHandler;
  let reload: jasmine.Spy;

  const KEY = 'darilab.stale-chunk-reload';

  beforeEach(() => {
    sessionStorage.removeItem(KEY);
    delegate = jasmine.createSpyObj<ErrorHandler>('ErrorHandler', ['handleError']);
    reload = jasmine.createSpy('reload');
    handler = new StaleChunkErrorHandler(delegate, reload);
  });

  afterEach(() => sessionStorage.removeItem(KEY));

  /** Les libellés relevés en production, moteur par moteur. */
  const staleChunkMessages = [
    'Failed to fetch dynamically imported module: https://www.darilab.app/chunk-B6CRKH3L.js',
    'error loading dynamically imported module',
    'Importing a module script failed.',
    'Failed to load module script: Expected a JavaScript-or-Wasm module script but the server '
      + 'responded with a MIME type of "text/html".',
  ];

  for (const message of staleChunkMessages) {
    it(`recharge sur « ${message.slice(0, 40)}… »`, () => {
      handler.handleError(new Error(message));

      expect(reload).toHaveBeenCalledTimes(1);
      // L'erreur est traitée : la remonter en plus n'apprendrait rien, la page part déjà.
      expect(delegate.handleError).not.toHaveBeenCalled();
    });
  }

  it('laisse passer une erreur ordinaire au gestionnaire en aval', () => {
    const boom = new Error('Cannot read properties of undefined');
    handler.handleError(boom);

    expect(reload).not.toHaveBeenCalled();
    expect(delegate.handleError).toHaveBeenCalledOnceWith(boom);
  });

  /**
   * Le garde-fou : si le rechargement ne règle rien, la cause n'est pas la version périmée.
   * Une page qui se recharge en boucle serait pire que le défaut d'origine.
   */
  it('ne recharge pas deux fois de suite', () => {
    handler.handleError(new Error('Failed to fetch dynamically imported module: /chunk-A.js'));
    const second = new Error('Failed to fetch dynamically imported module: /chunk-B.js');
    handler.handleError(second);

    expect(reload).toHaveBeenCalledTimes(1);
    // Le second échec n'est pas avalé : il part au gestionnaire en aval, donc à Sentry.
    expect(delegate.handleError).toHaveBeenCalledOnceWith(second);
  });

  /** Passé le délai, un déploiement ultérieur est rattrapé comme le premier. */
  it('recharge à nouveau une fois le délai écoulé', () => {
    sessionStorage.setItem(KEY, String(Date.now() - 11 * 60 * 1000));

    handler.handleError(new Error('Failed to fetch dynamically imported module: /chunk-C.js'));

    expect(reload).toHaveBeenCalledTimes(1);
  });
});
