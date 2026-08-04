import { ChangeDetectionStrategy, Component, inject, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MissedReason, Workout, WorkoutStatus } from '../../../core/models/workout.model';
import { AthletePortalService, WorkoutFeedback } from '../../../core/services/athlete-portal.service';
import { FeedbackQueueService } from '../../../core/services/feedback-queue.service';
import { NetworkStatusService } from '../../../core/services/network-status.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';
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
 * Retour de séance course (RPE + fatigue + douleur + commentaire), en bottom sheet ~10 s.
 *
 * Composant unique partagé par « Aujourd'hui », l'agenda et l'historique : une séance non
 * clôturée doit être notable d'où qu'on la regarde, sans que la file hors ligne ni les
 * invariants (la forme = fatigue + douleur, jamais le RPE) soient réécrits trois fois.
 *
 * @example
 * <app-workout-feedback-sheet [(open)]="fbOpen" [workout]="fbWorkout()" (saved)="onSaved($event)" />
 */
@Component({
  selector: 'app-workout-feedback-sheet',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent, BottomSheetComponent, PainFatigueSelectorComponent, RpeScaleSelectorComponent],
  template: `
    <app-bottom-sheet [(open)]="open" [title]="sheetTitle()">
      @if (workout(); as w) {
        <div class="fb">
          <!-- Une séance rattrapée n'est plus « celle du jour » : on rappelle laquelle on note. -->
          @if (subtitle(); as s) { <p class="fb__lead">{{ w.title }}<br /><span class="field-hint">{{ s }}</span></p> }

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
            @case ('feel') {
              <app-rpe-scale-selector [(value)]="rpe" />

              <app-pain-fatigue-selector kind="fatigue" [(value)]="fatigue" />
              <app-pain-fatigue-selector kind="pain" [(value)]="pain" />

              <textarea class="form-control" rows="2" placeholder="Un commentaire ? (optionnel)"
                        [ngModel]="comment()" (ngModelChange)="comment.set($event)"></textarea>

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
                <button type="button" class="btn btn-ghost" (click)="step.set('feel')">Retour</button>
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
              <div class="fb__actions">
                <button type="button" class="btn btn-accent btn-lg" (click)="submitMissed()">Confirmer <app-icon name="check" [size]="16" /></button>
                <button type="button" class="btn btn-ghost" (click)="step.set('feel')">Retour</button>
              </div>
            }
          }
        </div>
      }
    </app-bottom-sheet>
  `,
  styles: [`
    .fb { display: flex; flex-direction: column; gap: var(--sp-5); }
    .fb__lead { margin: 0; font-weight: 700; color: var(--ink); }
    .fb__actions { display: flex; flex-direction: column; gap: var(--sp-2); }
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

  readonly rpe = signal<number | null>(null);
  readonly fatigue = signal<number | null>(null);
  readonly pain = signal<number | null>(null);
  readonly comment = signal('');
  /** Activité rapprochée affichée en contexte, ou `null` (ouverture manuelle). */
  readonly activity = signal<MatchedActivity | null>(null);

  /**
   * Étape de la feuille. Le ressenti reste l'écran par défaut et le geste en un tap ; les deux
   * autres ne s'ouvrent que si l'athlète les demande, et posent une seule question chacune.
   */
  readonly step = signal<'feel' | 'partial' | 'missed'>('feel');
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
    return this.isLate() ? 'Ton ressenti (en retard)' : 'Ton ressenti';
  }

  /** Rappel de la date pour une séance rattrapée ; rien pour la séance du jour. */
  protected subtitle(): string | null {
    const w = this.workout();
    if (!w || !this.isLate()) return null;
    const fmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
    return `Séance du ${fmt.format(new Date(w.scheduledDate + 'T00:00:00'))}`;
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
    this.rpe.set(prefill?.rpe ?? w.rpe ?? null);
    this.fatigue.set(null);
    this.pain.set(null);
    this.comment.set(w.athleteComment ?? '');
    this.activity.set(prefill?.activity ?? null);
    // Toujours rouvrir sur le ressenti : c'est le geste courant, les deux autres sont des sorties.
    this.step.set('feel');
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
      rpe: this.rpe(),
      fatigue: this.fatigue(),
      pain: this.pain(),
      comment: this.comment() || null,
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
      rpe: this.rpe(),
      fatigue: this.fatigue(),
      pain: this.pain(),
      comment: this.comment() || null,
      actualDurationS: minutes && minutes > 0 ? Math.round(minutes * 60) : null,
    });
  }

  /** Séance non faite : aucun effort à décrire, seulement un motif. */
  protected submitMissed(): void {
    this.send({
      status: 'MISSED',
      rpe: null,
      fatigue: null,
      pain: null,
      comment: this.comment() || null,
      missedReason: this.missedReason(),
    });
  }

  private send(body: WorkoutFeedback & { status: WorkoutStatus }): void {
    const w = this.workout();
    if (!w) return;
    const optimistic: Workout = {
      ...w,
      status: body.status,
      rpe: body.rpe ?? null,
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
