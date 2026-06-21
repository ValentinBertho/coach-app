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

export const STATUS_BADGE: Record<WorkoutStatus, string> = {
  PLANNED: 'badge-info',
  COMPLETED: 'badge-success',
  PARTIAL: 'badge-warning',
  MISSED: 'badge-danger',
};
