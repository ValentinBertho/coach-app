import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  HostListener,
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
import { ActivityService } from '../../core/services/activity.service';
import { MessageService } from '../../core/services/message.service';
import { Workout } from '../../core/models/workout.model';
import { Activity } from '../../core/models/activity.model';
import { frNumber } from '../../core/utils/number-format';
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
  imports: [RouterLink, DatePipe, IconComponent, FormsModule],
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
          <span class="fq__bar-r">
          <!-- Lire les retours l'un après l'autre, répondre et passer au suivant, sans quitter
               l'écran : le geste du matin. Chaque retour coûtait un aller-retour vers sa séance. -->
          <button type="button" class="btn btn-primary btn-sm" (click)="openReader(0)">
            <app-icon name="play" [size]="15" /> Traiter en série
          </button>
          <button type="button" class="btn btn-ghost btn-sm" (click)="markSelected()"
                  [disabled]="selected().size === 0 || marking()">
            <app-icon name="check" [size]="15" />
            {{ marking() ? 'Traitement…' : 'Marquer traité' + (selected().size ? ' (' + selected().size + ')' : '') }}
          </button>
          </span>
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
                <button type="button" class="fq__title" (click)="openReader($index)" title="Lire ce retour et enchaîner">{{ it.title }}</button>
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
                  <a class="btn btn-ghost btn-sm" [routerLink]="['/app/athletes', it.athleteId, 'workouts', it.sessionId]" [queryParams]="{ from: 'journee' }">Voir la séance</a>
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

      @if (current(); as it) {
        <div class="fr-backdrop" (click)="closeReader()"></div>
        <aside class="fr" role="dialog" aria-modal="true" [attr.aria-label]="'Retour de ' + it.athleteName">
          <header class="fr__hd">
            <button type="button" class="icon-btn" (click)="step(-1)" [disabled]="readerIndex() === 0"
                    aria-label="Retour précédent" title="Précédent (K)"><app-icon name="chevron-left" [size]="18" /></button>
            <span class="fr__pos">{{ (readerIndex() ?? 0) + 1 }} / {{ items().length }}</span>
            <button type="button" class="icon-btn" (click)="step(1)" [disabled]="(readerIndex() ?? 0) >= items().length - 1"
                    aria-label="Retour suivant" title="Suivant (J)"><app-icon name="chevron-right" [size]="18" /></button>
            <button type="button" class="icon-btn fr__close" (click)="closeReader()" aria-label="Fermer" title="Fermer (Échap)">
              <app-icon name="x" [size]="18" />
            </button>
          </header>

          <div class="fr__body">
            <div class="fr__who">
              <app-icon [name]="it.kind === 'STRENGTH' ? 'dumbbell' : 'footprints'" [size]="16" />
              <a [routerLink]="['/app/athletes', it.athleteId]" class="fr__athlete">{{ it.athleteName }}</a>
              <span class="field-hint">{{ it.sessionDate | date: 'EEEE d MMM' }}</span>
            </div>
            <h3 class="fr__title">{{ it.title }}</h3>

            @if (it.kind === 'COURSE') {
              <div class="fr__pvr">
                <div><span class="fr__lb">Prévu</span><strong>{{ plannedText() || '—' }}</strong></div>
                <div><span class="fr__lb">Réalisé</span><strong>{{ doneText() || (detailLoading() ? '…' : 'pas d’activité rattachée') }}</strong></div>
              </div>
            }

            <div class="fq__metrics fr__metrics">
              @if (it.feel != null) {
                <span class="badge fq__feel" [style.--fc]="feelColor(it.feel)">{{ feelText(it.feel) }}</span>
              }
              @if (it.rpe != null) { <span class="badge badge-neutral">RPE {{ it.rpe }}/10</span> }
              @if (it.fatigue != null) { <span class="badge badge-neutral">Fatigue {{ it.fatigue }}/10</span> }
              @if (it.pain != null) {
                <span class="badge" [class.badge-danger]="it.pain >= 5" [class.badge-warning]="it.pain >= 3 && it.pain < 5"
                      [class.badge-neutral]="it.pain < 3">Douleur {{ it.pain }}/10</span>
              }
            </div>
            @if (it.injuries.length) {
              <p class="fq__injuries">
                <app-icon name="alert-triangle" [size]="14" />
                @for (i of it.injuries; track $index) { <span>{{ injury(i) }}@if (i.note) { — « {{ i.note }} »}</span> }
              </p>
            }
            @if (it.comment) { <blockquote class="fr__comment">{{ it.comment }}</blockquote> }
            @else { <p class="field-hint">Pas de commentaire écrit.</p> }

            @if (existingComment()) {
              <p class="fr__prev"><span class="fr__lb">Ton mot déjà envoyé</span> {{ existingComment() }}</p>
            }

            <label class="fr__lb" for="fr-reply">{{ it.kind === 'COURSE' ? 'Ton mot sur la séance' : 'Ta réponse (messagerie)' }}</label>
            <textarea id="fr-reply" class="form-control fr__reply" rows="3" [(ngModel)]="draft"
                      [placeholder]="it.kind === 'COURSE' ? 'Il s’affichera sur sa séance, avec une notification.' : 'Elle part dans votre fil de messages.'"
                      (keydown.control.enter)="sendAndNext()" (keydown.meta.enter)="sendAndNext()"></textarea>
          </div>

          <footer class="fr__ft">
            <button type="button" class="btn btn-primary" (click)="sendAndNext()" [disabled]="!draft.trim() || readerBusy()"
                    title="Envoie ton mot, marque le retour traité et passe au suivant (Ctrl+Entrée)">
              Envoyer et suivant <app-icon name="arrow-right" [size]="15" />
            </button>
            <button type="button" class="btn btn-ghost" (click)="ackAndNext()" [disabled]="readerBusy()"
                    title="Traite le retour et prévient l'athlète que tu l'as vu">Vu 👏</button>
            <button type="button" class="btn btn-ghost" (click)="silentAndNext()" [disabled]="readerBusy()"
                    title="Traite le retour sans rien envoyer">Traité</button>
            <a class="btn btn-ghost fr__open" [routerLink]="it.kind === 'COURSE' ? ['/app/athletes', it.athleteId, 'workouts', it.sessionId] : ['/app/athletes', it.athleteId, 'programme']"
               [queryParams]="{ from: 'journee' }">Ouvrir</a>
          </footer>
        </aside>
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
      .fq__bar-r { display: inline-flex; gap: var(--sp-2); flex-wrap: wrap; }
      button.fq__title {
        background: none; border: 0; padding: 0; text-align: left; cursor: pointer;
        font: inherit; font-size: var(--text-sm); color: var(--ink-2);
      }
      button.fq__title:hover { color: var(--primary); text-decoration: underline; }
      .fr-backdrop { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.25); z-index: 300; }
      .fr {
        position: fixed; top: 0; right: 0; bottom: 0; z-index: 301;
        width: min(520px, 100vw); display: flex; flex-direction: column;
        background: var(--paper); box-shadow: var(--shadow-lg);
      }
      .fr__hd {
        display: flex; align-items: center; gap: var(--sp-2);
        padding: calc(var(--sp-3) + env(safe-area-inset-top, 0px)) var(--sp-4) var(--sp-3);
        border-bottom: 1px solid var(--hairline);
      }
      .fr__pos { font-weight: 700; font-variant-numeric: tabular-nums; color: var(--ink-2); }
      .fr__close { margin-left: auto; }
      .fr__body { flex: 1; overflow-y: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
      .fr__who { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; color: var(--ink-3); }
      .fr__athlete { font-weight: 800; color: var(--ink); text-decoration: none; }
      .fr__title { margin: 0; font-size: var(--text-lg); }
      .fr__pvr { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-2); }
      .fr__pvr > div { display: flex; flex-direction: column; gap: 2px; padding: var(--sp-2) var(--sp-3); background: var(--paper-sunk); border-radius: var(--radius-sm); }
      .fr__lb { font-size: var(--text-2xs); font-weight: 800; text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-4); }
      .fr__metrics { justify-content: flex-start; }
      .fr__comment { margin: 0; padding: var(--sp-2) var(--sp-3); border-left: 3px solid var(--primary); background: var(--primary-wash); white-space: pre-wrap; }
      .fr__prev { margin: 0; font-size: var(--text-sm); color: var(--ink-2); display: flex; flex-direction: column; gap: 2px; }
      .fr__reply { min-height: 88px; }
      .fr__ft {
        display: flex; gap: var(--sp-2); flex-wrap: wrap; align-items: center;
        padding: var(--sp-3) var(--sp-4) calc(var(--sp-3) + env(safe-area-inset-bottom, 0px));
        border-top: 1px solid var(--hairline);
      }
      .fr__open { margin-left: auto; }
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
        /* Les trois boutons tenaient sur deux lignes : une seule, plus serrée. */
        .fq__actions {
          flex-wrap: nowrap;
          gap: 4px;
        }
        .fq__actions .btn {
          padding-inline: var(--sp-2);
          white-space: nowrap;
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

  private readonly activities = inject(ActivityService);
  private readonly messageService = inject(MessageService);

  // --- Lecture en série -------------------------------------------------------
  // Un panneau, un retour à la fois : prévu/réalisé, ressenti, commentaire, et une réponse qui
  // traite le retour en partant. On enchaîne au clavier (J / K), comme une boîte de réception.

  /** Position du retour lu dans la file ; null = panneau fermé. */
  readonly readerIndex = signal<number | null>(null);
  readonly current = computed(() => {
    const i = this.readerIndex();
    return i == null ? null : (this.items()[i] ?? null);
  });
  readonly readerBusy = signal(false);
  readonly detailLoading = signal(false);
  readonly detail = signal<{ w: Workout | null; a: Activity | null }>({ w: null, a: null });
  draft = '';

  readonly existingComment = computed(() => this.detail().w?.coachComment ?? null);
  readonly plannedText = computed(() => {
    const w = this.detail().w;
    return w ? volumeText(w.targetDistanceM, w.targetDurationS) : '';
  });
  readonly doneText = computed(() => {
    const a = this.detail().a;
    if (!a) return '';
    const v = volumeText(a.distanceM, a.durationS);
    return a.avgHr ? `${v} · ${a.avgHr} bpm` : v;
  });

  openReader(index: number): void {
    if (!this.items().length) return;
    this.expanded.set(true);
    this.readerIndex.set(Math.min(index, this.items().length - 1));
    this.loadDetail();
  }

  closeReader(): void {
    this.readerIndex.set(null);
    this.draft = '';
  }

  step(delta: number): void {
    const i = this.readerIndex();
    if (i == null) return;
    const next = i + delta;
    if (next < 0 || next >= this.items().length) return;
    this.readerIndex.set(next);
    this.draft = '';
    this.loadDetail();
  }

  @HostListener('document:keydown', ['$event'])
  onKey(ev: KeyboardEvent): void {
    if (this.readerIndex() == null) return;
    const tag = (ev.target as HTMLElement | null)?.tagName;
    if (ev.key === 'Escape') { this.closeReader(); return; }
    if (tag === 'TEXTAREA' || tag === 'INPUT') return;
    if (ev.key === 'j' || ev.key === 'J') { ev.preventDefault(); this.step(1); }
    if (ev.key === 'k' || ev.key === 'K') { ev.preventDefault(); this.step(-1); }
  }

  private loadDetail(): void {
    const it = this.current();
    this.detail.set({ w: null, a: null });
    if (!it || it.kind !== 'COURSE') return;
    this.detailLoading.set(true);
    const key = this.key(it);
    this.workouts.get(it.athleteId, it.sessionId).subscribe({
      next: (w) => { if (this.isCurrent(key)) this.detail.update((d) => ({ ...d, w })); },
      error: () => undefined,
    });
    this.activities.forWorkout(it.athleteId, it.sessionId).subscribe({
      next: (a) => {
        if (!this.isCurrent(key)) return;
        this.detail.update((d) => ({ ...d, a }));
        this.detailLoading.set(false);
      },
      error: () => this.detailLoading.set(false),
    });
  }

  private isCurrent(key: string): boolean {
    const c = this.current();
    return !!c && this.key(c) === key;
  }

  /**
   * Envoie le mot du coach puis traite le retour. Sur une séance course, le mot va SUR la séance
   * (un seul canal par question — l'athlète répond depuis elle) ; une séance de prépa physique
   * n'a pas ce champ, la réponse part donc dans le fil de messages.
   */
  sendAndNext(): void {
    const it = this.current();
    const text = this.draft.trim();
    if (!it || !text || this.readerBusy()) return;
    const send: Observable<unknown> =
      it.kind === 'COURSE'
        ? this.workouts.setCoachComment(it.athleteId, it.sessionId, text)
        : this.messageService.coachSend(it.athleteId, `À propos de « ${it.title} » : ${text}`);
    this.readerBusy.set(true);
    send.subscribe({
      next: () =>
        this.setReviewed(it.kind, it.athleteId, it.sessionId, true).subscribe({
          next: () => this.advance(it, `Mot envoyé à ${it.athleteName.split(' ')[0]}`),
          error: () => this.advance(it, `Mot envoyé à ${it.athleteName.split(' ')[0]}`),
        }),
      error: () => {
        this.readerBusy.set(false);
        this.toast.error('Envoi impossible.');
      },
    });
  }

  ackAndNext(): void {
    const it = this.current();
    if (!it || this.readerBusy()) return;
    this.readerBusy.set(true);
    const call: Observable<unknown> =
      it.kind === 'STRENGTH'
        ? this.strength.acknowledgeScheduled(it.athleteId, it.sessionId)
        : this.workouts.acknowledge(it.athleteId, it.sessionId);
    call.subscribe({
      next: () => this.advance(it, `${it.athleteName.split(' ')[0]} a été prévenu 👏`),
      error: () => { this.readerBusy.set(false); this.toast.error('Action impossible.'); },
    });
  }

  silentAndNext(): void {
    const it = this.current();
    if (!it || this.readerBusy()) return;
    this.readerBusy.set(true);
    this.setReviewed(it.kind, it.athleteId, it.sessionId, true).subscribe({
      next: () => this.advance(it, 'Retour marqué comme traité'),
      error: () => { this.readerBusy.set(false); this.toast.error('Action impossible.'); },
    });
  }

  /** Retire le retour traité ; le suivant prend sa place sous le même index. */
  private advance(it: FeedbackQueueItem, message: string): void {
    this.readerBusy.set(false);
    this.draft = '';
    const k = this.key(it);
    const i = this.readerIndex() ?? 0;
    this.removeWhere((x) => this.key(x) === k);
    this.selected.update((s) => { const n = new Set(s); n.delete(k); return n; });
    this.toast.success(message);
    if (!this.items().length) {
      this.closeReader();
      this.toast.info('File vide : tous les retours sont traités.');
      return;
    }
    this.readerIndex.set(Math.min(i, this.items().length - 1));
    this.loadDetail();
  }

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

/** « 12,0 km · 1 h 05 », « 45 min », ou rien. */
function volumeText(distanceM: number | null | undefined, durationS: number | null | undefined): string {
  const parts: string[] = [];
  if (distanceM && distanceM > 0) parts.push(`${frNumber(distanceM / 1000, 1)} km`);
  if (durationS && durationS > 0) {
    const h = Math.floor(durationS / 3600);
    const m = Math.round((durationS % 3600) / 60);
    parts.push(h ? `${h} h ${String(m).padStart(2, '0')}` : `${m} min`);
  }
  return parts.join(' · ');
}
