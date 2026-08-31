import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { Observable, defer, forkJoin, of, switchMap, tap } from 'rxjs';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AthleteSummary } from '../../core/models/athlete.model';
import {
  STATUS_BADGE, STATUS_LABELS, WORKOUT_TYPE_LABELS, WORKOUT_TYPE_META,
  Workout, WorkoutType, WorkoutTypeMeta,
} from '../../core/models/workout.model';
import { AthleteService } from '../../core/services/athlete.service';
import { CourseService } from '../../core/services/course.service';
import { StrengthService } from '../../core/services/strength.service';
import { ScheduledStrength, StrengthPrescriptionView, StrengthSession } from '../../core/models/strength.model';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { PaceReferenceService } from '../../core/services/pace-reference.service';
import { plannedVolume, plannedVolumeLabel } from '../../core/utils/planned-volume';
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
import { GroupCalendarRow, TrainingGroupService } from '../../core/services/training-group.service';
import { MesocycleParams } from '../../core/services/workout.service';
import { RunDrill } from '../../core/models/run-drill.model';
import { RunDrillService } from '../../core/services/run-drill.service';
import { CalendarNote, isCycle } from '../../core/models/calendar-note.model';
import { CalendarNoteService } from '../../core/services/calendar-note.service';
import { WeekOutlook } from '../../core/models/decision.model';
import { DecisionService } from '../../core/services/decision.service';
import { SessionCategory } from '../../core/models/session-category.model';
import { SessionCategoryService } from '../../core/services/session-category.service';
import { SessionLibraryPanelComponent } from '../../shared/components/session-library-panel/session-library-panel.component';
import { StrengthPrescriptionViewComponent } from '../../shared/components/strength-prescription-view/strength-prescription-view.component';
import { SidePanelComponent } from '../../shared/components/ui';
import { ZoneBarComponent } from '../../shared/components/zone-bar/zone-bar.component';
import { Activity } from '../../core/models/activity.model';
import { ActivityService } from '../../core/services/activity.service';
import { UndoCommand, UndoStackService } from '../../core/services/undo-stack.service';
import { AthleteSwitcherComponent } from '../../shared/components/athlete-switcher/athlete-switcher.component';
import { ShortcutsOverlayComponent } from '../../shared/components/shortcuts/shortcuts-overlay.component';
import { shortcutText } from '../../shared/components/shortcuts/shortcuts';
import { CALENDAR_SHORTCUTS } from './calendar-shortcuts';
import {
  CELLS_BY_MODE, CalMode, gridStartFor, mondayOf, periodLabelFor, shiftAnchor,
} from './calendar-period';
import { CalendarSelection, ChipPosition, ChipRef } from './calendar-selection';
import { SessionStructure } from '../../core/models/course.model';

/** Vue du calendrier : séances prévues, activités réalisées, ou les deux (façon Nolio). */
type CalView = 'planned' | 'realized' | 'both';

interface DayCell {
  date: string;
  label: string;
  dayNum: number;
  isToday: boolean;
  /**
   * Samedi ou dimanche. Un plan de course se lit autour du week-end — sortie longue, course,
   * séance clé — et la grille ne distinguait pas les sept colonnes : compter les jours depuis le
   * bord était le seul moyen de savoir où l'on regardait.
   */
  weekend: boolean;
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

/**
 * Un cycle tel qu'il traverse une semaine affichée : la note, et les colonnes qu'elle occupe.
 * Un cycle de trois semaines produit trois bandeaux, un par ligne de la grille.
 */
interface CycleBand {
  note: CalendarNote;
  /** Colonne de départ dans la semaine (0 = lundi) et nombre de jours couverts. */
  startCol: number;
  span: number;
  /** Le cycle commence — ou finit — dans cette semaine ? Sinon le bandeau se prolonge. */
  startsHere: boolean;
  endsHere: boolean;
}

/** Semaine (7 jours) + totaux agrégés (prévu et réalisé), façon Nolio (colonne de droite). */
interface WeekRow {
  days: DayCell[];
  /** Cycles actifs cette semaine (bandeaux au-dessus des jours). */
  cycles: CycleBand[];
  km: number;
  durationS: number;
  /** Charge prévue de la semaine en UA (sRPE) : le volume seul ne dit rien de la difficulté. */
  loadUa: number;
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

// La sémantique de type (couleur, icône, « séance clé ») vit dans le modèle : le calendrier de
// l'athlète peint les mêmes couleurs, et un seuil ne peut pas changer de teinte d'un écran à
// l'autre.
type TypeMeta = WorkoutTypeMeta;
const TYPE_META = WORKOUT_TYPE_META;

function toIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-calendar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, RouterLink, DragDropModule, DatePipe, IconComponent, HelpHintComponent,
    SessionLibraryPanelComponent, SidePanelComponent, StrengthPrescriptionViewComponent, ZoneBarComponent,
    AthleteSwitcherComponent, ShortcutsOverlayComponent,
  ],
  host: { '(document:keydown)': 'onKeydown($event)' },
  templateUrl: './calendar.component.html',
  styleUrls: ['./calendar.component.scss', './calendar-group.scss', './calendar-tools.scss'],
})
export class CalendarComponent implements OnInit, OnDestroy {
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
  private readonly undoStack = inject(UndoStackService);

  readonly reasonMeta = REASON_META;
  readonly shortcutGroups = CALENDAR_SHORTCUTS;

  // --- Sélection multiple, presse-papier, annulation ----------------------------------------
  // Le calendrier passait de « lecture » à « production » une séance à la fois. Ces trois états
  // en font un plan de travail : on désigne un lot, on agit dessus, on revient en arrière.

  private readonly decisions = inject(DecisionService);

  /**
   * Ce que la semaine affichée fera à l'athlète, dit pendant qu'on la construit.
   *
   * <p>La charge prévue de chaque séance était déjà calculée et déjà additionnée ici ; elle
   * n'était simplement jamais confrontée à la charge habituelle de l'athlète. L'ACWR alertait
   * donc le lundi suivant, sur une semaine qu'il avait déjà courue.</p>
   */
  readonly weekOutlook = signal<WeekOutlook | null>(null);

  readonly selection = new CalendarSelection();
  /** Séances copiées (Cmd+C) : on garde la référence, pas une copie de l'objet. */
  readonly clipboard = signal<{ refs: ChipRef[]; label: string }>({ refs: [], label: '' });
  /** Jour survolé : cible implicite de Cmd+V et de N, comme un curseur de tableur. */
  readonly hoveredDate = signal<string | null>(null);
  /** Plage de jours retenue par un rectangle tracé sur des jours vides. */
  readonly dayRange = signal<string[]>([]);
  readonly shortcutsOpen = signal(false);
  readonly viewMenuOpen = signal(false);
  readonly actionsMenuOpen = signal(false);

  readonly canUndo = this.undoStack.canUndo;
  readonly canRedo = this.undoStack.canRedo;
  readonly undoLabel = this.undoStack.undoLabel;
  readonly redoLabel = this.undoStack.redoLabel;

  /** Libellé d'un raccourci pour les infobulles et les menus (⌘ ou Ctrl selon la plateforme). */
  keys(...k: string[]): string { return shortcutText(k); }

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  private readonly dayNames = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  /**
   * Athlète imposé par le contexte (onglet « Programme » de la coquille athlète). Absent sur
   * l'écran Calendrier global, où le coach choisit lui-même dans le sélecteur.
   */
  readonly athleteId = input<string>();
  /** Vrai quand le calendrier est cadré sur un athlète : le sélecteur est alors masqué. */
  readonly lockedToAthlete = computed(() => !!this.athleteId());

  readonly athletes = signal<AthleteSummary[]>([]);
  private readonly paceReference = inject(PaceReferenceService);

  selectedAthleteId = '';

  /** Allure d'endurance de l'athlète affiché, seule base admise pour estimer un volume. */
  readonly referencePace = signal<number | null>(null);

  /**
   * Volume d'une séance prévue, « 12 km » ou « ≈ 10 km ».
   *
   * <p>Une séance écrite en durée sans allure prescrite ne totalise que ses blocs chiffrables —
   * quelques centaines de mètres d'éducatifs — et s'affichait « 0,1 km » pour une heure de
   * course. Le « ≈ » distingue l'ordre de grandeur de la consigne.</p>
   */
  volumeLabel(w: Workout): string {
    return plannedVolumeLabel(
      plannedVolume(w.targetDistanceM, w.targetDurationS, this.referencePace()));
  }

  /** Distance retenue pour les totaux : la cible crédible, ou l'estimation, ou rien. */
  private volumeM(w: Workout): number {
    return plannedVolume(w.targetDistanceM, w.targetDurationS, this.referencePace())?.distanceM ?? 0;
  }
  /**
   * Période affichée. « Jour » est né du téléphone : sous 768 px, la grille passe à une colonne
   * et un mois devient une pile de quarante-deux cartes qu'on ne finit jamais de dérouler. Une
   * journée à la fois, en revanche, se lit d'un coup d'œil et se touche sans viser.
   */
  /**
   * Vue courante, restaurée du choix précédent.
   *
   * <p>Elle était figée à « semaine » à chaque ouverture. Un coach qui travaille au mois — pour
   * poser un bloc, pour relire une charge — repassait donc par le sélecteur à chaque visite, et
   * finissait par croire que la semaine était la seule vue possible.</p>
   */
  readonly mode = signal<CalMode>(CalendarComponent.savedMode());
  /** Vue prévu / réalisé / les deux (façon Nolio). */
  readonly view = signal<CalView>('both');
  readonly showPlanned = computed(() => this.view() !== 'realized');
  readonly showRealized = computed(() => this.view() !== 'planned');
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
    // Une note de période est un cycle : elle vit en bandeau au-dessus de la semaine, pas en
    // chip sur son premier jour — où elle donnerait à croire qu'elle ne concerne que lui.
    const noteByDate = this.groupBy(this.notes().filter((n) => !isCycle(n)), (n) => n.noteDate);
    const activityByDate = this.groupBy(this.activities(), (a) => a.activityDate);
    const unavail = this.unavailabilities();
    const count = CELLS_BY_MODE[this.mode()];
    const start = this.gridStart();
    const monthRef = this.anchor().getMonth();
    return Array.from({ length: count }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      const workouts = byDate.get(iso) ?? [];
      const strength = strengthByDate.get(iso) ?? [];
      const km = workouts.reduce((s, w) => s + this.volumeM(w), 0) / 1000;
      const sessions = workouts.length + strength.length;
      const hasKey = workouts.some((w) => TYPE_META[w.type].key);
      return {
        date: iso,
        label: this.mode() === 'day' ? this.dayNames[(d.getDay() + 6) % 7] : this.dayNames[i % 7],
        dayNum: d.getDate(),
        isToday: iso === today,
        weekend: i % 7 >= 5,
        inMonth: this.mode() !== 'month' || d.getMonth() === monthRef,
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

  /**
   * Ordre de parcours des chips dans la grille (jour par jour, course puis force). C'est lui
   * qui donne un sens à « de celle-ci à celle-là » du Maj+clic : sans ordre explicite, une
   * extension de sélection dépendrait de l'ordre du DOM, donc du hasard du rendu.
   */
  readonly chipOrder = computed<ChipPosition[]>(() => {
    const out: ChipPosition[] = [];
    for (const cell of this.cells()) {
      let index = 0;
      for (const w of cell.workouts) out.push({ kind: 'course', id: w.id, date: cell.date, index: index++ });
      for (const st of cell.strength) out.push({ kind: 'strength', id: st.id, date: cell.date, index: index++ });
    }
    return out;
  });

  /** Sélection résolue dans l'ordre de la grille (ce sur quoi agissent les actions groupées). */
  readonly selected = computed<ChipPosition[]>(() => this.selection.resolve(this.chipOrder()));

  isSelected(kind: 'course' | 'strength', id: string): boolean {
    return this.selection.has({ kind, id });
  }

  /**
   * Clic sur une chip. Le modificateur décide : rien = ouvrir la séance (le geste de lecture
   * reste premier), Cmd/Ctrl = ajouter au lot, Maj = étendre depuis l'ancre. On n'ouvre jamais
   * une séance pendant qu'on constitue une sélection, ce serait perdre le lot en cours.
   */
  onChipClick(ev: MouseEvent, kind: 'course' | 'strength', target: Workout | ScheduledStrength): void {
    const ref: ChipRef = { kind, id: target.id };
    if (ev.shiftKey) {
      ev.preventDefault();
      this.selection.extendTo(ref, this.chipOrder());
      return;
    }
    if (ev.metaKey || ev.ctrlKey) {
      ev.preventDefault();
      this.selection.toggle(ref);
      return;
    }
    // Une sélection en cours : le clic simple la réduit à cette chip plutôt que d'ouvrir.
    if (this.selection.count() > 1) {
      this.selection.select(ref);
      return;
    }
    this.selection.clear();
    if (kind === 'course') this.openWorkout(target as Workout);
    else this.openStrength(target as ScheduledStrength);
  }

  /** Jour survolé : cible de Cmd+V et de N. */
  onDayEnter(date: string): void { this.hoveredDate.set(date); }
  onDayLeave(date: string): void { if (this.hoveredDate() === date) this.hoveredDate.set(null); }

  selectAll(): void {
    this.selection.setAll(this.chipOrder());
  }

  clearSelection(): void {
    this.selection.clear();
    this.dayRange.set([]);
  }

    // --- Mode groupe : la semaine de tous les athlètes d'un groupe ---------------
  // Un coach de club planifie par groupe, pas athlète par athlète. Le mode groupe
  // affiche une ligne par athlète × 7 jours, en lecture + déplacement (pas de vue
  // mois : au-delà d'une semaine, la grille devient illisible).

  readonly scopeMode = signal<'athlete' | 'group'>('athlete');
  selectedGroupId = '';
  readonly groupRows = signal<GroupCalendarRow[]>([]);
  readonly groupLoading = signal(false);

  /** Les 7 dates de la semaine affichée (en-têtes du mode groupe). */
  readonly weekDates = computed<{ date: string; label: string; dayNum: number; isToday: boolean; weekend: boolean }[]>(() => {
    const today = toIso(new Date());
    const start = mondayOf(this.anchor());
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      return {
        date: iso, label: this.dayNames[i], dayNum: d.getDate(),
        isToday: iso === today, weekend: i >= 5,
      };
    });
  });

