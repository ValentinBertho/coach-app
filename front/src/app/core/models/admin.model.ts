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
