export interface MesocycleTemplate {
  id: string;
  name: string;
  description: string | null;
  weeks: number;
  increasePct: number;
  deloadEvery: number;
  deloadPct: number;
}

export interface MesocycleTemplateRequest {
  name: string;
  description?: string | null;
  weeks: number;
  increasePct: number;
  deloadEvery: number;
  deloadPct: number;
}
