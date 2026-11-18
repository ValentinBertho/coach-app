export interface StravaStatus {
  configured: boolean;
  connected: boolean;
  providerAthleteId: string | null;
  /** Curseur d'import (départ de la dernière sortie vue, reculé de deux jours), pas une heure de synchro. */
  lastImportEpoch: number | null;
  /** Fin de la dernière synchro réussie (epoch, secondes) ; null avant la première. */
  lastSyncEpoch: number | null;
  /** L'athlète a accepté que Darilab renomme ses sorties sur son compte Strava. */
  renameOnStrava: boolean;
  /** Strava nous a bien accordé le droit d'écrire (scope `activity:write`). */
  canRenameOnStrava: boolean;
}
