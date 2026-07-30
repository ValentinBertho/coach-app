export type RacePriority = 'A' | 'B' | 'C';
export type RaceStatus = 'UPCOMING' | 'DONE' | 'CANCELLED';

export interface RaceObjective {
  id: string;
  athleteId: string;
  /** Renseigné sur les listes multi-athlètes (cockpit coach), nul sur la fiche d'un athlète. */
  athleteName: string | null;
  name: string;
  raceDate: string;
  distanceM: number | null;
  targetTimeS: number | null;
  priority: RacePriority;
  status: RaceStatus;
  daysUntil: number;
}

export interface RaceObjectiveRequest {
  name: string;
  raceDate: string;
  distanceM?: number | null;
  targetTimeS?: number | null;
  priority?: RacePriority;
  status?: RaceStatus;
}
