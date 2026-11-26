/**
 * Nom d'une séance créée vierge : « Séance du jeudi 27 novembre ».
 *
 * <p>Toutes les séances vierges s'appelaient « Séance » ; dans le calendrier comme dans les
 * listes, on ne savait plus laquelle était laquelle tant que le coach ne les avait pas
 * renommées. La date donne au moins un nom distinct, que l'éditeur permet de remplacer.</p>
 */
export function blankSessionTitle(isoDate: string): string {
  const d = new Date(isoDate + 'T00:00:00');
  if (isNaN(d.getTime())) return 'Nouvelle séance';
  const label = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' }).format(d);
  return `Séance du ${label}`;
}
