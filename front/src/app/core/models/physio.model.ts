export type Discipline = 'ROUTE' | 'TRAIL';

export interface PhysioProfile {
  discipline: Discipline | null;
  lt1Ms: number | null;
  lt2Ms: number | null;
  vcMs: number | null;
  lt1Kmh: number | null;
  lt2Kmh: number | null;
  vcKmh: number | null;
  fcMax: number | null;
  fcLt1: number | null;
  fcLt2: number | null;
  vcDomain1Pct: number | null;
  vcDomain2Pct: number | null;
  fcDomain1Pct: number | null;
  fcDomain2Pct: number | null;
  vdot: number | null;
}

export interface VdotPaceItem {
  distance: string;
  paceSecPerKm: number | null;
  paceLabel: string;
  speedKmh: number | null;
}

export interface Vdot {
  vdot: number | null;
  paces: VdotPaceItem[];
}

export interface Performance {
  id: string;
  distance: string;
  distanceCode: string;
  timeSeconds: number;
  dateSet: string | null;
  vdot: number | null;
}
