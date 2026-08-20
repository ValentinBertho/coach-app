import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Activity } from '../../core/models/activity.model';
import { WorkoutPrescription } from '../../core/models/course.model';
import {
  STATUS_BADGE, STATUS_LABELS, WORKOUT_TYPE_LABELS, WORKOUT_TYPE_META, Workout, needsFeedback,
} from '../../core/models/workout.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { MessageService } from '../../core/services/message.service';
import { ToastService } from '../../core/services/toast.service';
import { PaceReferenceService } from '../../core/services/pace-reference.service';
import { plannedVolume } from '../../core/utils/planned-volume';
import { ActivityChartComponent } from '../../shared/components/activity-chart/activity-chart.component';
import { ActivityLapsComponent } from '../../shared/components/activity-laps/activity-laps.component';
import { ActivityRouteMapComponent } from '../../shared/components/activity-route-map/activity-route-map.component';
import { CoursePrescriptionViewComponent } from '../../shared/components/course-prescription-view/course-prescription-view.component';
import { FeedbackRecapComponent } from '../../shared/components/feedback-recap/feedback-recap.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PlannedStats, RealizedStats, SessionStatsComponent } from '../../shared/components/session-stats/session-stats.component';
import { TimeInZoneBarComponent } from '../../shared/components/time-in-zone-bar/time-in-zone-bar.component';
import { BottomSheetComponent } from '../../shared/components/ui';
import { ComplianceVerdictComponent } from '../../shared/components/compliance-verdict/compliance-verdict.component';
import { Compliance } from '../../core/models/decision.model';
import { WorkoutFeedbackSheetComponent } from '../../shared/components/workout-feedback-sheet/workout-feedback-sheet.component';

type State = 'loading' | 'ready' | 'error';

const SOURCE_LABELS: Record<string, string> = {
  STRAVA: 'Strava',
  GARMIN: 'Garmin',
  COROS: 'Coros',
  FILE: 'Fichier importé',
  MANUAL: 'Saisie manuelle',
};

/**
 * Ma séance, en détail — la fiche que l'athlète ouvre en rentrant de courir.
 *
 * <p><b>Pourquoi cet écran.</b> Le portail athlète savait tout montrer, mais jamais au même
 * endroit : la prescription vivait dans « Aujourd'hui », les chiffres de la montre dans « Mes
 * activités » (repliés dans un accordéon), le ressenti dans une feuille qui se refermait sans
 * rien laisser à relire. Aucun écran ne répondait à la question qu'on se pose vraiment — <em>ma
 * séance de samedi, elle a donné quoi ?</em> — qui demande la prescription, le réalisé, l'écart
 * entre les deux, la courbe, les tours et le mot du coach côte à côte. C'est la fiche que Nolio
 * met au centre de son produit, et elle manquait entièrement.</p>
 *
 * <p>L'ordre de lecture est celui du coureur : ce que ça a donné (chiffres), comment ça s'est
 * passé (courbe, zones), ce qu'on en a dit (débrief, mot du coach), puis le détail — tours,
 * tracé — et enfin ce qui était prévu, en bas, parce que c'est la question qu'on se pose en
 * dernier une fois la séance faite.</p>
 */
