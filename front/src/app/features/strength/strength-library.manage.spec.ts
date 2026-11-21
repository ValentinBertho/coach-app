import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { PpExercise } from '../../core/models/strength.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { RunDrill } from '../../core/models/run-drill.model';
import { RunDrillsComponent } from '../templates/run-drills.component';
import { StrengthComponent, draftToRequest, exerciseToDraft } from './strength.component';

/**
 * Bibliothèques d'exercices de prépa physique et d'éducatifs : une fiche créée doit se consulter,
 * se corriger, se dupliquer et se supprimer, comme une séance de course. Avant, seule la catégorie
 * se changeait : une faute de frappe ou une mauvaise vidéo restaient pour toujours.
 */
describe('bibliothèques prépa physique et éducatifs — gérer une fiche', () => {
  let http: HttpTestingController;

  function exercise(over: Partial<PpExercise> = {}): PpExercise {
    return {
      id: 'e1', name: 'Squat', category: 'FORCE_MAX', categoryId: null, level: 'INTERMEDIAIRE',
      objective: 'Force', muscleGroups: ['QUADRICEPS', 'FESSIERS'], equipment: ['BARRE'],
      videoUrl: null, imageUrl: null, instructions: 'Dos droit', technicalNotes: 'Talons au sol',
      contraindications: null, progressionId: null, regressionId: null, favorite: false, useCount: 3,
      ...over,
    } as PpExercise;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('garde les champs que la fiche n’affiche pas quand on modifie un exercice', () => {
    const e = exercise();
    const d = exerciseToDraft(e);
    d.name = 'Squat avant';

    const req = draftToRequest(d, e);
    expect(req.name).toBe('Squat avant');
    expect(req.objective).toBe('Force');
    expect(req.technicalNotes).toBe('Talons au sol');
    expect(req.muscleGroups).withContext('muscles secondaires gardés').toEqual(['QUADRICEPS', 'FESSIERS']);

    d.muscle = 'ISCHIOS';
    expect(draftToRequest(d, e).muscleGroups).toEqual(['ISCHIOS', 'FESSIERS']);
  });

  it('ouvre la fiche, la modifie et enregistre l’exercice', () => {
    const c = TestBed.createComponent(StrengthComponent).componentInstance;
    c.exercises.set([exercise()]);

    c.openExercise(c.exercises()[0], true);
    expect(c.exPanelOpen()).toBe(true);
    expect(c.exEditing()).toBe(true);
    c.exDraft.name = 'Squat goblet';
    c.exDraft.videoUrl = ' https://youtu.be/abc ';
    c.saveExercise();

    const req = http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/pp/exercises/e1'));
    expect(req.request.body.name).toBe('Squat goblet');
    expect(req.request.body.videoUrl).toBe('https://youtu.be/abc');
    req.flush(exercise({ name: 'Squat goblet', videoUrl: 'https://youtu.be/abc' }));

    expect(c.exercises()[0].name).toBe('Squat goblet');
    expect(c.exEditing()).toBe(false);
    expect(c.exPanel()?.name).toBe('Squat goblet');
  });

  it('duplique un exercice en « (copie) » et ouvre la copie en modification', () => {
    const c = TestBed.createComponent(StrengthComponent).componentInstance;
    c.exercises.set([exercise()]);

    c.duplicateExercise(c.exercises()[0]);
    const req = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/pp/exercises'));
    expect(req.request.body.name).toBe('Squat (copie)');
    req.flush(exercise({ id: 'e2', name: 'Squat (copie)' }));

    expect(c.exercises().length).toBe(2);
    expect(c.exPanel()?.id).toBe('e2');
    expect(c.exEditing()).toBe(true);
  });

  it('supprime un exercice après confirmation', async () => {
    spyOn(TestBed.inject(ConfirmService), 'ask').and.resolveTo(true);
    const c = TestBed.createComponent(StrengthComponent).componentInstance;
    c.exercises.set([exercise()]);

    await c.deleteExercise(c.exercises()[0]);
    http.expectOne((r) => r.method === 'DELETE' && r.url.endsWith('/pp/exercises/e1')).flush(null);

    expect(c.exercises().length).toBe(0);
  });

  it('modifie un éducatif depuis sa fiche', () => {
    const c = TestBed.createComponent(RunDrillsComponent).componentInstance;
    const d: RunDrill = { id: 'd1', name: 'Montées de genou', category: 'TECHNIQUE', categoryId: null, description: null, videoUrl: null };
    c.drills.set([d]);

    c.open(d, true);
    c.edit.name = 'Montées de genoux';
    c.edit.category = 'AMPLITUDE';
    c.edit.description = 'Bassin haut';
    c.save();

    const req = http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/run-drills/d1'));
    expect(req.request.body).toEqual({
      name: 'Montées de genoux', category: 'AMPLITUDE', categoryId: null, description: 'Bassin haut', videoUrl: null,
    });
    req.flush({ ...d, name: 'Montées de genoux', category: 'AMPLITUDE', description: 'Bassin haut' });

    expect(c.drills()[0].name).toBe('Montées de genoux');
    expect(c.editing()).toBe(false);
  });
});
