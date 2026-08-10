import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Activity } from '../../core/models/activity.model';
import { WorkoutPrescription } from '../../core/models/course.model';
import {
  STATUS_BADGE, STATUS_LABELS, WORKOUT_TYPE_LABELS, WORKOUT_TYPE_META, Workout, needsFeedback,
} from '../../core/models/workout.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ActivityChartComponent } from '../../shared/components/activity-chart/activity-chart.component';
import { ActivityLapsComponent } from '../../shared/components/activity-laps/activity-laps.component';
import { ActivityRouteMapComponent } from '../../shared/components/activity-route-map/activity-route-map.component';
import { CoursePrescriptionViewComponent } from '../../shared/components/course-prescription-view/course-prescription-view.component';
import { FeedbackRecapComponent } from '../../shared/components/feedback-recap/feedback-recap.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PlannedStats, RealizedStats, SessionStatsComponent } from '../../shared/components/session-stats/session-stats.component';
import { TimeInZoneBarComponent } from '../../shared/components/time-in-zone-bar/time-in-zone-bar.component';
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
            @if (activity()) {
              <section class="card">
                <app-session-stats [realized]="realized()!" [planned]="planned()" />
              </section>
            } @else {
              <section class="card wd-nodata">
                <p class="field-hint">
                  Aucune sortie n'est encore rattachée à cette séance. Importe ta trace ou
                  connecte ta montre, et les chiffres viendront se poser ici.
                </p>
                <a routerLink="/athlete/activities" class="btn btn-ghost btn-sm">Mes activités</a>
              </section>
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
                <app-feedback-recap [feel]="w.feel" [rpe]="w.rpe" [fatigue]="w.fatigue"
                                    [pain]="w.pain" [injuries]="w.injuries"
                                    [comment]="w.athleteComment" />
              } @else {
                <p class="field-hint">
                  Tu n'as pas encore débriefé cette séance. Ton coach n'a que les chiffres de ta
                  montre — c'est ton ressenti qui lui dit comment adapter la suite.
                </p>
              }
            </section>

            @if (w.coachComment) {
              <section class="card wd-coach">
                <div class="wd-block-hd">
                  <h2 class="wd-h2">Le mot de ton coach</h2>
                  @if (w.coachCommentAt) {
                    <span class="field-hint metric">{{ w.coachCommentAt | date: 'd MMM, HH:mm' }}</span>
                  }
                </div>
                <blockquote class="wd-quote">{{ w.coachComment }}</blockquote>
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

    .wd-coach { border-left: 3px solid var(--primary); }
    .wd-quote { margin: 0; color: var(--ink); font-style: italic; }

    .empty { text-align: center; display: flex; flex-direction: column; align-items: center; gap: var(--sp-2); }
  `],
})
export class AthleteWorkoutDetailComponent implements OnInit {
  /** Lié par le routeur (`withComponentInputBinding`). */
  readonly workoutId = input.required<string>();

  private readonly portal = inject(AthletePortalService);
  private readonly feedbackSheet = viewChild(WorkoutFeedbackSheetComponent);

  readonly state = signal<State>('loading');
  readonly workout = signal<Workout | null>(null);
  readonly activity = signal<Activity | null>(null);
  readonly prescription = signal<WorkoutPrescription | null>(null);

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
    return {
      durationS: calc?.totalDurationS ?? w.targetDurationS ?? null,
      distanceM: calc?.totalDistanceM ?? w.targetDistanceM ?? null,
    };
  });

  ngOnInit(): void { this.load(); }

  private load(): void {
    const id = this.workoutId();
    this.state.set('loading');
    this.portal.workout(id).subscribe({
      next: (w) => { this.workout.set(w); this.state.set('ready'); },
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
