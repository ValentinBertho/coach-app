import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteSummary } from '../../core/models/athlete.model';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  WORKOUT_TYPE_LABELS,
  Workout,
} from '../../core/models/workout.model';
import { AthleteService } from '../../core/services/athlete.service';
import { WorkoutService } from '../../core/services/workout.service';

interface DayCell {
  date: string;
  label: string;
  dayNum: number;
  isToday: boolean;
  workouts: Workout[];
}

/** Convertit une Date en chaîne locale YYYY-MM-DD (sans décalage UTC). */
function toIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function mondayOf(d: Date): Date {
  const date = new Date(d);
  const day = (date.getDay() + 6) % 7; // 0 = lundi
  date.setDate(date.getDate() - day);
  date.setHours(0, 0, 0, 0);
  return date;
}

@Component({
  selector: 'app-calendar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.scss',
})
export class CalendarComponent implements OnInit {
  private readonly athleteService = inject(AthleteService);
  private readonly workoutService = inject(WorkoutService);
  private readonly router = inject(Router);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  private readonly dayNames = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  readonly athletes = signal<AthleteSummary[]>([]);
  selectedAthleteId = '';
  readonly weekStart = signal<Date>(mondayOf(new Date()));
  readonly workouts = signal<Workout[]>([]);
  readonly loading = signal(false);

  readonly days = computed<DayCell[]>(() => {
    const start = this.weekStart();
    const today = toIso(new Date());
    const byDate = new Map<string, Workout[]>();
    for (const w of this.workouts()) {
      (byDate.get(w.scheduledDate) ?? byDate.set(w.scheduledDate, []).get(w.scheduledDate)!).push(w);
    }
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      return {
        date: iso,
        label: this.dayNames[i],
        dayNum: d.getDate(),
        isToday: iso === today,
        workouts: byDate.get(iso) ?? [],
      };
    });
  });

  readonly weekLabel = computed(() => {
    const start = this.weekStart();
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
    return `${fmt.format(start)} – ${fmt.format(end)}`;
  });

  readonly weeklyVolumeKm = computed(() => {
    const m = this.workouts().reduce((sum, w) => sum + (w.targetDistanceM ?? 0), 0);
    return (m / 1000).toFixed(1);
  });

  ngOnInit(): void {
    this.athleteService.list({ status: 'ACTIVE' }).subscribe((page) => {
      this.athletes.set(page.content);
      if (page.content.length && !this.selectedAthleteId) {
        this.selectedAthleteId = page.content[0].id;
        this.load();
      }
    });
  }

  onAthleteChange(): void {
    this.load();
  }

  shiftWeek(weeks: number): void {
    const d = new Date(this.weekStart());
    d.setDate(d.getDate() + weeks * 7);
    this.weekStart.set(d);
    this.load();
  }

  goToday(): void {
    this.weekStart.set(mondayOf(new Date()));
    this.load();
  }

  load(): void {
    if (!this.selectedAthleteId) return;
    const days = this.days();
    const from = days[0].date;
    const to = days[6].date;
    this.loading.set(true);
    this.workoutService.calendar(this.selectedAthleteId, from, to).subscribe({
      next: (list) => {
        this.workouts.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  addWorkout(date: string): void {
    this.router.navigate(['/app/athletes', this.selectedAthleteId, 'workouts', 'new'], {
      queryParams: { date },
    });
  }

  openWorkout(w: Workout): void {
    this.router.navigate(['/app/athletes', w.athleteId, 'workouts', w.id, 'edit']);
  }

  /** Zones distinctes des étapes (ordre Z1→Z5) pour la mini-barre de la pastille. */
  zonesOf(w: Workout): string[] {
    const order = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5'];
    const present = new Set(w.steps.map((s) => s.zone).filter((z): z is NonNullable<typeof z> => !!z));
    return order.filter((z) => present.has(z as never));
  }
}
