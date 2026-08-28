import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import {
  STEP_TYPE_LABELS, WORKOUT_TYPE_LABELS, WORKOUT_TYPE_META, Workout, awaitsFeedback, needsFeedback,
} from '../../core/models/workout.model';
import { ScheduledStrength } from '../../core/models/strength.model';
import { Unavailability, UnavailabilityReason } from '../../core/models/unavailability.model';
import { CalendarNoteService } from '../../core/services/calendar-note.service';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Activity } from '../../core/models/activity.model';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { PaceReferenceService } from '../../core/services/pace-reference.service';
import { formatPace, paceFrom } from '../../core/utils/pace';
import { plannedVolume, plannedVolumeLabel, usableDurationS } from '../../core/utils/planned-volume';
import { formatBlockSets, formatBlockVolume } from '../../core/utils/prescription-format';
import { DataOriginTagComponent, IntensityZoneBadgeComponent, type IntensityZone as ZoneNum } from '../../shared/components/physiology';
import { BottomSheetComponent, SegmentedControlComponent } from '../../shared/components/ui';
import {
  AthleteMonthGridComponent, type MonthChip, type MonthDay, type MonthRace, type MonthWeek,
} from './athlete-month-grid.component';
import { WorkoutFeedbackSheetComponent } from '../../shared/components/workout-feedback-sheet/workout-feedback-sheet.component';
import { HelpHintComponent } from '../help/help-hint.component';
import { CycleBannerComponent } from '../../shared/components/cycle-banner/cycle-banner.component';
import { CalendarNote } from '../../core/models/calendar-note.model';
import { WeekOutlook } from '../../core/models/decision.model';
import { CalculatedBlockEntry, courseBlockTypeLabel, CourseBlock, WorkoutPrescription } from '../../core/models/course.model';
import { RaceObjective } from '../../core/models/race.model';

interface DayRow {
  date: string;
  weekday: string;
  dayNum: number;
  isToday: boolean;
  workouts: Workout[];
  strength: ScheduledStrength[];
  activities: Activity[];
  /** Courses visées ce jour-là : l'échéance, pas une séance de plus. */
  races: RaceObjective[];
  unavailability: Unavailability | null;
  /** Mots posés sur la journée : ceux du coach qui me sont adressés, et les miens. */
  notes: CalendarNote[];
  /** Le jour porte-t-il quelque chose une fois le filtre appliqué ? */
  empty: boolean;
}

/** Ce que l'agenda montre : le programme, ce qui a réellement été couru, ou les deux. */
type AgendaView = 'planned' | 'done' | 'both';
interface MoveTarget { kind: 'run' | 'strength'; id: string; title: string; date: string; }
interface PickDay { date: string; label: string; isCurrent: boolean; isPast: boolean; }

function toIso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function mondayOf(d: Date): Date {
  const date = new Date(d);
  const day = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - day);
  date.setHours(0, 0, 0, 0);
  return date;
}

