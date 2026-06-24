import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import { AthleteSummary } from '../../core/models/athlete.model';
import {
  Athlete1rm,
  CycleWeek,
  E1rmHistory,
  E1rmResult,
  ExerciseCategory,
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
  imports: [FormsModule, DatePipe, RouterLink],
  templateUrl: './strength.component.html',
  styleUrl: './strength.component.scss',
})
export class StrengthComponent implements OnInit {
  private readonly strength = inject(StrengthService);
  private readonly athletes = inject(AthleteService);
  private readonly toast = inject(ToastService);

  readonly tab = signal<Tab>('exercises');

  // Exercices
  readonly exercises = signal<PpExercise[]>([]);
  readonly loadingEx = signal(true);
  readonly loadingSes = signal(true);
  readonly filterCategory = signal('');
  readonly filterMuscle = signal('');
  readonly searchQ = signal('');
  readonly showExerciseForm = signal(false);
  newExercise = { name: '', category: 'FORCE_MAX' as ExerciseCategory, muscle: '' as MuscleGroup | '', videoUrl: '', instructions: '' };

  // Séances
  readonly sessions = signal<StrengthSession[]>([]);
  newSessionName = '';

  // Cycles
  readonly cycles = signal<StrengthCycle[]>([]);
  readonly loadingCycles = signal(true);
  newCycle = { name: '', weeks: 4, objective: '', sessionIds: [] as string[] };
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
    this.loadSessions();
    this.loadCycles();
    this.computeRm();
    this.athletes.list({ page: 0 }).subscribe((p) => this.athleteList.set(p.content));
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

  createCycle(): void {
    if (!this.newCycle.name.trim() || this.newCycle.sessionIds.length === 0) return;
    const weeks: CycleWeek[] = Array.from({ length: this.newCycle.weeks }, (_, i) => ({
      week: i + 1,
      sessionIds: [...this.newCycle.sessionIds],
      chargePctAdjustment: i * 2.5,
    }));
    this.strength
      .createCycle({ name: this.newCycle.name, weeks: this.newCycle.weeks, objective: this.newCycle.objective || null, structure: { weeks } })
      .subscribe(() => {
        this.toast.success('Cycle créé ✅');
        this.newCycle = { name: '', weeks: 4, objective: '', sessionIds: [] };
        this.loadCycles();
      });
  }

  assignCycle(cycleId: string): void {
    const a = this.selectedAthlete();
    if (!a) { this.toast.warning('Sélectionne un athlète (onglet Suivi) d\'abord.'); return; }
    this.strength.assignCycle(cycleId, a, this.assignDate()).subscribe((res) => {
      this.toast.success(`${res.scheduled} séance(s) planifiée(s) ✅`);
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
        muscleGroups: this.newExercise.muscle ? [this.newExercise.muscle] : [],
        videoUrl: this.newExercise.videoUrl || null,
        instructions: this.newExercise.instructions || null,
      })
      .subscribe(() => {
        this.toast.success('Exercice créé ✅');
        this.newExercise = { name: '', category: 'FORCE_MAX', muscle: '', videoUrl: '', instructions: '' };
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
      this.toast.success('Séance créée ✅');
      this.newSessionName = '';
      this.loadSessions();
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
      this.toast.success(`Test enregistré — e1RM ${t.computedE1rmKg} kg ✅`);
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
    this.strength.e1rmHistory(a, exerciseId).subscribe((h) => this.history.set(h));
  }

  label(value: string): string {
    return value.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  }
}
