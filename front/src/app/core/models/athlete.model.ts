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
  /** Le suivi est terminé : la fiche reste en lecture chez le coach qui l'a tenue. */
  coachingEnded?: boolean;
  /** Coup d'œil sur l'entraînement, fourni par la liste des athlètes. */
  glance?: AthleteGlance | null;
}

export interface AthleteGlance {
  lastSessionDate: string | null;
  lastSessionStatus: 'PLANNED' | 'COMPLETED' | 'PARTIAL' | 'MISSED' | null;
  done7: number;
  planned7: number;
  nextRaceName: string | null;
  nextRaceDate: string | null;
}

export interface Athlete extends AthleteSummary {
  /**
   * La personne se connecte-t-elle à l'application ? Décide si « mettre fin au suivi » a un sens :
   * un athlète saisi par son coach, sans accès, n'a rien à quitter.
   */
  appAccount?: boolean;
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
  /**
   * Statut du profil. Facultatif : omis, le statut reste inchangé. Seule l'administration
   * l'envoie — côté coach, l'archivage garde sa route dédiée.
   */
  status?: AthleteStatus | null;
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
