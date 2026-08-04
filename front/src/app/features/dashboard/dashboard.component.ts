import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import {
  AthleteForm,
  CoachAlert,
  CoachDashboard,
  CoachDashboardService,
  CoachFormDashboard,
  FormStatus,
} from '../../core/services/coach-dashboard.service';
import { ReadinessGaugeComponent, type FormLevel } from '../../shared/components/physiology';
import { MetricCardComponent, SegmentedControlComponent, type SegmentOption } from '../../shared/components/ui';
import { RevealDirective } from '../../shared/directives/reveal.directive';

type Scope = 'all' | 'mine' | 'private' | 'club';

const LEVEL_OF: Record<FormStatus, FormLevel> = {
  GREEN: 'green', ORANGE: 'orange', RED: 'red', STALE: 'stale',
};
/**
 * Criticité de tri. « Sans signal » se place entre le vert et l'orange : ce n'est pas une
 * alerte — on ne sait rien — mais ça mérite d'être vu avant les athlètes qui vont bien, parce
 * que le silence est précisément ce qu'un coach doit aller chercher.
 */
const SEVERITY: Record<FormStatus, number> = { RED: 3, ORANGE: 2, STALE: 1, GREEN: 0 };

/**
 * Cockpit coach (blueprint §7.2) : zone « à surveiller » dominante, KPI rail,
 * club overview, prochaines courses. Câblé sur CoachDashboardService (données
 * réelles, scope club). L'état de forme est calculé backend (formStatus).
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink, DatePipe, ReadinessGaugeComponent, MetricCardComponent, SegmentedControlComponent, RevealDirective],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly dashboardService = inject(CoachDashboardService);

  readonly user = this.auth.currentUser;
  readonly data = signal<CoachDashboard | null>(null);
  readonly form = signal<CoachFormDashboard | null>(null);
  readonly alerts = signal<CoachAlert[]>([]);
  readonly loading = signal(true);

  /**
   * Première ouverture : aucun athlète actif. Le cockpit est alors remplacé par les étapes de
   * démarrage — piloter zéro athlète n'a pas de sens, et « Tout le monde est en forme » sur une
   * liste vide est trompeur.
   */
  readonly firstRun = computed(() => this.data()?.activeAthletes === 0);

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
    const c = { GREEN: 0, ORANGE: 0, RED: 0, STALE: 0 } as Record<FormStatus, number>;
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
    this.load();
  }

  /** Tout le cockpit suit le périmètre : KPI, jauge de forme et alertes. */
  load(): void {
    this.dashboardService.get(this.scope()).subscribe({
      next: (d) => this.data.set(d),
      complete: () => this.loading.set(false),
    });
    this.dashboardService.form(this.scope()).subscribe((f) => this.form.set(f));
    this.dashboardService.alerts(this.scope()).subscribe({
      next: (a) => this.alerts.set(a),
      error: () => this.alerts.set([]),
    });
  }

  setScope(value: string): void {
    this.scope.set(value as Scope);
    this.load();
  }

  level(status: FormStatus): FormLevel { return LEVEL_OF[status]; }

  /** Note contextuelle pour la jauge (dernier retour). */
  /**
   * Ancienneté du signal, toujours affichée.
   *
   * <p>Elle ne l'était pas : une pastille rouge datant d'une compétition de novembre était
   * indiscernable d'une douleur signalée ce matin. Deux informations très différentes, la même
   * couleur, et un tri qui remontait la plus ancienne en premier.</p>
   */
  feedbackNote(a: AthleteForm): string {
    if (!a.lastFeedbackDate) return 'Aucun retour';
    const days = Math.floor(
      (Date.parse(this.todayIso()) - Date.parse(a.lastFeedbackDate)) / 86_400_000,
    );
    if (days <= 0) return "Retour d'aujourd'hui";
    if (days === 1) return "Retour d'hier";
    return `Retour d'il y a ${days} jours`;
  }

  private todayIso(): string {
    const n = new Date();
    return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`;
  }

  countdown(days: number): string {
    return days > 0 ? `J-${days}` : days === 0 ? 'Jour J' : 'passée';
  }
}
