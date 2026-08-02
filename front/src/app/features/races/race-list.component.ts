import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { RaceObjective } from '../../core/models/race.model';
import { formatDuration, parseDuration } from '../../core/utils/duration';
import { ConfirmService } from '../../core/services/confirm.service';
import { RaceService } from '../../core/services/race.service';
import { ToastService } from '../../core/services/toast.service';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'app-race-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, FormsModule, RouterLink, DatePipe],
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
  /**
   * Saisie de l'objectif. La distance se donne en kilomètres et le chrono en hh:mm:ss : personne
   * ne pense un marathon en « 42195 m » ni un objectif en « 10800 s ».
   */
  draft = {
    name: '', raceDate: '',
    km: null as number | null,
    targetTime: '',
    priority: 'B' as 'A' | 'B' | 'C',
  };

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
    const targetTimeS = this.parseTime(this.draft.targetTime);
    if (this.draft.targetTime.trim() && targetTimeS == null) {
      this.toast.warning('Chrono visé invalide — attendu hh:mm:ss (ou mm:ss).');
      return;
    }
    this.raceService.create(this.athleteId(), {
      name: this.draft.name,
      raceDate: this.draft.raceDate,
      distanceM: this.draft.km != null ? Math.round(this.draft.km * 1000) : null,
      targetTimeS,
      priority: this.draft.priority,
    }).subscribe(() => {
      this.toast.success('Objectif ajouté');
      this.draft = { name: '', raceDate: '', km: null, targetTime: '', priority: 'B' };
      this.load();
    });
  }

  /** « hh:mm:ss », « mm:ss » ou un nombre de minutes → secondes ; null si inexploitable. */
  private parseTime(raw: string): number | null {
    return parseDuration(raw);
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
    return seconds == null ? null : formatDuration(seconds);
  }
}
