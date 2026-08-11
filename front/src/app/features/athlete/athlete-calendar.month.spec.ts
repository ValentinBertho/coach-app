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

    it('porte la charge du jour : une valeur chiffrée, prévu comme réalisé', () => {
      const chips = day('2026-08-12').chips;

      expect(chips.length).toBe(3);
      // Le volume d'abord : c'est ce qu'on vient chercher dans une case de calendrier.
      expect(chips[0].value).toBe('12 km');
      expect(chips[0].done).toBeFalse();
      // Séance clé (seuil) : la pastille porte la couleur pleine, le mois montre ses points durs.
      expect(chips[0].strong).toBeTrue();
      // La sortie réalisée se distingue du prescrit sans lire l'étiquette.
      expect(chips[2].done).toBeTrue();
      expect(chips[2].value).toBe('12.4 km');
    });

    /**
     * Le renforcement n'a pas de volume à montrer. La grille affichait « Renfo » — un mot
     * amputé de moitié dans une case de 45 px. L'icône le dit en entier, et sans place.
     */
    it('ne met aucun mot tronqué dans une case', () => {
      const chips = day('2026-08-12').chips;

      expect(chips[1].value).toBe('');
      expect(chips[1].icon).toBe('dumbbell');
      expect(chips.every((c) => !/^(Endu|Récu|Frac|Renfo|Fait)$/.test(c.value))).toBeTrue();
    });

    /** Une séance écrite en durée, sans allure de référence pour l'estimer, annonce sa durée. */
    it('replie sur la durée quand aucun volume n’est calculable', () => {
      component.workouts.set([
        { id: 'w2', scheduledDate: '2026-08-13', title: 'Footing', type: 'ENDURANCE',
          status: 'PLANNED', steps: [], targetDurationS: 2700 } as unknown as Workout,
      ]);

      const chip = day('2026-08-13').chips[0];
      expect(chip.value).toBe('45 min');
      expect(chip.strong).toBeFalse();
    });

    /**
     * La gouttière porte un seul chiffre par semaine : dès qu'une sortie est enregistrée, c'est
     * le réalisé — la question qu'on se pose sur une semaine entamée — et il se lit dans la
     * couleur du fait pour qu'on ne le prenne jamais pour du prévu.
     */
    it('totalise la semaine : le réalisé s’il existe, le prévu sinon', () => {
      const week = () => component.monthWeeks().find((w) => w.days.some((d) => d.date === '2026-08-12'))!;

      expect(week().totalKm).toBe('12');
      expect(week().totalDone).toBeTrue();

      component.activities.set([]);
      expect(week().totalKm).toBe('12');
      expect(week().totalDone).toBeFalse();

      component.workouts.set([]);
      expect(week().totalKm).toBeUndefined();
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
