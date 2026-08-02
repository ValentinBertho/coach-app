import {
  FEEDBACK_QUEUE_KEY,
  clearFeedbackQueue,
  feedbackQueuePending,
  readFeedbackQueue,
  writeFeedbackQueue,
} from './feedback-queue.storage';

describe('feedback-queue.storage', () => {
  beforeEach(() => {
    localStorage.removeItem(FEEDBACK_QUEUE_KEY);
    feedbackQueuePending.set(0);
  });

  afterEach(() => localStorage.removeItem(FEEDBACK_QUEUE_KEY));

  it('relit ce qui a été écrit et tient le compteur à jour', () => {
    writeFeedbackQueue([{ workoutId: 'w1', body: { rpe: 7 } }]);

    expect(readFeedbackQueue().length).toBe(1);
    expect(feedbackQueuePending()).toBe(1);
  });

  it('purge la file à la déconnexion (données de santé sur appareil partagé)', () => {
    writeFeedbackQueue([
      { workoutId: 'w1', body: { rpe: 7, pain: 3 } },
      { workoutId: 'w2', body: { rpe: 5 } },
    ]);

    clearFeedbackQueue();

    // Rien ne doit subsister : le compte suivant rejouait ces retours sous SON jeton.
    expect(localStorage.getItem(FEEDBACK_QUEUE_KEY)).toBeNull();
    expect(readFeedbackQueue().length).toBe(0);
    expect(feedbackQueuePending()).toBe(0);
  });

  it('repart d’une file vide sur une valeur corrompue, sans jeter', () => {
    localStorage.setItem(FEEDBACK_QUEUE_KEY, '{ceci n’est pas du JSON');

    expect(() => readFeedbackQueue()).not.toThrow();
    expect(readFeedbackQueue()).toEqual([]);
    // La valeur illisible est écartée : elle ne peut plus refaire tomber l'application.
    expect(localStorage.getItem(FEEDBACK_QUEUE_KEY)).toBeNull();
  });

  it('ignore une valeur JSON valide mais non tabulaire', () => {
    localStorage.setItem(FEEDBACK_QUEUE_KEY, '{"workoutId":"w1"}');

    expect(readFeedbackQueue()).toEqual([]);
  });
});
