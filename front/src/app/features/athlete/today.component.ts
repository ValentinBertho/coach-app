import { ChangeDetectionStrategy, Component, OnInit, inject, signal, viewChild } from '@angular/core';
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
import { AthletePortalService, FeedbackPrompt } from '../../core/services/athlete-portal.service';
import { ScheduledStrength } from '../../core/models/strength.model';
import { WorkoutPrescription } from '../../core/models/course.model';
import { CoursePrescriptionViewComponent } from '../../shared/components/course-prescription-view/course-prescription-view.component';
import { AuthService } from '../../core/services/auth.service';
import { CelebrationService } from '../../core/services/celebration.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';
import { IntensityZoneBadgeComponent, type IntensityZone as ZoneNum } from '../../shared/components/physiology';
import { WorkoutFeedbackSheetComponent } from '../../shared/components/workout-feedback-sheet/workout-feedback-sheet.component';
import { MorningCheckInComponent } from './morning-check-in.component';
import { HelpHintComponent } from '../help/help-hint.component';

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
  imports: [IconComponent, RouterLink,
    LogoComponent, OfflineBannerComponent, NotificationBellComponent,
    IntensityZoneBadgeComponent, WorkoutFeedbackSheetComponent,
    CoursePrescriptionViewComponent, MorningCheckInComponent, HelpHintComponent,
  ],
  templateUrl: './today.component.html',
  styleUrl: './today.component.scss',
})
export class TodayComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly celebration = inject(CelebrationService);

  /** Feuille de ressenti partagée : « Aujourd'hui », l'agenda et l'historique ouvrent la même. */
  private readonly feedbackSheet = viewChild(WorkoutFeedbackSheetComponent);

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

  /** Séances de force du jour, en carte de lancement (la saisie est en plein écran). */
  readonly strengthSessions = signal<ScheduledStrength[]>([]);

  /** L'athlète a-t-il des allures de travail (VDOT) ? Sinon on l'invite à saisir une perf. */
  readonly hasPaces = signal(true);

  /** Initiales pour l'avatar de la barre supérieure (porte d'entrée du Profil). */
  initials(): string {
    const parts = (this.user()?.fullName ?? '').trim().split(/\s+/).filter(Boolean);
    return (parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '');
  }

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
    const day = this.todayIso();
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
        // Les deux ouvertures automatiques dépendent de la liste du jour : on les branche
        // une fois qu'elle est là, pour pouvoir y retrouver la séance visée.
        this.openFromNotification();
        this.openFromMatchedActivity();
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
   * Auto-détection : quand une activité importée a été rapprochée d'une séance, la feuille
   * s'ouvre pré-remplie (distance, durée, FC) — l'athlète confirme, il ne saisit pas.
   *
   * Sans ça, le rapprochement clôture la séance côté données et la fait disparaître du bandeau
   * des retours en attente : le coach n'a jamais de signal de forme sur les séances importées
   * depuis la montre.
   */
  private openFromMatchedActivity(): void {
    if (this.route.snapshot.queryParamMap.get('feedback')) return; // la notification a priorité
    this.portal.feedbackPrompt().subscribe({
      next: (prompt) => {
        if (!prompt) return;
        this.feedbackSheet()?.openFor(prompt.workout, { activity: prompt });
        // Vue une fois, proposée une fois : un refus ne doit pas revenir à chaque lancement.
        this.portal.ackFeedbackPrompt(prompt.activityId).subscribe({ error: () => undefined });
      },
      error: () => undefined,
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

  private todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}
