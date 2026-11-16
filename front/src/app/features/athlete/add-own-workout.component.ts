import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { WORKOUT_TYPE_LABELS, Workout, WorkoutType } from '../../core/models/workout.model';
import { WorkoutTemplate } from '../../core/models/workout-template.model';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * « Ajouter une séance » depuis la feuille d'un jour.
 *
 * <p>Sans coach, l'athlète pioche dans le catalogue de son espace ou décrit sa séance en trois
 * champs. Avec un coach, le catalogue est celui du coach : il ne reste que la saisie à la main —
 * une sortie de club, un footing entre amis — et la séance arrive chez le coach marquée
 * « ajoutée par l'athlète ».</p>
 */
@Component({
  selector: 'app-add-own-workout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    @if (!open()) {
      <button type="button" class="btn btn-ghost btn-sm aow-open" (click)="start()">
        <app-icon name="plus" [size]="14" /> Ajouter une séance ce jour-là
      </button>
    } @else {
      <div class="aow">
        @if (withCatalog()) {
          <div class="aow-tabs" role="tablist">
            <button type="button" role="tab" [class.on]="tab() === 'catalog'"
                    [attr.aria-selected]="tab() === 'catalog'" (click)="tab.set('catalog')">
              Mon catalogue
            </button>
            <button type="button" role="tab" [class.on]="tab() === 'manual'"
                    [attr.aria-selected]="tab() === 'manual'" (click)="tab.set('manual')">
              À la main
            </button>
          </div>
        }

        @if (withCatalog() && tab() === 'catalog') {
          <input class="form-control" type="search" placeholder="Chercher : seuil, fractionné, sortie longue…"
                 [ngModel]="query()" (ngModelChange)="query.set($event)" aria-label="Chercher une séance" />
          @if (loading()) {
            <p class="field-hint">Chargement du catalogue…</p>
          } @else if (filtered().length === 0) {
            <p class="field-hint">Aucune séance ne correspond. Passe par « À la main ».</p>
          } @else {
            <ul class="aow-list">
              @for (t of filtered(); track t.id) {
                <li>
                  <button type="button" [disabled]="busy()" (click)="pick(t)">
                    <span class="aow-name">{{ t.name }}</span>
                    <span class="field-hint">{{ labels[t.type] }}{{ volume(t) }}</span>
                  </button>
                </li>
              }
            </ul>
          }
        } @else {
          <div class="aow-form">
            <label class="form-label" for="aow-type">Type</label>
            <select id="aow-type" class="form-control" [(ngModel)]="type">
              @for (t of types; track t) { <option [value]="t">{{ labels[t] }}</option> }
            </select>
            <label class="form-label" for="aow-title">Intitulé</label>
            <input id="aow-title" class="form-control" maxlength="255" [(ngModel)]="title"
                   placeholder="Sortie du club, footing entre amis…" />
            <div class="aow-row">
              <div>
                <label class="form-label" for="aow-min">Durée (min)</label>
                <input id="aow-min" class="form-control" type="number" min="0" inputmode="numeric" [(ngModel)]="minutes" />
              </div>
              <div>
                <label class="form-label" for="aow-km">Distance (km)</label>
                <input id="aow-km" class="form-control" type="number" min="0" step="0.1" inputmode="decimal" [(ngModel)]="km" />
              </div>
            </div>
            <label class="form-label" for="aow-notes">Notes (facultatif)</label>
            <textarea id="aow-notes" class="form-control" rows="2" maxlength="2048" [(ngModel)]="notes"></textarea>
          </div>
        }

        <div class="aow-actions">
          <button type="button" class="btn btn-ghost btn-sm" (click)="open.set(false)">Annuler</button>
          @if (!withCatalog() || tab() === 'manual') {
            <button type="button" class="btn btn-primary btn-sm" [disabled]="!title.trim() || busy()" (click)="save()">
              {{ busy() ? 'Ajout…' : 'Ajouter' }}
            </button>
          }
        </div>
        @if (!withCatalog()) {
          <p class="field-hint">Ton coach la verra dans ton calendrier, marquée « ajoutée par l'athlète ».</p>
        }
      </div>
    }
  `,
  styles: [`
    .aow-open { margin-top: var(--sp-2); }
    .aow { display: flex; flex-direction: column; gap: var(--sp-2); margin-top: var(--sp-3);
      padding: var(--sp-3); border: 1px solid var(--hairline); border-radius: var(--radius-md, 12px); }
    .aow-tabs { display: flex; gap: var(--sp-2); }
    .aow-tabs button { flex: 1; padding: var(--sp-2); border-radius: 999px; border: 1px solid var(--hairline);
      background: transparent; color: var(--ink-2); font-weight: 600; }
    .aow-tabs button.on { background: var(--primary); border-color: var(--primary); color: #fff; }
    .aow-list { list-style: none; margin: 0; padding: 0; max-height: 280px; overflow-y: auto; }
    .aow-list button { width: 100%; text-align: left; display: flex; flex-direction: column; gap: 2px;
      padding: var(--sp-2) 0; background: none; border: 0; border-top: 1px solid var(--hairline); color: var(--ink); }
    .aow-name { font-weight: 600; }
    .aow-form { display: flex; flex-direction: column; gap: var(--sp-1); }
    .aow-row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--sp-2); }
    .aow-actions { display: flex; justify-content: flex-end; gap: var(--sp-2); }
  `],
})
export class AddOwnWorkoutComponent {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);

  /** Jour visé (ISO). */
  readonly date = input.required<string>();
  /** Espace personnel : le catalogue est proposé. */
  readonly withCatalog = input(false);
  readonly added = output<Workout>();

  readonly labels = WORKOUT_TYPE_LABELS;
  readonly types: WorkoutType[] = ['ENDURANCE', 'RECOVERY', 'LONG_RUN', 'TEMPO', 'THRESHOLD',
    'INTERVALS', 'RACE', 'STRENGTH', 'CROSS_TRAINING'];

  readonly open = signal(false);
  readonly tab = signal<'catalog' | 'manual'>('catalog');
  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly query = signal('');
  readonly templates = signal<WorkoutTemplate[]>([]);
  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const all = this.templates();
    return q ? all.filter((t) => `${t.name} ${t.title}`.toLowerCase().includes(q)) : all;
  });

  type: WorkoutType = 'ENDURANCE';
  title = '';
  minutes: number | null = null;
  km: number | null = null;
  notes = '';

  start(): void {
    this.open.set(true);
    this.tab.set(this.withCatalog() ? 'catalog' : 'manual');
    if (this.withCatalog() && this.templates().length === 0) {
      this.loading.set(true);
      this.portal.catalog().subscribe({
        next: (list) => { this.templates.set(list); this.loading.set(false); },
        error: () => { this.templates.set([]); this.loading.set(false); this.tab.set('manual'); },
      });
    }
  }

  volume(t: WorkoutTemplate): string {
    if (t.targetDistanceM) return ` · ${(t.targetDistanceM / 1000).toLocaleString('fr-FR', { maximumFractionDigits: 1 })} km`;
    if (t.targetDurationS) return ` · ${Math.round(t.targetDurationS / 60)} min`;
    return '';
  }

  pick(t: WorkoutTemplate): void {
    this.busy.set(true);
    this.portal.scheduleFromCatalog(t.id, this.date()).subscribe({
      next: (w) => this.done(w),
      error: () => { this.busy.set(false); this.toast.error('Séance non ajoutée.'); },
    });
  }

  save(): void {
    const title = this.title.trim();
    if (!title) return;
    this.busy.set(true);
    this.portal.addOwnWorkout({
      scheduledDate: this.date(),
      type: this.type,
      title,
      notes: this.notes.trim() || null,
      targetDurationS: this.minutes ? Math.round(this.minutes * 60) : null,
      targetDistanceM: this.km ? Math.round(this.km * 1000) : null,
      steps: [],
    }).subscribe({
      next: (w) => this.done(w),
      error: () => { this.busy.set(false); this.toast.error('Séance non ajoutée.'); },
    });
  }

  private done(w: Workout): void {
    this.busy.set(false);
    this.open.set(false);
    this.title = '';
    this.minutes = null;
    this.km = null;
    this.notes = '';
    this.toast.success('Séance ajoutée à ton calendrier.');
    this.added.emit(w);
  }
}
