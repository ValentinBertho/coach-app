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

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
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
