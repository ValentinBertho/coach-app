import { Workout, awaitsFeedback, needsFeedback } from './workout.model';

function make(over: Partial<Workout>): Workout {
  return {
    id: 'w1', athleteId: 'a1', scheduledDate: '2026-07-28', type: 'ENDURANCE',
    status: 'PLANNED', title: 'Footing', notes: null,
    targetDistanceM: null, targetDurationS: null, actualDurationS: null, missedReason: null,
    rpe: null, athleteComment: null,
    coachComment: null, coachCommentAt: null, plannedLoadUa: null, orderIndex: 0, steps: [],
    ...over,
  };
}

describe('workout.model — ressenti athlète', () => {
  it('reste ouvert au ressenti tant que la séance n’est pas clôturée', () => {
    expect(needsFeedback(make({ status: 'PLANNED' }))).toBeTrue();
    expect(needsFeedback(make({ status: 'PARTIAL' }))).toBeTrue();
    expect(needsFeedback(make({ status: 'COMPLETED' }))).toBeFalse();
    expect(needsFeedback(make({ status: 'MISSED' }))).toBeFalse();
  });

  it('n’attend un retour que si l’athlète n’a encore rien déclaré', () => {
    expect(awaitsFeedback(make({ status: 'PLANNED' }))).toBeTrue();
    // Déjà noté (RPE ou commentaire) : plus de relance, même si le statut reste partiel.
    expect(awaitsFeedback(make({ status: 'PARTIAL', rpe: 6 }))).toBeFalse();
    expect(awaitsFeedback(make({ status: 'PARTIAL', athleteComment: 'genou sensible' }))).toBeFalse();
    expect(awaitsFeedback(make({ status: 'COMPLETED' }))).toBeFalse();
  });

  it('considère close une séance déclarée non faite', () => {
    // L'athlète n'avait auparavant aucun moyen de le dire : son seul recours était le silence,
    // qui ressortait en alerte « séance manquée » côté coach, sans motif.
    const missed = make({ status: 'MISSED', missedReason: 'UNEXPECTED' });
    expect(needsFeedback(missed)).toBeFalse();
    expect(awaitsFeedback(missed)).toBeFalse();
  });
});
