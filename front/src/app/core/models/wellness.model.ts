/**
 * Check-in matinal : sommeil, fatigue, douleur.
 *
 * La fatigue et la douleur alimentent l'état de forme (invariant DARI Lab : jamais le RPE) ;
 * le sommeil est un contexte pour le coach — « mal dormi, fatigue 7 » ne se lit pas comme
 * « bien dormi, fatigue 7 » — mais il n'entre pas dans le calcul.
 */
export interface WellnessCheckIn {
  id: string;
  checkDate: string;
  sleep: number | null;
  fatigue: number | null;
  pain: number | null;
}

export interface WellnessCheckInRequest {
  checkDate?: string | null;
  sleep?: number | null;
  fatigue?: number | null;
  pain?: number | null;
}
