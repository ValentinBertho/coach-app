export type Sex = 'MALE' | 'FEMALE' | 'OTHER';
export type AthleteLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'ELITE';

/**
 * Libellés français des niveaux. Vivaient dans `athlete-shell.component.ts`, d'où la liste
 * d'athlètes — le seul écran où le coach relit la valeur — affichait l'énuméré brut.
 */
export const ATHLETE_LEVEL_LABELS: Record<AthleteLevel, string> = {
  BEGINNER: 'Débutant',
  INTERMEDIATE: 'Intermédiaire',
  ADVANCED: 'Avancé',
  ELITE: 'Élite',
};
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
  /** Le coach courant peut-il prescrire/modifier cet athlète (sinon lecture seule) ? */
  canWrite?: boolean;
}

export interface Athlete extends AthleteSummary {
  /**
   * L'athlète autorise-t-il actuellement le traitement de ses données de santé (RGPD art. 9) ?
   *
   * <p>Faux tant qu'il n'a pas accepté son invitation, et après un retrait de consentement.
   * Le serveur refuse alors les tests de lactate, les douleurs et les motifs médicaux : sans
   * cette information, le coach subissait le refus sans pouvoir le comprendre — et, avant que la
   * garde existe, saisissait des données de santé sans base légale.</p>
   */
  healthDataConsentGranted: boolean;
  healthDataConsentAt?: string | null;
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
