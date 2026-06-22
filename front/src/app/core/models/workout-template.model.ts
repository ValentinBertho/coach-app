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
  steps: TemplateStep[];
}

export interface WorkoutTemplateRequest {
  name: string;
  type: WorkoutType;
  title: string;
  notes?: string | null;
  targetDistanceM?: number | null;
  targetDurationS?: number | null;
  steps: TemplateStep[];
}
