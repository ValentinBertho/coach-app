import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { Router, RouterLink } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  STEP_TYPE_LABELS,
  WORKOUT_TYPE_LABELS,
  Workout,
  WorkoutStatus,
} from '../../core/models/workout.model';
import { WorkoutService } from '../../core/services/workout.service';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import {
  DataOriginTagComponent,
  IntensityZoneBadgeComponent,
  type IntensityZone as ZoneNum,
} from '../../shared/components/physiology';
import { StickyActionBarComponent } from '../../shared/components/ui';
import { CalculatedBlockEntry, WorkoutPrescription } from '../../core/models/course.model';
import { CoursePrescriptionViewComponent } from '../../shared/components/course-prescription-view/course-prescription-view.component';
import { TrainingZone } from '../../core/models/training-zone.model';
import { TrainingZoneService } from '../../core/services/training-zone.service';
import { Activity } from '../../core/models/activity.model';
import { ActivityService } from '../../core/services/activity.service';

/** Segment de la barre de répartition du temps par zone (façon Nolio). */
interface ZoneSegment {
  name: string;
  color: string;
  durationS: number;
  pct: number;
}

type State = 'loading' | 'ready' | 'error';

/**
 * Page séance (lecture) — vue coach. Registres distincts : prescription /
 * explication / retour athlète. Réutilise les primitives (zones, origine).
 * L'édition est une action délibérée (lien vers l'éditeur), conforme à la
 * distinction lecture vs édition du blueprint §7.3.
 */
