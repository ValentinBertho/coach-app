import { formatDuration, formatMinSec, parseDuration, parseMinSec } from './duration';

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

/**
 * Volumes d'une séance : l'unité est choisie à côté du champ, donc un nombre nu s'y lit dans
 * cette unité. Seule la forme sexagésimale est sans ambiguïté — et c'est celle qui manquait :
 * une récup de 1'30 n'avait d'autre écriture que « 90 » en secondes.
 */
describe('parseMinSec', () => {
  it('lit « 1:30 » comme 90 secondes', () => {
    expect(parseMinSec('1:30')).toBe(90);
  });

  it('accepte l’apostrophe des coureurs — 1\'30 et 1’30', () => {
    expect(parseMinSec("1'30")).toBe(90);
    expect(parseMinSec('1’30')).toBe(90);
  });

  it('accepte le séparateur seul, pour ne pas casser la saisie en cours', () => {
    expect(parseMinSec('2:')).toBe(120);
  });

  it('rend null sur un nombre nu : c’est l’unité du champ qui décide', () => {
    expect(parseMinSec('90')).toBeNull();
    expect(parseMinSec('')).toBeNull();
  });

  it('rejette des secondes qui débordent plutôt que de les interpréter', () => {
    expect(parseMinSec('1:75')).toBeNull();
    expect(parseMinSec('1:2:3')).toBeNull();
    expect(parseMinSec('a:30')).toBeNull();
  });

  it('formate en m:ss et fait l’aller-retour', () => {
    expect(formatMinSec(90)).toBe('1:30');
    expect(formatMinSec(600)).toBe('10:00');
    expect(parseMinSec(formatMinSec(95))).toBe(95);
  });
});
