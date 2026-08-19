import {
  CELLS_BY_MODE, CalMode, gridStartFor, mondayOf, periodLabelFor, shiftAnchor,
} from './calendar-period';

/** Un mercredi, choisi exprès : le lundi et le premier du mois tombent ailleurs. */
const WEDNESDAY = new Date(2026, 6, 15); // mercredi 15 juillet 2026
const MONDAY_OF_IT = new Date(2026, 6, 13);

/** Dernier jour couvert par la grille, cases comprises. */
function lastDay(mode: CalMode, anchor: Date): Date {
  const d = gridStartFor(mode, anchor);
  d.setDate(d.getDate() + CELLS_BY_MODE[mode] - 1);
  return d;
}

describe('Période du calendrier', () => {
  it('quatre semaines couvrent 28 jours, du lundi au dimanche', () => {
    expect(CELLS_BY_MODE['4weeks']).toBe(28);

    const start = gridStartFor('4weeks', WEDNESDAY);
    expect(start.getTime()).withContext('démarre au lundi de la semaine ancrée')
      .toBe(MONDAY_OF_IT.getTime());

    const end = lastDay('4weeks', WEDNESDAY);
    expect(end.getDay()).withContext('finit un dimanche').toBe(0);
    expect(end.getDate()).toBe(9); // dimanche 9 août 2026
  });

  /**
   * Le point qui distingue « 4 semaines » d'un mois : la grille est <b>glissante</b>. Un mois se
   * recale sur le premier du mois et déborde sur les deux mois voisins ; ici les quatre rangées
   * sont exactement les quatre semaines à comparer.
   */
  it('la grille 4 semaines glisse, là où le mois se recale sur le 1er', () => {
    expect(gridStartFor('4weeks', WEDNESDAY).getTime())
      .withContext('glissante : lundi de la semaine ancrée')
      .toBe(MONDAY_OF_IT.getTime());

    // Juillet 2026 commence un mercredi : la grille du mois démarre donc en juin.
    const monthStart = gridStartFor('month', WEDNESDAY);
    expect(monthStart.getMonth()).withContext('le mois déborde sur le mois précédent').toBe(5);
    expect(monthStart.getDay()).withContext('et commence tout de même un lundi').toBe(1);
  });

  it('chaque mode part d’un lundi, sauf la journée qui commence à elle-même', () => {
    for (const mode of ['week', '4weeks', 'month'] as CalMode[]) {
      expect(gridStartFor(mode, WEDNESDAY).getDay())
        .withContext(`mode ${mode}`).toBe(1);
    }
    expect(gridStartFor('day', WEDNESDAY).getTime()).toBe(new Date(2026, 6, 15).getTime());
  });

  /**
   * Un pas de navigation vaut la période affichée. À un pas d'une semaine, la fenêtre de quatre
   * semaines se recouvrirait aux trois quarts et le repère du bloc serait perdu.
   */
  it('une flèche fait avancer d’exactement ce qui est affiché', () => {
    const next = shiftAnchor('4weeks', WEDNESDAY, 1);
    expect(gridStartFor('4weeks', next).getTime())
      .withContext('la fenêtre suivante commence là où la précédente finissait')
      .toBe(lastDay('4weeks', WEDNESDAY).getTime() + 24 * 3600 * 1000);

    // Et le retour en arrière ramène exactement à la fenêtre de départ.
    const back = shiftAnchor('4weeks', next, -1);
    expect(gridStartFor('4weeks', back).getTime()).toBe(MONDAY_OF_IT.getTime());
  });

  it('les autres modes gardent leur pas', () => {
    expect(shiftAnchor('day', WEDNESDAY, 1).getDate()).toBe(16);
    expect(shiftAnchor('week', WEDNESDAY, 1).getDate()).toBe(22);
    expect(shiftAnchor('month', WEDNESDAY, 1).getMonth()).toBe(7);
  });

  it('le libellé annonce la plage réellement couverte', () => {
    expect(periodLabelFor('4weeks', WEDNESDAY)).toBe('13 juil. – 9 août');
    expect(periodLabelFor('week', WEDNESDAY)).toBe('13 juil. – 19 juil.');
    expect(periodLabelFor('month', WEDNESDAY)).toBe('juillet 2026');
  });

  it('mondayOf normalise à minuit, pour que deux dates du même jour soient égales', () => {
    const withTime = new Date(2026, 6, 15, 23, 47, 12);
    expect(mondayOf(withTime).getTime()).toBe(MONDAY_OF_IT.getTime());
  });
});
