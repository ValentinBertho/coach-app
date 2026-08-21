export type PrescriptionRef =
  | 'PCT_LT1' | 'PCT_LT2' | 'PCT_VC' | 'PCT_VMA'
  | 'PCT_PACE_800M' | 'PCT_PACE_1500M' | 'PCT_PACE_3000M' | 'PCT_PACE_5KM'
  | 'PCT_PACE_10KM' | 'PCT_PACE_15KM' | 'PCT_PACE_SEMI' | 'PCT_PACE_MARATHON';

/** Libellés FR des référentiels d'allure (le code reste en anglais, cf. convention README). */
export const PRESCRIPTION_REF_LABELS: Record<PrescriptionRef, string> = {
  PCT_LT2: 'LT2 (seuil lactique)',
  PCT_LT1: 'LT1 (seuil aérobie)',
  PCT_VC: 'VC (vitesse critique)',
  PCT_VMA: 'VMA',
  PCT_PACE_800M: 'Allure 800 m',
  PCT_PACE_1500M: 'Allure 1500 m',
  PCT_PACE_3000M: 'Allure 3000 m',
  PCT_PACE_5KM: 'Allure 5 km',
  PCT_PACE_10KM: 'Allure 10 km',
  PCT_PACE_15KM: 'Allure 15 km',
  PCT_PACE_SEMI: 'Allure semi',
  PCT_PACE_MARATHON: 'Allure marathon',
};

/** Libellé court d'un référentiel, pour les pastilles denses (« % LT2 », « % 5 km »). */
export const PRESCRIPTION_REF_SHORT: Record<PrescriptionRef, string> = {
  PCT_LT2: 'LT2',
  PCT_LT1: 'LT1',
  PCT_VC: 'VC',
  PCT_VMA: 'VMA',
  PCT_PACE_800M: '800 m',
  PCT_PACE_1500M: '1500 m',
  PCT_PACE_3000M: '3000 m',
  PCT_PACE_5KM: '5 km',
  PCT_PACE_10KM: '10 km',
  PCT_PACE_15KM: '15 km',
  PCT_PACE_SEMI: 'semi',
  PCT_PACE_MARATHON: 'marathon',
};

/**
 * Prescription d'intensité d'un bloc. Authoring cible (Z3) : `zoneId` seul (zone à 100 %),
 * la cible allure/FC est lue sur la fiche athlète. Le couple `ref/minPct/maxPct` sert aux
 * snapshots figés et anciens modèles — et, avec `custom`, à la fourchette que le coach écrit
 * lui-même pour un fractionné (« 102–106 % de VC ») qui ne rentre dans aucune zone nommée.
 */
export interface CoursePrescription {
  zoneId?: string | null;
  /**
   * Zone cardio facultative portée en plus de la zone d'allure : les deux échelles étant
   * indépendantes, c'est elle qui fournit la cible FC affichée à côté de l'allure.
   */
  hrZoneId?: string | null;
  ref?: PrescriptionRef | null;
  minPct?: number | null;
  maxPct?: number | null;
  /**
   * La fourchette `ref + %` est voulue par le coach : le serveur la fait primer sur toute zone,
   * y compris celle qu'il déduirait du même couple à la relecture.
   */
  custom?: boolean | null;
}

export interface CourseRecovery {
  type: string;
  durationS?: number | null;
  distanceM?: number | null;
  prescription?: CoursePrescription | null;
}

/** Types de bloc course (liste fermée). La clé reste en anglais (stockage) ; l'UI affiche le libellé FR. */
export type CourseBlockType =
  | 'easy' | 'warmup' | 'cooldown' | 'intervals'
  | 'tempo' | 'threshold' | 'recovery' | 'long' | 'run';

/** Libellés français des types de bloc (affichage UI). Voir convention README : code EN, UI FR. */
export const COURSE_BLOCK_TYPE_LABELS: Record<CourseBlockType, string> = {
  easy: 'Footing',
  warmup: 'Échauffement',
  cooldown: 'Retour au calme',
  intervals: 'Intervalles',
  tempo: 'Tempo',
  threshold: 'Seuil',
  recovery: 'Récupération',
  long: 'Sortie longue',
  run: 'Course',
};

