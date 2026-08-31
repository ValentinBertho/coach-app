import { CELLS_BY_MODE, gridStartFor, groupWeekLabel, mondayOf, periodLabelFor, shiftAnchor } from './calendar-period';

/**
 * Le calendrier coach n'affiche plus qu'une période : quatre semaines glissantes.
 *
 * <p>Jour, semaine et mois ont été retirés — un coach programme sur un bloc, pas sur une journée
 * ni sur une grille civile. Ce que la semaine portait de spécifique (dupliquer, générer un
 * mésocycle) vit dans le menu de la colonne de totaux, qui désigne <b>une</b> semaine précise.</p>
 */
describe('calendar-period — quatre semaines, et rien d’autre', () => {
  // Mercredi 15 juillet 2026 ; son lundi est le 13.
  const WEDNESDAY = new Date(2026, 6, 15);
  const MONDAY = new Date(2026, 6, 13);

  it('n’offre plus qu’une période', () => {
    expect(Object.keys(CELLS_BY_MODE)).toEqual(['4weeks']);
    expect(CELLS_BY_MODE['4weeks']).toBe(28);
  });

  it('la fenêtre commence au lundi de la date ancrée, jamais au premier du mois', () => {
    // C'est ce qui rend les quatre totaux hebdomadaires comparables d'une navigation à l'autre,
    // et ce qui permet à « la phrase de la semaine » d'y désigner la première semaine affichée.
    expect(gridStartFor('4weeks', WEDNESDAY).getTime()).toBe(MONDAY.getTime());
    expect(gridStartFor('4weeks', WEDNESDAY).getDay()).toBe(1);
  });

  it('un pas avance de ce qui est affiché, sans recouvrement', () => {
    const next = shiftAnchor('4weeks', WEDNESDAY, 1);
    const lastDay = new Date(gridStartFor('4weeks', WEDNESDAY));
    lastDay.setDate(lastDay.getDate() + 27);
    expect(gridStartFor('4weeks', next).getTime())
      .withContext('la fenêtre suivante commence là où la précédente finissait')
      .toBe(lastDay.getTime() + 24 * 3600 * 1000);

    // Et le retour en arrière ramène exactement à la fenêtre de départ.
    expect(gridStartFor('4weeks', shiftAnchor('4weeks', next, -1)).getTime()).toBe(MONDAY.getTime());
  });

  it('le libellé annonce les 28 jours réellement couverts', () => {
    expect(periodLabelFor('4weeks', WEDNESDAY)).toBe('13 juil. – 9 août');
  });

  it('la grille groupe garde son libellé de sept jours', () => {
    // Sa disposition (une ligne par athlète × 7 jours) n'est pas une période du sélecteur :
    // sans libellé propre, l'en-tête annoncerait quatre semaines au-dessus d'une seule.
    expect(groupWeekLabel(WEDNESDAY)).toBe('13 juil. – 19 juil.');
  });

  it('mondayOf normalise à minuit, pour que deux dates du même jour soient égales', () => {
    expect(mondayOf(new Date(2026, 6, 15, 23, 47, 12)).getTime()).toBe(MONDAY.getTime());
  });
});
