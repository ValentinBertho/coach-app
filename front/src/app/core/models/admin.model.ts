import { UserRole } from './user.model';
import { AthleteLevel, AthleteStatus } from './athlete.model';

export type ClubStatus = 'ACTIVE' | 'SUSPENDED';
export type UserStatus = 'ACTIVE' | 'INVITED' | 'SUSPENDED';

/** Compteurs bruts historiques (`GET /admin/stats`). Conservés : des PWA en cache les appellent. */
export interface AdminStats {
  clubs: number;
  headCoaches: number;
  coaches: number;
  athletes: number;
  pendingInvitations: number;
  workouts: number;
  activities: number;
}

// ---------------------------------------------------------------------------
// Pilotage
// ---------------------------------------------------------------------------

export type SignalSeverity = 'CRITICAL' | 'WARNING' | 'INFO';

/**
 * Une anomalie actionnable. Le tableau de bord n'affiche que ce qui appelle une décision :
 * un écran d'administration se regarde une minute par jour, pas dix.
 */
export interface AdminSignal {
  key: string;
  severity: SignalSeverity;
  title: string;
  detail: string;
  actionLabel: string | null;
  actionRoute: string | null;
  value: number;
}

export type IntegrationStatus = 'OK' | 'WARNING' | 'OFF';

export interface AdminIntegration {
  key: string;
  label: string;
  status: IntegrationStatus;
  detail: string;
  count: number;
}

export interface AdminCounts {
  clubs: number;
  clubsActive: number;
  clubsSuspended: number;
  users: number;
  admins: number;
  headCoaches: number;
  coaches: number;
  athleteAccounts: number;
  usersSuspended: number;
  usersUnverified: number;
  athletes: number;
  athletesActive: number;
  athletesPaused: number;
  athletesArchived: number;
  pendingInvitations: number;
  workouts: number;
  activities: number;
}

export interface AdminGrowth {
  newUsers7d: number;
  newUsers30d: number;
  newClubs30d: number;
  newAthletes30d: number;
}

export interface AdminEngagement {
  activeUsers24h: number;
  activeUsers7d: number;
  activeUsers30d: number;
  activities7d: number;
  workoutsPlanned7d: number;
  workoutsCompleted7d: number;
  adminActions7d: number;
}

export interface AdminOverview {
  signals: AdminSignal[];
  counts: AdminCounts;
  growth: AdminGrowth;
  engagement: AdminEngagement;
  integrations: AdminIntegration[];
  recentActions: AdminAuditEntry[];
}

// ---------------------------------------------------------------------------
// Journal d'audit
// ---------------------------------------------------------------------------

export type AuditTargetType = 'USER' | 'CLUB' | 'ATHLETE' | 'INVITATION' | 'PLATFORM';

export interface AdminAuditEntry {
  id: string;
  actorUserId: string | null;
  actorEmail: string | null;
  action: string;
  actionLabel: string;
  sensitive: boolean;
  targetType: AuditTargetType;
  targetTypeLabel: string;
  targetId: string | null;
  targetLabel: string | null;
  summary: string | null;
  ipAddress: string | null;
  occurredAt: string;
}

export interface AdminAuditAction {
  value: string;
  label: string;
  sensitive: boolean;
}

// ---------------------------------------------------------------------------
// Recherche globale
// ---------------------------------------------------------------------------

export interface AdminSearchHit {
  id: string;
  title: string;
  subtitle: string | null;
  badge: string | null;
  route: string;
}

export interface AdminSearchResult {
  query: string;
  users: AdminSearchHit[];
  usersTotal: number;
  clubs: AdminSearchHit[];
  clubsTotal: number;
  athletes: AdminSearchHit[];
  athletesTotal: number;
}

// ---------------------------------------------------------------------------
// Clubs
// ---------------------------------------------------------------------------

export interface ClubAdmin {
  id: string;
  name: string;
  slug: string;
  status: ClubStatus;
  createdAt: string;
}

export interface ClubRequest {
  name: string;
  status?: ClubStatus;
}

export interface ClubMemberAdmin {
  id: string;
  fullName: string;
  email: string;
  role: UserRole;
  roleLabel: string;
  status: UserStatus;
  primaryClub: boolean;
  lastSeenAt: string | null;
}

/** Fiche club, aperçu d'impact de suppression compris. */
export interface ClubDetailAdmin {
  id: string;
  name: string;
  slug: string;
  status: ClubStatus;
  createdAt: string;
  coaches: number;
  athletes: number;
  athletesActive: number;
  athletesPaused: number;
  athletesArchived: number;
  pendingInvitations: number;
  workouts: number;
  activities: number;
  activities30d: number;
  deviceConnections: number;
  lastActivityDate: string | null;
  members: ClubMemberAdmin[];
}

// ---------------------------------------------------------------------------
// Utilisateurs
// ---------------------------------------------------------------------------

export interface ClubRef {
  id: string;
  name: string;
}

export interface AdminUser {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  clubId: string | null;
  clubName: string | null;
  athleteId: string | null;
  createdAt: string;
  /** Champs ajoutés côté serveur : optionnels, des PWA tournent encore sur l'ancien front. */
  additionalClubs?: ClubRef[];
  emailVerified?: boolean;
  lastSeenAt?: string | null;
  lastLoginAt?: string | null;
  invitePending?: boolean;
}

