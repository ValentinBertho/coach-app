import { ThemeService } from './theme.service';

const KEY = 'darilab.theme';

describe('ThemeService', () => {
  beforeEach(() => localStorage.removeItem(KEY));
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
    const service = new ThemeService();
    expect(service.chosen()).toBeFalse();
    expect(service.preference()).toBe('system');
  });

  it('retient le choix, et le dit', () => {
    const service = new ThemeService();
    service.set('light');
    expect(service.chosen()).toBeTrue();
    expect(service.preference()).toBe('light');
    expect(localStorage.getItem(KEY)).toBe('light');
  });

  it('relit un choix antérieur au démarrage', () => {
    localStorage.setItem(KEY, 'dark');
    const service = new ThemeService();
    expect(service.chosen()).toBeTrue();
    expect(service.preference()).toBe('dark');
  });

  /** « Système » est un choix comme un autre : il vaut opt-out du sombre imposé. */
  it('compte « système » comme un choix explicite', () => {
    const service = new ThemeService();
    service.set('system');
    expect(service.chosen()).toBeTrue();
  });

  it('pose l’attribut sur la racine en sombre, et le retire en clair', () => {
    const service = new ThemeService();
    service.set('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(service.effective()).toBe('dark');

    service.set('light');
    expect(document.documentElement.hasAttribute('data-theme')).toBeFalse();
    expect(service.effective()).toBe('light');
  });
});
