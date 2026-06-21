export type RacePriority = 'A' | 'B' | 'C';
export type RaceStatus = 'UPCOMING' | 'DONE' | 'CANCELLED';

export interface RaceObjective {
  id: string;
  athleteId: string;
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
