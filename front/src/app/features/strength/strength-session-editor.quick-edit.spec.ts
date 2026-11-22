import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { StrengthBlock, StrengthExerciseItem } from '../../core/models/strength.model';
import { StrengthSessionEditorComponent } from './strength-session-editor.component';

/**
 * Panneau « Éditer l'exercice » allégé.
 *
 * <p><b>Ce que ces tests protègent.</b> Un coach : « rendre ça moins chargé, juste séries,
 * répétitions, tempo, RIR/RPE, charge, %RM », et « choisir Cluster ne change rien ». Une borne
 * max vide vaut valeur exacte, remplie elle fait une fourchette ; une méthode de série porte
 * ses propres réglages.</p>
 */
describe('strength-session-editor — édition rapide d’un exercice', () => {
  let c: StrengthSessionEditorComponent;
  const block: StrengthBlock = {
    id: 'b', blockType: 'PRINCIPAL', format: 'CLASSIQUE', exercises: [],
  };
  let item: StrengthExerciseItem;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    c = TestBed.createComponent(StrengthSessionEditorComponent).componentInstance;
    item = {
      exerciseId: 'e', exerciseName: 'Back squat', setType: 'STANDARD',
      prescription: { sets: 4, volumeType: 'REPS', repsFixed: 6, chargeRefType: 'PCT_RM_RANGE',
        chargePctRmMin: 70, chargePctRmMax: 80, effortRefType: 'RIR_RANGE', rirMin: 1, rirMax: 3 },
    };
  });

  it('une borne max fait une fourchette, la vider revient à la valeur exacte', () => {
    c.setVolHigh(block, item, 8);
    expect(item.prescription).toEqual(jasmine.objectContaining({ repsFixed: null, repsMin: 6, repsMax: 8 }));
    c.setVolLow(block, item, 5);
    expect(item.prescription.repsMin).toBe(5);
    c.setVolHigh(block, item, '');
    expect(item.prescription).toEqual(jasmine.objectContaining({ repsFixed: 5, repsMin: null, repsMax: null }));
  });

  it('séries une à une : pyramide 12 / 10 / 8, ajout et retrait de série', () => {
    c.setRowValue(block, item, 0, 'value', 12);
    c.setRowValue(block, item, 1, 'value', 10);
    c.setRowValue(block, item, 2, 'value', 8);
    expect(c.setRows(block, item).map((r) => r.value)).toEqual([12, 10, 8, 6]);
    c.removeSetRow(block, item, 3);
    c.addSetRow(block, item);
    expect(c.setRows(block, item).map((r) => r.value)).toEqual([12, 10, 8, 8]);
    expect(item.prescription.sets).toBe(4);
    expect(c.inlineVolume(block, item)).toBeNull();
    c.setInlineSets(item, 2);
    expect(c.setRows(block, item).map((r) => r.value)).toEqual([12, 10]);
  });

  it('Travail en durée ou distance : l’unité change pour toutes les séries, leur nombre reste', () => {
    c.setRowValue(block, item, 0, 'value', 12);
    c.setVolumeType(block, item, 'DUREE');
    expect(item.prescription.setTargets).toBeNull();
    expect(c.setRows(block, item)).toEqual(Array(4).fill({ value: 30, valueMax: null }));
    expect(c.workUnit(block, item)).toBe('s');
    c.setVolumeType(block, item, 'DISTANCE');
    expect(c.workUnit(block, item)).toBe('m');
    expect(item.prescription.repsFixed).toBeNull();
  });

  it('charge : passe de fourchette à valeur exacte, et de % RM à kg', () => {
    c.setChargeHigh(item, null);
    expect(item.prescription.chargeRefType).toBe('PCT_RM');
    c.setChargeMode(item, 'KG');
    expect(c.chargeMode(item)).toBe('KG');
    c.setChargeLow(item, 60);
    c.setChargeHigh(item, 65);
    expect(item.prescription).toEqual(jasmine.objectContaining({ chargeRefType: 'KG_RANGE', chargeKgMin: 60, chargeKgMax: 65 }));
  });

  it('effort : RIR ou RPE, exact ou en fourchette', () => {
    c.setEffortMode(item, 'RPE');
    expect(item.prescription.effortRefType).toBe('RPE');
    c.setEffortLow(item, 8);
    c.setEffortHigh(item, 9);
    expect(item.prescription).toEqual(jasmine.objectContaining({ effortRefType: 'RPE_RANGE', rpeMin: 8, rpeMax: 9 }));
  });

  it('choisir Cluster pose ses réglages et les dit à l’athlète', () => {
    c.setSetMethod(item, 'CLUSTER');
    expect(item.setConfig).toEqual({ clusters: 3, repsPerCluster: 2, pauseSec: 15 });
    c.setMethodValue(item, 'pauseSec', 20);
    expect(c.methodSummary(item)).toBe('Cluster : 3 blocs de 2 reps, 20 s de pause entre les blocs');
  });

  it('une iso se prescrit en secondes', () => {
    c.setSetMethod(item, 'ISO_YIELDING');
    expect(item.prescription.volumeType).toBe('DUREE');
    expect(item.prescription.durationSec).toBe(30);
    expect(item.prescription.repsFixed).toBeNull();
  });
});
