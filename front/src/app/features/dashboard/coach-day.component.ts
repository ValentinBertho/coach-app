import { DatePipe, LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AthleteSummary } from '../../core/models/athlete.model';
import { WORKOUT_TYPE_LABELS, WorkoutType } from '../../core/models/workout.model';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { AthleteService } from '../../core/services/athlete.service';
import {
  CoachAlert,
  CoachDashboardService,
  CoachDaySession,
  FeedbackQueueItem,
} from '../../core/services/coach-dashboard.service';
import { Conversation, MessageService } from '../../core/services/message.service';
import { NetworkStatusService } from '../../core/services/network-status.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { CourseService } from '../../core/services/course.service';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';
import { WorkoutTemplateService } from '../../core/services/workout-template.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { InstallPromptComponent } from '../../shared/components/install-prompt/install-prompt.component';
import { PushPromptComponent } from '../../shared/components/push-prompt/push-prompt.component';
import { ReplySheetComponent, type ReplyTarget } from '../../shared/components/reply-sheet/reply-sheet.component';
import { injuryLabel } from '../../core/models/injury.model';
import { Proposal } from '../../core/models/decision.model';
import { DecisionService } from '../../core/services/decision.service';
import { ProposalListComponent } from '../../shared/components/proposal-list/proposal-list.component';

type Scope = 'all' | 'mine' | 'private' | 'club';

/** Périmètre retenu entre deux ouvertures : on ne le rechoisit pas chaque matin. */
const SCOPE_KEY = 'coach-day-scope';

/** Dernier chargement réussi : sert à dater ce que le cache affiche hors ligne. */
const STAMP_KEY = 'coach-day-loaded-at';

/**
 * « Ma journée » — l'écran du matin, pensé pour un téléphone.
 *
 * <p><b>Pourquoi il existe.</b> Ce qu'un coach cherche en ouvrant son application tient en une
 * phrase : <i>qui a mal, qui n'a pas fait sa séance, qui m'a écrit, qu'est-ce qui est prévu
 * aujourd'hui</i>. Ces quatre informations existaient toutes — alertes, file de retours,
 * conversations, calendrier — mais réparties sur quatre écrans, chacun avec ses filtres et sa
 * densité de bureau. Sur ordinateur, les parcourir coûte quelques clics ; sur un téléphone,
 * c'était le parcours entier, et c'est pourquoi personne ne le faisait.</p>
 *
 * <p><b>Ce qu'il n'est pas.</b> Ni un cockpit (le tableau de bord garde les KPI, la répartition
 * de forme et les courses), ni un calendrier. On ne conçoit pas une semaine ici : on regarde
 * une journée, et on agit dessus — ouvrir, déplacer, supprimer, planifier.</p>
 *
 * <p>Le périmètre par défaut est <b>mes athlètes</b>, pas tout le club : sur un écran de
 * téléphone, une file doit être la sienne pour valoir la peine d'être lue.</p>
 */
