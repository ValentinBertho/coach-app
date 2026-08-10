import { ChangeDetectionStrategy, Component, inject, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Injury } from '../../../core/models/injury.model';
import { MissedReason, Workout, WorkoutStatus } from '../../../core/models/workout.model';
import { AthletePortalService, WorkoutFeedback } from '../../../core/services/athlete-portal.service';
import { FeedbackQueueService } from '../../../core/services/feedback-queue.service';
import { NetworkStatusService } from '../../../core/services/network-status.service';
import { ToastService } from '../../../core/services/toast.service';
import { FeelSelectorComponent } from '../feel-selector/feel-selector.component';
import { IconComponent } from '../icon/icon.component';
import { InjuryPickerComponent } from '../injury-picker/injury-picker.component';
import { PainFatigueSelectorComponent } from '../physiology';
import { BottomSheetComponent } from '../ui';
import { RpeScaleSelectorComponent } from '../rpe-scale-selector/rpe-scale-selector.component';

/** Faits mesurés d'une activité rapprochée, affichés en contexte au-dessus du ressenti. */
export interface MatchedActivity {
  source: string;
  distanceM: number | null;
  durationS: number | null;
  avgHr: number | null;
}

const SOURCE_LABELS: Record<string, string> = {
  STRAVA: 'Strava',
  GARMIN: 'Garmin',
  COROS: 'Coros',
  FILE: 'importée',
  MANUAL: 'saisie',
};

/**
 * Débrief d'après séance, en bottom sheet : sensation, perception de l'effort, fatigue et
 * douleur, commentaire, blessure.
 *
 * Composant unique partagé par « Aujourd'hui », l'agenda, l'historique et la fiche de séance :
 * une séance non clôturée doit être notable d'où qu'on la regarde, sans que la file hors ligne
 * ni les invariants soient réécrits quatre fois.
 *
 * Trois invariants, tenus ici et nulle part ailleurs :
 * - l'état de forme est porté par la **fatigue et la douleur**, jamais par le RPE ni la sensation ;
 * - la **sensation** (comment ça s'est passé) ne se déduit pas du **RPE** (ce que ça a coûté) —
 *   une séance de seuil peut être très dure et excellente, un footing facile et pénible ;
 * - une **blessure nommée** survit aux trois issues, y compris « pas faite » : c'est là qu'elle
 *   explique l'absence.
 *
 * Une question par carte, façon Nolio, mais dans un seul défilement plutôt qu'un carrousel à
 * cinq écrans : chaque question reste visible et sautable, et le débrief se ferme d'un tap
 * depuis n'importe où — la validation ne dépend d'aucune réponse.
 *
 * @example
 * <app-workout-feedback-sheet [(open)]="fbOpen" [workout]="fbWorkout()" (saved)="onSaved($event)" />
 */
