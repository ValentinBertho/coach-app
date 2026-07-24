import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
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
import { ConfirmService } from '../../core/services/confirm.service';
import { WorkoutService } from '../../core/services/workout.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { HelpHintComponent } from '../help/help-hint.component';
import { RaceService } from '../../core/services/race.service';
import { LactateService } from '../../core/services/lactate.service';
import { RaceObjective } from '../../core/models/race.model';
import { LactateTest } from '../../core/models/lactate.model';
import { Unavailability, UnavailabilityReason } from '../../core/models/unavailability.model';
import { MesocycleTemplate } from '../../core/models/mesocycle-template.model';
import { MesocycleTemplateService } from '../../core/services/mesocycle-template.service';
import { TrainingGroup } from '../../core/models/training-group.model';
import { TrainingGroupService } from '../../core/services/training-group.service';
import { MesocycleParams } from '../../core/services/workout.service';
import { RunDrill } from '../../core/models/run-drill.model';
import { RunDrillService } from '../../core/services/run-drill.service';
import { CalendarNote } from '../../core/models/calendar-note.model';
import { CalendarNoteService } from '../../core/services/calendar-note.service';
import { SessionCategory } from '../../core/models/session-category.model';
import { SessionCategoryService } from '../../core/services/session-category.service';
import { SessionLibraryPanelComponent } from '../../shared/components/session-library-panel/session-library-panel.component';
import { Activity } from '../../core/models/activity.model';
import { ActivityService } from '../../core/services/activity.service';

/** Vue du calendrier : séances prévues, activités réalisées, ou les deux (façon Nolio). */
type CalView = 'planned' | 'realized' | 'both';

interface DayCell {
  date: string;
  label: string;
  dayNum: number;
  isToday: boolean;
  inMonth: boolean;
  workouts: Workout[];
  strength: ScheduledStrength[];
  objectives: RaceObjective[];
  tests: LactateTest[];
  notes: CalendarNote[];
  unavailability: Unavailability | null;
  /** Activités réalisées (importées) ce jour-là. */
  activities: Activity[];
  km: number;
  sessions: number;
  /** Charge élevée : ≥ 2 séances dont au moins une séance clé (qualité). */
  conflict: boolean;
}

/** Semaine (7 jours) + totaux agrégés (prévu et réalisé), façon Nolio (colonne de droite). */
interface WeekRow {
  days: DayCell[];
  km: number;
  durationS: number;
  sessions: number;
  realKm: number;
  realDurationS: number;
  realSessions: number;
}

const REASON_META: Record<UnavailabilityReason, { label: string; icon: string }> = {
  INJURY: { label: 'Blessure', icon: 'heart-pulse' },
  ILLNESS: { label: 'Maladie', icon: 'thermometer' },
  VACATION: { label: 'Vacances', icon: 'palmtree' },
  PERSONAL: { label: 'Personnel', icon: 'pin' },
  OTHER: { label: 'Indispo', icon: 'ban' },
};

