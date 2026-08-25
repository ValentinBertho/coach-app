import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { CLUB_STATUS_LABELS, ClubAdmin, ClubStatus, clubStatusBadge } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Liste des clubs.
 *
 * <p>L'édition en ligne a été retirée : renommer ou suspendre un club se fait depuis sa fiche,
 * où l'on voit d'abord ce qu'il contient. Suspendre un tenant sans savoir combien de coachs et
 * d'athlètes il porte est un geste qu'on ne devrait jamais faire d'un tableau.</p>
 */
@Component({
  selector: 'app-admin-clubs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SkeletonComponent, FormsModule, PaginatorComponent, RouterLink, IconComponent,
    EmptyStateComponent, DatePipe,
  ],
  templateUrl: './admin-clubs.component.html',
  styleUrl: './admin-list.scss',
})
export class AdminClubsComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly searchInput$ = new Subject<void>();

  readonly clubs = signal<ClubAdmin[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly total = signal(0);
  readonly creating = signal(false);
  readonly saving = signal(false);

  readonly statuses: ClubStatus[] = ['ACTIVE', 'SUSPENDED'];
  readonly statusLabels = CLUB_STATUS_LABELS;
  readonly statusBadge = clubStatusBadge;

  search = '';
  filterStatus: ClubStatus | '' = '';
  newName = '';

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
    const qp = this.route.snapshot.queryParamMap;
    this.search = qp.get('q') ?? '';
    this.filterStatus = (qp.get('status') as ClubStatus) ?? '';
    this.load();
  }

  onSearchChange(): void {
    this.searchInput$.next();
  }

  onFilterChange(): void {
    this.page.set(0);
    this.load();
  }

  resetFilters(): void {
    this.search = '';
    this.filterStatus = '';
    this.onFilterChange();
  }

  get hasFilters(): boolean {
    return !!(this.search || this.filterStatus);
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin.clubs(this.search || undefined, this.page(), this.filterStatus || undefined).subscribe({
      next: (p) => {
        this.clubs.set(p.content);
        this.totalPages.set(p.totalPages);
        this.total.set(p.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  create(): void {
    if (!this.newName.trim()) return;
    this.saving.set(true);
    this.admin.createClub({ name: this.newName.trim() }).subscribe({
      next: (c) => {
        this.toast.success('Club créé.');
        this.newName = '';
        this.creating.set(false);
        this.saving.set(false);
        void this.router.navigate(['/admin/clubs', c.id]);
      },
      error: () => this.saving.set(false),
    });
  }
}
