export type PaceUnit = 'PACE' | 'SPEED';

export type UserRole = 'PLATFORM_ADMIN' | 'HEAD_COACH' | 'COACH' | 'ATHLETE';

export interface User {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  clubId: string | null;
  clubName: string | null;
  emailVerified?: boolean;
  /** Unité d'affichage des allures préférée : PACE = min/km, SPEED = km/h. */
  paceUnit?: PaceUnit;
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
  clubName: string;
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
  clubName: string;
  phone?: string;
  message?: string;
  termsAccepted: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}
