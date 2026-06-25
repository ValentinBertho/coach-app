import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { RaceObjective } from '../../core/models/race.model';
import { ConfirmService } from '../../core/services/confirm.service';
import { RaceService } from '../../core/services/race.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-race-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './race-list.component.html',
  styleUrl: './race-list.component.scss',
})
export class RaceListComponent implements OnInit {
  readonly athleteId = input.required<string>();
  private readonly raceService = inject(RaceService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly races = signal<RaceObjective[]>([]);
  readonly loading = signal(true);
  draft = { name: '', raceDate: '', distanceM: null as number | null, targetTimeS: null as number | null, priority: 'B' as 'A' | 'B' | 'C' };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.raceService.list(this.athleteId()).subscribe({
      next: (r) => { this.races.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  create(): void {
    if (!this.draft.name || !this.draft.raceDate) {
      this.toast.warning('Nom et date requis.');
      return;
    }
    this.raceService.create(this.athleteId(), { ...this.draft }).subscribe(() => {
      this.toast.success('Objectif ajouté 🎯');
      this.draft = { name: '', raceDate: '', distanceM: null, targetTimeS: null, priority: 'B' };
      this.load();
    });
  }

  async remove(r: RaceObjective): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer l’objectif', message: `Supprimer « ${r.name} » ?`, confirmLabel: 'Supprimer', danger: true,
    });
    if (!ok) return;
    this.raceService.delete(this.athleteId(), r.id).subscribe(() => { this.toast.success('Supprimé.'); this.load(); });
  }

  countdown(r: RaceObjective): string {
    return r.daysUntil > 0 ? `J-${r.daysUntil}` : r.daysUntil === 0 ? "Jour J" : 'passée';
  }

  priorityClass(p: string): string {
    return { A: 'badge-danger', B: 'badge-info', C: 'badge-neutral' }[p] ?? 'badge-neutral';
  }
  priorityLabel(p: string): string {
    return { A: 'A — majeur', B: 'B — intermédiaire', C: 'C — préparation' }[p] ?? p;
  }

  statusLabel(s: string): string {
    return { UPCOMING: 'À venir', DONE: 'Courue', CANCELLED: 'Annulée' }[s] ?? s;
  }

  /** Chrono visé formaté h:mm:ss ou m:ss. */
  targetTime(seconds: number | null): string | null {
    if (seconds == null) return null;
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.round(seconds % 60);
    const mm = m.toString().padStart(2, '0');
    const ss = s.toString().padStart(2, '0');
    return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`;
  }
}
