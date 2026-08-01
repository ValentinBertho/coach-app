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

/** Catégorie replacée dans son arbre : profondeur et libellé indenté pour les menus déroulants. */
export interface CategoryOption {
  category: SessionCategory;
  depth: number;
  /** Nom précédé de sa hiérarchie (« — Seuil court ») — lisible dans un `<select>`. */
  label: string;
}

const INDENT = '  ';

/**
 * Aplatit l'arbre des catégories : chaque parent est suivi de ses enfants, indentés. Les
 * catégories dont le parent a disparu remontent à la racine plutôt que de devenir invisibles.
 */
export function categoryOptions(list: SessionCategory[]): CategoryOption[] {
  const byParent = new Map<string, SessionCategory[]>();
  const ids = new Set(list.map((c) => c.id));
  for (const c of list) {
    const key = c.parentId && ids.has(c.parentId) ? c.parentId : '';
    byParent.set(key, [...(byParent.get(key) ?? []), c]);
  }
  const sort = (a: SessionCategory, b: SessionCategory) =>
    a.sortOrder - b.sortOrder || a.name.localeCompare(b.name);

  const out: CategoryOption[] = [];
  const walk = (parentKey: string, depth: number): void => {
    for (const c of (byParent.get(parentKey) ?? []).slice().sort(sort)) {
      out.push({ category: c, depth, label: depth ? `${INDENT.repeat(depth)}↳ ${c.name}` : c.name });
      walk(c.id, depth + 1);
    }
  };
  walk('', 0);
  return out;
}

/** Chemin complet d'une catégorie (« VMA › VMA courte »), ou '' si introuvable. */
export function categoryPath(list: SessionCategory[], id: string | null): string {
  if (!id) return '';
  const byId = new Map(list.map((c) => [c.id, c]));
  const parts: string[] = [];
  let cur = byId.get(id);
  // Garde-fou : un cycle de parents (donnée corrompue) ne doit pas figer l'écran.
  const seen = new Set<string>();
  while (cur && !seen.has(cur.id)) {
    seen.add(cur.id);
    parts.unshift(cur.name);
    cur = cur.parentId ? byId.get(cur.parentId) : undefined;
  }
  return parts.join(' › ');
}

/** Identifiants d'une catégorie et de toutes ses descendantes (filtre « incluant les sous-catégories »). */
export function categoryWithDescendants(list: SessionCategory[], id: string): Set<string> {
  const out = new Set<string>([id]);
  let grew = true;
  while (grew) {
    grew = false;
    for (const c of list) {
      if (c.parentId && out.has(c.parentId) && !out.has(c.id)) {
        out.add(c.id);
        grew = true;
      }
    }
  }
  return out;
}
