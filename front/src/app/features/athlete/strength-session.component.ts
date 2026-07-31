import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AthletePortalService, StrengthPrescriptionView } from '../../core/services/athlete-portal.service';
import { CelebrationService } from '../../core/services/celebration.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { Progression, ScheduledStrength, StrengthResultEntry } from '../../core/models/strength.model';
import { IconComponent } from '../../shared/components/icon/icon.component';
import {
  EffortBadgeComponent,
  type EffortKind,
  PainFatigueSelectorComponent,
  RangePrescriptionPillComponent,
} from '../../shared/components/physiology';
import { RpeScaleSelectorComponent } from '../../shared/components/rpe-scale-selector/rpe-scale-selector.component';

/** Une série saisie par l'athlète. `done` = validée dans le parcours guidé. */
interface SetEntry {
  chargeKg: number | null;
  repsDone: number | null;
  rirDone: number | null;
  done: boolean;
}

/** Fourchettes prescrites d'un exercice, affichées en lecture seule au-dessus de la saisie. */
interface ExerciseRx {
  chargeKgMin: number | null;
  chargeKgMax: number | null;
  repsMin: number | null;
  repsMax: number | null;
  repsFixed: number | null;
  effortKind: EffortKind | null;
  effortMin: number | null;
  effortMax: number | null;
}

interface ExerciseSets {
  exerciseId: string;
  name: string;
  sets: SetEntry[];
  rx: ExerciseRx;
}

type State = 'loading' | 'ready' | 'error';

/** Incrément de charge : le plus petit disque de la plupart des salles. */
const CHARGE_STEP_KG = 2.5;

/**
 * Mode séance de force plein écran — un exercice à la fois.
 *
 * Ce que ça remplace : un tableau charge × reps × RIR par série et par exercice, posé
 * directement dans la carte « Aujourd'hui ». Une séance de 4 exercices × 4 séries, c'est
 * 48 champs de formulaire sur 375 px, à remplir debout, en sueur, entre deux séries. Ce n'était
 * pas une carte, c'était un écran — il sort donc du flux et devient un parcours guidé.
 *
 * Les choix qui en découlent :
 * - **une série à la fois**, avec « série 2/4 » : on sait toujours où on en est sans compter ;
 * - **gros chiffres** (mono tabulaire) : lisibles à bout de bras, posés sur la barre ;
 * - **± 2,5 kg / ± 1 rep** au pouce : plus de clavier numérique qui recouvre l'écran ;
 * - **recopie de la série précédente** : la plupart des séries ne varient pas, et la série
 *   suivante démarre déjà sur les valeurs de la précédente ;
 * - **le ressenti en dernière étape** : fatigue + douleur (la forme) puis RPE (la difficulté),
 *   jamais mélangés — le RPE n'entre pas dans la forme.
 */