@Component({
  selector: 'app-workout-feedback-sheet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, IconComponent, BottomSheetComponent, PainFatigueSelectorComponent,
    RpeScaleSelectorComponent, FeelSelectorComponent, InjuryPickerComponent,
  ],
  template: `
    <app-bottom-sheet [(open)]="open" [title]="sheetTitle()">
      @if (workout(); as w) {
        <div class="fb">
          <!-- Rappel de la séance notée, toujours affiché : la feuille s'ouvre depuis quatre
               écrans (le jour, l'agenda, l'historique, une notification), et sur trois d'entre
               eux rien ne garantissait qu'on notait bien la séance qu'on croyait. -->
          <div class="fb__lead">
            <span class="fb__lead-date metric">{{ dateLabel() }}</span>
            <span class="fb__lead-title">{{ w.title }}</span>
          </div>

          <!-- Activité rapprochée : les faits sont déjà là, l'athlète n'a qu'à confirmer.
               Ils restent en lecture seule — c'est de la donnée mesurée, pas déclarée. -->
          @if (activity(); as a) {
            <div class="fb__matched">
              <span class="fb__matched-hd">
                <app-icon name="activity" [size]="14" /> Activité {{ sourceLabel(a.source) }} rapprochée
              </span>
              <span class="fb__matched-vals metric">
                @if (a.distanceM) { {{ (a.distanceM / 1000).toFixed(1) }} km }
                @if (a.durationS) { · {{ durationLabel(a.durationS) }} }
                @if (a.avgHr) { · {{ a.avgHr }} bpm }
              </span>
              <span class="field-hint">C'était bien cette séance ? Ajoute ton ressenti et valide.</span>
            </div>
          }

          @switch (step()) {
            @case ('debrief') {
              <!-- Une question par carte, façon Nolio : le débrief se parcourt du pouce, et
                   chaque bloc se répond ou se saute sans jamais bloquer l'envoi. L'ordre suit
                   celui de la lecture du coach — comment ça s'est passé, ce que ça a coûté,
                   ce qu'il y a à dire, ce qui fait mal. -->
              <div class="fb__card">
                <app-feel-selector [(value)]="feel" />
              </div>

              <div class="fb__card">
                <app-rpe-scale-selector [(value)]="rpe" label="Perception de l'effort" />
              </div>

              <div class="fb__card fb__card--split">
                <app-pain-fatigue-selector kind="fatigue" [(value)]="fatigue" />
                <app-pain-fatigue-selector kind="pain" [(value)]="pain" />
              </div>

              <div class="fb__card">
                <span class="fb__card-lb">Commentaire</span>
                <textarea class="form-control" rows="2"
                          placeholder="Tu peux écrire un mot à ton entraîneur ici."
                          [ngModel]="comment()" (ngModelChange)="comment.set($event)"></textarea>
              </div>

              <div class="fb__card">
                <app-injury-picker [(injuries)]="injuries" />
              </div>

              <div class="fb__actions">
                <button type="button" class="btn btn-accent btn-lg" (click)="submitDone()">Séance réalisée <app-icon name="check" [size]="16" /></button>
                <button type="button" class="btn btn-ghost" (click)="step.set('partial')">Écourtée</button>
                <!-- Sans cette porte de sortie, l'athlète empêché n'avait que le silence — qui
                     ressortait quelques jours plus tard en alerte « séance manquée », sans motif. -->
                <button type="button" class="btn btn-ghost" (click)="step.set('missed')">Pas faite</button>
              </div>
            }

            @case ('partial') {
              <p class="fb__q">Tu as fait combien de temps&nbsp;?</p>
              <p class="field-hint">
                C'est cette durée qui compte dans ta charge — pas celle qui était prévue.
                @if (w.targetDurationS) { <br />Prévu&nbsp;: {{ durationLabel(w.targetDurationS) }}. }
              </p>
              <div class="fb__dur">
                <input type="number" class="form-control" inputmode="numeric" min="1" max="720"
                       placeholder="minutes"
                       [ngModel]="actualMinutes()" (ngModelChange)="actualMinutes.set($event)" />
                <span class="fb__dur-unit">min</span>
              </div>
              <div class="fb__actions">
                <button type="button" class="btn btn-accent btn-lg" (click)="submitPartial()">Enregistrer <app-icon name="check" [size]="16" /></button>
                <button type="button" class="btn btn-ghost" (click)="step.set('debrief')">Retour</button>
              </div>
            }

            @case ('missed') {
              <p class="fb__q">Qu'est-ce qui s'est passé&nbsp;?</p>
              <div class="fb__reasons">
                @for (r of missedReasons; track r.value) {
                  <button type="button" class="fb__reason" [class.fb__reason--on]="missedReason() === r.value"
                          (click)="missedReason.set(r.value)">{{ r.label }}</button>
                }
              </div>
              <textarea class="form-control" rows="2" placeholder="Un mot pour ton coach ? (optionnel)"
                        [ngModel]="comment()" (ngModelChange)="comment.set($event)"></textarea>
              <!-- Une séance sautée pour raison physique est le cas où nommer la blessure sert le
                   plus : c'est le seul motif sur lequel le coach a quelque chose à décider. -->
              <div class="fb__card">
                <app-injury-picker [(injuries)]="injuries" />
              </div>
              <div class="fb__actions">
                <button type="button" class="btn btn-accent btn-lg" (click)="submitMissed()">Confirmer <app-icon name="check" [size]="16" /></button>
                <button type="button" class="btn btn-ghost" (click)="step.set('debrief')">Retour</button>
              </div>
            }
          }
        </div>
      }
    </app-bottom-sheet>
  `,
  styles: [`
    .fb { display: flex; flex-direction: column; gap: var(--sp-3); }

    /* Bandeau de rappel : la séance notée, date en tête — la lecture de Nolio, qui met la
       séance en évidence avant de poser la moindre question. */
    .fb__lead {
      display: flex; flex-direction: column; gap: 2px;
      padding: var(--sp-3); border-radius: var(--radius);
      background: var(--primary); color: #fff;
    }
    .fb__lead-date { font-size: var(--text-xs); font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; opacity: 0.85; }
    .fb__lead-title { font-size: var(--text-lg); font-weight: 800; }

    /* Une carte par question : chacune se répond, se saute, et se relit isolément. */
    .fb__card {
      display: flex; flex-direction: column; gap: var(--sp-3);
      padding: var(--sp-3); border-radius: var(--radius);
      background: var(--paper); border: 1px solid var(--hairline);
    }
    .fb__card--split { gap: var(--sp-4); }
    .fb__card-lb { font-size: var(--text-sm); color: var(--ink-2); font-weight: 700; }

    .fb__actions { display: flex; flex-direction: column; gap: var(--sp-2); margin-top: var(--sp-2); }
    .fb__actions .btn { width: 100%; }

    .fb__matched {
      display: flex; flex-direction: column; gap: 2px;
      padding: var(--sp-3); border-radius: var(--radius);
      background: var(--primary-wash); border: 1px solid var(--hairline);
    }
    .fb__matched-hd {
      display: inline-flex; align-items: center; gap: var(--sp-1);
      font-size: var(--text-xs); font-weight: 700; text-transform: uppercase;
      letter-spacing: 0.04em; color: var(--primary);
    }
    .fb__matched-vals { font-size: var(--text-lg); font-weight: 800; color: var(--ink); }

    .fb__q { margin: 0; font-weight: 700; color: var(--ink); font-size: var(--text-lg); }

    .fb__dur { display: flex; align-items: center; gap: var(--sp-2); }
    .fb__dur .form-control { flex: 1; font-size: var(--text-lg); font-weight: 700; }
    .fb__dur-unit { font-weight: 700; color: var(--ink-3); }

    .fb__reasons { display: flex; flex-wrap: wrap; gap: var(--sp-2); }
    .fb__reason {
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius);
      border: 1px solid var(--hairline); background: transparent;
      color: var(--ink-2); font-weight: 600; font-size: var(--text-sm);
      min-height: 44px;
    }
    .fb__reason--on {
      background: var(--primary-wash); border-color: var(--primary);
      color: var(--primary); font-weight: 700;
    }
  `],
})
export class WorkoutFeedbackSheetComponent {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly network = inject(NetworkStatusService);
  private readonly queue = inject(FeedbackQueueService);

