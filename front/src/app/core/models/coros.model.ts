export interface CorosStatus {
  /** Intégration ouverte sur ce serveur (COROS_ENABLED). */
  configured: boolean;
  connected: boolean;
  /** Identifiant COROS de l'utilisateur, opaque ; null si COROS ne l'a pas fourni. */
  providerUserId: string | null;
  /** Serveur régional COROS du compte : EU, US, CN, ou null si inconnu. */
  region: string | null;
}
