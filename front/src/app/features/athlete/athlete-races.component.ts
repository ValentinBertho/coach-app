import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { RaceObjective, RacePriority } from '../../core/models/race.model';

interface Draft {
  id: string | null;
  name: string;
  raceDate: string;
  km: number | null;
  targetTime: string; // hh:mm
  priority: RacePriority;
}

/**
 * Mes objectifs (athlète) — CRUD complet (CDC §10 : l'athlète gère ses courses cibles).
 * Câblé sur /me/races (GET/POST/PATCH/DELETE).
 */
@Component({
  selector: 'app-athlete-races',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, FormsModule, RouterLink, IconComponent],
  template: `
    <div class="races">
      <header class="races-top">
        <a routerLink="/athlete/progress" class="btn btn-ghost btn-sm">← Progrès</a>
        <h1 class="display-sm">Mes objectifs</h1>
        <p class="subtitle">Tes courses cibles (priorité A/B/C).</p>
      </header>

      <button type="button" class="btn btn-primary btn-sm" (click)="openNew()">
        <app-icon name="flag" [size]="15" /> Nouvel objectif
      </button>

      @if (editing()) {
        <form class="card race-form" (ngSubmit)="submit()">
          <label>Nom<input class="form-control" [(ngModel)]="draft.name" name="n" placeholder="Marathon de Paris" required /></label>
          <div class="rf-row">
            <label>Date<input type="date" class="form-control" [(ngModel)]="draft.raceDate" name="d" required /></label>
            <label>Priorité
              <select class="form-control" [(ngModel)]="draft.priority" name="p">
                <option value="A">A</option><option value="B">B</option><option value="C">C</option>
              </select>
            </label>
          </div>
          <div class="rf-row">
            <label>Distance (km)<input type="number" min="0" step="0.1" class="form-control" [(ngModel)]="draft.km" name="km" /></label>
            <label>Temps visé (hh:mm)<input class="form-control" [(ngModel)]="draft.targetTime" name="tt" placeholder="3:30" /></label>
          </div>
          <div class="rf-actions">
            <button type="button" class="btn btn-ghost btn-sm" (click)="cancel()">Annuler</button>
            <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy()">{{ busy() ? 'Enregistrement…' : 'Enregistrer' }}</button>
          </div>
        </form>
      }

      @if (loading()) {
        <div class="card"><div class="skeleton" style="height: 64px;"></div></div>
      } @else if (races().length === 0) {
        <div class="card empty"><p class="field-hint">Aucun objectif. Ajoute ta prochaine course.</p></div>
      } @else {
        @for (r of races(); track r.id) {
          <article class="card race">
            <div class="race-main">
              <span class="badge prio-{{ r.priority }}">{{ r.priority }}</span>
              <div class="race-id">
                <strong>{{ r.name }}</strong>
                <span class="field-hint">{{ r.raceDate | date: 'EEE d MMM yyyy' }}@if (r.daysUntil >= 0) { · J-{{ r.daysUntil }} }</span>
              </div>
            </div>
            <div class="race-actions">
              <button type="button" class="icon-btn" (click)="openEdit(r)" aria-label="Modifier"><app-icon name="pencil" [size]="15" /></button>
              <button type="button" class="icon-btn danger" (click)="remove(r)" aria-label="Supprimer"><app-icon name="trash-2" [size]="15" /></button>
            </div>
          </article>
        }
      }
    </div>
  `,
  styles: [`
    .races { max-width: 560px; margin-inline: auto; padding: var(--sp-4); display: flex; flex-direction: column; gap: var(--sp-3); }
    .races-top { display: flex; flex-direction: column; gap: var(--sp-1); align-items: flex-start; }
    .races-top h1 { margin: 0; }
    .subtitle { color: var(--ink-3); margin: 0; }
    .race-form { display: flex; flex-direction: column; gap: var(--sp-3); }
    .race-form label { display: flex; flex-direction: column; gap: 4px; font-size: var(--text-sm); color: var(--ink-2); font-weight: 600; }
    .rf-row { display: flex; gap: var(--sp-2); }
    .rf-row label { flex: 1; }
    .rf-actions { display: flex; justify-content: flex-end; gap: var(--sp-2); }
    .empty { text-align: center; }
    .race { display: flex; align-items: center; justify-content: space-between; gap: var(--sp-3); }
    .race-main { display: flex; align-items: center; gap: var(--sp-3); min-width: 0; }
    .race-id { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .race-id strong { color: var(--ink); }
    .race-actions { display: flex; gap: var(--sp-1); }
    .badge { font-weight: 700; min-width: 24px; text-align: center; border-radius: var(--radius-full); padding: 2px 8px; }
    .prio-A { background: var(--danger-bg); color: var(--danger-text); }
    .prio-B { background: var(--primary-wash); color: var(--primary); }
    .prio-C { background: var(--paper-sunk); color: var(--ink-3); }
    .icon-btn { background: transparent; border: none; cursor: pointer; color: var(--ink-2); padding: 6px; border-radius: var(--radius-md); }
    .icon-btn:hover { background: var(--paper-sunk); }
    .icon-btn.danger { color: var(--danger-text); }
  `],
})
export class AthleteRacesComponent implements OnInit {
  private readonly portal = inject(AthletePortalService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);

