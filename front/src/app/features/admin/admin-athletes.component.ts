import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { AdminAthlete, ClubAdmin } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';

@Component({
  selector: 'app-admin-athletes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, PaginatorComponent],
  templateUrl: './admin-athletes.component.html',
})
export class AdminAthletesComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly searchInput$ = new Subject<void>();

  readonly athletes = signal<AdminAthlete[]>([]);
  readonly clubs = signal<ClubAdmin[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  search = '';
  filterClub = '';

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
    this.admin.clubs(undefined, 0).subscribe((p) => this.clubs.set(p.content));
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
    this.loading.set(true);
    this.admin.athletes({ clubId: this.filterClub || undefined, q: this.search || undefined, page: this.page() }).subscribe({
      next: (p) => {
        this.athletes.set(p.content);
        this.totalPages.set(p.totalPages);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  async remove(a: AdminAthlete): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer l’athlète',
      message: `Supprimer ${a.firstName} ${a.lastName} et ses données (séances, activités) ?`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    this.admin.deleteAthlete(a.id).subscribe(() => {
      this.toast.success('Athlète supprimé.');
      this.load();
    });
  }
}