/** Fiche compte : tout ce qu'un ticket de support demande, en un seul appel. */
export interface AdminUserDetail {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  status: UserStatus;
  clubId: string | null;
  clubName: string | null;
  athleteId: string | null;
  additionalClubs: ClubRef[];
  emailVerified: boolean;
  invitePending: boolean;
  inviteExpiresAt: string | null;
  termsAcceptedAt: string | null;
  lastLoginAt: string | null;
  lastSeenAt: string | null;
  passwordChangedAt: string | null;
  sessionsInvalidatedAt: string | null;
  hasPassword: boolean;
  /** Faux pour un compte athlète créé par lien magique : aucun e-mail ne peut lui parvenir. */
  realEmail: boolean;
  pushSubscriptions: number;
  coachedAthletes: number;
  createdAt: string;
  history: AdminAuditEntry[];
}

export interface AdminUserCreateRequest {
  email: string;
  password: string;
  fullName: string;
  role: UserRole;
  clubId?: string | null;
}

export interface AdminUserUpdateRequest {
  fullName?: string;
  role?: UserRole;
  status?: UserStatus;
  clubId?: string | null;
}

// ---------------------------------------------------------------------------
// Athlètes & invitations
// ---------------------------------------------------------------------------

export interface AdminAthlete {
  id: string;
  firstName: string;
  lastName: string;
  email: string | null;
  clubId: string;
  clubName: string;
  level: AthleteLevel | null;
  status: AthleteStatus;
  invitationPending: boolean;
  createdAt: string;
  inviteExpiresAt?: string | null;
  coachCount?: number;
}

export interface InvitationAdmin {
  athleteId: string;
  firstName: string;
  lastName: string;
  clubName: string;
  expiresAt: string;
  email?: string | null;
  expired?: boolean;
  clubId?: string;
}

/** Lien régénéré : rendu au client car l'e-mail peut ne jamais arriver. */
export interface InvitationLink {
  url: string;
  expiresAt: string;
  emailSent: boolean;
}

// ---------------------------------------------------------------------------
// Configuration plateforme
// ---------------------------------------------------------------------------

export type PlatformSettingState = 'ON' | 'OFF' | 'PARTIAL';

export interface PlatformSetting {
  key: string;
  label: string;
  /** Porte la couleur de la pastille ; le sens est dans `stateLabel`. */
  state: PlatformSettingState;
  /**
   * Ce que cet état s'appelle. Distinct de `state` parce que « actif / inactif » ne convient pas
   * à tous les réglages : une inscription *libre* n'est pas une inscription *inactive*.
   */
  stateLabel?: string;
  detail: string;
  /** Variable d'environnement concernée — jamais sa valeur. */
  source: string;
}

export interface AdminPlatform {
  environment: string;
  version: string;
  timezone: string;
  frontendUrl: string;
  registrationMode: string;
  mailDailyQuota: number;
  mailMonthlyQuota: number;
  mailLogRetentionDays: number;
  settings: PlatformSetting[];
}

// ---------------------------------------------------------------------------
// Libellés (UI française, code anglais)
// ---------------------------------------------------------------------------

export const ROLE_LABELS: Record<UserRole, string> = {
  PLATFORM_ADMIN: 'Admin plateforme',
  HEAD_COACH: 'Responsable club',
  COACH: 'Coach',
  ATHLETE: 'Athlète',
};

export const USER_STATUS_LABELS: Record<UserStatus, string> = {
  ACTIVE: 'Actif',
  INVITED: 'Invité',
  SUSPENDED: 'Suspendu',
};

export const CLUB_STATUS_LABELS: Record<ClubStatus, string> = {
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
};

export const ATHLETE_LEVEL_LABELS: Record<AthleteLevel, string> = {
  BEGINNER: 'Débutant',
  INTERMEDIATE: 'Intermédiaire',
  ADVANCED: 'Avancé',
  ELITE: 'Élite',
};

export const ATHLETE_STATUS_LABELS: Record<AthleteStatus, string> = {
  ACTIVE: 'Actif',
  PAUSED: 'En pause',
  ARCHIVED: 'Archivé',
};

/** Pastille de statut : même vocabulaire de couleurs que le reste du produit. */
export function userStatusBadge(status: UserStatus): string {
  return status === 'ACTIVE' ? 'badge-success' : status === 'INVITED' ? 'badge-info' : 'badge-danger';
}

export function clubStatusBadge(status: ClubStatus): string {
  return status === 'ACTIVE' ? 'badge-success' : 'badge-danger';
}

// ---------------------------------------------------------------------------
// Strava & e-mails (inchangés)
// ---------------------------------------------------------------------------

/** Abonnement Strava aux événements d'activité, tel que Strava le renvoie. */
export interface StravaSubscription {
  id: number;
  callbackUrl: string;
}

/**
 * État du webhook Strava sur cet environnement. `configured` dit si les deux réglages
 * (adresse de rappel, jeton de validation) sont posés — sans eux, l'abonnement ne peut pas
 * être créé et la synchronisation reste horaire.
 */
export interface StravaWebhookState {
  configured: boolean;
  callbackUrl: string;
  subscriptions: StravaSubscription[];
}

/** Consommation d'e-mails : où en est-on du plafond, et qui le consomme. */
export interface MailStats {
  today: number;
  dailyQuota: number;
  month: number;
  monthlyQuota: number;
  failed7d: number;
  byDay: MailDay[];
  byKind: MailKindVolume[];
}

export interface MailDay {
  date: string;
  sent: number;
  failed: number;
}

export interface MailKindVolume {
  kind: string;
  label: string;
  /** Envoi sans autre canal de secours : c'est ce qu'on ne coupe jamais pour tenir un plafond. */
  transactional: boolean;
  count: number;
}

/** Une ligne du journal : sert à répondre à « untel a-t-il bien reçu son lien ? ». */
export interface MailLogEntry {
  recipient: string;
  subject: string;
  kind: string;
  label: string;
  audience: string | null;
  sent: boolean;
  errorMessage: string | null;
  sentAt: string;
}