@Component({
  selector: 'app-workout-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink, FormsModule, DatePipe, IntensityZoneBadgeComponent, DataOriginTagComponent, StickyActionBarComponent, CoursePrescriptionViewComponent],
  templateUrl: './workout-detail.component.html',
  styleUrl: './workout-detail.component.scss',
})
export class WorkoutDetailComponent implements OnInit {
  private readonly workoutService = inject(WorkoutService);
  private readonly zoneService = inject(TrainingZoneService);
  private readonly activityService = inject(ActivityService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly router = inject(Router);

  /** Activité réalisée rapprochée de la séance (vue « réalisé » façon Nolio). */
  readonly activity = signal<Activity | null>(null);

  /** Stats du réalisé + écarts prévu/réalisé, dérivés de l'activité rapprochée. */
  readonly realizedStats = computed(() => {
    const a = this.activity();
    if (!a) return null;
    const paceLabel = a.durationS && a.distanceM ? this.fmtPace(a.durationS / (a.distanceM / 1000)) : null;
    return {
      durationLabel: a.durationS ? this.fmtDuration(a.durationS) : null,
      distanceKm: a.distanceM ? a.distanceM / 1000 : null,
      paceLabel,
      elevationM: a.elevationGainM,
      avgHr: a.avgHr,
      distanceDeltaKm: a.distanceDeltaM != null ? a.distanceDeltaM / 1000 : null,
      durationDeltaS: a.durationDeltaS,
      source: a.source,
      date: a.activityDate,
    };
  });

  /** Zones du club (id → nom/couleur), pour la répartition du temps par zone. */
  readonly zones = signal<TrainingZone[]>([]);
  private readonly zoneById = computed(() => {
    const map = new Map<string, TrainingZone>();
    for (const z of this.zones()) map.set(z.id, z);
    return map;
  });

  /** Répartition du temps planifié par zone (barre + légende façon Nolio). */
  readonly zoneDistribution = computed<{ segments: ZoneSegment[]; totalS: number }>(() => {
    const calc = this.courseRx()?.calculated;
    if (!calc) return { segments: [], totalS: 0 };
    const byZone = new Map<string, number>();
    const add = (zoneId: string | null | undefined, s: number | null | undefined) => {
      if (!zoneId || !s) return;
      byZone.set(zoneId, (byZone.get(zoneId) ?? 0) + s);
    };
    const acc = (entries: CalculatedBlockEntry[]) => {
      for (const e of entries) {
        add(e.block.prescription?.zoneId, e.calc?.estimatedDurationS);
        const reps = e.block.reps && e.block.reps > 1 ? e.block.reps - 1 : 0;
        if (reps && e.recoveryCalc?.estimatedDurationS) {
          add(e.block.recovery?.prescription?.zoneId, e.recoveryCalc.estimatedDurationS * reps);
        }
      }
    };
    acc(calc.warmup); acc(calc.main); acc(calc.cooldown);
    const totalS = [...byZone.values()].reduce((s, v) => s + v, 0);
    if (!totalS) return { segments: [], totalS: 0 };
    const segments = [...byZone.entries()]
      .map(([zoneId, durationS]) => {
        const z = this.zoneById().get(zoneId);
        return { name: z?.name ?? 'Zone', color: z?.color || 'var(--ink-3)', durationS, pct: (durationS / totalS) * 100 };
      })
      .sort((a, b) => b.durationS - a.durationS);
    return { segments, totalS };
  });

  readonly athleteId = input.required<string>();
  readonly workoutId = input.required<string>();

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;

  readonly state = signal<State>('loading');
  readonly workout = signal<Workout | null>(null);
  /** Prescription course en fourchettes (snapshot + cibles calculées) — affichage prioritaire. */
  readonly courseRx = signal<WorkoutPrescription | null>(null);
  readonly courseRxHasContent = computed(() => {
    const c = this.courseRx()?.calculated;
    return !!c && (c.warmup.length + c.main.length + c.cooldown.length) > 0;
  });

  /** La séance a-t-elle un retour athlète déclaré ? */
  readonly hasFeedback = computed(() => {
    const w = this.workout();
    return !!w && (w.rpe != null || !!w.athleteComment);
  });

  /**
   * RPE prescrit de la séance : moyenne des RPE de bloc pondérée par leur durée estimée. La
   * boucle prescription → ressenti existait des deux côtés mais n'était confrontée nulle part ;
   * pondérer par la durée évite qu'un bloc de 2 min pèse autant qu'un bloc de 40 min.
   */
  readonly prescribedRpe = computed<number | null>(() => {
    const calc = this.courseRx()?.calculated;
    if (!calc) return null;
    let weighted = 0;
    let totalS = 0;
    for (const e of [...calc.warmup, ...calc.main, ...calc.cooldown]) {
      const rpe = e.block.rpe;
      if (rpe == null) continue;
      // Un bloc sans durée estimée compte quand même, avec un poids neutre d'une minute.
      const s = e.calc?.estimatedDurationS ?? 60;
      weighted += rpe * s;
      totalS += s;
    }
    if (!totalS) return null;
    return Math.round((weighted / totalS) * 10) / 10;
  });

  /** Écart entre RPE ressenti et RPE prescrit (positif = plus dur que prévu). */
  readonly rpeGap = computed<number | null>(() => {
    const felt = this.workout()?.rpe;
    const planned = this.prescribedRpe();
    if (felt == null || planned == null) return null;
    return Math.round((felt - planned) * 10) / 10;
  });

  /** Au-delà de ±2, l'écart mérite l'attention du coach (séance mal calibrée ou athlète en difficulté). */
  readonly rpeGapNotable = computed(() => Math.abs(this.rpeGap() ?? 0) >= 2);

  // --- Commentaire du coach sur la séance réalisée --------------------------
  coachCommentDraft = '';
  readonly savingComment = signal(false);
  readonly editingComment = signal(false);

  startEditComment(): void {
    this.coachCommentDraft = this.workout()?.coachComment ?? '';
    this.editingComment.set(true);
  }
  cancelEditComment(): void { this.editingComment.set(false); }

  saveCoachComment(): void {
    if (this.savingComment()) return;
    this.savingComment.set(true);
    const text = this.coachCommentDraft.trim();
    this.workoutService.setCoachComment(this.athleteId(), this.workoutId(), text || null).subscribe({
      next: (updated) => {
        this.workout.set(updated);
        this.savingComment.set(false);
        this.editingComment.set(false);
        this.toast.success(text ? 'Commentaire envoyé à l’athlète' : 'Commentaire retiré.');
      },
      error: () => { this.savingComment.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  /** Bandeau de stats façon Nolio : durée · distance · allure moyenne · charge estimée. */
  readonly sessionStats = computed(() => {
    const w = this.workout();
    if (!w) return null;
    const calc = this.courseRx()?.calculated;
    const durationS = calc?.totalDurationS ?? w.targetDurationS ?? null;
    const distanceM = calc?.totalDistanceM ?? w.targetDistanceM ?? null;
    const paceLabel = durationS && distanceM ? this.fmtPace(durationS / (distanceM / 1000)) : null;
    const loadUA = w.rpe != null && durationS ? Math.round(w.rpe * (durationS / 60)) : null;
    return {
      durationLabel: durationS ? this.fmtDuration(durationS) : null,
      distanceKm: distanceM ? distanceM / 1000 : null,
      paceLabel,
      loadUA,
    };
  });

  fmtDuration(totalS: number): string {
    const min = Math.round(totalS / 60);
    if (min < 60) return `${min} min`;
    return `${Math.floor(min / 60)}h${String(min % 60).padStart(2, '0')}`;
  }

  fmtPace(secPerKm: number): string {
    const m = Math.floor(secPerKm / 60);
    const s = Math.round(secPerKm % 60);
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.state.set('loading');
    this.courseRx.set(null);
    this.activity.set(null);
    this.workoutService.get(this.athleteId(), this.workoutId()).subscribe({
      next: (w) => { this.workout.set(w); this.state.set('ready'); },
      error: () => this.state.set('error'),
    });
    this.workoutService.prescription(this.athleteId(), this.workoutId()).subscribe({
      next: (p) => this.courseRx.set(p),
      error: () => this.courseRx.set(null),
    });
    // Les zones lues sont celles de l'échelle appliquée à CET athlète (modèle de zones).
    this.zoneService.list({ athleteId: this.athleteId() })
      .subscribe({ next: (z) => this.zones.set(z), error: () => this.zones.set([]) });
    this.activityService.forWorkout(this.athleteId(), this.workoutId()).subscribe({
      next: (a) => this.activity.set(a),
      error: () => this.activity.set(null),
    });
  }

  /** Écart de durée signé (« +2:15 » / « -0:40 ») pour l'affichage prévu/réalisé. */
  fmtDeltaDuration(deltaS: number): string {
    const sign = deltaS >= 0 ? '+' : '−';
    const abs = Math.abs(deltaS);
    const m = Math.floor(abs / 60);
    const s = abs % 60;
    return `${sign}${m}:${s.toString().padStart(2, '0')}`;
  }

  setStatus(status: WorkoutStatus): void {
    this.workoutService.updateStatus(this.athleteId(), this.workoutId(), status).subscribe({
      next: (w) => { this.workout.set(w); this.toast.success(`Séance ${this.statusLabels[status].toLowerCase()}.`); },
      error: () => this.toast.error('Mise à jour impossible.'),
    });
  }

  async remove(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer cette séance ?',
      message: 'Cette action est définitive.',
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    this.workoutService.delete(this.athleteId(), this.workoutId()).subscribe({
      next: () => {
        this.toast.success('Séance supprimée.');
        this.router.navigate(['/app/athletes', this.athleteId()]);
      },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  zoneNum(zone: string | null): ZoneNum | null {
    if (!zone) return null;
    const n = Number(zone.replace(/\D/g, ''));
    return n >= 1 && n <= 5 ? (n as ZoneNum) : null;
  }

  stepTarget(distanceM: number | null, durationS: number | null): string {
    if (distanceM) return distanceM >= 1000 ? `${(distanceM / 1000).toFixed(1)} km` : `${distanceM} m`;
    if (durationS) {
      const m = Math.floor(durationS / 60);
      const s = durationS % 60;
      return m ? `${m}:${s.toString().padStart(2, '0')}` : `${s} s`;
    }
    return '';
  }
}
