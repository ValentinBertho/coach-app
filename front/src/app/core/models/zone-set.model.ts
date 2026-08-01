/**
 * Modèle de zones : un jeu nommé de zones de travail, appliqué à un ou plusieurs athlètes.
 * Un club peut ainsi entretenir plusieurs échelles (route / trail, débutant / confirmé…).
 */
export interface ZoneSet {
  id: string;
  name: string;
  sortOrder: number;
  /** Jeu appliqué aux athlètes qui n'en désignent aucun — non supprimable. */
  isDefault: boolean;
  zoneCount: number;
}

export interface ZoneSetRequest {
  name: string;
  /** Jeu dont on recopie l'échelle à la création (par défaut : le jeu par défaut du club). */
  copyFromSetId?: string | null;
  /** `false` = modèle vide, à construire zone par zone. */
  copyZones?: boolean;
}
