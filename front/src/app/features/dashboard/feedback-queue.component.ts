import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Injury, injuryLabel } from '../../core/models/injury.model';
import { CoachDashboardService, FeedbackQueueItem } from '../../core/services/coach-dashboard.service';
import { FEEL_COLORS, feelLabel } from '../../shared/components/feel-scale';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SegmentedControlComponent, type SegmentOption } from '../../shared/components/ui';
import { HelpHintComponent } from '../help/help-hint.component';

type Scope = 'all' | 'mine' | 'private' | 'club';

/**
 * File « retours à traiter » — destination du KPI du cockpit.
 *
 * Répond au trou de parcours identifié à l'audit : le coach voyait un compteur de retours mais
 * n'avait nulle part où les traiter. Une ligne par retour d'athlète non encore vu (RPE, douleur,
 * commentaire), tous athlètes du périmètre confondus, du plus récent au plus ancien, avec le
 * lien vers le détail de la séance et l'action « marquer comme traité ».
 *
 * Course et force sont unifiées ici comme partout ailleurs dans le produit.
 */
@Component({
  selector: 'app-feedback-queue',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, IconComponent, SegmentedControlComponent, HelpHintComponent],
  template: `
    <section class="page-header">
      <div>
        <h1 class="display-sm">Retours à traiter <app-help-hint section="suivi" label="Aide : suivi & charge" /></h1>
        <p class="subtitle">Les retours de tes athlètes que tu n'as pas encore vus, du plus récent au plus ancien.</p>
      </div>
      <div class="fq__filters">
        <app-segmented-control [options]="scopeOptions" [value]="scope()" (valueChange)="setScope($event)" [ariaLabel]="'Périmètre'" />
        <app-segmented-control [options]="windowOptions" [value]="days()" (valueChange)="setDays($event)" [ariaLabel]="'Profondeur'" />
      </div>
    </section>

    @if (loading()) {
      <div class="card"><div class="skeleton" style="height: 120px;"></div></div>
    } @else if (items().length === 0) {
      <div class="card empty-state">
        <h2>Aucun retour en attente</h2>
        <p class="field-hint">
          Aucun retour en attente sur les {{ days() }} derniers jours. Les nouveaux retours de
          séance (RPE, douleur, commentaire) apparaîtront ici dès que tes athlètes les enverront.
        </p>
        <a routerLink="/app/calendar" class="btn btn-primary">Ouvrir le calendrier</a>
      </div>
    } @else {
      <div class="card">
        <div class="fq__bar">
          <p class="field-hint fq__count">{{ items().length }} retour{{ items().length > 1 ? 's' : '' }} en attente</p>
          <button type="button" class="btn btn-ghost btn-sm" (click)="markAll()" [disabled]="markingAll()">
            <app-icon name="check" [size]="15" />
            {{ markingAll() ? 'Traitement…' : 'Tout marquer comme traité' }}
          </button>
        </div>
        <ul class="fq">
          @for (it of items(); track it.kind + it.sessionId) {
            <li class="fq__row" [class.fq__row--alert]="(it.pain ?? 0) >= 3 || !!it.injuries?.length">
              <span class="fq__kind" [title]="it.kind === 'STRENGTH' ? 'Renforcement' : 'Course'">
                <app-icon [name]="it.kind === 'STRENGTH' ? 'dumbbell' : 'footprints'" [size]="15" />
              </span>

              <div class="fq__id">
                <a class="fq__athlete" [routerLink]="['/app/athletes', it.athleteId]">{{ it.athleteName }}</a>
                <span class="fq__title">{{ it.title }}</span>
                <span class="field-hint metric">{{ it.sessionDate | date: 'EEE d MMM y' }}</span>
              </div>

              <div class="fq__metrics">
                <!-- La sensation d'abord : c'est comment la séance a été vécue, ce que le RPE
                     ne dit pas et que le coach cherchait dans le commentaire libre. -->
                @if (it.feel != null) {
                  <span class="badge fq__feel" [style.--fc]="feelColor(it.feel)">{{ feelLabel(it.feel) }}</span>
                }
                @if (it.rpe != null) { <span class="badge badge-neutral">RPE {{ it.rpe }}/10</span> }
                @if (it.fatigue != null) { <span class="badge badge-neutral">Fatigue {{ it.fatigue }}/10</span> }
                @if (it.pain != null) {
                  <span class="badge" [class.badge-danger]="it.pain >= 5" [class.badge-warning]="it.pain >= 3 && it.pain < 5"
                        [class.badge-neutral]="it.pain < 3">Douleur {{ it.pain }}/10</span>
                }
              </div>

              <!-- Une blessure nommée est ce qui se décide en premier dans cette file : elle a sa
                   ligne, en toutes lettres, et ne se devine pas d'un niveau de douleur. -->
              @if (it.injuries?.length) {
                <p class="fq__injuries">
                  <app-icon name="alert-triangle" [size]="14" />
                  @for (i of it.injuries; track $index) {
                    <span class="fq__injury">{{ injuryLabel(i) }}@if (i.note) { — « {{ i.note }} » }</span>
                  }
                </p>
              }

              @if (it.comment) { <p class="fq__comment">« {{ excerpt(it.comment) }} »</p> }

              <div class="fq__actions">
                @if (it.kind === 'COURSE') {
                  <a class="btn btn-ghost btn-sm" [routerLink]="['/app/athletes', it.athleteId, 'workouts', it.sessionId]">
                    Voir la séance
                  </a>
                } @else {
                  <a class="btn btn-ghost btn-sm" [routerLink]="['/app/athletes', it.athleteId, 'programme']">
                    Voir le programme
                  </a>
                }
                <button type="button" class="btn btn-primary btn-sm" (click)="markReviewed(it)" [disabled]="busy().has(key(it))">
                  <app-icon name="check" [size]="15" /> Marquer comme traité
                </button>
              </div>
            </li>
          }
        </ul>
      </div>
    }
  `,
  styles: [`
    .fq__count { margin: 0 0 var(--sp-3); }
    .fq { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .fq__row {
      display: grid; gap: var(--sp-2) var(--sp-3);
      grid-template-columns: auto 1fr auto;
      grid-template-areas: 'kind id metrics' '. injuries injuries' '. comment comment' '. actions actions';
      align-items: start;
      padding: var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius-md);
      background: var(--paper);
    }
    .fq__row--alert { border-left: 3px solid var(--danger, var(--energy)); }
    .fq__kind { grid-area: kind; color: var(--ink-3); padding-top: 2px; }
    .fq__id { grid-area: id; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .fq__athlete { font-weight: 700; color: var(--ink); text-decoration: none; }
    .fq__athlete:hover { text-decoration: underline; }
    .fq__filters { display: flex; flex-wrap: wrap; gap: var(--sp-2); align-items: center; }
    .fq__bar { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); flex-wrap: wrap; }
    .fq__title { font-size: var(--text-sm); color: var(--ink-2); }
    .fq__metrics { grid-area: metrics; display: flex; gap: var(--sp-2); flex-wrap: wrap; justify-content: flex-end; }
    .fq__feel { background: color-mix(in srgb, var(--fc) 16%, var(--paper)); color: var(--fc); border: 1px solid color-mix(in srgb, var(--fc) 40%, transparent); }
    .fq__injuries {
      grid-area: injuries; margin: 0; display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-1) var(--sp-2);
      font-size: var(--text-sm); font-weight: 700; color: var(--form-red);
    }
    .fq__injury::after { content: ''; }
    .fq__comment { grid-area: comment; margin: 0; font-size: var(--text-sm); color: var(--ink-2); font-style: italic; }
    .fq__actions { grid-area: actions; display: flex; gap: var(--sp-2); flex-wrap: wrap; }
    @media (max-width: 640px) {
      .fq__row { grid-template-columns: auto 1fr; grid-template-areas: 'kind id' '. metrics' '. injuries' '. comment' '. actions'; }
      .fq__metrics { justify-content: flex-start; }
    }
  `],
})
export class FeedbackQueueComponent implements OnInit {
  private readonly dashboardService = inject(CoachDashboardService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly workoutService = inject(WorkoutService);
  private readonly strengthService = inject(StrengthService);
  private readonly toast = inject(ToastService);

  readonly items = signal<FeedbackQueueItem[]>([]);

  /** « Excellente », « Difficile »… — le mot, jamais le chiffre nu : 4/5 ne veut rien dire. */
  protected feelLabel(value: number): string {
    const word = feelLabel(value);
    return word ? word.charAt(0).toUpperCase() + word.slice(1) : '';
  }

  protected feelColor(value: number): string {
    return FEEL_COLORS[value] ?? 'var(--ink-3)';
  }

  protected injuryLabel(injury: Injury): string {
    return injuryLabel(injury);
  }

  readonly loading = signal(true);
  /** Lignes en cours de traitement (évite le double clic). */
  readonly busy = signal<Set<string>>(new Set());

  readonly markingAll = signal(false);

  /**
   * Profondeur de la file. Elle n'en avait aucune : tout retour jamais marqué comme traité y
   * restait pour toujours, et la pastille de navigation ne redescendait jamais. Quatorze jours
   * correspondent à ce qu'un coach peut encore exploiter — au-delà, le retour reste lisible sur
   * la séance.
   */
  readonly days = signal('14');
  readonly windowOptions: SegmentOption[] = [
    { value: '7', label: '7 j' },
    { value: '14', label: '14 j' },
    { value: '30', label: '30 j' },
    { value: '90', label: '90 j' },
  ];

  readonly scope = signal<Scope>('all');
  readonly scopeOptions: SegmentOption[] = [
    { value: 'all', label: 'Tout le club' },
    { value: 'mine', label: 'Mes athlètes' },
    { value: 'private', label: 'Privés' },
    { value: 'club', label: 'Club' },
  ];

  ngOnInit(): void {
    this.load();
    this.runNotificationAction();
  }

  /**
   * Exécute l'action rapide d'une notification : « Traité » depuis l'écran verrouillé.
   *
   * <p>Le bouton du système ouvre cet écran avec le retour désigné en clair
   * (<code>?review=COURSE:&lt;athlète&gt;:&lt;séance&gt;</code>). Trois précautions, parce qu'une
   * écriture déclenchée par une URL n'est pas un clic dans l'application :</p>
   * <ul>
   *   <li><b>idempotente</b> — l'accusé de lecture pose une date ; le rejouer ne casse rien, et
   *       le paramètre est retiré de l'URL aussitôt pour qu'un rechargement ne le relance pas ;</li>
   *   <li><b>annulable</b> — un tap sur un écran verrouillé se fait aussi par erreur, d'où le
   *       « Annuler » du toast, qui remet le retour dans la file ;</li>
   *   <li><b>silencieuse sur un format inconnu</b> — un lien tronqué ne doit rien écrire.</li>
   * </ul>
   */
  private runNotificationAction(): void {
    const raw = this.route.snapshot.queryParamMap.get('review');
    if (!raw) return;
    // On nettoie l'URL avant même d'agir : sans ça, un rechargement de page rejouerait l'action.
    void this.router.navigate([], {
      relativeTo: this.route, queryParams: {}, replaceUrl: true,
    });

    const [kind, athleteId, sessionId] = raw.split(':');
    if ((kind !== 'COURSE' && kind !== 'STRENGTH') || !athleteId || !sessionId) return;

    this.setReviewed(kind, athleteId, sessionId, true).subscribe({
      next: () => {
        this.items.update((l) => l.filter((x) => this.key(x) !== `${kind}:${sessionId}`));
        this.dashboardService.refreshPendingReviews().subscribe({ error: () => undefined });
        this.toast.withAction('Retour marqué comme traité', 'Annuler', () => this.undoReview(kind, athleteId, sessionId));
      },
      error: () => this.toast.error("Ce retour n'a pas pu être marqué comme traité."),
    });
  }

  /** Remet le retour dans la file, et recharge pour qu'il y réapparaisse à sa place. */
  private undoReview(kind: 'COURSE' | 'STRENGTH', athleteId: string, sessionId: string): void {
    this.setReviewed(kind, athleteId, sessionId, false).subscribe({
      next: () => {
        this.load();
        this.dashboardService.refreshPendingReviews().subscribe({ error: () => undefined });
        this.toast.info('Retour remis dans la file');
      },
      error: () => this.toast.error('Annulation impossible.'),
    });
  }

  private setReviewed(kind: 'COURSE' | 'STRENGTH', athleteId: string, sessionId: string,
                      reviewed: boolean): Observable<unknown> {
    return kind === 'STRENGTH'
      ? this.strengthService.markScheduledReviewed(athleteId, sessionId, reviewed)
      : this.workoutService.markReviewed(athleteId, sessionId, reviewed);
  }

  setScope(value: string): void {
    this.scope.set(value as Scope);
    this.load();
  }

  setDays(value: string): void {
    this.days.set(value);
    this.load();
  }

  /** Vide la file en un geste : sinon il fallait un clic par ligne, et personne ne le faisait. */
  markAll(): void {
    if (this.markingAll() || this.items().length === 0) return;
    this.markingAll.set(true);
    this.dashboardService.markAllFeedbackReviewed(this.scope(), Number(this.days())).subscribe({
      next: (res) => {
        this.markingAll.set(false);
        this.items.set([]);
        this.toast.success(`${res.marked} retour${res.marked > 1 ? 's' : ''} marqué${res.marked > 1 ? 's' : ''} comme traité${res.marked > 1 ? 's' : ''}`);
      },
      error: () => { this.markingAll.set(false); this.toast.error('Action impossible.'); },
    });
  }

  load(): void {
    this.loading.set(true);
    this.dashboardService.feedbackQueue(this.scope(), Number(this.days())).subscribe({
      next: (list) => { this.items.set(list); this.loading.set(false); },
      error: () => { this.items.set([]); this.loading.set(false); this.toast.error('Chargement impossible.'); },
    });
  }

  key(it: FeedbackQueueItem): string { return `${it.kind}:${it.sessionId}`; }

  /** Extrait du commentaire : la ligne reste lisible, le détail est derrière le lien. */
  excerpt(comment: string): string {
    return comment.length > 160 ? comment.slice(0, 160).trimEnd() + '…' : comment;
  }

  markReviewed(it: FeedbackQueueItem): void {
    const k = this.key(it);
    if (this.busy().has(k)) return;
    this.busy.update((s) => new Set(s).add(k));

    const call: Observable<unknown> = it.kind === 'STRENGTH'
      ? this.strengthService.markScheduledReviewed(it.athleteId, it.sessionId)
      : this.workoutService.markReviewed(it.athleteId, it.sessionId);

    call.subscribe({
      next: () => {
        this.items.update((l) => l.filter((x) => this.key(x) !== k));
        this.busy.update((s) => { const n = new Set(s); n.delete(k); return n; });
        this.toast.success('Retour marqué comme traité');
      },
      error: () => {
        this.busy.update((s) => { const n = new Set(s); n.delete(k); return n; });
        this.toast.error('Action impossible.');
      },
    });
  }
}
