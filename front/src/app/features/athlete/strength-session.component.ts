import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AthletePortalService, StrengthPrescriptionView } from '../../core/services/athlete-portal.service';
import { CelebrationService } from '../../core/services/celebration.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import {
  Progression, ScheduledStrength, StrengthResultEntry, VolumeType,
} from '../../core/models/strength.model';
import {
  effectiveVolumeType, sideLabel, volumePill, type VolumePill,
} from '../../core/utils/strength-volume';
import { IconComponent } from '../../shared/components/icon/icon.component';
import {
  EffortBadgeComponent,
  type EffortKind,
  PainFatigueSelectorComponent,
  RangePrescriptionPillComponent,
} from '../../shared/components/physiology';
import { RpeScaleSelectorComponent } from '../../shared/components/rpe-scale-selector/rpe-scale-selector.component';

/**
 * Une série saisie par l'athlète. `done` = validée dans le parcours guidé.
 *
 * <p>Trois volumes possibles et un seul rempli : l'exercice est prescrit en répétitions, en
 * durée ou en distance — jamais dans deux unités à la fois.</p>
 */
interface SetEntry {
  chargeKg: number | null;
  repsDone: number | null;
  durationSecDone: number | null;
  distanceMDone: number | null;
  rirDone: number | null;
  done: boolean;
}

/** Fourchettes prescrites d'un exercice, affichées en lecture seule au-dessus de la saisie. */
interface ExerciseRx {
  chargeKgMin: number | null;
  chargeKgMax: number | null;
  /** Volume prescrit, déjà lu dans son unité (« Reps 8 », « Durée 30–45 s »). */
  volume: VolumePill | null;
  effortKind: EffortKind | null;
  effortMin: number | null;
  effortMax: number | null;
}

interface ExerciseSets {
  exerciseId: string;
  name: string;
  sets: SetEntry[];
  rx: ExerciseRx;
  /** Ce que compte la série : décide du champ de saisie proposé à l'athlète. */
  volumeType: VolumeType;
  /** « Par côté », « Alterné G / D » — absent en bilatéral. */
  side: string | null;
  /** Consigne du coach sur cet exercice, s'il en a laissé une. */
  notes: string | null;
}

type State = 'loading' | 'ready' | 'error';

