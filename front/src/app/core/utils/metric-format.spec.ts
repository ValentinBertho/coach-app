import { formatMetricRange, formatMetricValue, metricSuffix, secondsToMmss } from './metric-format';

describe('metric-format', () => {
  const pace = { unit: 'S_PER_KM' as const, format: 'MMSS' as const };
  const hr = { unit: 'BPM' as const, format: 'INT' as const };
  const speed = { unit: 'KMH' as const, format: 'DEC1' as const };

  it('écrit une allure en m:ss et non en secondes', () => {
    expect(formatMetricValue(pace, 275)).toBe('4:35');
    expect(secondsToMmss(305)).toBe('5:05');
  });

  it('arrondit une fréquence cardiaque à l’entier', () => {
    expect(formatMetricValue(hr, 152.4)).toBe('152');
  });

  it('garde une décimale pour une vitesse, à la française', () => {
    expect(formatMetricValue(speed, 12.34)).toBe('12,3');
  });

  it('n’écrit qu’une seule borne quand les deux sont égales', () => {
    expect(formatMetricRange(hr, 152, 152)).toBe('152 bpm');
  });

  it('assemble une fourchette avec son unité', () => {
    expect(formatMetricRange(pace, 275, 290)).toBe('4:35 – 4:50/km');
  });

  it('rend un tiret quand la valeur manque, plutôt qu’une case vide', () => {
    expect(formatMetricValue(pace, null)).toBe('—');
    expect(formatMetricRange(pace, null, null)).toBe('—');
  });

  it('porte le suffixe de chaque unité', () => {
    expect(metricSuffix(pace)).toBe('/km');
    expect(metricSuffix(hr)).toBe(' bpm');
    expect(metricSuffix({ unit: 'RPE', format: 'INT' })).toBe('');
  });
});
