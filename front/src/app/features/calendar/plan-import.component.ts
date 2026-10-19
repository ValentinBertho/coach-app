import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { WORKOUT_TYPE_LABELS, WorkoutType } from '../../core/models/workout.model';
import { ToastService } from '../../core/services/toast.service';
import { WorkoutService } from '../../core/services/workout.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PlanFile, parsePlanFile } from './plan-import';

/**
 * Import d'un plan rédigé hors de l'application (cf. docs/IMPORT-PLAN.md).
 *
 * <p>Trois temps, et rien n'est écrit avant le dernier : choisir le fichier, relire l'aperçu
 * séance par séance, importer. Réimporter une version corrigée du même plan met à jour ce qui
 * reste à faire sans dupliquer ni toucher aux séances réalisées.</p>
 */
@Component({
  selector: 'app-plan-import',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, IconComponent],
  template: `
    <section class="card plan-import">
      <h2>Importer un plan</h2>
      <p class="field-hint">
        Un fichier JSON de séances (par exemple le PDF de ton coach converti avec Claude).
        Réimporter une version corrigée met à jour les séances à venir, sans doublon ;
        les séances déjà réalisées ne sont jamais modifiées.
      </p>

      <label class="btn btn-ghost btn-sm plan-import__pick">
        <app-icon name="file-text" [size]="15" /> {{ fileName() || 'Choisir un fichier…' }}
        <input type="file" accept=".json,application/json" hidden (change)="onFile($event)" />
      </label>

      @if (errors().length) {
        <ul class="plan-import__errors" role="alert">
          @for (e of errors(); track $index) { <li>{{ e }}</li> }
        </ul>
      }

      @if (plan(); as p) {
        <p class="plan-import__summary">
          <strong>{{ p.title || 'Plan' }}</strong> — {{ p.workouts.length }} séance(s),
          du {{ first() | date: 'd MMM' }} au {{ last() | date: 'd MMM y' }}
        </p>
        <div class="plan-import__list">
          <table>
            <tbody>
              @for (w of p.workouts; track $index) {
                <tr [title]="w.notes ?? ''">
                  <td class="metric">{{ w.scheduledDate | date: 'EEE d MMM' }}</td>
                  <td>{{ typeLabel(w.type) }}</td>
                  <td>{{ w.title }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <div class="plan-import__actions">
        <button type="button" class="btn btn-ghost btn-sm" (click)="closed.emit()">Annuler</button>
        <button type="button" class="btn btn-primary btn-sm" (click)="submit()" [disabled]="!plan() || busy()">
          Importer {{ plan()?.workouts?.length ?? '' }} séance(s)
        </button>
      </div>
    </section>
  `,
  styles: [`
    .plan-import { margin: 0 0 var(--sp-4); }
    .plan-import h2 { margin: 0 0 var(--sp-1); font-size: var(--text-lg); }
    .plan-import__pick { margin: var(--sp-3) 0; cursor: pointer; }
    .plan-import__errors { color: var(--danger, #c0392b); font-size: var(--text-sm); margin: 0 0 var(--sp-3); padding-left: 1.2em; }
    .plan-import__summary { font-size: var(--text-sm); margin: 0 0 var(--sp-2); }
    .plan-import__list { max-height: 280px; overflow: auto; border: 1px solid var(--hairline); border-radius: 8px; margin-bottom: var(--sp-3); }
    .plan-import__list table { width: 100%; border-collapse: collapse; font-size: var(--text-sm); }
    .plan-import__list td { padding: 4px var(--sp-2); border-bottom: 1px solid var(--hairline); vertical-align: top; }
    .plan-import__list td:first-child { white-space: nowrap; color: var(--ink-3); }
    .plan-import__actions { display: flex; justify-content: flex-end; gap: var(--sp-2); }
  `],
})
export class PlanImportComponent {
  private readonly workoutService = inject(WorkoutService);
  private readonly toast = inject(ToastService);

  readonly athleteId = input.required<string>();
  /** Émis après un import réussi, avec la date de la première séance (pour y amener le calendrier). */
  readonly imported = output<string>();
  readonly closed = output<void>();

  readonly fileName = signal('');
  readonly plan = signal<PlanFile | null>(null);
  readonly errors = signal<string[]>([]);
  readonly busy = signal(false);
  readonly first = computed(() => this.plan()?.workouts[0]?.scheduledDate ?? null);
  readonly last = computed(() => this.plan()?.workouts.at(-1)?.scheduledDate ?? null);

  typeLabel(t: WorkoutType): string { return WORKOUT_TYPE_LABELS[t] ?? t; }

  async onFile(event: Event): Promise<void> {
    const inputEl = event.target as HTMLInputElement;
    const file = inputEl.files?.[0];
    inputEl.value = ''; // rechoisir le même fichier corrigé doit relancer la lecture
    if (!file) return;
    this.fileName.set(file.name);
    const r = parsePlanFile(await file.text());
    this.plan.set(r.ok ? r.plan : null);
    this.errors.set(r.ok ? [] : r.errors);
  }

  submit(): void {
    const p = this.plan();
    if (!p || this.busy()) return;
    this.busy.set(true);
    this.workoutService.importPlan(this.athleteId(), p).subscribe({
      next: (r) => {
        this.busy.set(false);
        const parts = [`${r.created} créée(s)`];
        if (r.updated) parts.push(`${r.updated} mise(s) à jour`);
        if (r.unchanged) parts.push(`${r.unchanged} inchangée(s)`);
        if (r.kept) parts.push(`${r.kept} déjà réalisée(s), conservée(s)`);
        this.toast.success(`Plan importé : ${parts.join(', ')}`);
        this.imported.emit(p.workouts[0].scheduledDate);
      },
      error: () => { this.busy.set(false); this.toast.error('Import impossible.'); },
    });
  }
}
