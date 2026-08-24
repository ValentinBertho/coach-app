import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Athlete, Ref } from '../../core/models/athlete.model';
import { StravaStatus } from '../../core/models/strava.model';
import { Unavailability, UnavailabilityReason } from '../../core/models/unavailability.model';
import { AthleteService } from '../../core/services/athlete.service';
import { ToastService } from '../../core/services/toast.service';
import { PhysioPanelComponent } from './physio-panel.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { AthleteTimelineComponent } from '../../shared/components/athlete-timeline/athlete-timeline.component';
import { TrajectoryBannerComponent } from '../../shared/components/trajectory-banner/trajectory-banner.component';
import { Timeline, Trajectory } from '../../core/models/decision.model';
import { DecisionService } from '../../core/services/decision.service';
import { AthleteAccessPanelComponent } from '../../shared/components/athlete-access/athlete-access-panel.component';

/**
 * Onglet « Résumé » de la coquille athlète : profil physiologique, indisponibilités, antécédents,
 * rattachements. L'identité, les métriques de référence, les actions globales et la barre d'onglets
 * appartiennent à {@link AthleteShellComponent} et restent affichées quel que soit l'onglet.
 */
@Component({
  selector: 'app-athlete-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, IconComponent, FormsModule, DatePipe, PhysioPanelComponent,
    TrajectoryBannerComponent, AthleteTimelineComponent, AthleteAccessPanelComponent],
  templateUrl: './athlete-detail.component.html',
  styleUrl: './athletes.scss',
})
export class AthleteDetailComponent implements OnInit {
  readonly athleteId = input.required<string>();

  private readonly athleteService = inject(AthleteService);
  private readonly decisions = inject(DecisionService);

  /** L'écart à l'objectif, et sa cause. Nulle quand aucune course n'est déclarée. */
  readonly trajectory = signal<Trajectory | null>(null);
  /** Six mois de suivi rassemblés. */
  readonly timeline = signal<Timeline | null>(null);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly athlete = signal<Athlete | null>(null);
  readonly loading = signal(true);
  /** Volet « Rattachements & réglages » (coachs, clubs, Strava) — replié pour aérer la fiche. */
  readonly showAdmin = signal(false);

  /** Multi-tenant : athlète privé (hors club) vs rattaché à un ou plusieurs clubs. */
  readonly isPrivate = computed(() => (this.athlete()?.clubs ?? []).length === 0);

  readonly assignableCoaches = signal<Ref[]>([]);

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
    // Ni l'une ni l'autre n'est bloquante : une fiche doit s'afficher même sans objectif déclaré
    // et même si la frise échoue. Ce sont des lectures d'appoint, pas le contenu de l'écran.
    this.decisions.trajectory(this.athleteId()).subscribe({
      next: (t) => this.trajectory.set(t),
      error: () => this.trajectory.set(null),
    });
    this.decisions.timeline(this.athleteId()).subscribe({
      next: (t) => this.timeline.set(t),
      error: () => this.timeline.set(null),
    });
    this.athleteService.stravaStatus(this.athleteId()).subscribe((s) => this.strava.set(s));
    this.athleteService.get(this.athleteId()).subscribe({
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
    this.loadUnavailabilities();
  }

  loadUnavailabilities(): void {
    this.athleteService.listUnavailabilities(this.athleteId()).subscribe((u) => this.unavailabilities.set(u));
  }

  addUnavailability(): void {
    if (!this.newUnavail.startDate || !this.newUnavail.endDate) {
      this.toast.warning('Renseigne les dates de début et de fin.');
      return;
    }
    this.athleteService.createUnavailability(this.athleteId(), {
      startDate: this.newUnavail.startDate,
      endDate: this.newUnavail.endDate,
      reason: this.newUnavail.reason,
      notes: this.newUnavail.notes || null,
    }).subscribe(() => {
      this.toast.success('Indisponibilité ajoutée');
      this.newUnavail = { startDate: '', endDate: '', reason: 'INJURY', notes: '' };
      this.showUnavailForm.set(false);
      this.loadUnavailabilities();
    });
  }

  removeUnavailability(unavailabilityId: string): void {
    this.athleteService.deleteUnavailability(this.athleteId(), unavailabilityId).subscribe(() => {
      this.toast.info('Indisponibilité supprimée.');
      this.loadUnavailabilities();
    });
  }

  reasonLabel(reason: UnavailabilityReason): string {
    return this.reasons.find((r) => r.value === reason)?.label ?? reason;
  }

  // --- Strava ---
  connectStrava(): void {
    this.athleteService.stravaAuthorizeUrl(this.athleteId()).subscribe({
      next: ({ url }) => { window.location.href = url; },
      error: () => this.toast.error('Intégration Strava non configurée sur ce serveur.'),
    });
  }

  importStrava(): void {
    this.toast.info('Import Strava en cours…');
    this.athleteService.stravaImport(this.athleteId()).subscribe({
      next: ({ imported }) => {
        this.toast.success(`${imported} activité(s) importée(s)`);
        this.athleteService.stravaStatus(this.athleteId()).subscribe((s) => this.strava.set(s));
      },
      error: () => this.toast.error('Import impossible.'),
    });
  }

  disconnectStrava(): void {
    this.athleteService.stravaDisconnect(this.athleteId()).subscribe(() => {
      this.toast.info('Strava déconnecté.');
      this.athleteService.stravaStatus(this.athleteId()).subscribe((s) => this.strava.set(s));
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
    this.athleteService.assignCoach(this.athleteId(), coachId).subscribe({
      next: (a) => {
        this.athlete.set(a);
        this.toast.success('Coach rattaché');
      },
    });
  }

  removeCoach(coachId: string): void {
    this.athleteService.removeCoach(this.athleteId(), coachId).subscribe({
      next: (a) => {
        this.athlete.set(a);
        this.toast.info('Coach retiré.');
      },
    });
  }

  archive(): void {
    this.athleteService.archive(this.athleteId()).subscribe({
      next: () => {
        this.toast.success('Athlète archivé.');
        this.router.navigate(['/app/athletes']);
      },
    });
  }
}
