import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteSummary } from '../../core/models/athlete.model';
import { STATUS_BADGE, STATUS_LABELS, WORKOUT_TYPE_LABELS, Workout, WorkoutType } from '../../core/models/workout.model';
import { AthleteService } from '../../core/services/athlete.service';
import { CourseService } from '../../core/services/course.service';
import { StrengthService } from '../../core/services/strength.service';
import { ScheduledStrength, StrengthSession } from '../../core/models/strength.model';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';

interface DayCell {
  date: string;
  label: string;
  dayNum: number;
  isToday: boolean;
  inMonth: boolean;
  workouts: Workout[];
  strength: ScheduledStrength[];
  km: number;
  sessions: number;
  /** Charge élevée : ≥ 2 séances dont au moins une séance clé (qualité). */
  conflict: boolean;
}

/** Sémantique de type d'événement : couleur (token) + icône + nature « clé ». */
interface TypeMeta { color: string; icon: string; key: boolean; }
const TYPE_META: Record<WorkoutType, TypeMeta> = {
  ENDURANCE:      { color: 'var(--zone-2)', icon: '🏃', key: false },
  RECOVERY:       { color: 'var(--zone-1)', icon: '🧘', key: false },
  TEMPO:          { color: 'var(--zone-3)', icon: '⏱️', key: true },
  THRESHOLD:      { color: 'var(--zone-4)', icon: '🔥', key: true },
  INTERVALS:      { color: 'var(--zone-5)', icon: '⚡', key: true },
  LONG_RUN:       { color: 'var(--primary)', icon: '🏔️', key: true },
  RACE:           { color: 'var(--energy)', icon: '🏁', key: true },
  STRENGTH:       { color: 'var(--dari-violet)', icon: '💪', key: false },
  CROSS_TRAINING: { color: 'var(--dari-teal)', icon: '🚴', key: false },
  REST:           { color: 'var(--ink-4)', icon: '🌙', key: false },
};

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
  private readonly strengthService = inject(StrengthService);
  private readonly courseService = inject(CourseService);
  private readonly templateService = inject(WorkoutTemplateService);
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
  readonly strength = signal<ScheduledStrength[]>([]);
  readonly librarySessions = signal<StrengthSession[]>([]);
  readonly courseTemplates = signal<WorkoutTemplate[]>([]);
  readonly loading = signal(false);

  /** Cellules affichées (7 en semaine, 42 en mois). */
  readonly cells = computed<DayCell[]>(() => {
    const today = toIso(new Date());
    const byDate = this.groupByDate();
    const strengthByDate = this.groupStrengthByDate();
    const count = this.mode() === 'week' ? 7 : 42;
    const start = this.gridStart();
    const monthRef = this.anchor().getMonth();
    return Array.from({ length: count }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      const workouts = byDate.get(iso) ?? [];
      const strength = strengthByDate.get(iso) ?? [];
      const km = workouts.reduce((s, w) => s + (w.targetDistanceM ?? 0), 0) / 1000;
      const sessions = workouts.length + strength.length;
      const hasKey = workouts.some((w) => TYPE_META[w.type].key);
      return {
        date: iso,
        label: this.dayNames[i % 7],
        dayNum: d.getDate(),
        isToday: iso === today,
        inMonth: this.mode() === 'week' || d.getMonth() === monthRef,
        workouts,
        strength,
        km,
        sessions,
        conflict: sessions >= 2 && hasKey,
      };
    });
  });

  /** Volume max d'un jour sur la période (pour normaliser les barres de densité). */
  readonly maxDayKm = computed(() => Math.max(1, ...this.cells().map((c) => c.km)));

  typeMeta(type: WorkoutType): TypeMeta { return TYPE_META[type]; }

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
    this.strengthService.listSessions().subscribe((p) => this.librarySessions.set(p.content));
    this.templateService.list().subscribe((p) => this.courseTemplates.set(p.content));
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
    this.reloadStrength();
  }

  reloadStrength(): void {
    if (!this.selectedAthleteId) return;
    const cells = this.cells();
    const from = cells[0].date;
    const to = cells[cells.length - 1].date;
    this.strengthService.scheduledCalendar(this.selectedAthleteId, from, to).subscribe({
      next: (list) => this.strength.set(list),
      error: () => this.strength.set([]),
    });
  }

  /** Drop dans la bibliothèque (retour) : aucune action, l'élément revient à sa place. */
  onLibDrop(): void { /* no-op */ }

  addWorkout(date: string): void {
    this.router.navigate(['/app/athletes', this.selectedAthleteId, 'workouts', 'new'], { queryParams: { date } });
  }
  openWorkout(w: Workout): void {
    // Vue séance (lecture) ; l'édition est une action délibérée depuis la page.
    this.router.navigate(['/app/athletes', w.athleteId, 'workouts', w.id]);
  }

  onDrop(event: CdkDragDrop<DayCell>, targetDate: string): void {
    const data = event.item.data as Workout | StrengthSession | WorkoutTemplate;
    const rec = data as unknown as Record<string, unknown>;

    // Séance de force glissée depuis la bibliothèque → planification.
    if ('structure' in rec) {
      const s = data as StrengthSession;
      this.strengthService
        .scheduleSession(this.selectedAthleteId, s.id, { date: targetDate, fieldsPreset: 'AVANCE' })
        .subscribe({
          next: () => { this.toast.success(`${s.name} planifiée le ${targetDate} 💪`); this.reloadStrength(); },
        });
      return;
    }

    // Modèle de séance course glissé depuis la bibliothèque → planification.
    if (!('scheduledDate' in rec)) {
      const t = data as WorkoutTemplate;
      this.courseService.schedule(this.selectedAthleteId, t.id, { date: targetDate }).subscribe({
        next: () => { this.toast.success(`${t.name} planifiée le ${targetDate}`); this.load(); },
      });
      return;
    }

    // Déplacement d'une séance course existante.
    if (event.previousContainer === event.container) return;
    const w = data as Workout;
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

  private groupStrengthByDate(): Map<string, ScheduledStrength[]> {
    const map = new Map<string, ScheduledStrength[]>();
    for (const s of this.strength()) {
      const arr = map.get(s.scheduledDate) ?? map.set(s.scheduledDate, []).get(s.scheduledDate)!;
      arr.push(s);
    }
    return map;
  }
}
