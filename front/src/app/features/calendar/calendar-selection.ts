import { computed, signal } from '@angular/core';

/** Nature d'une chip sélectionnable. Course et force se manipulent ensemble mais pas pareil. */
export type ChipKind = 'course' | 'strength';

/** Référence stable d'une chip dans la grille : ce qui est sélectionné, pas l'objet lui-même. */
export interface ChipRef {
  readonly kind: ChipKind;
  readonly id: string;
}

/** Ordre de parcours de la grille : une chip est identifiée par (date, rang dans le jour). */
export interface ChipPosition extends ChipRef {
  readonly date: string;
  readonly index: number;
}

function key(ref: ChipRef): string { return `${ref.kind}:${ref.id}`; }

/**
 * Sélection multiple de chips du calendrier.
 *
 * Le modèle est celui d'un explorateur de fichiers, parce que c'est celui que tout le monde
 * connaît déjà : clic simple = sélection unique, Cmd/Ctrl+clic = ajouter ou retirer,
 * Maj+clic = étendre depuis la dernière ancre dans l'ordre de la grille. Programmer devient
 * alors un travail de tableur — l'écart exact que le produit avait avec Nolio.
 *
 * L'état est volontairement sans dépendance : ni HTTP, ni toast, ni routeur. Il se teste seul.
 */
export class CalendarSelection {
  private readonly keys = signal<ReadonlySet<string>>(new Set());
  /** Dernière chip cliquée sans Maj : point de départ des extensions. */
  private anchor: ChipRef | null = null;

  readonly count = computed(() => this.keys().size);
  readonly isEmpty = computed(() => this.keys().size === 0);
  /** Vrai dès que plusieurs chips sont sélectionnées : bascule l'UI en mode « lot ». */
  readonly isMulti = computed(() => this.keys().size > 1);

  has(ref: ChipRef): boolean { return this.keys().has(key(ref)); }

  /** Sélection courante, dans l'ordre de la grille passée en argument. */
  resolve(order: readonly ChipPosition[]): ChipPosition[] {
    const set = this.keys();
    return order.filter((p) => set.has(key(p)));
  }

  ids(kind: ChipKind): string[] {
    return [...this.keys()]
      .filter((k) => k.startsWith(`${kind}:`))
      .map((k) => k.slice(kind.length + 1));
  }

  clear(): void {
    this.keys.set(new Set());
    this.anchor = null;
  }

  /** Sélection unique (clic simple). */
  select(ref: ChipRef): void {
    this.keys.set(new Set([key(ref)]));
    this.anchor = ref;
  }

  /** Ajoute ou retire (Cmd/Ctrl+clic) : l'ancre suit le dernier geste, comme dans un explorateur. */
  toggle(ref: ChipRef): void {
    const next = new Set(this.keys());
    if (next.has(key(ref))) next.delete(key(ref));
    else next.add(key(ref));
    this.keys.set(next);
    this.anchor = ref;
  }

  /**
   * Étend la sélection de l'ancre jusqu'à `ref` dans l'ordre de la grille (Maj+clic).
   * Sans ancre, se comporte comme un clic simple : mieux vaut sélectionner une chip que rien.
   */
  extendTo(ref: ChipRef, order: readonly ChipPosition[]): void {
    if (!this.anchor) { this.select(ref); return; }
    const from = order.findIndex((p) => p.kind === this.anchor!.kind && p.id === this.anchor!.id);
    const to = order.findIndex((p) => p.kind === ref.kind && p.id === ref.id);
    if (from < 0 || to < 0) { this.select(ref); return; }
    const [lo, hi] = from <= to ? [from, to] : [to, from];
    this.keys.set(new Set(order.slice(lo, hi + 1).map(key)));
    // L'ancre ne bouge pas : Maj+clic répété doit pouvoir rétrécir la plage.
  }

  /** Remplace la sélection par un ensemble complet (sélection rectangle, « tout sélectionner »). */
  setAll(refs: readonly ChipRef[]): void {
    this.keys.set(new Set(refs.map(key)));
    this.anchor = refs.at(-1) ?? null;
  }

  /**
   * Retire de la sélection ce qui n'existe plus dans la grille (suppression, changement de
   * période). Une sélection fantôme ferait agir Suppr sur des ids déjà morts.
   */
  prune(existing: readonly ChipRef[]): void {
    const alive = new Set(existing.map(key));
    const next = new Set([...this.keys()].filter((k) => alive.has(k)));
    if (next.size !== this.keys().size) this.keys.set(next);
  }
}
