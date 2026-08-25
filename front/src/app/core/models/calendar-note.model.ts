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
  /**
   * Lisible par l'autre partie. Faux = carnet de travail du coach — c'est la valeur de toutes
   * les notes écrites avant que le partage n'existe, et le défaut à la saisie.
   */
  shared?: boolean;
  /** Qui l'a écrite. Dans un calendrier à deux voix, cela fait partie du message. */
  authorRole?: 'COACH' | 'ATHLETE';
  authorName?: string | null;
}

export interface CalendarNoteRequest {
  noteDate: string;
  endDate?: string | null;
  text: string;
  shared?: boolean;
}

/** La note couvre-t-elle une période (donc : est-ce un cycle) ? */
export function isCycle(n: CalendarNote): boolean {
  return !!n.endDate && n.endDate > n.noteDate;
}