  /** Ouverture two-way : l'appelant pilote, la feuille se referme après envoi. */
  readonly open = model(false);
  /** Séance visée. `openFor()` la positionne en même temps que les champs. */
  readonly workout = model<Workout | null>(null);
  /** Séance mise à jour (optimiste hors ligne) : l'appelant rafraîchit sa liste. */
  readonly saved = output<Workout>();

  /** Sensation générale 1–5 : comment la séance a été vécue, jamais sa difficulté. */
  readonly feel = signal<number | null>(null);
  readonly rpe = signal<number | null>(null);
  readonly fatigue = signal<number | null>(null);
  readonly pain = signal<number | null>(null);
  readonly comment = signal('');
  /** Blessures nommées au débrief (liste vide = aucune). */
  readonly injuries = signal<Injury[]>([]);
  /** Activité rapprochée affichée en contexte, ou `null` (ouverture manuelle). */
  readonly activity = signal<MatchedActivity | null>(null);

  /**
   * Étape de la feuille. Le débrief reste l'écran par défaut ; les deux autres ne s'ouvrent que si
   * l'athlète les demande, et posent une seule question chacune.
   */
  readonly step = signal<'debrief' | 'partial' | 'missed'>('debrief');
  /** Durée réellement effectuée, en minutes (l'athlète ne compte pas en secondes). */
  readonly actualMinutes = signal<number | null>(null);
  readonly missedReason = signal<MissedReason>('UNEXPECTED');

  protected readonly missedReasons: { value: MissedReason; label: string }[] = [
    { value: 'UNEXPECTED', label: 'Un imprévu' },
    { value: 'NO_TIME', label: 'Pas eu le temps' },
    { value: 'WEATHER', label: 'Météo' },
    { value: 'HEALTH', label: 'Santé' },
    { value: 'OTHER', label: 'Autre' },
  ];

  protected sheetTitle(): string {
    return this.isLate() ? 'Débrief (en retard)' : 'Débrief';
  }

  /** « Aujourd'hui » pour la séance du jour, la date en toutes lettres sinon. */
  protected dateLabel(): string {
    const w = this.workout();
    if (!w) return '';
    if (!this.isLate()) return 'Aujourd’hui';
    const fmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
    return fmt.format(new Date(w.scheduledDate + 'T00:00:00'));
  }

  private isLate(): boolean {
    const w = this.workout();
    if (!w) return false;
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    return w.scheduledDate < today;
  }

