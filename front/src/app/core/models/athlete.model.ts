export type Sex = 'MALE' | 'FEMALE' | 'OTHER';
export type AthleteLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'ELITE';
export type AthleteStatus = 'ACTIVE' | 'PAUSED' | 'ARCHIVED';

/** Référence légère (id + libellé) d'une entité liée (coach, club…). */
export interface Ref {
  id: string;
  name: string;
}

export interface AthleteSummary {
  id: string;
  firstName: string;
  lastName: string;
  level: AthleteLevel | null;
  status: AthleteStatus;
  invitationPending: boolean;
  groupName?: string | null;
}

export interface Athlete extends AthleteSummary {
  groupId?: string | null;
  email: string | null;
  birthDate: string | null;
  sex: Sex | null;
  hrMax: number | null;
  hrRest: number | null;
  vma: number | null;
  weightKg: number | null;
  medicalNotes: string | null;
  /** Coachs rattachés (modèle many-to-many). */
  coaches: Ref[];
  /** Clubs de l'athlète : principal + additionnels (modèle many-to-many). */
  clubs: Ref[];
}

export interface AthleteRequest {
  firstName: string;
  lastName: string;
  email?: string | null;
  birthDate?: string | null;
  sex?: Sex | null;
  level?: AthleteLevel | null;
  hrMax?: number | null;
  hrRest?: number | null;
  vma?: number | null;
  weightKg?: number | null;
  medicalNotes?: string | null;
  groupId?: string | null;
  privateAthlete?: boolean;
}

export interface AthleteInvitation {
  inviteUrl: string;
  expiresAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
