/**
 * Ce que le produit conclut — et ne fait jamais tout seul.
 *
 * Toutes les suggestions du produit convergent vers une `Proposal` : elle porte ce qui serait
 * fait, pourquoi, et attend une décision humaine. Il n'existe aucun chemin qui applique une
 * suggestion sans passer par `accept`.
 */

export type ProposalType =
  | 'SESSION_LIGHTEN'
  | 'SESSION_EASY'
  | 'SESSION_MOVE'
  | 'SESSION_DROP'
  | 'PHYSIO_UPDATE'
  | 'STRENGTH_LOAD';

export type ProposalStatus = 'PENDING' | 'ACCEPTED' | 'DISMISSED' | 'EXPIRED';

export interface Proposal {
  id: string;
  athleteId: string;
  /** Rempli côté coach (file multi-athlètes), nul dans le portail athlète. */
  athleteName: string | null;
  type: ProposalType;
  status: ProposalStatus;
  targetId: string | null;
  targetDate: string | null;
  title: string;
  reason: string | null;
  /** Vrai quand c'est l'athlète qui demande : une demande attend une réponse du jour même. */
  requestedByAthlete: boolean;
  payloadJson: string | null;
}

/** Libellés de type, pour la puce qui précède le titre dans la file. */
export const PROPOSAL_TYPE_LABELS: Record<ProposalType, string> = {
  SESSION_LIGHTEN: 'Alléger',
  SESSION_EASY: 'Footing',
  SESSION_MOVE: 'Décaler',
  SESSION_DROP: 'Retirer',
  PHYSIO_UPDATE: 'Niveau',
  STRENGTH_LOAD: 'Charge',
};

// --- Feu vert du matin -------------------------------------------------------

export type Advice = 'KEEP' | 'LIGHTEN' | 'EASY' | 'MOVE';
export type FormLevel = 'GREEN' | 'ORANGE' | 'RED' | 'STALE';

export interface Readiness {
  advice: Advice;
  severity: FormLevel;
  title: string;
  reason: string | null;
  workoutId: string | null;
  sessionTitle: string | null;
  /** Ce qui est prévu, en une ligne — « 6 × 1000 m ». */
  plannedSummary: string | null;
  /** Ce que donnerait l'adaptation — « 4 × 1000 m ». Nul quand il n'y a rien à montrer. */
  adaptedSummary: string | null;
  /** Une demande est déjà partie : on ne la redemande pas. */
  pendingRequest: boolean;
  checkInDone: boolean;
  availableActions: string[];
}

// --- Séance tenue ? ----------------------------------------------------------

export type EffortVerdict = 'ON_TARGET' | 'TOO_FAST' | 'TOO_SLOW' | 'NOT_ASSESSED';

export interface ComplianceEffort {
  label: string;
  verdict: EffortVerdict;
  targetFastSecPerKm: number | null;
  targetSlowSecPerKm: number | null;
  actualPaceSecPerKm: number | null;
  /** Écart signé à la fourchette : négatif = plus rapide que prescrit. */
  deltaSecPerKm: number | null;
  /** FC moyenne du tour apparié à ce bloc. Nulle quand la montre n'en donne pas. */
  actualAvgHrBpm: number | null;
}

/**
 * Dérive du dernier bloc du corps de séance par rapport au premier.
 *
 * <p>Absente tant qu'il n'y a pas deux blocs porteurs d'une FC : sans comparaison possible,
 * « 0 % » se lirait comme une séance parfaitement stable.</p>
 */
export interface ComplianceDrift {
  firstLabel: string;
  lastLabel: string;
  /** Écart de FC, signé : positif = cœur plus haut à la fin. */
  hrDeltaBpm: number;
  /** Le même écart rapporté à la FC de départ — comparable d'un athlète à l'autre. */
  hrDeltaPct: number;
  /**
   * Écart d'allure, signé (positif = plus lent à la fin). C'est lui qui dit si la dérive a été
   * *subie* — même allure, cœur plus haut — ou *compensée* — cœur tenu, allure lâchée.
   */
  paceDeltaSecPerKm: number | null;
}

export interface Compliance {
  /** Nul signifie « rien n'était jugeable », jamais « zéro pour cent ». */
  scorePct: number | null;
  assessed: number;
  onTarget: number;
  verdict: string | null;
  detail: string | null;
  activityId: string | null;
  efforts: ComplianceEffort[];
  drift: ComplianceDrift | null;
}

// --- Trajectoire -------------------------------------------------------------

export type Outlook = 'AHEAD' | 'ON_TRACK' | 'AT_RISK' | 'OUT_OF_REACH' | 'UNKNOWN';

export interface Trajectory {
  raceId: string;
  raceName: string;
  raceDate: string;
  daysLeft: number;
  weeksLeft: number;
  distanceM: number | null;
  targetTimeS: number | null;
  outlook: Outlook;
  currentVdot: number | null;
  requiredVdot: number | null;
  projectedTimeS: number | null;
  gapSeconds: number | null;
  vdotGap: number | null;
  vdotPerWeek: number | null;
  headline: string;
  detail: string | null;
  /** Ce que la charge dit de la trajectoire — absent quand elle ne dit rien. */
  loadNote: string | null;
}

// --- La semaine en une phrase ------------------------------------------------

export type WeekLevel = 'CALM' | 'NORMAL' | 'WATCH' | 'HIGH';

export interface WeekOutlook {
  weekStart: string;
  plannedLoadUa: number;
  chronicWeeklyUa: number;
  deltaPct: number | null;
  projectedAcwr: number | null;
  hardSessions: number;
  level: WeekLevel;
  /** Nulle quand la semaine est vide : l'interface se tait plutôt que d'annoncer « 0 UA ». */
  sentence: string | null;
}

// --- Le plan qui part de la course -------------------------------------------

export type PlanPhase = 'BASE' | 'BUILD' | 'SPECIFIC' | 'DELOAD' | 'TAPER' | 'RACE';

export interface PlanWeek {
  index: number;
  monday: string;
  phase: PlanPhase;
  label: string;
  volumeFactor: number;
  skipped: boolean;
}

export interface PlanPreview {
  applied: boolean;
  raceName: string;
  raceDate: string;
  sourceWeekStart: string;
  sourceSessions: number;
  summary: string;
  warning: string | null;
  createdSessions: number;
  skippedWeeks: number;
  weeks: PlanWeek[];
}

export interface PlanFromRaceParams {
  sourceWeekStart?: string;
  firstWeekStart?: string;
  peakFactor?: number;
  taperWeeks?: number;
  deloadEvery?: number;
  overwrite?: boolean;
}

// --- Le fil de l'athlète -----------------------------------------------------

export type TimelineKind =
  | 'TEST'
  | 'RECORD'
  | 'RACE'
  | 'INJURY'
  | 'UNAVAILABILITY'
  | 'STRENGTH_TEST';

export interface TimelineEvent {
  date: string;
  kind: TimelineKind;
  /** La couleur de la pastille, pas une note : neutral | good | watch | alert. */
  tone: 'neutral' | 'good' | 'watch' | 'alert';
  title: string;
  detail: string | null;
  targetId: string | null;
  link: string | null;
}

export interface Timeline {
  from: string;
  to: string;
  events: TimelineEvent[];
}

export const TIMELINE_ICONS: Record<TimelineKind, string> = {
  TEST: 'activity',
  RECORD: 'timer',
  RACE: 'flag',
  INJURY: 'triangle-alert',
  UNAVAILABILITY: 'calendar-off',
  STRENGTH_TEST: 'dumbbell',
};
