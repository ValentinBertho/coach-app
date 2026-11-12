import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { Injury, injuryLabel } from '../../core/models/injury.model';
import {
  CoachDashboardService,
  FeedbackQueueItem,
} from '../../core/services/coach-dashboard.service';
import { CoachScope } from '../../core/services/coach-scope.service';
import { FEEL_COLORS, feelLabel } from '../../shared/components/feel-scale';
import { StrengthService } from '../../core/services/strength.service';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * File « retours à traiter », montée dans « Ma journée » : la seule boîte de tri du coach.
 *
 * <p>Elle avait son propre écran (« Retours »), à côté de « Ma journée » qui en montrait cinq et
 * du tableau de bord qui en affichait le total, chacun sur son périmètre et sa fenêtre de temps.
 * Le coach voyait trois chiffres pour la même file et trois portes pour la même tâche. Elle vit
 * maintenant à un seul endroit, sur le périmètre partagé et sur quatorze jours, comme la pastille
 * du menu et le KPI du tableau de bord.</p>
 *
 * <p><b>Une action principale, pas une par ligne.</b> Chaque carte portait son bouton plein
 * « Traité » : cinquante-sept boutons primaires empilés. Les lignes se cochent, et un seul bouton
 * « Marquer traité » agit sur la sélection. « Vu 👏 », qui prévient l'athlète, reste sur chaque
 * ligne, en retrait.</p>
 */
