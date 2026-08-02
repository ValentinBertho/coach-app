import { Injectable, inject } from '@angular/core';
import { EMPTY, concatMap, from } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { AthletePortalService, WorkoutFeedback } from './athlete-portal.service';
import { NetworkStatusService } from './network-status.service';
import {
  feedbackQueuePending,
  readFeedbackQueue,
  writeFeedbackQueue,
} from './feedback-queue.storage';

interface QueuedFeedback {
  workoutId: string;
  body: WorkoutFeedback;
}

/**
 * File de synchronisation pour le feedback athlète saisi hors ligne.
 * Persistée en localStorage (cf. `feedback-queue.storage.ts`), vidée automatiquement au retour
 * du réseau (PWA sync) et **purgée à la déconnexion** — elle contient des données de santé.
 */
@Injectable({ providedIn: 'root' })
export class FeedbackQueueService {
  private readonly portal = inject(AthletePortalService);
  private readonly network = inject(NetworkStatusService);

  readonly pending = feedbackQueuePending.asReadonly();

  /** Une seule vidange à la fois : deux flush concurrents renverraient les mêmes retours. */
  private flushing = false;

  constructor() {
    feedbackQueuePending.set(this.read().length);
    this.network.reconnected$.subscribe(() => this.flush());
    if (this.network.online()) {
      this.flush();
    }
  }

  enqueue(workoutId: string, body: WorkoutFeedback): void {
    const queue = this.read().filter((q) => q.workoutId !== workoutId);
    queue.push({ workoutId, body });
    writeFeedbackQueue(queue);
  }

  /**
   * Vide la file, un envoi à la fois. Les envois partaient auparavant en parallèle et chaque
   * réponse réécrivait la file entière à partir de sa propre lecture : deux réponses simultanées
   * se marchaient dessus, et un retour déjà transmis pouvait réapparaître — donc repartir.
   */
  flush(): void {
    const queue = this.read();
    if (queue.length === 0 || this.flushing) return;
    this.flushing = true;

    from(queue)
      .pipe(
        concatMap((item) =>
          this.portal.feedback(item.workoutId, item.body).pipe(
            // Succès : on retire CE retour de la file telle qu'elle est à cet instant.
            concatMap(() => {
              writeFeedbackQueue(this.read().filter((q) => q.workoutId !== item.workoutId));
              return EMPTY;
            }),
            // Échec : le retour reste en file, il repartira au prochain retour réseau.
            catchError(() => EMPTY),
          ),
        ),
        finalize(() => { this.flushing = false; }),
      )
      .subscribe();
  }

  private read(): QueuedFeedback[] {
    return readFeedbackQueue<QueuedFeedback>();
  }
}
