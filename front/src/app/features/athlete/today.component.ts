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
import { AthletePortalService, StrengthPrescriptionView } from '../../core/services/athlete-portal.service';
import { ScheduledStrength } from '../../core/models/strength.model';
import { AuthService } from '../../core/services/auth.service';
import { FeedbackQueueService } from '../../core/services/feedback-queue.service';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ToastService } from '../../core/services/toast.service';
import { InstallButtonComponent } from '../../shared/components/install-button/install-button.component';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';
import { PushButtonComponent } from '../../shared/components/push-button/push-button.component';

type State = 'loading' | 'ready' | 'error';

/**
 * Portail athlète — « séance du jour » (mobile-first). Affiche la séance, ses étapes,
 * et permet de saisir le ressenti (RPE 1–10 + commentaire) et de la valider.
 */
@Component({
  selector: 'app-today',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, LogoComponent, InstallButtonComponent, OfflineBannerComponent, PushButtonComponent],
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
  readonly painScale = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  readonly state = signal<State>('loading');
  readonly workout = signal<Workout | null>(null);
  readonly nextRace = signal<import('../../core/models/race.model').RaceObjective | null>(null);
  readonly user = this.auth.currentUser;
  rpe: number | null = null;
  fatigue: number | null = null;
  pain: number | null = null;
  comment = '';

  // Renforcement du jour
  readonly strength = signal<ScheduledStrength | null>(null);
  readonly strengthRx = signal<StrengthPrescriptionView | null>(null);
  sRpe: number | null = null;
  sFatigue: number | null = null;
  sPain: number | null = null;

  ngOnInit(): void {
    this.load();
    this.loadStrength();
    this.portal.nextRace().subscribe({ next: (r) => this.nextRace.set(r) });
  }

  loadStrength(): void {
    const day = new Date().toISOString().slice(0, 10);
    this.portal.ppScheduled(day, day).subscribe({
      next: (list) => {
        const s = list[0] ?? null;
        this.strength.set(s);
        if (s) {
          this.sRpe = s.sessionFatigue != null ? this.sRpe : this.sRpe;
          this.portal.ppPrescription(s.id).subscribe({ next: (p) => this.strengthRx.set(p) });
        }
      },
    });
  }

  submitStrength(completed: boolean): void {
    const s = this.strength();
    if (!s) return;
    this.portal
      .ppFeedback(s.id, { completed, sessionRpe: this.sRpe, fatigue: this.sFatigue, pain: this.sPain, comment: null })
      .subscribe({
        next: (updated) => {
          this.strength.set(updated);
          this.toast.success('Renforcement enregistré 💪');
        },
      });
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
      fatigue: this.fatigue,
      pain: this.pain,
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
