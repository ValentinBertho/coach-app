import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import {
  CoachDashboard,
  CoachDashboardService,
  CoachFormDashboard,
} from '../../core/services/coach-dashboard.service';

/** Tableau de bord coach : KPI réels + état de forme Route/Trail + prochaines courses. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly dashboardService = inject(CoachDashboardService);

  readonly user = this.auth.currentUser;
  readonly data = signal<CoachDashboard | null>(null);
  readonly form = signal<CoachFormDashboard | null>(null);

  ngOnInit(): void {
    this.dashboardService.get().subscribe((d) => this.data.set(d));
    this.dashboardService.form().subscribe((f) => this.form.set(f));
  }

  countdown(days: number): string {
    return days > 0 ? `J-${days}` : days === 0 ? 'Jour J' : 'passée';
  }
}