  readonly loading = signal(true);
  readonly races = signal<RaceObjective[]>([]);
  readonly editing = signal(false);
  readonly busy = signal(false);
  draft: Draft = this.blank();

  ngOnInit(): void { this.load(); }

  private blank(): Draft {
    return { id: null, name: '', raceDate: new Date().toISOString().slice(0, 10), km: null, targetTime: '', priority: 'B' };
  }

  private load(): void {
    this.loading.set(true);
    this.portal.races().subscribe({
      next: (r) => { this.races.set(r); this.loading.set(false); },
      error: () => { this.races.set([]); this.loading.set(false); },
    });
  }

  openNew(): void { this.draft = this.blank(); this.editing.set(true); }

  openEdit(r: RaceObjective): void {
    this.draft = {
      id: r.id, name: r.name, raceDate: r.raceDate,
      km: r.distanceM != null ? r.distanceM / 1000 : null,
      targetTime: r.targetTimeS != null ? this.fmtTime(r.targetTimeS) : '',
      priority: r.priority,
    };
    this.editing.set(true);
  }

  cancel(): void { this.editing.set(false); }

  submit(): void {
    if (!this.draft.name?.trim() || !this.draft.raceDate || this.busy()) { return; }
    this.busy.set(true);
    const req = {
      name: this.draft.name.trim(),
      raceDate: this.draft.raceDate,
      distanceM: this.draft.km != null ? Math.round(this.draft.km * 1000) : null,
      targetTimeS: this.parseTime(this.draft.targetTime),
      priority: this.draft.priority,
    };
    const call = this.draft.id
      ? this.portal.updateRace(this.draft.id, req)
      : this.portal.createRace(req);
    call.subscribe({
      next: () => { this.busy.set(false); this.editing.set(false); this.toast.success('Objectif enregistré'); this.load(); },
      error: () => { this.busy.set(false); this.toast.error('Enregistrement impossible.'); },
    });
  }

  async remove(r: RaceObjective): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer cet objectif ?', message: r.name, confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) { return; }
    this.portal.deleteRace(r.id).subscribe({
      next: () => { this.toast.success('Objectif supprimé'); this.load(); },
      error: () => this.toast.error('Suppression impossible.'),
    });
  }

  private fmtTime(s: number): string {
    const h = Math.floor(s / 3600);
    const m = Math.round((s % 3600) / 60);
    return `${h}:${m.toString().padStart(2, '0')}`;
  }

  private parseTime(t: string): number | null {
    if (!t?.trim()) { return null; }
    const parts = t.split(':').map((x) => Number(x));
    if (parts.some((n) => Number.isNaN(n))) { return null; }
    const [h, m = 0] = parts;
    return h * 3600 + m * 60;
  }
}
