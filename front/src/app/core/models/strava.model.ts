export interface StravaStatus {
  configured: boolean;
  connected: boolean;
  providerAthleteId: string | null;
  lastImportEpoch: number | null;
  /** L'athlète a accepté que Darilab renomme ses sorties sur son compte Strava. */
  renameOnStrava: boolean;
  /** Strava nous a bien accordé le droit d'écrire (scope `activity:write`). */
  canRenameOnStrava: boolean;
}
