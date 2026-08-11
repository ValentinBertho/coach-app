import { CalendarNote } from '../models/calendar-note.model';
import { coversDate, currentCycle, cyclePosition, cyclesOverlapping, daysLeft } from './cycle';

function note(id: string, noteDate: string, endDate: string | null, text = id): CalendarNote {
  return { id, athleteId: 'a', noteDate, endDate, text };
}

/**
 * Situer l'athlète dans son cycle.
 *
 * <p>Le coach pose des périodes sur le calendrier ; l'athlète ne voyait que des séances. Ces
 * tests fixent la lecture qu'on en fait — et surtout ses bornes : une note d'un seul jour est une
 * note de travail du coach, pas un cycle, et elle ne doit jamais remonter jusqu'à l'athlète.</p>
 */
describe('cycle — situer une date dans un cycle', () => {
  const bloc = note('c1', '2026-08-03', '2026-08-30', 'Bloc spécifique');

  it('reconnaît les bornes du cycle, incluses', () => {
    expect(coversDate(bloc, '2026-08-03')).toBeTrue();
    expect(coversDate(bloc, '2026-08-30')).toBeTrue();
    expect(coversDate(bloc, '2026-08-02')).toBeFalse();
    expect(coversDate(bloc, '2026-08-31')).toBeFalse();
  });

  /** Une note d'un seul jour est privée au coach : elle n'est un cycle pour personne. */
  it('ne prend jamais une note d’un jour pour un cycle', () => {
    const perso = note('c2', '2026-08-10', null, 'appeler le kiné');
    expect(coversDate(perso, '2026-08-10')).toBeFalse();
    expect(currentCycle([perso], '2026-08-10')).toBeNull();
    expect(cyclesOverlapping([perso], '2026-08-01', '2026-08-31')).toEqual([]);
  });

  it('compte les semaines depuis le premier jour', () => {
    expect(cyclePosition(bloc, '2026-08-03')).toEqual({ week: 1, total: 4 });
    expect(cyclePosition(bloc, '2026-08-09')).toEqual({ week: 1, total: 4 });
    expect(cyclePosition(bloc, '2026-08-10')).toEqual({ week: 2, total: 4 });
    expect(cyclePosition(bloc, '2026-08-30')).toEqual({ week: 4, total: 4 });
  });

  /** Un cycle de dix jours occupe deux semaines : on arrondit au supérieur, jamais l'inverse. */
  it('arrondit au supérieur un cycle qui ne tombe pas juste', () => {
    expect(cyclePosition(note('c3', '2026-09-01', '2026-09-10'), '2026-09-09'))
      .toEqual({ week: 2, total: 2 });
  });

  it('ne situe pas une date hors du cycle', () => {
    expect(cyclePosition(bloc, '2026-09-01')).toBeNull();
    expect(daysLeft(bloc, '2026-09-01')).toBeNull();
  });

  it('compte les jours restants, dernier jour compris', () => {
    expect(daysLeft(bloc, '2026-08-30')).toBe(0);
    expect(daysLeft(bloc, '2026-08-29')).toBe(1);
  });

  /**
   * Rien n'interdit au coach de faire démarrer un affûtage à l'intérieur d'un bloc plus long.
   * C'est le plus récemment commencé qui décrit la phase courante.
   */
  it('retient le cycle le plus récemment commencé quand deux se chevauchent', () => {
    const affutage = note('c4', '2026-08-24', '2026-08-30', 'Affûtage');
    expect(currentCycle([bloc, affutage], '2026-08-26')?.text).toBe('Affûtage');
    expect(currentCycle([bloc, affutage], '2026-08-20')?.text).toBe('Bloc spécifique');
  });

  it('liste les cycles chevauchant une plage, du plus ancien au plus récent', () => {
    const suivant = note('c5', '2026-08-31', '2026-09-13', 'Affûtage');
    const passe = note('c6', '2026-07-01', '2026-07-20', 'Reprise');
    const found = cyclesOverlapping([suivant, bloc, passe], '2026-08-01', '2026-08-31');
    expect(found.map((c) => c.text)).toEqual(['Bloc spécifique', 'Affûtage']);
  });
});
