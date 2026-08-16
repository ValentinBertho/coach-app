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
import { SidePanelComponent } from '../../shared/components/side-panel/side-panel.component';
import { DecisionService } from '../../core/services/decision.service';
import { PlanPreview, PlanWeek } from '../../core/models/decision.model';

@Component({
  selector: 'app-race-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, FormsModule, RouterLink, DatePipe, SidePanelComponent],
  templateUrl: './race-list.component.html',
  styleUrl: './race-list.component.scss',
})
export class RaceListComponent implements OnInit {
  readonly athleteId = input.required<string>();
  private readonly raceService = inject(RaceService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly decisions = inject(DecisionService);

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

  // --- Le plan qui part de la course ---------------------------------------
  //
  // Deux gestes, jamais un seul : l'aperçu ne touche à rien, la pose est explicite. Poser douze
  // semaines de séances dans le calendrier de quelqu'un est trop lourd pour être un effet de bord.

  readonly planOpen = signal(false);
  readonly planRace = signal<RaceObjective | null>(null);
  readonly planPreview = signal<PlanPreview | null>(null);
  readonly planBusy = signal(false);
  readonly peakFactor = signal(1.3);
  readonly taperWeeks = signal(2);

  openPlan(race: RaceObjective): void {
    this.planRace.set(race);
    this.planPreview.set(null);
    this.planOpen.set(true);
    this.refreshPreview();
  }

  setPeak(value: number): void {
    this.peakFactor.set(value);
    this.refreshPreview();
  }

  setTaper(value: number): void {
    this.taperWeeks.set(value);
    this.refreshPreview();
  }

  private refreshPreview(): void {
    const race = this.planRace();
    if (!race) return;
    this.decisions
      .planPreview(this.athleteId(), race.id, {
        peakFactor: this.peakFactor(),
        taperWeeks: this.taperWeeks(),
      })
      .subscribe({
        next: (p) => this.planPreview.set(p),
        error: () => {
          this.planPreview.set(null);
          this.toast.error('Aperçu impossible pour cette course.');
        },
      });
  }

  applyPlan(): void {
    const race = this.planRace();
    if (!race || this.planBusy()) return;
    this.planBusy.set(true);
    this.decisions
      .planApply(this.athleteId(), race.id, {
        peakFactor: this.peakFactor(),
        taperWeeks: this.taperWeeks(),
      })
      .subscribe({
        next: (p) => {
          this.planBusy.set(false);
          this.planOpen.set(false);
          this.toast.success(
            `${p.createdSessions} séance(s) posée(s)` +
              (p.skippedWeeks ? `, ${p.skippedWeeks} semaine(s) laissée(s) vide(s)` : ''));
        },
        error: () => {
          this.planBusy.set(false);
          this.toast.error('La semaine de référence est vide : construis-la d\'abord.');
        },
      });
  }

  /** Barre proportionnelle au volume, plafonnée pour rester lisible sur un panneau étroit. */
  barWidth(w: PlanWeek): number {
    return w.skipped ? 0 : Math.min(100, Math.round((w.volumeFactor / 1.6) * 100));
  }

  factorLabel(w: PlanWeek): string {
    const pct = Math.round((w.volumeFactor - 1) * 100);
    return pct === 0 ? 'ref' : (pct > 0 ? '+' : '') + pct + ' %';
  }

  // --- Le chrono réalisé ----------------------------------------------------

  readonly resultOpen = signal(false);
  readonly resultBusy = signal(false);
  resultTime = '';

  openResult(race: RaceObjective): void {
    this.planRace.set(race);
    this.resultTime = '';
    this.resultOpen.set(true);
  }

  saveResult(): void {
    const race = this.planRace();
    const seconds = this.parseTime(this.resultTime);
    if (!race || !seconds) {
      this.toast.warning('Indique un chrono au format hh:mm:ss.');
      return;
    }
    this.resultBusy.set(true);
    this.decisions.recordRaceResult(this.athleteId(), race.id, seconds).subscribe({
      next: () => {
        this.resultBusy.set(false);
        this.resultOpen.set(false);
        this.toast.success('Chrono enregistré — VDOT, allures et zones mis à jour.');
        this.load();
      },
      error: () => {
        this.resultBusy.set(false);
        this.toast.error('Enregistrement impossible.');
      },
    });
  }

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
