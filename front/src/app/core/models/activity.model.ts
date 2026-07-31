export type ActivitySource = 'MANUAL' | 'FILE' | 'STRAVA' | 'GARMIN' | 'COROS';
export type ActivityStatus = 'IMPORTED' | 'MATCHED' | 'UNMATCHED';

export interface Activity {
  id: string;
  athleteId: string;
  source: ActivitySource;
  activityDate: string;
  title: string | null;
  distanceM: number | null;
  durationS: number | null;
  avgHr: number | null;
  elevationGainM: number | null;
  /** Capteurs remontés par la montre (Strava) — nuls sur une saisie manuelle ou un GPX sans capteur. */
  maxHr: number | null;
  avgCadence: number | null;
  avgPowerW: number | null;
  calories: number | null;
  /** Allure moyenne en secondes par kilomètre (calculée backend). */
  paceSPerKm: number | null;
  status: ActivityStatus;
  matchedWorkoutId: string | null;
  distanceDeltaM: number | null;
  durationDeltaS: number | null;
}

export interface ActivityImportRequest {
  source?: ActivitySource;
  externalId?: string | null;
  activityDate: string;
  title?: string | null;
  distanceM?: number | null;
  durationS?: number | null;
  avgHr?: number | null;
  elevationGainM?: number | null;
}

export const ACTIVITY_STATUS_LABELS: Record<ActivityStatus, string> = {
  IMPORTED: 'Importée',
  MATCHED: 'Rapprochée',
  UNMATCHED: 'Non rattachée',
};

export const ACTIVITY_STATUS_BADGE: Record<ActivityStatus, string> = {
  IMPORTED: 'badge-info',
  MATCHED: 'badge-success',
  UNMATCHED: 'badge-warning',
};

/** Temps passé par zone pour une activité (une échelle par métrique : Allure, FC…). V2-7. */
export interface TimeInZoneBucket {
  zoneId: string;
  zoneName: string;
  color: string | null;
  seconds: number;
  pct: number;
}

export interface TimeInZoneScale {
  metricCode: string;
  metricName: string;
  totalS: number;
  buckets: TimeInZoneBucket[];
}

export interface TimeInZone {
  scales: TimeInZoneScale[];
}

/** Récapitulatif chiffré de la semaine en cours pour l'athlète. */
export interface WeekSummary {
  weekStart: string;
  plannedKm: number;
  realizedKm: number;
  plannedSessions: number;
  completedSessions: number;
}
