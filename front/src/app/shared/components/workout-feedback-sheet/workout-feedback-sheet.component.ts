import { ChangeDetectionStrategy, Component, inject, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Workout } from '../../../core/models/workout.model';
import { AthletePortalService } from '../../../core/services/athlete-portal.service';
import { FeedbackQueueService } from '../../../core/services/feedback-queue.service';
import { NetworkStatusService } from '../../../core/services/network-status.service';
import { ToastService } from '../../../core/services/toast.service';
import { CelebrationService } from '../../../core/services/celebration.service';
import { IconComponent } from '../icon/icon.component';
import { PainFatigueSelectorComponent } from '../physiology';
import { BottomSheetComponent } from '../ui';
import { RpeScaleSelectorComponent } from '../rpe-scale-selector/rpe-scale-selector.component';

/**
 * Retour de séance course (RPE + fatigue + douleur + commentaire), en bottom sheet ~10 s.
 *
 * Composant unique partagé par « Aujourd'hui », l'agenda et l'historique : une séance non
 * clôturée doit être notable d'où qu'on la regarde, sans que la file hors ligne ni les
 * invariants (la forme = fatigue + douleur, jamais le RPE) soient réécrits trois fois.
 *
 * @example
 * <app-workout-feedback-sheet [(open)]="fbOpen" [workout]="fbWorkout()" (saved)="onSaved($event)" />
 */
@Component({
  selector: 'app-workout-feedback-sheet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, BottomSheetComponent, PainFatigueSelectorComponent, RpeScaleSelectorComponent],
  template: `
    <app-bottom-sheet [(open)]="open" [title]="sheetTitle()">
      @if (workout(); as w) {
        <div class="fb">
          <!-- Une séance rattrapée n'est plus « celle du jour » : on rappelle laquelle on note. -->
          @if (subtitle(); as s) { <p class="fb__lead">{{ w.title }}<br /><span class="field-hint">{{ s }}</span></p> }

          <app-rpe-scale-selector [(value)]="rpe" />

          <app-pain-fatigue-selector kind="fatigue" [(value)]="fatigue" />
          <app-pain-fatigue-selector kind="pain" [(value)]="pain" />

          <textarea class="form-control" rows="2" placeholder="Un commentaire ? (optionnel)"
                    [ngModel]="comment()" (ngModelChange)="comment.set($event)"></textarea>

          <div class="fb__actions">
            <button type="button" class="btn btn-accent btn-lg" (click)="submit(true)">Séance réalisée <app-icon name="check" [size]="16" /></button>
            <button type="button" class="btn btn-ghost" (click)="submit(false)">Partiellement</button>
          </div>
        </div>
      }
    </app-bottom-sheet>
  `,
  styles: [`
    .fb { display: flex; flex-direction: column; gap: var(--sp-5); }
    .fb__lead { margin: 0; font-weight: 700; color: var(--ink); }
    .fb__actions { display: flex; flex-direction: column; gap: var(--sp-2); }
    .fb__actions .btn { width: 100%; }
  `],
})
export class WorkoutFeedbackSheetComponent {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly network = inject(NetworkStatusService);
  private readonly queue = inject(FeedbackQueueService);
  private readonly celebration = inject(CelebrationService);

  /** Ouverture two-way : l'appelant pilote, la feuille se referme après envoi. */
  readonly open = model(false);
  /** Séance visée. `openFor()` la positionne en même temps que les champs. */
  readonly workout = model<Workout | null>(null);
  /** Séance mise à jour (optimiste hors ligne) : l'appelant rafraîchit sa liste. */
  readonly saved = output<Workout>();

  readonly rpe = signal<number | null>(null);
  readonly fatigue = signal<number | null>(null);
  readonly pain = signal<number | null>(null);
  readonly comment = signal('');

  protected sheetTitle(): string {
    return this.isLate() ? 'Ton ressenti (en retard)' : 'Ton ressenti';
  }

  /** Rappel de la date pour une séance rattrapée ; rien pour la séance du jour. */
  protected subtitle(): string | null {
    const w = this.workout();
    if (!w || !this.isLate()) return null;
    const fmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
    return `Séance du ${fmt.format(new Date(w.scheduledDate + 'T00:00:00'))}`;
  }

  private isLate(): boolean {
    const w = this.workout();
    if (!w) return false;
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    return w.scheduledDate < today;
  }

  /** Ouvre la feuille sur une séance, champs pré-remplis avec ce qui a déjà été déclaré. */
  openFor(w: Workout): void {
    this.workout.set(w);
    this.rpe.set(w.rpe ?? null);
    this.fatigue.set(null);
    this.pain.set(null);
    this.comment.set(w.athleteComment ?? '');
    this.open.set(true);
  }

  /**
   * Fête le retour et affiche la série en cours. Le compteur est demandé après coup : il ne
   * doit jamais retarder la fermeture de la feuille, et son absence n'est pas un échec.
   */
  private celebrate(): void {
    this.celebration.fire('Ressenti enregistré');
    this.portal.feedbackStreak().subscribe({
      next: (n) => {
        if (n >= 2) this.celebration.fire('Ressenti enregistré', `${n}e retour d’affilée`);
      },
      error: () => { /* la célébration vaut sans le compteur */ },
    });
  }

  protected submit(completed: boolean): void {
    const w = this.workout();
    if (!w) return;
    const body = {
      status: (completed ? 'COMPLETED' : 'PARTIAL') as 'COMPLETED' | 'PARTIAL',
      rpe: this.rpe(),
      fatigue: this.fatigue(),
      pain: this.pain(),
      comment: this.comment() || null,
    };
    const optimistic: Workout = {
      ...w, status: body.status, rpe: body.rpe, athleteComment: body.comment,
    };

    // Hors ligne : mise à jour optimiste + mise en file (sync au retour réseau).
    if (!this.network.online()) {
      this.queue.enqueue(w.id, body);
      this.open.set(false);
      this.saved.emit(optimistic);
      this.toast.info('Enregistré hors ligne — synchronisé au retour du réseau.');
      return;
    }

    this.portal.feedback(w.id, body).subscribe({
      next: (updated) => {
        this.open.set(false);
        this.saved.emit(updated);
        this.toast.success('Ressenti enregistré');
        this.celebrate();
      },
      error: () => {
        this.queue.enqueue(w.id, body);
        this.open.set(false);
        this.saved.emit(optimistic);
        this.toast.warning('Hors ligne — ressenti mis en file pour synchronisation.');
      },
    });
  }
}
