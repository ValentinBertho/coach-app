import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  STATUS_BADGE,
  STATUS_LABELS,
  Workout,
  isRealised,
} from '../../core/models/workout.model';
import { WorkoutService } from '../../core/services/workout.service';
import { frNumber } from '../../core/utils/number-format';

/** Date ISO locale (aaaa-mm-jj) décalée de `days` jours depuis aujourd'hui. */
function isoOffset(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/**
 * L'entraînement en un coup d'œil, en tête du Résumé de la fiche athlète.
 *
 * <p>Le Résumé montrait le profil physiologique (déjà répété dans Zones et Tests), les
 * indisponibilités et des réglages — mais rien de ce que le coach vient chercher en ouvrant
 * une fiche : ce qui s'est passé ces derniers jours, ce qui arrive, et ce que l'athlète a dit
 * de sa dernière séance.</p>
 */
@Component({
  selector: 'app-athlete-week-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="card ws">
      <div class="ws__col">
        <div class="ws__hd">
          <h2>7 derniers jours</h2>
          @if (past().length) {
            <span class="ws__score metric">{{ doneCount() }}/{{ past().length }} faites</span>
          }
        </div>
        @if (past().length) {
          <ul class="ws__list">
            @for (w of past(); track w.id) {
              <li>
                <a class="ws__row" [routerLink]="['/app/athletes', athleteId(), 'workouts', w.id]">
                  <span class="ws__day">{{ dayLabel(w.scheduledDate) }}</span>
                  <span class="ws__title">{{ w.title }}</span>
                  <span class="badge" [class]="badge[w.status]">{{ statusLabel(w) }}</span>
                </a>
              </li>
            }
          </ul>
        } @else {
          <p class="field-hint">Aucune séance prévue sur les 7 derniers jours.</p>
        }
      </div>

      <div class="ws__col">
        <div class="ws__hd">
          <h2>À venir</h2>
          <a [routerLink]="['/app/athletes', athleteId(), 'programme']" class="field-hint">Programme →</a>
        </div>
        @if (next().length) {
          <ul class="ws__list">
            @for (w of next(); track w.id) {
              <li>
                <a class="ws__row" [routerLink]="['/app/athletes', athleteId(), 'workouts', w.id]">
                  <span class="ws__day">{{ dayLabel(w.scheduledDate) }}</span>
                  <span class="ws__title">{{ w.title }}</span>
                  @if (volume(w); as v) { <span class="ws__vol metric">{{ v }}</span> }
                </a>
              </li>
            }
          </ul>
        } @else {
          <p class="field-hint">Rien de planifié pour les deux prochaines semaines.</p>
        }
      </div>

      <div class="ws__col">
        <div class="ws__hd"><h2>Dernier retour</h2></div>
        @if (lastFeedback(); as f) {
          <a class="ws__fb" [routerLink]="['/app/athletes', athleteId(), 'workouts', f.id]">
            <span class="ws__day">{{ dayLabel(f.scheduledDate) }} · {{ f.title }}</span>
            <span class="ws__fbvals metric">
              @if (f.rpe != null) { RPE {{ f.rpe }}/10 }
              @if (f.fatigue != null) { · fatigue {{ f.fatigue }} }
              @if (f.pain != null) {
                <span [class.ws__pain]="f.pain >= 4"> · douleur {{ f.pain }}</span>
              }
            </span>
            @if (f.athleteComment) { <span class="ws__quote">« {{ f.athleteComment }} »</span> }
          </a>
        } @else {
          <p class="field-hint">Pas encore de retour sur les 7 derniers jours.</p>
        }
      </div>
    </section>
  `,
  styles: [`
    .ws {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: var(--sp-4) var(--sp-5);
      margin-bottom: var(--sp-4);
    }
    .ws__col { min-width: 0; display: flex; flex-direction: column; gap: var(--sp-2); }
    .ws__hd { display: flex; align-items: baseline; justify-content: space-between; gap: var(--sp-2); }
    .ws__hd h2 { margin: 0; font-size: var(--text-md); }
    .ws__score { font-size: var(--text-sm); color: var(--ink-2); font-weight: 700; }
    .ws__list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
    .ws__row {
      display: grid; grid-template-columns: 64px minmax(0, 1fr) auto; align-items: center;
      gap: var(--sp-2); padding: 6px 0; border-top: 1px solid var(--hairline);
      text-decoration: none; color: var(--ink); font-size: var(--text-sm);
    }
    .ws__list li:first-child .ws__row { border-top: none; }
    .ws__row:hover .ws__title { color: var(--primary); }
    .ws__day { color: var(--ink-3); font-size: var(--text-xs); text-transform: capitalize; }
    .ws__title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
    .ws__vol { color: var(--ink-2); font-size: var(--text-xs); }
    .ws__fb {
      display: flex; flex-direction: column; gap: 4px; text-decoration: none; color: var(--ink);
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius); background: var(--paper-sunk);
    }
    .ws__fbvals { font-size: var(--text-sm); font-weight: 700; }
    .ws__pain { color: var(--danger); }
    .ws__quote { font-size: var(--text-sm); color: var(--ink-2); font-style: italic; }
  `],
})
export class AthleteWeekSummaryComponent implements OnInit {
  readonly athleteId = input.required<string>();
  private readonly workouts = inject(WorkoutService);

  protected readonly badge = STATUS_BADGE;
  private readonly all = signal<Workout[]>([]);
  private readonly today = isoOffset(0);

  /** Séances des 7 derniers jours (aujourd'hui compris), du plus récent au plus ancien. */
  readonly past = computed(() =>
    this.all()
      .filter((w) => w.type !== 'REST' && w.scheduledDate <= this.today && w.scheduledDate >= isoOffset(-6))
      .sort((a, b) => b.scheduledDate.localeCompare(a.scheduledDate)),
  );
  readonly doneCount = computed(() => this.past().filter((w) => isRealised(w)).length);
  /** Les trois prochaines séances, à partir de demain. */
  readonly next = computed(() =>
    this.all()
      .filter((w) => w.type !== 'REST' && w.scheduledDate > this.today)
      .sort((a, b) => a.scheduledDate.localeCompare(b.scheduledDate))
      .slice(0, 3),
  );
  /** Dernière séance où l'athlète a dit quelque chose (RPE, fatigue, douleur ou mot). */
  readonly lastFeedback = computed(
    () =>
      this.past().find(
        (w) => w.rpe != null || w.fatigue != null || w.pain != null || !!w.athleteComment,
      ) ?? null,
  );

  ngOnInit(): void {
    this.workouts.calendar(this.athleteId(), isoOffset(-6), isoOffset(14)).subscribe({
      next: (ws) => this.all.set(ws),
      error: () => this.all.set([]),
    });
  }

  dayLabel(iso: string): string {
    if (iso === this.today) return "aujourd'hui";
    const d = new Date(iso + 'T00:00:00');
    return new Intl.DateTimeFormat('fr-FR', { weekday: 'short', day: 'numeric' }).format(d);
  }

  statusLabel(w: Workout): string {
    if (w.status === 'PLANNED' && w.scheduledDate < this.today) return 'Sans retour';
    return STATUS_LABELS[w.status];
  }

  volume(w: Workout): string {
    if (w.targetDistanceM && w.targetDistanceM >= 500) {
      return `${frNumber(w.targetDistanceM / 1000, 1).replace(/,0$/, '')} km`;
    }
    if (w.targetDurationS) return `${Math.round(w.targetDurationS / 60)} min`;
    return '';
  }
}
