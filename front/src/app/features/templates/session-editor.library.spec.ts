import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { CourseBlock } from '../../core/models/course.model';
import { RunDrill } from '../../core/models/run-drill.model';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { SessionEditorComponent } from './session-editor.component';

/**
 * Bibliothèque à gauche de l'éditeur course (demande d'un coach : « comme en renfo, la
 * bibliothèque sur le côté, et je glisse »).
 *
 * <p><b>Ce que ces tests protègent.</b> Une séance glissée ou cliquée verse des <b>copies</b> de
 * ses blocs (le modèle d'origine n'est jamais modifié par l'édition qui suit) ; un éducatif se
 * pose sur un bloc ; un bloc peut changer de section.</p>
 */
describe('session-editor — bibliothèque latérale', () => {
  let component: SessionEditorComponent;
  let http: HttpTestingController;

  const block = (id: string): CourseBlock => ({
    id, type: 'easy', reps: null, distanceM: null, durationS: 600, prescription: { zoneId: 'z' },
  });
  const drill = { id: 'd1', name: 'Montées de genoux', categoryId: null } as unknown as RunDrill;
  const template = { id: 't1', name: '6 × 1000' } as unknown as WorkoutTemplate;

  function drop(data: unknown, from: unknown, to: unknown, previousIndex: number, currentIndex: number) {
    return {
      item: { data },
      previousContainer: { data: from },
      container: { data: to },
      previousIndex,
      currentIndex,
    } as unknown as CdkDragDrop<unknown>;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(SessionEditorComponent).componentInstance;
    http = TestBed.inject(HttpTestingController);
    component.structure.set({ warmup: [block('w1')], main: [block('m1'), block('m2')], cooldown: [] });
    component.drills.set([drill]);
  });

  it('pose un éducatif glissé sur le bloc visé, et le clic sur le dernier bloc du corps', () => {
    component.dropBlock('main', drop(drill, undefined, 'main', 0, 0));
    expect(component.structure().main[0].drillIds).toEqual(['d1']);

    component.addDrillFromLibrary(drill);
    expect(component.structure().main[1].drillIds).toEqual(['d1']);
  });

  it('déplace un bloc d’une section à l’autre', () => {
    component.dropBlock('main', drop(null, 'warmup', 'main', 0, 1));
    expect(component.structure().warmup.length).toBe(0);
    expect(component.structure().main.map((b) => b.id)).toEqual(['m1', 'w1', 'm2']);
  });

  it('recopie les blocs d’une séance à l’endroit du dépôt, avec de nouveaux identifiants', () => {
    component.dropBlock('main', drop(template, undefined, 'main', 0, 1));
    const req = http.expectOne((r) => r.url.endsWith('/workout-templates/t1/structure'));
    req.flush({
      templateId: 't1',
      structure: { warmup: [block('tw')], main: [block('tm1'), block('tm2')], cooldown: [] },
    });

    const ids = component.structure().main.map((b) => b.id);
    expect(ids.length).toBe(4);
    expect(ids[0]).toBe('m1');
    expect(ids[3]).toBe('m2');
    expect(ids).not.toContain('tm1');
    expect(component.structure().warmup.length).toBe(1);
  });
});
