import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ACTIVITY_STATUS_BADGE,
  ACTIVITY_STATUS_LABELS,
  Activity,
} from '../../core/models/activity.model';
import { Workout } from '../../core/models/workout.model';
import { ActivityService } from '../../core/services/activity.service';
import { WorkoutService } from '../../core/services/workout.service';
import { ToastService } from '../../core/services/toast.service';
import { ActivityLapsComponent } from '../../shared/components/activity-laps/activity-laps.component';
import { TimeInZoneBarComponent } from '../../shared/components/time-in-zone-bar/time-in-zone-bar.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'app-activity-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, ReactiveFormsModule, RouterLink, DatePipe, ActivityLapsComponent, TimeInZoneBarComponent],
  templateUrl: './activity-list.component.html',
  styleUrl: './activity-list.component.scss',
})
export class ActivityListComponent implements OnInit {
  readonly athleteId = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly activityService = inject(ActivityService);
  private readonly workoutService = inject(WorkoutService);
  private readonly toast = inject(ToastService);

  readonly statusLabels = ACTIVITY_STATUS_LABELS;
  readonly statusBadge = ACTIVITY_STATUS_BADGE;

  readonly activities = signal<Activity[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);
  /** Id de l'activité dont la barre temps-en-zone est dépliée (une à la fois). */
  readonly zonesOpen = signal<string | null>(null);
  /** Id de l'activité dont le sélecteur de rapprochement manuel est ouvert. */
  readonly matchOpen = signal<string | null>(null);
  readonly matchCandidates = signal<Workout[]>([]);

  /**
   * L'activité porte-t-elle un tracé et un flux exploitables ? Depuis l'import Strava complet,
   * ce n'est plus le seul privilège des fichiers déposés à la main.
   */
  hasDetail(a: Activity): boolean {
    return a.source === 'FILE' || a.source === 'STRAVA';
  }

  toggleZones(id: string): void {
    this.zonesOpen.update((cur) => (cur === id ? null : id));
  }

  readonly form = this.fb.group({
    activityDate: ['', Validators.required],
    title: [''],
    distanceM: [null as number | null, Validators.min(0)],
    durationS: [null as number | null, Validators.min(0)],
    avgHr: [null as number | null, Validators.min(0)],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.activityService.list(this.athleteId()).subscribe({
      next: (list) => {
        this.activities.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  import(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.activityService.import(this.athleteId(), this.form.getRawValue() as never).subscribe({
      next: (a) => {
        this.toast.success(
          a.status === 'MATCHED' ? 'Activité importée et rapprochée' : 'Activité importée.'
        );
        this.form.reset();
        this.submitting.set(false);
        this.load();
      },
      error: () => this.submitting.set(false),
    });
  }

  onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.activityService.importFile(this.athleteId(), file).subscribe({
      next: (a) => {
        this.toast.success(a.status === 'MATCHED' ? 'Fichier importé et rapproché' : 'Fichier importé.');
        input.value = '';
        this.load();
      },
      error: () => { input.value = ''; },
    });
  }

  /** Ouvre le sélecteur de rapprochement : séances planifiées à ±3 jours de la sortie. */
  openMatch(a: Activity): void {
    if (this.matchOpen() === a.id) {
      this.matchOpen.set(null);
      return;
    }
    this.matchOpen.set(a.id);
    this.matchCandidates.set([]);
    this.workoutService
      .calendar(this.athleteId(), shiftDate(a.activityDate, -3), shiftDate(a.activityDate, 3))
      .subscribe({
        next: (ws) => this.matchCandidates.set(ws.filter((w) => w.status === 'PLANNED')),
        error: () => this.matchCandidates.set([]),
      });
  }

  /** Rattache l'activité à la séance choisie. L'algorithme se trompe : le coach tranche. */
  confirmMatch(a: Activity, workoutId: string): void {
    if (!workoutId) {
      return;
    }
    this.activityService.match(this.athleteId(), a.id, workoutId).subscribe({
      next: () => {
        this.toast.success('Sortie rattachée à la séance.');
        this.matchOpen.set(null);
        this.load();
      },
    });
  }

  unmatch(a: Activity): void {
    this.activityService.unmatch(this.athleteId(), a.id).subscribe({
      next: () => {
        this.toast.info('Rapprochement annulé.');
        this.load();
      },
    });
  }

  km(m: number | null): string {
    return m != null ? (m / 1000).toFixed(1) + ' km' : '—';
  }

  delta(m: number | null): string {
    if (m == null) return '';
    const sign = m > 0 ? '+' : '';
    return `${sign}${(m / 1000).toFixed(1)} km`;
  }
}

/** Date ISO décalée de `days` jours (bornes du sélecteur de rapprochement). */
function shiftDate(iso: string, days: number): string {
  const d = new Date(iso);
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}