@Component({
  selector: 'app-athlete-workout-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe, RouterLink, IconComponent, SessionStatsComponent, ActivityChartComponent,
    ActivityLapsComponent, ActivityRouteMapComponent, TimeInZoneBarComponent,
    FeedbackRecapComponent, CoursePrescriptionViewComponent, WorkoutFeedbackSheetComponent,
    ComplianceVerdictComponent,
    BottomSheetComponent, FormsModule,
  ],
  template: `
    @switch (state()) {
      @case ('loading') {
        <div class="wd"><div class="card"><div class="skeleton" style="height:140px"></div></div></div>
      }
      @case ('error') {
        <div class="wd">
          <div class="card empty">
            <h2>Séance introuvable</h2>
            <p class="field-hint">Elle a peut-être été retirée de ton programme.</p>
            <a routerLink="/athlete/calendar" class="btn btn-primary btn-sm">Mon agenda</a>
          </div>
        </div>
      }
      @case ('ready') {
        @if (workout(); as w) {
          <div class="wd">
            <header class="wd-head" [style.--tc]="typeColor()">
              <a routerLink="/athlete/calendar" class="wd-back">
                <app-icon name="arrow-left" [size]="18" /> Agenda
              </a>
              <span class="wd-date metric">{{ w.scheduledDate | date: 'EEEE d MMMM y' }}</span>
              <h1 class="wd-title">{{ w.title }}</h1>
              <div class="wd-tags">
                <span class="badge" [class]="statusBadge[w.status]">{{ statusLabels[w.status] }}</span>
                <span class="wd-type"><app-icon [name]="typeIcon()" [size]="14" /> {{ typeLabels[w.type] }}</span>
                @if (activity(); as a) {
                  <span class="wd-src"><app-icon name="watch" [size]="14" /> {{ sourceLabel(a.source) }}</span>
                }
              </div>
            </header>

            <!-- 1. Ce que ça a donné. Réalisé en grand, prévu juste en dessous. -->
            @if (activity(); as a) {
              <section class="card">
                <app-session-stats [realized]="realized()!" [planned]="planned()" />
                <!-- Le rapprochement se trompe — deux sorties le même jour, une séance déplacée —
                     et son erreur était sans recours côté athlète : seul le coach pouvait la
                     défaire. C'est pourtant l'athlète qui sait ce qu'il a couru. -->
                <div class="wd-match">
                  <span class="field-hint">Sortie {{ sourceLabel(a.source) }} rattachée à cette séance.</span>
                  <button type="button" class="btn btn-ghost btn-sm" (click)="detach(a)" [disabled]="matching()">
                    <app-icon name="x" [size]="15" /> Ce n'est pas la bonne
                  </button>
                </div>
              </section>
            } @else {
              <section class="card wd-nodata">
                <p class="field-hint">
                  Aucune sortie n'est encore rattachée à cette séance. Importe ta trace ou
                  connecte ta montre, et les chiffres viendront se poser ici.
                </p>
                <div class="wd-nodata-actions">
                  @if (attachable().length) {
                    <button type="button" class="btn btn-primary btn-sm" (click)="pickOpen.set(true)">
                      <app-icon name="plus" [size]="15" /> Rattacher une sortie
                    </button>
                  }
                  <a routerLink="/athlete/activities" class="btn btn-ghost btn-sm">Mes activités</a>
                </div>
              </section>
            }

            <!-- 1 bis. Séance tenue ? — le verdict, avant la courbe. Une phrase se lit ; une
                 courbe se déchiffre, et personne ne déchiffre en rentrant de courir. -->
            @if (compliance(); as c) {
              @if (c.scorePct !== null) {
                <section class="wd-compliance">
                  <app-compliance-verdict [data]="c" />
                </section>
              }
            }

            <!-- 2. Comment ça s'est passé. -->
            @if (activity(); as a) {
              <section class="card">
                <app-activity-chart [activityId]="a.id" />
              </section>

              <section class="card">
                <app-time-in-zone-bar [activityId]="a.id" />
              </section>
            }

            <!-- 3. Ce qu'on en a dit. -->
            <section class="card">
              <div class="wd-block-hd">
                <h2 class="wd-h2">Mon débrief</h2>
                <button type="button" class="btn btn-ghost btn-sm" (click)="openDebrief()">
                  <app-icon name="pencil" [size]="15" /> {{ hasFeedback() ? 'Modifier' : 'Débriefer' }}
                </button>
              </div>
              @if (hasFeedback()) {
                <app-feedback-recap [feel]="w.feel" [rpe]="w.rpe" [targetRpe]="w.targetRpe" [fatigue]="w.fatigue"
                                    [pain]="w.pain" [injuries]="w.injuries"
                                    [comment]="w.athleteComment" />
              } @else {
                <p class="field-hint">
                  Tu n'as pas encore débriefé cette séance. Ton coach n'a que les chiffres de ta
                  montre — c'est ton ressenti qui lui dit comment adapter la suite.
                </p>
              }
            </section>

            <!-- Le « vu 👏 ». Effacé quand le coach a aussi écrit : un commentaire vaut mieux
                 qu'un applaudissement, et afficher les deux ferait passer l'un pour un doublon. -->
            @if (w.coachAcknowledgedAt && !w.coachComment) {
              <section class="card wd-ack">
                <p class="wd-ack__txt">👏 Ton coach a vu ta séance</p>
                <span class="field-hint metric">{{ w.coachAcknowledgedAt | date: 'd MMM, HH:mm' }}</span>
              </section>
            }

            @if (w.coachComment) {
              <section class="card wd-coach">
                <div class="wd-block-hd">
                  <h2 class="wd-h2">Le mot de ton coach</h2>
                  @if (w.coachCommentAt) {
                    <span class="field-hint metric">{{ w.coachCommentAt | date: 'd MMM, HH:mm' }}</span>
                  }
                </div>
                <blockquote class="wd-quote">{{ w.coachComment }}</blockquote>

                <!--
                  Répondre depuis la séance, pas depuis la messagerie. Le coach pose sa question
                  ici — « tu te sentais facile sur les allures ? » — et exiger d'aller rouvrir un
                  fil, retrouver de quelle sortie il parlait et recontextualiser à la main est
                  précisément ce qui fait qu'on ne répond pas.

                  La réponse part malgré tout dans le fil de messagerie, rattachée à la séance :
                  un second canal de conversation se serait désynchronisé du premier, et le coach
                  aurait eu deux endroits où regarder.
                -->
                @if (replySent()) {
                  <p class="wd-reply-done field-hint">
                    <app-icon name="check" [size]="14" /> Réponse envoyée à ton coach.
                    <a routerLink="/athlete/messages">Voir la conversation</a>
                  </p>
                } @else {
                  <div class="wd-reply">
                    <label class="wd-reply__lb" for="wd-reply">Répondre</label>
                    <textarea id="wd-reply" class="form-control" rows="2"
                              [ngModel]="reply()" (ngModelChange)="reply.set($event)"
                              placeholder="Ta réponse à ton coach…"></textarea>
                    <div class="wd-reply__actions">
                      <button type="button" class="btn btn-primary btn-sm"
                              [disabled]="!reply().trim() || replyBusy()"
                              (click)="sendReply()">
                        {{ replyBusy() ? 'Envoi…' : 'Envoyer' }}
                      </button>
                    </div>
                  </div>
                }
              </section>
            }

            <!-- 4. Le détail : tours et tracé. -->
            @if (activity(); as a) {
              <section class="card">
                <app-activity-laps [activityId]="a.id" />
              </section>

              <section class="card">
                <app-activity-route-map [activityId]="a.id" [height]="260" />
              </section>
            }

            <!-- 5. Ce qui était prévu — la question qu'on se pose en dernier. -->
            @if (prescriptionHasContent()) {
              <section class="card">
                <h2 class="wd-h2">Ce qui était prévu</h2>
                <app-course-prescription-view [prescription]="prescription()" />
              </section>
            }
            @if (w.notes) {
              <section class="card">
                <h2 class="wd-h2">Intention de la séance</h2>
                <p class="wd-notes">{{ w.notes }}</p>
              </section>
            }
          </div>

          <app-workout-feedback-sheet (saved)="onFeedbackSaved($event)" />

          <!-- Choix d'une sortie à rattacher : celles des jours voisins, non encore rattachées. -->
          <app-bottom-sheet [(open)]="pickOpen" title="Quelle sortie ?">
            @if (attachable().length) {
              <ul class="wd-pick">
                @for (a of attachable(); track a.id) {
                  <li>
                    <button type="button" class="wd-pick-row" (click)="attach(a)" [disabled]="matching()">
                      <span class="wd-pick-id">
                        <strong>{{ a.title || 'Sortie' }}</strong>
                        <span class="field-hint">{{ a.activityDate | date: 'EEEE d MMMM' }}</span>
                      </span>
                      <span class="wd-pick-kpi metric">
                        @if (a.distanceM) { {{ (a.distanceM / 1000).toFixed(1) }} km }
                        @if (a.durationS) { · {{ durationLabel(a.durationS) }} }
                      </span>
                    </button>
                  </li>
                }
              </ul>
            } @else {
              <p class="field-hint">Aucune sortie libre autour de cette date.</p>
            }
          </app-bottom-sheet>
        }
      }
    }
  `,
  styles: [`
    /* padding-top : safe-area de la coquille athlète (PWA) — sinon le titre passe sous l'heure. */
    .wd { max-width: 560px; margin-inline: auto; padding: var(--sp-4); padding-top: max(var(--sp-4), var(--safe-top, 0px)); display: flex; flex-direction: column; gap: var(--sp-3); }

    .wd-head { display: flex; flex-direction: column; gap: var(--sp-1); }
    .wd-back { display: inline-flex; align-items: center; gap: var(--sp-1); color: var(--primary); font-weight: 700; text-decoration: none; min-height: 44px; }
    .wd-date { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); font-weight: 700; }
    .wd-title { margin: 0; font-size: var(--text-2xl); font-weight: 800; color: var(--ink); }
    .wd-tags { display: flex; flex-wrap: wrap; align-items: center; gap: var(--sp-2); margin-top: var(--sp-1); }
    .wd-type { display: inline-flex; align-items: center; gap: 4px; font-size: var(--text-sm); font-weight: 700; color: var(--tc); }
    .wd-src { display: inline-flex; align-items: center; gap: 4px; font-size: var(--text-sm); color: var(--ink-3); }

    .wd-block-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); margin-bottom: var(--sp-2); }
    .wd-h2 { margin: 0; font-size: var(--text-md); font-weight: 700; color: var(--ink); }
    .wd-notes { margin: 0; color: var(--ink-2); white-space: pre-wrap; }
    .wd-nodata { display: flex; flex-direction: column; align-items: flex-start; gap: var(--sp-2); }
    .wd-nodata-actions { display: flex; flex-wrap: wrap; gap: var(--sp-2); }

    .wd-match {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2);
      flex-wrap: wrap; margin-top: var(--sp-3);
      padding-top: var(--sp-3); border-top: 1px solid var(--hairline);
    }

    .wd-pick { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .wd-pick-row {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3);
      width: 100%; min-height: 56px; padding: var(--sp-3);
      border: 1px solid var(--hairline); border-radius: var(--radius);
      background: var(--paper); cursor: pointer; text-align: left;
    }
    .wd-pick-id { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .wd-pick-id strong { color: var(--ink); }
    .wd-pick-id .field-hint { text-transform: capitalize; }
    .wd-pick-kpi { font-weight: 700; color: var(--ink-2); white-space: nowrap; }

    .wd-coach { border-left: 3px solid var(--primary); }
    .wd-quote { margin: 0; color: var(--ink); font-style: italic; }

    /* La réponse est sous la citation, séparée d'un filet : on lit d'abord, on répond ensuite. */
    .wd-reply { display: flex; flex-direction: column; gap: var(--sp-2); margin-top: var(--sp-3); padding-top: var(--sp-3); border-top: 1px solid var(--hairline); }
    .wd-reply__lb { font-size: var(--text-sm); font-weight: 700; color: var(--ink-2); }
    .wd-reply__actions { display: flex; justify-content: flex-end; }
    .wd-reply-done { display: flex; align-items: center; gap: var(--sp-1); margin: var(--sp-3) 0 0; padding-top: var(--sp-3); border-top: 1px solid var(--hairline); }
    .wd-reply-done a { color: var(--primary); }
    /* Plus léger que le mot du coach : c'est une attention, pas une consigne. */
    .wd-ack {
      border-left: 3px solid var(--primary);
      display: flex; align-items: baseline; justify-content: space-between;
      gap: var(--sp-3); flex-wrap: wrap;
    }
    .wd-ack__txt { margin: 0; font-weight: 700; color: var(--primary); }

    .empty { text-align: center; display: flex; flex-direction: column; align-items: center; gap: var(--sp-2); }
  `],
})
export class AthleteWorkoutDetailComponent implements OnInit {
  /** Lié par le routeur (`withComponentInputBinding`). */
  readonly workoutId = input.required<string>();

