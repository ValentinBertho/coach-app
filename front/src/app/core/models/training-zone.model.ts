/**
 * Zone de travail nommée par le coach (nombre libre, ré-ordonnable). `metricTypeIds` = métriques
 * portées par la zone, à résoudre contre le catalogue. Cf. AUDIT-COACH-ZONES-METRIQUES §3.1.
 */
export type ZoneScope = 'CLUB' | 'COACH';

export interface TrainingZone {
  id: string;
  name: string;
  color: string | null;
  description: string | null;
  sortOrder: number;
  scope: ZoneScope;
  discipline: string | null;
  builtin: boolean;
  metricTypeIds: string[];
}

export interface TrainingZoneRequest {
  name: string;
  color?: string | null;
  description?: string | null;
  scope?: ZoneScope | null;
  discipline?: string | null;
  sortOrder?: number | null;
}

export interface TrainingZoneReorderRequest {
  orderedIds: string[];
}

export interface ZoneMetricsRequest {
  metricTypeIds: string[];
}
