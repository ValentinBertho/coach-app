export interface StravaStatus {
  configured: boolean;
  connected: boolean;
  providerAthleteId: string | null;
  lastImportEpoch: number | null;
}
