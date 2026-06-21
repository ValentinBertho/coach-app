import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime } from 'rxjs';
import { AdminUser, ClubAdmin, ROLE_LABELS, UserStatus } from '../../core/models/admin.model';
import { UserRole } from '../../core/models/user.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, PaginatorComponent],
  templateUrl: './admin-users.component.html',
})
export class AdminUsersComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly searchInput$ = new Subject<void>();

  readonly roleLabels = ROLE_LABELS;
  readonly roles: UserRole[] = ['PLATFORM_ADMIN', 'HEAD_COACH', 'COACH', 'ATHLETE'];
  readonly statuses: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'INVITED'];

  readonly users = signal<AdminUser[]>([]);
  readonly clubs = signal<ClubAdmin[]>([]);
  readonly loading = signal(true);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  search = '';
  filterRole: UserRole | '' = '';

  draft = { email: '', password: '', fullName: '', role: 'COACH' as UserRole, clubId: '' };

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
    this.admin.users({ role: this.filterRole || undefined, q: this.search || undefined, page: this.page() }).subscribe({
      next: (p) => {
        this.users.set(p.content);
        this.totalPages.set(p.totalPages);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  needsClub(role: UserRole): boolean {
    return role === 'HEAD_COACH' || role === 'COACH';
  }

  create(): void {
    const d = this.draft;
    if (!d.email || !d.password || !d.fullName) {
      this.toast.warning('Email, mot de passe et nom sont requis.');
      return;
    }
    this.admin
      .createUser({
        email: d.email,
        password: d.password,
        fullName: d.fullName,
        role: d.role,
        clubId: this.needsClub(d.role) ? d.clubId || null : null,
      })
      .subscribe(() => {
        this.toast.success('Utilisateur créé.');
        this.draft = { email: '', password: '', fullName: '', role: 'COACH', clubId: '' };
        this.load();
      });
  }

  save(u: AdminUser): void {
    this.admin
      .updateUser(u.id, { fullName: u.fullName, role: u.role, status: u.status, clubId: u.clubId })
      .subscribe(() => this.toast.success('Utilisateur mis à jour.'));
  }

  async remove(u: AdminUser): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer l’utilisateur',
      message: `Supprimer le compte ${u.email} ?`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    this.admin.deleteUser(u.id).subscribe(() => {
      this.toast.success('Utilisateur supprimé.');
      this.load();
    });
  }
}
