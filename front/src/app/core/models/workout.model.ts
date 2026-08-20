import { Injury } from './injury.model';

export type WorkoutType =
  | 'ENDURANCE' | 'RECOVERY' | 'TEMPO' | 'THRESHOLD' | 'INTERVALS'
  | 'LONG_RUN' | 'RACE' | 'STRENGTH' | 'CROSS_TRAINING' | 'REST';

export type WorkoutStatus = 'PLANNED' | 'COMPLETED' | 'PARTIAL' | 'MISSED';
export type IntensityZone = 'Z1' | 'Z2' | 'Z3' | 'Z4' | 'Z5';
export type WorkoutStepType = 'WARMUP' | 'STEADY' | 'REPETITION' | 'RECOVERY' | 'COOLDOWN';

export interface WorkoutStep {
  id?: string;
  orderIndex?: number;
  stepType: WorkoutStepType;
  repetitions: number;
  zone: IntensityZone | null;
  distanceM: number | null;
  durationS: number | null;
  notes: string | null;
}

/**
 * Motif d'une séance non faite. L'athlète n'avait auparavant que « réalisée » et
 * « partiellement » : pour une séance qu'il n'avait pas faite, le seul geste possible était de
 * ne rien faire, et son silence ressortait en alerte « séance manquée » côté coach.
 */
export type MissedReason = 'UNEXPECTED' | 'NO_TIME' | 'WEATHER' | 'HEALTH' | 'OTHER';

export interface Workout {
  id: string;
  athleteId: string;
  scheduledDate: string;
  type: WorkoutType;
  status: WorkoutStatus;
  title: string;
  notes: string | null;
  targetDistanceM: number | null;
  targetDurationS: number | null;
  /** Durée réellement effectuée sur une séance écourtée ; null si menée à son terme. */
  actualDurationS: number | null;
  /** Motif renseigné quand l'athlète a déclaré la séance non faite. */
  missedReason: MissedReason | null;
  /**
   * Effort perçu **attendu** pour la séance entière (1–10), annoncé par le coach à la création.
   * Nul quand rien n'a été annoncé — l'interface se tait plutôt que d'afficher un zéro.
   */
  targetRpe: number | null;
  /** Effort perçu **ressenti**, saisi par l'athlète. C'est l'écart entre les deux qui informe. */
  rpe: number | null;
  /** Fatigue et douleur déclarées (données de santé, absentes sans consentement actif). */
  fatigue: number | null;
  pain: number | null;
  /**
   * Sensation générale de la séance (1 = excellente … 5 = très mauvaise).
   * Jamais l'effort : une séance peut être très dure et très bien vécue.
   */
  feel: number | null;
  /** Blessures nommées au débrief ; liste vide si aucune. */
  injuries: Injury[];
  athleteComment: string | null;
  /** Retour du coach sur la séance réalisée (feedback in situ), visible par l'athlète. */
  coachComment: string | null;
  /** Quand l'athlète a ouvert ce mot. Nul = non lu, donc encore remonté sur « Aujourd'hui ». */
  coachCommentReadAt: string | null;
  coachCommentAt: string | null;
  /**
   * Date du « vu 👏 » du coach ; null tant qu'il n'a pas eu lieu.
   *
   * Elle reste sur la séance, là où la notification est passée et oubliée : c'est tout l'objet du
   * geste — que l'athlète retrouve la trace qu'on a regardé ce qu'il avait écrit.
   */
  coachAcknowledgedAt: string | null;
  /**
   * L'athlète a déplacé cette séance lui-même. Renvoyé par l'API depuis toujours, mais absent du
   * modèle : le coach n'avait donc aucun signal, ni notification, quand sa semaine était
   * réorganisée.
   */
  movedByAthlete: boolean;
  /** Date à laquelle la séance était initialement prévue, si elle a été déplacée. */
  originalDate: string | null;
  /** Charge prévue en UA (sRPE appliqué à la prescription) — total hebdo du calendrier. */
  plannedLoadUa: number | null;
  /** Ordre d'affichage au sein d'un même jour (glisser-déposer intra-jour). */
  orderIndex: number;
  steps: WorkoutStep[];
}

export interface WorkoutStepRequest {
  stepType: WorkoutStepType;
  repetitions: number;
  zone?: IntensityZone | null;
  distanceM?: number | null;
  durationS?: number | null;
  notes?: string | null;
}

