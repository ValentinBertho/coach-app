import { Injury } from './injury.model';

export type ActivitySource = 'MANUAL' | 'FILE' | 'STRAVA' | 'GARMIN' | 'COROS';
export type ActivityStatus = 'IMPORTED' | 'MATCHED' | 'UNMATCHED';

/**
 * Sport d'une sortie, tel que la montre ou Strava le déclare. `null` = non déclaré (saisie
 * manuelle, et tout ce qui a été importé avant l'existence du champ).
 *
 * C'est lui qui empêche le rapprochement automatique de confondre une séance de renforcement ou
 * une sortie à vélo avec le fractionné prescrit le même jour : sans lui, ces trois sorties
 * n'étaient qu'une date, une distance et une durée.
 */
export type ActivitySport = 'RUN' | 'RIDE' | 'SWIM' | 'STRENGTH' | 'WALK' | 'OTHER';

/** Libellés FR des sports (le code reste en anglais, cf. convention README). */
export const ACTIVITY_SPORT_LABELS: Record<ActivitySport, string> = {
  RUN: 'Course à pied',
  RIDE: 'Vélo',
  SWIM: 'Natation',
  STRENGTH: 'Renforcement',
  WALK: 'Marche',
  OTHER: 'Autre',
};

/** Libellé FR d'un sport, ou chaîne vide quand la source n'a rien déclaré. */
export function activitySportLabel(sport: ActivitySport | null | undefined): string {
  return sport ? ACTIVITY_SPORT_LABELS[sport] ?? '' : '';
}

/**
 * Libellés FR des **catégories exactes** déclarées par les sources — le type Strava
 * (`GravelRide`), le sport FIT nommé (`trail_running`), l'attribut TCX (`Biking`).
 *
 * La clé est normalisée (minuscules, sans séparateur) : `GravelRide`, `gravel_ride` et
 * `Gravel Ride` désignent la même chose et ne méritent pas trois entrées. La table n'a pas
 * vocation à être exhaustive — Strava en ajoute au fil de l'eau — et ce n'est pas grave :
 * un type absent s'affiche quand même, simplement en anglais mis en forme.
 */
const ACTIVITY_CATEGORY_LABELS: Record<string, string> = {
  // Course à pied
  run: 'Course à pied', running: 'Course à pied',
  trailrun: 'Trail', trailrunning: 'Trail',
  virtualrun: 'Course virtuelle', treadmill: 'Tapis de course',
  indoorrunning: 'Course en salle', streetrunning: 'Course sur route',
  trackrunning: 'Course sur piste',
  // Marche
  walk: 'Marche', walking: 'Marche', hike: 'Randonnée', hiking: 'Randonnée',
  casualwalking: 'Marche', speedwalking: 'Marche rapide', indoorwalking: 'Marche en salle',
  snowshoe: 'Raquettes', snowshoeing: 'Raquettes',
  // Vélo
  ride: 'Vélo', cycling: 'Vélo', biking: 'Vélo',
  gravelride: 'Vélo gravel', gravelcycling: 'Vélo gravel',
  mountainbikeride: 'VTT', mountainbiking: 'VTT',
  roadcycling: 'Vélo route', cyclocross: 'Cyclo-cross',
  ebikeride: 'Vélo électrique', ebiking: 'Vélo électrique',
  emountainbikeride: 'VTT électrique', ebikefitness: 'Vélo électrique',
  virtualride: 'Vélo virtuel', indoorcycling: 'Home-trainer', spin: 'Home-trainer',
  trackcycling: 'Piste', handcycle: 'Handbike', handcycling: 'Handbike',
  velomobile: 'Vélomobile', bmx: 'BMX',
  // Natation
  swim: 'Natation', swimming: 'Natation',
  lapswimming: 'Natation en bassin', openwater: 'Nage en eau libre',
  // Renforcement et salle
  weighttraining: 'Musculation', strengthtraining: 'Musculation',
  crossfit: 'CrossFit', workout: 'Séance libre', training: 'Entraînement',
  hiit: 'HIIT', highintensityintervaltraining: 'HIIT',
  yoga: 'Yoga', pilates: 'Pilates', flexibilitytraining: 'Souplesse',
  cardiotraining: 'Cardio', fitnessequipment: 'Appareil de cardio',
  elliptical: 'Elliptique', stairstepper: 'Escalier', stairclimbing: 'Escalier',
  rowing: 'Aviron', indoorrowing: 'Rameur', virtualrow: 'Rameur virtuel',
  boxing: 'Boxe',
  // Neige, eau, autres
  alpineski: 'Ski alpin', alpineskiing: 'Ski alpin',
  backcountryski: 'Ski de randonnée', nordicski: 'Ski de fond',
  crosscountryskiing: 'Ski de fond', rollerski: 'Ski-roues',
  snowboard: 'Snowboard', snowboarding: 'Snowboard', downhill: 'Descente',
  iceskate: 'Patin à glace', iceskating: 'Patin à glace',
  inlineskate: 'Roller', inlineskating: 'Roller', skateboard: 'Skateboard',
  kayaking: 'Kayak', canoeing: 'Canoë', paddling: 'Pagaie',
  standuppaddling: 'Paddle', standuppaddleboarding: 'Paddle',
  surfing: 'Surf', windsurf: 'Planche à voile', windsurfing: 'Planche à voile',
  kitesurf: 'Kitesurf', kitesurfing: 'Kitesurf', sail: 'Voile', sailing: 'Voile',
  diving: 'Plongée', rockclimbing: 'Escalade', mountaineering: 'Alpinisme',
  golf: 'Golf', soccer: 'Football', tennis: 'Tennis', badminton: 'Badminton',
  squash: 'Squash', tabletennis: 'Tennis de table', pickleball: 'Pickleball',
  wheelchair: 'Fauteuil roulant', virtualactivity: 'Activité virtuelle',
};

