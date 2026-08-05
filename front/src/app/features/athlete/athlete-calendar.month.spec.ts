import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Activity } from '../../core/models/activity.model';
import { ScheduledStrength } from '../../core/models/strength.model';
import { Workout } from '../../core/models/workout.model';
import { AthleteCalendarComponent } from './athlete-calendar.component';

/**
 * Grille du mois — l'écran d'ouverture du portail athlète.
 *
 * <p>Le portail ouvrait sur la séance du jour : on savait ce qu'on avait à faire aujourd'hui,
 * jamais à quoi ressemblait le mois. Ces tests fixent ce qu'une case doit porter (la charge du
 * jour, prévue et réalisée) et les pièges de calendrier qui font qu'une grille mensuelle est
 * fausse une fois sur douze : semaines entières, débordements des mois voisins, pas de mois qui
 * saute février depuis un 31.</p>
 */
describe('athlete-calendar — grille du mois', () => {
  let component: AthleteCalendarComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(AthleteCalendarComponent).componentInstance;
    // Août 2026 : commence un samedi, finit un lundi — de quoi éprouver les débordements.
    component.anchor.set(new Date(2026, 7, 12));
  });

  it('couvre le mois par semaines entières, du lundi au dimanche', () => {
    const weeks = component.monthWeeks();

    expect(weeks.length).toBeGreaterThanOrEqual(5);
    expect(weeks.every((w) => w.days.length === 7)).toBeTrue();
    // La grille démarre le lundi 27 juillet, la semaine qui contient le 1er août.
    expect(weeks[0].days[0].date).toBe('2026-07-27');
    expect(weeks[0].days[0].inMonth).toBeFalse();
    expect(weeks[0].days[5].date).toBe('2026-08-01');
    expect(weeks[0].days[5].inMonth).toBeTrue();
  });

  it('numérote les semaines en ISO, comme les plans d’entraînement', () => {
    // La semaine du 27 juillet 2026 est la 31e de l'année.
    expect(component.monthWeeks()[0].weekNumber).toBe(31);
  });

  /** Le piège classique : « mois + 1 » depuis un 31 saute le mois court. */
  it('change de mois sans sauter les mois courts', () => {
    component.setMode('month');
    component.anchor.set(new Date(2026, 0, 31));
    component.shift(1);
    expect(component.anchor().getMonth()).toBe(1);

    component.shift(1);
    expect(component.anchor().getMonth()).toBe(2);
  });

  it('avance d’une semaine en vue hebdomadaire, d’un mois en vue mensuelle', () => {
    component.setMode('week');
    component.anchor.set(new Date(2026, 7, 12));
    component.shift(1);
    expect(component.anchor().getDate()).toBe(19);

    component.setMode('month');
    component.shift(1);
    expect(component.anchor().getMonth()).toBe(8);
  });

  describe('contenu des cases', () => {
    beforeEach(() => {
      component.workouts.set([
        { id: 'w1', scheduledDate: '2026-08-12', title: 'Seuil', type: 'THRESHOLD',
          status: 'PLANNED', steps: [], targetDistanceM: 12000, targetDurationS: 3600 } as unknown as Workout,
      ]);
      component.strength.set([
        { id: 's1', scheduledDate: '2026-08-12', title: 'Renfo', completed: false } as unknown as ScheduledStrength,
      ]);
      component.activities.set([
        { id: 'a1', activityDate: '2026-08-12', title: 'Sortie', status: 'MATCHED',
          distanceM: 12400, durationS: 3720 } as unknown as Activity,
      ]);
    });

    function day(iso: string) {
      return component.monthWeeks().flatMap((w) => w.days).find((d) => d.date === iso)!;
    }

    it('porte la charge du jour : durée et volume, prévu comme réalisé', () => {
      const chips = day('2026-08-12').chips;

      expect(chips.length).toBe(3);
      // Format des durées du calendrier, identique côté coach : « 1h00 », « 1h15 », « 45 min ».
      expect(chips[0].duration).toBe('1h00');
      expect(chips[0].volume).toBe('12 km');
      expect(chips[0].done).toBeFalse();
      expect(chips[1].duration).toBe('Renfo');
      // La sortie réalisée se distingue du prescrit sans lire l'étiquette.
      expect(chips[2].done).toBeTrue();
      expect(chips[2].volume).toBe('12.4 km');
    });

    it('suit le filtre prévu / réalisé', () => {
      component.setView('planned');
      expect(day('2026-08-12').chips.every((c) => !c.done)).toBeTrue();

      component.setView('done');
      expect(day('2026-08-12').chips.map((c) => c.done)).toEqual([true]);
    });

    it('marque le jour même, et lui seul', () => {
      const todayIso = new Date().toISOString().slice(0, 10);
      const marked = component.monthWeeks().flatMap((w) => w.days).filter((d) => d.isToday);
      expect(marked.length).toBeLessThanOrEqual(1);
      if (marked.length === 1) expect(marked[0].date).toBe(todayIso);
    });

    /** Ouvrir un jour depuis la grille doit donner son contenu, même hors semaine affichée. */
    it('ouvre la feuille d’un jour avec tout ce qu’il porte', () => {
      component.openDay('2026-08-12');

      expect(component.dayOpen()).toBeTrue();
      expect(component.daySelected()?.workouts.length).toBe(1);
      expect(component.daySelected()?.activities.length).toBe(1);
      expect(component.daySelected()?.empty).toBeFalse();
    });
  });
});
