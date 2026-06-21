import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  STEP_TYPE_LABELS,
  WORKOUT_TYPE_LABELS,
  Workout,
} from '../../core/models/workout.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { AuthService } from '../../core/services/auth.service';
import { FeedbackQueueService } from '../../core/services/feedback-queue.service';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ToastService } from '../../core/services/toast.service';
import { InstallButtonComponent } from '../../shared/components/install-button/install-button.component';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';

type State = 'loading' | 'ready' | 'error';

/**
 * Portail athlète — « séance du jour » (mobile-first). Affiche la séance, ses étapes,
 * et permet de saisir le ressenti (RPE 1–10 + commentaire) et de la valider.
 */
@Component({
  selector: 'app-today',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, LogoComponent, InstallButtonComponent, OfflineBannerComponent],
  templateUrl: './today.component.html',
  styleUrl: './today.component.scss',
})
export class TodayComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly network = inject(NetworkStatusService);
  private readonly queue = inject(FeedbackQueueService);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  readonly rpeScale = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  readonly state = signal<State>('loading');
  readonly workout = signal<Workout | null>(null);
  readonly nextRace = signal<import('../../core/models/race.model').RaceObjective | null>(null);
  readonly user = this.auth.currentUser;
  rpe: number | null = null;
  comment = '';

  ngOnInit(): void {
    this.load();
    this.portal.nextRace().subscribe({ next: (r) => this.nextRace.set(r) });
  }

  load(): void {
    this.state.set('loading');
    this.portal.today().subscribe({
      next: (list) => {
        const w = list[0] ?? null;
        this.workout.set(w);
        if (w) {
          this.rpe = w.rpe ?? null;
          this.comment = w.athleteComment ?? '';
        }
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  zoneClass(zone: string | null): string {
    return zone ? `zone-${zone.toLowerCase()}` : '';
  }

  submit(completed: boolean): void {
    const w = this.workout();
    if (!w) return;
    const body = {
      status: (completed ? 'COMPLETED' : 'PARTIAL') as 'COMPLETED' | 'PARTIAL',
      rpe: this.rpe,
      comment: this.comment || null,
    };

    // Hors ligne : mise à jour optimiste + mise en file (sync au retour réseau).
    if (!this.network.online()) {
      this.queue.enqueue(w.id, body);
      this.workout.set({ ...w, status: body.status, rpe: this.rpe, athleteComment: this.comment || null });
      this.toast.info('Enregistré hors ligne — synchronisé au retour du réseau.');
      return;
    }

    this.portal.feedback(w.id, body).subscribe({
      next: (updated) => {
        this.workout.set(updated);
        this.toast.success('Ressenti enregistré 🎉');
      },
      error: () => {
        // Échec réseau : on met en file pour réessayer plus tard.
        this.queue.enqueue(w.id, body);
        this.workout.set({ ...w, status: body.status, rpe: this.rpe, athleteComment: this.comment || null });
        this.toast.warning('Hors ligne — ressenti mis en file pour synchronisation.');
      },
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
