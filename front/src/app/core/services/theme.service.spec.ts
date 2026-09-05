import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

const KEY = 'darilab.theme';

describe('ThemeService', () => {
  beforeEach(() => localStorage.removeItem(KEY));

  /**
   * Le service passe désormais par l'injecteur : il obtient son document par le jeton `DOCUMENT`
   * plutôt que par le global, pour exister aussi au pré-rendu des fiches coachs — où Node n'a pas
   * de `document`. Un `new ThemeService()` nu n'a plus de contexte d'injection ; c'est
   * `TestBed.inject` qui en fournit un, et il rend le même service qu'à l'exécution.
   *
   * <p>La préférence étant lue à la construction, chaque cas doit obtenir une instance
   * <b>après</b> avoir posé le stockage : d'où cette fabrique plutôt qu'un service partagé.</p>
   */
  function makeService(): ThemeService {
    TestBed.resetTestingModule();
    return TestBed.inject(ThemeService);
  }
  afterEach(() => {
    localStorage.removeItem(KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  /**
   * La distinction « rien choisi » / « a choisi » commande la peau du portail athlète : sans
   * elle, ouvrir le réglage aux athlètes éclaircirait le portail de tous ceux qui n'ont rien
   * demandé, au premier téléphone réglé en clair.
   */
  it('ne se dit pas « choisi » tant que rien n’a été enregistré', () => {
    const service = makeService();
    expect(service.chosen()).toBeFalse();
    expect(service.preference()).toBe('system');
  });

  it('retient le choix, et le dit', () => {
    const service = makeService();
    service.set('light');
    expect(service.chosen()).toBeTrue();
    expect(service.preference()).toBe('light');
    expect(localStorage.getItem(KEY)).toBe('light');
  });

  it('relit un choix antérieur au démarrage', () => {
    localStorage.setItem(KEY, 'dark');
    const service = makeService();
    expect(service.chosen()).toBeTrue();
    expect(service.preference()).toBe('dark');
  });

  /** « Système » est un choix comme un autre : il vaut opt-out du sombre imposé. */
  it('compte « système » comme un choix explicite', () => {
    const service = makeService();
    service.set('system');
    expect(service.chosen()).toBeTrue();
  });

  it('pose l’attribut sur la racine en sombre, et le retire en clair', () => {
    const service = makeService();
    service.set('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(service.effective()).toBe('dark');

    service.set('light');
    expect(document.documentElement.hasAttribute('data-theme')).toBeFalse();
    expect(service.effective()).toBe('light');
  });
});
