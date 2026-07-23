/**
 * Catégorie de la bibliothèque de séances course (arbre éditable par le coach, scopé club).
 * Liste plate ordonnée ; l'arborescence se reconstruit via `parentId`.
 */
export interface SessionCategory {
  id: string;
  name: string;
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
