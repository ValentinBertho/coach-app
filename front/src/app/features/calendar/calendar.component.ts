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
import { SaveToLibraryService } from '../../core/services/save-to-library.service';
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
  CELLS_BY_MODE, CalMode, gridStartFor, groupWeekLabel, mondayOf, periodLabelFor, shiftAnchor,
} from './calendar-period';
import { CalendarSelection, ChipPosition, ChipRef } from './calendar-selection';
import { SessionStructure } from '../../core/models/course.model';

/** Vue du calendrier : séances prévues, activités réalisées, ou les deux (façon Nolio). */
type CalView = 'planned' | 'realized' | 'both';

/**
 * Une entrée du presse-papier du calendrier.
 *
 * <p>Autoportante à dessein : elle dit <b>chez qui</b> et <b>quand</b> la séance se trouvait, pas
 * seulement son identifiant. Le presse-papier ne résolvait ses références qu'en relisant les
 * signaux de la grille mono-athlète ; il ne pouvait donc ni copier ni coller dans la vue groupe,
 * qui charge ses lignes ailleurs. Porter l'origine dans l'entrée est ce qui rend le geste
 * indépendant de la vue — et permet de coller la séance de Marc chez Julie.</p>
 */
interface ClipEntry extends ChipRef {
  /** Athlète chez qui la séance se trouve — la source, pas la cible. */
  readonly athleteId: string;
  /** Date d'origine : c'est elle qui donne les écarts d'un collage multi-jours. */
  readonly date: string;
  /** Séance de force : son modèle de bibliothèque, seule façon de la recréer ailleurs. */
  readonly sourceSessionId: string | null;
}

