import { UserRole } from './user.model';
import { AthleteLevel, AthleteStatus } from './athlete.model';

export type ClubStatus = 'ACTIVE' | 'SUSPENDED';
export type UserStatus = 'ACTIVE' | 'INVITED' | 'SUSPENDED';

export interface AdminStats {
  clubs: number;
  headCoaches: number;
  coaches: number;
  athletes: number;
  pendingInvitations: number;
  workouts: number;
  activities: number;
}

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
}

export interface InvitationAdmin {
  athleteId: string;
  firstName: string;
  lastName: string;
  clubName: string;
  expiresAt: string;
}

export const ROLE_LABELS: Record<UserRole, string> = {
  PLATFORM_ADMIN: 'Admin plateforme',
  HEAD_COACH: 'Responsable club',
  COACH: 'Coach',
  ATHLETE: 'Athlète',
};

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
