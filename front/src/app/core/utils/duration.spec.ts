import { formatDuration, parseDuration } from './duration';

describe('duration', () => {
  it('lit un chrono hh:mm:ss', () => {
    expect(parseDuration('3:29:59')).toBe(3 * 3600 + 29 * 60 + 59);
  });

  it('lit un chrono mm:ss sous l’heure — « 42:30 » est un 10 km, pas 42 heures', () => {
    expect(parseDuration('42:30')).toBe(42 * 60 + 30);
  });

  it('lit un nombre seul comme des minutes', () => {
    expect(parseDuration('45')).toBe(45 * 60);
  });

  it('rejette une saisie illisible plutôt que de l’interpréter', () => {
    expect(parseDuration('3:75')).toBeNull();
    expect(parseDuration('3::30')).toBeNull();
    expect(parseDuration('abc')).toBeNull();
    expect(parseDuration('-1:00')).toBeNull();
  });

  it('rend null sur une saisie vide', () => {
    expect(parseDuration('')).toBeNull();
    expect(parseDuration(null)).toBeNull();
  });

  it('formate en h:mm:ss, et en m:ss sous l’heure', () => {
    expect(formatDuration(12599)).toBe('3:29:59');
    expect(formatDuration(2550)).toBe('42:30');
  });

  it('fait l’aller-retour sans perdre les secondes', () => {
    const seconds = 3 * 3600 + 29 * 60 + 59;
    expect(parseDuration(formatDuration(seconds))).toBe(seconds);
  });
});
