import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import {
  AthleteForm,
  CoachDashboard,
  CoachDashboardService,
  CoachFormDashboard,
  FormStatus,
} from '../../core/services/coach-dashboard.service';
import { ReadinessGaugeComponent, type FormLevel } from '../../shared/components/physiology';
import { MetricCardComponent, SegmentedControlComponent, type SegmentOption } from '../../shared/components/ui';

type Scope = 'all' | 'mine' | 'private' | 'club';

const LEVEL_OF: Record<FormStatus, FormLevel> = { GREEN: 'green', ORANGE: 'orange', RED: 'red' };
const SEVERITY: Record<FormStatus, number> = { RED: 2, ORANGE: 1, GREEN: 0 };

/**
 * Cockpit coach (blueprint §7.2) : zone « à surveiller » dominante, KPI rail,
 * club overview, prochaines courses. Câblé sur CoachDashboardService (données
 * réelles, scope club). L'état de forme est calculé backend (formStatus).
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink, ReadinessGaugeComponent, MetricCardComponent, SegmentedControlComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly dashboardService = inject(CoachDashboardService);

  readonly user = this.auth.currentUser;
  readonly data = signal<CoachDashboard | null>(null);
  readonly form = signal<CoachFormDashboard | null>(null);
  readonly loading = signal(true);

  private readonly allAthletes = computed<AthleteForm[]>(() => {
    const f = this.form();
    return f ? [...f.routeAthletes, ...f.trailAthletes] : [];
  });

  /** Athlètes nécessitant une attention : forme non verte ou douleur ≥ 4, triés par criticité. */
  readonly attention = computed<AthleteForm[]>(() =>
    this.allAthletes()
      .filter((a) => a.formStatus !== 'GREEN' || (a.pain ?? 0) >= 4)
      .sort((a, b) =>
        SEVERITY[b.formStatus] - SEVERITY[a.formStatus] ||
        (b.pain ?? 0) - (a.pain ?? 0) ||
        (b.fatigue ?? 0) - (a.fatigue ?? 0),
      ),
  );

  /** Répartition de la forme sur tout le périmètre. */
  readonly formCounts = computed(() => {
    const c = { GREEN: 0, ORANGE: 0, RED: 0 } as Record<FormStatus, number>;
    for (const a of this.allAthletes()) c[a.formStatus]++;
    return c;
  });

  // --- Périmètre (scope) ---
  readonly scope = signal<Scope>('all');
  readonly scopeOptions: SegmentOption[] = [
    { value: 'all', label: 'Tout le club' },
    { value: 'mine', label: 'Mes athlètes' },
    { value: 'private', label: 'Privés' },
    { value: 'club', label: 'Club' },
  ];

  ngOnInit(): void {
    this.dashboardService.get().subscribe({
      next: (d) => this.data.set(d),
      complete: () => this.loading.set(false),
    });
    this.loadForm();
  }

  loadForm(): void {
    this.dashboardService.form(this.scope()).subscribe((f) => this.form.set(f));
  }

  setScope(value: string): void {
    this.scope.set(value as Scope);
    this.loadForm();
  }

  level(status: FormStatus): FormLevel { return LEVEL_OF[status]; }

  /** Note contextuelle pour la jauge (dernier retour). */
  feedbackNote(a: AthleteForm): string {
    return a.lastFeedbackDate ? '' : 'Aucun retour récent';
  }

  countdown(days: number): string {
    return days > 0 ? `J-${days}` : days === 0 ? 'Jour J' : 'passée';
  }
}
