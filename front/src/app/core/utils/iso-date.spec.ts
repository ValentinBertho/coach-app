import { toIsoDate } from './iso-date';

describe('toIsoDate', () => {
  it('écrit la date locale, pas la date UTC', () => {
    // 12 août 2026, 23 h locales : en UTC on serait déjà le 13 dans une bonne partie de l'Europe.
    expect(toIsoDate(new Date(2026, 7, 12, 23, 30))).toBe('2026-08-12');
  });

  it('complète le mois et le jour à deux chiffres', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
