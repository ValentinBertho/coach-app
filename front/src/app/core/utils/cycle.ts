import { CalendarNote, isCycle } from '../models/calendar-note.model';

/**
 * Lecture d'un cycle d'entraînement du point de vue de l'athlète.
 *
 * <p>Un cycle est une note de période posée par le coach sur le calendrier — « bloc spécifique »,
 * « affûtage », « reprise ». Le coach la voit depuis toujours ; l'athlète, lui, recevait des
 * séances sans jamais savoir dans quelle phase elles s'inscrivaient. Ces quelques fonctions
 * servent uniquement à situer une date dans un cycle : rien ici ne décide de l'entraînement.</p>
 */

/** Position d'une date dans un cycle : « semaine 2 sur 4 ». */
export interface CyclePosition {
  /** Semaine en cours, à partir de 1. */
  week: number;
  /** Nombre de semaines couvertes, arrondi au supérieur (un cycle de 10 jours en fait 2). */
  total: number;
}

/** Nombre de jours entiers entre deux dates ISO (`to - from`), négatif si `to` précède `from`. */
function daysBetween(from: string, to: string): number {
  const a = Date.parse(`${from}T00:00:00Z`);
  const b = Date.parse(`${to}T00:00:00Z`);
  if (Number.isNaN(a) || Number.isNaN(b)) return 0;
  return Math.round((b - a) / 86_400_000);
}

/** La date tombe-t-elle dans le cycle (bornes incluses) ? */
export function coversDate(note: CalendarNote, iso: string): boolean {
  return isCycle(note) && note.noteDate <= iso && (note.endDate ?? '') >= iso;
}

/**
 * Cycle couvrant la date donnée.
 *
 * <p>Rien n'interdit au coach d'en faire se chevaucher deux (un bloc long et un affûtage qui
 * démarre dedans). Le plus récemment commencé l'emporte : c'est celui qui décrit la phase
 * courante, l'autre n'est plus qu'un contexte.</p>
 */
export function currentCycle(cycles: CalendarNote[], iso: string): CalendarNote | null {
  const covering = cycles.filter((c) => coversDate(c, iso));
  if (covering.length === 0) return null;
  return covering.reduce((best, c) => (c.noteDate > best.noteDate ? c : best));
}

/**
 * Où en est-on dans le cycle, en semaines.
 *
 * <p>Retourne `null` si la date est hors du cycle : on ne situe pas une semaine dans une période
 * qui ne la contient pas.</p>
 */
export function cyclePosition(note: CalendarNote, iso: string): CyclePosition | null {
  if (!coversDate(note, iso)) return null;
  const span = daysBetween(note.noteDate, note.endDate!) + 1;
  return {
    week: Math.floor(daysBetween(note.noteDate, iso) / 7) + 1,
    total: Math.ceil(span / 7),
  };
}

/** Jours restants avant la fin du cycle, la date incluse (0 = dernier jour). */
export function daysLeft(note: CalendarNote, iso: string): number | null {
  if (!coversDate(note, iso)) return null;
  return daysBetween(iso, note.endDate!);
}

/** Cycles chevauchant une plage `[from, to]`, du plus ancien au plus récent. */
export function cyclesOverlapping(cycles: CalendarNote[], from: string, to: string): CalendarNote[] {
  return cycles
    .filter((c) => isCycle(c) && c.noteDate <= to && (c.endDate ?? '') >= from)
    .sort((a, b) => a.noteDate.localeCompare(b.noteDate));
}
