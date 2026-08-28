import { Injury } from './injury.model';

export type ActivitySource = 'MANUAL' | 'FILE' | 'STRAVA' | 'GARMIN' | 'COROS';
export type ActivityStatus = 'IMPORTED' | 'MATCHED' | 'UNMATCHED';

/**
 * Sources dont une sortie peut <b>revenir toute seule</b> : elles se synchronisent. Une saisie
 * manuelle ou un fichier déposé à la main ne reviennent que si quelqu'un les redépose — proposer
 * « ne plus jamais importer » là-dessus n'aurait rien à empêcher.
 */
const SYNCED_SOURCES: ReadonlySet<ActivitySource> = new Set<ActivitySource>(['STRAVA', 'GARMIN', 'COROS']);

export function isSyncedSource(source: ActivitySource | null | undefined): boolean {
  return source != null && SYNCED_SOURCES.has(source);
}

/**
 * Une sortie écartée pour de bon : la synchro ne la rapporte plus.
 *
 * <p>Le titre et la date sont ceux recopiés à la suppression — la sortie n'existe plus, et sans
 * eux l'écran n'aurait qu'un identifiant à proposer à qui voudrait annuler le masquage.</p>
 */
export interface ActivityExclusion {
  id: string;
  source: ActivitySource;
  title: string | null;
  activityDate: string | null;
}

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
  /**
   * Ressenti de la sortie — **celui de sa séance** quand elle est rapprochée.
   *
   * Une sortie rapprochée et sa séance décrivent le même effort : il n'y a qu'un ressenti, porté
   * par la séance, et le serveur le rend ici pour que les deux écrans ne puissent pas diverger.
   */
  rpe: number | null;
  /** Sensation générale (1 = excellente … 5 = très mauvaise) ; distincte de la difficulté. */
  feel: number | null;
  /** Fatigue et douleur ressenties (0–10) — mêmes échelles que sur une séance. */
  fatigue: number | null;
  pain: number | null;
  /** Blessures nommées ; liste vide si aucune. */
  injuries: Injury[];
  /** Mot de l'athlète à son coach. */
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
  feel?: number | null;
  /** Fatigue et douleur (0–10) — 0 est une valeur, pas une absence de réponse. */
  fatigue?: number | null;
  pain?: number | null;
  comment?: string | null;
  /** Blessures déclarées ; champ absent = inchangé, liste vide = plus aucune. */
  injuries?: Injury[];
  clearRpe?: boolean;
  clearFeel?: boolean;
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

/** `DEVICE` = tours de la montre (les vraies répétitions) ; `SPLIT` = découpe au kilomètre. */
export type LapKind = 'DEVICE' | 'SPLIT';

export interface ActivityLaps {
  kind: LapKind;
  laps: ActivityLap[];
  /**
   * Découpes que cette sortie sait produire. Une sortie portant à la fois les tours de sa montre
   * et un tracé exploitable offre les deux lectures — et comparer les répétitions aux kilomètres
   * est justement ce qu'on fait sur un fractionné en côte. Absent sur les réponses anciennes.
   */
  availableKinds?: LapKind[] | null;
}

export const LAP_KIND_LABELS: Record<LapKind, string> = {
  DEVICE: 'Tours montre',
  SPLIT: 'Tours 1 km',
};

/** Un point de la courbe de séance : FC et allure à une distance donnée. */
export interface ActivityStreamPoint {
  distanceM: number;
  elapsedS: number;
  hr: number | null;
  paceSPerKm: number | null;
}

/**
 * Courbe d'une sortie, en fonction de la **distance** — les moyennes disent ce que la séance a
 * coûté, la courbe dit comment elle s'est passée (dérive cardiaque, dents de scie d'un fractionné).
 */
export interface ActivityStream {
  points: ActivityStreamPoint[];
  totalDistanceM: number;
  hasHeartRate: boolean;
  hasPace: boolean;
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
