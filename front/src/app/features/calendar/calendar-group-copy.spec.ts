import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { Workout } from '../../core/models/workout.model';
import { GroupCalendarRow } from '../../core/services/training-group.service';
import { CalendarComponent } from './calendar.component';

/**
 * Copier-coller dans la vue groupe.
 *
 * <p><b>Ce que ce fichier protège.</b> La grille de groupe savait planifier depuis la
 * bibliothèque et déplacer une séance d'un jour à l'autre — pas la copier. Un coach de club
 * l'a signalé en bêta en ces termes : « sur les séances de groupe, juste les copier-coller qui
 * sont pas possible ». Le presse-papier existait pourtant, mais il ne résolvait ses références
 * qu'en relisant les signaux de la grille mono-athlète : dans la vue groupe, coller répondait
 * invariablement « les séances à copier ne sont plus affichées ».</p>
 *
 * <p>On vérifie ici le geste complet et surtout <b>où il aboutit</b> : coller chez un autre
 * athlète doit passer par la route qui recalcule la prescription pour lui, jamais par la
 * duplication mono-athlète, qui lui donnerait les allures de quelqu'un d'autre.</p>
 */
describe('calendrier — copier-coller en vue groupe', () => {
  let fixture: ComponentFixture<CalendarComponent>;
  let component: CalendarComponent;
  let http: HttpTestingController;

  const MARC = 'ath-marc';
  const JULIE = 'ath-julie';
  const DAY = '2026-07-08';
  const OTHER_DAY = '2026-07-10';

  function row(athleteId: string, firstName: string, workouts: Workout[]): GroupCalendarRow {
    return { athleteId, firstName, lastName: 'T', canWrite: true, workouts, strength: [] };
  }

  function seance(id: string, athleteId: string, date: string): Workout {
    return {
      id, athleteId, scheduledDate: date, title: 'Seuil 3x8',
      type: 'THRESHOLD', status: 'PLANNED', steps: [],
    } as unknown as Workout;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        importProvidersFrom(LucideAngularModule.pick(ICONS)),
      ],
    });
    fixture = TestBed.createComponent(CalendarComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);

    fixture.componentRef.setInput('athleteId', MARC);
    fixture.detectChanges();
    // Après le premier cycle : l'initialisation du calendrier repeuple ses propres signaux, et
    // écraserait la grille de groupe posée trop tôt.
    component.scopeMode.set('group');
    component.groupRows.set([
      row(MARC, 'Marc', [seance('w1', MARC, DAY)]),
      row(JULIE, 'Julie', []),
    ]);
    fixture.detectChanges();
  });

  // Pas de `http.verify()` : l'initialisation du calendrier émet une dizaine d'appels (athlètes,
  // bibliothèque, éducatifs, catégories) qui n'ont rien à voir avec le geste testé ici.

  /** Le presse-papier retient l'origine, pas seulement l'identifiant. */
  it('copie une séance de la grille groupe', () => {
    const marc = component.groupRows()[0];
    component.openGroupChipMenu(marc, marc.workouts[0], new MouseEvent('contextmenu'));
    component.ctxGroupCopy();

    expect(component.clipboard().entries.length).toBe(1);
    expect(component.clipboard().entries[0].athleteId).toBe(MARC);
    expect(component.clipboard().entries[0].date).toBe(DAY);
  });

  it('colle chez un autre athlète par la route qui recalcule sa prescription', () => {
    component.openGroupChipMenu(
      component.groupRows()[0], component.groupRows()[0].workouts[0], new MouseEvent('contextmenu'));
    component.ctxGroupCopy();

    component.pasteOnGroupCell({ athleteId: JULIE, date: OTHER_DAY });

    const req = http.expectOne((r) => r.url.includes(`/athletes/${JULIE}/workouts/copy-from`));
    expect(req.request.method).toBe('POST');
    expect(req.request.body.sourceWorkoutId).toBe('w1');
    expect(req.request.body.scheduledDate).toBe(OTHER_DAY);
    req.flush({ id: 'w2', athleteId: JULIE, scheduledDate: OTHER_DAY });
  });

  /** Chez le même athlète, la duplication existante suffit — et garde la prescription figée. */
  it('colle chez le même athlète par la duplication mono-athlète', () => {
    component.openGroupChipMenu(
      component.groupRows()[0], component.groupRows()[0].workouts[0], new MouseEvent('contextmenu'));
    component.ctxGroupCopy();

    component.pasteOnGroupCell({ athleteId: MARC, date: OTHER_DAY });

    const req = http.expectOne((r) => r.url.includes(`/athletes/${MARC}/workouts/w1/copy`));
    expect(req.request.body.scheduledDate).toBe(OTHER_DAY);
    req.flush({ id: 'w3', athleteId: MARC, scheduledDate: OTHER_DAY });
  });

  /** Un athlète en lecture seule refuse le collage côté client : le serveur renverrait 403. */
  it('refuse de coller chez un athlète en lecture seule', () => {
    component.groupRows.set([
      row(MARC, 'Marc', [seance('w1', MARC, DAY)]),
      { ...row(JULIE, 'Julie', []), canWrite: false },
    ]);
    component.openGroupChipMenu(
      component.groupRows()[0], component.groupRows()[0].workouts[0], new MouseEvent('contextmenu'));
    component.ctxGroupCopy();

    component.pasteOnGroupCell({ athleteId: JULIE, date: OTHER_DAY });

    http.expectNone((r) => r.url.includes('copy-from'));
  });

  /** Cmd+C prend la journée survolée : en vue groupe, le curseur est la case, pas la sélection. */
  it('copie la journée de la case survolée', () => {
    component.hoveredCell.set({ athleteId: MARC, date: DAY });

    component.copyHoveredCell();

    expect(component.clipboard().entries.length).toBe(1);
    expect(component.clipboard().entries[0].id).toBe('w1');
  });
});
