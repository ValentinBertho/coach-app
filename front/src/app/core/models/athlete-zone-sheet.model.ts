import { MetricFormat, MetricUnit } from './metric-type.model';
import { ZoneAnchor } from './training-zone.model';

/**
 * L'échelle de zones telle que l'athlète la lit (`GET /me/zones`).
 *
 * <p>Composée par le serveur, à la différence de l'écran du coach qui assemble trois catalogues de
 * club dans le navigateur : l'athlète n'a pas à recevoir le paramétrage de son club pour lire ses
 * propres allures.</p>
 */
export interface AthleteZoneSheet {
  zoneId: string;
  name: string;
  color: string | null;
  description: string | null;
  sortOrder: number;
  metrics: AthleteZoneMetric[];
}

/**
 * Une métrique de la zone : ce qu'elle mesure, la fourchette calculée pour cet athlète, et la
 * règle dont elle sort.
 */
export interface AthleteZoneMetric {
  metricTypeId: string;
  code: string;
  name: string;
  unit: MetricUnit;
  format: MetricFormat;
  valueMin: number | null;
  valueMax: number | null;
  /** `AUTO` = calculée depuis son profil ; `MANUAL` = fixée par son coach. */
  source: 'AUTO' | 'MANUAL' | null;
  anchor: ZoneAnchor | null;
  highAnchor: ZoneAnchor | null;
  lowPct: number | null;
  highPct: number | null;
}
