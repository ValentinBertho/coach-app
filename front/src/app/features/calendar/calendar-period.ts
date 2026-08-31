/**
 * Arithmétique de la période affichée par le calendrier — extraite du composant pour être
 * éprouvable seule, comme {@link ./calendar-selection} et {@link ./calendar-shortcuts}.
 *
 * <p>Trois règles y vivent ensemble parce qu'elles doivent s'accorder : où commence la grille,
 * combien de cases elle contient, et de combien une flèche la fait avancer. Réparties dans le
 * composant, elles avaient déjà chacune leur `if` sur le mode — et il suffisait d'en oublier un
 * pour obtenir une grille qui n'avance pas de ce qu'elle affiche.</p>
 */

/**
 * Période affichée par la grille : une journée, une semaine, quatre semaines, ou un mois.
 *
 * <p>« 4 semaines » n'est pas un mois raccourci : c'est la maille du <b>bloc d'entraînement</b>.
 * Le mois est une grille civile — il commence un mardi, déborde sur deux mois voisins et compte
 * cinq ou six rangées selon le hasard du calendrier ; on ne peut pas y comparer une semaine à
 * la précédente. Quatre semaines glissantes partent toujours du lundi affiché et alignent
 * exactement les quatre totaux hebdomadaires qu'un coach veut confronter : trois de charge, une
 * d'assimilation.</p>
 */
/**
 * Périodes affichables du calendrier coach.
 *
 * <p>La semaine seule a été retirée : elle montrait trop peu pour construire un bloc, et un coach
 * qui programme raisonne sur plusieurs semaines. Ce qu'elle portait de spécifique — dupliquer une
 * semaine, en générer un mésocycle — vit maintenant dans le menu de la colonne de totaux, qui
 * désigne <b>une</b> semaine précise au lieu de « celle qui est affichée ». La vue groupe garde
 * ses sept jours : c'est une disposition à elle, pas une période de ce sélecteur.</p>
 */
export type CalMode = 'day' | '4weeks' | 'month';

/** Nombre de cases rendues par période. Une seule table, pour que rien ne se contredise. */
export const CELLS_BY_MODE: Record<CalMode, number> = { day: 1, '4weeks': 28, month: 42 };

/** Lundi de la semaine contenant `d` (semaine ISO : la semaine commence le lundi). */
export function mondayOf(d: Date): Date {
  const copy = new Date(d);
  const shift = (copy.getDay() + 6) % 7;
  copy.setDate(copy.getDate() - shift);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

/**
 * Première case de la grille.
 *
 * <p>Semaine et quatre semaines sont <b>glissantes</b> : elles partent du lundi de la date
 * ancrée. Seul le mois se recale sur le premier du mois, parce que c'est une grille civile — et
 * la journée ne se recale pas du tout, elle commence à elle-même.</p>
 */
export function gridStartFor(mode: CalMode, anchor: Date): Date {
  if (mode === 'day') {
    const d = new Date(anchor);
    d.setHours(0, 0, 0, 0);
    return d;
  }
  if (mode === '4weeks') {
    return mondayOf(anchor);
  }
  const first = new Date(anchor);
  first.setDate(1);
  return mondayOf(first);
}

/**
 * Nouvelle date d'ancrage après un pas de navigation.
 *
 * <p>Un pas vaut la période affichée : quatre semaines avancent de quatre semaines. Un pas d'une
 * semaine ferait défiler une fenêtre qui se recouvre aux trois quarts, et on perdrait le repère
 * du bloc — c'est justement ce qu'on vient regarder.</p>
 */
export function shiftAnchor(mode: CalMode, anchor: Date, step: number): Date {
  const d = new Date(anchor);
  if (mode === 'day') d.setDate(d.getDate() + step);
  else if (mode === '4weeks') d.setDate(d.getDate() + step * 28);
  else d.setMonth(d.getMonth() + step);
  return d;
}

/** Libellé de la période, tel qu'affiché dans la barre d'outils. */
export function periodLabelFor(mode: CalMode, anchor: Date): string {
  if (mode === 'day') {
    return new Intl.DateTimeFormat('fr-FR',
      { weekday: 'short', day: 'numeric', month: 'short' }).format(anchor);
  }
  if (mode === 'month') {
    return new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(anchor);
  }
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
 * période du sélecteur — la semaine a été retirée de celui-ci. Elle a donc besoin de son propre
 * libellé, sans quoi l'en-tête annoncerait quatre semaines au-dessus de sept colonnes.</p>
 */
export function groupWeekLabel(anchor: Date): string {
  const start = mondayOf(anchor);
  const end = new Date(start);
  end.setDate(start.getDate() + 6);
  const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
  return `${fmt.format(start)} – ${fmt.format(end)}`;
}
