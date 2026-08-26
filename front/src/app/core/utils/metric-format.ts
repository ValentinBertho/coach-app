import { MetricFormat, MetricUnit } from '../models/metric-type.model';

/**
 * Mise en forme d'une valeur de zone selon son unité.
 *
 * <p>Cette logique existait en trois exemplaires — écran des zones du coach, sélecteur de zone de
 * l'éditeur, et maintenant l'écran de l'athlète. Trois copies d'une même règle d'affichage, c'est
 * trois occasions de diverger : le jour où l'une arrondit une allure et pas l'autre, coach et
 * athlète lisent deux chiffres différents pour la même zone et n'ont aucun moyen de le savoir.</p>
 *
 * <p>La forme n'est pas cosmétique. Une allure se lit en `m:ss` au kilomètre, jamais en secondes
 * décimales ; une fréquence cardiaque est un entier. Le champ `unit` porte le sens, `format` la
 * précision.</p>
 */
export interface MetricShape {
  unit: MetricUnit;
  format: MetricFormat;
}

/** Une valeur seule, sans son unité (« 4:35 », « 152 »). */
export function formatMetricValue(m: MetricShape, v: number | null | undefined): string {
  if (v == null) return '—';
  if (m.unit === 'S_PER_KM' || m.format === 'MMSS') return secondsToMmss(v);
  if (m.format === 'DEC1') return (Math.round(v * 10) / 10).toString().replace('.', ',');
  return Math.round(v).toString();
}

/** Suffixe d'unité, espace insécable de présentation comprise (« /km », « bpm »). */
export function metricSuffix(m: MetricShape): string {
  switch (m.unit) {
    case 'S_PER_KM':
      return '/km';
    case 'BPM':
      return ' bpm';
    case 'KMH':
      return ' km/h';
    case 'PCT':
      return ' %';
    case 'W':
      return ' W';
    default:
      return '';
  }
}

/**
 * Fourchette complète, unité comprise. Deux bornes égales s'écrivent une seule fois : « 152 bpm »
 * plutôt que « 152 – 152 bpm ».
 *
 * @param dash séparateur ; l'écran des zones du coach aère avec des espaces, le sélecteur non.
 */
export function formatMetricRange(
  m: MetricShape,
  min: number | null | undefined,
  max: number | null | undefined,
  dash = ' – '
): string {
  if (min == null && max == null) return '—';
  const lo = formatMetricValue(m, min);
  const hi = formatMetricValue(m, max);
  return `${lo === hi ? lo : `${lo}${dash}${hi}`}${metricSuffix(m)}`;
}

/** Secondes → `m:ss`. Une allure de 275 s/km s'écrit « 4:35 ». */
export function secondsToMmss(sec: number): string {
  const s = Math.round(sec);
  return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, '0')}`;
}
