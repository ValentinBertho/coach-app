import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Workout } from '../../core/models/workout.model';
import { WorkoutService } from '../../core/services/workout.service';
import { AthleteCalendarComponent } from './athlete-calendar.component';

/**
 * L'ordre des séances d'une même journée, côté athlète.
 *
 * <p>Ce que ces tests tiennent : le numéro n'apparaît que si le coach a ordonné la journée. Une
 * journée où il n'a rien demandé ne doit porter aucun numéro — l'athlète y lirait une consigne
 * que personne n'a donnée, et l'ordre cesserait d'être facultatif.</p>
 */
describe('ordre des séances d’une journée', () => {
  let component: AthleteCalendarComponent;

  function workout(over: Partial<Workout>): Workout {
    return {
      id: 'w1', athleteId: 'a1', scheduledDate: '2026-09-08', type: 'ENDURANCE',
      status: 'PLANNED', title: 'Footing', notes: null,
      targetDistanceM: null, targetDurationS: null, actualDurationS: null, missedReason: null,
      targetRpe: null, rpe: null, fatigue: null, pain: null, feel: null, injuries: [],
      athleteComment: null, coachComment: null, coachCommentAt: null, coachCommentReadAt: null,
      coachAcknowledgedAt: null, movedByAthlete: false, originalDate: null,
      plannedLoadUa: null, orderIndex: 0, steps: [], ...over,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(AthleteCalendarComponent).componentInstance;
  });

  it('ne numérote rien quand le coach n’a rien demandé', () => {
    component.workouts.set([
      workout({ id: 'a', orderIndex: 0 }),
      workout({ id: 'b', orderIndex: 0, title: 'Côtes' }),
    ]);
    expect(component.dayIsOrdered('2026-09-08')).toBeFalse();
    expect(component.orderRank(workout({ id: 'a', orderIndex: 0 }))).toBeNull();
  });

  it('numérote à partir de 1 quand la journée est ordonnée', () => {
    const premier = workout({ id: 'a', orderIndex: 0 });
    const second = workout({ id: 'b', orderIndex: 1, title: 'Côtes' });
    component.workouts.set([premier, second]);

    expect(component.dayIsOrdered('2026-09-08')).toBeTrue();
    // L'indice est en base 0, l'athlète lit « 1 » et « 2 ».
    expect(component.orderRank(premier)).toBe(1);
    expect(component.orderRank(second)).toBe(2);
  });

  it('n’ordonne que la journée concernée', () => {
    component.workouts.set([
      workout({ id: 'a', orderIndex: 0 }),
      workout({ id: 'b', orderIndex: 1, title: 'Côtes' }),
      workout({ id: 'c', scheduledDate: '2026-09-09', orderIndex: 0, title: 'Sortie longue' }),
    ]);
    expect(component.dayIsOrdered('2026-09-09')).toBeFalse();
  });
});

/** Le geste du coach qui retire l'ordre d'une journée. */
describe('retrait de l’ordre (coach)', () => {
  let service: WorkoutService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(WorkoutService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('appelle le retrait avec la journée visée', () => {
    service.clearOrder('ath-1', '2026-09-08').subscribe();
    const req = http.expectOne((r) => r.url.endsWith('/workouts/order'));
    expect(req.request.method).toBe('DELETE');
    expect(req.request.params.get('date')).toBe('2026-09-08');
    req.flush(null);
  });
});
