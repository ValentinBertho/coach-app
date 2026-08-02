import { signal } from '@angular/core';

/**
 * Stockage de la file de retours hors ligne, isolé du service qui l'exploite.
 *
 * <p>Ce module ne dépend de rien : la déconnexion doit pouvoir purger la file sans injecter
 * `FeedbackQueueService` (qui dépend d'`AthletePortalService`, lui-même d'`AuthService` — le
 * cycle serait complet).</p>
 */

export interface QueuedFeedback {
  workoutId: string;
  body: unknown;
}

export const FEEDBACK_QUEUE_KEY = 'darilab.feedbackQueue';

/** Compteur partagé : la purge de déconnexion doit remettre à zéro le badge de l'instance vivante. */
export const feedbackQueuePending = signal(0);

/** Lit la file en tolérant une valeur corrompue (un `JSON.parse` nu figeait toute l'application). */
export function readFeedbackQueue<T = QueuedFeedback>(): T[] {
  const raw = localStorage.getItem(FEEDBACK_QUEUE_KEY);
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch {
    // Valeur illisible (écriture partielle, quota atteint, bricolage manuel) : on repart d'une
    // file vide plutôt que de laisser un service `providedIn: 'root'` jeter au démarrage —
    // l'exception remontait dans le constructeur et l'écran restait blanc, sans recours.
    localStorage.removeItem(FEEDBACK_QUEUE_KEY);
    return [];
  }
}

export function writeFeedbackQueue(queue: unknown[]): void {
  localStorage.setItem(FEEDBACK_QUEUE_KEY, JSON.stringify(queue));
  feedbackQueuePending.set(queue.length);
}

/**
 * Purge la file des retours saisis hors ligne. Appelée à la déconnexion : la file contient des
 * RPE, douleurs et fatigues (données de santé, art. 9). Sur un appareil partagé, elle survivait
 * au logout et le compte suivant la rejouait — sous SON jeton.
 */
export function clearFeedbackQueue(): void {
  localStorage.removeItem(FEEDBACK_QUEUE_KEY);
  feedbackQueuePending.set(0);
}