  private readonly portal = inject(AthletePortalService);
  private readonly paceReference = inject(PaceReferenceService);
  private readonly toast = inject(ToastService);
  private readonly messages = inject(MessageService);
  private readonly feedbackSheet = viewChild(WorkoutFeedbackSheetComponent);

  /** Mon allure d'endurance, seule base admise pour estimer un volume écrit en durée. */
  readonly referencePace = signal<number | null>(null);

  readonly state = signal<State>('loading');
  readonly workout = signal<Workout | null>(null);

  /** Réponse en cours de saisie au mot du coach, et son état d'envoi. */
  readonly reply = signal('');
  readonly replyBusy = signal(false);
  readonly replySent = signal(false);
  readonly activity = signal<Activity | null>(null);
  readonly prescription = signal<WorkoutPrescription | null>(null);

  /**
   * « Séance tenue ? » — le verdict bloc par bloc. Nul tant qu'aucune sortie n'est rattachée ou
   * qu'aucune fourchette n'était prescrite : dans ces cas-là, il n'y a rien à juger, et le
   * composant se tait plutôt que d'afficher un zéro.
   */
  readonly compliance = signal<Compliance | null>(null);

  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE;
  readonly typeLabels = WORKOUT_TYPE_LABELS;

  readonly prescriptionHasContent = computed(() => {
    const c = this.prescription()?.calculated;
    return !!c && (c.warmup.length + c.main.length + c.cooldown.length) > 0;
  });

