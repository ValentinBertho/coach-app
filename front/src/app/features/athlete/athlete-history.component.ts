import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SegmentedControlComponent } from '../../shared/components/ui';
import {
  STATUS_BADGE, STATUS_LABELS, WORKOUT_TYPE_LABELS, Workout,
} from '../../core/models/workout.model';

interface MonthGroup {
  label: string;
  items: Workout[];
}

/**
 * Mon historique de séances (athlète, lecture seule) — séances passées avec mon ressenti
 * (RPE) et le réalisé vs prescrit. Câblé sur /me/workouts (plage). CDC §10 « Mon historique ».
 */
@Component({
  selector: 'app-athlete-history',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, DecimalPipe, RouterLink, IconComponent, SegmentedControlComponent],
  template: `
    <div class="hist">
      <header class="hist-top">
        <a routerLink="/athlete/progress" class="btn btn-ghost btn-sm">← Progrès</a>
        <h1 class="display-sm">Mon historique</h1>
        <p class="subtitle">Tes séances passées et ton ressenti.</p>
        <app-segmented-control [options]="rangeOptions" [value]="weeks()"
          (valueChange)="setWeeks($event)" ariaLabel="Période" />
      </header>

      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 80px;"></div></div>
      } @else if (groups().length === 0) {
        <div class="card empty">
          <h2>Pas encore de séance</h2>
          <p class="field-hint">Tes séances réalisées apparaîtront ici.</p>
        </div>
      } @else {
        @for (g of groups(); track g.label) {
          <section class="month">
            <h2 class="month-h">{{ g.label }}</h2>
            @for (w of g.items; track w.id) {
              <article class="row card">
                <div class="row-date">
                  <span class="d">{{ w.scheduledDate | date: 'dd' }}</span>
                  <span class="m">{{ w.scheduledDate | date: 'MMM' }}</span>
                </div>
                <div class="row-body">
                  <div class="row-hd">
                    <strong>{{ w.title }}</strong>
                    <span class="badge" [class]="statusBadge(w.status)">{{ statusLabel(w.status) }}</span>
                  </div>
                  <div class="row-meta field-hint">
                    <span>{{ typeLabel(w.type) }}</span>
                    @if (w.targetDistanceM) { <span>· {{ (w.targetDistanceM / 1000) | number: '1.0-1' }} km</span> }
                    @if (w.rpe != null) { <span>· RPE {{ w.rpe }}</span> }
                  </div>
                  @if (w.athleteComment) {
                    <p class="row-cmt"><app-icon name="message-square" [size]="13" /> {{ w.athleteComment }}</p>
                  }
                </div>
              </article>
            }
          </section>
        }
      }
    </div>
  `,
  styles: [`
    .hist { max-width: 560px; margin-inline: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .hist-top { display: flex; flex-direction: column; gap: var(--sp-2); align-items: flex-start; }
    .hist-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: 0; }
    .empty { text-align: center; }

    .month { display: flex; flex-direction: column; gap: var(--sp-2); }
    .month-h { font-size: var(--text-md); color: var(--ink-3); text-transform: capitalize; margin: var(--sp-2) 0 0; }

    .row { display: flex; gap: var(--sp-3); align-items: flex-start; padding: var(--sp-3); }
    .row-date { display: flex; flex-direction: column; align-items: center; min-width: 38px; }
    .row-date .d { font-size: var(--text-xl); font-weight: 800; color: var(--ink); line-height: 1; font-variant-numeric: tabular-nums; }
    .row-date .m { font-size: var(--text-sm); color: var(--ink-3); text-transform: uppercase; }
    .row-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
    .row-hd { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2); }
    .row-hd strong { color: var(--ink); min-width: 0; }
    .row-meta { display: flex; flex-wrap: wrap; gap: 4px; }
    .row-cmt { margin: 2px 0 0; font-size: var(--text-sm); color: var(--ink-2); display: flex; align-items: center; gap: 4px; }
  `],
})
export class AthleteHistoryComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);

  readonly rangeOptions = [
    { label: '4 sem.', value: '4' },
    { label: '12 sem.', value: '12' },
    { label: '26 sem.', value: '26' },
  ];
  readonly weeks = signal('12');
  readonly loading = signal(true);
  readonly workouts = signal<Workout[]>([]);

  /** Séances passées (date ≤ aujourd'hui), du plus récent au plus ancien, groupées par mois. */
  readonly groups = computed<MonthGroup[]>(() => {
    const today = new Date().toISOString().slice(0, 10);
    const past = this.workouts()
      .filter((w) => w.scheduledDate <= today)
      .sort((a, b) => b.scheduledDate.localeCompare(a.scheduledDate));
    const out: MonthGroup[] = [];
    const fmt = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' });
    for (const w of past) {
      const label = fmt.format(new Date(w.scheduledDate + 'T00:00:00'));
      let g = out.find((x) => x.label === label);
      if (!g) { g = { label, items: [] }; out.push(g); }
      g.items.push(w);
    }
    return out;
  });

  ngOnInit(): void { this.fetch(); }

  setWeeks(w: string): void { this.weeks.set(w); this.fetch(); }

  private fetch(): void {
    this.loading.set(true);
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - Number(this.weeks()) * 7);
    const iso = (d: Date) => d.toISOString().slice(0, 10);
    this.portal.workouts(iso(from), iso(to)).subscribe({
      next: (ws) => { this.workouts.set(ws); this.loading.set(false); },
      error: () => { this.workouts.set([]); this.loading.set(false); },
    });
  }

  statusLabel(s: Workout['status']): string { return STATUS_LABELS[s]; }
  statusBadge(s: Workout['status']): string { return STATUS_BADGE[s]; }
  typeLabel(t: Workout['type']): string { return WORKOUT_TYPE_LABELS[t]; }
}
