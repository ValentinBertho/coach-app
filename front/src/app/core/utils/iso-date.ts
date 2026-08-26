/**
 * Date locale au format `YYYY-MM-DD`.
 *
 * <p>Volontairement pas `toISOString().slice(0, 10)` : celui-ci convertit d'abord en UTC, si bien
 * qu'un soir d'été en France une séance du 12 renvoie « 2026-08-12 » jusqu'à 22 h puis
 * « 2026-08-13 » — le calendrier saute d'un jour à l'heure où l'athlète rentre de sa sortie. On lit
 * donc les composantes locales, qui sont celles que la personne a sous les yeux.</p>
 */
export function toIsoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
