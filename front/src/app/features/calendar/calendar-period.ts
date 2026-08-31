/**
 * Arithmétique de la période affichée par le calendrier — extraite du composant pour être
 * éprouvable seule, comme {@link ./calendar-selection} et {@link ./calendar-shortcuts}.
 */

/**
 * La grille du coach affiche quatre semaines, et rien d'autre.
 *
 * <p>Le sélecteur de période a été retiré : jour, semaine et mois ont disparu au profit de la
 * seule maille qui serve à programmer. « 4 semaines » n'est pas un mois raccourci — le mois est
 * une grille civile qui commence un mardi, déborde sur deux mois voisins et compte cinq ou six
 * rangées selon le hasard du calendrier ; on n'y compare pas une semaine à la précédente. Quatre
 * semaines glissantes partent toujours du lundi affiché et alignent exactement les quatre totaux
 * hebdomadaires qu'un coach veut confronter : trois de charge, une d'assimilation.</p>
 *
 * <p>Le type subsiste, réduit à une valeur, plutôt que d'être supprimé : il nomme la maille dans
 * les signatures et laisse la porte ouverte si une autre période devait revenir. La vue groupe,
 * elle, montre sept jours — c'est une disposition à elle, pas une période d'ici.</p>
 */
export type CalMode = '4weeks';

/** Nombre de cases rendues. Quatre semaines pleines, du lundi au dimanche. */
export const CELLS_BY_MODE: Record<CalMode, number> = { '4weeks': 28 };

/** Lundi de la semaine contenant `d` (semaine ISO : la semaine commence le lundi). */
export function mondayOf(d: Date): Date {
  const copy = new Date(d);
  const shift = (copy.getDay() + 6) % 7;
  copy.setDate(copy.getDate() - shift);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

/**
 * Première case de la grille : le lundi de la date ancrée.
 *
 * <p>La fenêtre est <b>glissante</b>, jamais recalée sur un premier du mois : c'est ce qui permet
 * aux quatre totaux hebdomadaires de rester comparables d'une navigation à l'autre.</p>
 */
export function gridStartFor(_mode: CalMode, anchor: Date): Date {
  return mondayOf(anchor);
}

/**
 * Nouvelle date d'ancrage après un pas de navigation.
 *
 * <p>Un pas vaut la période affichée : quatre semaines avancent de quatre semaines. Un pas d'une
 * semaine ferait défiler une fenêtre qui se recouvre aux trois quarts, et on perdrait le repère
 * du bloc — c'est justement ce qu'on vient regarder.</p>
 */
export function shiftAnchor(_mode: CalMode, anchor: Date, step: number): Date {
  const d = new Date(anchor);
  d.setDate(d.getDate() + step * 28);
  return d;
}

/** Plage annoncée dans l'en-tête : « 13 juil. – 9 août ». */
export function periodLabelFor(mode: CalMode, anchor: Date): string {
  const start = mondayOf(anchor);
  const end = new Date(start);
  // La dernière case, pas la première du lendemain : quatre semaines couvrent 28 jours, du
  // lundi au dimanche inclus.
  end.setDate(start.getDate() + CELLS_BY_MODE[mode] - 1);
  const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
  return `${fmt.format(start)} – ${fmt.format(end)}`;
}

/**
 * Libellé des sept jours affichés par la <b>grille groupe</b>.
 *
 * <p>Cette grille montre une ligne par athlète sur une semaine : c'est sa disposition, pas une
 * période du sélecteur. Elle a donc son propre libellé, sans quoi l'en-tête annoncerait quatre
 * semaines au-dessus de sept colonnes.</p>
 */
export function groupWeekLabel(anchor: Date): string {
  const start = mondayOf(anchor);
  const end = new Date(start);
  end.setDate(start.getDate() + 6);
  const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
  return `${fmt.format(start)} – ${fmt.format(end)}`;
}
