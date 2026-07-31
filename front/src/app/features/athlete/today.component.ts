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
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ScheduledStrength } from '../../core/models/strength.model';
import { Activity } from '../../core/models/activity.model';
import { WorkoutPrescription } from '../../core/models/course.model';
import { CoursePrescriptionViewComponent } from '../../shared/components/course-prescription-view/course-prescription-view.component';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';
import {
  IntensityZoneBadgeComponent,
  type IntensityZone as ZoneNum,
} from '../../shared/components/physiology';
import { WorkoutFeedbackSheetComponent } from '../../shared/components/workout-feedback-sheet/workout-feedback-sheet.component';
import { HelpHintComponent } from '../help/help-hint.component';
import { CheckInCardComponent } from './check-in-card.component';

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
    RouterLink,
    LogoComponent, OfflineBannerComponent, NotificationBellComponent,
    IntensityZoneBadgeComponent, WorkoutFeedbackSheetComponent,
    CoursePrescriptionViewComponent, HelpHintComponent, CheckInCardComponent,
  ],
  templateUrl: './today.component.html',
  styleUrl: './today.component.scss',
})
export class TodayComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

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

  /**
   * Séances de force du jour. « Aujourd'hui » n'en montre plus qu'un résumé : la saisie a son
   * écran plein écran (`/athlete/session/:id`), un exercice à la fois.
   */
  readonly strengthSessions = signal<ScheduledStrength[]>([]);

  /** L'athlète a-t-il des allures de travail (VDOT) ? Sinon on l'invite à saisir une perf. */
  readonly hasPaces = signal(true);

  /** Activités rapprochées d'une séance, indexées par séance : le réalisé remonté par la montre. */
  private readonly matched = signal<Map<string, Activity>>(new Map());

  /**
   * Séances pour lesquelles la feuille s'est déjà ouverte toute seule. Une proposition qu'on
   * décline doit rester déclinée : la même feuille rouverte à chaque visite serait une nuisance.
   */
  private static readonly PROPOSED_KEY = 'darilab.autoFeedbackProposed';

  /** Initiales pour l'avatar de la barre supérieure (porte d'entrée du Profil). */
  initials(): string {
    const parts = (this.user()?.fullName ?? '').trim().split(/\s+/).filter(Boolean);
    return (parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '');
  }

  ngOnInit(): void {
    this.readNudgeParams();
    this.load();
    this.loadPending();
    this.loadStrength();
    this.loadMatched();
    this.portal.nextRace().subscribe({ next: (r) => this.nextRace.set(r) });
    this.portal.vdot().subscribe({
      next: (v) => this.hasPaces.set((v.paces?.length ?? 0) > 0),
      error: () => this.hasPaces.set(true),
    });
  }

  // --- Arrivée depuis une relance push -----------------------------------------------------
  // `?feedback=<id>&rpe=<n>` : l'athlète a répondu depuis la notification. On ouvre sa feuille
  // avec ce RPE pré-sélectionné, pour qu'il complète fatigue et douleur — sans lesquelles la
  // forme ne se met pas à jour — et confirme. Le service worker ne peut pas enregistrer seul.

  /** Séance et RPE demandés par l'URL, consommés une fois la séance chargée. */
  private nudge: { workoutId: string; rpe: number | null } | null = null;

  private readNudgeParams(): void {
    const params = this.route.snapshot.queryParamMap;
    const workoutId = params.get('feedback');
    if (!workoutId) return;
    const rpe = Number(params.get('rpe'));
    this.nudge = { workoutId, rpe: rpe >= 1 && rpe <= 10 ? rpe : null };
    // L'URL a joué son rôle : on la nettoie pour qu'un rechargement ne rouvre pas la feuille.
    void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  /** Ouvre la feuille demandée par la notification, dès que la séance est connue. */
  private consumeNudge(): void {
    const nudge = this.nudge;
    if (!nudge) return;
    const target = [...this.workouts(), ...this.pending()].find((w) => w.id === nudge.workoutId);
    if (!target) return;
    this.nudge = null;
    this.feedbackSheet()?.openFor(target, this.matched().get(target.id) ?? null, nudge.rpe);
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

  /** Séances de force du jour (résumé ; la saisie vit dans l'écran plein écran). */
  loadStrength(): void {
    const day = new Date();
    const iso = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`;
    this.portal.ppScheduled(iso, iso).subscribe({
      next: (list) => this.strengthSessions.set(list),
      error: () => this.strengthSessions.set([]),
    });
  }

  loadPending(): void {
    this.portal.pendingFeedback(7).subscribe({
      next: (list) => { this.pending.set(list); this.consumeNudge(); this.proposeMatched(); },
      error: () => this.pending.set([]),
    });
  }

  /** Le réalisé de la montre, indexé par séance rapprochée. */
  private loadMatched(): void {
    this.portal.activities().subscribe({
      next: (list) => {
        const map = new Map<string, Activity>();
        for (const a of list) if (a.matchedWorkoutId) map.set(a.matchedWorkoutId, a);
        this.matched.set(map);
        this.proposeMatched();
      },
      error: () => this.matched.set(new Map()),
    });
  }

  /**
   * Quand la montre a déjà rapproché une sortie d'une séance non notée, on ouvre la feuille
   * tout seul, réalisé affiché : il ne reste qu'à confirmer le ressenti. C'est le moment où
   * l'athlète a le moins d'effort à faire, et celui où il oublie le plus.
   */
  private proposeMatched(): void {
    const map = this.matched();
    // Une relance en attente ouvre déjà une feuille : deux propositions d'affilée seraient
    // vécues comme un harcèlement.
    if (!map.size || this.nudge) return;
    const candidates = [...this.workouts(), ...this.pending()].filter(awaitsFeedback);
    const proposed = this.readProposed();
    const target = candidates.find((w) => map.has(w.id) && !proposed.has(w.id));
    if (!target) return;
    this.rememberProposed(target.id);
    this.openFeedback(target);
  }

  private readProposed(): Set<string> {
    try {
      const raw = localStorage.getItem(TodayComponent.PROPOSED_KEY);
      return raw ? new Set<string>(JSON.parse(raw)) : new Set();
    } catch { return new Set(); }
  }

  private rememberProposed(id: string): void {
    const next = this.readProposed();
    next.add(id);
    // On borne : la liste ne sert qu'à ne pas reproposer, elle n'a pas à grandir sans fin.
    try { localStorage.setItem(TodayComponent.PROPOSED_KEY, JSON.stringify([...next].slice(-50))); }
    catch { /* stockage indisponible : au pire la feuille se rouvre une fois */ }
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
        this.consumeNudge();
        this.proposeMatched();
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
    this.feedbackSheet()?.openFor(w, this.matched().get(w.id) ?? null);
  }

  /**
   * Une séance notée quitte le bandeau de rattrapage : le compteur doit refléter ce qu'il
   * reste à faire, y compris quand la sauvegarde est partie en file hors ligne.
   */
  onFeedbackSaved(updated: Workout): void {
    this.workouts.set(this.workouts().map((x) => (x.id === updated.id ? updated : x)));
    this.pending.set(this.pending().map((x) => (x.id === updated.id ? updated : x)).filter(awaitsFeedback));
  }

}
