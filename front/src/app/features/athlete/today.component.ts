import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  STEP_TYPE_LABELS,
  WORKOUT_TYPE_LABELS,
  Workout,
} from '../../core/models/workout.model';
import { AthletePortalService, StrengthPrescriptionView } from '../../core/services/athlete-portal.service';
import { Progression, ScheduledStrength, StrengthResultEntry } from '../../core/models/strength.model';
import { WorkoutPrescription } from '../../core/models/course.model';
import { CoursePrescriptionViewComponent } from '../../shared/components/course-prescription-view/course-prescription-view.component';
import { AuthService } from '../../core/services/auth.service';
import { FeedbackQueueService } from '../../core/services/feedback-queue.service';
import { NetworkStatusService } from '../../core/services/network-status.service';
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
import {
  BottomSheetComponent,
} from '../../shared/components/ui';
import { HelpService } from '../help/help.service';
import { HelpHintComponent } from '../help/help-hint.component';

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
    PainFatigueSelectorComponent, BottomSheetComponent,
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
  private readonly network = inject(NetworkStatusService);
  private readonly queue = inject(FeedbackQueueService);
  readonly help = inject(HelpService);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  readonly rpeScale = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  readonly state = signal<State>('loading');
  /** Toutes les séances de course du jour (double séance possible). */
  readonly workouts = signal<Workout[]>([]);
  /** Prescription course en fourchettes par séance (clé = id séance). */
  readonly courseRx = signal<Record<string, WorkoutPrescription | null>>({});
  readonly nextRace = signal<import('../../core/models/race.model').RaceObjective | null>(null);
  readonly user = this.auth.currentUser;

  // Retour course (signals pour two-way binding des sélecteurs du bottom sheet).
  readonly rpe = signal<number | null>(null);
  readonly fatigue = signal<number | null>(null);
  readonly pain = signal<number | null>(null);
  readonly comment = signal('');
  readonly feedbackOpen = signal(false);
  /** Séance de course visée par le bottom sheet de retour. */
  readonly activeWorkout = signal<Workout | null>(null);

  /** Toutes les séances de force du jour (double séance possible). */
  readonly strengthCards = signal<StrengthCard[]>([]);

  /** L'athlète a-t-il des allures de travail (VDOT) ? Sinon on l'invite à saisir une perf. */
  readonly hasPaces = signal(true);

  ngOnInit(): void {
    this.load();
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
  needsFeedback(w: Workout): boolean {
    return w.status === 'PLANNED' || w.status === 'PARTIAL';
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

  /** Ouvre le bottom sheet de retour pour une séance de course donnée. */
  openFeedback(w: Workout): void {
    this.activeWorkout.set(w);
    this.rpe.set(w.rpe ?? null);
    this.fatigue.set(null);
    this.pain.set(null);
    this.comment.set(w.athleteComment ?? '');
    this.feedbackOpen.set(true);
  }

  submit(completed: boolean): void {
    const w = this.activeWorkout();
    if (!w) return;
    const body = {
      status: (completed ? 'COMPLETED' : 'PARTIAL') as 'COMPLETED' | 'PARTIAL',
      rpe: this.rpe(),
      fatigue: this.fatigue(),
      pain: this.pain(),
      comment: this.comment() || null,
    };

    const applyLocal = (status: 'COMPLETED' | 'PARTIAL') =>
      this.workouts.set(
        this.workouts().map((x) =>
          x.id === w.id ? { ...x, status, rpe: this.rpe(), athleteComment: this.comment() || null } : x,
        ),
      );

    // Hors ligne : mise à jour optimiste + mise en file (sync au retour réseau).
    if (!this.network.online()) {
      this.queue.enqueue(w.id, body);
      applyLocal(body.status);
      this.feedbackOpen.set(false);
      this.toast.info('Enregistré hors ligne — synchronisé au retour du réseau.');
      return;
    }

    this.portal.feedback(w.id, body).subscribe({
      next: (updated) => {
        this.workouts.set(this.workouts().map((x) => (x.id === updated.id ? updated : x)));
        this.feedbackOpen.set(false);
        this.toast.success('Ressenti enregistré');
      },
      error: () => {
        this.queue.enqueue(w.id, body);
        applyLocal(body.status);
        this.feedbackOpen.set(false);
        this.toast.warning('Hors ligne — ressenti mis en file pour synchronisation.');
      },
    });
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
