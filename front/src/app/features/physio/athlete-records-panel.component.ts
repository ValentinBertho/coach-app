import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { Performance, Vdot } from '../../core/models/physio.model';
import { PhysioService } from '../../core/services/physio.service';
import { ToastService } from '../../core/services/toast.service';

/** Distances de référence saisissables (éligibles au calcul VDOT). */
const DISTANCES: { value: string; label: string }[] = [
  { value: 'D800', label: '800 m' },
  { value: 'D1500', label: '1500 m' },
  { value: 'D3000', label: '3000 m' },
  { value: 'D5KM', label: '5 km' },
  { value: 'D10KM', label: '10 km' },
  { value: 'D15KM', label: '15 km' },
  { value: 'SEMI', label: 'Semi (21,1 km)' },
  { value: 'MARATHON', label: 'Marathon (42,2 km)' },
];

/**
 * Historique des records de l'athlète (800 m → marathon) côté coach. Saisir un chrono
 * <b>recalcule automatiquement le VDOT</b> et, par ricochet, les allures dérivées (Pace 800…
 * marathon) qui servent d'ancres aux zones d'allure — les cibles de séance suivent donc seules.
 */
@Component({
  selector: 'app-athlete-records-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    <section class="card records">
      <header class="rec-head">
        <div>
          <h2>Records</h2>
          <p class="field-hint">
            Un chrono saisi met à jour le VDOT et les allures dérivées (utilisées comme ancres
            « Allure 800 m … marathon » dans les zones).
          </p>
        </div>
        @if (vdot()?.vdot != null) {
          <span class="vdot-badge">VDOT <strong class="metric">{{ vdot()!.vdot }}</strong></span>
        }
      </header>

      <!-- Saisie : distance · temps · date -->
      <form class="rec-form" (ngSubmit)="add()">
        <select class="form-control" [(ngModel)]="draft.distance" name="distance" aria-label="Distance">
          @for (d of distances; track d.value) { <option [value]="d.value">{{ d.label }}</option> }
        </select>
        <input class="form-control rf-time" [(ngModel)]="draft.time" name="time"
               placeholder="mm:ss ou h:mm:ss" aria-label="Temps" />
        <input class="form-control rf-date" type="date" [(ngModel)]="draft.dateSet" name="dateSet" aria-label="Date" />
        <button type="submit" class="btn btn-primary" [disabled]="busy()">
          <app-icon name="plus" [size]="15" /> Ajouter
        </button>
      </form>

      @if (loading()) {
        <div class="skeleton" style="height:80px"></div>
      } @else if (records().length === 0) {
        <p class="empty-hint">Aucun record enregistré. Saisis un chrono pour amorcer le VDOT.</p>
      } @else {
        <ul class="rec-list">
          @for (p of records(); track p.id) {
            <li class="rec-row">
              <span class="rr-dist">{{ label(p.distanceCode || p.distance) }}</span>
              <span class="rr-time metric">{{ hms(p.timeSeconds) }}</span>
              <span class="rr-date field-hint">{{ p.dateSet || '—' }}</span>
              @if (p.vdot != null) { <span class="badge badge-info">VDOT {{ p.vdot }}</span> }
              <button type="button" class="icon-btn danger" (click)="remove(p)" [attr.aria-label]="'Supprimer ' + p.distance">
                <app-icon name="trash-2" [size]="15" />
              </button>
            </li>
          }
        </ul>
      }

      <!-- Allures dérivées du VDOT : ce que les ancres Pace800/1500/… utilisent -->
      @if (vdot()?.paces?.length) {
        <div class="derived">
          <span class="derived-lb">Allures dérivées</span>
          <div class="derived-grid">
            @for (p of vdot()!.paces; track p.distance) {
              <span class="derived-item">
                <span class="di-k">{{ p.distance }}</span>
                <span class="di-v metric">{{ p.paceLabel }}</span>
              </span>
            }
          </div>
        </div>
      }
    </section>
  `,
  styles: [`
    .records { margin-bottom: var(--sp-5); }
    .rec-head { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-3); flex-wrap: wrap; }
    .rec-head h2 { margin: 0 0 var(--sp-1); font-size: var(--text-lg); }
    .rec-head .field-hint { margin: 0; max-width: 60ch; }
    .vdot-badge { font-size: var(--text-sm); color: var(--ink-3); }
    .vdot-badge strong { color: var(--dari-teal); font-size: var(--text-lg); margin-left: var(--sp-1); }

    .rec-form { display: flex; flex-wrap: wrap; gap: var(--sp-2); align-items: center; margin: var(--sp-4) 0; }
    .rec-form .form-control { min-height: 40px; }
    .rf-time { max-width: 150px; }
    .rf-date { max-width: 170px; }

    .rec-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; }
    .rec-row { display: flex; align-items: center; gap: var(--sp-3); padding: var(--sp-2) var(--sp-3); border-radius: var(--radius-sm); background: var(--paper-sunk); }
    .rr-dist { font-weight: 700; min-width: 120px; }
    .rr-time { font-weight: 800; min-width: 84px; }
    .rr-date { margin-right: auto; }
    .icon-btn { border: none; background: none; cursor: pointer; color: var(--ink-3); display: inline-flex; padding: var(--sp-1); border-radius: var(--radius-sm); }
    .icon-btn.danger:hover { color: var(--danger); background: color-mix(in srgb, var(--danger) 12%, transparent); }

    .derived { margin-top: var(--sp-4); padding-top: var(--sp-3); border-top: 1px solid var(--hairline); }
    .derived-lb { font-size: var(--text-xs); text-transform: uppercase; letter-spacing: 0.04em; color: var(--ink-3); font-weight: 700; }
    .derived-grid { display: flex; flex-wrap: wrap; gap: var(--sp-2); margin-top: var(--sp-2); }
    .derived-item { display: inline-flex; align-items: baseline; gap: var(--sp-1); background: var(--paper-sunk); border-radius: var(--radius-full); padding: 2px var(--sp-2); }
    .di-k { font-size: var(--text-xs); color: var(--ink-3); }
    .di-v { font-weight: 700; font-size: var(--text-sm); }
    .empty-hint { color: var(--ink-3); font-size: var(--text-sm); }
  `],
})
export class AthleteRecordsPanelComponent implements OnInit {
  readonly athleteId = input.required<string>();

  private readonly physio = inject(PhysioService);
  private readonly toast = inject(ToastService);

  readonly distances = DISTANCES;
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly performances = signal<Performance[]>([]);
  readonly vdot = signal<Vdot | null>(null);

  draft = { distance: 'D10KM', time: '', dateSet: '' };

  /** Records triés du plus court au plus long, puis par date décroissante. */
  readonly records = computed(() => {
    const order = DISTANCES.map((d) => d.value);
    return [...this.performances()].sort((a, b) => {
      const da = order.indexOf(a.distanceCode || a.distance);
      const db = order.indexOf(b.distanceCode || b.distance);
      if (da !== db) return da - db;
      return (b.dateSet ?? '').localeCompare(a.dateSet ?? '');
    });
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.physio.performances(this.athleteId()).subscribe({
      next: (p) => { this.performances.set(p); this.loading.set(false); },
      error: () => { this.performances.set([]); this.loading.set(false); },
    });
    this.physio.vdot(this.athleteId()).subscribe({ next: (v) => this.vdot.set(v) });
  }

  label(code: string): string {
    return DISTANCES.find((d) => d.value === code)?.label ?? code;
  }

  /** « mm:ss » ou « h:mm:ss » → secondes ; null si invalide. */
  private parseTime(input: string): number | null {
    const parts = input.trim().split(':').map(Number);
    if (!parts.length || parts.some((n) => Number.isNaN(n) || n < 0)) return null;
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    return null;
  }

  hms(sec: number): string {
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    const mm = String(m).padStart(h ? 2 : 1, '0');
    return `${h ? h + ':' : ''}${mm}:${String(s).padStart(2, '0')}`;
  }

  add(): void {
    const seconds = this.parseTime(this.draft.time);
    if (!this.draft.distance || seconds == null || seconds <= 0) {
      this.toast.warning('Renseigne une distance et un temps (mm:ss ou h:mm:ss).');
      return;
    }
    this.busy.set(true);
    this.physio.addPerformance(this.athleteId(), {
      distance: this.draft.distance,
      timeSeconds: seconds,
      dateSet: this.draft.dateSet || null,
    }).subscribe({
      next: () => {
        this.toast.success('Record enregistré — VDOT et allures recalculés.');
        this.draft = { distance: this.draft.distance, time: '', dateSet: '' };
        this.busy.set(false);
        this.load();
      },
      error: () => { this.busy.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  remove(p: Performance): void {
    this.physio.deletePerformance(this.athleteId(), p.id).subscribe({
      next: () => { this.toast.info('Record retiré — VDOT recalculé.'); this.load(); },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }
}
