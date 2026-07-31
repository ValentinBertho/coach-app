import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { CelebrationService } from '../../core/services/celebration.service';
import { ToastService } from '../../core/services/toast.service';
import {
  Progression, ScheduledStrength, StrengthPrescriptionView, StrengthResultEntry,
} from '../../core/models/strength.model';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { CelebrationOverlayComponent } from '../../shared/components/celebration/celebration-overlay.component';
import {
  EffortBadgeComponent, type EffortKind,
  PainFatigueSelectorComponent, RangePrescriptionPillComponent,
} from '../../shared/components/physiology';
import { RpeScaleSelectorComponent } from '../../shared/components/rpe-scale-selector/rpe-scale-selector.component';

/** Une série à saisir. */
interface SetEntry { chargeKg: number | null; repsDone: number | null; rirDone: number | null; done: boolean; }

/** Prescription d'un exercice (fourchettes), en lecture seule. */
interface ExerciseRx {
  chargeKgMin: number | null;
  chargeKgMax: number | null;
  repsMin: number | null;
  repsMax: number | null;
  repsFixed: number | null;
  effortKind: EffortKind | null;
  effortMin: number | null;
  effortMax: number | null;
  restSecMin: number | null;
}
interface Exercise { exerciseId: string; name: string; sets: SetEntry[]; rx: ExerciseRx; }

type State = 'loading' | 'ready' | 'error';

/**
 * Mode séance de force plein écran — un exercice à la fois.
 *
 * La saisie vivait dans la carte « Aujourd'hui » : un tableau charge/reps/RIR par série et par
 * exercice, soit une trentaine de champs sur 375 px, debout, en sueur, entre deux séries. Ce
 * n'était plus une carte, c'était un formulaire posé au milieu de l'écran d'accueil.
 *
 * Ici, un seul exercice occupe l'écran, la série courante est en gros chiffres, la charge se
 * règle au pouce par pas de 2,5 kg (le pas réel des disques), et « série 2/4 » dit toujours où
 * l'on en est. Le ressenti de fin reprend l'invariant : la forme est fatigue + douleur, le RPE
 * ne mesure que la difficulté.
 */
@Component({
  selector: 'app-strength-session',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, RangePrescriptionPillComponent, EffortBadgeComponent,
    PainFatigueSelectorComponent, RpeScaleSelectorComponent, CelebrationOverlayComponent,
  ],
  templateUrl: './strength-session.component.html',
  styleUrl: './strength-session.component.scss',
})
export class StrengthSessionComponent implements OnInit {
  /** Identifiant de la séance planifiée (paramètre de route). */
  readonly id = input.required<string>();

  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly celebration = inject(CelebrationService);
  private readonly router = inject(Router);

  /** Pas de réglage de charge : celui des disques, pas celui d'un clavier. */
  private static readonly CHARGE_STEP_KG = 2.5;

  readonly state = signal<State>('loading');
  readonly session = signal<ScheduledStrength | null>(null);
  readonly exercises = signal<Exercise[]>([]);
  /** Index de l'exercice affiché : l'écran n'en montre qu'un. */
  readonly index = signal(0);
  readonly progression = signal<Progression | null>(null);

  // Ressenti de fin de séance.
  readonly rpe = signal<number | null>(null);
  readonly fatigue = signal<number | null>(null);
  readonly pain = signal<number | null>(null);
  /** Écran de clôture : on ne demande le ressenti qu'une fois les séries passées.  */
  readonly reviewing = signal(false);
  readonly saving = signal(false);

  readonly current = computed<Exercise | null>(() => this.exercises()[this.index()] ?? null);
  readonly total = computed(() => this.exercises().length);
  readonly isLast = computed(() => this.index() >= this.total() - 1);

  /** Séries cochées sur l'ensemble de la séance : alimente la barre de progression. */
  readonly doneSets = computed(() =>
    this.exercises().reduce((n, ex) => n + ex.sets.filter((s) => s.done).length, 0));
  readonly totalSets = computed(() =>
    this.exercises().reduce((n, ex) => n + ex.sets.length, 0));
  readonly progressPct = computed(() =>
    this.totalSets() ? Math.round((this.doneSets() / this.totalSets()) * 100) : 0);

  ngOnInit(): void {
    this.portal.ppSession(this.id()).subscribe({
      next: (s) => {
        this.session.set(s);
        this.rpe.set(null);
        this.fatigue.set(s.sessionFatigue);
        this.pain.set(s.sessionPain);
      },
      error: () => this.state.set('error'),
    });
    this.portal.ppPrescription(this.id()).subscribe({
      next: (rx) => { this.exercises.set(this.buildSets(rx)); this.state.set('ready'); },
      error: () => this.state.set('error'),
    });
  }

