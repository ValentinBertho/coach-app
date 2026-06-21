import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ACTIVITY_STATUS_BADGE,
  ACTIVITY_STATUS_LABELS,
  Activity,
} from '../../core/models/activity.model';
import { ActivityService } from '../../core/services/activity.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-activity-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './activity-list.component.html',
  styleUrl: './activity-list.component.scss',
})
export class ActivityListComponent implements OnInit {
  readonly athleteId = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly activityService = inject(ActivityService);
  private readonly toast = inject(ToastService);

  readonly statusLabels = ACTIVITY_STATUS_LABELS;
  readonly statusBadge = ACTIVITY_STATUS_BADGE;

  readonly activities = signal<Activity[]>([]);
  readonly loading = signal(true);
  readonly submitting = signal(false);

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
          a.status === 'MATCHED' ? 'Activité importée et rapprochée ✅' : 'Activité importée.'
        );
        this.form.reset();
        this.submitting.set(false);
        this.load();
      },
      error: () => this.submitting.set(false),
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
