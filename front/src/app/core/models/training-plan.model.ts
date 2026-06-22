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
}

export interface TrainingPlanRequest {
  name: string;
  description?: string | null;
  durationWeeks: number;
  items: PlanItem[];
}