/**
 * Catégorie d'une sortie, telle qu'elle s'affiche : la plus précise dont on dispose.
 *
 * La famille (`sport`) sert au rapprochement — elle doit être grossière pour ça. Mais un coach
 * qui lit « Vélo » là où son athlète a fait du gravel lit une information appauvrie, et un
 * « Pickleball » que la famille ne sait pas ranger n'a aucune raison de disparaître de l'écran.
 * On affiche donc le libellé exact quand la source en a donné un, la famille sinon.
 *
 * Un type que la table ne traduit pas n'est pas perdu pour autant : il est mis en forme tel
 * quel (« Pickleball », « Gravel Ride »). Afficher l'anglais vaut mieux que masquer le fait.
 */
export function activityCategoryLabel(
  a: Pick<Activity, 'sport' | 'sportDetail'>,
): string {
  const detail = a.sportDetail?.trim();
  if (!detail) {
    return activitySportLabel(a.sport);
  }
  const known = ACTIVITY_CATEGORY_LABELS[detail.toLowerCase().replace(/[^a-z0-9]/gi, '')];
  return known ?? humanizeCategory(detail);
}

/** « GravelRide » → « Gravel ride », « trail_running » → « Trail running ». */
function humanizeCategory(raw: string): string {
  const words = raw
    .replace(/[_-]+/g, ' ')
    // Coupe le chameau sans couper les sigles : « EBikeRide » → « E Bike Ride ».
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .trim()
    .toLowerCase();
  return words ? words.charAt(0).toUpperCase() + words.slice(1) : '';
}

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
  /** Sport déclaré par la source, ou `null` si elle n'a rien dit. */
  sport: ActivitySport | null;
  /**
   * Catégorie exacte déclarée par la source (« GravelRide », « trail_running »), quand `sport`
   * n'en garde que la famille. C'est elle qu'on affiche — cf. {@link activityCategoryLabel}.
   */
  sportDetail: string | null;
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
  /**
   * Sport de la sortie. Facultatif : `null` signifie « non déclaré », et le rapprochement se
   * comporte alors comme avant l'existence du champ. Le renseigner sur une séance de
   * renforcement saisie à la main l'empêche d'aller se rattacher au fractionné du jour.
   */
  sport?: ActivitySport | null;
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
