/**
 * Note libre du coach sur le calendrier d'un athlète.
 *
 * <p>Deux formes, une seule donnée : sans `endDate`, c'est la note d'un jour (une chip sur la
 * case) ; avec, elle couvre une période et l'interface l'appelle un <b>cycle</b> — « bloc
 * spécifique », « affûtage » — affiché en bandeau au-dessus des semaines qu'il traverse.</p>
 */
export interface CalendarNote {
  id: string;
  athleteId: string;
  noteDate: string;
  /** Dernier jour couvert (inclus), ou `null` pour une note d'un seul jour. */
  endDate?: string | null;
  text: string;
}

export interface CalendarNoteRequest {
  noteDate: string;
  endDate?: string | null;
  text: string;
}

/** La note couvre-t-elle une période (donc : est-ce un cycle) ? */
export function isCycle(n: CalendarNote): boolean {
  return !!n.endDate && n.endDate > n.noteDate;
}