/** Numéro de semaine ISO 8601 — celui que les coureurs et les plans d'entraînement emploient. */
function isoWeek(d: Date): number {
  const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
  // Le jeudi de la semaine décide de l'année ISO à laquelle elle appartient.
  date.setUTCDate(date.getUTCDate() + 4 - (date.getUTCDay() || 7));
  const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
  return Math.ceil(((date.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
}

const REASON_ICON: Record<UnavailabilityReason, string> = {
  INJURY: 'heart-pulse', ILLNESS: 'thermometer', VACATION: 'palmtree', PERSONAL: 'pin', OTHER: 'ban',
};

/**
 * Mon calendrier (athlète, mobile-first) — agenda vertical de la semaine.
 * L'athlète peut DÉPLACER une séance (date) mais jamais la modifier ni la
 * supprimer (invariant CDC §10). Lecture seule + action « Déplacer ».
 */
@Component({
  selector: 'app-athlete-calendar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IconComponent, IntensityZoneBadgeComponent, DataOriginTagComponent, BottomSheetComponent,
    SegmentedControlComponent, WorkoutFeedbackSheetComponent, HelpHintComponent, RouterLink,
    AthleteMonthGridComponent, CycleBannerComponent, FormsModule,
  ],
  template: `
    <header class="cal-top">
      <div class="cal-title">
        <h1 class="display-sm">{{ mode() === 'month' ? 'Mon mois' : 'Ma semaine' }}</h1>
        <app-help-hint section="agenda" label="Aide : mon agenda" />
      </div>

      <!-- Navigation : un pas de mois ou de semaine selon la vue, et le retour à aujourd'hui
           toujours à portée — c'est le geste le plus fréquent après avoir feuilleté. -->
      <div class="week-nav">
        <button type="button" class="btn btn-ghost btn-sm" (click)="shift(-1)"
                [attr.aria-label]="mode() === 'month' ? 'Mois précédent' : 'Semaine précédente'">
          <app-icon name="arrow-left" [size]="16" />
        </button>
        <span class="period">{{ periodLabel() }}</span>
        <button type="button" class="btn btn-ghost btn-sm" (click)="shift(1)"
                [attr.aria-label]="mode() === 'month' ? 'Mois suivant' : 'Semaine suivante'">
          <app-icon name="arrow-right" [size]="16" />
        </button>
        <button type="button" class="btn btn-ghost btn-sm today-btn" (click)="goToday()"
                aria-label="Revenir à aujourd'hui">
          <app-icon name="rotate-ccw" [size]="15" />
        </button>
      </div>

      <div class="cal-controls">
        <app-segmented-control [options]="modeOptions" [value]="mode()"
          (valueChange)="setMode($event)" ariaLabel="Vue du calendrier" />
        <!-- Prévu / Réalisé / Les deux : sans « réalisé », une sortie non programmée — une course,
             un footing improvisé — n'existait nulle part dans l'agenda, alors qu'elle compte dans
             la semaine d'entraînement autant que le reste. -->
        <app-segmented-control [options]="viewOptions" [value]="view()"
          (valueChange)="setView($event)" ariaLabel="Contenu de l'agenda" />
      </div>
    </header>

    <!-- Les cycles posés par le coach : l'agenda montrait les séances sans jamais dire de quelle
         phase elles faisaient partie. Trois semaines de côtes se lisent autrement quand on sait
         qu'elles forment un bloc, et quand il finit. -->
    <app-cycle-banner [cycles]="cycles()" [today]="todayIso" [from]="rangeFrom()" [to]="rangeTo()" />

    <!-- Ce que la semaine affichée demande, dit avant de la vivre. Traduit pour l'athlète : il
         n'a pas à savoir ce qu'est un ACWR pour comprendre « semaine chargée ». -->
    @if (mode() === 'week' && weekWord(); as word) {
      <p class="week-word" [class]="'week-word--' + (weekOutlook()?.level ?? 'normal').toLowerCase()">
        {{ word }}
      </p>
    }

    @if (mode() === 'month') {
      <main class="month">
        <app-athlete-month-grid [weeks]="monthWeeks()" (dayPicked)="openDay($event)" />
        <p class="month-hint field-hint">Touche un jour pour l'ouvrir.</p>
      </main>
    }

    @if (mode() === 'week') {
    <main class="agenda">
      @for (day of days(); track day.date) {
        <section class="day" [class.day--today]="day.isToday" [class.day--unavailable]="day.unavailability"
                 [class.day--rest]="day.empty">
          <div class="day-hd">
            <span class="day-wd">{{ day.weekday }}</span>
            <span class="day-n metric">{{ day.dayNum }}</span>
            @if (day.isToday) { <span class="day-now">aujourd'hui</span> }
            @if (day.unavailability; as u) {
              <span class="day-unavail"><app-icon [name]="reasonIcon[u.reason]" [size]="12" /> indispo</span>
            }
            @if (day.notes.length) {
              <span class="day-note" [title]="day.notes[0].text">
                <app-icon name="pin" [size]="12" /> {{ day.notes.length > 1 ? day.notes.length + ' mots' : 'note' }}
              </span>
            }
            <!-- À droite : le total du jour — la semaine se lit alors en descendant la colonne
                 des chiffres, sans additionner les séances de tête — ou le mot « repos », qui
                 tient sur la même ligne et évite au jour vide de prendre la place d'une séance. -->
            @if (dayTotal(day); as t) {
              <span class="day-sum metric">{{ t }}</span>
            } @else if (day.empty) {
              <span class="day-sum day-sum--rest">repos</span>
            }
          </div>

          <div class="day-items">
            <!-- La course avant tout le reste : elle n'est pas une séance du jour, elle est ce
                 vers quoi la séance du jour travaille. D'où une forme différente — bandeau plein,
                 orange d'énergie, drapeau — que rien d'autre dans l'agenda ne porte. -->
            @for (r of day.races; track r.id) {
              <a class="race" [class.race--past]="r.status !== 'UPCOMING'"
                 [routerLink]="['/athlete/races']">
                <span class="race-l">
                  <app-icon name="flag" [size]="15" />
                  <span class="race-name">{{ r.name }}</span>
                  <span class="race-prio">{{ r.priority }}</span>
                </span>
                <span class="race-r metric">{{ raceSub(r) }}</span>
              </a>
            }
            @for (w of day.workouts; track w.id) {
              <div class="ses ses--run" [style.--type-c]="typeColor(w)">
                <button type="button" class="ses-body" (click)="openDetail(w)">
                  <span class="ses-main">
                    <span class="ses-title">
                      <!-- Le rang n'apparaît que si le coach a ordonné la journée : sans décision
                           de sa part, numéroter afficherait une consigne que personne n'a donnée. -->
                      @if (orderRank(w); as rank) { <span class="ses-rank">{{ rank }}</span> }
                      {{ typeLabels[w.type] }} · {{ w.title }}
                    </span>
                    @if (volumeLabel(w); as km) { <span class="ses-km metric">{{ km }}</span> }
                    <!-- Sans ce repère, rien n'indique qu'une séance passée attend encore un ressenti. -->
                    @if (isLate(w)) { <span class="ses-todo">à noter</span> }
                  </span>
                  <span class="ses-zones">
                    @for (z of zonesOf(w); track z) { <app-intensity-zone-badge [zone]="z" /> }
                  </span>
                </button>
                <button type="button" class="ses-move-btn" (click)="openMove('run', w.id, w.title, w.scheduledDate)">
                  <app-icon name="move" [size]="13" /> déplacer
                </button>
              </div>
            }
            @for (s of day.strength; track s.id) {
              <button type="button" class="ses ses--strength" (click)="openMove('strength', s.id, s.title, s.scheduledDate)">
                <span class="ses-main">
                  <span class="ses-title"><app-icon name="dumbbell" [size]="14" /> {{ s.title }}</span>
                  @if (s.completed) { <span class="badge badge-success">Réalisé</span> }
                </span>
                <span class="ses-move" aria-hidden="true"><app-icon name="move" [size]="13" /> déplacer</span>
              </button>
            }
            <!-- Sorties réellement effectuées. Celles qui ne sont rattachées à aucune séance
                 portent « hors programme » : c'est l'information que l'agenda taisait. -->
            @for (a of day.activities; track a.id) {
              <a class="ses ses--done" [routerLink]="['/athlete/activities']" [queryParams]="{ open: a.id }">
                <span class="ses-main">
                  <span class="ses-title"><app-icon name="check" [size]="14" /> {{ a.title || 'Sortie' }}</span>
                  @if (a.status !== 'MATCHED') { <span class="ses-extra">hors programme</span> }
                </span>
                <span class="ses-facts">
                  @if (a.distanceM) { <span class="metric">{{ (a.distanceM / 1000).toFixed(1) }} km</span> }
                  @if (a.durationS) { <span class="metric">{{ fmtDur(a.durationS) }}</span> }
                  @if (actPace(a); as p) { <span class="metric">{{ p }}/km</span> }
                  @if (a.avgHr) { <span class="metric">{{ a.avgHr }} bpm</span> }
                </span>
              </a>
            }
          </div>
        </section>
      }
    </main>
    }

    <!-- Contenu d'un jour, ouvert depuis la grille du mois : la case ne peut porter que deux
         chiffres, tout le reste (blocs, déplacement, ressenti) vit ici. -->
    <app-bottom-sheet [(open)]="dayOpen" [title]="dayLabel()">
      @if (daySelected(); as d) {
        <div class="dsheet">
          @if (d.unavailability; as u) {
            <p class="dsheet-off"><app-icon [name]="reasonIcon[u.reason]" [size]="14" /> Indisponibilité déclarée</p>
          }

          <!-- Dire une fois ce que les numéros veulent dire : un chiffre seul devant un titre
               pourrait aussi bien être un nombre de séries ou une semaine de bloc. -->
          @if (dayIsOrdered(d.date)) {
            <p class="dsheet-order field-hint">
              <app-icon name="list" [size]="14" /> Ton coach a précisé l'ordre : suis les numéros.
            </p>
          }

          <!-- Les mots de la journée. Ni une séance ni une indisponibilité : le fait simple
               qu'on signale, dans un sens comme dans l'autre. -->
          @for (n of d.notes; track n.id) {
            <div class="dnote" [class.dnote--mine]="n.authorRole === 'ATHLETE'">
              <app-icon name="pin" [size]="14" />
              <span class="dnote-txt">{{ n.text }}</span>
              <span class="dnote-who field-hint">
                {{ n.authorRole === 'ATHLETE' ? 'moi' : (n.authorName || 'coach') }}
              </span>
              @if (n.authorRole === 'ATHLETE') {
                <button type="button" class="icon-btn" (click)="removeNote(n)" aria-label="Retirer ma note">
                  <app-icon name="x" [size]="14" />
                </button>
              }
            </div>
          }

          @if (noteOpen()) {
            <div class="dnote-form">
              <textarea class="form-control" rows="2" [(ngModel)]="noteDraft" maxlength="500"
                        placeholder="Un mot pour ton coach — « je finis tard, je décale d'une heure »"></textarea>
              <div class="dnote-actions">
                <button type="button" class="btn btn-ghost btn-sm" (click)="noteOpen.set(false)">Annuler</button>
                <button type="button" class="btn btn-primary btn-sm"
                        [disabled]="!noteDraft.trim() || savingNote()" (click)="addNote(d.date)">
                  {{ savingNote() ? 'Envoi…' : 'Laisser le mot' }}
                </button>
              </div>
            </div>
          } @else {
            <button type="button" class="btn btn-ghost btn-sm dnote-open" (click)="noteOpen.set(true)">
              <app-icon name="pin" [size]="14" /> Laisser un mot sur cette journée
            </button>
          }
          @for (r of d.races; track r.id) {
            <a class="race" [class.race--past]="r.status !== 'UPCOMING'" [routerLink]="['/athlete/races']">
              <span class="race-l">
                <app-icon name="flag" [size]="15" />
                <span class="race-name">{{ r.name }}</span>
                <span class="race-prio">{{ r.priority }}</span>
              </span>
              <span class="race-r metric">{{ raceSub(r) }}</span>
            </a>
          }
          @for (w of d.workouts; track w.id) {
            <div class="ses ses--run" [style.--type-c]="typeColor(w)">
              <button type="button" class="ses-body" (click)="openDetail(w)">
                <span class="ses-main">
                  <span class="ses-title">
                    @if (orderRank(w); as rank) { <span class="ses-rank">{{ rank }}</span> }
                    {{ typeLabels[w.type] }} · {{ w.title }}
                  </span>
                  @if (volumeLabel(w); as km) { <span class="ses-km metric">{{ km }}</span> }
                  @if (isLate(w)) { <span class="ses-todo">à noter</span> }
                </span>
                <span class="ses-zones">
                  @for (z of zonesOf(w); track z) { <app-intensity-zone-badge [zone]="z" /> }
                </span>
              </button>
              <button type="button" class="ses-move-btn" (click)="openMove('run', w.id, w.title, w.scheduledDate)">
                <app-icon name="move" [size]="13" /> déplacer
              </button>
            </div>
          }
          @for (s of d.strength; track s.id) {
            <button type="button" class="ses ses--strength" (click)="openMove('strength', s.id, s.title, s.scheduledDate)">
              <span class="ses-main">
                <span class="ses-title"><app-icon name="dumbbell" [size]="14" /> {{ s.title }}</span>
                @if (s.completed) { <span class="badge badge-success">Réalisé</span> }
              </span>
              <span class="ses-move" aria-hidden="true"><app-icon name="move" [size]="13" /> déplacer</span>
            </button>
          }
          @for (a of d.activities; track a.id) {
            <a class="ses ses--done" [routerLink]="['/athlete/activities']" [queryParams]="{ open: a.id }">
              <span class="ses-main">
                <span class="ses-title"><app-icon name="check" [size]="14" /> {{ a.title || 'Sortie' }}</span>
                @if (a.status !== 'MATCHED') { <span class="ses-extra">hors programme</span> }
              </span>
              <span class="ses-facts">
                @if (a.distanceM) { <span class="metric">{{ (a.distanceM / 1000).toFixed(1) }} km</span> }
                @if (a.durationS) { <span class="metric">{{ fmtDur(a.durationS) }}</span> }
                @if (actPace(a); as p) { <span class="metric">{{ p }}/km</span> }
                @if (a.rpe != null) { <span class="metric">RPE {{ a.rpe }}</span> }
              </span>
            </a>
          }
          @if (d.empty) {
            <p class="dsheet-empty field-hint">Rien de prévu ni de réalisé ce jour-là.</p>
          }

          <!-- Une sortie improvisée n'avait aucune porte d'entrée depuis l'agenda : il fallait
               deviner qu'elle se saisit dans l'onglet Activités, puis y ressaisir la date du jour
               qu'on venait justement d'ouvrir. Les deux gestes partent d'ici avec la date déjà
               posée, et retombent sur le formulaire existant — contrôle de doublon compris. -->
          <div class="dsheet-add">
            <a class="btn btn-ghost btn-sm" [routerLink]="['/athlete/activities']"
               [queryParams]="{ log: d.date }">
              <app-icon name="pencil" [size]="14" /> Saisir une sortie
            </a>
            <a class="btn btn-ghost btn-sm" [routerLink]="['/athlete/activities']"
               [queryParams]="{ import: 1 }">
              <app-icon name="download" [size]="14" /> Importer un fichier
            </a>
          </div>
        </div>
      }
    </app-bottom-sheet>

    <!-- Déplacer une séance -->
    <app-bottom-sheet [(open)]="moveOpen" title="Déplacer la séance">
      @if (moveTarget(); as t) {
        <p class="move-lead">{{ t.title }}<br /><span class="field-hint">Le contenu de la séance reste inchangé.</span></p>
        <div class="pick-grid">
          @for (p of pickDays(); track p.date) {
            <button type="button" class="pick" [class.pick--current]="p.isCurrent"
                    [disabled]="p.isCurrent || p.isPast" (click)="confirmMove(p.date)">
              {{ p.label }}
            </button>
          }
        </div>
      }
    </app-bottom-sheet>

    <!-- Détail (lecture seule) d'une séance course -->
    <app-bottom-sheet [(open)]="detailOpen" title="Détail de la séance">
      @if (detailWorkout(); as w) {
        <div class="det">
          <div class="det-hd">
            <strong>{{ typeLabels[w.type] }} · {{ w.title }}</strong>
            <div class="det-meta field-hint">
              @if (volumeLabel(w); as km) { <span>{{ km }}</span> }
              @if (w.targetDurationS) { <span>· {{ fmtDur(w.targetDurationS) }}</span> }
            </div>
          </div>
          @if (w.notes) { <p class="det-notes">{{ w.notes }}</p> }

          @if (detailLoading()) {
            <div class="skeleton" style="height: 72px; border-radius: var(--radius);"></div>
          } @else if (detailGroups().length > 0) {
            @for (g of detailGroups(); track g.section) {
              <section class="grp">
                <h4 class="grp-h">{{ g.section }}</h4>
                @for (e of g.entries; track $index) {
                  <div class="blk">
                    <div class="blk-hd">
                      <span class="blk-type">{{ blockLabel(e.block) }}</span>
                    </div>
                    @if (e.calc?.computable) {
                      <div class="targets">
                        @if (e.calc!.paceMinLabel) {
                          <span class="tgt"><app-icon name="footprints" [size]="13" /> {{ e.calc!.paceMinLabel }}–{{ e.calc!.paceMaxLabel }}/km</span>
                        }
                        @if (e.calc!.hrMin != null) {
                          <span class="tgt"><app-icon name="heart-pulse" [size]="13" /> {{ e.calc!.hrMin }}–{{ e.calc!.hrMax }} bpm</span>
                        }
                        @if (e.calc!.rpeMin != null) {
                          <span class="tgt">RPE {{ e.calc!.rpeMin }}–{{ e.calc!.rpeMax }}</span>
                        }
                      </div>
                    }
<!-- Récupération : sa durée (ou sa distance) d'abord, l'allure ensuite si elle est
                         calculable. Seule l'allure était affichée, et uniquement quand elle existait :
                         « 5 × 2000 m » n'indiquait donc pas combien de temps on récupère entre les
                         répétitions — l'information qui fait la séance. -->
                    @if (e.block.recovery) {
                      <div class="targets recov field-hint">
                        récup{{ recoveryVol(e) ? ' · ' + recoveryVol(e) : '' }}
                        @if (e.recoveryCalc?.computable && e.recoveryCalc!.paceMinLabel) {
                          · {{ e.recoveryCalc!.paceMinLabel }}–{{ e.recoveryCalc!.paceMaxLabel }}/km
                        }
                      </div>
                    }
                    <!-- Récup entre séries : un bloc doublé s'annoncerait sinon comme s'il
                         s'enchaînait sans pause. -->
                    @if (setRecoveryVol(e); as sr) {
                      <div class="targets recov field-hint">récup entre séries · {{ sr }}</div>
                    }
                    @if (e.block.note) { <p class="blk-note field-hint">{{ e.block.note }}</p> }
                  </div>
                }
              </section>
            }
            <app-data-origin-tag origin="calcule" label="Allures calculées pour toi" />
          } @else if (w.steps.length > 0) {
            <ul class="blocks">
              @for (s of w.steps; track $index) {
                <li class="blk">
                  <div class="blk-hd">
                    @if (s.zone) { <app-intensity-zone-badge [zone]="zoneNum(s.zone)" /> }
                    <span class="blk-type">{{ stepLabels[s.stepType] }}</span>
                    @if (s.repetitions > 1) { <span class="blk-rep metric">×{{ s.repetitions }}</span> }
                  </div>
                  <div class="blk-target field-hint">
                    @if (stepVolume(s.distanceM, s.durationS); as v) { <span>{{ v }}</span> }
                    @if (s.notes) { <span>· {{ s.notes }}</span> }
                  </div>
                </li>
              }
            </ul>
          } @else {
            <p class="field-hint">Séance libre — pas de blocs détaillés.</p>
          }
          <!-- Une séance passée non clôturée reste notable : sinon son ressenti est perdu. -->
          @if (canRate(w)) {
            <button type="button" class="btn btn-accent btn-lg det-cta" (click)="rate(w)">Noter mon retour</button>
          }
          <!-- Cette feuille montre ce qui était prévu ; la fiche montre ce que ça a donné —
               chiffres de la montre, courbe, tours, tracé et débrief relu. -->
          <a class="btn btn-ghost btn-sm det-cta" [routerLink]="['/athlete/workouts', w.id]"
             (click)="detailOpen.set(false)">
            <app-icon name="chevron-right" [size]="15" /> Voir le détail de la séance
          </a>
          <p class="det-foot field-hint">Lecture seule. Pour décaler la séance, utilise « déplacer ».</p>
        </div>
      }
    </app-bottom-sheet>

    <!-- Feuille de ressenti partagée avec « Aujourd'hui » et l'historique. -->
    <app-workout-feedback-sheet (saved)="onFeedbackSaved($event)" />
  `,
  styles: [`
    /* Le mot de la semaine : une phrase, jamais un ratio. L'athlète n'a pas à connaître
       l'ACWR pour savoir que sa semaine est chargée. */
    .week-word {
      margin: 0 0 var(--sp-3);
      padding: var(--sp-2) var(--sp-3);
      border-radius: var(--radius-sm);
      border-left: 3px solid currentColor;
      font-size: var(--text-sm);
      font-weight: 600;
      background: rgba(255, 255, 255, .04);
    }
    .week-word--high  { color: var(--dari-red, #ef4444); }
    .week-word--watch { color: var(--dari-yellow, #fbbf24); }
    .week-word--calm  { color: var(--dari-teal, #2dd4bf); }
    .week-word--normal { color: inherit; }

    :host { display: block; }
    /* Le titre et la navigation de semaine passaient derrière l'heure et l'encoche en PWA :
       le retrait haut suit la safe-area exposée par la coquille athlète. */
    .cal-top { padding: var(--sp-4) var(--sp-4) 0; padding-top: max(var(--sp-4), var(--safe-top, 0px)); max-width: 720px; margin-inline: auto; }
    .cal-top h1 { margin: 0; }
    .cal-title { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .week-nav { display: flex; align-items: center; gap: var(--sp-1); margin: var(--sp-2) 0; }
    /* La période prend la place libre entre les flèches : c'est le titre réel de la vue. */
    .period { flex: 1; text-align: center; font-weight: 700; color: var(--ink); text-transform: capitalize; font-size: var(--text-md); }
    .today-btn { flex-shrink: 0; }
    .cal-controls { display: flex; flex-wrap: wrap; gap: var(--sp-2); }
    .subtitle { color: var(--ink-3); margin: 0; text-transform: capitalize; }

    /* Marge latérale réduite : sur un téléphone, 16 px de chaque côté sont 32 px pris aux sept
       colonnes — c'est-à-dire 4 px par jour, la différence entre « 12,4 km » et « 12,4 kı ». Le
       reste de l'écran garde sa marge, la grille est le seul élément qui en manque. */
    .month { max-width: 720px; margin-inline: auto; padding: var(--sp-3) var(--sp-2) var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-2); }
    @media (min-width: 600px) { .month { padding-inline: var(--sp-4); } }
    .month-hint { text-align: center; margin: 0; }

    .dsheet { display: flex; flex-direction: column; gap: var(--sp-2); }
    .dsheet-order { display: flex; align-items: center; gap: var(--sp-2); margin: 0; }
    /* Le rang se lit avant le titre : c'est ce qu'on cherche sur une journée à deux séances. */
    .ses-rank {
      display: inline-flex; align-items: center; justify-content: center;
      min-width: 18px; height: 18px; padding: 0 4px; margin-right: 4px;
      border-radius: var(--radius-full);
      background: var(--type-c, var(--ink-3)); color: var(--paper);
      font-size: var(--text-2xs); font-weight: 800; font-variant-numeric: tabular-nums;
    }

    /* Le mot posé sur une journée : lisible d'un coup d'œil, jamais confondu avec une séance. */
    .dnote {
      display: flex; align-items: center; gap: var(--sp-2);
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius-md);
      background: var(--paper-sunk); border-left: 3px solid var(--ink-4);
    }
    .dnote--mine { border-left-color: var(--primary); }
    .dnote-txt { flex: 1; min-width: 0; overflow-wrap: anywhere; }
    .dnote-who { white-space: nowrap; }
    .dnote-form { display: flex; flex-direction: column; gap: var(--sp-2); }
    .dnote-actions { display: flex; justify-content: flex-end; gap: var(--sp-2); }
    .dnote-actions .btn, .dnote-open { min-height: 44px; }
    .dnote-open { align-self: flex-start; }
    /* Pastille de la case : elle avertit, elle n'empêche rien — comme l'indisponibilité. */
    .day-note {
      display: inline-flex; align-items: center; gap: 3px;
      font-size: var(--text-2xs); font-weight: 700; color: var(--ink-3);
    }
    .dsheet-off { display: flex; align-items: center; gap: var(--sp-2); margin: 0; color: var(--ink-3); font-size: var(--text-sm); }
    .dsheet-empty { margin: 0; text-align: center; }
    .dsheet-add {
      display: flex; gap: var(--sp-2); flex-wrap: wrap; justify-content: center;
      padding-top: var(--sp-2); border-top: 1px solid var(--hairline);
    }

    .agenda { max-width: 560px; margin-inline: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-2); }
    .day { background: var(--paper); border: 1px solid var(--hairline); border-radius: var(--radius-lg); padding: var(--sp-3); }
    .day--today { border-color: var(--primary); box-shadow: 0 0 0 1px var(--primary); }
    .day--unavailable { background: repeating-linear-gradient(135deg, var(--paper) 0 10px, var(--paper-sunk) 10px 20px); }
    /* Jour de repos : une ligne, pas une carte. Il reste visible — le repos fait partie du plan
       — mais il cesse de peser autant qu'une séance dans la hauteur de la semaine. */
    .day--rest { background: transparent; border-style: dashed; padding: var(--sp-2) var(--sp-3); }
    .day--rest .day-hd { margin-bottom: 0; }
    .day--rest .day-wd, .day--rest .day-n { color: var(--ink-4); }
    .day-hd { display: flex; align-items: baseline; gap: var(--sp-2); margin-bottom: var(--sp-2); }
    .day-wd { font-weight: 700; text-transform: capitalize; color: var(--ink-2); }
    .day-n { color: var(--ink-3); font-weight: 700; }
    /* Aujourd'hui se nomme : sur une liste qu'on fait défiler, le liseré seul sort du champ. */
    .day-now {
      padding: 1px 8px; border-radius: var(--radius-full);
      background: var(--primary); color: var(--paper);
      font-size: var(--text-2xs); font-weight: 800; letter-spacing: 0.02em;
    }
    .day-unavail { font-size: var(--text-xs); font-weight: 700; color: var(--ink-3); }
    .day-sum { margin-left: auto; font-weight: 800; color: var(--ink-2); font-variant-numeric: tabular-nums; }
    .day-sum--rest {
      font-weight: 700; color: var(--ink-4); text-transform: uppercase;
      font-size: var(--text-2xs); letter-spacing: 0.04em;
    }
    .day-items { display: flex; flex-direction: column; gap: var(--sp-2); }
    /* Le jour de repos n'a plus de corps : sa mention tient dans l'en-tête. */
    .day--rest .day-items { display: none; }

    .ses {
      display: flex; flex-direction: column; gap: var(--sp-1); text-align: left;
      width: 100%; padding: var(--sp-3); border-radius: var(--radius);
      border: 1px solid var(--hairline); border-left: 4px solid var(--type-c, var(--primary));
      background: var(--paper); cursor: pointer;
    }
    .ses:active { transform: scale(0.99); }
    /* Le rail portait une seule couleur pour toutes les séances de course : ouvrir sa semaine ne
       disait donc rien de sa forme — cinq barres identiques, qu'il fallait lire une à une pour
       savoir laquelle était dure. Il porte maintenant la couleur du type, qui est déjà l'échelle
       d'intensité du produit (récupération → zone 1, endurance → 2, tempo → 3, seuil → 4,
       fractionné → 5). Aucune deuxième échelle n'est inventée : c'est la même que celle des
       pastilles de zone et de la grille du mois.

       La couleur ne porte jamais seule : le type reste écrit en toutes lettres sur la carte. */
    .ses--run { --type-c: var(--dari-teal); }
    /* Un voile de la couleur du type, à peine perceptible sur une carte isolée mais suffisant
       pour que la semaine se lise en descendant la colonne. 6 % : au-delà, le texte perd du
       contraste sur les zones chaudes. */
    .ses--run { background: color-mix(in srgb, var(--type-c) 6%, var(--paper)); }
    .ses--strength { --type-c: var(--dari-violet); }

    /* --- La course. Une forme à elle, que rien d'autre ne porte. ---
       Elle n'est pas une séance de plus : elle est ce vers quoi les séances travaillent. Si elle
       ressemblait à une carte de séance avec une autre couleur, elle se lirait comme une séance
       — d'où l'aplat plein, le drapeau et la lettre de priorité, tous trois absents ailleurs. */
    .race {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2);
      width: 100%; padding: var(--sp-2) var(--sp-3); border-radius: var(--radius);
      background: var(--energy); color: #fff; text-decoration: none;
      font-weight: 700; font-size: var(--text-sm);
    }
    .race-l { display: flex; align-items: center; gap: var(--sp-2); min-width: 0; }
    .race-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    /* La priorité écrite, pas seulement colorée : une course A ne se prépare pas comme une C, et
       la distinction doit survivre à un écran en plein soleil comme à une vision des couleurs
       différente. */
    .race-prio {
      flex: none; padding: 0 6px; border-radius: var(--radius-full);
      background: rgba(255, 255, 255, 0.25); font-size: var(--text-xs); font-weight: 800;
    }
    .race-r { flex: none; opacity: 0.9; font-size: var(--text-xs); }
    /* Une course passée reste au calendrier — elle explique la semaine qui l'entoure — mais elle
       cesse d'être une échéance : elle rend son aplat. */
    .race--past {
      background: transparent; color: var(--energy);
      box-shadow: inset 0 0 0 1px var(--energy);
    }
    .race--past .race-prio { background: color-mix(in srgb, var(--energy) 15%, transparent); }
    /* Le réalisé se distingue du prescrit à la couleur du rail, sans avoir à lire l'étiquette. */
    .ses--done { --type-c: var(--success, var(--dari-teal)); text-decoration: none; color: inherit; }
    .ses-facts { display: flex; flex-wrap: wrap; gap: var(--sp-2); color: var(--ink-3); font-size: var(--text-sm); }
    .ses-extra {
      padding: 2px 8px; border-radius: var(--radius-full);
      background: var(--paper-sunk); color: var(--ink-3);
      font-size: var(--text-xs); font-weight: 700; white-space: nowrap;
    }

    .view-nav { margin-top: var(--sp-2); }
    .ses-body { display: flex; flex-direction: column; gap: var(--sp-1); text-align: left; width: 100%; background: transparent; border: none; padding: 0; cursor: pointer; }
    .ses-main { display: flex; align-items: center; gap: var(--sp-2); flex-wrap: wrap; }
    .ses-title { font-weight: 700; color: var(--ink); flex: 1; }
    .ses-km { color: var(--ink-2); font-weight: 700; font-size: var(--text-sm); }
    .ses-zones { display: flex; flex-wrap: wrap; gap: var(--sp-1); }
    .ses-move { font-size: var(--text-xs); color: var(--ink-3); font-weight: 600; }
    .ses-todo {
      padding: 2px 8px; border-radius: var(--radius-full);
      background: var(--warning-bg); color: var(--warning-text);
      font-size: var(--text-xs); font-weight: 700;
    }
    .det-cta { width: 100%; }
    .ses-move-btn {
      align-self: flex-start; margin-top: var(--sp-1); display: inline-flex; align-items: center; gap: 4px;
      font-size: var(--text-xs); color: var(--ink-3); font-weight: 700; background: transparent;
      border: none; padding: var(--sp-1) 0; cursor: pointer;
    }
    .ses-move-btn:active { transform: scale(0.97); }

    .det { display: flex; flex-direction: column; gap: var(--sp-3); }
    .det-hd strong { color: var(--ink); }
    .det-meta { display: flex; gap: 4px; margin-top: 2px; }
    .det-notes { margin: 0; color: var(--ink-2); }
    .blocks { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .blk { padding: var(--sp-2) 0; border-top: 1px solid var(--hairline); }
    .blk:first-child { border-top: none; }
    .blk-hd { display: flex; align-items: center; gap: var(--sp-2); }
    .blk-type { font-weight: 700; color: var(--ink); }
    .blk-rep { color: var(--ink-2); font-weight: 800; }
    .blk-target { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 2px; }
    .det-foot { margin: 0; }

    .grp { display: flex; flex-direction: column; }
    .grp-h { margin: var(--sp-2) 0 0; font-size: var(--text-sm); color: var(--ink-3); text-transform: uppercase; letter-spacing: .03em; }
    .targets { display: flex; flex-wrap: wrap; gap: var(--sp-2); margin-top: 4px; }
    .tgt { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: var(--radius-full);
      background: var(--paper-sunk); color: var(--ink); font-weight: 700; font-size: var(--text-sm); font-variant-numeric: tabular-nums; }
    .targets.recov { margin-top: 2px; }
    .blk-note { margin: 2px 0 0; }

    .move-lead { font-weight: 700; color: var(--ink); margin: 0 0 var(--sp-4); }
    .pick-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--sp-2); }
    .pick {
      min-height: 48px; border: 1px solid var(--hairline); background: var(--paper);
      border-radius: var(--radius); font-weight: 700; color: var(--ink-2); cursor: pointer;
      text-transform: capitalize;
    }
    .pick:active { transform: scale(0.97); }
    .pick--current { background: var(--paper-sunk); color: var(--ink-4); cursor: default; }
  `],
})
export class AthleteCalendarComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly paceReference = inject(PaceReferenceService);
  private readonly toast = inject(ToastService);
  private readonly noteService = inject(CalendarNoteService);

  /** Mon allure d'endurance, seule base admise pour estimer un volume écrit en durée. */
  readonly referencePace = signal<number | null>(null);

  readonly typeLabels = WORKOUT_TYPE_LABELS;
  readonly stepLabels = STEP_TYPE_LABELS;
  readonly reasonIcon = REASON_ICON;
  private readonly weekdays = ['Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam', 'Dim'];

  readonly anchor = signal<Date>(new Date());
  readonly workouts = signal<Workout[]>([]);
  readonly strength = signal<ScheduledStrength[]>([]);
  readonly activities = signal<Activity[]>([]);
  readonly unavailabilities = signal<Unavailability[]>([]);
  /** Mots posés sur les journées : ceux que le coach m'adresse, et les miens. */
  readonly notes = signal<CalendarNote[]>([]);
  readonly noteOpen = signal(false);
  readonly savingNote = signal(false);
  noteDraft = '';
  /**
   * Les courses visées. Elles n'apparaissaient nulle part dans le calendrier : l'athlète voyait
   * ses séances sans jamais voir vers quoi elles allaient, alors que c'est la date qui donne son
   * sens à tout le reste — trois semaines de côtes ne se lisent pas pareil à six semaines d'un
   * marathon. Chargées une fois, non bornées : elles sont peu nombreuses et on feuillette
   * volontiers les mois à venir jusqu'à la prochaine.
   */
  readonly races = signal<RaceObjective[]>([]);
  /** Cycles du coach couvrant la plage affichée (périodes seulement, jamais ses notes du jour). */
  readonly cycles = signal<CalendarNote[]>([]);

  /**
   * Ce que la semaine affichée demande, comparé à l'habitude de l'athlète.
   *
   * <p>Le même calcul que côté coach, dit autrement : l'athlète n'a pas à savoir ce qu'est un
   * ACWR pour comprendre qu'une semaine est chargée. On lui donne le mot, pas le ratio.</p>
   */
  readonly weekOutlook = signal<WeekOutlook | null>(null);

  /** Le mot de la semaine, ou rien à dire. */
  readonly weekWord = computed(() => {
    const o = this.weekOutlook();
    if (!o || o.plannedLoadUa <= 0) return null;
    switch (o.level) {
      case 'HIGH': return 'Semaine chargée — soigne ton sommeil et tes récupérations.';
      case 'WATCH': return 'Semaine exigeante.';
      case 'CALM': return o.deltaPct !== null && o.deltaPct <= -25
        ? 'Semaine de décharge — c’est voulu, profites-en.' : null;
      default: return null;
    }
  });
  /** Plage effectivement chargée : le bandeau des cycles n'en montre pas d'autres. */
  readonly rangeFrom = signal<string | null>(null);
  readonly rangeTo = signal<string | null>(null);
  readonly todayIso = toIso(new Date());

  readonly modeOptions = [
    { label: 'Mois', value: 'month' },
    { label: 'Semaine', value: 'week' },
  ];
  /**
   * Le mois d'abord : c'est ce qu'on vient voir en ouvrant l'application — la forme du mois, la
   * charge des jours à venir. La semaine reste d'un geste, pour lire le détail des séances et
   * les déplacer.
   */
  readonly mode = signal<'month' | 'week'>('month');

  readonly viewOptions = [
    { label: 'Prévu', value: 'planned' },
    { label: 'Réalisé', value: 'done' },
    { label: 'Les deux', value: 'both' },
  ];
  /**
   * « Les deux » par défaut : c'est la lecture qui répond à la question qu'on se pose en ouvrant
   * son agenda — ce que j'avais à faire, et ce que j'ai fait. Les deux autres vues servent à
   * isoler l'un ou l'autre.
   */
  readonly view = signal<AgendaView>('both');

  /** Feuille de ressenti partagée (même parcours que « Aujourd'hui »). */
  private readonly feedbackSheet = viewChild(WorkoutFeedbackSheetComponent);

  readonly moveOpen = signal(false);
  readonly moveTarget = signal<MoveTarget | null>(null);
  readonly detailOpen = signal(false);
  readonly detailWorkout = signal<Workout | null>(null);
  readonly detailPrescription = signal<WorkoutPrescription | null>(null);
  readonly detailLoading = signal(false);

  /** Blocs calculés groupés par phase (échauffement / principal / retour au calme). */
  readonly detailGroups = computed<{ section: string; entries: CalculatedBlockEntry[] }[]>(() => {
    const c = this.detailPrescription()?.calculated;
    if (!c) return [];
    return [
      { section: 'Échauffement', entries: c.warmup ?? [] },
      { section: 'Bloc principal', entries: c.main ?? [] },
      { section: 'Retour au calme', entries: c.cooldown ?? [] },
    ].filter((g) => g.entries.length > 0);
  });

  readonly days = computed<DayRow[]>(() => {
    const today = toIso(new Date());
    const start = mondayOf(this.anchor());
    const view = this.view();
    const showPlanned = view !== 'done';
    const showDone = view !== 'planned';
    const ws = this.workouts();
    const ss = this.strength();
    const acts = this.activities();
    const un = this.unavailabilities();
    const rcs = this.races();
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      const workouts = showPlanned ? ws.filter((w) => w.scheduledDate === iso) : [];
      const strength = showPlanned ? ss.filter((s) => s.scheduledDate === iso) : [];
      const activities = showDone ? acts.filter((a) => a.activityDate === iso) : [];
      // Les courses ne suivent pas le filtre prévu/réalisé : une échéance reste une échéance,
      // qu'on regarde son programme ou ce qu'on a couru.
      const races = rcs.filter((r) => r.raceDate === iso);
      return {
        date: iso,
        weekday: this.weekdays[i],
        dayNum: d.getDate(),
        isToday: iso === today,
        workouts,
        strength,
        activities,
        races,
        unavailability: un.find((u) => iso >= u.startDate && iso <= u.endDate) ?? null,
        notes: this.notesOn(iso),
        empty: workouts.length === 0 && strength.length === 0 && activities.length === 0
            && races.length === 0,
      };
    });
  });

  readonly periodLabel = computed(() => {
    if (this.mode() === 'month') {
      return new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(this.anchor());
    }
    const start = mondayOf(this.anchor());
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    const fmt = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'long' });
    return `${fmt.format(start)} – ${fmt.format(end)}`;
  });

  /**
   * Grille du mois : les semaines complètes qui couvrent le mois affiché, débordements compris
   * (un lundi de fin de mois appartient à une semaine d'entraînement qui continue).
   */
  readonly monthWeeks = computed<MonthWeek[]>(() => {
    const today = toIso(new Date());
    const ref = this.anchor();
    const monthRef = ref.getMonth();
    const first = mondayOf(new Date(ref.getFullYear(), monthRef, 1));
    const lastDay = new Date(ref.getFullYear(), monthRef + 1, 0);
    const view = this.view();
    const showPlanned = view !== 'done';
    const showDone = view !== 'planned';

    const weeks: MonthWeek[] = [];
    const cursor = new Date(first);
    // Tant qu'on n'a pas dépassé le dernier jour du mois : 4 à 6 semaines selon le calendrier.
    while (cursor <= lastDay || weeks.length === 0) {
      const days: MonthDay[] = [];
      for (let i = 0; i < 7; i++) {
        const iso = toIso(cursor);
        days.push({
          date: iso,
          dayNum: cursor.getDate(),
          isToday: iso === today,
          inMonth: cursor.getMonth() === monthRef,
          unavailable: this.unavailabilities().some((u) => iso >= u.startDate && iso <= u.endDate),
          races: this.racesFor(iso),
          chips: this.chipsFor(iso, showPlanned, showDone),
        });
        cursor.setDate(cursor.getDate() + 1);
      }
      weeks.push({
        weekNumber: isoWeek(new Date(days[0].date + 'T00:00:00')),
        days,
        ...this.weekTotal(days, showPlanned, showDone),
      });
    }
    return weeks;
  });

  /**
   * Total de la semaine, dans la gouttière : « quelle semaine ai-je chargée » sans ouvrir un
   * seul jour.
   *
   * <p>Une seule valeur y tient, il faut donc choisir. Dès qu'une sortie a été enregistrée dans
   * la semaine, c'est le <b>réalisé</b> qui compte — c'est la question qu'on se pose sur une
   * semaine entamée. Sinon, le prévu. Les deux ne portent pas la même couleur, pour qu'on ne
   * confonde jamais l'un avec l'autre en parcourant le mois.</p>
   */
  private weekTotal(days: MonthDay[], showPlanned: boolean, showDone: boolean):
      Pick<MonthWeek, 'totalKm' | 'totalDone' | 'totalTitle'> {
    const dates = new Set(days.map((d) => d.date));
    const doneM = showDone
      ? this.activities().filter((a) => dates.has(a.activityDate))
          .reduce((s, a) => s + (a.distanceM ?? 0), 0)
      : 0;
    if (doneM > 0) {
      return { totalKm: this.km(doneM), totalDone: true, totalTitle: `${this.km(doneM)} km réalisés` };
    }
    const plannedM = showPlanned
      ? this.workouts().filter((w) => dates.has(w.scheduledDate))
          .reduce((s, w) => s + (plannedVolume(w.targetDistanceM, w.targetDurationS, this.referencePace())?.distanceM ?? 0), 0)
      : 0;
    if (plannedM > 0) {
      return { totalKm: this.km(plannedM), totalDone: false, totalTitle: `${this.km(plannedM)} km prévus` };
    }
    return {};
  }

  /** Mètres → kilomètres entiers : la gouttière fait 26 px, une décimale n'y tient pas. */
  private km(meters: number): string {
    return String(Math.round(meters / 1000));
  }

  /**
   * Courses d'un jour, prêtes pour la grille du mois.
   *
   * <p>Hors du filtre prévu/réalisé : une échéance reste une échéance qu'on regarde son programme
   * ou ce qu'on a couru. C'est même en vue « réalisé » qu'elle explique le mieux la semaine
   * qu'on relit.</p>
   */
  private racesFor(iso: string): MonthRace[] {
    return this.races().filter((r) => r.raceDate === iso).map((r) => ({
      name: r.name,
      priority: r.priority,
      past: r.status !== 'UPCOMING',
      title: `${r.name} — objectif ${r.priority}`,
    }));
  }

  /** Pastilles d'un jour : séances prescrites, renforcement, puis sorties réellement faites. */
  private chipsFor(iso: string, showPlanned: boolean, showDone: boolean): MonthChip[] {
    const chips: MonthChip[] = [];
    if (showPlanned) {
      for (const w of this.workouts().filter((x) => x.scheduledDate === iso)) {
        const meta = WORKOUT_TYPE_META[w.type];
        chips.push({
          color: meta.color,
          icon: meta.icon,
          // La durée au-dessus, la distance en dessous — les deux chiffres qu'un athlète cherche
          // dans une case de calendrier. Jamais le type tronqué : l'icône le dit en entier.
          sub: this.durationLabel(w.targetDurationS),
          value: this.volumeLabel(w),
          strong: meta.key,
          done: false,
          title: `${WORKOUT_TYPE_LABELS[w.type]} · ${w.title}`,
        });
      }
      for (const s of this.strength().filter((x) => x.scheduledDate === iso)) {
        chips.push({
          color: WORKOUT_TYPE_META.STRENGTH.color, icon: 'dumbbell',
          sub: '', value: '', strong: false, done: false, title: s.title,
        });
      }
    }
    if (showDone) {
      for (const a of this.activities().filter((x) => x.activityDate === iso)) {
        chips.push({
          color: 'var(--success, var(--dari-teal))', icon: 'check',
          sub: this.durationLabel(a.durationS),
          value: a.distanceM ? `${Math.round(a.distanceM / 100) / 10} km` : '',
          strong: false,
          done: true,
          title: a.title || 'Sortie réalisée',
        });
      }
    }
    return chips;
  }

  /** Jours proposés pour le déplacement : 14 jours à partir du lundi courant. */
  /**
   * Jours proposés au déplacement : la quinzaine affichée, jours passés exclus.
   *
   * Déplacer une séance vers hier n'a pas de sens — elle n'a pas été faite ce jour-là non plus —
   * et brouillait la lecture du coach comme le calcul de charge. Rien ne l'empêchait.
   */
  readonly pickDays = computed<PickDay[]>(() => {
    const t = this.moveTarget();
    const start = mondayOf(this.anchor());
    const today = toIso(new Date());
    const fmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric', month: 'short' });
    return Array.from({ length: 14 }, (_, i) => {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const iso = toIso(d);
      return {
        date: iso, label: fmt.format(d),
        isCurrent: !!t && iso === t.date,
        isPast: iso < today,
      };
    });
  });

  ngOnInit(): void {
    this.load();
    // Une seule fois : l'API des sorties n'est pas bornée par semaine, le filtrage par jour se
    // fait à l'affichage. La recharger à chaque flèche serait un appel de plus pour rien.
    this.portal.activities().subscribe({
      next: (a) => this.activities.set(a),
      error: () => this.activities.set([]),
    });
    this.paceReference.mine().subscribe({
      next: (p) => this.referencePace.set(p),
      error: () => this.referencePace.set(null),
    });
    this.portal.races().subscribe({
      next: (r) => this.races.set(r),
      error: () => this.races.set([]),
    });
  }

  load(): void {
    // La vue mensuelle affiche des semaines entières, débordements des mois voisins compris :
    // la plage chargée part du lundi de la première semaine et va jusqu'au dimanche de la
    // dernière. En vue hebdomadaire, la quinzaine suffit (elle couvre le choix de déplacement).
    const ref = this.anchor();
    const start = this.mode() === 'month'
      ? mondayOf(new Date(ref.getFullYear(), ref.getMonth(), 1))
      : mondayOf(ref);
    const end = new Date(start);
    end.setDate(start.getDate() + (this.mode() === 'month' ? 41 : 13));
    const from = toIso(start);
    const to = toIso(end);
    this.rangeFrom.set(from);
    this.rangeTo.set(to);
    this.portal.cycles(from, to).subscribe({ next: (c) => this.cycles.set(c), error: () => this.cycles.set([]) });
    // Le mot de la semaine n'a de sens qu'en vue semaine : sur un mois, il ne désignerait
    // aucune semaine en particulier.
    if (this.mode() === 'week') {
      this.portal.weekOutlook(from).subscribe({
        next: (o) => this.weekOutlook.set(o),
        error: () => this.weekOutlook.set(null),
      });
    } else {
      this.weekOutlook.set(null);
    }
    this.portal.workouts(from, to).subscribe({ next: (w) => this.workouts.set(w), error: () => this.workouts.set([]) });
    this.portal.ppScheduled(from, to).subscribe({ next: (s) => this.strength.set(s), error: () => this.strength.set([]) });
    this.portal.unavailabilities().subscribe({ next: (u) => this.unavailabilities.set(u), error: () => this.unavailabilities.set([]) });
    this.loadNotes();
  }

  setView(view: string): void { this.view.set(view as AgendaView); }

  /**
   * Couleur d'une séance : celle de son type, qui est déjà l'échelle d'intensité du produit.
   *
   * Inventer une seconde échelle « intensité » aurait donné deux vocabulaires de couleur dans le
   * même écran — celui des pastilles de zone et le nouveau — pour dire la même chose.
   */
  typeColor(w: Workout): string {
    return WORKOUT_TYPE_META[w.type].color;
  }

  /**
   * Le second membre du bandeau de course : la distance, et le compte à rebours tant qu'il a un
   * sens. « dans 3 jours » est l'information qu'un coureur cherche en ouvrant son calendrier ;
   * passé le jour J, elle devient du bruit et disparaît.
   */
  raceSub(r: RaceObjective): string {
    const bits: string[] = [];
    if (r.distanceM) {
      const km = r.distanceM / 1000;
      bits.push(`${km === Math.round(km) ? km : km.toFixed(1)} km`);
    }
    if (r.status === 'UPCOMING' && r.daysUntil > 0) {
      bits.push(r.daysUntil === 1 ? 'demain' : `dans ${r.daysUntil} j`);
    } else if (r.status === 'UPCOMING' && r.daysUntil === 0) {
      bits.push("aujourd'hui");
    }
    return bits.join(' · ');
  }

  /** Allure réelle d'une sortie, celle déjà calculée côté serveur si elle existe. */
  actPace(a: Activity): string | null {
    return formatPace(a.paceSPerKm) ?? paceFrom(a.distanceM, a.durationS);
  }

  /** Un pas = un mois en vue mensuelle, une semaine en vue hebdomadaire. */
  shift(step: number): void {
    const d = new Date(this.anchor());
    if (this.mode() === 'month') {
      // Le 1er du mois avant d'ajouter : partir du 31 donnerait le 3 du mois suivant.
      d.setDate(1);
      d.setMonth(d.getMonth() + step);
    } else {
      d.setDate(d.getDate() + step * 7);
    }
    this.anchor.set(d);
    this.load();
  }

  goToday(): void { this.anchor.set(new Date()); this.load(); }

  setMode(mode: string): void {
    this.mode.set(mode as 'month' | 'week');
    this.load();
  }

  // --- Feuille d'un jour (ouverte depuis la grille du mois) ----------------

  readonly dayOpen = signal(false);
  readonly daySelected = signal<DayRow | null>(null);

  dayLabel(): string {
    const d = this.daySelected();
    if (!d) return 'Ce jour';
    return new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
      .format(new Date(d.date + 'T00:00:00'));
  }

  openDay(iso: string): void {
    this.daySelected.set(this.rowFor(iso));
    // Le brouillon de mot appartient à la journée qu'on quitte, pas à celle qu'on ouvre.
    this.noteOpen.set(false);
    this.noteDraft = '';
    this.dayOpen.set(true);
  }

  /** Contenu d'un jour quelconque, y compris hors de la semaine affichée par la vue hebdo. */
  private rowFor(iso: string): DayRow {
    const view = this.view();
    const workouts = view !== 'done' ? this.workouts().filter((w) => w.scheduledDate === iso) : [];
    const strength = view !== 'done' ? this.strength().filter((s) => s.scheduledDate === iso) : [];
    const activities = view !== 'planned' ? this.activities().filter((a) => a.activityDate === iso) : [];
    const d = new Date(iso + 'T00:00:00');
    return {
      date: iso,
      weekday: this.weekdays[(d.getDay() + 6) % 7],
      dayNum: d.getDate(),
      isToday: iso === toIso(new Date()),
      workouts,
      strength,
      activities,
      // Hors filtre prévu/réalisé, comme dans la vue hebdomadaire.
      races: this.races().filter((r) => r.raceDate === iso),
      unavailability: this.unavailabilities().find((u) => iso >= u.startDate && iso <= u.endDate) ?? null,
      notes: this.notesOn(iso),
      empty: workouts.length === 0 && strength.length === 0 && activities.length === 0
          && this.races().every((r) => r.raceDate !== iso),
    };
  }

  /**
   * Cette journée porte-t-elle un ordre voulu par le coach ?
   *
   * <p>Même convention que le serveur : une journée est ordonnée dès qu'une de ses séances porte
   * un {@code orderIndex} non nul. Toutes à zéro = aucune décision, donc aucun numéro affiché.</p>
   */
  dayIsOrdered(iso: string): boolean {
    return this.workouts().some((w) => w.scheduledDate === iso && (w.orderIndex ?? 0) > 0);
  }

  /** Rang de la séance dans sa journée (1, 2, 3…), ou `null` si la journée n'est pas ordonnée. */
  orderRank(w: Workout): number | null {
    return this.dayIsOrdered(w.scheduledDate) ? (w.orderIndex ?? 0) + 1 : null;
  }

  /**
   * Mots couvrant ce jour. Un cycle du coach couvre une période : il vaut pour chacun de ses
   * jours, comme le bandeau qu'il affiche côté coach.
   */
  private notesOn(iso: string): CalendarNote[] {
    return this.notes().filter((n) => iso >= n.noteDate && iso <= (n.endDate ?? n.noteDate));
  }

  /** Fenêtre large : le calendrier navigue de mois en mois sans redemander à chaque pas. */
  private loadNotes(): void {
    const from = toIso(new Date(Date.now() - 120 * 864e5));
    const to = toIso(new Date(Date.now() + 200 * 864e5));
    this.noteService.mine(from, to).subscribe({
      next: (n) => this.notes.set(n),
      error: () => this.notes.set([]),
    });
  }

  /** L'athlète laisse un mot sur une journée. Il part visible : c'est tout son objet. */
  addNote(date: string): void {
    const text = this.noteDraft.trim();
    if (!text || this.savingNote()) return;
    this.savingNote.set(true);
    this.noteService.addMine({ noteDate: date, text }).subscribe({
      next: (created) => {
        this.notes.update((list) => [...list, created]);
        this.refreshOpenDay();
        this.noteDraft = '';
        this.noteOpen.set(false);
        this.savingNote.set(false);
        this.toast.success('Ton coach le verra sur cette journée.');
      },
      error: () => {
        this.savingNote.set(false);
        this.toast.error('Mot non enregistré.');
      },
    });
  }

  /**
   * La feuille du jour tient une copie figée de la journée, prise à l'ouverture — elle ne suit
   * donc pas les signaux. Après avoir posé ou retiré un mot, on la reprend, sinon l'athlète
   * referme et rouvre pour voir ce qu'il vient d'écrire.
   */
  private refreshOpenDay(): void {
    const open = this.daySelected();
    if (open) this.daySelected.set(this.rowFor(open.date));
  }

  /** Retirer SON mot : celui du coach n'appartient pas à l'athlète. */
  removeNote(note: CalendarNote): void {
    this.noteService.deleteMine(note.id).subscribe({
      next: () => {
        this.notes.update((list) => list.filter((n) => n.id !== note.id));
        this.refreshOpenDay();
        this.toast.info('Mot retiré.');
      },
      error: () => this.toast.error('Retrait impossible.'),
    });
  }

  openMove(kind: 'run' | 'strength', id: string, title: string, date: string): void {
    // La feuille du jour se referme d'abord : deux feuilles empilées sont illisibles au pouce.
    this.dayOpen.set(false);
    this.moveTarget.set({ kind, id, title, date });
    this.moveOpen.set(true);
  }

  openDetail(w: Workout): void {
    this.dayOpen.set(false);
    this.detailWorkout.set(w);
    this.detailPrescription.set(null);
    this.detailOpen.set(true);
    this.detailLoading.set(true);
    this.portal.workoutPrescription(w.id).subscribe({
      next: (p) => { this.detailPrescription.set(p); this.detailLoading.set(false); },
      error: () => this.detailLoading.set(false),
    });
  }

  /**
   * Séance passée (ou du jour) encore ouverte au ressenti. On ne propose jamais de noter une
   * séance à venir : le ressenti se déclare après l'effort, pas avant.
   */
  canRate(w: Workout): boolean {
    return needsFeedback(w) && w.scheduledDate <= toIso(new Date());
  }

  /** Séance strictement passée qui n'a encore rien reçu : c'est elle qu'on signale « à noter ». */
  isLate(w: Workout): boolean {
    return awaitsFeedback(w) && w.scheduledDate < toIso(new Date());
  }

  /** Le détail se referme d'abord : deux bottom sheets superposés seraient illisibles. */
  rate(w: Workout): void {
    this.detailOpen.set(false);
    this.feedbackSheet()?.openFor(w);
  }

  onFeedbackSaved(updated: Workout): void {
    this.workouts.set(this.workouts().map((w) => (w.id === updated.id ? updated : w)));
  }

  /**
   * Libellé d'un bloc : « 6 × 1000 m », « 20 min », « 2 × (6 × 400 m) » quand il est en séries.
   *
   * <p>L'écriture passait par {@link fmtDur}, qui arrondit à la minute : une répétition de
   * 30 secondes s'y lisait « 1 min » et une récupération de 1'30 « 2 min ». La même séance
   * annonçait donc des trentes secondes ici et des minutes sur la fiche de séance — un athlète
   * qui suivait cet écran courait le double sans que rien ne le lui signale.</p>
   */
  /** Volume d'un pas hérité (WorkoutStep) — même écriture que les blocs structurés. */
  stepVolume(distanceM: number | null, durationS: number | null): string {
    return formatBlockVolume(distanceM, durationS);
  }

  blockLabel(b: CourseBlock): string {
    return formatBlockSets(b) || courseBlockTypeLabel(b.type);
  }

  /**
   * Volume d'une récupération : sa distance, sinon sa durée. Chaîne vide si le coach n'en a
   * prescrit aucun (récup libre).
   *
   * <p>Les secondes sont conservées, contrairement à {@link fmtDur} qui arrondit à la minute :
   * une récupération se prescrit en 45 s, 1'30 ou 2'30, et « 2 min » pour 1'30 n'est pas la même
   * séance.</p>
   */
  /** Récupération entre séries, vide tant que le bloc n'en compte qu'une. */
  setRecoveryVol(e: CalculatedBlockEntry): string {
    const r = e.block.setRecovery;
    if (!r || (e.block.sets ?? 1) <= 1) return '';
    return formatBlockVolume(r.distanceM, r.durationS);
  }

  recoveryVol(e: CalculatedBlockEntry): string {
    const r = e.block.recovery;
    if (!r) return '';
    return formatBlockVolume(r.distanceM, r.durationS);
  }

  /**
   * Volume d'une séance prévue, « 12,0 km » ou « ≈ 10,0 km ».
   *
   * <p>Une séance écrite en durée sans allure prescrite n'a pas de distance calculable : le total
   * ne comptait alors que ses blocs chiffrables — quelques centaines de mètres d'éducatifs — et
   * l'agenda affichait « 0,1 km » pour une heure de course. Faute de mieux, la durée est convertie
   * à mon allure d'endurance, et le « ≈ » dit que c'est un repère, pas une consigne.</p>
   */
  volumeLabel(w: Workout): string {
    return plannedVolumeLabel(
      plannedVolume(w.targetDistanceM, w.targetDurationS, this.referencePace()));
  }

  /**
   * Total d'un jour de l'agenda : les kilomètres réalisés s'il y en a, sinon ceux prévus.
   *
   * <p>Même règle que la gouttière du mois, et pour la même raison : une seule valeur tient à
   * droite d'un en-tête de jour, et sur un jour entamé c'est le réalisé qu'on regarde. Rien
   * n'est rendu quand rien n'est chiffrable — un total absent vaut mieux qu'un « 0 km » faux.</p>
   */
  dayTotal(day: DayRow): string | null {
    const doneM = day.activities.reduce((s, a) => s + (a.distanceM ?? 0), 0);
    if (doneM > 0) return `${Math.round(doneM / 100) / 10} km`;
    const plannedM = day.workouts.reduce(
      (s, w) => s + (plannedVolume(w.targetDistanceM, w.targetDurationS, this.referencePace())?.distanceM ?? 0), 0);
    return plannedM > 0 ? `${Math.round(plannedM / 100) / 10} km` : null;
  }

  /**
   * Durée d'une case de calendrier, ou rien.
   *
   * <p>Une séance dont la durée enregistrée est un résidu — quelques secondes d'éducatifs, tout
   * ce que le calcul a su convertir — affichait « <b>0 min</b> » : un chiffre faux, à côté d'une
   * séance bien réelle. Le seuil est celui, partagé avec le serveur, en dessous duquel une durée
   * ne décrit aucune séance de course à pied. Sous ce seuil, on n'écrit rien.</p>
   */
  private durationLabel(seconds: number | null | undefined): string {
    const usable = usableDurationS(seconds);
    return usable == null ? '' : this.fmtDur(usable);
  }

  /** Durée « h:mm » ou « m min ». */
  fmtDur(s: number): string {
    const h = Math.floor(s / 3600);
    const m = Math.round((s % 3600) / 60);
    return h > 0 ? `${h}h${m.toString().padStart(2, '0')}` : `${m} min`;
  }

  /** Zone 'Z3' → 3 (pour le badge). */
  zoneNum(zone: string): ZoneNum {
    return Number(zone.replace(/\D/g, '')) as ZoneNum;
  }

  confirmMove(targetDate: string): void {
    const t = this.moveTarget();
    if (!t || targetDate === t.date) return;
    this.moveOpen.set(false);

    if (t.kind === 'run') {
      const prev = this.workouts();
      this.workouts.set(prev.map((w) => (w.id === t.id ? { ...w, scheduledDate: targetDate } : w)));
      this.portal.moveWorkout(t.id, targetDate).subscribe({
        next: () => this.toast.success('Séance déplacée'),
        error: () => { this.workouts.set(prev); this.toast.error('Déplacement impossible.'); },
      });
    } else {
      const prev = this.strength();
      this.strength.set(prev.map((s) => (s.id === t.id ? { ...s, scheduledDate: targetDate } : s)));
      this.portal.ppMove(t.id, targetDate).subscribe({
        next: () => this.toast.success('Séance déplacée'),
        error: () => { this.strength.set(prev); this.toast.error('Déplacement impossible.'); },
      });
    }
  }

  zonesOf(w: Workout): ZoneNum[] {
    const present = new Set<number>();
    for (const s of w.steps) {
      if (s.zone) { const n = Number(s.zone.replace(/\D/g, '')); if (n >= 1 && n <= 5) present.add(n); }
    }
    return [...present].sort() as ZoneNum[];
  }
}
