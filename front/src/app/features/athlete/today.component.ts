import { ChangeDetectionStrategy, Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  STEP_TYPE_LABELS,
  WORKOUT_TYPE_LABELS,
  Workout,
  awaitsFeedback,
  needsFeedback,
} from '../../core/models/workout.model';
import { AthletePortalService, StrengthPrescriptionView } from '../../core/services/athlete-portal.service';
import { Progression, ScheduledStrength, StrengthResultEntry } from '../../core/models/strength.model';
import { WorkoutPrescription } from '../../core/models/course.model';
import { CoursePrescriptionViewComponent } from '../../shared/components/course-prescription-view/course-prescription-view.component';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InstallButtonComponent } from '../../shared/components/install-button/install-button.component';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';
import { PushButtonComponent } from '../../shared/components/push-button/push-button.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';
import {
  EffortBadgeComponent,
  type EffortKind,
  IntensityZoneBadgeComponent,
  type IntensityZone as ZoneNum,
  PainFatigueSelectorComponent,
  RangePrescriptionPillComponent,
} from '../../shared/components/physiology';
import { WorkoutFeedbackSheetComponent } from '../../shared/components/workout-feedback-sheet/workout-feedback-sheet.component';
import { HelpService } from '../help/help.service';
import { HelpHintComponent } from '../help/help-hint.component';
import { RPE_SCALE, rpeLabel } from '../../shared/components/rpe-scale';

interface SetEntry { chargeKg: number | null; repsDone: number | null; rirDone: number | null; }

/** Résumé de la prescription d'un exercice (fourchettes), pour l'affichage lecture seule. */
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
interface ExerciseSets { exerciseId: string; name: string; sets: SetEntry[]; rx: ExerciseRx; }

/**
 * Carte de renforcement du jour. Chaque séance de force planifiée a sa propre
 * saisie de séries, son retour (RPE/fatigue/douleur) et sa progression — pour
 * supporter plusieurs séances de force le même jour (double séance).
 */
interface StrengthCard {
  session: ScheduledStrength;
  rx: StrengthPrescriptionView | null;
  exercises: ExerciseSets[];
  progression: Progression | null;
  sRpe: number | null;
  sFatigue: number | null;
  sPain: number | null;
}

type State = 'loading' | 'ready' | 'error';

/**
 * Portail athlète — « Aujourd'hui » (mobile-first PWA).
 * Refonte blueprint : carte héro de la séance, zones d'intensité explicites,
 * retour rapide (RPE + fatigue + douleur) dans un bottom sheet. Préserve le
 * câblage services et la file offline.
 *
 * Supporte **plusieurs séances course ET force le même jour** (double séance) :
 * chaque séance est une carte autonome avec son propre retour.
 */
