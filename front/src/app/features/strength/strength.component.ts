import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
  StrengthSession,
} from '../../core/models/strength.model';

type Tab = 'exercises' | 'sessions' | 'cycles' | 'tests1rm' | 'analysis';

@Component({
  selector: 'app-strength',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, DatePipe],
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

  // Suivi
  readonly athleteList = signal<AthleteSummary[]>([]);
  readonly selectedAthlete = signal('');
  readonly profile1rm = signal<Athlete1rm[]>([]);
  readonly history = signal<E1rmHistory[]>([]);

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

  // --- Suivi ---
  onAthleteChange(id: string): void {
    this.selectedAthlete.set(id);
    this.history.set([]);
    if (!id) {
      this.profile1rm.set([]);
      return;
    }
    this.strength.list1rm(id).subscribe((list) => this.profile1rm.set(list));
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
