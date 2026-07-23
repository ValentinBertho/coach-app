/**
 * Métrique mesurable d'une zone (catalogue extensible). `clubId` null = métrique globale builtin ;
 * sinon métrique custom du club. Cf. AUDIT-COACH-ZONES-METRIQUES §3.1 (chantier Z1).
 */
export type MetricUnit = 'S_PER_KM' | 'BPM' | 'KMH' | 'PCT' | 'W' | 'RPE';
export type MetricFormat = 'MMSS' | 'INT' | 'DEC1';
export type MetricDirection = 'HIGHER_HARDER' | 'LOWER_HARDER';

export interface MetricType {
  id: string;
  clubId: string | null;
  code: string;
  name: string;
  unit: MetricUnit;
  format: MetricFormat;
  direction: MetricDirection;
  sortOrder: number;
  builtin: boolean;
}

export interface MetricTypeRequest {
  code: string;
  name: string;
  unit: MetricUnit;
  format: MetricFormat;
  direction: MetricDirection;
  sortOrder?: number | null;
}
