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
  rpe: number | null;
  athleteComment: string | null;
  /** Retour du coach sur la séance réalisée (feedback in situ), visible par l'athlète. */
  coachComment: string | null;
  coachCommentAt: string | null;
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
export function awaitsFeedback(w: Pick<Workout, 'status' | 'rpe' | 'athleteComment'>): boolean {
  return needsFeedback(w) && w.rpe == null && !w.athleteComment;
}

export const STATUS_BADGE: Record<WorkoutStatus, string> = {
  PLANNED: 'badge-info',
  COMPLETED: 'badge-success',
  PARTIAL: 'badge-warning',
  MISSED: 'badge-danger',
};
