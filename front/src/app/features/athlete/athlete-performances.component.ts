import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { Performance } from '../../core/models/physio.model';

const DISTANCES: { value: string; label: string }[] = [
  { value: 'D1500', label: '1500 m' },
  { value: 'D3000', label: '3000 m' },
  { value: 'D5KM', label: '5 km' },
  { value: 'D10KM', label: '10 km' },
  { value: 'D15KM', label: '15 km' },
  { value: 'SEMI', label: 'Semi-marathon' },
  { value: 'MARATHON', label: 'Marathon' },
];

/**
 * Mes performances de référence (athlète) — chronos par distance qui alimentent le VDOT et
 * donc les allures de travail. Permet à un athlète sans test lactate d'obtenir des allures
 * dès son arrivée (onboarding). Câblé sur /me/performances.
 */
@Component({
  selector: 'app-athlete-performances',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, RouterLink, IconComponent],
  template: `
    <div class="perfs">
      <header class="perfs-top">
        <a routerLink="/athlete/progress" class="btn btn-ghost btn-sm">← Progrès</a>
        <h1 class="display-sm">Mes performances</h1>
        <p class="subtitle">Tes chronos de référence calculent tes allures de travail (VDOT).</p>
      </header>

      <form class="card perf-form" (ngSubmit)="submit()">
        <div class="pf-row">
          <label>Distance
            <select class="form-control" [(ngModel)]="draft.distance" name="d">
              @for (d of distances; track d.value) { <option [value]="d.value">{{ d.label }}</option> }
            </select>
          </label>
          <label>Temps (hh:mm:ss)
            <input class="form-control" [(ngModel)]="draft.time" name="t" placeholder="0:42:30" required />
          </label>
        </div>
        <div class="pf-row">
          <label>Date (optionnel)<input type="date" class="form-control" [(ngModel)]="draft.dateSet" name="ds" /></label>
        </div>
        <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy()">
          {{ busy() ? 'Enregistrement…' : 'Ajouter la performance' }}
        </button>
      </form>

      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 56px;"></div></div>
      } @else if (performances().length === 0) {
        <div class="card empty"><p class="field-hint">Aucune performance. Ajoute ton meilleur chrono récent pour obtenir tes allures.</p></div>
      } @else {
        @for (p of performances(); track p.id) {
          <article class="card perf">
            <div class="perf-id">
              <strong>{{ p.distance }}</strong>
              <span class="field-hint">{{ fmt(p.timeSeconds) }}@if (p.dateSet) { · {{ p.dateSet | date: 'MMM yyyy' }} }</span>
            </div>
            @if (p.vdot != null) { <span class="badge badge-info">VDOT {{ p.vdot }}</span> }
          </article>
        }
      }
    </div>
  `,
  styles: [`
    .perfs { max-width: 560px; margin-inline: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .perfs-top { display: flex; flex-direction: column; gap: var(--sp-1); align-items: flex-start; }
    .perfs-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: 0; }
    .perf-form { display: flex; flex-direction: column; gap: var(--sp-3); }
    .perf-form label { display: flex; flex-direction: column; gap: 4px; font-size: var(--text-sm); color: var(--ink-2); font-weight: 600; }
    .pf-row { display: flex; gap: var(--sp-2); }
    .pf-row label { flex: 1; }
    .empty { text-align: center; }
    .perf { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); }
    .perf-id { display: flex; flex-direction: column; gap: 2px; }
    .perf-id strong { color: var(--ink); }
  `],
})
export class AthletePerformancesComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);

  readonly distances = DISTANCES;
  readonly loading = signal(true);
  readonly performances = signal<Performance[]>([]);
  readonly busy = signal(false);
  draft: { distance: string; time: string; dateSet: string } = { distance: 'D10KM', time: '', dateSet: '' };

  ngOnInit(): void { this.load(); }

  private load(): void {
    this.loading.set(true);
    this.portal.performances().subscribe({
      next: (p) => { this.performances.set(p); this.loading.set(false); },
      error: () => { this.performances.set([]); this.loading.set(false); },
    });
  }

  submit(): void {
    const seconds = this.parseTime(this.draft.time);
    if (!this.draft.distance || seconds == null || this.busy()) {
      this.toast.warning('Renseigne une distance et un temps (hh:mm:ss).');
      return;
    }
    this.busy.set(true);
    this.portal.addPerformance({
      distance: this.draft.distance,
      timeSeconds: seconds,
      dateSet: this.draft.dateSet || null,
    }).subscribe({
      next: () => {
        this.busy.set(false);
        this.draft = { distance: 'D10KM', time: '', dateSet: '' };
        this.toast.success('Performance ajoutée — allures mises à jour');
        this.load();
      },
      error: () => { this.busy.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  fmt(s: number): string {
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const sec = s % 60;
    return h > 0
      ? `${h}:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`
      : `${m}:${sec.toString().padStart(2, '0')}`;
  }

  private parseTime(t: string): number | null {
    if (!t?.trim()) { return null; }
    const parts = t.split(':').map((x) => Number(x));
    if (parts.some((n) => Number.isNaN(n))) { return null; }
    if (parts.length === 3) { return parts[0] * 3600 + parts[1] * 60 + parts[2]; }
    if (parts.length === 2) { return parts[0] * 60 + parts[1]; }
    return parts[0];
  }
}
