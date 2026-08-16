import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  STEP_TYPE_LABELS,
  WORKOUT_TYPE_LABELS,
  Workout,
  awaitsFeedback,
  needsFeedback,
} from '../../core/models/workout.model';
import { WeekSummary } from '../../core/models/activity.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { PaceReferenceService } from '../../core/services/pace-reference.service';
import { plannedVolume, plannedVolumeLabel } from '../../core/utils/planned-volume';
import { formatBlockVolume } from '../../core/utils/prescription-format';
import { ScheduledStrength } from '../../core/models/strength.model';
import { WorkoutPrescription } from '../../core/models/course.model';
import { CoursePrescriptionViewComponent } from '../../shared/components/course-prescription-view/course-prescription-view.component';
import { AuthService } from '../../core/services/auth.service';
import { CelebrationService } from '../../core/services/celebration.service';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';
import { IntensityZoneBadgeComponent, type IntensityZone as ZoneNum } from '../../shared/components/physiology';
import { WorkoutFeedbackSheetComponent } from '../../shared/components/workout-feedback-sheet/workout-feedback-sheet.component';
import { MorningCheckInComponent } from './morning-check-in.component';
import { ReadinessCardComponent } from './readiness-card.component';
import { TrajectoryBannerComponent } from '../../shared/components/trajectory-banner/trajectory-banner.component';
import { Proposal, Trajectory } from '../../core/models/decision.model';
import { StravaCardComponent } from './strava-card.component';
import { PushPromptComponent } from '../../shared/components/push-prompt/push-prompt.component';
import { HelpHintComponent } from '../help/help-hint.component';
import { CycleBannerComponent } from '../../shared/components/cycle-banner/cycle-banner.component';
import { CalendarNote } from '../../core/models/calendar-note.model';

/** Date du jour au format ISO local (jamais UTC : un cycle se lit dans le fuseau de l'athlète). */
function toIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

type State = 'loading' | 'ready' | 'error';

/**
 * Portail athlète — « Aujourd'hui » (mobile-first PWA).
 *
 * Cet écran **porte** les séances du jour, il ne les fait pas saisir : carte héro de la séance
 * course, zones d'intensité explicites, retour rapide (RPE + fatigue + douleur) en bottom
 * sheet. La saisie de force vit désormais dans son propre mode plein écran
 * (`/athlete/session/:id`) — un tableau de 30 champs n'a jamais été une carte.
 *
 * Supporte **plusieurs séances course ET force le même jour** (double séance) : chaque séance
 * est une carte autonome avec sa propre action.
 */
