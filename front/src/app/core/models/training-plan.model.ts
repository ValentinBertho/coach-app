export interface PlanItem {
  weekIndex: number;
  dayOfWeek: number;
  templateId: string;
  templateName?: string;
}

export interface TrainingPlan {
  id: string;
  name: string;
  description: string | null;
  durationWeeks: number;
  items: PlanItem[];
  /** Athlètes auxquels le plan est attribué (modèle many-to-many). */
  athletes?: { id: string; name: string }[];
}

export interface TrainingPlanRequest {
  name: string;
  description?: string | null;
  durationWeeks: number;
  items: PlanItem[];
}
