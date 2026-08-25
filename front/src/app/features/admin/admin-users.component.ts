import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import {
  AdminUser,
  ClubAdmin,
  ROLE_LABELS,
  USER_STATUS_LABELS,
  UserStatus,
  userStatusBadge,
} from '../../core/models/admin.model';
import { UserRole } from '../../core/models/user.model';
import { AdminService } from '../../core/services/admin.service';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Liste des comptes.
 *
 * <p><b>Ce qui a changé.</b> Chaque ligne était un mini-formulaire : trois champs éditables et un
 * bouton « Enregistrer » par compte, sans jamais montrer si l'adresse était vérifiée ni quand la
 * personne s'était connectée. On modifiait à l'aveugle, dans un tableau, ce qui est précisément
 * le geste qu'il faut faire en connaissance de cause. La liste <i>liste</i> désormais, et la
 * fiche (<code>/admin/users/:id</code>) porte les modifications et les actions de support.</p>
 *
 * <p>Le filtre par statut existait côté serveur depuis toujours et n'était jamais envoyé : la
 * liste déroulante affichée à l'écran ne filtrait rien.</p>
 */
@Component({
  selector: 'app-admin-users',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SkeletonComponent, FormsModule, PaginatorComponent, RouterLink, IconComponent,
    EmptyStateComponent, DatePipe,
  ],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-list.scss',
})
export class AdminUsersComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly searchInput$ = new Subject<void>();

  readonly roleLabels = ROLE_LABELS;
  readonly statusLabels = USER_STATUS_LABELS;
  readonly statusBadge = userStatusBadge;
  readonly roles: UserRole[] = ['PLATFORM_ADMIN', 'HEAD_COACH', 'COACH', 'ATHLETE'];
  readonly statuses: UserStatus[] = ['ACTIVE', 'INVITED', 'SUSPENDED'];

  readonly users = signal<AdminUser[]>([]);
  readonly clubs = signal<ClubAdmin[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly total = signal(0);
  readonly creating = signal(false);
  readonly saving = signal(false);

  search = '';
  filterRole: UserRole | '' = '';
  filterStatus: UserStatus | '' = '';
  filterClub = '';
  /** '' = indifférent, 'false' = comptes bloqués sur leur e-mail de confirmation. */
  filterVerified: '' | 'true' | 'false' = '';

  draft = { email: '', password: '', fullName: '', role: 'COACH' as UserRole, clubId: '' };

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.load();
    });

    // Les signaux du pilotage et la recherche globale arrivent ici avec un filtre déjà posé.
    const qp = this.route.snapshot.queryParamMap;
    this.search = qp.get('q') ?? '';
    this.filterRole = (qp.get('role') as UserRole) ?? '';
    this.filterStatus = (qp.get('status') as UserStatus) ?? '';
    this.filterVerified = (qp.get('verified') as '' | 'true' | 'false') ?? '';

    // Taille explicite : le sélecteur ne chargeait que la première page, si bien qu'au-delà de
    // 20 clubs le filtre devenait faux — les suivants étaient introuvables dans la liste.
    this.admin.clubs(undefined, 0, undefined, 200).subscribe({
      next: (p) => this.clubs.set(p.content),
      error: () => this.clubs.set([]),
    });
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
    this.filterRole = '';
    this.filterStatus = '';
    this.filterClub = '';
    this.filterVerified = '';
    this.onFilterChange();
  }

  get hasFilters(): boolean {
    return !!(this.search || this.filterRole || this.filterStatus || this.filterClub || this.filterVerified);
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin
      .users({
        role: this.filterRole || undefined,
        status: this.filterStatus || undefined,
        clubId: this.filterClub || undefined,
        verified: this.filterVerified === '' ? undefined : this.filterVerified === 'true',
        q: this.search || undefined,
        page: this.page(),
      })
      .subscribe({
        next: (p) => {
          this.users.set(p.content);
          this.totalPages.set(p.totalPages);
          this.total.set(p.totalElements);
          this.loading.set(false);
        },
        error: () => {
          // Un tableau vide se lit « aucun compte » : on distingue explicitement l'échec.
          this.failed.set(true);
          this.loading.set(false);
        },
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
    if (this.needsClub(d.role) && !d.clubId) {
      this.toast.warning('Un coach doit être rattaché à un club.');
      return;
    }
    this.saving.set(true);
    this.admin
      .createUser({
        email: d.email,
        password: d.password,
        fullName: d.fullName,
        role: d.role,
        clubId: this.needsClub(d.role) ? d.clubId : null,
      })
      .subscribe({
        next: (u) => {
          this.toast.success('Compte créé.');
          this.draft = { email: '', password: '', fullName: '', role: 'COACH', clubId: '' };
          this.creating.set(false);
          this.saving.set(false);
          void this.router.navigate(['/admin/users', u.id]);
        },
        error: () => this.saving.set(false),
      });
  }
}