/** Incrément de charge : le plus petit disque de la plupart des salles. */
const CHARGE_STEP_KG = 2.5;
/** Incrément de durée : 5 secondes, le grain d'un gainage ou d'un porté. */
const DURATION_STEP_SEC = 5;
/** Incrément de distance : 5 mètres, le grain d'un aller de porté du fermier. */
const DISTANCE_STEP_M = 5;

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

  /**
   * Ajuste le volume réalisé dans l'unité de l'exercice : une répétition, cinq secondes de
   * gainage, cinq mètres de porté. Un seul bouton pour les trois — l'athlète voit l'unité, pas
   * le champ qui la stocke.
   */
  adjustVolume(delta: number): void {
    const set = this.currentSet();
    if (!set) return;
    switch (this.currentExercise()?.volumeType) {
      case 'DUREE':
        set.durationSecDone = Math.max(0, (set.durationSecDone ?? 0) + delta * DURATION_STEP_SEC);
        break;
      case 'DISTANCE':
        set.distanceMDone = Math.max(0, (set.distanceMDone ?? 0) + delta * DISTANCE_STEP_M);
        break;
      default:
        set.repsDone = Math.max(0, (set.repsDone ?? 0) + delta);
    }
    this.touch();
  }

  /** Volume réalisé de la série en cours, dans son unité — « — » tant que rien n'est saisi. */
  volumeDone(set: SetEntry): string {
    const value = this.volumeValue(set);
    return value == null ? '—' : String(value);
  }

  /** Libellé et unité du champ de volume, selon ce qui est prescrit. */
  volumeFieldLabel(): string {
    switch (this.currentExercise()?.volumeType) {
      case 'DUREE': return 'Durée';
      case 'DISTANCE': return 'Distance';
      default: return 'Répétitions';
    }
  }
  volumeUnit(): string {
    switch (this.currentExercise()?.volumeType) {
      case 'DUREE': return 's';
      case 'DISTANCE': return 'm';
      default: return '';
    }
  }
  /** Pas d'incrément affiché sur les boutons (« +5 » pour une durée, rien pour une rep). */
  volumeStepLabel(): string {
    switch (this.currentExercise()?.volumeType) {
      case 'DUREE': return String(DURATION_STEP_SEC);
      case 'DISTANCE': return String(DISTANCE_STEP_M);
      default: return '';
    }
  }

  private volumeValue(set: SetEntry, type?: VolumeType): number | null {
    switch (type ?? this.currentExercise()?.volumeType) {
      case 'DUREE': return set.durationSecDone;
      case 'DISTANCE': return set.distanceMDone;
      default: return set.repsDone;
    }
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
    copyVolume(previous, current);
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
        copyVolume(current, next);
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
        // Une série compte dès qu'elle porte son volume — en reps, en secondes ou en mètres.
        // Le critère « des répétitions » aurait jeté toutes les séries d'un gainage.
        const volume = this.volumeValue(set, exercise.volumeType);
        if (set.done && volume != null) {
          results.push({
            exerciseId: exercise.exerciseId, setNumber: i + 1,
            chargeKg: set.chargeKg,
            repsDone: set.repsDone,
            durationSecDone: set.durationSecDone,
            distanceMDone: set.distanceMDone,
            rirDone: set.rirDone,
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
        const volumeType = effectiveVolumeType(presc, block.block.format);
        // Chaque série démarre sur le bas de la fourchette prescrite, dans la bonne unité :
        // proposer « 30 répétitions » sur un gainage de 30 s serait une consigne fausse.
        const sets: SetEntry[] = Array.from({ length: count }, () => ({
          chargeKg: ex.charge.kgMin ?? presc.chargeKgMin ?? null,
          repsDone: volumeType === 'REPS' ? presc.repsFixed ?? presc.repsMin ?? null : null,
          durationSecDone: volumeType === 'DUREE' ? presc.durationSec ?? null : null,
          distanceMDone: volumeType === 'DISTANCE' ? presc.distanceM ?? null : null,
          rirDone: presc.rirMin ?? null,
          done: false,
        }));
        const isRir = presc.effortRefType === 'RIR' || presc.effortRefType === 'RIR_RANGE';
        list.push({
          exerciseId: ex.item.exerciseId,
          name: ex.item.exerciseName,
          sets,
          volumeType,
          side: sideLabel(presc),
          notes: ex.item.coachNotes ?? null,
          rx: {
            chargeKgMin: ex.charge.kgMin ?? presc.chargeKgMin ?? null,
            chargeKgMax: ex.charge.kgMax ?? presc.chargeKgMax ?? null,
            volume: volumePill(presc, block.block.format),
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

  /**
   * Résumé d'une série validée pour le récapitulatif de fin : « 60×8 », « 30 s », « 20 m ».
   * La charge ne s'écrit que si elle a été saisie — un gainage au poids du corps n'en a pas.
   */
  recapChip(exercise: ExerciseSets, set: SetEntry): string {
    const charge = set.chargeKg ? `${this.formatKg(set.chargeKg)} kg` : '';
    switch (exercise.volumeType) {
      case 'DUREE':
        return [charge, `${set.durationSecDone ?? '—'} s`].filter(Boolean).join(' × ');
      case 'DISTANCE':
        return [charge, `${set.distanceMDone ?? '—'} m`].filter(Boolean).join(' × ');
      default:
        return `${charge || '—'} × ${set.repsDone ?? '—'}`;
    }
  }

  /** « 62,5 » — virgule décimale française, entier sans décimale inutile. */
  formatKg(value: number | null): string {
    if (value == null) return '—';
    return Number.isInteger(value) ? String(value) : value.toFixed(1).replace('.', ',');
  }
}

/**
 * Recopie charge, volume et RIR d'une série sur une autre — les trois unités de volume comprises,
 * dont une seule est renseignée. Recopier les seules répétitions perdait la série d'un gainage.
 */
function copyVolume(from: SetEntry, to: SetEntry): void {
  to.chargeKg = from.chargeKg;
  to.repsDone = from.repsDone;
  to.durationSecDone = from.durationSecDone;
  to.distanceMDone = from.distanceMDone;
  to.rirDone = from.rirDone;
}
