import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TrendChartComponent, type TrendPoint } from '../../shared/components/trend-chart/trend-chart.component';
import { SidePanelComponent } from '../../shared/components/ui';
import { AthleteService } from '../../core/services/athlete.service';
import { StrengthService } from '../../core/services/strength.service';
import { SessionCategoryService } from '../../core/services/session-category.service';
import { SessionCategory } from '../../core/models/session-category.model';
import { ToastService } from '../../core/services/toast.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import {
  Athlete1rm,
  CycleWeek,
  E1rmHistory,
  E1rmResult,
  EquipmentType,
  ExerciseCategory,
  ExerciseLevel,
  MuscleGroup,
  PpExercise,
  RmFormula,
  StrengthCycle,
  StrengthLoadPoint,
  StrengthSession,
  StrengthTest,
  StrengthTestProtocol,
} from '../../core/models/strength.model';

type Tab = 'exercises' | 'sessions' | 'cycles' | 'tests1rm' | 'analysis';

@Component({
  selector: 'app-strength',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, IconComponent, FormsModule, DatePipe, RouterLink, TrendChartComponent, SidePanelComponent],
  templateUrl: './strength.component.html',
  styleUrl: './strength.component.scss',
})
export class StrengthComponent implements OnInit {
  private readonly strength = inject(StrengthService);
  private readonly athletes = inject(AthleteService);
  private readonly categoryService = inject(SessionCategoryService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly tab = signal<Tab>('exercises');

  // Exercices
  readonly exercises = signal<PpExercise[]>([]);
  readonly loadingEx = signal(true);
  readonly loadingSes = signal(true);
  readonly filterCategory = signal('');
  readonly filterMuscle = signal('');
  readonly searchQ = signal('');
  readonly showExerciseForm = signal(false);
  newExercise = {
    name: '', category: 'FORCE_MAX' as ExerciseCategory, categoryId: '' as string,
    muscle: '' as MuscleGroup | '', videoUrl: '', imageUrl: '', instructions: '',
    // Fiche exercice complète (CdC §7.5) : le modèle de données portait déjà ces champs,
    // seule l'UI les ignorait.
    level: 'INTERMEDIAIRE' as ExerciseLevel, equipment: [] as EquipmentType[],
    contraindications: '', progressionId: '', regressionId: '',
  };

  readonly levels: ExerciseLevel[] = ['DEBUTANT', 'INTERMEDIAIRE', 'AVANCE'];
  readonly equipments: EquipmentType[] = [
    'POIDS_DU_CORPS', 'HALTERES', 'KETTLEBELL', 'BARRE', 'MACHINE', 'ELASTIQUE',
    'MEDECINE_BALL', 'BOX', 'TRX', 'POULIE', 'LEG_EXTENSION', 'LEG_CURL', 'PRESSE', 'AUTRE'];

  toggleEquipment(eq: EquipmentType): void {
    const list = this.newExercise.equipment;
    this.newExercise.equipment = list.includes(eq) ? list.filter((x) => x !== eq) : [...list, eq];
  }

  /** Catégories unifiées du domaine prépa physique (QA1). */
  readonly strengthCats = signal<SessionCategory[]>([]);
  /** Filtre par catégorie unifiée : '' = toutes, '__none__' = sans catégorie, sinon id. */
  readonly filterCategoryId = signal('');
  newStrengthCatName = '';

  private readonly catNameById = computed(() => {
    const map = new Map<string, string>();
    for (const c of this.strengthCats()) map.set(c.id, c.name);
    return map;
  });

  catName(id: string | null): string {
    return id ? this.catNameById().get(id) ?? '' : '';
  }

  /** Exercices filtrés par catégorie unifiée (le filtre enum/muscle reste côté serveur). */
  readonly filteredExercises = computed(() => {
    const f = this.filterCategoryId();
    return this.exercises().filter((e) =>
      !f || (f === '__none__' ? !e.categoryId : e.categoryId === f));
  });

  // Séances
  readonly sessions = signal<StrengthSession[]>([]);
  newSessionName = '';

  // Cycles
  readonly cycles = signal<StrengthCycle[]>([]);
  readonly loadingCycles = signal(true);
  // Un cycle sans progression de charge n'est qu'une semaine répétée à l'identique : on reprend
  // le vocabulaire du générateur de mésocycle course (progression, décharge) pour ne pas
  // inventer un second modèle mental.
  newCycle = {
    name: '', weeks: 4, objective: '', sessionIds: [] as string[],
    increasePct: 2.5, deloadEvery: 4, deloadPct: 60,
  };
  readonly assignDate = signal(new Date().toISOString().slice(0, 10));

  // Tests 1RM
  rm = { weight: 100, reps: 5, formula: 'NUZZO' as RmFormula };
  readonly rmResult = signal<E1rmResult | null>(null);

  // Tests directs (4 protocoles)
  readonly tests = signal<StrengthTest[]>([]);
  newTest = { exerciseId: '', protocol: 'TRUE_1RM' as StrengthTestProtocol, weightKg: 100, reps: 1, durationSec: 5 };
  readonly protocols: { value: StrengthTestProtocol; label: string }[] = [
    { value: 'TRUE_1RM', label: '1RM direct (1 rép. max)' },
    { value: 'REP_TEST_3_5', label: 'Test 3–5 reps (à l\'échec)' },
    { value: 'AMRAP_TEST', label: 'AMRAP (reps max à charge fixe)' },
    { value: 'ISO_MVC', label: 'Isométrie max (MVC)' },
  ];

  // Suivi
  readonly athleteList = signal<AthleteSummary[]>([]);
  readonly selectedAthlete = signal('');
  readonly profile1rm = signal<Athlete1rm[]>([]);
  readonly history = signal<E1rmHistory[]>([]);
  readonly loadPoints = signal<StrengthLoadPoint[]>([]);

  readonly categories: ExerciseCategory[] = [
    'FORCE_MAX', 'HYPERTROPHIE', 'PUISSANCE', 'PLIOMETRIE', 'ISOMETRIE',
    'ENDURANCE_MUSCULAIRE', 'GAINAGE', 'MOBILITE', 'REATHLETISATION', 'PREVENTION'];
  readonly muscles: MuscleGroup[] = [
    'QUADRICEPS', 'ISCHIOS', 'MOLLETS', 'FESSIERS', 'TRONC', 'HAUT_DU_CORPS',
    'PIED_CHEVILLE', 'HANCHE', 'DOS', 'EPAULE'];
  readonly formulas: RmFormula[] = ['NUZZO', 'EPLEY', 'BRZYCKI', 'RIR_BASED'];

  ngOnInit(): void {
    this.loadExercises();
    this.loadStrengthCategories();
    this.loadSessions();
    this.loadCycles();
    this.computeRm();
    this.athletes.list({ page: 0 }).subscribe((p) => this.athleteList.set(p.content));
  }

  private loadStrengthCategories(): void {
    this.categoryService.list('STRENGTH').subscribe({
      next: (c) => this.strengthCats.set(c),
      error: () => this.strengthCats.set([]),
    });
  }

  createStrengthCategory(): void {
    const name = this.newStrengthCatName.trim();
    if (!name) return;
    this.categoryService.create({ name }, 'STRENGTH').subscribe((c) => {
      this.strengthCats.update((list) => [...list, c]);
      this.newStrengthCatName = '';
      this.toast.success('Catégorie ajoutée.');
    });
  }

  /** Réassigne la catégorie unifiée d'un exercice existant (mise à jour sans perte des autres champs). */
  assignExerciseCategory(e: PpExercise, categoryId: string): void {
    this.strength.updateExercise(e.id, {
      name: e.name, category: e.category, categoryId: categoryId || null,
      level: e.level, objective: e.objective,
      muscleGroups: e.muscleGroups, equipment: e.equipment,
      videoUrl: e.videoUrl, imageUrl: e.imageUrl, instructions: e.instructions,
      technicalNotes: e.technicalNotes, contraindications: e.contraindications,
      progressionId: e.progressionId, regressionId: e.regressionId,
    }).subscribe((updated) => {
      this.exercises.update((list) => list.map((x) => (x.id === e.id ? updated : x)));
    });
  }

  // --- Cycles ---
  loadCycles(): void {
    this.loadingCycles.set(true);
    this.strength.listCycles().subscribe({
      next: (c) => { this.cycles.set(c); this.loadingCycles.set(false); },
      error: () => this.loadingCycles.set(false),
    });
  }

  toggleCycleSession(id: string): void {
    const arr = this.newCycle.sessionIds;
    this.newCycle.sessionIds = arr.includes(id) ? arr.filter((s) => s !== id) : [...arr, id];
  }

  /**
   * Progression de charge semaine par semaine : montée régulière, coupée par une semaine de
   * décharge tous les N. L'ajustement est relatif à la prescription de base du cycle (0 % en
   * semaine 1) et s'applique aux charges au moment de l'assignation.
   */
  weeklyAdjustment(week: number): number {
    const c = this.newCycle;
    if (c.deloadEvery > 0 && week % c.deloadEvery === 0) {
      return Math.round((c.deloadPct - 100) * 10) / 10;
    }
    return Math.round((week - 1) * c.increasePct * 10) / 10;
  }

  /** Aperçu de la progression, pour que le coach voie ce qu'il programme avant de créer. */
  readonly cyclePreview = computed<{ week: number; pct: number; deload: boolean }[]>(() => {
    const c = this.newCycle;
    return Array.from({ length: Math.max(1, Math.min(c.weeks, 16)) }, (_, i) => {
      const week = i + 1;
      return {
        week,
        pct: this.weeklyAdjustment(week),
        deload: c.deloadEvery > 0 && week % c.deloadEvery === 0,
      };
    });
  });

  createCycle(): void {
    if (!this.newCycle.name.trim() || this.newCycle.sessionIds.length === 0) return;
    const weeks: CycleWeek[] = Array.from({ length: this.newCycle.weeks }, (_, i) => ({
      week: i + 1,
      sessionIds: [...this.newCycle.sessionIds],
      chargePctAdjustment: this.weeklyAdjustment(i + 1),
    }));
    this.strength
      .createCycle({ name: this.newCycle.name, weeks: this.newCycle.weeks, objective: this.newCycle.objective || null, structure: { weeks } })
      .subscribe(() => {
        this.toast.success('Cycle créé');
        this.newCycle = { name: '', weeks: 4, objective: '', sessionIds: [], increasePct: 2.5, deloadEvery: 4, deloadPct: 60 };
        this.loadCycles();
      });
  }

  // --- Assignation d'un cycle : athlète + date + confirmation dans le même geste --------
  // Les sélecteurs vivaient dans une carte séparée des cartes cycles : le lien entre les deux
  // n'était pas évident, et rien ne disait à quel cycle s'appliquait la date choisie.
  readonly assignPanelOpen = signal(false);
  readonly assignTarget = signal<StrengthCycle | null>(null);
  readonly assigning = signal(false);

  openAssign(c: StrengthCycle): void {
    this.assignTarget.set(c);
    this.assignPanelOpen.set(true);
  }

  confirmAssign(): void {
    const c = this.assignTarget();
    const a = this.selectedAthlete();
    if (!c) return;
    if (!a) { this.toast.warning('Choisis un athlète.'); return; }
    this.assigning.set(true);
    this.strength.assignCycle(c.id, a, this.assignDate()).subscribe({
      next: (res) => {
        this.assigning.set(false);
        this.assignPanelOpen.set(false);
        this.toast.success(`${res.scheduled} séance(s) planifiée(s) — charges ajustées semaine par semaine`);
      },
      error: () => { this.assigning.set(false); this.toast.error('Assignation impossible.'); },
    });
  }

  switchTab(t: Tab): void {
    this.tab.set(t);
  }

  // --- Exercices ---
  loadExercises(): void {
    this.loadingEx.set(true);
    this.strength
      .listExercises({
        category: this.filterCategory() || undefined,
        muscle: this.filterMuscle() || undefined,
        q: this.searchQ() || undefined,
      })
      .subscribe({
        next: (p) => { this.exercises.set(p.content); this.loadingEx.set(false); },
        error: () => this.loadingEx.set(false),
      });
  }

  createExercise(): void {
    if (!this.newExercise.name.trim()) return;
    this.strength
      .createExercise({
        name: this.newExercise.name,
        category: this.newExercise.category,
        categoryId: this.newExercise.categoryId || null,
        level: this.newExercise.level,
        muscleGroups: this.newExercise.muscle ? [this.newExercise.muscle] : [],
        equipment: this.newExercise.equipment,
        videoUrl: this.newExercise.videoUrl || null,
        imageUrl: this.newExercise.imageUrl || null,
        instructions: this.newExercise.instructions || null,
        contraindications: this.newExercise.contraindications || null,
        progressionId: this.newExercise.progressionId || null,
        regressionId: this.newExercise.regressionId || null,
      })
      .subscribe(() => {
        this.toast.success('Exercice créé');
        this.newExercise = {
          name: '', category: 'FORCE_MAX', categoryId: this.newExercise.categoryId, muscle: '',
          videoUrl: '', imageUrl: '', instructions: '', level: 'INTERMEDIAIRE', equipment: [],
          contraindications: '', progressionId: '', regressionId: '',
        };
        this.showExerciseForm.set(false);
        this.loadExercises();
      });
  }

  // --- Séances ---
  loadSessions(): void {
    this.loadingSes.set(true);
    this.strength.listSessions().subscribe({
      next: (p) => { this.sessions.set(p.content); this.loadingSes.set(false); },
      error: () => this.loadingSes.set(false),
    });
  }

  createSession(): void {
    if (!this.newSessionName.trim()) return;
    this.strength.createSession({ name: this.newSessionName }).subscribe(() => {
      this.toast.success('Séance créée');
      this.newSessionName = '';
      this.loadSessions();
    });
  }

  /** Duplication en cours (évite le double clic, qui créerait deux copies). */
  readonly duplicating = signal(false);

  /**
   * Duplique une séance de force et ouvre son éditeur de structure : on duplique pour créer une
   * variante, l'étape suivante est toujours l'ajustement.
   */
  duplicateSession(s: StrengthSession): void {
    if (this.duplicating()) return;
    this.duplicating.set(true);
    this.strength.duplicateSession(s.id).subscribe({
      next: (copy) => {
        this.duplicating.set(false);
        this.toast.success(`« ${copy.name} » créée — ajuste la structure`);
        this.router.navigate(['/app/strength/sessions', copy.id, 'structure']);
      },
      error: () => { this.duplicating.set(false); this.toast.error('Duplication impossible.'); },
    });
  }

  // --- Tests 1RM ---
  computeRm(): void {
    this.strength.calcE1rm({ weight: this.rm.weight, reps: this.rm.reps, formula: this.rm.formula })
      .subscribe((r) => this.rmResult.set(r));
  }

  needsReps(): boolean {
    return this.newTest.protocol === 'REP_TEST_3_5' || this.newTest.protocol === 'AMRAP_TEST';
  }

  needsDuration(): boolean {
    return this.newTest.protocol === 'ISO_MVC';
  }

  loadTests(): void {
    const a = this.selectedAthlete();
    if (!a) { this.tests.set([]); return; }
    this.strength.listTests(a).subscribe((t) => this.tests.set(t));
  }

  recordTest(): void {
    const a = this.selectedAthlete();
    if (!a) { this.toast.warning('Sélectionne un athlète d\'abord.'); return; }
    if (!this.newTest.exerciseId) { this.toast.warning('Choisis un exercice.'); return; }
    this.strength.recordTest(a, {
      exerciseId: this.newTest.exerciseId,
      protocol: this.newTest.protocol,
      weightKg: this.newTest.weightKg,
      reps: this.needsReps() ? this.newTest.reps : null,
      durationSec: this.needsDuration() ? this.newTest.durationSec : null,
    }).subscribe((t) => {
      this.toast.success(`Test enregistré — e1RM ${t.computedE1rmKg} kg`);
      this.loadTests();
    });
  }

  exerciseName(id: string): string {
    return this.exercises().find((e) => e.id === id)?.name ?? id.slice(0, 8) + '…';
  }


  protocolLabel(p: StrengthTestProtocol): string {
    return this.protocols.find((x) => x.value === p)?.label ?? p;
  }

  // --- Suivi ---
  onAthleteChange(id: string): void {
    this.selectedAthlete.set(id);
    this.history.set([]);
    if (!id) {
      this.profile1rm.set([]);
      this.tests.set([]);
      this.loadPoints.set([]);
      return;
    }
    this.strength.list1rm(id).subscribe((list) => this.profile1rm.set(list));
    this.strength.loadTracking(id).subscribe((pts) => this.loadPoints.set(pts));
    this.loadTests();
  }

  maxLoad(): number {
    return Math.max(1, ...this.loadPoints().map((p) => p.mechanicalLoad));
  }

  loadHistory(exerciseId: string): void {
    const a = this.selectedAthlete();
    if (!a) return;
    this.historyExerciseId.set(exerciseId);
    this.strength.e1rmHistory(a, exerciseId).subscribe((h) => this.history.set(h));
  }

  /** Exercice dont l'historique est affiché (titre du graphe d'évolution). */
  readonly historyExerciseId = signal<string>('');
  readonly historyExerciseName = computed(() => {
    const id = this.historyExerciseId();
    if (!id) return '';
    return this.profile1rm().find((p) => p.exerciseId === id)?.exerciseName || this.exerciseName(id);
  });

  /** Historique e1RM en série temporelle : une liste de valeurs ne dit pas si l'athlète progresse. */
  readonly e1rmPoints = computed<TrendPoint[]>(() =>
    this.history().map((h) => ({ date: h.calculatedAt, value: h.e1rmKg })),
  );

  label(value: string): string {
    return value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  }
}
