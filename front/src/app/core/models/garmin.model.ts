export interface GarminStatus {
  /** Intégration configurée sur ce serveur (identifiants Garmin présents). */
  configured: boolean;
  connected: boolean;
  /** Identifiant Garmin de l'utilisateur, opaque et stable. */
  providerUserId: string | null;
  /** Permissions accordées chez Garmin (WORKOUT_IMPORT, ACTIVITY_EXPORT…). */
  permissions: string[];
  /** Garmin nous autorise à déposer des séances dans son calendrier. */
  canSendWorkouts: boolean;
  /** Garmin nous autorise à recevoir ses activités. */
  canImportActivities: boolean;
  /** Séances à venir actuellement dans son calendrier Garmin. */
  workoutsOnGarmin: number;
  /** Séances à venir dont l'envoi a échoué (retentées automatiquement). */
  workoutsInError: number;
}
