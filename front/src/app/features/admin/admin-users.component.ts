import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime } from 'rxjs';
import { AdminUser, ClubAdmin, ROLE_LABELS, UserStatus } from '../../core/models/admin.model';
import { UserRole } from '../../core/models/user.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/services/auth.service';
import { Router } from '@angular/router';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, FormsModule, PaginatorComponent],
  templateUrl: './admin-users.component.html',
})
export class AdminUsersComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
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

  /** Session d'impersonation en cours d'ouverture (évite le double clic). */
  readonly impersonating = signal(false);

  /**
   * Un compte d'administration ne s'emprunte pas : l'impersonation sert à voir l'application comme
   * un coach ou un athlète, pas à agir sous l'identité d'un pair. Le serveur le refuse aussi — ce
   * test n'est là que pour ne pas proposer un bouton qui échouera.
   */
  canImpersonate(u: AdminUser): boolean {
    return u.role !== 'PLATFORM_ADMIN';
  }

  async impersonate(u: AdminUser): Promise<void> {
    const ok = await this.confirm.ask({
      title: `Voir l'application en tant que ${u.fullName || u.email} ?`,
      message: 'Ta session reste ouverte et te sera rendue. Tout ce que tu feras pendant ce temps '
        + "sera enregistré au nom de cette personne, sans distinction : c'est un outil de lecture.",
      confirmLabel: 'Voir en tant que',
    });
    if (!ok || this.impersonating()) { return; }

    this.impersonating.set(true);
    this.admin.impersonate(u.id).subscribe({
      next: (res) => {
        this.impersonating.set(false);
        this.auth.startImpersonation(res);
        void this.router.navigateByUrl(this.auth.homeRoute());
      },
      error: () => {
        this.impersonating.set(false);
        // Le message du serveur est déjà affiché par l'intercepteur : il nomme la raison du refus.
      },
    });
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