  /**
   * Un débrief compte dès qu'un seul de ses champs est rempli : un athlète qui a tapé un visage
   * et rien d'autre s'est prononcé, et lui afficher « pas encore débriefé » l'inviterait à
   * recommencer ce qu'il vient de faire.
   */
  readonly hasFeedback = computed(() => {
    const w = this.workout();
    if (!w) return false;
    return w.feel != null || w.rpe != null || w.fatigue != null || w.pain != null
      || !!w.athleteComment || (w.injuries?.length ?? 0) > 0;
  });

  readonly realized = computed<RealizedStats | null>(() => {
    const a = this.activity();
    if (!a) return null;
    return {
      durationS: a.durationS,
      distanceM: a.distanceM,
      elevationGainM: a.elevationGainM,
      paceSPerKm: a.paceSPerKm,
      avgHr: a.avgHr,
      maxHr: a.maxHr,
      avgCadence: a.avgCadence,
      avgPowerW: a.avgPowerW,
      calories: a.calories,
    };
  });

  /**
   * Cibles prescrites. Les totaux calculés de la structure priment sur les cibles globales
   * saisies à la main : c'est la prescription réellement construite bloc par bloc, celle que
   * l'athlète a suivie.
   */
  readonly planned = computed<PlannedStats | null>(() => {
    const w = this.workout();
    if (!w) return null;
    const calc = this.prescription()?.calculated;
    const durationS = calc?.totalDurationS ?? w.targetDurationS ?? null;
    // Une séance écrite en durée, sans allure prescrite, n'a pas de distance calculable : le
    // total ne comptait alors que les blocs chiffrables (les éducatifs, quelques centaines de
    // mètres) et annonçait « 0,1 km » pour une heure de course. On préfère un ordre de grandeur
    // tiré de l'allure d'endurance de l'athlète, affiché comme tel.
    const volume = plannedVolume(
      calc?.totalDistanceM ?? w.targetDistanceM ?? null, durationS, this.referencePace());
    return {
      durationS,
      distanceM: volume?.distanceM ?? null,
      distanceIndicative: volume?.indicative ?? false,
    };
  });

