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
  /**
   * L'espace est celui d'un coach indépendant : l'interface cesse de parler de « club ».
   *
   * Optionnel à dessein : un client encore servi par un service worker antérieur ne reçoit pas ce
   * champ, et retombe alors sur le vocabulaire « club » d'avant — démodé, jamais cassé.
   */
  soloPractice?: boolean;
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