/** Une séance créée par un collage, et chez qui — de quoi l'annuler. */
interface Created {
  readonly kind: 'course' | 'strength';
  readonly id: string;
  readonly athleteId: string;
}

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
  private readonly library = inject(SaveToLibraryService);
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
  /**
   * Séances copiées (Cmd+C) : on garde la référence, pas une copie de l'objet.
   *
   * <p>Chaque entrée est <b>autoportante</b> — elle dit chez qui et quand la séance était, pas
   * seulement son identifiant. C'est ce qui permet de coller ailleurs que dans la grille qui a
   * servi à copier : la vue groupe n'affiche pas {@link workouts}, et résoudre une référence en
   * la relisant dans les signaux du mode athlète y renvoyait toujours « les séances à copier ne
   * sont plus affichées ». C'est le manque remonté en bêta : « sur les séances de groupe, juste
   * les copier-coller qui sont pas possible ».</p>
   */
  readonly clipboard = signal<{ entries: ClipEntry[]; label: string }>({ entries: [], label: '' });
  /** Jour survolé : cible implicite de Cmd+V et de N, comme un curseur de tableur. */
  readonly hoveredDate = signal<string | null>(null);
  /**
   * Case survolée en vue groupe : (athlète, jour). Le curseur du mode athlète ne connaît qu'une
   * date — ici, coller demande aussi de savoir chez qui.
   */
  readonly hoveredCell = signal<{ athleteId: string; date: string } | null>(null);
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
   * Période affichée. Une seule désormais — quatre semaines — mais gardée en signal : la grille,
   * les totaux et la navigation la lisent, et un jour où une autre maille reviendrait, ils n'ont
   * pas à changer.
   */
  readonly mode = signal<CalMode>('4weeks');
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
        label: this.dayNames[i % 7],
        dayNum: d.getDate(),
        isToday: iso === today,
        weekend: i % 7 >= 5,
        // Plus de vue « mois » : la fenêtre glissante n'a pas de jours « hors période » à griser.
        inMonth: true,
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

    // Alt (ou ⌘/Ctrl) enfoncé : on duplique au lieu de déplacer, comme en mode athlète. Le
    // curseur l'annonçait déjà pendant le glisser ; la grille groupe déplaçait quand même.
    const chip = item as unknown as Workout | ScheduledStrength;
    const native = event.event as MouseEvent;
    if (native && (native.altKey || native.ctrlKey || native.metaKey)) {
      if (chip.scheduledDate === targetDate) return;
      this.copyGroupChip(row, chip, targetDate);
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

  // --- Copier-coller en vue groupe ----------------------------------------------------------
  // Le mode groupe savait planifier depuis la bibliothèque et déplacer une séance d'un jour à
  // l'autre, mais pas la copier : « sur les séances de groupe, juste les copier-coller qui sont
  // pas possible » (retour bêta d'un coach de club). Or c'est précisément là que le geste sert
  // le plus — un club fait courir la même séance à plusieurs athlètes, à un détail près.
  //
  // Le presse-papier est le MÊME que celui du mode athlète : on copie ici, on colle là-bas, et
  // réciproquement. Deux presse-papiers auraient été deux gestes à apprendre pour un seul mot.

  /** Menu contextuel de la grille groupe : sur une chip, sur une case, ou sur « tout le groupe ». */
  readonly groupMenu = signal<{
    x: number; y: number;
    athleteId: string | null; athleteName: string; canWrite: boolean; date: string;
    entry: ClipEntry | null; title: string | null; all: boolean;
  } | null>(null);

  closeGroupMenu(): void { this.groupMenu.set(null); }

  onGroupCellEnter(row: GroupCalendarRow, date: string): void {
    this.hoveredCell.set({ athleteId: row.athleteId, date });
  }
  onGroupCellLeave(row: GroupCalendarRow, date: string): void {
    const h = this.hoveredCell();
    if (h && h.athleteId === row.athleteId && h.date === date) this.hoveredCell.set(null);
  }

  /** Clic droit sur une séance de la grille groupe. */
  openGroupChipMenu(row: GroupCalendarRow, chip: Workout | ScheduledStrength, ev: MouseEvent): void {
    ev.preventDefault();
    ev.stopPropagation();
    const { x, y } = this.clampToViewport(ev.clientX, ev.clientY);
    this.hoveredCell.set({ athleteId: row.athleteId, date: chip.scheduledDate });
    this.groupMenu.set({
      x, y, athleteId: row.athleteId, athleteName: `${row.firstName} ${row.lastName}`,
      canWrite: row.canWrite, date: chip.scheduledDate,
      entry: this.groupEntry(row, chip), title: chip.title, all: false,
    });
  }

  /** Clic droit sur une case (jour d'un athlète). */
  openGroupCellMenu(row: GroupCalendarRow, date: string, ev: MouseEvent): void {
    ev.preventDefault();
    if ((ev.target as HTMLElement).closest('[data-chip]')) return;
    const { x, y } = this.clampToViewport(ev.clientX, ev.clientY);
    this.hoveredCell.set({ athleteId: row.athleteId, date });
    this.groupMenu.set({
      x, y, athleteId: row.athleteId, athleteName: `${row.firstName} ${row.lastName}`,
      canWrite: row.canWrite, date, entry: null, title: null, all: false,
    });
  }

  /** Clic droit sur la ligne « tout le groupe » : coller la même séance à tout le monde. */
  openGroupAllMenu(date: string, ev: MouseEvent): void {
    ev.preventDefault();
    const { x, y } = this.clampToViewport(ev.clientX, ev.clientY);
    this.groupMenu.set({
      x, y, athleteId: null, athleteName: 'Tout le groupe', canWrite: true, date,
      entry: null, title: null, all: true,
    });
  }

  /** Une chip de la grille groupe, en entrée de presse-papier autoportante. */
  private groupEntry(row: GroupCalendarRow, chip: Workout | ScheduledStrength): ClipEntry {
    const strength = chip as ScheduledStrength;
    const isStrength = 'sourceSessionId' in chip;
    return {
      kind: isStrength ? 'strength' : 'course',
      id: chip.id,
      athleteId: row.athleteId,
      date: chip.scheduledDate,
      sourceSessionId: isStrength ? (strength.sourceSessionId ?? null) : null,
    };
  }

  /** Copie la séance visée par le menu. */
  ctxGroupCopy(): void {
    const m = this.groupMenu();
    this.closeGroupMenu();
    if (!m?.entry) return;
    this.putInClipboard([m.entry], m.title ?? '1 séance');
  }

  /** Copie tout ce que porte la case survolée (une journée d'un athlète) — Cmd+C en vue groupe. */
  copyHoveredCell(): void {
    const h = this.hoveredCell();
    const row = h ? this.groupRows().find((r) => r.athleteId === h.athleteId) : undefined;
    if (!h || !row) { this.toast.info('Survole la séance d’un athlète pour la copier.'); return; }
    const chips = [...this.rowWorkouts(row, h.date), ...this.rowStrength(row, h.date)];
    if (!chips.length) { this.toast.info('Rien à copier sur ce jour.'); return; }
    const entries = chips.map((c) => this.groupEntry(row, c));
    this.putInClipboard(entries, chips.length === 1 ? chips[0].title : `${chips.length} séances`);
  }

  /** Colle sur une case de la grille groupe (menu contextuel ou Cmd+V sur la case survolée). */
  pasteOnGroupCell(target?: { athleteId: string; date: string }): void {
    const cell = target ?? this.hoveredCell();
    if (!cell) { this.toast.info('Survole le jour d’un athlète pour coller.'); return; }
    const row = this.groupRows().find((r) => r.athleteId === cell.athleteId);
    if (!row) return;
    if (!row.canWrite) {
      this.toast.warning(`Lecture seule sur ${row.firstName} ${row.lastName}.`);
      return;
    }
    const entries = this.clipboard().entries;
    if (!entries.length) {
      this.toast.info(`Presse-papier vide — ${this.keys('mod', 'C')} pour copier.`);
      return;
    }
    this.pasteRefs(entries, row.athleteId, cell.date, undefined, 'le collage',
      (n) => `${n} séance(s) collée(s) chez ${row.firstName} le ${this.fmtDate(cell.date)}`);
  }

  ctxGroupPaste(): void {
    const m = this.groupMenu();
    this.closeGroupMenu();
    if (!m?.athleteId) return;
    this.pasteOnGroupCell({ athleteId: m.athleteId, date: m.date });
  }

  /**
   * Colle le presse-papier chez <b>tous</b> les athlètes du groupe, sur le même jour.
   *
   * <p>C'est le geste que la ligne « tout le groupe » proposait déjà pour la bibliothèque, étendu
   * à une séance déjà écrite : un coach de club ajuste une séance pour un athlète, puis la donne
   * telle quelle aux quatorze autres. Chacun reçoit ses <b>propres</b> cibles — le serveur
   * recalcule la prescription pour lui.</p>
   *
   * <p>Les athlètes en lecture seule sont ignorés, et on le dit : les taire ferait croire à une
   * prescription complète.</p>
   */
  async ctxGroupPasteAll(): Promise<void> {
    const m = this.groupMenu();
    this.closeGroupMenu();
    if (!m) return;
    const entries = this.clipboard().entries;
    if (!entries.length) {
      this.toast.info(`Presse-papier vide — ${this.keys('mod', 'C')} pour copier.`);
      return;
    }
    const rows = this.groupRows().filter((r) => r.canWrite);
    const skipped = this.groupRows().length - rows.length;
    if (!rows.length) { this.toast.warning('Aucun athlète du groupe n’est modifiable.'); return; }

    const ok = await this.confirm.ask({
      title: 'Coller pour tout le groupe',
      message: `Donner « ${this.clipboardLabel()} » à ${rows.length} athlète(s) le `
        + `${this.fmtDate(m.date)} ?`,
      confirmLabel: 'Coller',
    });
    if (!ok) return;

    // Un seul lot, une seule entrée d'annulation : le coach a fait un geste, il doit pouvoir le
    // défaire d'un geste — pas athlète par athlète.
    const created: Created[] = [];
    const sourcesFor = (athleteId: string) =>
      this.pasteSources(entries, m.date).map((src) => this.pasteOne(src, athleteId, created));
    const run = () => defer(() => {
      created.length = 0;
      return this.all(rows.flatMap((r) => sourcesFor(r.athleteId)));
    });
    run().subscribe({
      next: () => {
        this.refreshAll();
        const note = skipped > 0 ? ` — ${skipped} athlète(s) ignoré(s) (lecture seule)` : '';
        this.commit({
          label: 'le collage sur le groupe',
          undo: () => this.all(created.map((c) => this.removeCreated(c))),
          redo: () => run(),
        }, `${this.clipboardLabel()} collée pour ${rows.length} athlète(s)${note}`);
      },
      error: () => { this.refreshAll(); this.toast.error('Collage impossible.'); },
    });
  }

  /**
   * Duplique une séance déjà planifiée d'une case à l'autre (Alt + glisser, en vue groupe).
   *
   * <p>Le mode athlète connaît ce geste depuis toujours ; la grille groupe, elle, ne savait que
   * déplacer — et Alt enfoncé y donnait donc silencieusement le contraire de ce que le curseur
   * annonçait.</p>
   */
  private copyGroupChip(row: GroupCalendarRow, chip: Workout | ScheduledStrength, date: string): void {
    const entry = this.groupEntry(row, chip);
    this.pasteRefs([entry], row.athleteId, date, undefined, 'la copie',
      () => `${chip.title} copiée le ${this.fmtDate(date)}`);
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

  /**
   * Plage annoncée dans l'en-tête.
   *
   * <p>La grille groupe montre sept jours quel que soit le mode : elle a son libellé propre, sinon
   * l'en-tête annoncerait quatre semaines au-dessus d'une seule.</p>
   */
  readonly periodLabel = computed(() => this.scopeMode() === 'group'
    ? groupWeekLabel(this.anchor())
    : periodLabelFor(this.mode(), this.anchor()));

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
    // Un pas vaut ce qui est affiché : sept jours en groupe, la période choisie sinon.
    this.anchor.set(this.scopeMode() === 'group'
      ? new Date(this.anchor().getTime() + step * 7 * 864e5)
      : shiftAnchor(this.mode(), this.anchor(), step));
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
   * <p>Elle était réservée à la vue semaine, où « +34 % » ne pouvait désigner qu'elle. La fenêtre
   * de quatre semaines commençant toujours un lundi, la phrase porte sur sa première semaine —
   * celle qu'on est en train de construire.</p>
   */
  private loadWeekOutlook(from: string): void {
    if (!this.selectedAthleteId) {
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

    const athleteId = this.selectedAthleteId;
    const created: Created[] = [];
    const run = () => this.all(dates.map(
      (d) => schedule(d).pipe(tap((c) => created.push({ kind, id: c.id, athleteId })))));
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

  /**
   * Recharge tout ce qui peut avoir bougé (séances course + force).
   *
   * <p>Suit la vue affichée : la grille groupe ne lit ni {@link workouts} ni {@link strength},
   * et recharger le mode athlète derrière un collage fait en vue groupe ne montrait rien —
   * l'annulation, elle, semblait alors n'avoir aucun effet.</p>
   */
  private refreshAll(): void {
    if (this.scopeMode() === 'group') { this.loadGroup(); return; }
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

  /**
   * Toutes les chips d'une semaine (7 jours à partir du lundi donné), course et force.
   *
   * <p>Rend des entrées de presse-papier plutôt que de simples références : elles servent aussi
   * bien à la duplication de semaine (qui a besoin des dates d'origine) qu'à la sélection, à
   * laquelle {@code kind} et {@code id} suffisent.</p>
   */
  private weekChips(start: string): ClipEntry[] {
    const end = this.shiftDate(start, 6);
    const inWeek = (d: string) => d >= start && d <= end;
    const athleteId = this.selectedAthleteId;
    return [
      ...this.workouts().filter((w) => inWeek(w.scheduledDate)).map((w) => ({
        kind: 'course' as const, id: w.id, athleteId, date: w.scheduledDate, sourceSessionId: null,
      })),
      ...this.strength().filter((x) => inWeek(x.scheduledDate)).map((x) => ({
        kind: 'strength' as const, id: x.id, athleteId, date: x.scheduledDate,
        sourceSessionId: x.sourceSessionId ?? null,
      })),
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
    if (!this.requireWrite()) return;
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
    this.pasteRefs(refs, this.selectedAthleteId, targetIso, sourceStart, 'la duplication de semaine',
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
    const entries = this.entriesFor(this.selected());
    if (!entries.length) { this.toast.info('Rien à copier — sélectionne d’abord une séance.'); return; }
    const label = entries.length === 1
      ? (this.selectedWorkouts()[0]?.title ?? this.selectedStrength()[0]?.title ?? '1 séance')
      : `${entries.length} séances`;
    this.putInClipboard(entries, label);
  }

  /** Dépose dans le presse-papier et le dit, avec le raccourci qui sert la suite du geste. */
  private putInClipboard(entries: ClipEntry[], label: string): void {
    this.clipboard.set({ entries, label });
    this.toast.info(
      `${label} copiée${entries.length > 1 ? 's' : ''} — ${this.keys('mod', 'V')} pour coller sur un jour.`);
  }

  /** Résout des positions de la grille athlète en entrées autoportantes. */
  private entriesFor(picked: readonly ChipPosition[]): ClipEntry[] {
    const strengthById = new Map(this.strength().map((x) => [x.id, x]));
    return picked.map((p) => ({
      kind: p.kind, id: p.id, athleteId: this.selectedAthleteId, date: p.date,
      sourceSessionId: p.kind === 'strength' ? (strengthById.get(p.id)?.sourceSessionId ?? null) : null,
    }));
  }

  /**
   * Colle le presse-papier sur une date, ou sur le jour survolé.
   */
  pasteOn(date: string | null): void {
    const clip = this.clipboard();
    const target = date ?? this.hoveredDate();
    if (!clip.entries.length) { this.toast.info(`Presse-papier vide — ${this.keys('mod', 'C')} pour copier.`); return; }
    if (!target) { this.toast.info('Survole un jour pour coller.'); return; }
    if (!this.requireWrite()) return;
    this.pasteRefs(clip.entries, this.selectedAthleteId, target, undefined, 'le collage',
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
    entries: ClipEntry[], targetAthleteId: string, target: string, base: string | undefined,
    label: string, message: (n: number) => string,
  ): void {
    // Une séance de force sans modèle de bibliothèque ne se recrée pas : elle était écartée en
    // silence, et le toast annonçait quand même « 2 séances collées » pour une seule créée.
    const usable = entries.filter((e) => e.kind === 'course' || !!e.sourceSessionId);
    if (!usable.length) {
      this.toast.warning('Cette séance de renforcement n’a plus de modèle : impossible de la recopier.');
      return;
    }
    const sources = this.pasteSources(usable, target, base);

    const created: Created[] = [];
    const run = () => defer(() => {
      created.length = 0;
      return this.all(sources.map((src) => this.pasteOne(src, targetAthleteId, created)));
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

  /**
   * Cale les entrées sur la date cible, décalage relatif conservé.
   *
   * <p>Les dates d'origine viennent des entrées elles-mêmes, plus des signaux de la grille. C'est
   * ce qui rend le presse-papier utilisable d'une vue à l'autre : la vue groupe ne remplit ni
   * {@link workouts} ni {@link strength}, et une résolution par ces signaux y répondait
   * invariablement « les séances à copier ne sont plus affichées ».</p>
   */
  private pasteSources(entries: ClipEntry[], target: string, base?: string):
    { entry: ClipEntry; date: string }[] {
    if (!entries.length) return [];
    const origin = base ?? entries.reduce((min, e) => (e.date < min ? e.date : min), entries[0].date);
    return entries.map((entry) => ({
      entry,
      date: this.shiftDate(target, this.daysBetween(origin, entry.date)),
    }));
  }

  /**
   * Recrée une séance chez l'athlète cible.
   *
   * <p>Chez le <b>même</b> athlète, on duplique la séance telle quelle : sa prescription figée
   * est l'adaptation faite pour lui, et c'est elle qu'on veut à l'identique. Chez un <b>autre</b>,
   * le serveur recalcule les cibles pour celui qui les recevra — recopier les allures de Marc
   * chez Julie serait faux, et faux silencieusement.</p>
   */
  private pasteOne(
    src: { entry: ClipEntry; date: string }, targetAthleteId: string, created: Created[],
  ): Observable<unknown> {
    const entry = src.entry;
    if (entry.kind === 'course') {
      const call = entry.athleteId === targetAthleteId
        ? this.workoutService.copy(targetAthleteId, entry.id, src.date)
        : this.workoutService.copyFrom(targetAthleteId, entry.id, src.date);
      return call.pipe(tap((c) => created.push({ kind: 'course', id: c.id, athleteId: targetAthleteId })));
    }
    // Une séance de force se recrée depuis son modèle de bibliothèque : c'est déjà indépendant de
    // l'athlète, il n'y a donc rien de particulier à faire pour la donner à quelqu'un d'autre.
    if (!entry.sourceSessionId) return of(null);
    return this.strengthService
      .scheduleSession(targetAthleteId, entry.sourceSessionId, { date: src.date, fieldsPreset: 'AVANCE' })
      .pipe(tap((c) => created.push({ kind: 'strength', id: c.id, athleteId: targetAthleteId })));
  }

  private removeCreated(c: Created): Observable<unknown> {
    return c.kind === 'course'
      ? this.workoutService.delete(c.athleteId, c.id)
      : this.strengthService.deleteScheduled(c.athleteId, c.id);
  }

  /** Duplique la sélection sur place (chaque séance sur son propre jour). */
  duplicateSelection(): void {
    const picked = this.selected();
    if (!picked.length) { this.toast.info('Sélectionne d’abord une séance.'); return; }
    if (!this.requireWrite()) return;
    // Cible = la date de la première : avec les écarts conservés, chacune retombe chez elle.
    this.pasteRefs(this.entriesFor(picked), this.selectedAthleteId, picked[0].date, undefined,
      'la duplication', (n) => `${n} séance(s) dupliquée(s)`);
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
    await this.library.promptAndSave(m.workout);
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
      // Le même raccourci dans les deux vues. En mode groupe il n'y a pas de sélection : le
      // curseur est la case survolée (un athlète × un jour), comme le jour survolé l'est en mode
      // athlète — et copier y prend toute la journée de cet athlète.
      else if (key === 'c') {
        ev.preventDefault();
        this.scopeMode() === 'group' ? this.copyHoveredCell() : this.copySelection();
      }
      else if (key === 'v') {
        ev.preventDefault();
        this.scopeMode() === 'group' ? this.pasteOnGroupCell() : this.pasteOn(null);
      }
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
    if (this.ctxMenu() || this.strengthMenu() || this.weekMenu() || this.groupMenu()) return;

    if (ev.key === 'Delete' || ev.key === 'Backspace') {
      if (this.selection.count()) { ev.preventDefault(); void this.deleteSelection(); }
      return;
    }
    if (ev.key === 'ArrowLeft') { this.shift(-1); ev.preventDefault(); }
    else if (ev.key === 'ArrowRight') { this.shift(1); ev.preventDefault(); }
    else if (key === 't') { this.goToday(); ev.preventDefault(); }
    else if (key === 'b') { this.toggleSidebar(); ev.preventDefault(); }
    else if (key === 'n') {
      const date = this.hoveredDate();
      if (date) { this.addWorkout(date); ev.preventDefault(); }
    }
  }

  /** Ferme ce qui est ouvert par-dessus la grille ; vrai si quelque chose s'est fermé. */
  private closeAllMenus(): boolean {
    const wasOpen = !!(this.ctxMenu() || this.strengthMenu() || this.weekMenu() || this.dayMenu()
      || this.groupMenu())
      || this.viewMenuOpen() || this.actionsMenuOpen() || this.pickerDates().length > 0;
    this.closeContextMenu();
    this.closeStrengthMenu();
    this.closeWeekMenu();
    this.closeDayMenu();
    this.closeGroupMenu();
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
