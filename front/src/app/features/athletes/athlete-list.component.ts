import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { AthleteSummary } from '../../core/models/athlete.model';
import { TrainingGroup } from '../../core/models/training-group.model';
import { AthleteService } from '../../core/services/athlete.service';
import { TrainingGroupService } from '../../core/services/training-group.service';
import { AthleteForm, CoachDashboardService, FormStatus } from '../../core/services/coach-dashboard.service';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { FormPillComponent } from '../../shared/components/form-pill/form-pill.component';
import type { FormLevel } from '../../shared/components/physiology';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

type ListState = 'loading' | 'ready' | 'error';

const LEVEL_OF: Record<FormStatus, FormLevel> = { GREEN: 'green', ORANGE: 'orange', RED: 'red' };

@Component({
  selector: 'app-athlete-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, RouterLink, FormsModule, PaginatorComponent, FormPillComponent],
  templateUrl: './athlete-list.component.html',
  styleUrl: './athletes.scss',
})
export class AthleteListComponent implements OnInit {
  private readonly athleteService = inject(AthleteService);
  private readonly groupService = inject(TrainingGroupService);
  private readonly dashboardService = inject(CoachDashboardService);
  private readonly searchInput$ = new Subject<void>();

  readonly state = signal<ListState>('loading');
  readonly athletes = signal<AthleteSummary[]>([]);
  readonly groups = signal<TrainingGroup[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  search = '';
  filterGroup = '';

  /**
   * État de forme par athlète, chargé une fois depuis le tableau de bord (même source que la
   * jauge du cockpit : fatigue + douleur calculés côté serveur). Une carte sans retour connu
   * n'affiche simplement pas de pastille.
   */
  private readonly formByAthlete = signal<Map<string, AthleteForm>>(new Map());

  formOf(athleteId: string): { level: FormLevel; fatigue: number | null; pain: number | null; stale: boolean } | null {
    const f = this.formByAthlete().get(athleteId);
    if (!f) return null;
    return {
      level: LEVEL_OF[f.formStatus],
      fatigue: f.fatigue,
      pain: f.pain,
      stale: !f.lastFeedbackDate,
    };
  }

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
    this.groupService.list().subscribe((g) => this.groups.set(g));
    this.dashboardService.form('all').subscribe({
      next: (f) => this.formByAthlete.set(
        new Map([...f.routeAthletes, ...f.trailAthletes].map((a) => [a.id, a]))),
      error: () => { /* pastille absente plutôt que liste bloquée */ },
    });
    this.load();
  }

  onSearchChange(): void {
    this.searchInput$.next();
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.athleteService.list({ q: this.search || undefined, groupId: this.filterGroup || undefined, page: this.page() }).subscribe({
      next: (page) => {
        this.athletes.set(page.content);
        this.totalPages.set(page.totalPages);
        this.state.set('ready');
      },
      error: () => this.state.set('error'),
    });
  }

  statusLabel(status: string): string {
    return { ACTIVE: 'Actif', PAUSED: 'En pause', ARCHIVED: 'Archivé' }[status] ?? status;
  }

  statusClass(status: string): string {
    return (
      { ACTIVE: 'badge-success', PAUSED: 'badge-warning', ARCHIVED: 'badge-neutral' }[status] ??
      'badge-neutral'
    );
  }
}
