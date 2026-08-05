import { ChangeDetectionStrategy, Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AthletePortalService, FeedbackPrompt } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { BottomSheetComponent } from '../../shared/components/ui';
import { RpeScaleSelectorComponent } from '../../shared/components/rpe-scale-selector/rpe-scale-selector.component';
import { WorkoutFeedbackSheetComponent } from '../../shared/components/workout-feedback-sheet/workout-feedback-sheet.component';

/**
 * « Ta sortie est enregistrée — comment c'était ? »
 *
 * <p>Monté dans la coquille athlète, donc actif <b>sur tous les écrans</b> du portail. Il vivait
 * dans « Aujourd'hui » : depuis que le calendrier est l'écran d'ouverture, un athlète pouvait
 * traverser toute l'application sans qu'on lui demande jamais son ressenti — et devait aller le
 * saisir lui-même sur la sortie, ce que personne ne fait.</p>
 *
 * <p>Deux parcours selon ce que la sortie a rencontré :</p>
 * <ul>
 *   <li><b>Rattachée à une séance</b> : la feuille de ressenti habituelle s'ouvre, pré-remplie
 *       des faits (distance, durée, FC). L'athlète confirme, il ne saisit pas.</li>
 *   <li><b>Hors programme</b> : aucune séance ne peut porter le ressenti, il se pose donc sur la
 *       sortie — RPE et mot au coach, les deux champs qu'elle porte désormais.</li>
 * </ul>
 *
 * <p>Proposé une fois et une seule : un refus ne doit pas revenir à chaque lancement.</p>
 */
@Component({
  selector: 'app-debrief-prompt',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe, FormsModule, IconComponent, BottomSheetComponent,
    RpeScaleSelectorComponent, WorkoutFeedbackSheetComponent,
  ],
  template: `
    <!-- Sortie hors programme : le ressenti se pose sur la sortie elle-même. -->
    <app-bottom-sheet [(open)]="open" title="Comment c'était ?">
      @if (prompt(); as p) {
        <form class="dbf" (ngSubmit)="save()">
          <div class="dbf-hd">
            <span class="dbf-ico"><app-icon name="footprints" [size]="18" /></span>
            <div class="dbf-id">
              <strong>{{ p.title || 'Ta sortie' }}</strong>
              <span class="field-hint">{{ dayLabel(p) }} · hors programme</span>
            </div>
          </div>

          <div class="dbf-facts">
            @if (p.distanceM) { <span class="metric">{{ (p.distanceM / 1000) | number: '1.0-1' }} km</span> }
            @if (p.durationS) { <span class="metric">{{ fmtDur(p.durationS) }}</span> }
            @if (p.avgHr) { <span class="metric">{{ p.avgHr }} bpm</span> }
          </div>

          <app-rpe-scale-selector [(value)]="rpe" label="Ton effort perçu (RPE)" />

          <label class="dbf-f">Un mot pour ton coach
            <textarea class="form-control" rows="3" [(ngModel)]="comment" name="c"
                      placeholder="Sensations, terrain, météo…"></textarea>
          </label>

          <div class="dbf-actions">
            <button type="submit" class="btn btn-primary" [disabled]="busy()">
              {{ busy() ? 'Envoi…' : 'Envoyer' }}
            </button>
            <button type="button" class="btn btn-ghost" (click)="dismiss()">Plus tard</button>
          </div>
        </form>
      }
    </app-bottom-sheet>

    <!-- Sortie rattachée à une séance : la feuille de ressenti habituelle, pré-remplie. -->
    <app-workout-feedback-sheet />
  `,
  styles: [`
    .dbf { display: flex; flex-direction: column; gap: var(--sp-4); }
    .dbf-hd { display: flex; align-items: center; gap: var(--sp-3); }
    .dbf-ico {
      display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0;
      width: 38px; height: 38px; border-radius: var(--radius-full);
      background: var(--primary-wash); color: var(--primary);
    }
    .dbf-id { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .dbf-id strong { color: var(--ink); }
    .dbf-facts { display: flex; flex-wrap: wrap; gap: var(--sp-3); color: var(--ink-2); font-size: var(--text-sm); font-variant-numeric: tabular-nums; }
    .dbf-f { display: flex; flex-direction: column; gap: 4px; font-size: var(--text-sm); color: var(--ink-2); font-weight: 600; }
    .dbf-actions { display: flex; align-items: center; gap: var(--sp-2); }
    .dbf-actions .btn { flex: 1; }
  `],
})
export class DebriefPromptComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly workoutSheet = viewChild(WorkoutFeedbackSheetComponent);

  readonly open = signal(false);
  readonly prompt = signal<FeedbackPrompt | null>(null);
  readonly busy = signal(false);
  rpe: number | null = null;
  comment = '';

  ngOnInit(): void {
    this.portal.feedbackPrompt().subscribe({
      next: (prompt) => this.offer(prompt),
      error: () => undefined,
    });
  }

  private offer(prompt: FeedbackPrompt | null): void {
    if (!prompt) return;
    this.prompt.set(prompt);
    if (prompt.workout) {
      // Séance rapprochée : la feuille connaît déjà ce parcours, faits pré-remplis compris.
      this.workoutSheet()?.openFor(prompt.workout, { activity: prompt });
    } else {
      this.rpe = null;
      this.comment = '';
      this.open.set(true);
    }
    // Vue une fois, proposée une fois : un refus ne doit pas revenir à chaque lancement.
    this.portal.ackFeedbackPrompt(prompt.activityId).subscribe({ error: () => undefined });
  }

  save(): void {
    const p = this.prompt();
    if (!p || this.busy()) return;
    this.busy.set(true);
    const comment = this.comment.trim();
    this.portal.updateActivity(p.activityId, {
      rpe: this.rpe ?? undefined,
      clearRpe: this.rpe == null,
      comment: comment || undefined,
      clearComment: !comment,
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.open.set(false);
        this.toast.success('Ressenti envoyé à ton coach');
      },
      error: () => { this.busy.set(false); this.toast.error('Envoi impossible.'); },
    });
  }

  dismiss(): void {
    this.open.set(false);
  }

  dayLabel(p: FeedbackPrompt): string {
    if (!p.activityDate) return 'Sortie récente';
    return new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
      .format(new Date(p.activityDate + 'T00:00:00'));
  }

  fmtDur(s: number): string {
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    return h > 0 ? `${h}h${String(m).padStart(2, '0')}` : `${m} min`;
  }
}