  /** Séances d'un athlète pour un jour donné (mode groupe). */
  rowWorkouts(row: GroupCalendarRow, date: string): Workout[] {
    return row.workouts.filter((w) => w.scheduledDate === date);
  }
  rowStrength(row: GroupCalendarRow, date: string): ScheduledStrength[] {
    return row.strength.filter((s) => s.scheduledDate === date);
  }

  setScopeMode(m: 'athlete' | 'group'): void {
    this.scopeMode.set(m);
    if (m === 'athlete') {
      // La semaine affichée a pu changer pendant le passage en mode groupe.
      this.load();
      this.loadOverlays();
      return;
    }
    if (m === 'group') {
      // La grille groupe a sa propre disposition (une ligne par athlète × 7 jours) et ne lit pas
      // mode() : on se contente de garder une période valide pour le retour en mode athlète.
      this.mode.set('4weeks');
      if (this.groups().length === 0) {
        this.groupService.list().subscribe((g) => {
          this.groups.set(g);
          if (!this.selectedGroupId && g.length) {
            this.selectedGroupId = g[0].id;
            this.loadGroup();
          }
        });
      } else {
        this.loadGroup();
      }
    }
  }

  onGroupChange(): void { this.loadGroup(); }

  loadGroup(): void {
    if (!this.selectedGroupId) { this.groupRows.set([]); return; }
    const dates = this.weekDates();
    this.groupLoading.set(true);
    this.groupService.calendar(this.selectedGroupId, dates[0].date, dates[6].date).subscribe({
      next: (c) => { this.groupRows.set(c.athletes); this.groupLoading.set(false); },
      error: () => { this.groupRows.set([]); this.groupLoading.set(false); this.toast.error('Chargement du groupe impossible.'); },
    });
  }

  /**
   * Déplacement dans la grille de groupe. Une séance appartient à un athlète : on refuse
   * explicitement le dépôt sur la ligne d'un autre athlète plutôt que de réassigner en douce.
   */
  onGroupDrop(event: CdkDragDrop<unknown>, row: GroupCalendarRow, targetDate: string): void {
    const item = event.item.data as { athleteId?: string } | undefined;
    if (!item) return;
    if (!row.canWrite) {
      this.toast.warning(`Lecture seule sur ${row.firstName} ${row.lastName}.`);
      return;
    }
    if (item.athleteId && item.athleteId !== row.athleteId) {
      this.toast.warning('Une séance ne se déplace pas d’un athlète à un autre.');
      return;
    }

    const rec = item as unknown as Record<string, unknown>;

    // Éléments venus de la bibliothèque (pas de date planifiée) : planification sur la ligne
    // de l'athlète. C'est ce qui manquait pour que le mode groupe soit une vue de travail.
    const refreshGroup = () => this.loadGroup();
    if (rec['category'] === 'TECHNIQUE' || rec['category'] === 'AMPLITUDE') {
      this.dropDrill(item as unknown as RunDrill, targetDate, row.athleteId, refreshGroup);
      return;
    }
    if ('structure' in rec) {
      this.scheduleStrength(item as unknown as StrengthSession, row.athleteId, targetDate, refreshGroup);
      return;
    }
    if (!('scheduledDate' in rec)) {
      this.scheduleTemplate(item as unknown as WorkoutTemplate, row.athleteId, targetDate, refreshGroup);
      return;
    }

    if ('sourceSessionId' in rec) {
      this.moveGroupStrength(row, item as unknown as ScheduledStrength, targetDate);
    } else {
      this.moveGroupWorkout(row, item as unknown as Workout, targetDate);
    }
  }

  /** Une planification de groupe est en cours (une requête, N séances créées côté serveur). */
  readonly groupScheduling = signal(false);

  /**
   * Dépôt sur la ligne « tout le groupe » : la séance est planifiée pour chaque athlète du
   * groupe, en une requête.
   *
   * <p>Seule la bibliothèque est acceptée. Déplacer une séance <b>déjà planifiée</b> vers cette
   * ligne n'aurait pas de sens : elle appartient à un athlète, et la dupliquer chez les quatorze
   * autres en la déplaçant chez son propriétaire serait la dernière chose attendue d'un geste de
   * déplacement.</p>
   */
  onGroupAllDrop(event: CdkDragDrop<unknown>, targetDate: string): void {
    const item = event.item.data as Record<string, unknown> | undefined;
    if (!item || !this.selectedGroupId) return;
    if ('scheduledDate' in item) {
      this.toast.warning('Glisse une séance de la bibliothèque pour la donner à tout le groupe.');
      return;
    }
    if (item['category'] === 'TECHNIQUE' || item['category'] === 'AMPLITUDE') {
      this.toast.warning('Les éducatifs se planifient athlète par athlète.');
      return;
    }
    const isStrength = 'structure' in item;
    const name = String(item['name'] ?? 'La séance');
    this.groupScheduling.set(true);
    this.groupService.schedule(this.selectedGroupId, {
      date: targetDate,
      templateId: isStrength ? null : (item['id'] as string),
      strengthSessionId: isStrength ? (item['id'] as string) : null,
    }).subscribe({
      next: (r) => {
        this.groupScheduling.set(false);
        this.loadGroup();
        // On annonce ce qui a réellement été fait : un athlète hors du périmètre du coach est
        // ignoré côté serveur, et le taire ferait croire à une prescription complète.
        const skipped = r.skipped > 0 ? ` — ${r.skipped} athlète(s) ignoré(s) (lecture seule)` : '';
        this.toast.success(
          `${name} planifiée le ${this.fmtDate(targetDate)} pour ${r.athletes} athlète(s)${skipped}`);
      },
      error: () => { this.groupScheduling.set(false); this.toast.error('Planification de groupe impossible.'); },
    });
  }

  private moveGroupWorkout(row: GroupCalendarRow, w: Workout, targetDate: string): void {
    if (w.scheduledDate === targetDate) return;
    this.patchGroupRow(row.athleteId, (r) => ({
      ...r, workouts: r.workouts.map((x) => (x.id === w.id ? { ...x, scheduledDate: targetDate } : x)),
    }));
    this.workoutService.reschedule(row.athleteId, w.id, targetDate).subscribe({
      next: () => this.toast.success(`${w.title} déplacée au ${this.fmtDate(targetDate)}`),
      error: () => {
        this.patchGroupRow(row.athleteId, (r) => ({
          ...r, workouts: r.workouts.map((x) => (x.id === w.id ? { ...x, scheduledDate: w.scheduledDate } : x)),
        }));
        this.toast.error('Déplacement impossible.');
      },
    });
  }

  private moveGroupStrength(row: GroupCalendarRow, s: ScheduledStrength, targetDate: string): void {
    if (s.scheduledDate === targetDate) return;
    this.patchGroupRow(row.athleteId, (r) => ({
      ...r, strength: r.strength.map((x) => (x.id === s.id ? { ...x, scheduledDate: targetDate } : x)),
    }));
    this.strengthService.rescheduleScheduled(row.athleteId, s.id, targetDate).subscribe({
      next: () => this.toast.success(`${s.title} déplacée au ${this.fmtDate(targetDate)}`),
      error: () => {
        this.patchGroupRow(row.athleteId, (r) => ({
          ...r, strength: r.strength.map((x) => (x.id === s.id ? { ...x, scheduledDate: s.scheduledDate } : x)),
        }));
        this.toast.error('Déplacement impossible.');
      },
    });
  }

  private patchGroupRow(athleteId: string, patch: (r: GroupCalendarRow) => GroupCalendarRow): void {
    this.groupRows.update((l) => l.map((r) => (r.athleteId === athleteId ? patch(r) : r)));
  }

  /** Ouvre la séance course d'une ligne de groupe (lecture). */
  openGroupWorkout(row: GroupCalendarRow, w: Workout): void {
    this.router.navigate(['/app/athletes', row.athleteId, 'workouts', w.id]);
  }

  /** Semaines (lignes de 7 jours) + totaux — colonne de droite façon Nolio. */
  readonly weeks = computed<WeekRow[]>(() => {
    const cells = this.cells();
    const rows: WeekRow[] = [];
    for (let i = 0; i < cells.length; i += 7) {
      const days = cells.slice(i, i + 7);
      const km = days.reduce((s, d) => s + d.km, 0);
      const durationS = days.reduce(
        (s, d) => s + d.workouts.reduce((a, w) => a + (w.targetDurationS ?? 0), 0), 0);
      const loadUa = days.reduce(
        (s, d) => s + d.workouts.reduce((a, w) => a + (w.plannedLoadUa ?? 0), 0), 0);
      const sessions = days.reduce((s, d) => s + d.sessions, 0);
      const realKm = days.reduce((s, d) => s + d.activities.reduce((a, x) => a + (x.distanceM ?? 0), 0), 0) / 1000;
      const realDurationS = days.reduce((s, d) => s + d.activities.reduce((a, x) => a + (x.durationS ?? 0), 0), 0);
      const realSessions = days.reduce((s, d) => s + d.activities.length, 0);
      rows.push({
        days, cycles: this.bandsFor(days), km, durationS, loadUa, sessions,
        realKm, realDurationS, realSessions,
      });
    }
    return rows;
  });

