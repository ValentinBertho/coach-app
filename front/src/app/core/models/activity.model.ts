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
