export type PaceUnit = 'PACE' | 'SPEED';

export type UserRole = 'PLATFORM_ADMIN' | 'HEAD_COACH' | 'COACH' | 'ATHLETE';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  clubId: string | null;
  clubName: string | null;
  /**
   * La fiche de suivi de cet athlète, quand elle existe.
   *
   * Le serveur l'envoyait déjà ; le modèle ne le déclarait pas. Il devient nécessaire avec le
   * hub : un athlète inscrit de lui-même n'a PAS de fiche tant qu'aucun coach ne l'a accepté, et
   * c'est ce champ qui distingue « pas encore de coach » de « athlète suivi ».
   */
  athleteId?: string | null;
  emailVerified?: boolean;
  /** Unité d'affichage des allures préférée : PACE = min/km, SPEED = km/h. */
  paceUnit?: PaceUnit;
  /**
   * L'espace est celui d'un coach indépendant : l'interface cesse de parler de « club ».
   *
   * Optionnel à dessein : un client encore servi par un service worker antérieur ne reçoit pas ce
   * champ, et retombe alors sur le vocabulaire « club » d'avant — démodé, jamais cassé.
   */
  soloPractice?: boolean;
}

/**
 * Mot de passe correct, second facteur attendu (administrateurs). Au premier passage
 * (`enrollment`), le secret est à enregistrer dans l'application d'authentification.
 */
export interface MfaChallenge {
  challengeToken: string;
  enrollment: boolean;
  secret: string | null;
  otpauthUri: string | null;
  expiresIn: number;
}

export interface AuthResponse {
  /** Absent tant que le second facteur n'est pas relevé (voir `mfa`). */
  accessToken: string;
  /**
   * Le jeton de rafraîchissement n'est plus jamais dans le corps : le serveur le dépose dans un
   * cookie `HttpOnly` que ce code ne peut ni lire ni écrire. Le champ reste `null` à la lecture.
   */
  refreshToken?: string | null;
  expiresIn: number;
  user: User;
  /** Défi de double authentification : aucun jeton n'est émis tant qu'il n'est pas relevé. */
  mfa?: MfaChallenge | null;
  /** Codes de secours, présents une seule fois : à la fin du premier enrôlement. */
  recoveryCodes?: string[] | null;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  /** Nom de la structure ; facultatif quand le coach exerce en indépendant. */
  clubName?: string;
  /** Le coach exerce seul : pas de club à nommer, et on ne lui en parlera pas. */
  soloPractice: boolean;
  /** Acceptation des CGU / politique de confidentialité (requise, horodatée côté serveur). */
  termsAccepted: boolean;
  /** Code de la cohorte, exigé seulement quand le serveur est en mode d'inscription « invite ». */
  invitationCode?: string;
}

/**
 * Comment on entre sur cette instance, tel que le serveur le déclare.
 *
 * - `OPEN` — le formulaire crée le club immédiatement.
 * - `INVITE` — il faut un code partagé, distribué par e-mail.
 * - `REQUEST` — le formulaire dépose une demande ; un administrateur la valide, et c'est la
 *   validation qui ouvre le club. C'est le régime de la bêta ouverte.
 */
export type RegistrationMode = 'OPEN' | 'INVITE' | 'REQUEST';

export interface RegistrationModeInfo {
  mode: RegistrationMode;
  label: string;
}

/** Dépôt d'une demande de création de club. Aucun mot de passe : rien n'est créé au dépôt. */
export interface ClubCreationRequestSubmission {
  email: string;
  fullName: string;
  /** Nom de la structure ; facultatif quand le candidat exerce en indépendant. */
  clubName?: string;
  /** Le candidat exerce seul : la validation ouvrira un espace solo. */
  soloPractice: boolean;
  phone?: string;
  message?: string;
  termsAccepted: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}