  /**
   * Cycles traversant une semaine, découpés à ses bornes.
   *
   * <p>Un cycle commencé avant le lundi affiché — ou finissant après le dimanche — n'est pas
   * tronqué de la vue : son bandeau démarre (ou s'arrête) au bord de la semaine, et perd sa
   * marque de début ou de fin pour dire qu'il se prolonge au-delà.</p>
   */
  private bandsFor(days: DayCell[]): CycleBand[] {
    // Dernier jour de la rangée, et non le septième : la vue « Jour » n'en affiche qu'un, et
    // `days[6]` y était indéfini — l'exception remontait dans le calcul des semaines, qui ne
    // rendait alors plus aucune colonne.
    const lastCol = days.length - 1;
    if (lastCol < 0) return [];
    const first = days[0].date;
    const last = days[lastCol].date;
    const bands: CycleBand[] = [];
    for (const note of this.notes()) {
      if (!isCycle(note)) continue;
      const end = note.endDate!;
      if (note.noteDate > last || end < first) continue;
      const startCol = note.noteDate <= first ? 0 : days.findIndex((d) => d.date === note.noteDate);
      const endCol = end >= last ? lastCol : days.findIndex((d) => d.date === end);
      if (startCol < 0 || endCol < 0) continue;
      bands.push({
        note, startCol, span: endCol - startCol + 1,
        startsHere: note.noteDate >= first, endsHere: end <= last,
      });
    }
    return bands;
  }

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

  readonly periodLabel = computed(() => periodLabelFor(this.mode(), this.anchor()));

  readonly weeklyVolumeKm = computed(() => {
    const m = this.workouts().reduce((s, w) => s + this.volumeM(w), 0);
    return (m / 1000).toFixed(1);
  });

  /** Infobulle d'une séance déplacée par l'athlète : d'où elle vient. */
  movedTitle(w: Workout): string {
    if (!w.originalDate) return 'Déplacée par l\'athlète';
    const fmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
    return `Déplacée par l'athlète — initialement le ${fmt.format(new Date(w.originalDate + 'T00:00:00'))}`;
  }

  ngOnInit(): void {
    this.openOnDayIfNarrow();
    // Onglet « Programme » d'un athlète : le calendrier est cadré sur lui, le sélecteur
    // d'athlète disparaît (c'est le contexte de la coquille qui dit de qui il s'agit).
    if (this.athleteId()) {
      this.selectedAthleteId = this.athleteId()!;
      this.load();
      this.loadOverlays();
    }
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
    this.strengthService.listAllSessions().subscribe((s) => this.librarySessions.set(s));
    this.templateService.listAll().subscribe((t) => this.courseTemplates.set(t));
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
    // Allure d'endurance : sans elle, une séance écrite en durée sans allure prescrite n'a
    // aucun volume affichable, et le total de la semaine la compte pour zéro.
    this.paceReference.forAthlete(this.selectedAthleteId).subscribe({
      next: (pace) => this.referencePace.set(pace),
      error: () => this.referencePace.set(null),
    });
  }

  /**
   * Au premier affichage sur un écran étroit, on ouvre sur la journée.
   *
   * <p>Ce n'est pas une préférence mémorisée : c'est le point de départ raisonnable là où la
   * semaine s'empile en sept blocs. Le coach garde la main — changer de mode reste un geste, et
   * le choix tient tant qu'il ne recharge pas.</p>
   */
  private openOnDayIfNarrow(): void {
    if (typeof window !== 'undefined' && window.innerWidth <= CalendarComponent.NARROW_PX) {
      this.mode.set('day');
    }
  }

  /** Seuil du basculement : celui où la grille passe déjà à une colonne (cf. la feuille de style). */
  private static readonly NARROW_PX = 768;

  setMode(mode: CalMode): void {
    this.mode.set(mode);
    CalendarComponent.rememberMode(mode);
    this.load();
  }

  /**
   * Dernière vue choisie, ou « semaine » à défaut.
   *
   * <p>Lue avant toute injection : le signal est initialisé à la construction du composant. Le
   * mode « groupe » impose sa propre vue plus loin — mémoriser reste sans effet là-bas.</p>
   */
  private static savedMode(): CalMode {
    try {
      const saved = localStorage.getItem(CalendarComponent.MODE_KEY);
      return saved === 'day' || saved === '4weeks' || saved === 'month'
        ? saved
        : '4weeks';
    } catch {
      return '4weeks';         // navigation privée, stockage refusé : la vue par défaut suffit
    }
  }

  private static rememberMode(mode: CalMode): void {
    try { localStorage.setItem(CalendarComponent.MODE_KEY, mode); } catch { /* stockage refusé */ }
  }
  /**
   * Changement d'athlète : la sélection et la pile d'annulation portent sur ce qui est affiché.
   * Annuler chez quelqu'un qu'on ne regarde plus produirait un effet invisible.
   */
  onAthleteChange(id?: string): void {
    if (id) this.selectedAthleteId = id;
    this.clearSelection();
    this.undoStack.reset();
    this.load();
    this.loadOverlays();
  }
  shift(step: number): void {
    this.anchor.set(shiftAnchor(this.mode(), this.anchor(), step));
    this.load();
  }
  goToday(): void { this.anchor.set(new Date()); this.load(); }

  load(): void {
    if (this.scopeMode() === 'group') { this.loadGroup(); return; }
    if (!this.selectedAthleteId) return;
    const cells = this.cells();
    const from = cells[0].date;
    const to = cells[cells.length - 1].date;
    this.loading.set(true);
    this.workoutService.calendar(this.selectedAthleteId, from, to).subscribe({
      next: (list) => {
        this.workouts.set(list);
        this.loading.set(false);
        // Une sélection qui survit à la disparition de sa cible ferait agir Suppr sur un fantôme.
        this.selection.prune(this.chipOrder());
      },
      error: () => this.loading.set(false),
    });
    this.noteService.list(this.selectedAthleteId, from, to).subscribe({
      next: (n) => this.notes.set(n), error: () => this.notes.set([]),
    });
    this.loadWeekOutlook(from);
    this.reloadStrength();
  }

  /**
   * La phrase de la semaine, sur la <b>première semaine affichée</b>.
   *
   * <p>Elle était réservée à la vue semaine, où « +34 % » ne pouvait désigner qu'elle. En quatre
   * semaines, la fenêtre commence toujours un lundi : la phrase porte sur cette semaine-là, et
   * l'en-tête le dit. Sur un mois, la fenêtre commence avant le 1er et déborde après : aucune
   * semaine n'y est « celle qu'on construit », donc pas de phrase — un chiffre qui ne désigne
   * rien vaut moins que pas de chiffre.</p>
   */
  private loadWeekOutlook(from: string): void {
    if (this.mode() !== '4weeks' || !this.selectedAthleteId) {
      this.weekOutlook.set(null);
      return;
    }
    this.decisions.weekOutlook(this.selectedAthleteId, from).subscribe({
      next: (o) => this.weekOutlook.set(o),
      error: () => this.weekOutlook.set(null),
    });
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
  /**
   * Semaine de départ du mésocycle, désignée par le menu de la colonne de totaux.
   *
   * <p>Le panneau partait auparavant de « la semaine affichée » — une notion qui n'existe plus
   * depuis qu'on regarde quatre semaines à la fois, et qui était déjà implicite. On désigne
   * maintenant la semaine, et l'en-tête du panneau la nomme.</p>
   */
  readonly mesoSource = signal<Date | null>(null);
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

  /** Ouvre le panneau mésocycle sur une semaine précise (menu de la colonne de totaux). */
  ctxGenerateMeso(): void {
    const start = this.weekMenu()?.start;
    this.closeWeekMenu();
    if (!start) return;
    this.mesoSource.set(new Date(start + 'T00:00:00'));
    this.showMeso.set(false);
    this.toggleMeso();
  }

  /** Libellé de la semaine de départ, affiché en tête du panneau. */
  mesoSourceLabel(): string {
    return this.fmtDate(toIso(mondayOf(this.mesoSource() ?? this.anchor())));
  }

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
      this.toast.warning('Donne un nom au modèle.');
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
    if (this.mesoBusy()) return;
    const sourceStart = mondayOf(this.mesoSource() ?? this.anchor());
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
      if (!this.mesoGroupId) { this.toast.warning('Choisis un groupe.'); return; }
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

  /**
   * Duplique la semaine affichée vers la suivante. Passe par le même chemin que le menu
   * contextuel de la colonne de totaux : un seul geste, une seule implémentation, et donc une
   * seule façon de l'annuler.
   */
  async duplicateWeek(): Promise<void> {
    if (!this.selectedAthleteId || !this.requireWrite()) return;
    this.actionsMenuOpen.set(false);
    const source = toIso(mondayOf(this.anchor()));
    await this.duplicateWeekTo(source, this.shiftDate(source, 7));
  }

  /**
   * Jours pour lesquels le sélecteur de séance est ouvert (vide = fermé). Une liste et non une
   * date unique : le même sélecteur sert au « + » d'un jour et à une plage tracée à la souris,
   * et ces deux parcours ne doivent pas diverger.
   */
  readonly pickerDates = signal<string[]>([]);
  readonly pickerDate = computed(() => this.pickerDates()[0] ?? null);
  /** Titre du sélecteur : une date lisible, ou le nombre de jours visés. */
  readonly pickerLabel = computed(() => {
    const d = this.pickerDates();
    return d.length > 1 ? `${d.length} jours` : (d[0] ? this.fmtDate(d[0]) : '');
  });

  /**
   * Panneau bibliothèque (colonne de gauche) — le geste central de la planification desktop.
   * Ouvert par défaut sur desktop large (poste de planification), replié sur petit écran.
   * Préférence mémorisée entre sessions (comme la nav latérale).
   */
  private static readonly LIB_KEY = 'coach-cal-lib-open';
  private static readonly MODE_KEY = 'coach-cal-mode';
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
    this.pickerDates.set([date]);
  }

  closePicker(): void {
    this.pickerDates.set([]);
    this.noteOpen.set(false);
    this.noteText = '';
    this.noteEnd = '';
  }

  /** Saisie de note inline dans le picker (remplace l'ancien window.prompt). */
  readonly noteOpen = signal(false);
  noteText = '';
  /** Une note d'un jour reste privée sauf décision explicite : c'est le contrat existant. */
  noteShared = false;
  /** Fin de période : renseignée, la note devient un cycle affiché en bandeau. */
  noteEnd = '';
  toggleNote(): void {
    this.noteOpen.update((v) => !v);
    if (!this.noteOpen()) { this.noteText = ''; this.noteEnd = ''; this.noteShared = false; }
  }

