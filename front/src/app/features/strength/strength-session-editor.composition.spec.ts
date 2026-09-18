import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { PpExercise, StrengthBlock } from '../../core/models/strength.model';
import { StrengthSessionEditorComponent } from './strength-session-editor.component';

/**
 * Composer une séance de prépa physique.
 *
 * <p><b>Ce que ces tests protègent.</b> Un coach bêta : « tu penses que ça peut être plus simple
 * pour créer des séances de prépa-physique ? ». L'éditeur demandait d'abord du décor — créer un
 * bloc, choisir son type, choisir son format parmi sept — avant de permettre le premier exercice,
 * et tout exercice ajouté naissait « 4 × 6 à 70–80 % du 1RM », y compris dans un échauffement.</p>
 */
describe('strength-session-editor — composition', () => {
  let component: StrengthSessionEditorComponent;

  function exercise(id = 'e1', name = 'Squat'): PpExercise {
    return {
      id, name, category: 'FORCE_MAX', categoryId: null, level: null, objective: null,
      muscleGroups: [], equipment: [], videoUrl: null, imageUrl: null, instructions: null,
      technicalNotes: null, contraindications: null, progressionId: null, regressionId: null,
      favorite: false, useCount: 0,
    };
  }

  function block(over: Partial<StrengthBlock> = {}): StrengthBlock {
    return {
      id: 'b1', blockType: 'PRINCIPAL', format: 'CLASSIQUE',
      durationSec: null, rounds: null, workSec: null, restSec: null, exercises: [], ...over,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    component = TestBed.createComponent(StrengthSessionEditorComponent).componentInstance;
    // Le catalogue est arrivé : c'est l'état normal quand un coach compose sa séance. Le cas
    // contraire a son propre test.
    component.exercisesLoaded.set(true);
  });

  /** Poser une section, c'est vouloir y mettre des exercices : le choix s'ouvre dans la foulée. */
  it('pose une section et ouvre aussitôt le choix des exercices', () => {
    component.addSection('ECHAUFFEMENT');

    expect(component.blocks().length).toBe(1);
    expect(component.blocks()[0].blockType).toBe('ECHAUFFEMENT');
    expect(component.blocks()[0].format).withContext('classique par défaut').toBe('CLASSIQUE');
    expect(component.pickerOpen()).withContext('le sélecteur d’exercice s’ouvre').toBe(true);
  });

  /**
   * Tant que la bibliothèque n'est pas arrivée, le sélecteur attend : ouvert trop tôt, il
   * annonce « aucun exercice — crée-les d'abord », ce qui est faux.
   */
  it('attend le catalogue avant d’ouvrir le sélecteur', () => {
    component.exercisesLoaded.set(false);

    component.addSection('PRINCIPAL');

    expect(component.pickerOpen()).toBe(false);
  });

  /**
   * Le cœur de la simplification : la section porte l'intention. Un échauffement prescrit à
   * 70–80 % du 1RM était une valeur fausse qu'il fallait corriger à chaque exercice ajouté.
   */
  it('donne à l’exercice les valeurs de sa section', () => {
    component.addSection('ECHAUFFEMENT');
    component.addExercise(exercise());

    const rx = component.blocks()[0].exercises[0].prescription;
    expect(rx.sets).toBe(2);
    expect(rx.repsFixed).toBe(10);
    expect(rx.chargePctRmMin).withContext('aucune charge imposée à l’échauffement').toBeUndefined();
    expect(rx.rirMin).toBe(4);
  });

  it('garde la prescription lourde pour la section principale', () => {
    component.addSection('PRINCIPAL');
    component.addExercise(exercise());

    const rx = component.blocks()[0].exercises[0].prescription;
    expect(rx.sets).toBe(4);
    expect(rx.chargePctRmMin).toBe(70);
    expect(rx.chargePctRmMax).toBe(80);
  });

  /** Un retour au calme se tient en secondes : le compter en répétitions n'a pas de sens. */
  it('prescrit le retour au calme en durée', () => {
    component.addSection('CALME');
    component.addExercise(exercise('e2', 'Gainage'));

    const rx = component.blocks()[0].exercises[0].prescription;
    expect(rx.volumeType).toBe('DUREE');
    expect(rx.durationSec).toBe(45);
  });

  /** La copie doit être profonde : sinon retoucher le double changerait aussi l'original. */
  it('duplique une section sans lier les deux prescriptions', () => {
    component.addSection('PRINCIPAL');
    component.addExercise(exercise());
    const original = component.blocks()[0];

    component.duplicateBlock(original);

    expect(component.blocks().length).toBe(2);
    const copy = component.blocks()[1];
    expect(copy.id).not.toBe(original.id);
    copy.exercises[0].prescription.sets = 9;
    expect(original.exercises[0].prescription.sets).withContext('l’original n’a pas bougé').toBe(4);
  });

  /** « La même, en plus lourd » : la copie se pose juste sous l'exercice d'origine. */
  it('duplique un exercice à sa suite', () => {
    const b = block({ exercises: [] });
    component.blocks.set([b]);
    component.openPicker(b);
    component.addExercise(exercise('e1', 'Squat'));
    component.addExercise(exercise('e2', 'Fente'));

    component.duplicateExercise(component.blocks()[0], 0);

    const names = component.blocks()[0].exercises.map((e) => e.exerciseName);
    expect(names).toEqual(['Squat', 'Squat', 'Fente']);
  });

  /** Séries × répétitions se règlent sur la carte ; une fourchette garde le panneau. */
  it('n’édite en ligne que les volumes simples', () => {
    const b = block();
    component.blocks.set([b]);
    component.openPicker(b);
    component.addExercise(exercise());
    const item = component.blocks()[0].exercises[0];

    expect(component.inlineVolume(b, item)).toBe('REPS');

    item.prescription.repsFixed = null;
    item.prescription.repsMin = 8;
    item.prescription.repsMax = 10;
    expect(component.inlineVolume(b, item)).withContext('fourchette : au panneau').toBeNull();
  });

  it('borne les valeurs saisies en ligne', () => {
    const b = block();
    component.blocks.set([b]);
    component.openPicker(b);
    component.addExercise(exercise());
    const item = component.blocks()[0].exercises[0];

    component.setInlineSets(item, 0);
    component.setInlineReps(item, 12.4);

    expect(item.prescription.sets).withContext('au moins une série').toBe(1);
    expect(item.prescription.repsFixed).withContext('des répétitions entières').toBe(12);
  });

  /** Le format reste accessible, mais cesse d'être la première question posée. */
  it('replie le format, et l’ouvre quand il n’est plus classique', () => {
    const classique = block();
    const emom = block({ id: 'b2', format: 'EMOM' });

    expect(component.optionsOpen(classique)).toBe(false);
    expect(component.optionsOpen(emom)).withContext('un choix fait ne se cache pas').toBe(true);

    component.toggleOptions(classique);
    expect(component.optionsOpen(classique)).toBe(true);
  });
});