  /**
   * Ouvre la feuille sur une séance, champs pré-remplis avec ce qui a déjà été déclaré.
   *
   * `prefill` sert les deux ouvertures automatiques :
   * - `rpe` vient de l'action rapide d'une notification push (« Facile / Moyen / Dur ») ;
   * - `activity` vient d'un rapprochement Strava et affiche les faits mesurés.
   *
   * Dans les deux cas la fatigue et la douleur restent vides : ce sont elles qui portent la
   * forme, et personne ne peut les déduire à la place de l'athlète.
   */
  openFor(w: Workout, prefill?: { rpe?: number | null; activity?: MatchedActivity | null }): void {
    this.workout.set(w);
    this.feel.set(w.feel ?? null);
    this.rpe.set(prefill?.rpe ?? w.rpe ?? null);
    this.fatigue.set(null);
    this.pain.set(null);
    this.comment.set(w.athleteComment ?? '');
    // Rouvrir un débrief déjà rempli doit repartir de ce qui a été déclaré : sinon corriger son
    // RPE effacerait silencieusement la blessure qu'on avait pris la peine de nommer.
    this.injuries.set(w.injuries ?? []);
    this.activity.set(prefill?.activity ?? null);
    // Toujours rouvrir sur le ressenti : c'est le geste courant, les deux autres sont des sorties.
    this.step.set('debrief');
    this.actualMinutes.set(null);
    this.missedReason.set('UNEXPECTED');
    this.open.set(true);
  }

  /** « 42:10 » ou « 1:08:32 » — durée réalisée, en mono tabulaire. */
  protected durationLabel(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    const mm = String(m).padStart(2, '0');
    const ss = String(s).padStart(2, '0');
    return h ? `${h}:${mm}:${ss}` : `${m}:${ss}`;
  }

  protected sourceLabel(source: string): string {
    return SOURCE_LABELS[source] ?? 'importée';
  }

  /** Séance menée à son terme : la prescription fait foi, aucune durée à déclarer. */
  protected submitDone(): void {
    this.send({
      status: 'COMPLETED',
      feel: this.feel(),
      rpe: this.rpe(),
      fatigue: this.fatigue(),
      pain: this.pain(),
      comment: this.comment() || null,
      injuries: this.injuries(),
    });
  }

  /**
   * Séance écourtée : la durée déclarée remplace la durée prescrite dans le calcul de charge.
   * Sans elle, une sortie longue abandonnée au tiers pesait autant qu'une sortie longue entière.
   */
  protected submitPartial(): void {
    const minutes = this.actualMinutes();
    this.send({
      status: 'PARTIAL',
      feel: this.feel(),
      rpe: this.rpe(),
      fatigue: this.fatigue(),
      pain: this.pain(),
      comment: this.comment() || null,
      injuries: this.injuries(),
      actualDurationS: minutes && minutes > 0 ? Math.round(minutes * 60) : null,
    });
  }

  /**
   * Séance non faite : aucun effort à décrire, seulement un motif — et, s'il y en a une, la
   * blessure qui l'explique. Elle survit là où le reste du ressenti est effacé : c'est
   * précisément sur une séance sautée qu'elle est l'information la plus utile au coach.
   */
  protected submitMissed(): void {
    this.send({
      status: 'MISSED',
      feel: null,
      rpe: null,
      fatigue: null,
      pain: null,
      comment: this.comment() || null,
      injuries: this.injuries(),
      missedReason: this.missedReason(),
    });
  }

  private send(body: WorkoutFeedback & { status: WorkoutStatus }): void {
    const w = this.workout();
    if (!w) return;
    const optimistic: Workout = {
      ...w,
      status: body.status,
      feel: body.feel ?? null,
      rpe: body.rpe ?? null,
      injuries: body.injuries ?? [],
      athleteComment: body.comment ?? null,
      actualDurationS: body.actualDurationS ?? null,
      missedReason: body.missedReason ?? null,
    };

    // Hors ligne : mise à jour optimiste + mise en file (sync au retour réseau).
    if (!this.network.online()) {
      this.queue.enqueue(w.id, body);
      this.open.set(false);
      this.saved.emit(optimistic);
      this.toast.info('Enregistré hors ligne — synchronisé au retour du réseau.');
      return;
    }

    this.portal.feedback(w.id, body).subscribe({
      next: (updated) => {
        this.open.set(false);
        this.saved.emit(updated);
        this.toast.success(body.status === 'MISSED' ? 'Séance marquée non faite' : 'Ressenti enregistré');
      },
      error: () => {
        this.queue.enqueue(w.id, body);
        this.open.set(false);
        this.saved.emit(optimistic);
        this.toast.warning('Hors ligne — ressenti mis en file pour synchronisation.');
      },
    });
  }
}
