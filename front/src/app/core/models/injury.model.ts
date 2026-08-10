/**
 * Blessure déclarée par l'athlète au débrief d'une séance (ou d'une sortie libre).
 *
 * Le curseur de douleur disait *combien* ; il ne disait ni *quoi* ni *où*. Un « 7/10 » sans
 * localisation n'est pas actionnable : le coach ne peut ni adapter la séance suivante, ni
 * décider d'un renvoi. Nommer la blessure est le geste qui rend le signal utilisable.
 *
 * Donnée de santé (RGPD art. 9) : sa collecte est gardée côté serveur par le consentement.
 */

export type InjuryKind =
  | 'SPRAIN' | 'STRAIN' | 'FRACTURE' | 'TENDINITIS'
  | 'CONTRACTURE' | 'SORENESS' | 'BLISTER' | 'OTHER';

export type InjuryArea =
  | 'FOOT' | 'ANKLE' | 'ACHILLES' | 'CALF' | 'SHIN' | 'KNEE'
  | 'HAMSTRING' | 'QUADRICEPS' | 'ADDUCTOR' | 'HIP' | 'GLUTE'
  | 'LOWER_BACK' | 'BACK' | 'ABDOMEN' | 'SHOULDER' | 'OTHER';

export type InjurySide = 'LEFT' | 'RIGHT' | 'BOTH';

export interface Injury {
  kind: InjuryKind;
  area: InjuryArea;
  side: InjurySide | null;
  note: string | null;
}

/**
 * Natures proposées, dans l'ordre où un coureur les rencontre : les trois premières sont celles
 * que Nolio met en tête, les suivantes couvrent le quotidien (tendinite, contracture) avant les
 * bobos bénins. `OTHER` ferme la liste plutôt que de forcer un choix approximatif.
 */
export const INJURY_KIND_LABELS: Record<InjuryKind, string> = {
  SPRAIN: 'Entorse',
  STRAIN: 'Déchirure',
  FRACTURE: 'Fracture',
  TENDINITIS: 'Tendinite',
  CONTRACTURE: 'Contracture',
  SORENESS: 'Courbature',
  BLISTER: 'Ampoule',
  OTHER: 'Autre',
};

export const INJURY_KINDS: InjuryKind[] = [
  'SPRAIN', 'STRAIN', 'FRACTURE', 'TENDINITIS', 'CONTRACTURE', 'SORENESS', 'BLISTER', 'OTHER',
];

/** Zones du corps, du sol vers le haut : l'ordre d'un coureur qui cherche où il a mal. */
export const INJURY_AREA_LABELS: Record<InjuryArea, string> = {
  FOOT: 'Pied',
  ANKLE: 'Cheville',
  ACHILLES: 'Tendon d’Achille',
  CALF: 'Mollet',
  SHIN: 'Tibia',
  KNEE: 'Genou',
  HAMSTRING: 'Ischio-jambier',
  QUADRICEPS: 'Quadriceps',
  ADDUCTOR: 'Adducteur',
  HIP: 'Hanche',
  GLUTE: 'Fessier',
  LOWER_BACK: 'Bas du dos',
  BACK: 'Dos',
  ABDOMEN: 'Abdomen',
  SHOULDER: 'Épaule',
  OTHER: 'Autre',
};

export const INJURY_AREAS: InjuryArea[] = [
  'FOOT', 'ANKLE', 'ACHILLES', 'CALF', 'SHIN', 'KNEE', 'HAMSTRING', 'QUADRICEPS',
  'ADDUCTOR', 'HIP', 'GLUTE', 'LOWER_BACK', 'BACK', 'ABDOMEN', 'SHOULDER', 'OTHER',
];

/** Zones sans latéralité : proposer « gauche / droite » sur un abdomen n'a pas de sens. */
const UNSIDED: InjuryArea[] = ['LOWER_BACK', 'BACK', 'ABDOMEN'];

export function hasSide(area: InjuryArea): boolean {
  return !UNSIDED.includes(area);
}

export const INJURY_SIDE_LABELS: Record<InjurySide, string> = {
  LEFT: 'Gauche',
  RIGHT: 'Droite',
  BOTH: 'Les deux',
};

/** « Entorse — cheville droite », le libellé que lit le coach dans son fil de retours. */
export function injuryLabel(injury: Injury): string {
  const area = INJURY_AREA_LABELS[injury.area] ?? 'Zone';
  const side = injury.side && hasSide(injury.area)
    ? ` ${INJURY_SIDE_LABELS[injury.side].toLowerCase()}` : '';
  return `${INJURY_KIND_LABELS[injury.kind] ?? 'Blessure'} — ${area.toLowerCase()}${side}`;
}