@Component({
  selector: 'app-strength-session',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IconComponent, RangePrescriptionPillComponent, EffortBadgeComponent,
    PainFatigueSelectorComponent, RpeScaleSelectorComponent,
  ],
  templateUrl: './strength-session.component.html',
  styleUrl: './strength-session.component.scss',
})
export class StrengthSessionComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly celebration = inject(CelebrationService);

  readonly state = signal<State>('loading');
  readonly session = signal<ScheduledStrength | null>(null);
  readonly exercises = signal<ExerciseSets[]>([]);
  readonly progression = signal<Progression | null>(null);
  /** Séances consécutives notées, affichée à la clôture. */
  readonly streak = signal(0);

  /** Index de l'exercice courant ; `exercises().length` = étape de ressenti (fin de séance). */
  readonly stepIndex = signal(0);
  /**
   * Séance enregistrée : écran de clôture. Il existe pour une raison précise — la suggestion
   * de progression du coach et surtout ses **alertes** (douleur, réathlétisation) arrivent
   * avec la réponse serveur. Renvoyer l'athlète sur « Aujourd'hui » sans les montrer les
   * ferait disparaître sans que personne ne les lise.
   */
  readonly finished = signal(false);
  /** Index de la série en cours au sein de l'exercice courant. */
  readonly setIndex = signal(0);
  readonly saving = signal(false);

  // Ressenti de fin de séance. Fatigue + douleur = la forme ; le RPE dit la difficulté.
  readonly fatigue = signal<number | null>(null);
  readonly pain = signal<number | null>(null);
  readonly rpe = signal<number | null>(null);

  readonly totalSteps = computed(() => this.exercises().length);
  readonly isDebrief = computed(() => this.stepIndex() >= this.totalSteps());
  readonly currentExercise = computed<ExerciseSets | null>(
    () => this.exercises()[this.stepIndex()] ?? null,
  );
  readonly currentSet = computed<SetEntry | null>(
    () => this.currentExercise()?.sets[this.setIndex()] ?? null,
  );

  /** Progression globale en pourcentage (barre de l'en-tête). */
  readonly progressPct = computed(() => {
    const total = this.totalSteps() + 1; // + l'étape de ressenti
    return Math.round((Math.min(this.stepIndex(), total) / total) * 100);
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('scheduledId');
    if (!id) {
      this.state.set('error');
      return;
    }
    this.load(id);
  }

  private load(id: string): void {
    this.state.set('loading');
    const day = this.today();
    // La séance planifiée n'a pas d'endpoint unitaire côté portail : on la retrouve dans la
    // fenêtre autour d'aujourd'hui, ce qui couvre aussi une séance rattrapée la veille.
    this.portal.ppScheduled(this.shift(day, -7), this.shift(day, 1)).subscribe({
      next: (list) => {
        const found = list.find((s) => s.id === id) ?? null;
        if (!found) {
          this.state.set('error');
          return;
        }
        this.session.set(found);
        this.portal.ppPrescription(id).subscribe({
          next: (rx) => {
            this.exercises.set(this.buildSets(rx));
            this.state.set('ready');
          },
          error: () => this.state.set('error'),
        });
      },
      error: () => this.state.set('error'),
    });
  }

  // --- Saisie d'une série ---------------------------------------------------

  /**
   * Ajuste la charge par pas de 2,5 kg. Jamais négative, et arrondie au pas : partir d'une
   * prescription à 62,3 kg ne doit pas produire une suite de valeurs impossibles à recharger.
   */
  adjustCharge(delta: number): void {
    const set = this.currentSet();
    if (!set) return;
    const base = set.chargeKg ?? 0;
    const next = Math.round(((base + delta * CHARGE_STEP_KG) / CHARGE_STEP_KG)) * CHARGE_STEP_KG;
    set.chargeKg = Math.max(0, Number(next.toFixed(1)));
    this.touch();
  }

  adjustReps(delta: number): void {
    const set = this.currentSet();
    if (!set) return;
    set.repsDone = Math.max(0, (set.repsDone ?? 0) + delta);
    this.touch();
  }

  adjustRir(delta: number): void {
    const set = this.currentSet();
    if (!set) return;
    set.rirDone = Math.max(0, (set.rirDone ?? 0) + delta);
    this.touch();
  }

  /** La série précédente existe-t-elle (recopie possible) ? */
  canRepeatPrevious(): boolean {
    return this.setIndex() > 0;
  }

  /**
   * Recopie charge / reps / RIR de la série précédente. La série suivante démarre déjà sur ces
   * valeurs ; le bouton sert à revenir dessus après un ajustement qu'on regrette.
   */
  repeatPrevious(): void {
    const exercise = this.currentExercise();
    const previous = exercise?.sets[this.setIndex() - 1];
    const current = this.currentSet();
    if (!previous || !current) return;
    current.chargeKg = previous.chargeKg;
    current.repsDone = previous.repsDone;
    current.rirDone = previous.rirDone;
    this.touch();
  }

  /** Valide la série courante et avance : série suivante, puis exercice suivant, puis ressenti. */
  validateSet(): void {
    const exercise = this.currentExercise();
    const current = this.currentSet();
    if (!exercise || !current) return;
    current.done = true;

    const next = exercise.sets[this.setIndex() + 1];
    if (next) {
      // Une série ressemble à la précédente : on la pré-remplit plutôt que de repartir de zéro.
      if (!next.done) {
        next.chargeKg = current.chargeKg;
        next.repsDone = current.repsDone;
        next.rirDone = current.rirDone;
      }
      this.setIndex.set(this.setIndex() + 1);
    } else {
      this.stepIndex.set(this.stepIndex() + 1);
      this.setIndex.set(0);
    }
    this.touch();
  }

  /** Revient sur la série ou l'exercice précédent (rien à l'ouverture). */
  goBack(): void {
    if (this.setIndex() > 0) {
      this.setIndex.set(this.setIndex() - 1);
      return;
    }
    if (this.stepIndex() > 0) {
      const previousIndex = this.stepIndex() - 1;
      this.stepIndex.set(previousIndex);
      this.setIndex.set(Math.max(0, (this.exercises()[previousIndex]?.sets.length ?? 1) - 1));
    }
  }

  /** Passe l'exercice courant (blessure, matériel occupé) sans le marquer réalisé. */
  skipExercise(): void {
    this.stepIndex.set(this.stepIndex() + 1);
    this.setIndex.set(0);
  }

  /** Saute directement à un exercice depuis le fil d'étapes. */
  goToStep(index: number): void {
    this.stepIndex.set(index);
    this.setIndex.set(0);
  }

  // --- Fin de séance --------------------------------------------------------

  /**
   * Enregistre les séries saisies (recalcul e1RM côté serveur) puis le retour de séance.
   * Une série jamais validée et laissée vide n'est pas envoyée : mieux vaut un trou qu'une
   * donnée inventée dans l'historique de charge.
   */
  finish(): void {
    const session = this.session();
    if (!session || this.saving()) return;
    this.saving.set(true);

    const results: StrengthResultEntry[] = [];
    for (const exercise of this.exercises()) {
      exercise.sets.forEach((set, i) => {
        if (set.done && set.chargeKg != null && set.repsDone != null) {
          results.push({
            exerciseId: exercise.exerciseId, setNumber: i + 1,
            chargeKg: set.chargeKg, repsDone: set.repsDone, rirDone: set.rirDone,
          });
        }
      });
    }

    const saveFeedback = () => {
      this.portal
        .ppFeedback(session.id, {
          completed: true, sessionRpe: this.rpe(), fatigue: this.fatigue(), pain: this.pain(), comment: null,
        })
        .subscribe({
          next: () => {
            this.saving.set(false);
            this.celebrateAndLeave();
          },
          error: () => {
            this.saving.set(false);
            this.toast.error('Enregistrement impossible. Vérifie ta connexion et réessaie.');
          },
        });
    };

    if (!results.length) {
      saveFeedback();
      return;
    }
    this.portal.ppResults(session.id, results).subscribe({
      next: (updates) => {
        if (updates.length) {
          this.toast.success(`e1RM mis à jour : ${updates[0].e1rmKg} kg`);
        }
        this.portal.ppProgression(session.id).subscribe({
          next: (p) => this.progression.set(p),
          error: () => this.progression.set(null),
        });
        saveFeedback();
      },
      // Le ressenti compte plus que le e1RM : un échec de calcul ne doit pas perdre la forme.
      error: () => saveFeedback(),
    });
  }

  private celebrateAndLeave(): void {
    this.portal.feedbackStreak().subscribe({
      next: (streak) => this.celebrate(streak),
      error: () => this.celebrate(0),
    });
  }

  private celebrate(streak: number): void {
    this.streak.set(streak);
    this.finished.set(true);
    this.celebration.celebrate({
      title: 'Séance validée',
      detail: streak > 1 ? `${streak}e retour d'affilée 🔥` : undefined,
      emoji: '💪',
    });
  }

  /** Retour à « Aujourd'hui » depuis l'écran de clôture (plus rien à sauvegarder). */
  leave(): void {
    this.router.navigate(['/athlete/today']);
  }

  /** Quitter en cours de séance : les séries saisies ne sont pas encore parties. */
  async quit(): Promise<void> {
    const started = this.exercises().some((e) => e.sets.some((s) => s.done));
    if (started && !this.isDebrief() && !this.finished()) {
      const ok = await this.confirm.ask({
        title: 'Quitter la séance ?',
        message: 'Les séries déjà validées ne seront pas enregistrées.',
        confirmLabel: 'Quitter',
        danger: true,
      });
      if (!ok) return;
    }
    this.router.navigate(['/athlete/today']);
  }

  // --- Construction ---------------------------------------------------------

  /** Pré-remplit les séries depuis la prescription + capture les fourchettes (lecture seule). */
  private buildSets(rx: StrengthPrescriptionView): ExerciseSets[] {
    const list: ExerciseSets[] = [];
    for (const block of rx.calculated?.blocks ?? []) {
      for (const ex of block.exercises) {
        const presc = ex.item.prescription;
        // Au moins une série : un exercice prescrit « 0 série » n'a pas de sens et laisserait
        // un exercice sans aucun écran de saisie au milieu du parcours.
        const count = Math.max(1, presc.sets ?? 3);
        const sets: SetEntry[] = Array.from({ length: count }, () => ({
          chargeKg: ex.charge.kgMin ?? presc.chargeKgMin ?? null,
          repsDone: presc.repsFixed ?? presc.repsMin ?? null,
          rirDone: presc.rirMin ?? null,
          done: false,
        }));
        const isRir = presc.effortRefType === 'RIR' || presc.effortRefType === 'RIR_RANGE';
        list.push({
          exerciseId: ex.item.exerciseId,
          name: ex.item.exerciseName,
          sets,
          rx: {
            chargeKgMin: ex.charge.kgMin ?? presc.chargeKgMin ?? null,
            chargeKgMax: ex.charge.kgMax ?? presc.chargeKgMax ?? null,
            repsMin: presc.repsMin ?? null,
            repsMax: presc.repsMax ?? null,
            repsFixed: presc.repsFixed ?? null,
            effortKind: presc.effortRefType ? (isRir ? 'RIR' : 'RPE') : null,
            effortMin: isRir ? presc.rirMin ?? null : presc.rpeMin ?? null,
            effortMax: isRir ? presc.rirMax ?? null : presc.rpeMax ?? null,
          },
        });
      }
    }
    return list;
  }

  /** Notifie le signal après mutation en place (OnPush). */
  private touch(): void {
    this.exercises.set([...this.exercises()]);
  }

  private today(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private shift(iso: string, days: number): string {
    const d = new Date(iso + 'T00:00:00');
    d.setDate(d.getDate() + days);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  /** « 62,5 » — virgule décimale française, entier sans décimale inutile. */
  formatKg(value: number | null): string {
    if (value == null) return '—';
    return Number.isInteger(value) ? String(value) : value.toFixed(1).replace('.', ',');
  }
}
