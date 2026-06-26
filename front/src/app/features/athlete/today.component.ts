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
  StickyActionBarComponent,
} from '../../shared/components/ui';

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

type State = 'loading' | 'ready' | 'error';

/**
 * Portail athlète — « Aujourd'hui » (mobile-first PWA).
 * Refonte blueprint : carte héro de la séance, zones d'intensité explicites,
 * retour rapide (RPE + fatigue + douleur) dans un bottom sheet déclenché par la
 * barre d'action collante. Préserve le câblage services et la file offline.
 */
@Component({
  selector: 'app-today',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, 
    FormsModule, RouterLink,
    LogoComponent, InstallButtonComponent, OfflineBannerComponent, PushButtonComponent, NotificationBellComponent,
    IntensityZoneBadgeComponent, RangePrescriptionPillComponent, EffortBadgeComponent,
    PainFatigueSelectorComponent, BottomSheetComponent, StickyActionBarComponent,
    CoursePrescriptionViewComponent,
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

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  readonly rpeScale = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

  readonly state = signal<State>('loading');
  readonly workout = signal<Workout | null>(null);
  /** Prescription course en fourchettes (snapshot + cibles calculées) — affichage prioritaire. */
  readonly courseRx = signal<WorkoutPrescription | null>(null);
  readonly courseRxHasContent = computed(() => {
    const c = this.courseRx()?.calculated;
    return !!c && (c.warmup.length + c.main.length + c.cooldown.length) > 0;
  });
  readonly nextRace = signal<import('../../core/models/race.model').RaceObjective | null>(null);
  readonly user = this.auth.currentUser;

  // Retour course (signals pour two-way binding des sélecteurs).
  readonly rpe = signal<number | null>(null);
  readonly fatigue = signal<number | null>(null);
  readonly pain = signal<number | null>(null);
  readonly comment = signal('');
  readonly feedbackOpen = signal(false);

  /** La course attend-elle encore un retour ? (PLANNED ou PARTIAL) */
  readonly runNeedsFeedback = computed(() => {
    const w = this.workout();
    return !!w && (w.status === 'PLANNED' || w.status === 'PARTIAL');
  });

  // Renforcement du jour
  readonly strength = signal<ScheduledStrength | null>(null);
  readonly strengthRx = signal<StrengthPrescriptionView | null>(null);
  readonly exerciseSets = signal<ExerciseSets[]>([]);
  readonly progression = signal<Progression | null>(null);
  readonly sRpe = signal<number | null>(null);
  readonly sFatigue = signal<number | null>(null);
  readonly sPain = signal<number | null>(null);

  ngOnInit(): void {
    this.load();
    this.loadStrength();
    this.portal.nextRace().subscribe({ next: (r) => this.nextRace.set(r) });
  }

  loadStrength(): void {
    const day = new Date().toISOString().slice(0, 10);
    this.portal.ppScheduled(day, day).subscribe({
      next: (list) => {
        const s = list[0] ?? null;
        this.strength.set(s);
        if (s) {
          this.portal.ppPrescription(s.id).subscribe({
            next: (p) => {
              this.strengthRx.set(p);
              this.buildSets(p);
            },
          });
        }
      },
    });
  }

  /** Pré-remplit les séries à saisir + capture les fourchettes prescrites (lecture seule). */
  private buildSets(rx: StrengthPrescriptionView): void {
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
    this.exerciseSets.set(list);
  }

  submitStrength(completed: boolean): void {
    const s = this.strength();
    if (!s) return;

    // 1. Séries réalisées → recalcul automatique du e1RM.
    const results: StrengthResultEntry[] = [];
    for (const ex of this.exerciseSets()) {
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
          this.portal.ppProgression(s.id).subscribe((p) => this.progression.set(p));
        },
      });
    }

    // 2. Retour de séance global.
    this.portal
      .ppFeedback(s.id, { completed, sessionRpe: this.sRpe(), fatigue: this.sFatigue(), pain: this.sPain(), comment: null })
      .subscribe({
        next: (updated) => {
          this.strength.set(updated);
          this.toast.success('Renforcement enregistré');
        },
      });
  }

  load(): void {
    this.state.set('loading');
    this.portal.today().subscribe({
      next: (list) => {
        const w = list[0] ?? null;
        this.workout.set(w);
        this.courseRx.set(null);
        if (w) {
          this.rpe.set(w.rpe ?? null);
          this.comment.set(w.athleteComment ?? '');
          this.portal.workoutPrescription(w.id).subscribe({
            next: (p) => this.courseRx.set(p),
            error: () => this.courseRx.set(null),
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

  submit(completed: boolean): void {
    const w = this.workout();
    if (!w) return;
    const body = {
      status: (completed ? 'COMPLETED' : 'PARTIAL') as 'COMPLETED' | 'PARTIAL',
      rpe: this.rpe(),
      fatigue: this.fatigue(),
      pain: this.pain(),
      comment: this.comment() || null,
    };

    // Hors ligne : mise à jour optimiste + mise en file (sync au retour réseau).
    if (!this.network.online()) {
      this.queue.enqueue(w.id, body);
      this.workout.set({ ...w, status: body.status, rpe: this.rpe(), athleteComment: this.comment() || null });
      this.feedbackOpen.set(false);
      this.toast.info('Enregistré hors ligne — synchronisé au retour du réseau.');
      return;
    }

    this.portal.feedback(w.id, body).subscribe({
      next: (updated) => {
        this.workout.set(updated);
        this.feedbackOpen.set(false);
        this.toast.success('Ressenti enregistré');
      },
      error: () => {
        this.queue.enqueue(w.id, body);
        this.workout.set({ ...w, status: body.status, rpe: this.rpe(), athleteComment: this.comment() || null });
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
