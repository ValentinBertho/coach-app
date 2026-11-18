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

/** Ce que donnent les règles d'un modèle pour un athlète (aperçu, rien n'est écrit). */
export interface ZoneSetPreview {
  /** L'athlète travaille déjà sur ce modèle (choisi, ou celui par défaut du club). */
  athleteUsesThisSet: boolean;
  references: {
    vcPaceS: number | null;
    vmaPaceS: number | null;
    lt1PaceS: number | null;
    lt2PaceS: number | null;
    fcMax: number | null;
    fcLt2: number | null;
    hrRest: number | null;
  };
  /** Allure en s/km (min = la plus rapide), vitesse en km/h, FC en bpm, %FCmax en %. */
  values: {
    zoneId: string;
    metricTypeId: string;
    metricCode: string;
    min: number;
    max: number;
    /** Valeur saisie à la main ou verrouillée sur la fiche : elle prime sur la règle. */
    manual: boolean;
  }[];
}