  ngOnInit(): void { this.load(); }

  /**
   * Envoie la réponse au coach, rattachée à cette séance.
   *
   * <p>Elle part dans le fil de messagerie plutôt que dans un canal propre à la séance : ouvrir
   * un second fil aurait donné au coach deux endroits où regarder, et c'est exactement ainsi
   * qu'un message se perd.</p>
   */
  sendReply(): void {
    const body = this.reply().trim();
    if (!body || this.replyBusy()) {
      return;
    }
    this.replyBusy.set(true);
    this.messages.mySend(body, this.workoutId()).subscribe({
      next: () => {
        this.replyBusy.set(false);
        this.replySent.set(true);
        this.reply.set('');
        this.toast.success('Réponse envoyée à ton coach.');
      },
      error: () => {
        this.replyBusy.set(false);
        this.toast.error('Envoi impossible — réessaie dans un instant.');
      },
    });
  }

  private load(): void {
    const id = this.workoutId();
    this.state.set('loading');
    this.portal.workout(id).subscribe({
      next: (w) => {
        this.workout.set(w);
        this.state.set('ready');
        // Ouvrir la fiche vaut lecture : le mot cesse de remonter sur « Aujourd'hui ». L'échec
        // est silencieux — ne pas avoir marqué un message comme lu n'est pas une panne qui
        // mérite d'interrompre quelqu'un venu regarder sa séance.
        if (w.coachComment && !w.coachCommentReadAt) {
          this.portal.readCoachComment(id).subscribe({ error: () => undefined });
        }
      },
      error: () => this.state.set('error'),
    });
    // Les trois autres appels sont indépendants : une séance sans sortie rattachée, ou sans
    // prescription structurée, reste parfaitement lisible sans eux.
    this.portal.workoutActivity(id).subscribe({
      next: (a) => this.activity.set(a),
      error: () => this.activity.set(null),
    });
    this.portal.workoutPrescription(id).subscribe({
      next: (p) => this.prescription.set(p),
      error: () => this.prescription.set(null),
    });
    this.portal.compliance(id).subscribe({
      next: (c) => this.compliance.set(c),
      error: () => this.compliance.set(null),
    });
    this.paceReference.mine().subscribe({
      next: (p) => this.referencePace.set(p),
      error: () => this.referencePace.set(null),
    });
    // Sorties des jours voisins, pour proposer un rattachement manuel quand l'automatique s'abstient.
    this.portal.activities().subscribe({
      next: (list) => this.activities.set(list),
      error: () => this.activities.set([]),
    });
  }

