/**
 * Zone de travail nommée par le coach (nombre libre, ré-ordonnable). `metricTypeIds` = métriques
 * portées par la zone, à résoudre contre le catalogue. Cf. AUDIT-COACH-ZONES-METRIQUES §3.1.
 */
export type ZoneScope = 'CLUB' | 'COACH';

/** Ancre de calcul d'une bande de zone (chantier zones v2). */
export type ZoneAnchor =
  | 'LT1' | 'LT2' | 'VC' | 'VMA'
  | 'PACE_800M' | 'PACE_1500M' | 'PACE_3000M' | 'PACE_5KM'
  | 'PACE_10KM' | 'PACE_15KM' | 'PACE_SEMI' | 'PACE_MARATHON'
  | 'FCMAX' | 'LTHR' | 'HRR';

/** Modèle nommé de calcul des zones. */
export type ZoneModel =
  | 'VMA' | 'VC' | 'DANIELS_VDOT' | 'LACTATE_THRESHOLD' | 'PCT_FCMAX' | 'CUSTOM';

/** Règle de calcul d'une métrique d'une zone : ancre + fourchette % + modèle. */
export interface ZoneRule {
  metricTypeId: string;
  anchor: ZoneAnchor | null;
  lowPct: number | null;
  highPct: number | null;
  model: ZoneModel | null;
}

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
  rules: ZoneRule[];
}

export interface ZoneRuleRequest {
  anchor: ZoneAnchor | null;
  lowPct: number | null;
  highPct: number | null;
  model: ZoneModel | null;
}

/** Libellés FR des ancres et modèles (affichage UI). */
export const ZONE_ANCHOR_LABELS: Record<ZoneAnchor, string> = {
  LT1: 'Seuil aérobie (LT1)',
  LT2: 'Seuil lactique (LT2)',
  VC: 'Vitesse critique (VC)',
  VMA: 'VMA',
  PACE_800M: 'Allure 800 m',
  PACE_1500M: 'Allure 1500 m',
  PACE_3000M: 'Allure 3000 m',
  PACE_5KM: 'Allure 5 km',
  PACE_10KM: 'Allure 10 km',
  PACE_15KM: 'Allure 15 km',
  PACE_SEMI: 'Allure semi',
  PACE_MARATHON: 'Allure marathon',
  FCMAX: 'FC max',
  LTHR: 'FC au seuil (LT2)',
  HRR: 'Réserve cardiaque',
};

export const ZONE_MODEL_LABELS: Record<ZoneModel, string> = {
  VMA: 'VMA',
  VC: 'Vitesse critique',
  DANIELS_VDOT: 'Daniels / VDOT',
  LACTATE_THRESHOLD: 'Seuils lactiques',
  PCT_FCMAX: '% FC max',
  CUSTOM: 'Personnalisé',
};

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
