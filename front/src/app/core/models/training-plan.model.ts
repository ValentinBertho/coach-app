export type PlanItemKind = 'COURSE' | 'STRENGTH';

export interface PlanItem {
  weekIndex: number;
  dayOfWeek: number;
  kind?: PlanItemKind;
  templateId: string;
  templateName?: string;
}

export interface TrainingPlan {
  id: string;
  name: string;
  description: string | null;
  durationWeeks: number;
  /** Modèle de mésocycle porté par le plan (progression de charge), si défini. */
  mesocycleTemplateId?: string | null;
  items: PlanItem[];
  /** Athlètes auxquels le plan est attribué (modèle many-to-many). */
  athletes?: { id: string; name: string }[];
}

export interface TrainingPlanRequest {
  name: string;
  description?: string | null;
  durationWeeks: number;
  mesocycleTemplateId?: string | null;
  items: PlanItem[];
}