  protected openDebrief(): void {
    const w = this.workout();
    if (!w) return;
    const a = this.activity();
    this.feedbackSheet()?.openFor(w, {
      activity: a ? {
        source: a.source, distanceM: a.distanceM, durationS: a.durationS, avgHr: a.avgHr,
      } : null,
    });
  }

  protected onFeedbackSaved(updated: Workout): void {
    this.workout.set(updated);
  }

  // --- Rattacher / détacher une sortie ------------------------------------

  /** Fenêtre de recherche autour de la séance : au-delà, ce n'est plus la même journée d'entraînement. */
  private static readonly PICK_WINDOW_DAYS = 3;

  readonly pickOpen = signal(false);
  readonly matching = signal(false);
  private readonly activities = signal<Activity[]>([]);

  /**
   * Sorties proposables : celles des jours voisins qui ne sont rattachées à rien.
   *
   * <p>Une sortie déjà rattachée à une autre séance n'est pas offerte : la déplacer ici laisserait
   * l'autre séance sans réalisé, sans que l'athlète l'ait demandé. Il la détache d'abord.</p>
   */
  readonly attachable = computed<Activity[]>(() => {
    const w = this.workout();
    if (!w) return [];
    const day = new Date(w.scheduledDate + 'T00:00:00').getTime();
    const window = AthleteWorkoutDetailComponent.PICK_WINDOW_DAYS * 86_400_000;
    return this.activities()
      .filter((a) => !a.matchedWorkoutId)
      .filter((a) => Math.abs(new Date(a.activityDate + 'T00:00:00').getTime() - day) <= window)
      .sort((a, b) => b.activityDate.localeCompare(a.activityDate));
  });

  protected attach(a: Activity): void {
    if (this.matching()) return;
    this.matching.set(true);
    this.portal.matchActivity(a.id, this.workoutId()).subscribe({
      next: () => {
        this.matching.set(false);
        this.pickOpen.set(false);
        this.toast.success('Sortie rattachée à ta séance');
        this.load();
      },
      error: () => { this.matching.set(false); this.toast.error('Rattachement impossible.'); },
    });
  }

  protected detach(a: Activity): void {
    if (this.matching()) return;
    this.matching.set(true);
    this.portal.matchActivity(a.id, null).subscribe({
      next: () => {
        this.matching.set(false);
        // La séance redevient « à faire » côté serveur : on recharge tout plutôt que de deviner.
        this.toast.info('Sortie détachée');
        this.load();
      },
      error: () => { this.matching.set(false); this.toast.error('Détachement impossible.'); },
    });
  }

  /** « 42:10 » ou « 1:08:32 », comme partout ailleurs. */
  protected durationLabel(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return h ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
      : `${m}:${String(s).padStart(2, '0')}`;
  }

  protected typeColor(): string {
    const w = this.workout();
    return w ? WORKOUT_TYPE_META[w.type].color : 'var(--primary)';
  }

  protected typeIcon(): string {
    const w = this.workout();
    return w ? WORKOUT_TYPE_META[w.type].icon : 'footprints';
  }

  protected sourceLabel(source: string): string {
    return SOURCE_LABELS[source] ?? 'Importée';
  }

  /** Le bouton de débrief reste offert tant que la séance n'est pas clôturée. */
  protected stillOpen(): boolean {
    const w = this.workout();
    return !!w && needsFeedback(w);
  }
}