@Component({
  selector: 'app-feedback-queue',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, IconComponent],
  template: `
      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 96px;"></div></div>
      } @else if (failed()) {
        <div class="card fq__empty">
          <p class="field-hint">Les retours n'ont pas pu être chargés.</p>
          <button type="button" class="btn btn-ghost btn-sm" (click)="load()">Réessayer</button>
        </div>
      } @else if (items().length === 0) {
        <div class="card fq__empty">
          <p class="field-hint">
            Aucun retour en attente sur les {{ days }} derniers jours. Les nouveaux retours de
            séance (ressenti, RPE, douleur, commentaire) arriveront ici.
          </p>
        </div>
      } @else {
        <div class="fq__bar">
          <label class="fq__check">
            <input type="checkbox" [checked]="allSelected()" [indeterminate]="someSelected()"
                   (change)="toggleAll()" />
            <span>Tout sélectionner ({{ items().length }})</span>
          </label>
          <button type="button" class="btn btn-primary btn-sm" (click)="markSelected()"
                  [disabled]="selected().size === 0 || marking()">
            <app-icon name="check" [size]="15" />
            {{ marking() ? 'Traitement…' : 'Marquer traité' + (selected().size ? ' (' + selected().size + ')' : '') }}
          </button>
        </div>

        <ul class="fq">
          @for (it of shown(); track key(it)) {
            <li class="fq__row" [class.fq__row--alert]="(it.pain ?? 0) >= 3 || !!it.injuries.length"
                [class.fq__row--selected]="selected().has(key(it))">
              <input type="checkbox" class="fq__select" [checked]="selected().has(key(it))"
                     (change)="toggle(it)"
                     [attr.aria-label]="'Sélectionner le retour de ' + it.athleteName + ', ' + it.title" />

              <div class="fq__id">
                <span class="fq__top">
                  <app-icon class="fq__kind" [name]="it.kind === 'STRENGTH' ? 'dumbbell' : 'footprints'" [size]="14" />
                  <a class="fq__athlete" [routerLink]="['/app/athletes', it.athleteId]">{{ it.athleteName }}</a>
                  <span class="field-hint">{{ it.sessionDate | date: 'EEE d MMM' }}</span>
                </span>
                <span class="fq__title">{{ it.title }}</span>
              </div>

              <div class="fq__metrics">
                <!-- La sensation d'abord : c'est comment la séance a été vécue, ce que le RPE ne
                     dit pas. -->
                @if (it.feel != null) {
                  <span class="badge fq__feel" [style.--fc]="feelColor(it.feel)">{{ feelText(it.feel) }}</span>
                }
                @if (it.rpe != null) { <span class="badge badge-neutral">RPE {{ it.rpe }}/10</span> }
                @if (it.fatigue != null) { <span class="badge badge-neutral">Fatigue {{ it.fatigue }}/10</span> }
                @if (it.pain != null) {
                  <span class="badge" [class.badge-danger]="it.pain >= 5"
                        [class.badge-warning]="it.pain >= 3 && it.pain < 5"
                        [class.badge-neutral]="it.pain < 3">Douleur {{ it.pain }}/10</span>
                }
              </div>

              <!-- Une blessure nommée se décide en premier : elle a sa ligne, en toutes lettres. -->
              @if (it.injuries.length) {
                <p class="fq__injuries">
                  <app-icon name="alert-triangle" [size]="14" />
                  @for (i of it.injuries; track $index) {
                    <span>{{ injury(i) }}@if (i.note) { — « {{ i.note }} »}</span>
                  }
                </p>
              }
              @if (it.comment) { <p class="fq__comment">« {{ excerpt(it.comment) }} »</p> }

              <div class="fq__actions">
                @if (it.kind === 'COURSE') {
                  <a class="btn btn-ghost btn-sm" [routerLink]="['/app/athletes', it.athleteId, 'workouts', it.sessionId]">Voir la séance</a>
                } @else {
                  <a class="btn btn-ghost btn-sm" [routerLink]="['/app/athletes', it.athleteId, 'programme']">Voir le programme</a>
                }
                <button type="button" class="btn btn-ghost btn-sm" (click)="reply.emit(it)">
                  <app-icon name="message-square" [size]="15" /> Répondre
                </button>
                <!-- « Vu 👏 » traite le retour et le fait savoir à l'athlète : le silence ne doit
                     pas être la réponse par défaut. -->
                <button type="button" class="btn btn-ghost btn-sm" (click)="acknowledge(it)"
                        [disabled]="busy().has(key(it))"
                        title="Traite le retour et prévient l'athlète que tu l'as vu">
                  Vu 👏
                </button>
              </div>
            </li>
          }
        </ul>

        <!-- Cinq d'abord : la journée ne doit pas devenir un mur de cartes. Le reste se déplie ici,
             sans changer d'écran ni perdre sa sélection. -->
        @if (hidden() > 0) {
          <button type="button" class="btn btn-ghost fq__more" (click)="expanded.set(true)">
            Afficher {{ hidden() === 1 ? "l'autre retour" : 'les ' + hidden() + ' autres retours' }}
          </button>
        }
      }
  `,
  styles: [
    `
      .fq__bar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--sp-3);
        flex-wrap: wrap;
        margin-bottom: var(--sp-2);
      }
      .fq__check {
        display: inline-flex;
        align-items: center;
        gap: var(--sp-2);
        min-height: 44px;
        font-size: var(--text-sm);
        font-weight: 600;
        color: var(--ink-2);
        cursor: pointer;
      }
      .fq__check input,
      .fq__select {
        width: 20px;
        height: 20px;
        accent-color: var(--primary);
        cursor: pointer;
      }
      .fq {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--sp-2);
      }
      .fq__row {
        display: grid;
        gap: var(--sp-2) var(--sp-3);
        grid-template-columns: auto 1fr auto;
        grid-template-areas:
          'select id metrics'
          '. injuries injuries'
          '. comment comment'
          '. actions actions';
        align-items: start;
        padding: var(--sp-3);
        border: 1px solid var(--hairline);
        border-radius: var(--radius-md);
        background: var(--paper);
      }
      .fq__row--alert {
        border-left: 3px solid var(--danger);
      }
      .fq__row--selected {
        border-color: var(--primary-light);
        background: var(--primary-wash);
      }
      .fq__select {
        grid-area: select;
        margin-top: 2px;
      }
      .fq__id {
        grid-area: id;
        display: flex;
        flex-direction: column;
        gap: 2px;
        min-width: 0;
      }
      .fq__top {
        display: flex;
        align-items: center;
        gap: var(--sp-2);
        flex-wrap: wrap;
      }
      .fq__kind {
        color: var(--ink-3);
      }
      .fq__athlete {
        font-weight: 700;
        color: var(--ink);
        text-decoration: none;
      }
      .fq__athlete:hover {
        text-decoration: underline;
      }
      .fq__title {
        font-size: var(--text-sm);
        color: var(--ink-2);
      }
      .fq__metrics {
        grid-area: metrics;
        display: flex;
        gap: var(--sp-2);
        flex-wrap: wrap;
        justify-content: flex-end;
      }
      .fq__feel {
        background: color-mix(in srgb, var(--fc) 16%, var(--paper));
        color: var(--fc);
        border: 1px solid color-mix(in srgb, var(--fc) 40%, transparent);
      }
      .fq__injuries {
        grid-area: injuries;
        margin: 0;
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--sp-1) var(--sp-2);
        font-size: var(--text-sm);
        font-weight: 700;
        color: var(--form-red);
      }
      .fq__comment {
        grid-area: comment;
        margin: 0;
        font-size: var(--text-sm);
        color: var(--ink-2);
        font-style: italic;
      }
      .fq__actions {
        grid-area: actions;
        display: flex;
        gap: var(--sp-2);
        flex-wrap: wrap;
      }
      .fq__more {
        width: 100%;
        margin-top: var(--sp-2);
      }
      .fq__empty {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--sp-3);
        flex-wrap: wrap;
      }
      .fq__empty p {
        margin: 0;
      }
      @media (max-width: 640px) {
        .fq__row {
          grid-template-columns: auto 1fr;
          grid-template-areas:
            'select id'
            '. metrics'
            '. injuries'
            '. comment'
            '. actions';
        }
        .fq__metrics {
          justify-content: flex-start;
        }
      }
    `,
  ],
})
export class FeedbackQueueComponent implements OnInit {
  private readonly dashboard = inject(CoachDashboardService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly workouts = inject(WorkoutService);
  private readonly strength = inject(StrengthService);
  private readonly toast = inject(ToastService);

  /** Périmètre partagé avec le reste de « Ma journée ». */
  readonly scope = input.required<CoachScope>();
  /** « Répondre » : la feuille de réponse appartient à l'écran hôte. */
  readonly reply = output<FeedbackQueueItem>();
  /** Nombre de retours en attente, pour l'écran hôte (« rien ne t'attend »). */
  readonly countChange = output<number>();

  /** Fenêtre de la file : la même que la pastille du menu et le KPI du tableau de bord. */
  readonly days = 14;
  private static readonly SHOWN = 5;

  readonly items = signal<FeedbackQueueItem[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly busy = signal<Set<string>>(new Set());
  readonly selected = signal<Set<string>>(new Set());
  readonly marking = signal(false);
  readonly expanded = signal(false);

  readonly shown = computed(() =>
    this.expanded() ? this.items() : this.items().slice(0, FeedbackQueueComponent.SHOWN),
  );
  readonly hidden = computed(() => this.items().length - this.shown().length);
  readonly allSelected = computed(
    () => this.items().length > 0 && this.selected().size === this.items().length,
  );
  readonly someSelected = computed(
    () => this.selected().size > 0 && this.selected().size < this.items().length,
  );

  injury = (i: Injury) => injuryLabel(i);

  /** Recharge à chaque changement de périmètre. */
  private readonly scopeSync = effect(() => {
    this.scope();
    untracked(() => this.load());
  });

  ngOnInit(): void {
    this.runNotificationAction();
  }

  load(): void {
    this.loading.set(true);
    this.dashboard.feedbackQueue(this.scope(), this.days).subscribe({
      next: (list) => {
        this.items.set(list);
        this.selected.set(new Set());
        this.loading.set(false);
        this.failed.set(false);
        this.countChange.emit(list.length);
      },
      error: () => {
        this.items.set([]);
        this.loading.set(false);
        this.failed.set(true);
        this.countChange.emit(0);
      },
    });
  }

  key(it: FeedbackQueueItem): string {
    return `${it.kind}:${it.sessionId}`;
  }

  toggle(it: FeedbackQueueItem): void {
    const k = this.key(it);
    this.selected.update((s) => {
      const n = new Set(s);
      if (n.has(k)) n.delete(k);
      else n.add(k);
      return n;
    });
  }

  /** Tout cocher porte sur toute la file, y compris les lignes encore repliées. */
  toggleAll(): void {
    this.selected.set(
      this.allSelected() ? new Set() : new Set(this.items().map((it) => this.key(it))),
    );
  }

  /** Marque la sélection comme traitée, en un seul appel. */
  markSelected(): void {
    const keys = this.selected();
    if (keys.size === 0 || this.marking()) return;
    const chosen = this.items().filter((it) => keys.has(this.key(it)));
    this.marking.set(true);
    this.dashboard.markFeedbackReviewed(chosen, this.scope(), this.days).subscribe({
      next: ({ marked }) => {
        this.marking.set(false);
        this.removeWhere((it) => keys.has(this.key(it)));
        this.selected.set(new Set());
        this.toast.success(
          marked > 1 ? `${marked} retours marqués comme traités` : 'Retour marqué comme traité',
        );
      },
      error: () => {
        this.marking.set(false);
        this.toast.error('Action impossible.');
      },
    });
  }

  /** « Vu 👏 » : traite le retour et prévient l'athlète. */
  acknowledge(it: FeedbackQueueItem): void {
    const k = this.key(it);
    if (this.busy().has(k)) return;
    this.busy.update((s) => new Set(s).add(k));
    const call: Observable<unknown> =
      it.kind === 'STRENGTH'
        ? this.strength.acknowledgeScheduled(it.athleteId, it.sessionId)
        : this.workouts.acknowledge(it.athleteId, it.sessionId);
    call.subscribe({
      next: () => {
        this.release(k);
        this.removeWhere((x) => this.key(x) === k);
        this.toast.success(`${it.athleteName.split(' ')[0]} a été prévenu 👏`);
      },
      error: () => {
        this.release(k);
        this.toast.error('Action impossible.');
      },
    });
  }

  /**
   * Exécute l'action rapide d'une notification : « Traité » depuis l'écran verrouillé.
   *
   * <p>Le bouton du système ouvre l'application avec le retour désigné en clair
   * (<code>?review=COURSE:&lt;athlète&gt;:&lt;séance&gt;</code>). L'action est idempotente, le
   * paramètre est retiré de l'URL aussitôt pour qu'un rechargement ne la rejoue pas, elle est
   * annulable depuis le toast, et un format inconnu n'écrit rien.</p>
   */
  private runNotificationAction(): void {
    const raw = this.route.snapshot.queryParamMap.get('review');
    if (!raw) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {},
      replaceUrl: true,
    });

    const [kind, athleteId, sessionId] = raw.split(':');
    if ((kind !== 'COURSE' && kind !== 'STRENGTH') || !athleteId || !sessionId) return;

    this.setReviewed(kind, athleteId, sessionId, true).subscribe({
      next: () => {
        this.removeWhere((x) => this.key(x) === `${kind}:${sessionId}`);
        this.toast.withAction('Retour marqué comme traité', 'Annuler', () =>
          this.setReviewed(kind, athleteId, sessionId, false).subscribe({
            next: () => {
              this.load();
              this.refreshBadge();
              this.toast.info('Retour remis dans la file');
            },
            error: () => this.toast.error('Annulation impossible.'),
          }),
        );
      },
      error: () => this.toast.error("Ce retour n'a pas pu être marqué comme traité."),
    });
  }

