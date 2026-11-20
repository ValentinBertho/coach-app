import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { ICONS } from '../../app.config';
import { Workout } from '../../core/models/workout.model';
import { RaceObjective } from '../../core/models/race.model';
import { CalendarComponent } from './calendar.component';

/**
 * Le haut du calendrier coach : onglets Prévu / Réalisé visibles, compte à rebours des
 * objectifs, et RPE moyen dans le total de chaque semaine.
 */
describe('calendrier — onglets, objectifs et RPE de la semaine', () => {
  let fixture: ComponentFixture<CalendarComponent>;
  let component: CalendarComponent;

  function today(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  function objective(over: Partial<RaceObjective>): RaceObjective {
    return {
      id: 'o', athleteId: 'ath-1', athleteName: null, name: 'Course', raceDate: '2027-02-07',
      distanceM: null, targetTimeS: null, priority: 'B', status: 'UPCOMING', daysUntil: 10, ...over,
    };
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
    fixture.componentRef.setInput('athleteId', 'ath-1');
  });

  it('affiche les onglets d’affichage et change de vue au clic', () => {
    fixture.detectChanges();
    const tabs = Array.from(fixture.nativeElement.querySelectorAll('.view-tab')) as HTMLElement[];
    expect(tabs.map((t) => t.textContent?.trim())).toEqual(['Prévu', 'Réalisé', 'Prévu et réalisé']);

    tabs[2].click();
    fixture.detectChanges();
    expect(component.view()).toBe('both');
  });

  it('montre les prochains objectifs, du plus proche au plus lointain, sans les passés ni les annulés', () => {
    component.objectives.set([
      objective({ id: 'loin', name: 'Semi', daysUntil: 107 }),
      objective({ id: 'proche', name: 'Cross', daysUntil: 79 }),
      objective({ id: 'passe', name: 'Ancien', daysUntil: -3, status: 'DONE' }),
      objective({ id: 'annule', name: 'Annulé', daysUntil: 20, status: 'CANCELLED' }),
    ]);
    fixture.detectChanges();

    expect(component.upcomingObjectives().map((o) => o.id)).toEqual(['proche', 'loin']);
    const chips = Array.from(fixture.nativeElement.querySelectorAll('.obj-chip__d')) as HTMLElement[];
    expect(chips.map((c) => c.textContent?.trim())).toEqual(['J-79', 'J-107']);
    expect(component.countdown(0)).toBe('Jour J');
  });

  it('moyenne le RPE prévu et le RPE ressenti de la semaine', () => {
    component.workouts.set([
      { id: 'w1', athleteId: 'ath-1', scheduledDate: today(), title: 'A', type: 'THRESHOLD', status: 'COMPLETED',
        steps: [], targetRpe: 6, rpe: 7 } as unknown as Workout,
      { id: 'w2', athleteId: 'ath-1', scheduledDate: today(), title: 'B', type: 'THRESHOLD', status: 'PLANNED',
        steps: [], targetRpe: 7, rpe: null } as unknown as Workout,
    ]);

    const week = component.weeks().find((w) => w.days.some((d) => d.workouts.length));
    expect(week?.plannedRpe).toBe(6.5);
    expect(week?.realRpe).toBe(7);
  });
});
