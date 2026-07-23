import { IntensityZone, WorkoutStepType, WorkoutType } from './workout.model';

export interface TemplateStep {
  stepType: WorkoutStepType;
  repetitions: number;
  zone: IntensityZone | null;
  distanceM: number | null;
  durationS: number | null;
  notes: string | null;
}

export interface WorkoutTemplate {
  id: string;
  name: string;
  type: WorkoutType;
  title: string;
  notes: string | null;
  targetDistanceM: number | null;
  targetDurationS: number | null;
  /** Catégorie de bibliothèque (personnalisée, scopée club) — null si non catégorisé. */
  categoryId: string | null;
  categoryName: string | null;
  favorite: boolean;
  useCount: number;
  steps: TemplateStep[];
}

export interface WorkoutTemplateRequest {
  name: string;
  type: WorkoutType;
  title: string;
  notes?: string | null;
  targetDistanceM?: number | null;
  targetDurationS?: number | null;
  categoryId?: string | null;
  steps: TemplateStep[];
}
