import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  STEP_TYPE_LABELS,
  WORKOUT_TYPE_LABELS,
  Workout,
} from '../../core/models/workout.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

type State = 'loading' | 'ready' | 'error';

/**
 * Portail athlète — « séance du jour » (mobile-first). Affiche la séance, ses étapes,
 * et permet de saisir le ressenti (RPE 1–10 + commentaire) et de la valider.
 */
@Component({
  selector: 'app-today',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './today.component.html',
  styleUrl: './today.component.scss',
})
export class TodayComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  readonly rpeScale = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  readonly state = signal<State>('loading');
  readonly workout = signal<Workout | null>(null);
  readonly user = this.auth.currentUser;
  rpe: number | null = null;
  comment = '';

  ngOnInit(): void {
    this.load();
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
    this.portal
      .feedback(w.id, {
        status: completed ? 'COMPLETED' : 'PARTIAL',
        rpe: this.rpe,
        comment: this.comment || null,
      })
      .subscribe({
        next: (updated) => {
          this.workout.set(updated);
          this.toast.success('Ressenti enregistré 🎉');
        },
      });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