@Component({
  selector: 'app-today',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, IconComponent, RouterLink,
    OfflineBannerComponent,
    IntensityZoneBadgeComponent, WorkoutFeedbackSheetComponent,
    CoursePrescriptionViewComponent, MorningCheckInComponent, ReadinessCardComponent,
    TrajectoryBannerComponent,
    StravaCardComponent, HelpHintComponent,
    PushPromptComponent, CycleBannerComponent,
  ],
  templateUrl: './today.component.html',
  styleUrl: './today.component.scss',
})
export class TodayComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly paceReference = inject(PaceReferenceService);

  /** Mon allure d'endurance, seule base admise pour estimer un volume écrit en durée. */
  readonly referencePace = signal<number | null>(null);

  /**
   * Volume de la séance du jour, « 12,0 km » ou « ≈ 10,0 km ». Une séance écrite en durée sans
   * allure prescrite n'a pas de distance calculable : le total ne comptait alors que ses blocs
   * chiffrables et pouvait annoncer « 0,1 km » pour une heure de course.
   */
  volumeLabel(w: Workout): string {
    return plannedVolumeLabel(
      plannedVolume(w.targetDistanceM, w.targetDurationS, this.referencePace()));
  }
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly celebration = inject(CelebrationService);

  /** Feuille de ressenti partagée : « Aujourd'hui », l'agenda et l'historique ouvrent la même. */
  private readonly feedbackSheet = viewChild(WorkoutFeedbackSheetComponent);

  /**
   * Le feu vert du matin, rechargé dès que le check-in est enregistré : c'est précisément la
   * déclaration qui vient d'être faite qui change le conseil.
   */
  readonly readinessCard = viewChild(ReadinessCardComponent);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;

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

  /** L'écart à mon objectif, ou rien si je n'ai pas déclaré de course. */
  readonly trajectory = signal<Trajectory | null>(null);

  /** Mes demandes en attente : ce que j'ai demandé et que mon coach n'a pas encore tranché. */
  readonly myProposals = signal<Proposal[]>([]);

  /** Séances de force du jour, en carte de lancement (la saisie est en plein écran). */
  readonly strengthSessions = signal<ScheduledStrength[]>([]);

  /** L'athlète a-t-il des allures de travail (VDOT) ? Sinon on l'invite à saisir une perf. */
  readonly hasPaces = signal(true);

  /** Récapitulatif de la semaine en cours : « 32/45 km, 3 séances sur 5 ». */
  readonly week = signal<WeekSummary | null>(null);

  /**
   * Le cycle du jour, s'il y en a un. On ne demande qu'aujourd'hui : cet écran répond à une
   * seule question — où j'en suis maintenant — la vue d'ensemble vit dans l'agenda.
   */
  readonly cycles = signal<CalendarNote[]>([]);
  /** Date du jour, fixée au montage de l'écran (rouvert chaque matin, il se remonte). */
  readonly todayIso = toIso(new Date());

  /** Part du volume hebdomadaire déjà couverte, plafonnée à 100 %. */
  readonly weekPct = computed(() => {
    const w = this.week();
    if (!w || w.plannedKm <= 0) {
      return w && w.realizedKm > 0 ? 100 : 0;
    }
    return Math.max(0, Math.min(100, Math.round((w.realizedKm / w.plannedKm) * 100)));
  });

  ngOnInit(): void {
    this.load();
    this.loadPending();
    this.loadStrength();
    this.portal.weekSummary().subscribe({
      next: (w) => this.week.set(w),
      error: () => this.week.set(null),
    });
    this.portal.nextRace().subscribe({ next: (r) => this.nextRace.set(r) });
    this.portal.trajectory().subscribe({
      next: (t) => this.trajectory.set(t),
      error: () => this.trajectory.set(null),
    });
    this.portal.cycles(this.todayIso, this.todayIso).subscribe({
      next: (c) => this.cycles.set(c),
      error: () => this.cycles.set([]),
    });
    this.paceReference.mine().subscribe({
      next: (p) => this.referencePace.set(p),
      error: () => this.referencePace.set(null),
    });
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
    this.portal.myProposals().subscribe({
      next: (p) => this.myProposals.set(p),
      error: () => this.myProposals.set([]),
    });
  }

  loadStrength(): void {
    const day = this.todayIso;
    this.portal.ppScheduled(day, day).subscribe({
      next: (list) => this.strengthSessions.set(list),
      error: () => this.strengthSessions.set([]),
    });
  }

  /** Ouvre le mode séance plein écran (un exercice à la fois, gros chiffres, ± 2,5 kg). */
  openSessionMode(session: ScheduledStrength): void {
    this.router.navigate(['/athlete/session', session.id]);
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
        // L'ouverture depuis une notification dépend de la liste du jour : on la branche une
        // fois qu'elle est là, pour pouvoir y retrouver la séance visée. L'invitation au débrief,
        // elle, a rejoint la coquille (app-debrief-prompt) : elle doit se déclencher sur tous
        // les écrans du portail, pas seulement sur celui-ci.
        this.openFromNotification();
      },
      error: () => this.state.set('error'),
    });
  }

  /**
   * Ressenti en deux taps depuis la notification push : l'action rapide dépose
   * `?feedback=<id>&rpe=<n>` et la feuille s'ouvre avec le RPE déjà choisi.
   *
   * L'athlète n'a plus qu'à poser sa fatigue et sa douleur — le RPE seul ne dit rien de sa
   * forme, il ne peut donc pas valider tout seul depuis la notification.
   */
  private openFromNotification(): void {
    const params = this.route.snapshot.queryParamMap;
    const workoutId = params.get('feedback');
    if (!workoutId) return;

    const rpe = Number(params.get('rpe'));
    const target = this.workouts().find((w) => w.id === workoutId)
      ?? this.pending().find((w) => w.id === workoutId);

    if (target) {
      this.feedbackSheet()?.openFor(target, { rpe: rpe >= 1 && rpe <= 10 ? rpe : null });
    } else {
      // La séance n'est plus du jour (notification ouverte le lendemain) : on va la chercher.
      this.portal.pendingFeedback(7).subscribe({
        next: (list) => {
          const late = list.find((w) => w.id === workoutId);
          if (late) this.feedbackSheet()?.openFor(late, { rpe: rpe >= 1 && rpe <= 10 ? rpe : null });
        },
      });
    }
    // L'URL ne doit pas garder le paramètre : un rafraîchissement rouvrirait la feuille.
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  /**
   * Une synchro Strava manuelle vient de ramener des sorties : l'écran doit s'en apercevoir tout
   * de suite. Une activité importée peut être rapprochée d'une séance (statut, retour attendu) et
   * elle compte dans le volume de la semaine — sans ce rafraîchissement, l'athlète synchronise et
   * ne voit rien changer.
   */
  onStravaSynced(): void {
    this.load();
    this.loadPending();
    this.portal.weekSummary().subscribe({
      next: (w) => this.week.set(w),
      error: () => this.week.set(null),
    });
  }

  /** 'Z3' → 3 pour le badge de zone (sécurisé). */
  zoneNum(zone: string | null): ZoneNum | null {
    if (!zone) return null;
    const n = Number(zone.replace(/\D/g, ''));
    return n >= 1 && n <= 5 ? (n as ZoneNum) : null;
  }

  /** Cible d'un pas course (distance ou durée) — écriture partagée (cf. prescription-format). */
  stepTarget(distanceM: number | null, durationS: number | null): string {
    return formatBlockVolume(distanceM, durationS);
  }

  /** Ouvre la feuille de ressenti — séance du jour comme séance en retard, même parcours. */
  openFeedback(w: Workout): void {
    this.feedbackSheet()?.openFor(w);
  }

  /**
   * Une séance notée quitte le bandeau de rattrapage : le compteur doit refléter ce qu'il
   * reste à faire, y compris quand la sauvegarde est partie en file hors ligne. Et l'athlète
   * reçoit enfin quelque chose en retour — sa série.
   */
  onFeedbackSaved(updated: Workout): void {
    this.workouts.set(this.workouts().map((x) => (x.id === updated.id ? updated : x)));
    this.pending.set(this.pending().map((x) => (x.id === updated.id ? updated : x)).filter(awaitsFeedback));

    this.portal.feedbackStreak().subscribe({
      next: (streak) => this.celebrateFeedback(streak),
      // La célébration est un bonus : son échec ne doit pas se voir.
      error: () => this.celebrateFeedback(0),
    });
  }

  private celebrateFeedback(streak: number): void {
    this.celebration.celebrate({
      title: 'Ressenti enregistré',
      detail: streak > 1 ? `${streak}e retour d'affilée 🔥` : undefined,
    });
  }

}
