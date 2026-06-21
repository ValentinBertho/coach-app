import { Injectable, inject, signal } from '@angular/core';
import { AthletePortalService, WorkoutFeedback } from './athlete-portal.service';
import { NetworkStatusService } from './network-status.service';

interface QueuedFeedback {
  workoutId: string;
  body: WorkoutFeedback;
}

const KEY = 'coachrun.feedbackQueue';

/**
 * File de synchronisation pour le feedback athlète saisi hors ligne.
 * Persistée en localStorage, vidée automatiquement au retour du réseau (PWA sync).
 */
@Injectable({ providedIn: 'root' })
export class FeedbackQueueService {
  private readonly portal = inject(AthletePortalService);
  private readonly network = inject(NetworkStatusService);

  readonly pending = signal<number>(this.read().length);

  constructor() {
    this.network.reconnected$.subscribe(() => this.flush());
    if (this.network.online()) {
      this.flush();
    }
  }

  enqueue(workoutId: string, body: WorkoutFeedback): void {
    const queue = this.read().filter((q) => q.workoutId !== workoutId);
    queue.push({ workoutId, body });
    this.write(queue);
  }

  flush(): void {
    const queue = this.read();
    if (queue.length === 0) return;
    queue.forEach((item) => {
      this.portal.feedback(item.workoutId, item.body).subscribe({
        next: () => this.write(this.read().filter((q) => q.workoutId !== item.workoutId)),
        error: () => {
          /* on retentera au prochain retour réseau */
        },
      });
    });
  }

  private read(): QueuedFeedback[] {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as QueuedFeedback[]) : [];
  }

  private write(queue: QueuedFeedback[]): void {
    localStorage.setItem(KEY, JSON.stringify(queue));
    this.pending.set(queue.length);
  }
}