@Component({
  selector: 'app-coach-day',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, LowerCasePipe, FormsModule, IconComponent,
    InstallPromptComponent, PushPromptComponent, ReplySheetComponent, ProposalListComponent],
  templateUrl: './coach-day.component.html',
  styleUrl: './coach-day.component.scss',
})
export class CoachDayComponent implements OnInit {
  private readonly dashboard = inject(CoachDashboardService);
  private readonly messages = inject(MessageService);
  private readonly workouts = inject(WorkoutService);
  private readonly courses = inject(CourseService);
  private readonly templates = inject(WorkoutTemplateService);
  private readonly strength = inject(StrengthService);
  private readonly athletes = inject(AthleteService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  readonly network = inject(NetworkStatusService);
  private readonly decisions = inject(DecisionService);

  readonly loading = signal(true);
  /** Le dernier chargement a échoué (réseau coupé, serveur muet) : on ne prétend pas que tout va bien. */
  readonly failed = signal(false);
  readonly alerts = signal<CoachAlert[]>([]);
  readonly reviews = signal<FeedbackQueueItem[]>([]);
  readonly conversations = signal<Conversation[]>([]);
  readonly sessions = signal<CoachDaySession[]>([]);

  /**
   * Les propositions du produit — demandes d'athlètes et suggestions détectées. Elles se
   * referment en un tap, là où une alerte demande un jugement : d'où leur place, en tête.
   */
  readonly proposals = signal<Proposal[]>([]);

  /** Jour affiché par la section « La journée ». Les autres sections ne bougent pas avec lui. */
  readonly date = signal(todayIso());
  readonly isToday = computed(() => this.date() === todayIso());

  /**
   * Horodatage du dernier chargement réussi.
   *
   * <p>Le service worker sert la file depuis son cache quand le réseau ne répond pas en trois
   * secondes. C'est exactement ce qu'on veut dans le métro — à condition de le <b>dire</b> : une
   * liste d'alertes de sept heures du matin présentée comme l'état courant à midi vaut moins que
   * pas de liste du tout.</p>
   */
  readonly lastLoadedAt = signal<string | null>(readStamp());

  readonly scope = signal<Scope>(readScope());
  readonly scopeLabel = computed(() => SCOPE_LABELS[this.scope()]);

  /** Fils avec des non-lus, les plus récents d'abord — le reste vit dans la boîte de réception. */
  readonly unread = computed(() => this.conversations().filter((c) => c.unreadCount > 0));

  /**
   * Ce qu'on montre de chaque file : les cinq premiers, jamais plus.
   *
   * <p>Sur le club de démonstration, la journée complète mesurait plus de vingt mille pixels de
   * haut — soixante-neuf cartes empilées. Une file qu'on ne finit jamais de faire défiler n'est
   * plus une file : c'est le cockpit qu'on cherchait précisément à ne pas refaire. Le compteur
   * de chaque section reste celui du <b>total</b>, et « Tout voir » mène à l'écran qui les porte
   * tous.</p>
   */
  private static readonly SHOWN = 5;
  readonly topAlerts = computed(() => this.alerts().slice(0, CoachDayComponent.SHOWN));
  readonly topReviews = computed(() => this.reviews().slice(0, CoachDayComponent.SHOWN));
  readonly topUnread = computed(() => this.unread().slice(0, CoachDayComponent.SHOWN));
  readonly moreAlerts = computed(() => Math.max(0, this.alerts().length - CoachDayComponent.SHOWN));
  readonly moreReviews = computed(() => Math.max(0, this.reviews().length - CoachDayComponent.SHOWN));
  readonly moreUnread = computed(() => Math.max(0, this.unread().length - CoachDayComponent.SHOWN));

  ngOnInit(): void {
    this.load();
  }

  /** Les propositions en attente sur le périmètre courant. */
  loadProposals(): void {
    this.decisions.proposals(this.scope()).subscribe({
      next: (p) => this.proposals.set(p),
      error: () => this.proposals.set([]),
    });
  }

  /**
   * Après une décision : on recharge tout, pas seulement la file.
   *
   * <p>Accepter une proposition modifie une séance — celle du jour, souvent — et parfois le
   * profil de l'athlète. Ne rafraîchir que la liste laisserait à l'écran une journée qui ne
   * correspond plus à ce qui vient d'être décidé.</p>
   */
  onProposalDecided(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const scope = this.scope();
    this.dashboard.alerts(scope).subscribe({
      next: (a) => this.alerts.set(a),
      error: () => this.alerts.set([]),
    });
    // Sept jours et non quatorze : la file complète a son écran. Ici, c'est ce qui vient
    // d'arriver — au-delà, on ne « traite » plus, on rattrape.
    this.dashboard.feedbackQueue(scope, 7).subscribe({
      next: (f) => {
        this.reviews.set(f);
        this.loading.set(false);
        this.failed.set(false);
        this.stampNow();
      },
      error: () => { this.reviews.set([]); this.loading.set(false); this.failed.set(true); },
    });
    this.messages.conversations().subscribe({
      next: (c) => this.conversations.set(c),
      error: () => this.conversations.set([]),
    });
    this.loadProposals();
    this.loadDay();
  }

  /** Recharge la seule section « La journée » (changement de jour, action sur une séance). */
  loadDay(): void {
    this.dashboard.day(this.scope(), this.date()).subscribe({
      next: (s) => this.sessions.set(s),
      error: () => this.sessions.set([]),
    });
  }

  setScope(value: Scope): void {
    this.scope.set(value);
    try { localStorage.setItem(SCOPE_KEY, value); } catch { /* préférence non persistée */ }
    this.scopeOpen.set(false);
    this.load();
  }

  readonly scopeOpen = signal(false);
  toggleScope(): void { this.scopeOpen.update((v) => !v); }
  readonly scopes: { value: Scope; label: string }[] = [
    { value: 'mine', label: SCOPE_LABELS.mine },
    { value: 'all', label: SCOPE_LABELS.all },
    { value: 'private', label: SCOPE_LABELS.private },
    { value: 'club', label: SCOPE_LABELS.club },
  ];

  shiftDay(days: number): void {
    const d = new Date(this.date() + 'T12:00:00');
    d.setDate(d.getDate() + days);
    this.date.set(isoOf(d));
    this.closeMenu();
    this.loadDay();
  }

  goToday(): void {
    this.date.set(todayIso());
    this.closeMenu();
    this.loadDay();
  }

  // --- Retours ---------------------------------------------------------------

  readonly busy = signal<Set<string>>(new Set());
  key(it: FeedbackQueueItem): string { return `${it.kind}:${it.sessionId}`; }
  injury = injuryLabel;

  /**
   * Marque un retour comme traité. La ligne disparaît tout de suite : sur un téléphone, une
   * liste qui ne bouge pas après un tap donne l'impression que rien ne s'est passé.
   */
  markReviewed(it: FeedbackQueueItem): void {
    const k = this.key(it);
    if (this.busy().has(k)) return;
    this.busy.update((s) => new Set(s).add(k));

    const call: Observable<unknown> = it.kind === 'STRENGTH'
      ? this.strength.markScheduledReviewed(it.athleteId, it.sessionId)
      : this.workouts.markReviewed(it.athleteId, it.sessionId);

    call.subscribe({
      next: () => {
        this.reviews.update((l) => l.filter((x) => this.key(x) !== k));
        this.release(k);
        this.dashboard.refreshPendingReviews().subscribe({ error: () => undefined });
        this.toast.success('Retour marqué comme traité');
      },
      error: () => { this.release(k); this.toast.error('Action impossible.'); },
    });
  }

  private release(k: string): void {
    this.busy.update((s) => { const n = new Set(s); n.delete(k); return n; });
  }

  // --- Répondre sans quitter l'écran ----------------------------------------
  // « Répondre » menait au fil de l'athlète, donc hors de la file en cours de traitement : on
  // perdait sa place pour écrire une phrase. La feuille écrit dans le même fil, ici.

  readonly replyTo = signal<ReplyTarget | null>(null);

  replyToAlert(a: CoachAlert): void {
    this.closeMenu();
    this.replyTo.set({ athleteId: a.athleteId, name: a.athleteName, about: a.title });
  }

  replyToReview(it: FeedbackQueueItem): void {
    this.closeMenu();
    this.replyTo.set({ athleteId: it.athleteId, name: it.athleteName, about: it.title });
  }

  replyToSession(s: CoachDaySession): void {
    this.closeMenu();
    this.replyTo.set({ athleteId: s.athleteId, name: s.athleteName, about: s.title });
  }

  /** Un message envoyé peut avoir vidé un non-lu : la section Messages se recharge. */
  onReplySent(): void {
    this.messages.conversations().subscribe({
      next: (c) => this.conversations.set(c),
      error: () => undefined,
    });
  }

  // --- Actions rapides sur une séance ---------------------------------------
  // Ouvrir, modifier, déplacer, supprimer : les quatre gestes qu'un coach fait entre deux
  // séries au bord d'une piste. Tout le reste — construire la séance, la prescrire en
  // fourchettes — reste l'affaire de l'éditeur, sur un écran qui a la place de le faire.

  /** Séance dont le menu d'actions est ouvert (une seule à la fois). */
  readonly menuFor = signal<string | null>(null);
  /** Séance en cours de déplacement : la carte affiche alors son sélecteur de date. */
  readonly movingId = signal<string | null>(null);
  moveDate = '';

  toggleMenu(s: CoachDaySession): void {
    this.menuFor.update((id) => (id === s.workoutId ? null : s.workoutId));
    this.movingId.set(null);
  }

  closeMenu(): void {
    this.menuFor.set(null);
    this.movingId.set(null);
  }

  startMove(s: CoachDaySession): void {
    this.movingId.set(s.workoutId);
    this.moveDate = s.scheduledDate;
    this.menuFor.set(null);
  }

  confirmMove(s: CoachDaySession): void {
    const target = this.moveDate;
    if (!target || target === s.scheduledDate) { this.movingId.set(null); return; }
    this.workouts.reschedule(s.athleteId, s.workoutId, target).subscribe({
      next: () => {
        this.movingId.set(null);
        // La séance quitte le jour affiché : on la retire de la liste sans attendre le rechargement.
        this.sessions.update((l) => l.filter((x) => x.workoutId !== s.workoutId));
        this.toast.success(`${s.title} déplacée au ${frDate(target)}`);
      },
      error: () => this.toast.error('Déplacement impossible.'),
    });
  }

  open(s: CoachDaySession): void {
    this.router.navigate(['/app/athletes', s.athleteId, 'workouts', s.workoutId]);
  }

  edit(s: CoachDaySession): void {
    this.router.navigate(['/app/athletes', s.athleteId, 'workouts', s.workoutId, 'structure']);
  }

  /**
   * Supprime une séance, après confirmation explicite. Pas d'annulation par pile ici : la
   * restauration fidèle d'une séance (attributs + prescription figée) vit dans le calendrier,
   * et la dupliquer à moitié serait pire que de demander une confirmation franche.
   */
  async remove(s: CoachDaySession): Promise<void> {
    this.closeMenu();
    const ok = await this.confirm.ask({
      title: 'Supprimer la séance ?',
      message: `« ${s.title} » du ${frDate(s.scheduledDate)}, chez ${s.athleteName}. `
        + `Cette suppression est définitive.`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    this.workouts.delete(s.athleteId, s.workoutId).subscribe({
      next: () => {
        this.sessions.update((l) => l.filter((x) => x.workoutId !== s.workoutId));
        this.toast.success('Séance supprimée');
      },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  // --- Planifier -------------------------------------------------------------

  readonly planOpen = signal(false);
  readonly planAthletes = signal<AthleteSummary[]>([]);
  readonly planTemplates = signal<WorkoutTemplate[]>([]);
  readonly planning = signal(false);
  planAthleteId = '';
  planDate = '';
  planQuery = '';

  /** Modèles filtrés par la recherche : une bibliothèque entière ne se parcourt pas au pouce. */
  readonly planMatches = computed(() => {
    const q = this.planFilter().trim().toLowerCase();
    const list = this.planTemplates();
    if (!q) return list.slice(0, 8);
    return list.filter((t) => t.name.toLowerCase().includes(q)).slice(0, 8);
  });
  private readonly planFilter = signal('');
  onPlanQuery(): void { this.planFilter.set(this.planQuery); }

  openPlan(): void {
    this.planOpen.set(true);
    this.planDate = this.date();
    this.closeMenu();
    if (!this.planAthletes().length) {
      this.athletes.list({ status: 'ACTIVE' }).subscribe({
        next: (p) => this.planAthletes.set(p.content),
        error: () => this.toast.error('Athlètes indisponibles.'),
      });
    }
    if (!this.planTemplates().length) {
      this.templates.listAll().subscribe({
        next: (t) => this.planTemplates.set(t),
        error: () => this.planTemplates.set([]),
      });
    }
  }

  closePlan(): void {
    this.planOpen.set(false);
    this.planQuery = '';
    this.planFilter.set('');
  }

  /** Planifie un modèle de la bibliothèque : mêmes cibles figées que depuis le calendrier. */
  planTemplate(t: WorkoutTemplate): void {
    if (!this.planAthleteId) { this.toast.warning('Choisis un athlète.'); return; }
    this.planning.set(true);
    this.courses.schedule(this.planAthleteId, t.id, { date: this.planDate }).subscribe({
      next: () => {
        this.planning.set(false);
        this.closePlan();
        this.toast.success(`${t.name} planifiée le ${frDate(this.planDate)}`);
        if (this.planDate === this.date()) this.loadDay();
      },
      error: () => { this.planning.set(false); this.toast.error('Planification impossible.'); },
    });
  }

  /**
   * Séance vierge : crée la séance puis ouvre l'éditeur de structure. C'est le seul geste de
   * cet écran qui mène à un écran de conception — on l'assume, il est explicite.
   */
  planBlank(): void {
    if (!this.planAthleteId) { this.toast.warning('Choisis un athlète.'); return; }
    this.planning.set(true);
    const athleteId = this.planAthleteId;
    this.workouts.create(athleteId, {
      scheduledDate: this.planDate, type: 'ENDURANCE', title: 'Séance', notes: null, steps: [],
    }).subscribe({
      next: (w) => {
        this.planning.set(false);
        this.closePlan();
        this.router.navigate(['/app/athletes', athleteId, 'workouts', w.id, 'structure']);
      },
      error: () => { this.planning.set(false); this.toast.error('Création impossible.'); },
    });
  }

  // --- Affichage -------------------------------------------------------------

  typeLabel(type: string | null): string {
    return type ? (WORKOUT_TYPE_LABELS[type as WorkoutType] ?? type) : '';
  }

  statusLabel(status: CoachDaySession['status']): string {
    return { PLANNED: 'Prévu', COMPLETED: 'Réalisé', PARTIAL: 'Partiel', MISSED: 'Manqué' }[status];
  }

  /** Extrait d'un commentaire : la ligne reste lisible, le détail est derrière le lien. */
  excerpt(text: string, max = 110): string {
    return text.length > max ? text.slice(0, max).trimEnd() + '…' : text;
  }

  /**
   * « Rien de prévu » n'est pas une erreur : c'est une information, et souvent une bonne. Encore
   * faut-il l'avoir vérifié — un chargement en échec n'autorise pas à annoncer une journée calme.
   */
  readonly quiet = computed(() =>
    !this.loading() && !this.failed()
    && !this.alerts().length && !this.reviews().length && !this.unread().length);

  private stampNow(): void {
    const iso = new Date().toISOString();
    this.lastLoadedAt.set(iso);
    try { localStorage.setItem(STAMP_KEY, iso); } catch { /* horodatage non persisté */ }
  }

  /** « aujourd'hui à 7 h 12 », « hier à 21 h 04 », ou la date pleine au-delà. */
  freshness(): string {
    const iso = this.lastLoadedAt();
    if (!iso) return '';
    const d = new Date(iso);
    const heure = d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    const jour = isoOf(d);
    if (jour === todayIso()) return `aujourd'hui à ${heure}`;
    const hier = new Date();
    hier.setDate(hier.getDate() - 1);
    if (jour === isoOf(hier)) return `hier à ${heure}`;
    return `${d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' })} à ${heure}`;
  }
}

const SCOPE_LABELS: Record<Scope, string> = {
  mine: 'Mes athlètes',
  all: 'Tout le club',
  private: 'Mes privés',
  club: 'Mes athlètes club',
};

function readStamp(): string | null {
  try { return localStorage.getItem(STAMP_KEY); } catch { return null; }
}

function readScope(): Scope {
  try {
    const v = localStorage.getItem(SCOPE_KEY);
    if (v === 'all' || v === 'mine' || v === 'private' || v === 'club') return v;
  } catch { /* stockage indisponible */ }
  return 'mine';
}

function todayIso(): string {
  return isoOf(new Date());
}

function isoOf(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function frDate(iso: string): string {
  return new Date(iso + 'T12:00:00').toLocaleDateString('fr-FR', {
    weekday: 'short', day: 'numeric', month: 'short',
  });
}