/** Sémantique de type d'événement : couleur (token) + icône + nature « clé ». */
interface TypeMeta { color: string; icon: string; key: boolean; }
const TYPE_META: Record<WorkoutType, TypeMeta> = {
  ENDURANCE:      { color: 'var(--zone-2)', icon: 'footprints', key: false },
  RECOVERY:       { color: 'var(--zone-1)', icon: 'wind', key: false },
  TEMPO:          { color: 'var(--zone-3)', icon: 'timer', key: true },
  THRESHOLD:      { color: 'var(--zone-4)', icon: 'flame', key: true },
  INTERVALS:      { color: 'var(--zone-5)', icon: 'zap', key: true },
  LONG_RUN:       { color: 'var(--primary)', icon: 'mountain-snow', key: true },
  RACE:           { color: 'var(--energy)', icon: 'flag', key: true },
  STRENGTH:       { color: 'var(--dari-violet)', icon: 'dumbbell', key: false },
  CROSS_TRAINING: { color: 'var(--dari-teal)', icon: 'bike', key: false },
  REST:           { color: 'var(--ink-4)', icon: 'moon', key: false },
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
  imports: [FormsModule, RouterLink, DragDropModule, IconComponent, HelpHintComponent, SessionLibraryPanelComponent],
  host: { '(document:keydown)': 'onKeydown($event)' },
  templateUrl: './calendar.component.html',
  styleUrl: './calendar.component.scss',
})
export class CalendarComponent implements OnInit {
  private readonly athleteService = inject(AthleteService);
  private readonly workoutService = inject(WorkoutService);
  private readonly strengthService = inject(StrengthService);
  private readonly courseService = inject(CourseService);
  private readonly templateService = inject(WorkoutTemplateService);
  private readonly mesoTemplateService = inject(MesocycleTemplateService);
  private readonly groupService = inject(TrainingGroupService);
  private readonly drillService = inject(RunDrillService);
  private readonly noteService = inject(CalendarNoteService);
  private readonly categoryService = inject(SessionCategoryService);
  private readonly activityService = inject(ActivityService);

