import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { RaceObjective } from '../../core/models/race.model';
import { Workout } from '../../core/models/workout.model';
import { AthleteCalendarComponent } from './athlete-calendar.component';

/**
 * Les courses au calendrier, et la couleur des séances.
 *
 * <p>Le calendrier montrait les séances sans jamais dire vers quoi elles allaient : l'objectif
 * vivait dans un écran séparé, alors que c'est sa date qui donne son sens au reste — trois
 * semaines de côtes ne se lisent pas pareil à six semaines d'un marathon. Et toutes les séances
 * de course portaient la même couleur, si bien qu'une semaine ouverte ne disait rien de sa
 * forme : cinq barres identiques qu'il fallait lire une à une.</p>
 */
describe('athlete-calendar — courses et intensités', () => {
  let component: AthleteCalendarComponent;

  function race(over: Partial<RaceObjective>): RaceObjective {
    return {
      id: 'r1', athleteId: 'a1', athleteName: null, name: 'Marathon de Paris',
      raceDate: '2026-08-12', distanceM: 42195, targetTimeS: null,
      priority: 'A', status: 'UPCOMING', daysUntil: 30, ...over,
    };
  }

  function workout(over: Partial<Workout>): Workout {
    return {
      id: 'w1', athleteId: 'a1', scheduledDate: '2026-08-12', type: 'ENDURANCE',
      status: 'PLANNED', title: 'Footing', notes: null,
      targetDistanceM: null, targetDurationS: null, actualDurationS: null, missedReason: null, targetRpe: null,
      rpe: null, fatigue: null, pain: null, feel: null, injuries: [], athleteComment: null,
      coachComment: null, coachCommentAt: null, coachAcknowledgedAt: null,
      movedByAthlete: false, originalDate: null,
      plannedLoadUa: null, orderIndex: 0, steps: [], ...over,
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(AthleteCalendarComponent).componentInstance;
    // Mercredi 12 août 2026 : la semaine affichée court du lundi 10 au dimanche 16.
    component.anchor.set(new Date(2026, 7, 12));
  });

  it('pose la course sur son jour, dans la semaine comme dans le mois', () => {
    component.races.set([race({})]);

    const day = component.days().find((d) => d.date === '2026-08-12');
    expect(day?.races.length).toBe(1);
    expect(day?.races[0].name).toBe('Marathon de Paris');

    const cell = component.monthWeeks()
      .flatMap((w) => w.days).find((d) => d.date === '2026-08-12');
    expect(cell?.races.length).toBe(1);
    expect(cell?.races[0].priority).toBe('A');
  });

  /**
   * Une échéance reste une échéance quel que soit le filtre : c'est même en vue « réalisé »
   * qu'elle explique le mieux la semaine qu'on relit.
   */
  it('montre la course quel que soit le filtre prévu / réalisé', () => {
    component.races.set([race({})]);

    for (const view of ['planned', 'done', 'both']) {
      component.setView(view);
      expect(component.days().find((d) => d.date === '2026-08-12')?.races.length)
        .withContext(`vue ${view}`).toBe(1);
    }
  });

  /** Un jour qui ne porte qu'une course n'est pas un jour vide : il ne doit pas s'afficher en repos. */
  it('ne traite pas comme vide un jour qui porte une course', () => {
    component.races.set([race({})]);

    expect(component.days().find((d) => d.date === '2026-08-12')?.empty).toBeFalse();
  });

  /**
   * Une course passée reste au calendrier — elle explique la semaine qui l'entoure — mais elle
   * cesse d'être une échéance, et le compte à rebours n'a plus de sens.
   */
  it('distingue la course courue de celle qui approche', () => {
    expect(component.raceSub(race({ distanceM: 10000, daysUntil: 3 }))).toBe('10 km · dans 3 j');
    expect(component.raceSub(race({ distanceM: 10000, daysUntil: 1 }))).toBe('10 km · demain');
    expect(component.raceSub(race({ distanceM: 10000, status: 'DONE', daysUntil: -4 })))
      .toBe('10 km');

    const cell = () => component.monthWeeks().flatMap((w) => w.days)
      .find((d) => d.date === '2026-08-12')?.races[0];
    component.races.set([race({ status: 'DONE' })]);
    expect(cell()?.past).toBeTrue();
    component.races.set([race({ status: 'UPCOMING' })]);
    expect(cell()?.past).toBeFalse();
  });

  /**
   * La couleur d'une séance est celle de son type — l'échelle d'intensité que le produit emploie
   * déjà pour ses pastilles de zone. En inventer une seconde donnerait deux vocabulaires de
   * couleur dans le même écran pour dire la même chose.
   */
  it('donne à chaque intensité sa couleur, de la récupération au fractionné', () => {
    const couleurs = (['RECOVERY', 'ENDURANCE', 'TEMPO', 'THRESHOLD', 'INTERVALS'] as const)
      .map((type) => component.typeColor(workout({ type })));

    expect(couleurs).toEqual([
      'var(--zone-1)', 'var(--zone-2)', 'var(--zone-3)', 'var(--zone-4)', 'var(--zone-5)',
    ]);
    // Deux intensités voisines ne doivent jamais retomber sur la même couleur : c'est tout
    // l'intérêt, distinguer la séance dure du footing sans avoir à lire l'étiquette.
    expect(new Set(couleurs).size).toBe(5);
  });

  /** La course garde sa couleur propre : elle n'est pas une intensité de plus. */
  it('ne range pas la course dans l’échelle des intensités', () => {
    expect(component.typeColor(workout({ type: 'RACE' }))).toBe('var(--energy)');
  });
});
