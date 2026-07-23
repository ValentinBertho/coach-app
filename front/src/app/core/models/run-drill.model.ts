export type RunDrillCategory = 'TECHNIQUE' | 'AMPLITUDE';

export interface RunDrill {
  id: string;
  name: string;
  category: RunDrillCategory;
  /** Catégorie de l'arbre unifié (domaine DRILL), optionnelle (QA1). */
  categoryId: string | null;
  description: string | null;
  videoUrl: string | null;
}

export interface RunDrillRequest {
  name: string;
  category: RunDrillCategory;
  categoryId?: string | null;
  description?: string | null;
  videoUrl?: string | null;
}

export const RUN_DRILL_CATEGORY_LABELS: Record<RunDrillCategory, string> = {
  TECHNIQUE: 'Technique',
  AMPLITUDE: 'Amplitude',
};
