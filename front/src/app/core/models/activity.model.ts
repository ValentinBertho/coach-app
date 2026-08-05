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
  /** Ressenti déclaré par l'athlète sur cette sortie (1–10) — utile surtout hors programme. */
  rpe: number | null;
  /** Mot de l'athlète à son coach sur cette sortie. */
  athleteComment: string | null;
}

/**
 * Correction d'une sortie par l'athlète. Champ absent = inchangé ; les deux drapeaux `clear*`
 * servent à effacer, ce qu'un `null` ne pourrait pas exprimer sans tout effacer par défaut.
 */
export interface ActivityUpdate {
  title?: string | null;
  activityDate?: string | null;
  distanceM?: number | null;
  durationS?: number | null;
  elevationGainM?: number | null;
  rpe?: number | null;
  comment?: string | null;
  clearRpe?: boolean;
  clearComment?: boolean;
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

/**
 * Un tour d'activité : soit un tour relevé par la montre (une répétition de fractionné), soit un
 * split kilométrique calculé. La distinction est portée par {@link ActivityLaps.kind}.
 */
export interface ActivityLap {
  index: number;
  distanceM: number | null;
  durationS: number | null;
  /** Allure moyenne du tour (s/km), calculée backend pour que tous les écrans s'accordent. */
  paceSPerKm: number | null;
  avgHr: number | null;
  maxHr: number | null;
  avgCadence: number | null;
  elevationGainM: number | null;
}

export interface ActivityLaps {
  /** `DEVICE` = tours de la montre (les vraies répétitions) ; `SPLIT` = découpe au kilomètre. */
  kind: 'DEVICE' | 'SPLIT';
  laps: ActivityLap[];
}

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