  private setReviewed(
    kind: 'COURSE' | 'STRENGTH',
    athleteId: string,
    sessionId: string,
    reviewed: boolean,
  ): Observable<unknown> {
    return kind === 'STRENGTH'
      ? this.strength.markScheduledReviewed(athleteId, sessionId, reviewed)
      : this.workouts.markReviewed(athleteId, sessionId, reviewed);
  }

  /** Retire des lignes de la file, puis remet la pastille du menu au même chiffre. */
  private removeWhere(pred: (it: FeedbackQueueItem) => boolean): void {
    this.items.update((l) => l.filter((x) => !pred(x)));
    this.countChange.emit(this.items().length);
    this.refreshBadge();
  }

  private refreshBadge(): void {
    this.dashboard.refreshPendingReviews().subscribe({ error: () => undefined });
  }

  private release(k: string): void {
    this.busy.update((s) => {
      const n = new Set(s);
      n.delete(k);
      return n;
    });
  }

  protected feelText(value: number): string {
    const word = feelLabel(value);
    return word ? word.charAt(0).toUpperCase() + word.slice(1) : '';
  }

  protected feelColor(value: number): string {
    return FEEL_COLORS[value] ?? 'var(--ink-3)';
  }

  /** Extrait du commentaire : la ligne reste lisible, le détail est derrière le lien. */
  excerpt(comment: string): string {
    return comment.length > 160 ? comment.slice(0, 160).trimEnd() + '…' : comment;
  }
}
