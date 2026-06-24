import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Athlete, AthleteLevel, AthleteStatus, Ref } from '../../core/models/athlete.model';
import { TrainingPlan } from '../../core/models/training-plan.model';
import { StravaStatus } from '../../core/models/strava.model';
import { Unavailability, UnavailabilityReason } from '../../core/models/unavailability.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ToastService } from '../../core/services/toast.service';
import { PhysioPanelComponent } from './physio-panel.component';

const STATUS_LABELS: Record<AthleteStatus, string> = { ACTIVE: 'Actif', PAUSED: 'En pause', ARCHIVED: 'Archivé' };
const STATUS_BADGES: Record<AthleteStatus, string> = { ACTIVE: 'badge-success', PAUSED: 'badge-warning', ARCHIVED: 'badge-neutral' };
const LEVEL_LABELS: Record<AthleteLevel, string> = { BEGINNER: 'Débutant', INTERMEDIATE: 'Intermédiaire', ADVANCED: 'Avancé', ELITE: 'Élite' };

@Component({
  selector: 'app-athlete-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, FormsModule, DatePipe, PhysioPanelComponent],
  templateUrl: './athlete-detail.component.html',
  styleUrl: './athletes.scss',
})
export class AthleteDetailComponent implements OnInit {
  readonly id = input.required<string>();

  private readonly athleteService = inject(AthleteService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly athlete = signal<Athlete | null>(null);
  readonly loading = signal(true);
  readonly inviteUrl = signal<string | null>(null);

  readonly statusLabels = STATUS_LABELS;
  readonly statusBadges = STATUS_BADGES;
  readonly levelLabels = LEVEL_LABELS;

  /** Multi-tenant : athlète privé (hors club) vs rattaché à un ou plusieurs clubs. */
  readonly isPrivate = computed(() => (this.athlete()?.clubs ?? []).length === 0);

  readonly assignableCoaches = signal<Ref[]>([]);
  readonly plans = signal<TrainingPlan[]>([]);

  // Indisponibilités
  readonly unavailabilities = signal<Unavailability[]>([]);
  readonly showUnavailForm = signal(false);
  newUnavail = { startDate: '', endDate: '', reason: 'INJURY' as UnavailabilityReason, notes: '' };
  readonly reasons: { value: UnavailabilityReason; label: string }[] = [
    { value: 'INJURY', label: 'Blessure' },
    { value: 'ILLNESS', label: 'Maladie' },
    { value: 'VACATION', label: 'Vacances' },
    { value: 'PERSONAL', label: 'Personnel' },
    { value: 'OTHER', label: 'Autre' },
  ];

  // Strava
  readonly strava = signal<StravaStatus | null>(null);

  ngOnInit(): void {
    this.athleteService.stravaStatus(this.id()).subscribe((s) => this.strava.set(s));
    this.athleteService.get(this.id()).subscribe({
      next: (a) => {
        this.athlete.set(a);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.router.navigate(['/app/athletes']);
      },
    });
    this.athleteService.assignableCoaches().subscribe({
      next: (coaches) => this.assignableCoaches.set(coaches),
    });
    this.athleteService.plans(this.id()).subscribe({
      next: (plans) => this.plans.set(plans),
    });
    this.loadUnavailabilities();
  }

  loadUnavailabilities(): void {
    this.athleteService.listUnavailabilities(this.id()).subscribe((u) => this.unavailabilities.set(u));
  }

  addUnavailability(): void {
    if (!this.newUnavail.startDate || !this.newUnavail.endDate) {
      this.toast.warning('Renseigne les dates de début et de fin.');
      return;
    }
    this.athleteService.createUnavailability(this.id(), {
      startDate: this.newUnavail.startDate,
      endDate: this.newUnavail.endDate,
      reason: this.newUnavail.reason,
      notes: this.newUnavail.notes || null,
    }).subscribe(() => {
      this.toast.success('Indisponibilité ajoutée ✅');
      this.newUnavail = { startDate: '', endDate: '', reason: 'INJURY', notes: '' };
      this.showUnavailForm.set(false);
      this.loadUnavailabilities();
    });
  }

  removeUnavailability(unavailabilityId: string): void {
    this.athleteService.deleteUnavailability(this.id(), unavailabilityId).subscribe(() => {
      this.toast.info('Indisponibilité supprimée.');
      this.loadUnavailabilities();
    });
  }

  reasonLabel(reason: UnavailabilityReason): string {
    return this.reasons.find((r) => r.value === reason)?.label ?? reason;
  }

  // --- Strava ---
  connectStrava(): void {
    this.athleteService.stravaAuthorizeUrl(this.id()).subscribe({
      next: ({ url }) => { window.location.href = url; },
      error: () => this.toast.error('Intégration Strava non configurée sur ce serveur.'),
    });
  }

  importStrava(): void {
    this.toast.info('Import Strava en cours…');
    this.athleteService.stravaImport(this.id()).subscribe({
      next: ({ imported }) => {
        this.toast.success(`${imported} activité(s) importée(s) ✅`);
        this.athleteService.stravaStatus(this.id()).subscribe((s) => this.strava.set(s));
      },
      error: () => this.toast.error('Import impossible.'),
    });
  }

  disconnectStrava(): void {
    this.athleteService.stravaDisconnect(this.id()).subscribe(() => {
      this.toast.info('Strava déconnecté.');
      this.athleteService.stravaStatus(this.id()).subscribe((s) => this.strava.set(s));
    });
  }

  /** Coachs du club non encore rattachés à cet athlète. */
  availableCoaches(): Ref[] {
    const current = new Set((this.athlete()?.coaches ?? []).map((c) => c.id));
    return this.assignableCoaches().filter((c) => !current.has(c.id));
  }

  assignCoach(event: Event): void {
    const select = event.target as HTMLSelectElement;
    const coachId = select.value;
    if (!coachId) return;
    select.value = '';
    this.athleteService.assignCoach(this.id(), coachId).subscribe({
      next: (a) => {
        this.athlete.set(a);
        this.toast.success('Coach rattaché ✅');
      },
    });
  }

  removeCoach(coachId: string): void {
    this.athleteService.removeCoach(this.id(), coachId).subscribe({
      next: (a) => {
        this.athlete.set(a);
        this.toast.info('Coach retiré.');
      },
    });
  }

  invite(): void {
    this.athleteService.invite(this.id()).subscribe({
      next: (res) => {
        this.inviteUrl.set(res.inviteUrl);
        this.toast.success("Lien d'invitation généré ✅");
      },
    });
  }

  copyInvite(): void {
    const url = this.inviteUrl();
    if (url) {
      navigator.clipboard?.writeText(url);
      this.toast.info('Lien copié dans le presse-papier.');
    }
  }

  archive(): void {
    this.athleteService.archive(this.id()).subscribe({
      next: () => {
        this.toast.success('Athlète archivé.');
        this.router.navigate(['/app/athletes']);
      },
    });
  }

  /** Télécharge le programme PDF des 4 prochaines semaines. */
  exportProgram(): void {
    const today = new Date();
    const from = today.toISOString().slice(0, 10);
    const to = new Date(today.getTime() + 28 * 86400000).toISOString().slice(0, 10);
    this.athleteService.exportProgram(this.id(), from, to).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'programme.pdf';
        a.click();
        URL.revokeObjectURL(url);
        this.toast.success('Programme exporté (PDF) ✅');
      },
      error: () => this.toast.error('Export impossible.'),
    });
  }
}
