import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteSummary } from '../../core/models/athlete.model';
import { STATUS_BADGE, STATUS_LABELS, WORKOUT_TYPE_LABELS, Workout } from '../../core/models/workout.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';

interface DayCell {
  date: string;
  label: string;
  dayNum: number;
  isToday: boolean;
  inMonth: boolean;
  workouts: Workout[];
}

function toIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function mondayOf(d: Date): Date {
  const date = new Date(d);
  const day = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - day);
  date.setHours(0, 0, 0, 0);
  return date;
}

@Component({
  selector: 'app-calendar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, DragDropModule],
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.scss',
})
export class CalendarComponent implements OnInit {
  private readonly athleteService = inject(AthleteService);
  private readonly workoutService = inject(WorkoutService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  private readonly dayNames = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  readonly athletes = signal<AthleteSummary[]>([]);
  selectedAthleteId = '';
  readonly mode = signal<'week' | 'month'>('week');
  readonly anchor = signal<Date>(new Date());
  readonly workouts = signal<Workout[]>([]);
  readonly loading = signal(false);

  /** Cellules affichées (7 en semaine, 42 en mois). */
  readonly cells = computed<DayCell[]>(() => {
    const today = toIso(new Date());
    const byDate = this.groupByDate();
    const count = this.mode() === 'week' ? 7 : 42;
    const start = this.gridStart();
    const monthRef = this.anchor().getMonth();
    return Array.from({ length: count }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      return {
        date: iso,
        label: this.dayNames[i % 7],
        dayNum: d.getDate(),
        isToday: iso === today,
        inMonth: this.mode() === 'week' || d.getMonth() === monthRef,
        workouts: byDate.get(iso) ?? [],
      };
    });
  });

  readonly periodLabel = computed(() => {
    const a = this.anchor();
    if (this.mode() === 'month') {
      return new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(a);
    }
    const start = mondayOf(a);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short' });
    return `${fmt.format(start)} – ${fmt.format(end)}`;
  });

  readonly weeklyVolumeKm = computed(() => {
    const m = this.workouts().reduce((s, w) => s + (w.targetDistanceM ?? 0), 0);
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

  setMode(mode: 'week' | 'month'): void {
    this.mode.set(mode);
    this.load();
  }
  onAthleteChange(): void { this.load(); }
  shift(step: number): void {
    const d = new Date(this.anchor());
    if (this.mode() === 'week') d.setDate(d.getDate() + step * 7);
    else d.setMonth(d.getMonth() + step);
    this.anchor.set(d);
    this.load();
  }
  goToday(): void { this.anchor.set(new Date()); this.load(); }

  load(): void {
    if (!this.selectedAthleteId) return;
    const cells = this.cells();
    const from = cells[0].date;
    const to = cells[cells.length - 1].date;
    this.loading.set(true);
    this.workoutService.calendar(this.selectedAthleteId, from, to).subscribe({
      next: (list) => { this.workouts.set(list); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  addWorkout(date: string): void {
    this.router.navigate(['/app/athletes', this.selectedAthleteId, 'workouts', 'new'], { queryParams: { date } });
  }
  openWorkout(w: Workout): void {
    this.router.navigate(['/app/athletes', w.athleteId, 'workouts', w.id, 'edit']);
  }

  onDrop(event: CdkDragDrop<DayCell>, targetDate: string): void {
    if (event.previousContainer === event.container) return;
    const w = event.item.data as Workout;
    if (w.scheduledDate === targetDate) return;
    const previous = w.scheduledDate;
    this.workouts.update((l) => l.map((x) => (x.id === w.id ? { ...x, scheduledDate: targetDate } : x)));
    this.workoutService.reschedule(this.selectedAthleteId, w.id, targetDate).subscribe({
      next: () => this.toast.success(`Séance déplacée au ${targetDate}`),
      error: () => this.workouts.update((l) => l.map((x) => (x.id === w.id ? { ...x, scheduledDate: previous } : x))),
    });
  }

  zonesOf(w: Workout): string[] {
    const order = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5'];
    const present = new Set(w.steps.map((s) => s.zone).filter((z): z is NonNullable<typeof z> => !!z));
    return order.filter((z) => present.has(z as never));
  }

  private gridStart(): Date {
    if (this.mode() === 'week') return mondayOf(this.anchor());
    const first = new Date(this.anchor());
    first.setDate(1);
    return mondayOf(first);
  }

  private groupByDate(): Map<string, Workout[]> {
    const map = new Map<string, Workout[]>();
    for (const w of this.workouts()) {
      const arr = map.get(w.scheduledDate) ?? map.set(w.scheduledDate, []).get(w.scheduledDate)!;
      arr.push(w);
    }
    return map;
  }
}