  /** Pré-remplit les séries avec la prescription : l'athlète confirme, il ne saisit pas. */
  private buildSets(rx: StrengthPrescriptionView): Exercise[] {
    const list: Exercise[] = [];
    for (const b of rx.calculated?.blocks ?? []) {
      for (const ex of b.exercises) {
        const presc = ex.item.prescription;
        const count = presc.sets ?? 3;
        const isRir = presc.effortRefType === 'RIR' || presc.effortRefType === 'RIR_RANGE';
        list.push({
          exerciseId: ex.item.exerciseId,
          name: ex.item.exerciseName,
          sets: Array.from({ length: count }, () => ({
            chargeKg: ex.charge.kgMin ?? presc.chargeKgMin ?? null,
            repsDone: presc.repsFixed ?? presc.repsMin ?? null,
            rirDone: presc.rirMin ?? null,
            done: false,
          })),
          rx: {
            chargeKgMin: ex.charge.kgMin ?? presc.chargeKgMin ?? null,
            chargeKgMax: ex.charge.kgMax ?? presc.chargeKgMax ?? null,
            repsMin: presc.repsMin ?? null,
            repsMax: presc.repsMax ?? null,
            repsFixed: presc.repsFixed ?? null,
            effortKind: presc.effortRefType ? (isRir ? 'RIR' : 'RPE') : null,
            effortMin: isRir ? presc.rirMin ?? null : presc.rpeMin ?? null,
            effortMax: isRir ? presc.rirMax ?? null : presc.rpeMax ?? null,
            restSecMin: presc.restSecMin ?? null,
          },
        });
      }
    }
    return list;
  }

  /** Notifie le signal après mutation interne (OnPush). */
  private touch(): void { this.exercises.set([...this.exercises()]); }

  // --- Saisie d'une série ---------------------------------------------------------------

  /** Règle la charge par pas de 2,5 kg — jamais en dessous de zéro. */
  bumpCharge(set: SetEntry, steps: number): void {
    const next = (set.chargeKg ?? 0) + steps * StrengthSessionComponent.CHARGE_STEP_KG;
    set.chargeKg = Math.max(0, Math.round(next * 10) / 10);
    this.touch();
  }

  bumpReps(set: SetEntry, delta: number): void {
    set.repsDone = Math.max(0, (set.repsDone ?? 0) + delta);
    this.touch();
  }

  /**
   * Valide la série et passe à la suivante. La plupart des séries ne varient pas : la suivante
   * hérite des valeurs de celle qu'on vient de faire, l'athlète ne corrige que l'exception.
   */
  validateSet(ex: Exercise, i: number): void {
    const set = ex.sets[i];
    set.done = true;
    const next = ex.sets[i + 1];
    if (next && !next.done) {
      next.chargeKg = set.chargeKg;
      next.repsDone = set.repsDone;
      next.rirDone = set.rirDone;
    }
    this.touch();
  }

  /** Rouvre une série cochée par erreur. */
  reopenSet(set: SetEntry): void { set.done = false; this.touch(); }

  /** Recopie explicite depuis la série précédente (quand on a corrigé et qu'on veut revenir). */
  copyPrevious(ex: Exercise, i: number): void {
    const previous = ex.sets[i - 1];
    const set = ex.sets[i];
    if (!previous || !set) return;
    set.chargeKg = previous.chargeKg;
    set.repsDone = previous.repsDone;
    set.rirDone = previous.rirDone;
    this.touch();
  }

  /** Première série non validée d'un exercice : celle qui est en gros à l'écran. */
  activeSetIndex(ex: Exercise): number {
    const i = ex.sets.findIndex((s) => !s.done);
    return i < 0 ? ex.sets.length - 1 : i;
  }

  // --- Navigation entre exercices --------------------------------------------------------

  goPrev(): void { this.index.update((i) => Math.max(0, i - 1)); }

  goNext(): void {
    if (this.isLast()) { this.reviewing.set(true); return; }
    this.index.update((i) => i + 1);
  }

  goTo(i: number): void { this.index.set(i); this.reviewing.set(false); }

  /** Retour à « Aujourd'hui » : la séance plein écran est une parenthèse, pas une destination. */
  exit(): void { void this.router.navigate(['/athlete/today']); }

  // --- Clôture ---------------------------------------------------------------------------

  /** Durée de repos prescrite, en texte court (« 2 min », « 90 s »). */
  restLabel(rx: ExerciseRx): string | null {
    const s = rx.restSecMin;
    if (!s) return null;
    return s >= 120 && s % 60 === 0 ? `${s / 60} min` : `${s} s`;
  }

  /**
   * Envoie les séries réalisées (recalcul e1RM côté serveur) puis le ressenti de séance.
   * L'ordre compte : le e1RM se calcule à partir des séries, pas du ressenti.
   */
  submit(): void {
    const s = this.session();
    if (!s || this.saving()) return;
    this.saving.set(true);

    const results: StrengthResultEntry[] = [];
    for (const ex of this.exercises()) {
      ex.sets.forEach((set, i) => {
        // Une série non cochée n'a pas été faite : on ne la déclare pas.
        if (set.done && set.chargeKg != null && set.repsDone != null) {
          results.push({
            exerciseId: ex.exerciseId, setNumber: i + 1,
            chargeKg: set.chargeKg, repsDone: set.repsDone, rirDone: set.rirDone,
          });
        }
      });
    }

    const finish = () => {
      this.portal
        .ppFeedback(s.id, { completed: true, sessionRpe: this.rpe(), fatigue: this.fatigue(), pain: this.pain(), comment: null })
        .subscribe({
          next: (updated) => {
            this.session.set(updated);
            this.saving.set(false);
            this.celebration.fire('Séance validée', `${this.doneSets()} séries · ${this.total()} exercices`);
            this.toast.success('Renforcement enregistré');
            this.portal.ppProgression(s.id).subscribe({ next: (p) => this.progression.set(p) });
          },
          error: () => { this.saving.set(false); this.toast.error('Enregistrement impossible.'); },
        });
    };

    if (!results.length) { finish(); return; }
    this.portal.ppResults(s.id, results).subscribe({
      next: (updates) => {
        if (updates.length) this.toast.info(`e1RM mis à jour : ${updates[0].e1rmKg} kg`);
        finish();
      },
      error: () => { this.saving.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }
}
