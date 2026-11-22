import type { StrengthPrescription } from '../models/strength.model';
import {
  applySetRows, effectiveVolumeType, isRestRange, restPill, setRowsOf, setTargetLabel, sideLabel,
  volumePill,
} from './strength-volume';

/**
 * La lecture du volume d'un exercice de force.
 *
 * <p>Ce que ces tests protègent : une prescription doit se lire à l'identique dans l'éditeur du
 * coach, dans la vue figée et dans le mode séance de l'athlète. « 45 » sans unité, ou « 8 » là où
 * le coach voulait dire « 8 par jambe », c'est une séance qui n'est pas faite comme elle a été
 * prescrite — et la compatibilité des séances déjà en base en fait partie : aucune ne doit
 * changer de sens parce que le modèle a gagné des champs.</p>
 */
describe('strength-volume — volume prescrit d’un exercice', () => {
  const reps: StrengthPrescription = { sets: 4, repsFixed: 8 };

  describe('unité effective', () => {
    it('lit en répétitions une prescription d’avant (sans volumeType)', () => {
      expect(effectiveVolumeType(reps)).toBe('REPS');
    });

    /** Le seul endroit où `durationSec` était saisissable avant : le bloc d'isométrie. */
    it('lit en durée une prescription d’avant dans un bloc d’isométrie', () => {
      expect(effectiveVolumeType({ durationSec: 45 }, 'ISOMETRIE')).toBe('DUREE');
    });

    it('respecte le volumeType prescrit, quel que soit le format du bloc', () => {
      expect(effectiveVolumeType({ volumeType: 'DISTANCE', distanceM: 30 }, 'ISOMETRIE')).toBe('DISTANCE');
      expect(effectiveVolumeType({ volumeType: 'REPS', repsFixed: 5 }, 'ISOMETRIE')).toBe('REPS');
    });
  });

  describe('pastille de volume', () => {
    it('écrit une valeur exacte avec deux bornes égales', () => {
      expect(volumePill(reps)).toEqual({ label: 'Reps', min: 8, max: 8, unit: '' });
    });

    it('écrit une fourchette de répétitions', () => {
      expect(volumePill({ volumeType: 'REPS', repsMin: 8, repsMax: 10 }))
        .toEqual({ label: 'Reps', min: 8, max: 10, unit: '' });
    });

    it('porte l’unité d’une durée et d’une distance — « 45 » seul ne veut rien dire', () => {
      expect(volumePill({ volumeType: 'DUREE', durationSec: 45 }))
        .toEqual({ label: 'Durée', min: 45, max: 45, unit: 's' });
      expect(volumePill({ volumeType: 'DISTANCE', distanceM: 30, distanceMMax: 40 }))
        .toEqual({ label: 'Distance', min: 30, max: 40, unit: 'm' });
    });

    it('ne montre rien plutôt qu’un zéro quand le volume n’est pas prescrit', () => {
      expect(volumePill({ sets: 3 })).toBeNull();
      expect(volumePill({ volumeType: 'DUREE' })).toBeNull();
      expect(volumePill(null)).toBeNull();
    });
  });

  describe('latéralité', () => {
    it('ne dit rien en bilatéral — c’est le cas par défaut', () => {
      expect(sideLabel({ sideMode: 'BILATERAL' })).toBeNull();
      expect(sideLabel(reps)).toBeNull();
    });

    it('nomme l’alternance et le travail par côté', () => {
      expect(sideLabel({ sideMode: 'ALTERNE' })).toBe('Alterné G / D');
      expect(sideLabel({ sideMode: 'PAR_COTE' })).toBe('Par côté');
    });
  });

  describe('repos', () => {
    it('distingue un repos strict d’une fourchette', () => {
      expect(restPill({ restSecMin: 90 })).toEqual({ label: 'Récup', min: 90, max: 90, unit: 's' });
      expect(isRestRange({ restSecMin: 90 })).toBeFalse();

      expect(restPill({ restSecMin: 90, restSecMax: 120 }))
        .toEqual({ label: 'Récup', min: 90, max: 120, unit: 's' });
      expect(isRestRange({ restSecMin: 90, restSecMax: 120 })).toBeTrue();
    });

    /** Deux bornes identiques, c'est un repos strict écrit en deux fois. */
    it('ne prend pas deux bornes égales pour une fourchette', () => {
      expect(isRestRange({ restSecMin: 90, restSecMax: 90 })).toBeFalse();
    });
  });

  describe('séries une à une (pyramide)', () => {
    it('montre une prescription d’avant comme N séries identiques', () => {
      expect(setRowsOf({ sets: 3, repsFixed: 8 })).toEqual([
        { value: 8, valueMax: null }, { value: 8, valueMax: null }, { value: 8, valueMax: null },
      ]);
      expect(setRowsOf({ sets: 2, volumeType: 'DUREE', durationSec: 30, durationSecMax: 45 }))
        .toEqual([{ value: 30, valueMax: 45 }, { value: 30, valueMax: 45 }]);
    });

    it('des séries identiques restent « 3 × 8 », sans détail série par série', () => {
      const p: StrengthPrescription = { sets: 2, repsFixed: 5 };
      applySetRows(p, null, [{ value: 8 }, { value: 8 }, { value: 8 }]);
      expect(p.sets).toBe(3);
      expect(p.repsFixed).toBe(8);
      expect(p.setTargets).toBeNull();
    });

    it('garde chaque série quand elles diffèrent, avec l’enveloppe min–max en scalaire', () => {
      const p: StrengthPrescription = { sets: 3, repsFixed: 8 };
      applySetRows(p, null, [{ value: 12 }, { value: 10 }, { value: 8 }]);
      expect(p.setTargets).toEqual([
        { value: 12, valueMax: null }, { value: 10, valueMax: null }, { value: 8, valueMax: null },
      ]);
      expect(p.repsFixed).toBeNull();
      expect([p.repsMin, p.repsMax]).toEqual([8, 12]);
      expect(volumePill(p)?.perSet).toBe('12 / 10 / 8');
      expect(setTargetLabel(p, null, 1)).toBe('10 reps');
    });

    it('écrit dans l’unité choisie : durée en secondes, distance en mètres', () => {
      const p: StrengthPrescription = { sets: 2, volumeType: 'DISTANCE', distanceM: 20 };
      applySetRows(p, null, [{ value: 20 }, { value: 40 }]);
      expect([p.distanceM, p.distanceMMax]).toEqual([20, 40]);
      expect(volumePill(p)?.perSet).toBe('20 / 40 m');
      expect(setTargetLabel(p, null, 1)).toBe('40 m');
      expect(p.repsFixed).toBeUndefined();
    });

    it('sans détail série par série, rien ne change à l’affichage', () => {
      expect(volumePill({ sets: 3, repsFixed: 8 })?.perSet).toBeUndefined();
      expect(setTargetLabel({ sets: 3, repsFixed: 8 }, null, 0)).toBeNull();
    });
  });
});