export interface WorkoutRequest {
  scheduledDate: string;
  type: WorkoutType;
  title: string;
  notes?: string | null;
  targetDistanceM?: number | null;
  targetDurationS?: number | null;
  steps: WorkoutStepRequest[];
}

export const WORKOUT_TYPE_LABELS: Record<WorkoutType, string> = {
  ENDURANCE: 'Endurance',
  RECOVERY: 'Récupération',
  TEMPO: 'Tempo',
  THRESHOLD: 'Seuil',
  INTERVALS: 'Intervalles',
  LONG_RUN: 'Sortie longue',
  RACE: 'Course',
  STRENGTH: 'Renforcement',
  CROSS_TRAINING: 'Cross-training',
  REST: 'Repos',
};

/** Sémantique d'un type de séance : couleur (token), icône, et nature « séance clé ». */
export interface WorkoutTypeMeta { color: string; icon: string; key: boolean; }

/**
 * Couleur et icône par type de séance — **source unique**, partagée par le calendrier du coach
 * et celui de l'athlète. Un même seuil doit avoir la même couleur des deux côtés, sinon coach et
 * athlète ne parlent pas du même calendrier au téléphone.
 */
export const WORKOUT_TYPE_META: Record<WorkoutType, WorkoutTypeMeta> = {
  ENDURANCE:      { color: 'var(--zone-2)', icon: 'footprints', key: false },
  RECOVERY:       { color: 'var(--zone-1)', icon: 'wind', key: false },
  TEMPO:          { color: 'var(--zone-3)', icon: 'timer', key: true },
  THRESHOLD:      { color: 'var(--zone-4)', icon: 'flame', key: true },
  INTERVALS:      { color: 'var(--zone-5)', icon: 'zap', key: true },
  LONG_RUN:       { color: 'var(--primary)', icon: 'mountain-snow', key: true },
  RACE:           { color: 'var(--energy)', icon: 'flag', key: true },
  STRENGTH:       { color: 'var(--dari-violet)', icon: 'dumbbell', key: false },
  CROSS_TRAINING: { color: 'var(--dari-teal)', icon: 'bike', key: false },
  REST:           { color: 'var(--ink-4)', icon: 'moon', key: false },
};

export const STEP_TYPE_LABELS: Record<WorkoutStepType, string> = {
  WARMUP: 'Échauffement',
  STEADY: 'Bloc continu',
  REPETITION: 'Répétitions',
  RECOVERY: 'Récupération',
  COOLDOWN: 'Retour au calme',
};

export const STATUS_LABELS: Record<WorkoutStatus, string> = {
  PLANNED: 'Prévu',
  COMPLETED: 'Réalisé',
  PARTIAL: 'Partiel',
  MISSED: 'Manqué',
};

/**
 * La séance est-elle encore ouverte au ressenti de l'athlète ?
 * Prédicat unique partagé par « Aujourd'hui », l'agenda et l'historique : le ressenti est la
 * donnée qui alimente la forme et les alertes du coach, il ne doit pas dépendre de l'écran
 * depuis lequel on regarde la séance.
 */
export function needsFeedback(w: Pick<Workout, 'status'>): boolean {
  return w.status === 'PLANNED' || w.status === 'PARTIAL';
}

/**
 * La séance n'a **encore rien reçu** de l'athlète : c'est ce qui la fait entrer dans le
 * bandeau « retours en attente ». Distinct de `needsFeedback` : une séance déclarée
 * partielle avec un RPE ou un commentaire a déjà livré son signal — la relancer serait du
 * harcèlement, alors qu'on garde le bouton pour lui permettre de corriger.
 */
export function awaitsFeedback(
  w: Pick<Workout, 'status' | 'rpe' | 'athleteComment'> & { feel?: number | null },
): boolean {
  // La sensation compte comme un signal au même titre que le RPE : un athlète qui a tapé un
  // visage et rien d'autre s'est prononcé, le relancer serait lui redemander ce qu'il vient de
  // dire. `feel` est optionnel pour les appelants qui ne projettent que les champs historiques.
  return needsFeedback(w) && w.rpe == null && w.feel == null && !w.athleteComment;
}

export const STATUS_BADGE: Record<WorkoutStatus, string> = {
  PLANNED: 'badge-info',
  COMPLETED: 'badge-success',
  PARTIAL: 'badge-warning',
  MISSED: 'badge-danger',
};