/** Libellé FR d'un type de bloc ; repli capitalisé pour les types hors liste. */
export function courseBlockTypeLabel(type: string | null | undefined): string {
  if (!type) return 'Bloc';
  const known = COURSE_BLOCK_TYPE_LABELS[type as CourseBlockType];
  return known ?? type.charAt(0).toUpperCase() + type.slice(1);
}

export interface CourseBlock {
  id: string;
  type: string;
  reps?: number | null;
  distanceM?: number | null;
  durationS?: number | null;
  prescription?: CoursePrescription | null;
  recovery?: CourseRecovery | null;
  /**
   * Nombre de séries : le bloc entier — ses répétitions et sa récupération — est répété d'autant.
   * `null` ou 1 = une seule série, ce que vaut toute séance écrite avant l'existence des séries.
   */
  sets?: number | null;
  /** Récupération entre deux séries, en général plus longue que celle entre répétitions. */
  setRecovery?: CourseRecovery | null;
  /** Effort perçu visé (RPE 1–10) — propre au contenu de la séance, pas aux zones. */
  rpe?: number | null;
  note?: string | null;
  /** Éducatifs (gammes) attachés au bloc (ids) — ex. échauffement. */
  drillIds?: string[] | null;
}

export interface SessionStructure {
  warmup: CourseBlock[];
  main: CourseBlock[];
  cooldown: CourseBlock[];
}

export interface CourseStructureResponse {
  templateId: string;
  name: string;
  /** Titre affiché à l'athlète (le nom, lui, range la séance dans la bibliothèque). */
  title: string;
  discipline: string | null;
  categoryId: string | null;
  categoryName: string | null;
  favorite: boolean;
  archived: boolean;
  useCount: number;
  /** Encart d'écriture libre du coach. */
  notes: string | null;
  /**
   * Effort perçu attendu pour la séance **entière** (1–10), annoncé par le coach. Distinct du RPE
   * porté par chaque bloc : un 10 × 400 a des blocs à 9 et un échauffement à 3, sans que « la
   * séance » ait un chiffre.
   */
  targetRpe: number | null;
  structure: SessionStructure;
}

export interface CalculatedBlock {
  computable: boolean;
  /** Référentiel legacy (null pour une cible lue depuis une zone). */
  ref?: PrescriptionRef | null;
  basePaceSecPerKm: number | null;
  paceMinSecPerKm: number | null;
  paceMaxSecPerKm: number | null;
  paceMinLabel: string | null;
  paceMaxLabel: string | null;
  speedMinKmh: number | null;
  speedMaxKmh: number | null;
  hrMin: number | null;
  hrMax: number | null;
  rpeMin: number | null;
  rpeMax: number | null;
  estimatedDurationS: number | null;
  estimatedDistanceM: number | null;
  /** Allure estimée depuis le VDOT (pas de seuil mesuré) — à signaler comme « estimée ». */
  paceEstimated?: boolean;
}

/** Un bloc de séance avec ses cibles calculées (et celles de sa récupération). */
export interface CalculatedBlockEntry {
  block: CourseBlock;
  calc: CalculatedBlock | null;
  recoveryCalc: CalculatedBlock | null;
}

/** Séance course entièrement calculée pour un athlète. */
export interface CalculatedSession {
  warmup: CalculatedBlockEntry[];
  main: CalculatedBlockEntry[];
  cooldown: CalculatedBlockEntry[];
  totalDistanceM: number | null;
  totalDurationS: number | null;
}

/** Éducatif (gamme) résolu pour l'affichage dans une séance. */
export interface CourseDrill {
  id: string;
  name: string;
  category: 'TECHNIQUE' | 'AMPLITUDE';
  description: string | null;
  videoUrl: string | null;
}

/** Prescription figée d'une séance planifiée : snapshot des blocs + cibles calculées. */
export interface WorkoutPrescription {
  /** Titre de la séance planifiée, tel que l'athlète le voit. */
  title: string | null;
  snapshot: SessionStructure;
  calculated: CalculatedSession | null;
  /** Éducatifs référencés par les blocs, résolus (nom, vidéo). */
  drills?: CourseDrill[] | null;
}