  /** Drop d'un éducatif : crée une courte séance technique avec la gamme attachée à l'échauffement. */
  private dropDrill(drill: RunDrill, date: string, athleteId = this.selectedAthleteId, refresh = () => this.load()): void {
    this.workoutService.create(athleteId, {
      scheduledDate: date, type: 'ENDURANCE', title: 'Technique — ' + drill.name, notes: null, steps: [],
    }).subscribe({
      next: (w) => {
        this.workoutService.updateStructure(athleteId, w.id, {
          warmup: [{ id: 'wu-' + Math.random().toString(36).slice(2, 8), type: 'warmup', drillIds: [drill.id] }],
          main: [], cooldown: [],
        }).subscribe({
          next: () => { this.toast.success(`${drill.name} planifié le ${this.fmtDate(date)}`); refresh(); },
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
    // Une fin renseignée fait de la note un cycle : même donnée, autre portée. C'est le seul
    // moyen d'écrire « bloc spécifique » sur cinq semaines sans le répéter cinq fois.
    const endDate = this.noteEnd || null;
    if (endDate && endDate < date) { this.toast.error('La fin du cycle précède son début.'); return; }
    this.noteService.create(this.selectedAthleteId,
      { noteDate: date, endDate, text, shared: this.noteShared }).subscribe({
      next: () => {
        this.closePicker();
        this.toast.success(endDate ? 'Cycle ajouté' : 'Note ajoutée');
        this.load();
      },
      error: () => this.toast.error('Ajout impossible.'),
    });
  }

  // --- Note du calendrier : ouverture, édition, suppression explicite ---------
  // Le clic sur une chip ouvrait la note… en la supprimant directement. Destructif
  // sans confirmation, et aucune édition possible.

  readonly notePanelOpen = signal(false);
  readonly activeNote = signal<CalendarNote | null>(null);
  noteEditText = '';
  /** Fin de période de la note ouverte ; vide = note d'un seul jour. */
  noteEditEnd = '';

  openNote(n: CalendarNote, ev: Event): void {
    ev.stopPropagation();
    this.activeNote.set(n);
    this.noteEditText = n.text;
    this.noteEditEnd = n.endDate ?? '';
    this.notePanelOpen.set(true);
  }

  saveNote(n: CalendarNote): void {
    const text = this.noteEditText.trim();
    const endDate = this.noteEditEnd || null;
    if (!text) { this.notePanelOpen.set(false); return; }
    if (text === n.text && endDate === (n.endDate ?? null)) { this.notePanelOpen.set(false); return; }
    if (endDate && endDate < n.noteDate) { this.toast.error('La fin du cycle précède son début.'); return; }
    this.noteService.update(this.selectedAthleteId, n.id, { noteDate: n.noteDate, endDate, text }).subscribe({
      next: () => { this.notePanelOpen.set(false); this.toast.success('Note enregistrée'); this.load(); },
      error: () => this.toast.error('Enregistrement impossible.'),
    });
  }

  async deleteNote(n: CalendarNote): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer la note ?', message: n.text, confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.noteService.delete(this.selectedAthleteId, n.id).subscribe({
      next: () => { this.notePanelOpen.set(false); this.toast.info('Note supprimée.'); this.load(); },
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
  // --- Planification (partagée entre le glisser-déposer, le picker « + » et le mode groupe) ---
  // Un seul chemin par famille de séance : le picker et le panneau latéral ne doivent pas
  // diverger, c'était toute la confusion de l'ancienne modale.

  /**
   * Planifie un modèle de séance course chez un athlète à une date.
   * `refresh` dit quelle vue recharger : la grille d'un athlète ou celle du groupe.
   */
  private scheduleTemplate(t: WorkoutTemplate, athleteId: string, date: string, refresh = () => this.load()): void {
    this.courseService.schedule(athleteId, t.id, { date }).subscribe({
      next: (w) => {
        // Renommer est proposé, jamais imposé. Une modale à chaque dépôt aurait ralenti le
        // geste qui fait tout l'intérêt du glisser-déposer — on planifie une semaine entière
        // d'affilée — alors que le besoin de renommer, lui, est occasionnel : « 10 × 400 »
        // devient « 10 × 400 spécifique semi » pour cet athlète-là, pas pour les six autres.
        this.toast.withAction(
          `${t.name} planifiée le ${this.fmtDate(date)}${this.chargeRecap(w)}`,
          'Renommer',
          () => this.promptRename(athleteId, w, refresh),
        );
        refresh();
      },
      error: () => this.toast.error('Planification impossible.'),
    });
  }

  /**
   * Renomme une séance planifiée, depuis l'invite de la modale globale.
   *
   * <p>Le titre courant est proposé et présélectionné : le remplacer prend une frappe, l'amender
   * prend une flèche. Renoncer ne touche à rien — la séance reste planifiée, c'est seulement son
   * libellé qui ne change pas.</p>
   */
  async promptRename(athleteId: string, w: Workout, refresh = () => this.load()): Promise<void> {
    const title = await this.confirm.prompt({
      title: 'Renommer la séance',
      message: `Séance du ${this.fmtDate(w.scheduledDate)}.`,
      promptLabel: 'Nom de la séance',
      initialValue: w.title,
      confirmLabel: 'Renommer',
    });
    if (title === null || title === w.title) {
      return;
    }
    this.workoutService.rename(athleteId, w.id, title).subscribe({
      next: () => { this.toast.success(`Séance renommée « ${title} ».`); refresh(); },
      error: () => this.toast.error('Renommage impossible.'),
    });
  }

  /** Planifie une séance de force chez un athlète à une date. */
  private scheduleStrength(s: StrengthSession, athleteId: string, date: string, refresh = () => this.reloadStrength()): void {
    this.strengthService.scheduleSession(athleteId, s.id, { date, fieldsPreset: 'AVANCE' }).subscribe({
      next: (scheduled) => {
        const charges = scheduled.chargeSummary ? ` — ${scheduled.chargeSummary}` : '';
        this.toast.success(`${s.name} planifiée le ${this.fmtDate(date)}${charges}`);
        refresh();
      },
      error: () => this.toast.error('Planification impossible.'),
    });
  }

  // --- Sélection depuis le picker (« + » d'un jour, ou plage tracée à la souris) ---
  // Le picker peut viser plusieurs jours : on planifie sur chacun, et le lot entier n'occupe
  // qu'une entrée dans la pile d'annulation — un geste, une annulation.

  scheduleTemplateOn(t: WorkoutTemplate): void {
    this.scheduleOnPickerDates(
      (date) => this.courseService.schedule(this.selectedAthleteId, t.id, { date }),
      'course', t.name);
  }

  scheduleStrengthOn(s: StrengthSession): void {
    this.scheduleOnPickerDates(
      (date) => this.strengthService.scheduleSession(this.selectedAthleteId, s.id, { date, fieldsPreset: 'AVANCE' }),
      'strength', s.name);
  }

  scheduleDrillOn(d: RunDrill): void {
    const dates = this.pickerDates();
    this.closePicker();
    for (const date of dates) this.dropDrill(d, date);
  }

  private scheduleOnPickerDates(
    schedule: (date: string) => Observable<{ id: string }>,
    kind: 'course' | 'strength',
    name: string,
  ): void {
    const dates = this.pickerDates();
    if (!dates.length) return;
    this.closePicker();
    this.dayRange.set([]);

    const created: { kind: 'course' | 'strength'; id: string }[] = [];
    const run = () => this.all(dates.map((d) => schedule(d).pipe(tap((c) => created.push({ kind, id: c.id })))));
    run().subscribe({
      next: () => {
        this.refreshAll();
        this.commit({
          label: 'la planification',
          undo: () => this.all(created.map((c) => this.removeCreated(c))),
          redo: () => defer(() => { created.length = 0; return run(); }),
        }, dates.length > 1
          ? `${name} planifiée sur ${dates.length} jours`
          : `${name} planifiée le ${this.fmtDate(dates[0])}`);
      },
      error: () => { this.refreshAll(); this.toast.error('Planification impossible.'); },
    });
  }
  openWorkout(w: Workout): void {
    if (this.consumeSuppressedClick()) return;
    // Vue séance (lecture) ; l'édition est une action délibérée depuis la page.
    this.router.navigate(['/app/athletes', w.athleteId, 'workouts', w.id]);
  }
  openObjectives(): void { this.router.navigate(['/app/athletes', this.selectedAthleteId, 'races']); }
  openTests(): void { this.router.navigate(['/app/athletes', this.selectedAthleteId, 'tests']); }

  // --- Alt + glisser = copier --------------------------------------------------------------
  // Le geste est un standard universel, mais invisible : sans retour pendant le drag, le coach
  // ne sait pas s'il s'apprête à déplacer ou à dupliquer. La classe est posée sur <body> parce
  // que la vignette de drag du CDK y est montée, hors de l'arbre du calendrier.
  private static readonly COPY_DRAG_CLASS = 'is-copy-drag';
  private dragging = false;

  onDragStarted(ev: { event: MouseEvent | TouchEvent }): void {
    this.dragging = true;
    this.setCopyCursor(this.isCopyModifier(ev.event));
  }

  onDragEnded(): void {
    this.dragging = false;
    this.setCopyCursor(false);
  }

  /** Alt (ou ⌘ sur mac) enfoncé/relâché **pendant** le glisser : le coach change d'avis en route. */
  @HostListener('document:keydown', ['$event'])
  @HostListener('document:keyup', ['$event'])
  protected onModifierChange(ev: KeyboardEvent): void {
    if (this.dragging) this.setCopyCursor(ev.altKey || ev.metaKey);
  }

  private isCopyModifier(ev: MouseEvent | TouchEvent): boolean {
    return !!ev && 'altKey' in ev && (ev.altKey || (ev as MouseEvent).metaKey);
  }

  private setCopyCursor(on: boolean): void {
    document.body.classList.toggle(CalendarComponent.COPY_DRAG_CLASS, on);
  }

  onDrop(event: CdkDragDrop<DayCell>, targetDate: string): void {
    const data = event.item.data as Workout | StrengthSession | ScheduledStrength | WorkoutTemplate;
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

    // Séance de force DÉJÀ planifiée glissée d'un jour à l'autre → déplacement.
    // (discriminée par `sourceSessionId`, absent des séances course et des modèles).
    if ('sourceSessionId' in rec && 'scheduledDate' in rec) {
      this.moveStrength(data as ScheduledStrength, targetDate);
      return;
    }

    // Séance de force glissée depuis la bibliothèque → planification.
    if ('structure' in rec) {
      this.scheduleStrength(data as StrengthSession, this.selectedAthleteId, targetDate);
      return;
    }

    // Modèle de séance course glissé depuis la bibliothèque → planification.
    if (!('scheduledDate' in rec)) {
      this.scheduleTemplate(data as WorkoutTemplate, this.selectedAthleteId, targetDate);
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

  /**
   * Les journées qui portent un ordre voulu.
   *
   * <p>Convention partagée avec le serveur : une journée est ordonnée dès qu'une de ses séances
   * porte un {@code orderIndex} non nul. Toutes à zéro = le coach n'a rien demandé, et on
   * n'affiche alors aucun numéro — un ordre inventé se lirait comme une consigne.</p>
   */
  readonly orderedDates = computed(() => {
    const dates = new Set<string>();
    for (const w of this.workouts()) {
      if ((w.orderIndex ?? 0) > 0) dates.add(w.scheduledDate);
    }
    return dates;
  });

  /** Rang de la séance dans sa journée (1, 2, 3…), ou `null` si la journée n'est pas ordonnée. */
  orderRank(w: Workout): number | null {
    return this.orderedDates().has(w.scheduledDate) ? (w.orderIndex ?? 0) + 1 : null;
  }

  /** Nombre de séances course posées ce jour-là — l'ordre ne se propose qu'à partir de deux. */
  dayWorkoutCount(date: string): number {
    return this.workouts().filter((w) => w.scheduledDate === date).length;
  }

  /**
   * Numérote les séances de la journée dans l'ordre où elles sont déjà affichées.
   *
   * <p>Le glisser-déposer intra-jour donnait déjà un ordre, mais seulement à qui savait qu'il
   * existait — et il ne se voyait nulle part une fois relâché. Ce geste-ci nomme la chose et la
   * rend atteignable sans glisser.</p>
   */
  ctxOrderDay(): void {
    const date = this.dayMenu()?.date;
    this.closeDayMenu();
    if (!date) return;
    const ids = this.workouts()
      .filter((w) => w.scheduledDate === date)
      .sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0))
      .map((w) => w.id);
    if (ids.length < 2) return;
    this.workouts.update((l) =>
      l.map((w) => (w.scheduledDate === date ? { ...w, orderIndex: ids.indexOf(w.id) } : w)));
    this.workoutService.reorder(this.selectedAthleteId, date, ids).subscribe({
      next: () => this.toast.success('Ordre défini — ton athlète le verra'),
      error: () => { this.toast.error('Ordre impossible à définir.'); this.load(); },
    });
  }

  /** Retire l'ordre : les séances du jour redeviennent à faire dans n'importe quel ordre. */
  ctxClearDayOrder(): void {
    const date = this.dayMenu()?.date;
    this.closeDayMenu();
    if (!date) return;
    this.workouts.update((l) =>
      l.map((w) => (w.scheduledDate === date ? { ...w, orderIndex: 0 } : w)));
    this.workoutService.clearOrder(this.selectedAthleteId, date).subscribe({
      next: () => this.toast.info('Ordre retiré'),
      error: () => { this.toast.error('Retrait impossible.'); this.load(); },
    });
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

  // --- Actions réversibles ------------------------------------------------------------------
  // Toute mutation du calendrier passe par `commit()` : elle entre dans la pile d'annulation ET
  // propose « Annuler » dans son toast. Les deux chemins exécutent le même code, sinon le toast
  // et Cmd+Z finiraient par se contredire.

  /** Enregistre une action déjà effectuée et propose son annulation immédiate. */
  private commit(command: UndoCommand, message: string): void {
    this.undoStack.push(command);
    this.toast.withAction(message, 'Annuler', () => this.undoLast());
  }

  undoLast(): void {
    this.undoStack.undo().subscribe({
      next: (label) => { if (label) { this.refreshAll(); this.toast.info(`Annulé : ${label}.`); } },
      error: () => { this.refreshAll(); this.toast.error('Annulation impossible.'); },
    });
  }

  redoLast(): void {
    this.undoStack.redo().subscribe({
      next: (label) => { if (label) { this.refreshAll(); this.toast.info(`Rétabli : ${label}.`); } },
      error: () => { this.refreshAll(); this.toast.error('Rétablissement impossible.'); },
    });
  }

  /** Recharge tout ce qui peut avoir bougé (séances course + force). */
  private refreshAll(): void {
    this.load();
    this.reloadStrength();
  }

  /** Exécute des opérations en parallèle ; échoue si l'une échoue (l'annulation reste en pile). */
  private all(ops: Observable<unknown>[]): Observable<unknown> {
    return ops.length ? forkJoin(ops) : of(null);
  }

  /** Replanifie sans toast ni pile : brique de base des commandes d'annulation. */
  private rescheduleTo(workoutId: string, date: string): Observable<unknown> {
    return this.workoutService.reschedule(this.selectedAthleteId, workoutId, date);
  }

  /** Déplace une séance vers une date (optimiste + rollback en cas d'échec back). */
  private moveWorkout(w: Workout, targetDate: string): void {
    if (w.scheduledDate === targetDate) return;
    const previous = w.scheduledDate;
    this.workouts.update((l) => l.map((x) => (x.id === w.id ? { ...x, scheduledDate: targetDate } : x)));
    this.workoutService.reschedule(this.selectedAthleteId, w.id, targetDate).subscribe({
      next: () => this.commit({
        label: 'le déplacement',
        undo: () => this.rescheduleTo(w.id, previous),
        redo: () => this.rescheduleTo(w.id, targetDate),
      }, `Séance déplacée au ${this.fmtDate(targetDate)}`),
      error: () => {
        this.workouts.update((l) => l.map((x) => (x.id === w.id ? { ...x, scheduledDate: previous } : x)));
        this.toast.error('Déplacement impossible.');
      },
    });
  }

  /**
   * Duplique une séance vers une date (copie figée côté back), avec annulation.
   * La copie reçoit un nouvel identifiant à chaque rejeu : la commande referme sur une boîte
   * mutable, sinon le second undo viserait une séance qui n'existe plus.
   */
  private copyWorkout(w: Workout, targetDate: string): void {
    this.workoutService.copy(this.selectedAthleteId, w.id, targetDate).subscribe({
      next: (copy) => {
        this.load();
        const ref = { id: copy.id };
        this.commit({
          label: 'la duplication',
          undo: () => this.workoutService.delete(this.selectedAthleteId, ref.id),
          redo: () => this.workoutService.copy(this.selectedAthleteId, w.id, targetDate)
            .pipe(tap((c) => { ref.id = c.id; })),
        }, `« ${w.title} » dupliquée le ${this.fmtDate(targetDate)}`);
      },
      error: () => this.toast.error('Duplication impossible.'),
    });
  }

  // --- Opérations de semaine (menu contextuel de la colonne de totaux) -----------------------
  // La colonne de totaux est le seul endroit qui parle « semaine » : c'est donc là que vivent
  // les gestes qui portent sur une semaine entière, plutôt que dans une barre d'outils déjà pleine.

  readonly weekMenu = signal<{ start: string; sessions: number; x: number; y: number } | null>(null);
  weekMenuTarget = '';

  openWeekMenu(week: WeekRow, ev: MouseEvent): void {
    ev.preventDefault();
    if (!this.canWriteSelected() || this.scopeMode() !== 'athlete') return;
    const start = week.days[0].date;
    const { x, y } = this.clampToViewport(ev.clientX, ev.clientY);
    this.weekMenuTarget = this.shiftDate(start, 7);
    this.weekMenu.set({ start, sessions: week.sessions, x, y });
  }
  closeWeekMenu(): void { this.weekMenu.set(null); }

  // --- Menu contextuel d'un jour -------------------------------------------
  /**
   * Le collage n'existait qu'au clavier ({@link keys}('mod','V')) et sur le jour <b>survolé</b>.
   * Copier une séance était donc découvrable — le menu d'une chip le propose — mais la coller ne
   * l'était pas : le geste s'arrêtait au milieu, et un coach concluait raisonnablement que le
   * copier-coller n'existait pas. Le jour porte maintenant sa moitié du geste.
   */
  readonly dayMenu = signal<{ date: string; x: number; y: number } | null>(null);

  openDayMenu(date: string, ev: MouseEvent): void {
    ev.preventDefault();
    if (!this.canWriteSelected() || this.scopeMode() !== 'athlete') return;
    // Un clic droit sur une chip ouvre le menu de la chip : le sien ne doit pas s'y superposer.
    if ((ev.target as HTMLElement).closest('[data-chip]')) return;
    const { x, y } = this.clampToViewport(ev.clientX, ev.clientY);
    this.hoveredDate.set(date);
    this.dayMenu.set({ date, x, y });
  }

  closeDayMenu(): void { this.dayMenu.set(null); }

  /** Ce que le presse-papier collerait ici, pour le libellé du menu. */
  clipboardLabel(): string { return this.clipboard().label; }

  ctxPasteHere(): void {
    const m = this.dayMenu();
    this.closeDayMenu();
    if (m) this.pasteOn(m.date);
  }

  ctxPlanHere(): void {
    const m = this.dayMenu();
    this.closeDayMenu();
    if (m) this.addWorkout(m.date);
  }

  /** Ouvre le sélecteur directement sur la saisie de note. */
  ctxNoteHere(): void {
    const m = this.dayMenu();
    this.closeDayMenu();
    if (!m) return;
    this.addWorkout(m.date);
    if (this.pickerDate()) this.noteOpen.set(true);
  }

  /** Toutes les chips d'une semaine (7 jours à partir du lundi donné), course et force. */
  private weekChips(start: string): ChipRef[] {
    const end = this.shiftDate(start, 6);
    const inWeek = (d: string) => d >= start && d <= end;
    return [
      ...this.workouts().filter((w) => inWeek(w.scheduledDate)).map((w) => ({ kind: 'course' as const, id: w.id })),
      ...this.strength().filter((x) => inWeek(x.scheduledDate)).map((x) => ({ kind: 'strength' as const, id: x.id })),
    ];
  }

  /**
   * Duplique une semaine vers une autre. Implémenté séance par séance plutôt que par l'appel
   * groupé du serveur : c'est le seul moyen de connaître les identifiants créés, donc de rendre
   * le geste annulable — sur une semaine, c'est une poignée de requêtes.
   */
  async ctxDuplicateWeek(targetStart: string): Promise<void> {
    const m = this.weekMenu(); if (!m || !targetStart) return;
    this.closeWeekMenu();
    await this.duplicateWeekTo(m.start, targetStart);
  }

  private async duplicateWeekTo(sourceStart: string, targetStart: string): Promise<void> {
    const refs = this.weekChips(sourceStart);
    if (!refs.length) { this.toast.info('Aucune séance à copier cette semaine.'); return; }

    const target = mondayOf(new Date(targetStart + 'T00:00:00'));
    const targetIso = toIso(target);
    const ok = await this.confirm.ask({
      title: 'Dupliquer la semaine',
      message: `Copier ${refs.length} séance(s) vers la semaine du ${this.fmtDate(targetIso)} ? Les séances déjà présentes sont conservées.`,
      confirmLabel: 'Dupliquer',
    });
    if (!ok) return;

    // Origine calée sur le lundi source : une semaine qui commence le mardi doit arriver le
    // mardi de la semaine cible, pas se recoller sur son lundi.
    this.pasteRefs(refs, targetIso, sourceStart, 'la duplication de semaine',
      (n) => `${n} séance(s) copiée(s) sur la semaine du ${this.fmtDate(targetIso)}`);
    this.anchor.set(target); // on montre le résultat, sinon la copie est invisible
  }

  /** Vide la semaine de ses séances course (les indispos et objectifs ne sont pas des séances). */
  async ctxClearWeek(): Promise<void> {
    const m = this.weekMenu(); if (!m) return;
    this.closeWeekMenu();
    const refs = this.weekChips(m.start);
    if (!refs.length) { this.toast.info('Cette semaine est déjà vide.'); return; }
    // On passe par la sélection : le lot, la confirmation et l'annulation sont déjà écrits.
    this.selection.setAll(refs);
    await this.deleteSelection();
  }

  /** Décale toute la semaine de ±1 jour (une compétition avancée, un déplacement, une météo). */
  ctxShiftWeek(days: number): void {
    const m = this.weekMenu(); if (!m) return;
    this.closeWeekMenu();
    if (!this.requireWrite()) return;
    const courseIds = new Set(this.weekChips(m.start).filter((r) => r.kind === 'course').map((r) => r.id));
    const strengthIds = new Set(this.weekChips(m.start).filter((r) => r.kind === 'strength').map((r) => r.id));
    if (!courseIds.size && !strengthIds.size) { this.toast.info('Aucune séance à décaler.'); return; }

    // Les dates de départ sont figées ici : rejouer le décalage doit repartir du même état,
    // pas de celui qu'on vient de produire.
    const starts = new Map<string, string>();
    for (const w of this.workouts()) if (courseIds.has(w.id)) starts.set(w.id, w.scheduledDate);
    for (const x of this.strength()) if (strengthIds.has(x.id)) starts.set(x.id, x.scheduledDate);

    const move = (delta: number) => this.all([
      ...[...courseIds].map((id) => this.rescheduleTo(id, this.shiftDate(starts.get(id)!, delta))),
      ...[...strengthIds].map((id) => this.strengthService
        .rescheduleScheduled(this.selectedAthleteId, id, this.shiftDate(starts.get(id)!, delta))),
    ]);

    move(days).subscribe({
      next: () => {
        this.refreshAll();
        this.commit({
          label: 'le décalage de semaine',
          undo: () => move(0),
          redo: () => move(days),
        }, `Semaine décalée de ${days > 0 ? '+' : ''}${days} jour${Math.abs(days) > 1 ? 's' : ''}`);
      },
      error: () => { this.refreshAll(); this.toast.error('Décalage impossible.'); },
    });
  }

  // --- Rectangle de sélection ----------------------------------------------------------------
  // Tracé sur le fond de la grille : il attrape les chips qu'il croise. S'il n'en attrape aucune,
  // il désigne une PLAGE DE JOURS — le geste naturel pour « pose-moi trois footings ici ».

  readonly marquee = signal<{ x: number; y: number; w: number; h: number } | null>(null);
  private marqueeOrigin: { x: number; y: number } | null = null;
  /** Rectangles relevés au début du tracé : ils ne bougent pas pendant le geste. */
  private marqueeTargets: { ref: ChipRef | null; date: string | null; box: DOMRect }[] = [];
  private marqueeCleanup: (() => void) | null = null;

  /**
   * Démarre un rectangle. Ignoré si le geste part d'une chip : là, c'est un glisser-déposer.
   * Les écouteurs sont posés à la demande plutôt qu'en permanence — le calendrier est l'écran
   * le plus lourd de l'app, il ne peut pas se permettre un cycle de détection par mouvement
   * de souris quand personne ne trace rien.
   */
  onGridPointerDown(ev: PointerEvent): void {
    if (ev.button !== 0 || ev.pointerType !== 'mouse') return;
    const el = ev.target as HTMLElement;
    if (el.closest('.workout-card, .strength-chip, .event-chip, .activity-card, button, a, input, select, textarea')) return;

    this.marqueeOrigin = { x: ev.clientX, y: ev.clientY };
    this.marquee.set({ x: ev.clientX, y: ev.clientY, w: 0, h: 0 });
    this.marqueeTargets = this.snapshotTargets();
    if (!ev.shiftKey && !ev.metaKey && !ev.ctrlKey) this.clearSelection();

    const move = (e: PointerEvent) => this.onMarqueeMove(e);
    const up = () => this.endMarquee();
    document.addEventListener('pointermove', move);
    document.addEventListener('pointerup', up);
    this.marqueeCleanup = () => {
      document.removeEventListener('pointermove', move);
      document.removeEventListener('pointerup', up);
      this.marqueeCleanup = null;
    };
  }

  /** Position à l'écran des chips et des jours, relevée une fois par tracé. */
  private snapshotTargets(): { ref: ChipRef | null; date: string | null; box: DOMRect }[] {
    const out: { ref: ChipRef | null; date: string | null; box: DOMRect }[] = [];
    for (const el of Array.from(document.querySelectorAll<HTMLElement>('[data-chip]'))) {
      const [kind, id] = (el.dataset['chip'] ?? '').split(':');
      if (kind && id) out.push({ ref: { kind: kind as 'course' | 'strength', id }, date: null, box: el.getBoundingClientRect() });
    }
    for (const el of Array.from(document.querySelectorAll<HTMLElement>('[data-day]'))) {
      const date = el.dataset['day'];
      if (date) out.push({ ref: null, date, box: el.getBoundingClientRect() });
    }
    return out;
  }

  private onMarqueeMove(ev: PointerEvent): void {
    const origin = this.marqueeOrigin;
    if (!origin) return;
    const rect = {
      x: Math.min(origin.x, ev.clientX), y: Math.min(origin.y, ev.clientY),
      w: Math.abs(ev.clientX - origin.x), h: Math.abs(ev.clientY - origin.y),
    };
    this.marquee.set(rect);
    // En dessous de quelques pixels, c'est un clic, pas un tracé : on n'écrase pas la sélection.
    if (rect.w + rect.h > 12) this.applyMarquee(rect);
  }

  private endMarquee(): void {
    this.marqueeCleanup?.();
    this.marqueeOrigin = null;
    this.marqueeTargets = [];
    this.marquee.set(null);
  }

  /** Traduit le rectangle écran en sélection de chips, ou à défaut en plage de jours. */
  private applyMarquee(rect: { x: number; y: number; w: number; h: number }): void {
    const hits: ChipRef[] = [];
    const days: string[] = [];
    for (const t of this.marqueeTargets) {
      const b = t.box;
      const overlaps = b.right >= rect.x && b.left <= rect.x + rect.w
        && b.bottom >= rect.y && b.top <= rect.y + rect.h;
      if (!overlaps) continue;
      if (t.ref) hits.push(t.ref);
      else if (t.date) days.push(t.date);
    }
    this.selection.setAll(hits);
    // La plage de jours n'a de sens que si le tracé n'a rien attrapé : sinon c'est le lot de
    // chips qui est la cible, et proposer « planifier sur 3 jours » serait un contresens.
    this.dayRange.set(hits.length === 0 && days.length > 1 ? days.sort() : []);
  }

  /** Planifie sur toute la plage retenue : le picker s'ouvre sur N jours au lieu d'un. */
  planRange(): void {
    const days = this.dayRange();
    if (!days.length || !this.requireWrite()) return;
    this.pickerDates.set(days);
  }

  /** Vide les jours de la plage (séances course). */
  async clearRange(): Promise<void> {
    const days = new Set(this.dayRange());
    if (!days.size) return;
    const victims: ChipRef[] = [
      ...this.workouts().filter((w) => days.has(w.scheduledDate)).map((w) => ({ kind: 'course' as const, id: w.id })),
      ...this.strength().filter((x) => days.has(x.scheduledDate)).map((x) => ({ kind: 'strength' as const, id: x.id })),
    ];
    if (!victims.length) { this.toast.info('Ces jours sont déjà vides.'); return; }
    this.selection.setAll(victims);
    this.dayRange.set([]);
    await this.deleteSelection();
  }

  // --- Actions groupées : presse-papier, duplication, suppression ----------------------------
  // Le presse-papier garde des RÉFÉRENCES, pas des copies d'objets : coller demande au serveur
  // de dupliquer la séance d'origine, seule façon de reprendre sa prescription figée à l'identique.

  /** Séances visées par une action : la sélection si elle existe, sinon rien. */
  private selectedWorkouts(): Workout[] {
    const ids = new Set(this.selection.ids('course'));
    return this.workouts().filter((w) => ids.has(w.id));
  }
  private selectedStrength(): ScheduledStrength[] {
    const ids = new Set(this.selection.ids('strength'));
    return this.strength().filter((x) => ids.has(x.id));
  }

  copySelection(): void {
    const refs = this.selected().map((p) => ({ kind: p.kind, id: p.id }));
    if (!refs.length) { this.toast.info('Rien à copier — sélectionne d’abord une séance.'); return; }
    const label = refs.length === 1
      ? (this.selectedWorkouts()[0]?.title ?? this.selectedStrength()[0]?.title ?? '1 séance')
      : `${refs.length} séances`;
    this.clipboard.set({ refs, label });
    this.toast.info(`${label} copiée${refs.length > 1 ? 's' : ''} — ${this.keys('mod', 'V')} pour coller sur un jour.`);
  }

  /**
   * Colle le presse-papier sur une date, ou sur le jour survolé.
   */
  pasteOn(date: string | null): void {
    const clip = this.clipboard();
    const target = date ?? this.hoveredDate();
    if (!clip.refs.length) { this.toast.info(`Presse-papier vide — ${this.keys('mod', 'C')} pour copier.`); return; }
    if (!target) { this.toast.info('Survole un jour pour coller.'); return; }
    this.pasteRefs(clip.refs, target, undefined, 'le collage',
      (n) => `${n} séance(s) collée(s) le ${this.fmtDate(target)}`);
  }

  /**
   * Recopie un lot de séances à partir d'une date cible. Les écarts de jour sont conservés :
   * coller un bloc de trois jours doit reproduire le bloc, pas empiler trois séances le même jour.
   *
   * `base` fixe l'origine des écarts. Sans elle, c'est la première séance du lot — ce qu'on veut
   * pour un collage. Pour une semaine entière, il faut au contraire caler sur le lundi, sinon
   * une semaine qui commence le mardi arriverait décalée d'un jour.
   */
  private pasteRefs(
    refs: ChipRef[], target: string, base: string | undefined,
    label: string, message: (n: number) => string,
  ): void {
    if (!this.requireWrite()) return;
    const sources = this.pasteSources(refs, target, base);
    if (!sources.length) { this.toast.warning('Les séances à copier ne sont plus affichées.'); return; }

    const created: { kind: 'course' | 'strength'; id: string }[] = [];
    const run = () => defer(() => {
      created.length = 0;
      return this.all(sources.map((src) => this.pasteOne(src, created)));
    });
    run().subscribe({
      next: () => {
        this.refreshAll();
        this.commit({
          label,
          undo: () => this.all(created.map((c) => this.removeCreated(c))),
          redo: () => run(),
        }, message(sources.length));
      },
      error: () => { this.refreshAll(); this.toast.error('Copie impossible.'); },
    });
  }

  /** Résout des références en (séance, date cible), décalage relatif conservé. */
  private pasteSources(refs: ChipRef[], target: string, base?: string):
    { kind: 'course' | 'strength'; source: Workout | ScheduledStrength; date: string }[] {
    const byId = new Map<string, Workout | ScheduledStrength>();
    for (const w of this.workouts()) byId.set(`course:${w.id}`, w);
    for (const st of this.strength()) byId.set(`strength:${st.id}`, st);

    const found = refs
      .map((r) => ({ kind: r.kind, source: byId.get(`${r.kind}:${r.id}`) }))
      .filter((x): x is { kind: 'course' | 'strength'; source: Workout | ScheduledStrength } => !!x.source);
    if (!found.length) return [];

    const origin = base ?? found.reduce(
      (min, x) => (x.source.scheduledDate < min ? x.source.scheduledDate : min),
      found[0].source.scheduledDate);
    return found.map((x) => ({
      ...x,
      date: this.shiftDate(target, this.daysBetween(origin, x.source.scheduledDate)),
    }));
  }

  private pasteOne(
    src: { kind: 'course' | 'strength'; source: Workout | ScheduledStrength; date: string },
    created: { kind: 'course' | 'strength'; id: string }[],
  ): Observable<unknown> {
    if (src.kind === 'course') {
      return this.workoutService.copy(this.selectedAthleteId, src.source.id, src.date)
        .pipe(tap((c) => created.push({ kind: 'course', id: c.id })));
    }
    const st = src.source as ScheduledStrength;
    if (!st.sourceSessionId) return of(null);
    return this.strengthService
      .scheduleSession(this.selectedAthleteId, st.sourceSessionId, { date: src.date, fieldsPreset: 'AVANCE' })
      .pipe(tap((c) => created.push({ kind: 'strength', id: c.id })));
  }

  private removeCreated(c: { kind: 'course' | 'strength'; id: string }): Observable<unknown> {
    return c.kind === 'course'
      ? this.workoutService.delete(this.selectedAthleteId, c.id)
      : this.strengthService.deleteScheduled(this.selectedAthleteId, c.id);
  }

  /** Duplique la sélection sur place (chaque séance sur son propre jour). */
  duplicateSelection(): void {
    const picked = this.selected();
    if (!picked.length) { this.toast.info('Sélectionne d’abord une séance.'); return; }
    const refs = picked.map((p) => ({ kind: p.kind, id: p.id }));
    // Cible = la date de la première : avec les écarts conservés, chacune retombe chez elle.
    this.pasteRefs(refs, picked[0].date, undefined, 'la duplication',
      (n) => `${n} séance(s) dupliquée(s)`);
  }

  /** Supprime tout le lot sélectionné, en une seule entrée d'annulation. */
  async deleteSelection(): Promise<void> {
    if (!this.requireWrite()) return;
    const workouts = this.selectedWorkouts();
    const strength = this.selectedStrength();
    const total = workouts.length + strength.length;
    if (!total) return;

    const ok = await this.confirm.ask({
      title: total > 1 ? `Supprimer ${total} séances` : 'Supprimer la séance',
      message: total > 1
        ? [...workouts.map((w) => w.title), ...strength.map((x) => x.title)].join(' · ')
        : (workouts[0]?.title ?? strength[0]?.title ?? ''),
      confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;

    // Instantanés d'abord : une fois supprimées côté serveur, les séances ne se relisent plus.
    this.all(workouts.map((w) => this.snapshot(w))).subscribe({
      next: (snaps) => {
        const snapshots = (snaps as { workout: Workout; structure: SessionStructure | null }[]) ?? [];
        const courseRefs = workouts.map((w) => ({ id: w.id }));
        const strengthRefs = strength.map((x) => ({ id: x.id, source: x.sourceSessionId, date: x.scheduledDate }));

        const removeAll = () => this.all([
          ...courseRefs.map((r) => this.workoutService.delete(this.selectedAthleteId, r.id)),
          ...strengthRefs.map((r) => this.strengthService.deleteScheduled(this.selectedAthleteId, r.id)),
        ]);

        removeAll().subscribe({
          next: () => {
            this.selection.clear();
            this.refreshAll();
            this.commit({
              label: total > 1 ? 'la suppression du lot' : 'la suppression',
              undo: () => this.all([
                ...snapshots.map((snap, i) => this.restore(snap).pipe(tap((c) => { courseRefs[i].id = c.id; }))),
                ...strengthRefs.filter((r) => r.source).map((r) => this.strengthService
                  .scheduleSession(this.selectedAthleteId, r.source!, { date: r.date, fieldsPreset: 'AVANCE' })
                  .pipe(tap((c) => { r.id = c.id; }))),
              ]),
              redo: () => removeAll(),
            }, total > 1 ? `${total} séances supprimées` : `« ${workouts[0]?.title ?? strength[0]?.title }» supprimée`);
          },
          error: () => { this.refreshAll(); this.toast.error('Suppression impossible.'); },
        });
      },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  /** Garde-fou commun : aucune action d'écriture sur un athlète en lecture seule. */
  private requireWrite(): boolean {
    if (this.canWriteSelected()) return true;
    this.toast.warning('Lecture seule : tu n’as pas les droits de prescription sur cet athlète.');
    return false;
  }

  /** Décale une date ISO de n jours (arithmétique locale, jamais de fuseau). */
  private shiftDate(iso: string, days: number): string {
    const [y, m, d] = iso.split('-').map(Number);
    const date = new Date(y, m - 1, d);
    date.setDate(date.getDate() + days);
    return toIso(date);
  }

  private daysBetween(from: string, to: string): number {
    const [y1, m1, d1] = from.split('-').map(Number);
    const [y2, m2, d2] = to.split('-').map(Number);
    return Math.round((new Date(y2, m2 - 1, d2).getTime() - new Date(y1, m1 - 1, d1).getTime()) / 86400000);
  }

  // --- Menu contextuel (clic droit) : alternative souris/clavier au glisser-déposer ----------
  readonly ctxMenu = signal<{ workout: Workout; x: number; y: number } | null>(null);
  ctxDate = '';

  openContextMenu(w: Workout, ev: MouseEvent): void {
    ev.preventDefault();
    if (!this.canWriteSelected()) return;
    this.ctxDate = w.scheduledDate;
    const { x, y } = this.clampToViewport(ev.clientX, ev.clientY);
    this.ctxMenu.set({ workout: w, x, y });
  }

  // --- Appui long : équivalent tactile du clic droit (CdC) --------------------
  // Le menu contextuel n'était atteignable qu'à la souris : sur mobile et tablette,
  // déplacer ou supprimer une séance était tout simplement impossible.

  private static readonly LONG_PRESS_MS = 500;
  /** Au-delà de ce déplacement, le geste est un glisser, pas un appui long. */
  private static readonly LONG_PRESS_TOLERANCE_PX = 10;

  private longPressTimer: ReturnType<typeof setTimeout> | null = null;
  private longPressOrigin: { x: number; y: number } | null = null;
  /** Un appui long vient d'ouvrir le menu : le clic qui suit ne doit pas ouvrir la séance. */
  private suppressNextClick = false;

  /** Démarre la détection d'appui long. Ignoré à la souris, qui garde le clic droit. */
  onChipPointerDown(ev: PointerEvent, target: Workout | ScheduledStrength, kind: 'course' | 'strength'): void {
    if (ev.pointerType === 'mouse' || !this.canWriteSelected()) return;
    this.cancelLongPress();
    this.longPressOrigin = { x: ev.clientX, y: ev.clientY };
    this.longPressTimer = setTimeout(() => {
      this.longPressTimer = null;
      this.suppressNextClick = true;
      const { x, y } = this.clampToViewport(ev.clientX, ev.clientY);
      if (kind === 'course') {
        const w = target as Workout;
        this.ctxDate = w.scheduledDate;
        this.ctxMenu.set({ workout: w, x, y });
      } else {
        const s = target as ScheduledStrength;
        this.strengthCtxDate = s.scheduledDate;
        this.strengthMenu.set({ session: s, x, y });
      }
      // Retour haptique quand la plateforme le propose : l'appui long est invisible sans lui.
      navigator.vibrate?.(15);
    }, CalendarComponent.LONG_PRESS_MS);
  }

  /** Un déplacement du doigt signifie un glisser : on abandonne l'appui long. */
  onChipPointerMove(ev: PointerEvent): void {
    const origin = this.longPressOrigin;
    if (!this.longPressTimer || !origin) return;
    const moved = Math.hypot(ev.clientX - origin.x, ev.clientY - origin.y);
    if (moved > CalendarComponent.LONG_PRESS_TOLERANCE_PX) this.cancelLongPress();
  }

  onChipPointerUp(): void { this.cancelLongPress(); }

  private cancelLongPress(): void {
    if (this.longPressTimer) clearTimeout(this.longPressTimer);
    this.longPressTimer = null;
    this.longPressOrigin = null;
  }

  /**
   * Le clic émis à la fin d'un appui long ne doit pas ouvrir la séance derrière le menu.
   * Renvoie vrai si le clic a été consommé.
   */
  private consumeSuppressedClick(): boolean {
    if (!this.suppressNextClick) return false;
    this.suppressNextClick = false;
    return true;
  }

  /** Position du menu bornée au viewport : en bord d'écran il sortait du cadre. */
  private clampToViewport(clientX: number, clientY: number): { x: number; y: number } {
    const MENU_W = 220;
    const MENU_H = 260;
    return {
      x: Math.max(8, Math.min(clientX, window.innerWidth - MENU_W - 8)),
      y: Math.max(8, Math.min(clientY, window.innerHeight - MENU_H - 8)),
    };
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
  /**
   * Verse la séance visée dans la bibliothèque, depuis le calendrier.
   *
   * <p>Le geste existait déjà — mais dans l'éditeur de structure, derrière un menu nommé
   * « Adapter la structure ». Un coach qui vient d'improviser une séance sur le calendrier et
   * veut la garder n'a aucune raison de deviner qu'il faut d'abord aller l'adapter. Ici il la
   * nomme et c'est fini : pas de navigation, pas d'écran intermédiaire.</p>
   *
   * <p>Sans catégorie : la ranger demanderait un second choix au moment où l'on veut seulement
   * ne pas perdre son travail. La bibliothèque permet de la classer ensuite.</p>
   */
  async ctxSaveToLibrary(): Promise<void> {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu();
    const w = m.workout;
    const title = await this.confirm.prompt({
      title: 'Enregistrer dans la bibliothèque',
      message: 'La structure est recopiée comme nouveau modèle ; la séance de l\'athlète n\'est pas '
        + 'modifiée. Les consignes écrites pour lui restent sur sa séance — un modèle resservira à '
        + 'd\'autres.',
      promptLabel: 'Nom du modèle',
      initialValue: w.title ?? '',
      confirmLabel: 'Ajouter à la bibliothèque',
    });
    if (!title) return;
    this.courseService.saveWorkoutAsTemplate(w.athleteId, w.id, { title }).subscribe({
      next: () => this.toast.success('« ' + title + ' » ajoutée à ta bibliothèque'),
      error: () => this.toast.error('Enregistrement impossible.'),
    });
  }

  ctxMoveTo(date: string): void {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu();
    if (date) this.moveWorkout(m.workout, date);
  }
  /** Copie la séance visée dans le presse-papier (le menu enseigne le raccourci). */
  ctxCopy(): void {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu();
    this.selection.select({ kind: 'course', id: m.workout.id });
    this.copySelection();
  }

  /** Duplique la séance visée sur son propre jour. */
  ctxDuplicate(): void {
    const m = this.ctxMenu(); if (!m) return;
    this.closeContextMenu();
    this.copyWorkout(m.workout, m.workout.scheduledDate);
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
    this.deleteWorkoutWithUndo(m.workout);
  }

  // --- Suppression réversible ---------------------------------------------------------------
  // La suppression était différée de 8 s : passé le délai, le geste devenait irrattrapable, et
  // la séance restait « supprimée à l'écran » tant que le délai courait — donc ressuscitée par
  // n'importe quel rechargement. On supprime maintenant tout de suite, mais on capture d'abord
  // de quoi reconstruire la séance à l'identique (attributs + structure figée). L'annulation
  // n'a plus de date de péremption : elle vaut tant que l'écran est ouvert.

  /** Tout ce qu'il faut pour recréer une séance supprimée, prescription figée comprise. */
  private snapshot(w: Workout): Observable<{ workout: Workout; structure: SessionStructure | null }> {
    return this.workoutService.prescription(this.selectedAthleteId, w.id).pipe(
      // Une prescription illisible ne doit pas empêcher la suppression : on restaurera alors
      // la séance sans sa structure, en le disant.
      switchMap((rx) => of({ workout: w, structure: rx?.snapshot ?? null })),
    );
  }

  /** Recrée une séance depuis son instantané et renvoie le nouvel identifiant. */
  private restore(snap: { workout: Workout; structure: SessionStructure | null }): Observable<Workout> {
    const w = snap.workout;
    return this.workoutService.create(this.selectedAthleteId, {
      scheduledDate: w.scheduledDate,
      type: w.type,
      title: w.title,
      notes: w.notes,
      targetDistanceM: w.targetDistanceM,
      targetDurationS: w.targetDurationS,
      steps: w.steps.map((st) => ({
        stepType: st.stepType, repetitions: st.repetitions, zone: st.zone,
        distanceM: st.distanceM, durationS: st.durationS, notes: st.notes,
      })),
    }).pipe(
      switchMap((created) => snap.structure
        ? this.workoutService.updateStructure(this.selectedAthleteId, created.id, snap.structure)
          .pipe(switchMap(() => of(created)))
        : of(created)),
    );
  }

  private deleteWorkoutWithUndo(w: Workout): void {
    this.snapshot(w).subscribe({
      next: (snap) => {
        this.workouts.update((l) => l.filter((x) => x.id !== w.id));
        const ref = { id: w.id };
        this.workoutService.delete(this.selectedAthleteId, ref.id).subscribe({
          next: () => this.commit({
            label: 'la suppression',
            undo: () => this.restore(snap).pipe(tap((created) => { ref.id = created.id; })),
            redo: () => this.workoutService.delete(this.selectedAthleteId, ref.id),
          }, `« ${w.title} » supprimée`),
          error: () => { this.toast.error('Suppression impossible.'); this.load(); },
        });
      },
      error: () => { this.toast.error('Suppression impossible.'); this.load(); },
    });
  }

  ngOnDestroy(): void {
    this.setCopyCursor(false); // la classe vit sur <body> : elle ne doit pas survivre à l'écran
    this.marqueeCleanup?.();
    // La pile est locale à l'écran : annuler un geste sur un calendrier qu'on ne regarde plus
    // produirait un effet invisible.
    this.undoStack.reset();
  }

  // --- Séances de force planifiées : détail, déplacement, suppression ---------
  // Les chips force sont manipulables exactement comme les séances course
  // (ouvrir / glisser / menu contextuel), dans la limite de canWriteSelected().

  readonly strengthPanelOpen = signal(false);
  readonly strengthDetail = signal<ScheduledStrength | null>(null);
  readonly strengthRx = signal<StrengthPrescriptionView | null>(null);
  readonly strengthRxLoading = signal(false);
  readonly strengthMenu = signal<{ session: ScheduledStrength; x: number; y: number } | null>(null);
  strengthCtxDate = '';

  /** Ouvre le panneau de détail d'une séance de force (prescription figée + charges calculées). */
  openStrength(s: ScheduledStrength): void {
    if (this.consumeSuppressedClick()) return;
    this.strengthDetail.set(s);
    this.strengthRx.set(null);
    this.strengthRxLoading.set(true);
    this.strengthPanelOpen.set(true);
    this.strengthService.scheduledPrescription(this.selectedAthleteId, s.id).subscribe({
      next: (rx) => { this.strengthRx.set(rx); this.strengthRxLoading.set(false); },
      error: () => { this.strengthRxLoading.set(false); this.toast.error('Détail indisponible.'); },
    });
  }

  openStrengthMenu(s: ScheduledStrength, ev: MouseEvent): void {
    ev.preventDefault();
    if (!this.canWriteSelected()) return;
    this.strengthCtxDate = s.scheduledDate;
    const { x, y } = this.clampToViewport(ev.clientX, ev.clientY);
    this.strengthMenu.set({ session: s, x, y });
  }
  closeStrengthMenu(): void { this.strengthMenu.set(null); }

  ctxStrengthOpen(): void {
    const m = this.strengthMenu(); if (!m) return;
    this.closeStrengthMenu(); this.openStrength(m.session);
  }
  ctxStrengthMoveTo(date: string): void {
    const m = this.strengthMenu(); if (!m) return;
    this.closeStrengthMenu();
    if (date) this.moveStrength(m.session, date);
  }
  ctxStrengthDelete(): void {
    const m = this.strengthMenu(); if (!m) return;
    this.closeStrengthMenu();
    this.deleteStrength(m.session);
  }

  /** Déplace une séance de force (optimiste + rollback en cas d'échec back). */
  private moveStrength(s: ScheduledStrength, targetDate: string): void {
    if (s.scheduledDate === targetDate) return;
    const previous = s.scheduledDate;
    this.strength.update((l) => l.map((x) => (x.id === s.id ? { ...x, scheduledDate: targetDate } : x)));
    this.strengthService.rescheduleScheduled(this.selectedAthleteId, s.id, targetDate).subscribe({
      next: () => this.commit({
        label: 'le déplacement',
        undo: () => this.strengthService.rescheduleScheduled(this.selectedAthleteId, s.id, previous),
        redo: () => this.strengthService.rescheduleScheduled(this.selectedAthleteId, s.id, targetDate),
      }, `${s.title} déplacée au ${this.fmtDate(targetDate)}`),
      error: () => {
        this.strength.update((l) => l.map((x) => (x.id === s.id ? { ...x, scheduledDate: previous } : x)));
        this.toast.error('Déplacement impossible.');
      },
    });
  }

  /**
   * Supprime une séance de force. Contrairement au course, il n'y a pas de reconstruction
   * fidèle possible (la prescription figée n'est pas ré-injectable) : on replanifie depuis la
   * séance de bibliothèque d'origine, ce qui restaure le contenu mais pas un éventuel ajustement
   * manuel. C'est dit dans le toast plutôt que promis en silence.
   */
  private deleteStrengthWithUndo(s: ScheduledStrength): void {
    this.strength.update((l) => l.filter((x) => x.id !== s.id));
    const source = s.sourceSessionId;
    const ref = { id: s.id };
    this.strengthService.deleteScheduled(this.selectedAthleteId, ref.id).subscribe({
      next: () => {
        // Sans séance de bibliothèque d'origine, il n'y a rien à replanifier : mieux vaut ne
        // pas promettre une annulation qui échouerait.
        if (!source) { this.toast.info(`« ${s.title} » supprimée`); return; }
        this.commit({
          label: 'la suppression',
          undo: () => this.strengthService
            .scheduleSession(this.selectedAthleteId, source, { date: s.scheduledDate, fieldsPreset: 'AVANCE' })
            .pipe(tap((created) => { ref.id = created.id; })),
          redo: () => this.strengthService.deleteScheduled(this.selectedAthleteId, ref.id),
        }, `« ${s.title} » supprimée`);
      },
      error: () => { this.toast.error('Suppression impossible.'); this.reloadStrength(); },
    });
  }

  async deleteStrength(s: ScheduledStrength): Promise<void> {
    if (!this.canWriteSelected()) {
      this.toast.warning('Lecture seule : tu n’as pas les droits de prescription sur cet athlète.');
      return;
    }
    const ok = await this.confirm.ask({
      title: 'Supprimer la séance de renforcement',
      message: `${s.title} — ${this.fmtDate(s.scheduledDate)}`,
      confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.strengthPanelOpen.set(false);
    this.deleteStrengthWithUndo(s);
  }

  /**
   * Récapitulatif des charges calculées pour l'athlète, à afficher au moment de la planification
   * (CdC §8) : « — ~55 min · 12,4 km · 420 UA ». Le coach voit ce que la séance donne pour CET
   * athlète, pas seulement qu'elle est planifiée.
   */
  private chargeRecap(w: Workout): string {
    const parts: string[] = [];
    if (w.targetDurationS) parts.push(`~${Math.round(w.targetDurationS / 60)} min`);
    if (this.volumeLabel(w)) parts.push(this.volumeLabel(w));
    if (w.plannedLoadUa) parts.push(`${w.plannedLoadUa} UA`);
    return parts.length ? ` — ${parts.join(' · ')}` : '';
  }

  /** Date ISO → « mer. 30 juil. » (jamais d'ISO brut à l'écran). */
  fmtDate(iso: string): string {
    const [y, m, d] = iso.split('-').map(Number);
    return new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' })
      .format(new Date(y, m - 1, d));
  }

  /**
   * Raccourcis clavier. Le calendrier devient un plan de travail : navigation, sélection,
   * presse-papier et annulation, sur le vocabulaire que tout le monde connaît déjà (celui d'un
   * tableur). La liste complète est documentée dans `calendar-shortcuts.ts` et affichée par « ? ».
   */
  onKeydown(ev: KeyboardEvent): void {
    const el = ev.target as HTMLElement | null;
    // Jamais dans un champ de saisie : Cmd+C doit y copier du texte, pas des séances.
    if (el && (/^(INPUT|SELECT|TEXTAREA)$/.test(el.tagName) || el.isContentEditable)) return;

    const mod = ev.metaKey || ev.ctrlKey;
    const key = ev.key.toLowerCase();

    if (mod) {
      if (key === 'z') {
        ev.preventDefault();
        ev.shiftKey ? this.redoLast() : this.undoLast();
      } else if (key === 'y') { ev.preventDefault(); this.redoLast(); }
      else if (key === 'c') { ev.preventDefault(); this.copySelection(); }
      else if (key === 'v') { ev.preventDefault(); this.pasteOn(null); }
      else if (key === 'd') { ev.preventDefault(); this.duplicateSelection(); }
      else if (key === 'a') { ev.preventDefault(); this.selectAll(); }
      return;
    }
    if (ev.altKey) return;

    // « ? » n'a pas la même touche selon la disposition : on se fie au caractère produit.
    if (ev.key === '?') { ev.preventDefault(); this.shortcutsOpen.set(true); return; }

    if (ev.key === 'Escape') {
      if (this.closeAllMenus()) { ev.preventDefault(); return; }
      if (this.selection.count() || this.dayRange().length) { this.clearSelection(); ev.preventDefault(); }
      return;
    }
    if (this.ctxMenu() || this.strengthMenu() || this.weekMenu()) return;

    if (ev.key === 'Delete' || ev.key === 'Backspace') {
      if (this.selection.count()) { ev.preventDefault(); void this.deleteSelection(); }
      return;
    }
    if (ev.key === 'ArrowLeft') { this.shift(-1); ev.preventDefault(); }
    else if (ev.key === 'ArrowRight') { this.shift(1); ev.preventDefault(); }
    else if (key === 't') { this.goToday(); ev.preventDefault(); }
    else if (key === 'j') { this.setMode('day'); ev.preventDefault(); }
    else if (key === '4') { if (this.scopeMode() === 'athlete') { this.setMode('4weeks'); ev.preventDefault(); } }
    else if (key === 'm') { if (this.scopeMode() === 'athlete') { this.setMode('month'); ev.preventDefault(); } }
    else if (key === 'b') { this.toggleSidebar(); ev.preventDefault(); }
    else if (key === 'n') {
      const date = this.hoveredDate();
      if (date) { this.addWorkout(date); ev.preventDefault(); }
    }
  }

  /** Ferme ce qui est ouvert par-dessus la grille ; vrai si quelque chose s'est fermé. */
  private closeAllMenus(): boolean {
    const wasOpen = !!(this.ctxMenu() || this.strengthMenu() || this.weekMenu() || this.dayMenu())
      || this.viewMenuOpen() || this.actionsMenuOpen() || this.pickerDates().length > 0;
    this.closeContextMenu();
    this.closeStrengthMenu();
    this.closeWeekMenu();
    this.closeDayMenu();
    this.viewMenuOpen.set(false);
    this.actionsMenuOpen.set(false);
    if (this.pickerDates().length) this.closePicker();
    return wasOpen;
  }

  private gridStart(): Date {
    return gridStartFor(this.mode(), this.anchor());
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
