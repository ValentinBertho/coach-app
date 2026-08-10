/**
 * Échelle de **sensation** d'une séance (1 = excellente … 5 = très mauvaise).
 *
 * Distincte du RPE, et c'est tout l'intérêt : le RPE mesure la *difficulté* d'une séance, la
 * sensation dit *comment elle a été vécue*. Une séance de seuil peut être très dure (RPE 8) et
 * excellente ; un footing de récupération peut être facile (RPE 2) et pénible. Le produit ne
 * demandait que le premier, et cherchait le second dans le commentaire libre — quand il y en
 * avait un.
 *
 * Invariant métier : la sensation n'entre **jamais** dans le calcul de forme, qui reste porté par
 * la fatigue et la douleur. Elle est un contexte de lecture, pas une mesure de santé.
 *
 * Cinq crans, comme Nolio : une échelle de visages se choisit d'un coup d'œil et se relit sans
 * apprentissage, là où un curseur 1–10 demande de se situer.
 */
export const FEEL_SCALE = [1, 2, 3, 4, 5] as const;

export type FeelValue = (typeof FEEL_SCALE)[number];

export const FEEL_LABELS: Record<number, string> = {
  1: 'excellente',
  2: 'bonne',
  3: 'correcte',
  4: 'difficile',
  5: 'très mauvaise',
};

/**
 * Couleur du cran : le même vert → rouge que la fatigue et la douleur (`--form-*`), pour que
 * l'athlète n'apprenne pas deux codes de couleur pour deux échelles déclarées côte à côte.
 */
export const FEEL_COLORS: Record<number, string> = {
  1: 'var(--form-green)',
  2: 'var(--form-green)',
  3: 'var(--form-orange)',
  4: 'var(--form-orange)',
  5: 'var(--form-red)',
};

/** Libellé verbal d'une sensation ; chaîne vide si rien n'est déclaré. */
export function feelLabel(value: number | null | undefined): string {
  return value == null ? '' : FEEL_LABELS[value] ?? '';
}
