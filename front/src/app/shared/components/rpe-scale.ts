/**
 * Échelle CR10 de Borg (effort perçu), libellés de référence DARI Lab.
 *
 * Un chiffre nu de 1 à 10 ne veut rien dire sans repère verbal : deux athlètes ne placent pas
 * « 7 » au même endroit. Ces libellés sont la seule source pour toute l'app — saisie athlète
 * comme prescription coach — pour que les deux registres parlent de la même chose.
 *
 * Invariant métier : le RPE mesure la difficulté d'une séance, jamais l'état de forme
 * (qui reste fatigue + douleur).
 */
export const RPE_SCALE = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] as const;

export const RPE_LABELS: Record<number, string> = {
  1: 'très facile',
  2: 'facile',
  3: 'modéré',
  4: 'un peu dur',
  5: 'dur',
  6: 'plus dur',
  7: 'très dur',
  8: 'très très dur',
  9: 'proche du max',
  10: 'maximal',
};

/** Libellé verbal d'une valeur de RPE ; chaîne vide si la valeur n'est pas renseignée. */
export function rpeLabel(value: number | null | undefined): string {
  return value == null ? '' : RPE_LABELS[value] ?? '';
}

/** « 7 — très dur », pour les infobulles et les récapitulatifs. */
export function rpeWithLabel(value: number | null | undefined): string {
  const label = rpeLabel(value);
  return label ? `${value} — ${label}` : '';
}
