/**
 * Catégorie de bibliothèque (arbre éditable par le coach, scopé club), unifiée sur les trois
 * domaines : course · prépa physique · éducatifs (QA1). Liste plate ordonnée ; l'arborescence se
 * reconstruit via `parentId`.
 */
export type CategoryDomain = 'COURSE' | 'STRENGTH' | 'DRILL';

export interface SessionCategory {
  id: string;
  name: string;
  domain: CategoryDomain;
  parentId: string | null;
  discipline: string | null;
  sortOrder: number;
}

export interface SessionCategoryRequest {
  name: string;
  parentId?: string | null;
  discipline?: string | null;
  sortOrder?: number | null;
}
