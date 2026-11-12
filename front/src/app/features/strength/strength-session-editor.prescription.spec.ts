import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { PpExercise, StrengthBlock, StrengthExerciseItem } from '../../core/models/strength.model';
import { SessionCategory } from '../../core/models/session-category.model';
import { StrengthSessionEditorComponent } from './strength-session-editor.component';

/**
 * La prescription d'un exercice de force, côté coach.
 *
 * <p>Ce que ces tests tiennent, et que le retour d'un coach bêta a mis en défaut : un exercice
 * ne se compte pas toujours en répétitions, « 3 × 8 » sur une fente doit pouvoir dire « par
 * côté », un repos n'est pas toujours une fourchette, et un AMRAP se prescrit en minutes. Chacun
 * de ces choix change la séance que l'athlète fait vraiment.</p>
 */
describe('strength-session-editor — prescription d’un exercice', () => {
  let component: StrengthSessionEditorComponent;

  function block(over: Partial<StrengthBlock> = {}): StrengthBlock {
    return {
      id: 'b1', blockType: 'PRINCIPAL', format: 'CLASSIQUE',
      durationSec: null, rounds: null, workSec: null, restSec: null, exercises: [], ...over,
    };
  }

  function item(over: Partial<StrengthExerciseItem> = {}): StrengthExerciseItem {
    return {
      exerciseId: 'e1', exerciseName: 'Squat', setType: 'STANDARD',
      prescription: { sets: 4, volumeType: 'REPS', repsFixed: 8, restSecMin: 90, restSecMax: 120 },
      ...over,
    };
  }

  function exercise(over: Partial<PpExercise> = {}): PpExercise {
    return {
      id: 'e1', name: 'Squat', category: 'FORCE_MAX', categoryId: null, level: null, objective: null,
      muscleGroups: [], equipment: [], videoUrl: null, imageUrl: null, instructions: null,
      technicalNotes: null, contraindications: null, progressionId: null, regressionId: null,
      favorite: false, useCount: 0, ...over,
    };
  }

  function category(id: string, name: string, sortOrder: number): SessionCategory {
    return { id, name, domain: 'STRENGTH', parentId: null, discipline: null, sortOrder };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    component = TestBed.createComponent(StrengthSessionEditorComponent).componentInstance;
  });

  describe('unité du volume', () => {
    it('vide les autres unités en changeant : « 8 reps » ne doit pas survivre à un passage en durée', () => {
      const b = block();
      const it = item();
      component.setVolumeType(b, it, 'DUREE');

      expect(it.prescription.volumeType).toBe('DUREE');
      expect(it.prescription.repsFixed).toBeNull();
      expect(it.prescription.durationSec).toBe(30);
    });

    it('propose une valeur de départ dans la nouvelle unité plutôt qu’un champ vide', () => {
      const b = block();
      const it = item();
      component.setVolumeType(b, it, 'DISTANCE');
      expect(it.prescription.distanceM).toBe(20);
      expect(it.prescription.durationSec).toBeNull();
    });

    it('lit en durée un exercice d’un bloc d’isométrie prescrit avant le choix d’unité', () => {
      const b = block({ format: 'ISOMETRIE' });
      const legacy = item({ prescription: { sets: 3, durationSec: 45 } });
      expect(component.volumeTypeOf(b, legacy)).toBe('DUREE');
      expect(component.volumeInfo(b, legacy.prescription))
        .toEqual({ label: 'Durée', min: 45, max: 45, unit: 's' });
    });
  });

  describe('valeur exacte ou fourchette', () => {
    it('ouvre la fourchette sur la valeur déjà saisie, sans la perdre', () => {
      const b = block();
      const it = item();
      component.setVolumeRanged(b, it, true);

      expect(component.volumeRanged(b, it)).toBeTrue();
      expect(it.prescription.repsMin).toBe(8);
      expect(it.prescription.repsMax).toBe(10);
      expect(it.prescription.repsFixed).toBeNull();
    });

    it('revient à une valeur exacte en gardant la borne basse', () => {
      const b = block();
      const it = item({ prescription: { volumeType: 'REPS', repsMin: 6, repsMax: 9 } });
      component.setVolumeRanged(b, it, false);

      expect(it.prescription.repsFixed).toBe(6);
      expect(it.prescription.repsMin).toBeNull();
      expect(it.prescription.repsMax).toBeNull();
    });

    it('vaut aussi pour une durée', () => {
      const b = block();
      const it = item({ prescription: { volumeType: 'DUREE', durationSec: 30 } });
      component.setVolumeRanged(b, it, true);
      expect(it.prescription.durationSecMax).toBe(45);
      expect(component.volumeInfo(b, it.prescription))
        .toEqual({ label: 'Durée', min: 30, max: 45, unit: 's' });
    });
  });

  describe('repos', () => {
    it('se prescrit strict : la borne haute disparaît, la valeur saisie reste', () => {
      const it = item();
      component.setRestRanged(it, false);

      expect(component.restRanged(it)).toBeFalse();
      expect(it.prescription.restSecMin).toBe(90);
      expect(it.prescription.restSecMax).toBeNull();
      expect(component.restInfo(it.prescription)).toEqual({ label: 'Récup', min: 90, max: 90, unit: 's' });
    });

    it('se rouvre en fourchette à partir du repos strict', () => {
      const it = item({ prescription: { restSecMin: 60 } });
      component.setRestRanged(it, true);
      expect(it.prescription.restSecMax).toBe(90);
    });
  });

  describe('latéralité', () => {
    it('ne dit rien en bilatéral et nomme les autres cas', () => {
      expect(component.sideInfo({ sideMode: 'BILATERAL' })).toBeNull();
      expect(component.sideInfo({ sideMode: 'PAR_COTE' })).toBe('Par côté');
    });
  });

  describe('durée d’un bloc chronométré', () => {
    it('se saisit en minutes et se stocke en secondes', () => {
      const b = block({ format: 'AMRAP', durationSec: 720 });
      expect(component.blockMinutes(b)).toBe(12);

      component.setBlockMinutes(b, 15);
      expect(b.durationSec).toBe(900);
    });

    it('accepte un champ vidé sans inventer de durée', () => {
      const b = block({ format: 'EMOM', durationSec: 600 });
      component.setBlockMinutes(b, null);
      expect(b.durationSec).toBeNull();
      expect(component.blockMinutes(b)).toBeNull();
    });
  });

  describe('choix d’un exercice', () => {
    beforeEach(() => {
      component.categories.set([category('c-haut', 'Haut du corps', 0), category('c-bas', 'Bas du corps', 1)]);
      component.exercises.set([
        exercise({ id: 'e1', name: 'Squat', categoryId: 'c-bas' }),
        exercise({ id: 'e2', name: 'Développé couché', categoryId: 'c-haut' }),
        exercise({ id: 'e3', name: 'Gainage planche', category: 'GAINAGE', categoryId: null }),
      ]);
    });

    it('range les exercices par catégorie du coach, dans son ordre, puis par type', () => {
      expect(component.pickerGroups().map((g) => g.title))
        .toEqual(['Haut du corps', 'Bas du corps', 'Gainage']);
    });

    it('ne laisse aucun exercice hors catégorie : sans catégorie du coach, son type le range', () => {
      const gainage = component.pickerGroups().find((g) => g.title === 'Gainage');
      expect(gainage?.exercises.map((e) => e.name)).toEqual(['Gainage planche']);
      expect(component.pickerCount()).toBe(3);
    });

    it('cherche sans accents ni casse', () => {
      component.pickerQuery.set('developpe');
      expect(component.pickerCount()).toBe(1);
      expect(component.pickerGroups()[0].exercises[0].name).toBe('Développé couché');
    });
  });
});
