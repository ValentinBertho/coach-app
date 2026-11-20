import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom, signal } from '@angular/core';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { PpExercise, StrengthBlock } from '../../core/models/strength.model';
import { StrengthSessionEditorComponent } from './strength-session-editor.component';

/**
 * Bibliothèque d'exercices ouverte à côté de la séance (grand écran).
 *
 * <p><b>Ce que ces tests protègent.</b> Un coach : « avoir ce visuel, la bibliothèque à gauche et
 * la séance à droite ». Composer un renfo, c'est enchaîner une dizaine d'ajouts : chacun doit
 * tomber dans la bonne section, d'un « + » ou d'un glisser, sans rouvrir de panneau.</p>
 */
describe('strength-session-editor — bibliothèque ouverte', () => {
  let component: StrengthSessionEditorComponent;
  let http: HttpTestingController;

  function exercise(over: Partial<PpExercise> = {}): PpExercise {
    return {
      id: 'e1', name: 'Gainage', category: 'GAINAGE', categoryId: null, level: null, objective: null,
      muscleGroups: [], equipment: [], videoUrl: null, imageUrl: null, instructions: null,
      technicalNotes: null, contraindications: null, progressionId: null, regressionId: null,
      favorite: false, useCount: 0, ...over,
    };
  }

  function block(id: string, over: Partial<StrengthBlock> = {}): StrengthBlock {
    return {
      id, blockType: 'PRINCIPAL', format: 'CLASSIQUE',
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
    http = TestBed.inject(HttpTestingController);
    (component as unknown as { wide: () => boolean }).wide = signal(true);
    component.exercisesLoaded.set(true);
  });

  it('pose une section sans ouvrir de panneau : elle devient la cible des ajouts', () => {
    component.addSection('ECHAUFFEMENT');

    expect(component.pickerOpen()).toBe(false);
    expect(component.targetBlock()?.blockType).toBe('ECHAUFFEMENT');
    expect(component.targetLabel()).toBe('1. Échauffement');
  });

  it('ajoute à la section désignée, pas forcément à la dernière', () => {
    component.blocks.set([block('b1', { blockType: 'ECHAUFFEMENT' }), block('b2')]);
    expect(component.targetBlock()?.id).withContext('par défaut, la dernière').toBe('b2');

    component.targetSection(component.blocks()[0]);
    component.addFromLibrary(exercise());

    expect(component.blocks()[0].exercises.length).toBe(1);
    expect(component.blocks()[1].exercises.length).toBe(0);
    expect(component.blocks()[0].exercises[0].prescription.repsFixed)
      .withContext('valeurs de la section Échauffement').toBe(10);
  });

  it('crée une section Principal si la séance est vide', () => {
    component.addFromLibrary(exercise());

    expect(component.blocks().length).toBe(1);
    expect(component.blocks()[0].blockType).toBe('PRINCIPAL');
    expect(component.blocks()[0].exercises[0].exerciseName).toBe('Gainage');
  });

  it('pose un exercice glissé depuis la bibliothèque à l’endroit du dépôt', () => {
    const target = block('b1');
    component.blocks.set([target]);
    component.addFromLibrary(exercise({ id: 'a', name: 'A' }));
    component.addFromLibrary(exercise({ id: 'c', name: 'C' }));

    component.dropExercise({
      item: { data: { kind: 'library', exercise: exercise({ id: 'b', name: 'B' }) } },
      previousContainer: { data: undefined }, container: { data: target },
      previousIndex: 0, currentIndex: 1,
    } as unknown as CdkDragDrop<StrengthBlock, StrengthBlock | undefined>, target);

    expect(component.blocks()[0].exercises.map((e) => e.exerciseName)).toEqual(['A', 'B', 'C']);
  });

  it('déplace un exercice d’une section à une autre', () => {
    const from = block('b1');
    const to = block('b2');
    component.blocks.set([from, to]);
    component.targetSection(from);
    component.addFromLibrary(exercise({ name: 'Fente' }));

    component.dropExercise({
      item: { data: { kind: 'item' } },
      previousContainer: { data: from }, container: { data: to },
      previousIndex: 0, currentIndex: 0,
    } as unknown as CdkDragDrop<StrengthBlock, StrengthBlock | undefined>, to);

    expect(component.blocks()[0].exercises.length).toBe(0);
    expect(component.blocks()[1].exercises[0].exerciseName).toBe('Fente');
  });

  it('filtre par onglet : favoris et fréquents', () => {
    component.exercises.set([
      exercise({ id: '1', name: 'Planche', favorite: true }),
      exercise({ id: '2', name: 'Squat', useCount: 5 }),
      exercise({ id: '3', name: 'Pont' }),
    ]);

    expect(component.libCount()).toBe(3);
    component.setLibTab('favorites');
    expect(component.libGroups().flatMap((g) => g.exercises.map((e) => e.name))).toEqual(['Planche']);
    component.setLibTab('frequent');
    expect(component.libGroups().flatMap((g) => g.exercises.map((e) => e.name))).toEqual(['Squat']);
  });

  it('épingle un exercice et l’enregistre', () => {
    const ex = exercise({ id: 'x' });
    component.exercises.set([ex]);

    component.toggleExerciseFavorite(ex);

    expect(component.exercises()[0].favorite).toBe(true);
    const req = http.expectOne((r) => r.url.endsWith('/pp/exercises/x/favorite'));
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ favorite: true });
  });
});
