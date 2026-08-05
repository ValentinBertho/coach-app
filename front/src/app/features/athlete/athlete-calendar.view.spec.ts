import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Activity } from '../../core/models/activity.model';
import { ScheduledStrength } from '../../core/models/strength.model';
import { Workout } from '../../core/models/workout.model';
import { AthleteCalendarComponent } from './athlete-calendar.component';

/**
 * Agenda athlète — prévu / réalisé / les deux.
 *
 * <p>L'agenda ne montrait que le programme. Une sortie non prescrite — une course, un footing
 * improvisé, une séance déplacée dans la vraie vie — n'y apparaissait donc nulle part, alors
 * qu'elle pèse dans la semaine autant que le reste ; et rien ne permettait de comparer d'un
 * coup d'œil ce qui était prévu à ce qui a été fait.</p>
 */
describe('athlete-calendar — prévu / réalisé', () => {
  let component: AthleteCalendarComponent;

  /** Lundi de la semaine courante : les jeux d'essai se posent dessus, comme l'agenda. */
  function monday(): string {
    const d = new Date();
    d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(AthleteCalendarComponent).componentInstance;

    component.workouts.set([
      { id: 'w1', scheduledDate: monday(), title: 'Seuil', type: 'THRESHOLD', status: 'PLANNED', steps: [] } as unknown as Workout,
    ]);
    component.strength.set([
      { id: 's1', scheduledDate: monday(), title: 'Renfo', completed: false } as unknown as ScheduledStrength,
    ]);
    component.activities.set([
      { id: 'a1', activityDate: monday(), title: '10 km du club', status: 'UNMATCHED', distanceM: 10000, durationS: 2400 } as unknown as Activity,
    ]);
  });

  /** Le jour du lundi, une fois les signaux posés. */
  function mondayRow() {
    return component.days().find((d) => d.date === monday())!;
  }

  it('montre par défaut le prescrit ET le réalisé', () => {
    expect(component.view()).toBe('both');
    expect(mondayRow().workouts.length).toBe(1);
    expect(mondayRow().strength.length).toBe(1);
    expect(mondayRow().activities.length).toBe(1);
  });

  it('« Prévu » masque les sorties réelles', () => {
    component.setView('planned');
    expect(mondayRow().workouts.length).toBe(1);
    expect(mondayRow().activities.length).toBe(0);
  });

  /** Le point de la demande : on doit pouvoir ne lire que ce qui a réellement été couru. */
  it('« Réalisé » ne garde que les sorties, y compris hors programme', () => {
    component.setView('done');
    expect(mondayRow().workouts.length).toBe(0);
    expect(mondayRow().strength.length).toBe(0);
    expect(mondayRow().activities.map((a) => a.id)).toEqual(['a1']);
  });

  /** « — » ne doit apparaître que si le jour est vide POUR LA VUE choisie, pas dans l'absolu. */
  it('ne déclare un jour vide qu’au regard de la vue courante', () => {
    expect(mondayRow().empty).toBeFalse();

    component.activities.set([]);
    component.setView('done');
    expect(mondayRow().empty).toBeTrue();

    component.setView('planned');
    expect(mondayRow().empty).toBeFalse();
  });

  it('affiche l’allure réelle d’une sortie', () => {
    // 10 km en 40 min = 4'00/km.
    expect(component.actPace({ distanceM: 10000, durationS: 2400, paceSPerKm: null } as Activity)).toBe("4'00");
    // L'allure calculée côté serveur prime quand elle existe.
    expect(component.actPace({ distanceM: 10000, durationS: 2400, paceSPerKm: 250 } as Activity)).toBe("4'10");
    expect(component.actPace({ distanceM: null, durationS: null, paceSPerKm: null } as Activity)).toBeNull();
  });
});
