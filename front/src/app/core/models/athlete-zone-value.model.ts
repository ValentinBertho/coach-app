/**
 * Valeur d'une métrique d'une zone pour un athlète (fourchette min/max exprimée dans l'unité de la
 * métrique). `source=AUTO` : générée par le moteur ; `MANUAL` : imposée par le coach.
 * Cf. AUDIT-COACH-ZONES-METRIQUES §3.1 (chantier Z2).
 */
export type ZoneValueSource = 'AUTO' | 'MANUAL';

export interface AthleteZoneValue {
  zoneId: string;
  metricTypeId: string;
  valueMin: number | null;
  valueMax: number | null;
  source: ZoneValueSource;
  locked: boolean;
}

export interface AthleteZoneValueRequest {
  valueMin?: number | null;
  valueMax?: number | null;
  locked?: boolean | null;
}