  readonly drills = signal<RunDrill[]>([]);
  readonly notes = signal<CalendarNote[]>([]);
  readonly categories = signal<SessionCategory[]>([]);
  private readonly raceService = inject(RaceService);
  private readonly lactateService = inject(LactateService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly reasonMeta = REASON_META;

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  private readonly dayNames = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  readonly athletes = signal<AthleteSummary[]>([]);
  selectedAthleteId = '';
  readonly mode = signal<'week' | 'month'>('week');
  /** Vue prévu / réalisé / les deux (façon Nolio). */
  readonly view = signal<CalView>('both');
  readonly showPlanned = computed(() => this.view() !== 'realized');
  readonly showRealized = computed(() => this.view() !== 'planned');
  /** Actions de planification avancées (duplication de semaine, mésocycle) — masquées pour l'instant. */
  readonly advancedPlanning = false;
  readonly anchor = signal<Date>(new Date());
  readonly workouts = signal<Workout[]>([]);
  readonly activities = signal<Activity[]>([]);
  readonly strength = signal<ScheduledStrength[]>([]);
  readonly objectives = signal<RaceObjective[]>([]);
  readonly tests = signal<LactateTest[]>([]);
  readonly unavailabilities = signal<Unavailability[]>([]);
  readonly librarySessions = signal<StrengthSession[]>([]);
  readonly courseTemplates = signal<WorkoutTemplate[]>([]);
  readonly loading = signal(false);

  /** Cellules affichées (7 en semaine, 42 en mois). */
  readonly cells = computed<DayCell[]>(() => {
    const today = toIso(new Date());
    const byDate = this.groupByDate();
    const strengthByDate = this.groupStrengthByDate();
    const objByDate = this.groupBy(this.objectives(), (o) => o.raceDate);
    const testByDate = this.groupBy(this.tests(), (t) => t.testDate);
    const noteByDate = this.groupBy(this.notes(), (n) => n.noteDate);
    const activityByDate = this.groupBy(this.activities(), (a) => a.activityDate);
    const unavail = this.unavailabilities();
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
        objectives: objByDate.get(iso) ?? [],
        tests: testByDate.get(iso) ?? [],
        notes: noteByDate.get(iso) ?? [],
        activities: activityByDate.get(iso) ?? [],
        unavailability: unavail.find((u) => iso >= u.startDate && iso <= u.endDate) ?? null,
        km,
        sessions,
        conflict: sessions >= 2 && hasKey,
      };
    });
  });

  /** Volume max d'un jour sur la période (pour normaliser les barres de densité). */
  readonly maxDayKm = computed(() => Math.max(1, ...this.cells().map((c) => c.km)));

  /** Semaines (lignes de 7 jours) + totaux — colonne de droite façon Nolio. */
  readonly weeks = computed<WeekRow[]>(() => {
    const cells = this.cells();
    const rows: WeekRow[] = [];
    for (let i = 0; i < cells.length; i += 7) {
      const days = cells.slice(i, i + 7);
      const km = days.reduce((s, d) => s + d.km, 0);
      const durationS = days.reduce(
        (s, d) => s + d.workouts.reduce((a, w) => a + (w.targetDurationS ?? 0), 0), 0);
      const sessions = days.reduce((s, d) => s + d.sessions, 0);
      const realKm = days.reduce((s, d) => s + d.activities.reduce((a, x) => a + (x.distanceM ?? 0), 0), 0) / 1000;
      const realDurationS = days.reduce((s, d) => s + d.activities.reduce((a, x) => a + (x.durationS ?? 0), 0), 0);
      const realSessions = days.reduce((s, d) => s + d.activities.length, 0);
      rows.push({ days, km, durationS, sessions, realKm, realDurationS, realSessions });
    }
    return rows;
  });

  /** Formatte une durée en « 3h25 » / « 45 min » (totaux hebdo). */
  fmtDuration(totalS: number): string {
    if (!totalS) return '—';
    const min = Math.round(totalS / 60);
    if (min < 60) return `${min} min`;
    return `${Math.floor(min / 60)}h${String(min % 60).padStart(2, '0')}`;
  }

  setView(v: CalView): void { this.view.set(v); }

  /** Km d'une activité réalisée (pour la pastille). */
  activityKm(a: Activity): string | null {
    return a.distanceM ? (a.distanceM / 1000).toFixed(1) : null;
  }

  /** Ouvre l'activité réalisée : la séance rapprochée si elle existe, sinon la liste d'activités. */
  openActivity(a: Activity): void {
    if (a.matchedWorkoutId) {
      this.router.navigate(['/app/athletes', this.selectedAthleteId, 'workouts', a.matchedWorkoutId]);
    } else {
      this.router.navigate(['/app/athletes', this.selectedAthleteId, 'activities']);
    }
  }

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
        // Sélectionner par défaut un athlète sur lequel on peut écrire (planifier),
        // pour éviter d'atterrir sur un athlète en lecture seule.
        const writable = page.content.find((a) => a.canWrite !== false);
        this.selectedAthleteId = (writable ?? page.content[0]).id;
        this.load();
        this.loadOverlays();
      }
    });
    this.strengthService.listSessions().subscribe((p) => this.librarySessions.set(p.content));
    this.templateService.list().subscribe((p) => this.courseTemplates.set(p.content));
    this.drillService.list().subscribe((d) => this.drills.set(d));
    this.categoryService.list().subscribe({ next: (c) => this.categories.set(c), error: () => this.categories.set([]) });
  }

  /** Épingle / dé-épingle (optimiste) une séance course depuis le panneau. */
  toggleFavorite(t: WorkoutTemplate): void {
    const next = !t.favorite;
    this.courseTemplates.update((l) => l.map((x) => (x.id === t.id ? { ...x, favorite: next } : x)));
    this.templateService.setFavorite(t.id, next).subscribe({
      next: (updated) => this.courseTemplates.update((l) => l.map((x) => (x.id === t.id ? updated : x))),
      error: () => this.courseTemplates.update((l) => l.map((x) => (x.id === t.id ? { ...x, favorite: !next } : x))),
    });
  }

  /** Objectifs, tests et indisponibilités de l'athlète (listes complètes, filtrées par jour). */
  loadOverlays(): void {
    if (!this.selectedAthleteId) return;
    this.activityService.list(this.selectedAthleteId).subscribe({ next: (a) => this.activities.set(a), error: () => this.activities.set([]) });
    this.raceService.list(this.selectedAthleteId).subscribe({ next: (r) => this.objectives.set(r), error: () => this.objectives.set([]) });
    this.lactateService.list(this.selectedAthleteId).subscribe({ next: (t) => this.tests.set(t), error: () => this.tests.set([]) });
    this.athleteService.listUnavailabilities(this.selectedAthleteId).subscribe({ next: (u) => this.unavailabilities.set(u), error: () => this.unavailabilities.set([]) });
  }

  setMode(mode: 'week' | 'month'): void {
    this.mode.set(mode);
    this.load();
  }
  onAthleteChange(): void { this.load(); this.loadOverlays(); }
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
    this.noteService.list(this.selectedAthleteId, from, to).subscribe({
      next: (n) => this.notes.set(n), error: () => this.notes.set([]),
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

  // Périodisation assistée (mésocycle progressif).
  readonly showMeso = signal(false);
  mesoWeeks = 4;
  mesoIncrease = 10;
  mesoDeloadEvery = 4;
  mesoDeloadPct = 60;
  readonly mesoBusy = signal(false);

  /** Modèles de mésocycle réutilisables + cible (athlète courant ou groupe). */
  readonly mesoTemplates = signal<MesocycleTemplate[]>([]);
  readonly groups = signal<TrainingGroup[]>([]);
  mesoTemplateId = '';
  mesoTarget: 'athlete' | 'group' = 'athlete';
  mesoGroupId = '';
  mesoSaveName = '';
  readonly mesoSaving = signal(false);

  toggleMeso(): void {
    this.showMeso.update((v) => !v);
    if (this.showMeso() && this.mesoTemplates().length === 0) {
      this.mesoTemplateService.list().subscribe((t) => this.mesoTemplates.set(t));
    }
    if (this.showMeso() && this.groups().length === 0) {
      this.groupService.list().subscribe((g) => this.groups.set(g));
    }
  }

  /** Pré-remplit les paramètres depuis le modèle choisi (ou repasse en saisie libre). */
  onMesoTemplateChange(): void {
    const t = this.mesoTemplates().find((m) => m.id === this.mesoTemplateId);
    if (t) {
      this.mesoWeeks = t.weeks;
      this.mesoIncrease = t.increasePct;
      this.mesoDeloadEvery = t.deloadEvery;
      this.mesoDeloadPct = t.deloadPct;
    }
  }

  /** Enregistre les paramètres courants comme « méso type » réutilisable. */
  saveMesoTemplate(): void {
    if (!this.mesoSaveName.trim() || this.mesoSaving()) {
      this.toast.warning('Donnez un nom au modèle.');
      return;
    }
    this.mesoSaving.set(true);
    this.mesoTemplateService.create({
      name: this.mesoSaveName.trim(),
      weeks: this.mesoWeeks,
      increasePct: this.mesoIncrease,
      deloadEvery: this.mesoDeloadEvery,
      deloadPct: this.mesoDeloadPct,
    }).subscribe({
      next: (t) => {
        this.mesoTemplates.update((list) => [...list, t].sort((a, b) => a.name.localeCompare(b.name)));
        this.mesoTemplateId = t.id;
        this.mesoSaveName = '';
        this.mesoSaving.set(false);
        this.toast.success('Modèle de mésocycle enregistré');
      },
      error: () => { this.mesoSaving.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  /** Génère un mésocycle à partir de la semaine affichée (= semaine type), pour l'athlète ou le groupe. */
  generateMeso(): void {
    if (this.mode() !== 'week' || this.mesoBusy()) return;
    const sourceStart = mondayOf(this.anchor());
    const firstStart = new Date(sourceStart);
    firstStart.setDate(firstStart.getDate() + 7); // le mésocycle démarre la semaine suivante
    const params: MesocycleParams = {
      sourceWeekStart: toIso(sourceStart),
      firstWeekStart: toIso(firstStart),
    };
    if (this.mesoTemplateId) {
      params.mesocycleTemplateId = this.mesoTemplateId;
    } else {
      params.weeks = this.mesoWeeks;
      params.increasePct = this.mesoIncrease;
      params.deloadEvery = this.mesoDeloadEvery;
      params.deloadPct = this.mesoDeloadPct;
    }

    if (this.mesoTarget === 'group') {
      if (!this.mesoGroupId) { this.toast.warning('Choisissez un groupe.'); return; }
      this.mesoBusy.set(true);
      this.workoutService.generateMesocycleForGroup(this.mesoGroupId, params).subscribe({
        next: (r) => {
          this.mesoBusy.set(false);
          this.showMeso.set(false);
          const skip = r.skipped ? `, ${r.skipped} ignoré(s)` : '';
          this.toast.success(`Mésocycle généré : ${r.created} séance(s) sur ${r.athletes} athlète(s)${skip}`);
          this.anchor.set(firstStart);
          this.load();
        },
        error: () => { this.mesoBusy.set(false); this.toast.error('Génération impossible.'); },
      });
      return;
    }

    if (!this.selectedAthleteId) return;
    this.mesoBusy.set(true);
    this.workoutService.generateMesocycle(this.selectedAthleteId, params).subscribe({
      next: (r) => {
        this.mesoBusy.set(false);
        this.showMeso.set(false);
        this.toast.success(`Mésocycle généré : ${r.created} séance(s)`);
        this.anchor.set(firstStart);
        this.load();
      },
      error: () => { this.mesoBusy.set(false); this.toast.error('Génération impossible.'); },
    });
  }

  /** Duplique la semaine course affichée vers la semaine suivante (planification en cycles). */
  async duplicateWeek(): Promise<void> {
    if (!this.selectedAthleteId || this.mode() !== 'week') return;
    const sourceStart = mondayOf(this.anchor());
    const source = toIso(sourceStart);
    const targetStart = new Date(sourceStart);
    targetStart.setDate(targetStart.getDate() + 7);
    const target = toIso(targetStart);

    const ok = await this.confirm.ask({
      title: 'Dupliquer la semaine',
      message: `Copier les séances course de cette semaine vers la semaine du ${target} ? Les séances existantes de la semaine cible sont conservées.`,
      confirmLabel: 'Dupliquer',
    });
    if (!ok) return;

    this.workoutService.duplicateWeek(this.selectedAthleteId, source, target).subscribe({
      next: (r) => {
        this.toast.success(r.created > 0
          ? `${r.created} séance(s) copiée(s) sur la semaine suivante`
          : 'Aucune séance à copier cette semaine');
        this.anchor.set(targetStart); // basculer sur la semaine cible pour voir le résultat
        this.load();
      },
      error: () => this.toast.error('Duplication impossible.'),
    });
  }

  /** Date pour laquelle le sélecteur de séance course est ouvert (null = fermé). */
  readonly pickerDate = signal<string | null>(null);

  /**
   * Panneau bibliothèque (colonne de gauche) — le geste central de la planification desktop.
   * Ouvert par défaut sur desktop large (poste de planification), replié sur petit écran.
   * Préférence mémorisée entre sessions (comme la nav latérale).
   */
  private static readonly LIB_KEY = 'coach-cal-lib-open';
  readonly sidebarOpen = signal(this.readLibPref());

  private readLibPref(): boolean {
    try {
      const saved = localStorage.getItem(CalendarComponent.LIB_KEY);
      if (saved !== null) return saved === '1';
    } catch { /* stockage indisponible : on retombe sur le défaut selon la largeur */ }
    // Défaut : ouvert sur desktop large, fermé sinon (le mobile reste de la consultation).
    return typeof window !== 'undefined' && window.innerWidth >= 1024;
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((v) => !v);
    try { localStorage.setItem(CalendarComponent.LIB_KEY, this.sidebarOpen() ? '1' : '0'); }
    catch { /* préférence non persistée, sans gravité */ }
  }

  /** Le coach peut-il prescrire à l'athlète sélectionné ? (false = lecture seule). */
  canWriteSelected(): boolean {
    const a = this.athletes().find((x) => x.id === this.selectedAthleteId);
    return a?.canWrite !== false;
  }

  /** Ouvre le sélecteur de modèle de séance course (planification structurée, en fourchettes). */
  addWorkout(date: string): void {
    if (!this.selectedAthleteId) { this.toast.error('Sélectionne un athlète.'); return; }
    if (!this.canWriteSelected()) { this.toast.warning('Lecture seule : tu n’as pas les droits de prescription sur cet athlète.'); return; }
    this.pickerDate.set(date);
  }

  closePicker(): void { this.pickerDate.set(null); this.noteOpen.set(false); this.noteText = ''; }

  /** Saisie de note inline dans le picker (remplace l'ancien window.prompt). */
  readonly noteOpen = signal(false);
  noteText = '';
  toggleNote(): void { this.noteOpen.update((v) => !v); if (!this.noteOpen()) this.noteText = ''; }

  /** Drop d'un éducatif : crée une courte séance technique avec la gamme attachée à l'échauffement. */
  private dropDrill(drill: RunDrill, date: string): void {
    this.workoutService.create(this.selectedAthleteId, {
      scheduledDate: date, type: 'ENDURANCE', title: 'Technique — ' + drill.name, notes: null, steps: [],
    }).subscribe({
      next: (w) => {
        this.workoutService.updateStructure(this.selectedAthleteId, w.id, {
          warmup: [{ id: 'wu-' + Math.random().toString(36).slice(2, 8), type: 'warmup', drillIds: [drill.id] }],
          main: [], cooldown: [],
        }).subscribe({
          next: () => { this.toast.success(`${drill.name} planifié le ${date}`); this.load(); },
          error: () => this.toast.error('Création impossible.'),
        });
      },
      error: () => this.toast.error('Création impossible.'),
    });
  }

  /** Ajoute une note libre sur la date du picker (chip note, CDC §8). */
  addNote(): void {
    const date = this.pickerDate();
    const text = this.noteText.trim();
    if (!date || !text) return;
    this.noteService.create(this.selectedAthleteId, { noteDate: date, text }).subscribe({
      next: () => { this.closePicker(); this.toast.success('Note ajoutée'); this.load(); },
      error: () => this.toast.error('Ajout impossible.'),
    });
  }

  async deleteNote(n: CalendarNote, ev: Event): Promise<void> {
    ev.stopPropagation();
    const ok = await this.confirm.ask({
      title: 'Supprimer la note ?', message: n.text, confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.noteService.delete(this.selectedAthleteId, n.id).subscribe({
      next: () => { this.toast.info('Note supprimée.'); this.load(); },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  /** Crée une séance course vierge (ad hoc) sur la date puis ouvre l'éditeur de structure. */
  createAdHoc(): void {
    const date = this.pickerDate();
    if (!date) return;
    this.workoutService.create(this.selectedAthleteId, {
      scheduledDate: date, type: 'ENDURANCE', title: 'Séance', notes: null, steps: [],
    }).subscribe({
      next: (w) => {
        this.closePicker();
        this.router.navigate(['/app/athletes', this.selectedAthleteId, 'workouts', w.id, 'structure']);
      },
      error: () => this.toast.error('Création impossible.'),
    });
  }

  /** Planifie un modèle de séance course sur la date choisie (snapshot figé + cibles en fourchettes). */
  scheduleTemplateOn(t: WorkoutTemplate): void {
    const date = this.pickerDate();
    if (!date) return;
    this.courseService.schedule(this.selectedAthleteId, t.id, { date }).subscribe({
      next: () => { this.toast.success(`${t.name} planifiée le ${date}`); this.closePicker(); this.load(); },
      error: () => this.toast.error('Planification impossible.'),
    });
  }
  openWorkout(w: Workout): void {
    // Vue séance (lecture) ; l'édition est une action délibérée depuis la page.
    this.router.navigate(['/app/athletes', w.athleteId, 'workouts', w.id]);
  }
  openObjectives(): void { this.router.navigate(['/app/athletes', this.selectedAthleteId, 'races']); }
  openTests(): void { this.router.navigate(['/app/athletes', this.selectedAthleteId, 'tests']); }

  onDrop(event: CdkDragDrop<DayCell>, targetDate: string): void {
    const data = event.item.data as Workout | StrengthSession | WorkoutTemplate;
    const rec = data as unknown as Record<string, unknown>;

    // Garde-fou UX : pas de planification/déplacement sur un athlète en lecture seule
    // (le backend renverrait 403). Cohérent avec la permission write côté serveur.
    if (!this.canWriteSelected()) {
      this.toast.warning('Lecture seule : tu n’as pas les droits de prescription sur cet athlète.');
      return;
    }

    // Éducatif (gamme) glissé depuis la bibliothèque → séance technique ad hoc avec l'éducatif.
    if (rec['category'] === 'TECHNIQUE' || rec['category'] === 'AMPLITUDE') {
      this.dropDrill(data as unknown as RunDrill, targetDate);
      return;
    }

    // Séance de force glissée depuis la bibliothèque → planification.
    if ('structure' in rec) {
      const s = data as StrengthSession;
      this.strengthService
        .scheduleSession(this.selectedAthleteId, s.id, { date: targetDate, fieldsPreset: 'AVANCE' })
        .subscribe({
          next: () => { this.toast.success(`${s.name} planifiée le ${targetDate}`); this.reloadStrength(); },
          error: () => this.toast.error('Planification impossible.'),
        });
      return;
    }

    // Modèle de séance course glissé depuis la bibliothèque → planification.
    if (!('scheduledDate' in rec)) {
      const t = data as WorkoutTemplate;
      this.courseService.schedule(this.selectedAthleteId, t.id, { date: targetDate }).subscribe({
        next: () => { this.toast.success(`${t.name} planifiée le ${targetDate}`); this.load(); },
        error: () => this.toast.error('Planification impossible.'),
      });
      return;
    }

    const w = data as Workout;

    // Glisser + Alt/Ctrl → duplication de la séance vers le jour cible (au lieu d'un déplacement).
    const native = event.event as MouseEvent;
    if (native && (native.altKey || native.ctrlKey || native.metaKey)) {
      this.copyWorkout(w, targetDate);
      return;
    }

    // Réordonnancement au sein d'un même jour (matin / soir).
    if (event.previousContainer === event.container) {
      this.reorderWithinDay(targetDate, event.previousIndex, event.currentIndex);
      return;
    }

    // Déplacement d'une séance course existante vers un autre jour.
    this.moveWorkout(w, targetDate);
  }

  /** Réordonne les séances d'un jour (glisser intra-jour) : mise à jour optimiste des orderIndex. */
  private reorderWithinDay(date: string, from: number, to: number): void {
    if (from === to) return;
    const dayWorkouts = this.workouts()
      .filter((w) => w.scheduledDate === date)
      .sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
    if (from < 0 || to < 0 || from >= dayWorkouts.length || to >= dayWorkouts.length) return;
    moveItemInArray(dayWorkouts, from, to);
    const orderById = new Map(dayWorkouts.map((w, i) => [w.id, i]));
    this.workouts.update((l) => l.map((w) => (orderById.has(w.id) ? { ...w, orderIndex: orderById.get(w.id)! } : w)));
    this.workoutService.reorder(this.selectedAthleteId, date, [...orderById.keys()]).subscribe({
      error: () => { this.toast.error('Réordonnancement impossible.'); this.load(); },
    });
  }

  /** Déplace une séance vers une date (optimiste + rollback en cas d'échec back). */
  private moveWorkout(w: Workout, targetDate: string): void {
    if (w.scheduledDate === targetDate) return;
    const previous = w.scheduledDate;
    this.workouts.update((l) => l.map((x) => (x.id === w.id ? { ...x, scheduledDate: targetDate } : x)));
    this.workoutService.reschedule(this.selectedAthleteId, w.id, targetDate).subscribe({
      next: () => this.toast.success(`Séance déplacée au ${targetDate}`),
      error: () => {
        this.workouts.update((l) => l.map((x) => (x.id === w.id ? { ...x, scheduledDate: previous } : x)));
        this.toast.error('Déplacement impossible.');
      },
    });
  }

  /** Duplique une séance vers une date (copie figée côté back). */
  private copyWorkout(w: Workout, targetDate: string): void {
    this.workoutService.copy(this.selectedAthleteId, w.id, targetDate).subscribe({
      next: () => { this.toast.success(`Séance dupliquée au ${targetDate}`); this.load(); },
      error: () => this.toast.error('Duplication impossible.'),
    });
  }

  // --- Menu contextuel (clic droit) : alternative souris/clavier au glisser-déposer ----------
  readonly ctxMenu = signal<{ workout: Workout; x: number; y: number } | null>(null);
  ctxDate = '';

  openContextMenu(w: Workout, ev: MouseEvent): void {
    ev.preventDefault();
    if (!this.canWriteSelected()) return;
    this.ctxDate = w.scheduledDate;
    // Position bornée à la fenêtre pour éviter le débordement.
    const x = Math.min(ev.clientX, window.innerWidth - 220);
    const y = Math.min(ev.clientY, window.innerHeight - 260);
    this.ctxMenu.set({ workout: w, x, y });
  }
  closeContextMenu(): void { this.ctxMenu.set(null); }

  ctxOpen(): void {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu(); this.openWorkout(m.workout);
  }
  ctxAdapt(): void {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu();
    this.router.navigate(['/app/athletes', m.workout.athleteId, 'workouts', m.workout.id, 'structure']);
  }
  ctxMoveTo(date: string): void {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu();
    if (date) this.moveWorkout(m.workout, date);
  }
  ctxCopyTo(date: string): void {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu();
    if (date) this.copyWorkout(m.workout, date);
  }
  async ctxDelete(): Promise<void> {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu();
    const ok = await this.confirm.ask({
      title: 'Supprimer la séance', message: m.workout.title, confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.workoutService.delete(this.selectedAthleteId, m.workout.id).subscribe({
      next: () => { this.toast.info('Séance supprimée.'); this.load(); },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  /** Raccourcis clavier de navigation (hors champ de saisie) : ←/→ période, T = aujourd'hui. */
  onKeydown(ev: KeyboardEvent): void {
    const el = ev.target as HTMLElement | null;
    if (el && /^(INPUT|SELECT|TEXTAREA)$/.test(el.tagName)) return;
    if (ev.ctrlKey || ev.metaKey || ev.altKey) return;
    if (this.ctxMenu()) return;
    if (ev.key === 'ArrowLeft') { this.shift(-1); ev.preventDefault(); }
    else if (ev.key === 'ArrowRight') { this.shift(1); ev.preventDefault(); }
    else if (ev.key === 't' || ev.key === 'T') { this.goToday(); ev.preventDefault(); }
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

  /** Regroupe une liste par clé de date (générique). */
  private groupBy<T>(items: T[], key: (item: T) => string): Map<string, T[]> {
    const map = new Map<string, T[]>();
    for (const it of items) {
      const k = key(it);
      const arr = map.get(k) ?? map.set(k, []).get(k)!;
      arr.push(it);
    }
    return map;
  }

  private groupByDate(): Map<string, Workout[]> {
    const map = new Map<string, Workout[]>();
    for (const w of this.workouts()) {
      const arr = map.get(w.scheduledDate) ?? map.set(w.scheduledDate, []).get(w.scheduledDate)!;
      arr.push(w);
    }
    // Ordre intra-jour (glisser-déposer) : trie chaque jour par orderIndex.
    for (const arr of map.values()) arr.sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
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
