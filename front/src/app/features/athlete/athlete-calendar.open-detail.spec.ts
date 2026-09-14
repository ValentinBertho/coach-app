import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { Activity } from '../../core/models/activity.model';
import { Workout } from '../../core/models/workout.model';
import { AthleteCalendarComponent } from './athlete-calendar.component';

/**
 * Où mène un clic dans l'agenda.
 *
 * <p><b>Ce que ces tests protègent.</b> L'agenda demandait systématiquement un second geste pour
 * atteindre ce qu'on venait chercher. Une séance déjà faite ouvrait une feuille décrivant ce qui
 * était <i>prévu</i> — puis il fallait toucher « Voir le détail de la séance » pour enfin voir ce
 * que ça avait donné. Une sortie réalisée, elle, renvoyait sur la <i>liste</i> des sorties, où il
 * fallait retrouver la bonne, puis repartir vers la séance pour la confronter au prévu.</p>
 *
 * <p>Le trajet est court et sans échec visible quand il se casse — d'où ces tests : un clic qui
 * mène ailleurs ne lève rien, il fatigue simplement celui qui s'en sert.</p>
 */
describe('athlete-calendar — où mène un clic', () => {
  let component: AthleteCalendarComponent;
  let navigate: jasmine.Spy;

  function workout(over: Partial<Workout>): Workout {
    return {
      id: 'w1', athleteId: 'a1', scheduledDate: '2026-08-12', type: 'ENDURANCE',
      status: 'PLANNED', title: 'Footing', notes: null,
      targetDistanceM: null, targetDurationS: null, actualDurationS: null, missedReason: null,
      targetRpe: null, rpe: null, fatigue: null, pain: null, feel: null, injuries: [],
      athleteComment: null, coachComment: null, coachCommentAt: null, coachCommentReadAt: null,
      coachAcknowledgedAt: null, movedByAthlete: false, originalDate: null,
      plannedLoadUa: null, orderIndex: 0, steps: [], ...over,
    } as Workout;
  }

  function activity(over: Partial<Activity>): Activity {
    return {
      id: 'act1', athleteId: 'a1', activityDate: '2026-08-12', title: 'Sortie du soir',
      distanceM: 10000, durationS: 3000, status: 'MATCHED', matchedWorkoutId: null, ...over,
    } as Activity;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    component = TestBed.createComponent(AthleteCalendarComponent).componentInstance;
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    component.anchor.set(new Date(2026, 7, 12));
  });

  // --- Une séance ---------------------------------------------------------------------------

  it('mène une séance réalisée droit à sa fiche', () => {
    component.openDetail(workout({ status: 'COMPLETED' }));

    expect(navigate).toHaveBeenCalledWith(['/athlete/workouts', 'w1']);
    expect(component.detailOpen())
      .withContext('la feuille du prévu n\'a plus lieu d\'être').toBeFalse();
  });

  /** « Écourtée » veut dire courue : il y a un réalisé à montrer. */
  it('traite une séance partielle comme réalisée', () => {
    component.openDetail(workout({ status: 'PARTIAL' }));

    expect(navigate).toHaveBeenCalledWith(['/athlete/workouts', 'w1']);
  });

  /**
   * Devant une séance à faire, le prévu EST la question : la feuille y répond au pouce sans
   * quitter l'agenda. L'envoyer sur la fiche ferait perdre ce raccourci.
   */
  it('garde la feuille du prévu sur une séance à venir', () => {
    component.openDetail(workout({ status: 'PLANNED' }));

    expect(navigate).not.toHaveBeenCalled();
    expect(component.detailOpen()).toBeTrue();
  });

  /** Une séance manquée n'a aucun réalisé : l'ouvrir sur une fiche vide serait une fausse promesse. */
  it('garde la feuille sur une séance manquée', () => {
    component.openDetail(workout({ status: 'MISSED' }));

    expect(navigate).not.toHaveBeenCalled();
    expect(component.detailOpen()).toBeTrue();
  });

  /**
   * Le rapprochement passe normalement la séance en COMPLETED. Quand il ne l'a pas fait — un
   * rattachement posé à la main, un historique ancien —, la sortie rattachée suffit à dire que la
   * séance a eu lieu : mieux vaut ouvrir un réalisé qui existe que s'arrêter à une colonne.
   */
  it('suit la sortie rattachée quand le statut n\'a pas suivi', () => {
    component.activities.set([activity({ matchedWorkoutId: 'w1' })]);

    component.openDetail(workout({ status: 'PLANNED' }));

    expect(navigate).toHaveBeenCalledWith(['/athlete/workouts', 'w1']);
  });

  // --- Une sortie ---------------------------------------------------------------------------

  it('mène une sortie rattachée à la séance qu\'elle réalise', () => {
    component.openActivity(activity({ matchedWorkoutId: 'w9' }));

    expect(navigate).toHaveBeenCalledWith(['/athlete/workouts', 'w9']);
  });

  /** Hors programme, il n'y a pas de séance à ouvrir : on reste sur la sortie elle-même. */
  it('laisse une sortie hors programme sur sa propre fiche', () => {
    component.openActivity(activity({ status: 'UNMATCHED', matchedWorkoutId: null }));

    expect(navigate).toHaveBeenCalledWith(['/athlete/activities'], { queryParams: { open: 'act1' } });
  });
});
