import {
  formatBlockDistance, formatBlockDuration, formatBlockSets, formatBlockVolume,
} from './prescription-format';

/**
 * L'écriture du volume d'un bloc de séance.
 *
 * <p>La même séance s'affichait « 2 × (8 × 30 s) » sur un écran et « 2 × (8 × 1 min) » sur un
 * autre : un formateur arrondissait à la minute. Un athlète qui suit le second court le double,
 * et rien ne le lui signale. Ces tests fixent la convention, et surtout l'interdiction d'arrondir.</p>
 */
describe('prescription-format — volume d’un bloc', () => {
  describe('durée', () => {
    it('écrit les secondes telles quelles sous la minute', () => {
      expect(formatBlockDuration(30)).toBe('30 s');
      expect(formatBlockDuration(45)).toBe('45 s');
      expect(formatBlockDuration(59)).toBe('59 s');
    });

    /** Le défaut corrigé : 30 s devenait « 1 min », 90 s devenait « 2 min ». */
    it('n’arrondit jamais à la minute', () => {
      expect(formatBlockDuration(30)).not.toBe('1 min');
      expect(formatBlockDuration(90)).toBe("1'30");
      expect(formatBlockDuration(45)).not.toBe('1 min');
      expect(formatBlockDuration(100)).toBe("1'40");
    });

    it('écrit « min » quand les minutes sont entières', () => {
      expect(formatBlockDuration(60)).toBe('1 min');
      expect(formatBlockDuration(300)).toBe('5 min');
      expect(formatBlockDuration(1200)).toBe('20 min');
    });

    it('passe en heures au-delà de soixante minutes', () => {
      expect(formatBlockDuration(3600)).toBe('1h00');
      expect(formatBlockDuration(3900)).toBe('1h05');
      expect(formatBlockDuration(5430)).toBe('1h30');
    });

    it('ne dit rien d’une durée absente ou nulle', () => {
      expect(formatBlockDuration(null)).toBe('');
      expect(formatBlockDuration(0)).toBe('');
      expect(formatBlockDuration(undefined)).toBe('');
    });
  });

  describe('distance', () => {
    it('écrit les mètres sous le kilomètre, et le kilomètre sans décimale inutile', () => {
      expect(formatBlockDistance(400)).toBe('400 m');
      expect(formatBlockDistance(1000)).toBe('1 km');
      expect(formatBlockDistance(1250)).toBe('1,25 km');
      expect(formatBlockDistance(1200)).toBe('1,2 km');
      expect(formatBlockDistance(18000)).toBe('18 km');
    });

    it('ne dit rien d’une distance absente ou nulle', () => {
      expect(formatBlockDistance(null)).toBe('');
      expect(formatBlockDistance(0)).toBe('');
    });
  });

  /** La distance prime : un bloc qui en porte une est prescrit en distance. */
  it('préfère la distance à la durée', () => {
    expect(formatBlockVolume(400, 90)).toBe('400 m');
    expect(formatBlockVolume(null, 90)).toBe("1'30");
    expect(formatBlockVolume(null, null)).toBe('');
  });

  describe('répétitions et séries', () => {
    it('compose les répétitions', () => {
      expect(formatBlockSets({ durationS: 30, reps: 8 })).toBe('8 × 30 s');
      expect(formatBlockSets({ distanceM: 400, reps: 10 })).toBe('10 × 400 m');
    });

    /** Les parenthèses distinguent deux séries de six d'une seule série de douze. */
    it('parenthèse les séries', () => {
      expect(formatBlockSets({ durationS: 30, reps: 8, sets: 2 })).toBe('2 × (8 × 30 s)');
      expect(formatBlockSets({ distanceM: 400, reps: 6, sets: 2 })).toBe('2 × (6 × 400 m)');
    });

    it('n’ajoute rien quand il n’y a ni série ni répétition', () => {
      expect(formatBlockSets({ durationS: 1200, reps: 1, sets: 1 })).toBe('20 min');
      expect(formatBlockSets({ durationS: 1200 })).toBe('20 min');
    });

    it('ne dit rien d’un bloc sans volume', () => {
      expect(formatBlockSets({ reps: 8, sets: 2 })).toBe('');
    });
  });
});