@Component({
  selector: 'app-today',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent,
    FormsModule, RouterLink,
    LogoComponent, InstallButtonComponent, OfflineBannerComponent, PushButtonComponent, NotificationBellComponent,
    IntensityZoneBadgeComponent, RangePrescriptionPillComponent, EffortBadgeComponent,
    PainFatigueSelectorComponent, WorkoutFeedbackSheetComponent,
    CoursePrescriptionViewComponent, HelpHintComponent,
  ],
  templateUrl: './today.component.html',
  styleUrl: './today.component.scss',
})
export class TodayComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly help = inject(HelpService);

  /** Feuille de ressenti partagée : « Aujourd'hui », l'agenda et l'historique ouvrent la même. */
  private readonly feedbackSheet = viewChild(WorkoutFeedbackSheetComponent);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  readonly rpeScale = RPE_SCALE;

  /** Repère verbal CR10 de la valeur choisie (échelle partagée avec l'éditeur coach). */
  rpeLabel(value: number | null | undefined): string { return rpeLabel(value); }

  /**
   * Recopie charge / reps / RIR de la série précédente. Sur mobile, une séance de 4 exercices
   * × 4 séries demande 48 frappes ; la plupart des séries ne varient pas.
   */
  repeatPreviousSet(exercise: ExerciseSets, index: number): void {
    const previous = exercise.sets[index - 1];
    const current = exercise.sets[index];
    if (!previous || !current) return;
    current.chargeKg = previous.chargeKg;
    current.repsDone = previous.repsDone;
    current.rirDone = previous.rirDone;
    this.strengthCards.set([...this.strengthCards()]);
  }

  readonly state = signal<State>('loading');
  /** Toutes les séances de course du jour (double séance possible). */
  readonly workouts = signal<Workout[]>([]);
  /** Prescription course en fourchettes par séance (clé = id séance). */
  readonly courseRx = signal<Record<string, WorkoutPrescription | null>>({});
  readonly nextRace = signal<import('../../core/models/race.model').RaceObjective | null>(null);
  readonly user = this.auth.currentUser;

  /**
   * Séances des 7 derniers jours encore non clôturées. Sans ce rattrapage, un ressenti oublié
   * la veille est perdu pour toujours — c'est la première fuite de données du produit.
   */
  readonly pending = signal<Workout[]>([]);

  /** Toutes les séances de force du jour (double séance possible). */
  readonly strengthCards = signal<StrengthCard[]>([]);

  /** L'athlète a-t-il des allures de travail (VDOT) ? Sinon on l'invite à saisir une perf. */
  readonly hasPaces = signal(true);

  ngOnInit(): void {
    this.load();
    this.loadPending();
    this.loadStrength();
    this.portal.nextRace().subscribe({ next: (r) => this.nextRace.set(r) });
    this.portal.vdot().subscribe({
      next: (v) => this.hasPaces.set((v.paces?.length ?? 0) > 0),
      error: () => this.hasPaces.set(true),
    });
  }

  /** La prescription course d'une séance a-t-elle du contenu calculé à afficher ? */
  rxHasContent(workoutId: string): boolean {
    const c = this.courseRx()[workoutId]?.calculated;
    return !!c && (c.warmup.length + c.main.length + c.cooldown.length) > 0;
  }
  rxFor(workoutId: string): WorkoutPrescription | null {
    return this.courseRx()[workoutId] ?? null;
  }
  /** Une séance attend-elle encore un retour ? (PLANNED ou PARTIAL) */
  needsFeedback(w: Workout): boolean { return needsFeedback(w); }

  /** Date lisible d'une séance en retard : « mardi 28 juillet ». */
  dayLabel(iso: string): string {
    return new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
      .format(new Date(iso + 'T00:00:00'));
  }

  loadPending(): void {
    this.portal.pendingFeedback(7).subscribe({
      next: (list) => this.pending.set(list),
      error: () => this.pending.set([]),
    });
  }

  loadStrength(): void {
    const day = new Date().toISOString().slice(0, 10);
    this.portal.ppScheduled(day, day).subscribe({
      next: (list) => {
        const cards: StrengthCard[] = list.map((session) => ({
          session, rx: null, exercises: [], progression: null,
          sRpe: null, sFatigue: null, sPain: null,
        }));
        this.strengthCards.set(cards);
        for (const card of cards) {
          this.portal.ppPrescription(card.session.id).subscribe({
            next: (p) => {
              card.rx = p;
              card.exercises = this.buildSets(p);
              // Re-set pour notifier le signal (OnPush) après l'arrivée asynchrone.
              this.strengthCards.set([...this.strengthCards()]);
            },
          });
        }
      },
    });
  }

  /** Pré-remplit les séries à saisir + capture les fourchettes prescrites (lecture seule). */
  private buildSets(rx: StrengthPrescriptionView): ExerciseSets[] {
    const list: ExerciseSets[] = [];
    for (const b of rx.calculated?.blocks ?? []) {
      for (const ex of b.exercises) {
        const presc = ex.item.prescription;
        const count = presc.sets ?? 3;
        const sets: SetEntry[] = Array.from({ length: count }, () => ({
          chargeKg: ex.charge.kgMin ?? presc.chargeKgMin ?? null,
          repsDone: presc.repsFixed ?? presc.repsMin ?? null,
          rirDone: presc.rirMin ?? null,
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

  /** Sélection d'un RPE de séance de force (mutation + notification OnPush). */
  setStrengthRpe(card: StrengthCard, n: number): void {
    card.sRpe = n;
    this.strengthCards.set([...this.strengthCards()]);
  }

  submitStrength(card: StrengthCard, completed: boolean): void {
    const s = card.session;

    // 1. Séries réalisées → recalcul automatique du e1RM.
    const results: StrengthResultEntry[] = [];
    for (const ex of card.exercises) {
      ex.sets.forEach((set, i) => {
        if (set.chargeKg != null && set.repsDone != null) {
          results.push({
            exerciseId: ex.exerciseId, setNumber: i + 1,
            chargeKg: set.chargeKg, repsDone: set.repsDone, rirDone: set.rirDone,
          });
        }
      });
    }
    if (results.length) {
      this.portal.ppResults(s.id, results).subscribe({
        next: (updates) => {
          if (updates.length) {
            this.toast.success(`e1RM mis à jour : ${updates[0].e1rmKg} kg`);
          }
          this.portal.ppProgression(s.id).subscribe((p) => {
            card.progression = p;
            this.strengthCards.set([...this.strengthCards()]);
          });
        },
      });
    }

    // 2. Retour de séance global.
    this.portal
      .ppFeedback(s.id, { completed, sessionRpe: card.sRpe, fatigue: card.sFatigue, pain: card.sPain, comment: null })
      .subscribe({
        next: (updated) => {
          card.session = updated;
          this.strengthCards.set([...this.strengthCards()]);
          this.toast.success('Renforcement enregistré');
        },
      });
  }

  load(): void {
    this.state.set('loading');
    this.portal.today().subscribe({
      next: (list) => {
        this.workouts.set(list);
        this.courseRx.set({});
        for (const w of list) {
          this.portal.workoutPrescription(w.id).subscribe({
            next: (p) => this.courseRx.set({ ...this.courseRx(), [w.id]: p }),
            error: () => this.courseRx.set({ ...this.courseRx(), [w.id]: null }),
          });
        }
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  /** 'Z3' → 3 pour le badge de zone (sécurisé). */
  zoneNum(zone: string | null): ZoneNum | null {
    if (!zone) return null;
    const n = Number(zone.replace(/\D/g, ''));
    return n >= 1 && n <= 5 ? (n as ZoneNum) : null;
  }

  /** Cible d'un pas course (distance ou durée) en texte tabulaire. */
  stepTarget(distanceM: number | null, durationS: number | null): string {
    if (distanceM) return distanceM >= 1000 ? `${(distanceM / 1000).toFixed(1)} km` : `${distanceM} m`;
    if (durationS) {
      const m = Math.floor(durationS / 60);
      const s = durationS % 60;
      return m ? `${m}:${s.toString().padStart(2, '0')}` : `${s} s`;
    }
    return '';
  }

  /** Ouvre la feuille de ressenti — séance du jour comme séance en retard, même parcours. */
  openFeedback(w: Workout): void {
    this.feedbackSheet()?.openFor(w);
  }

  /**
   * Une séance notée quitte le bandeau de rattrapage : le compteur doit refléter ce qu'il
   * reste à faire, y compris quand la sauvegarde est partie en file hors ligne.
   */
  onFeedbackSaved(updated: Workout): void {
    this.workouts.set(this.workouts().map((x) => (x.id === updated.id ? updated : x)));
    this.pending.set(this.pending().map((x) => (x.id === updated.id ? updated : x)).filter(awaitsFeedback));
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
