import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { AdminAuditAction, AdminAuditEntry, AuditTargetType } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Journal des actions d'administration.
 *
 * <p><b>Ce qu'il remplace.</b> Rien. Aucune trace en base ne disait qui avait supprimé un compte,
 * changé un rôle, suspendu un club ou ouvert une session au nom d'un utilisateur. La seule ligne
 * existante était un {@code WARN} applicatif pour l'impersonation — invisible depuis le produit,
 * et perdue à la rotation des journaux.</p>
 *
 * <p><b>Lecture seule.</b> Aucun bouton n'écrit ni ne supprime : un journal qu'on peut amender
 * depuis l'interface qu'il surveille ne prouve rien.</p>
 */
@Component({
  selector: 'app-admin-audit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule, PaginatorComponent, SkeletonComponent, EmptyStateComponent, IconComponent,
    RouterLink, DatePipe,
  ],
  templateUrl: './admin-audit.component.html',
  // La barre de filtres est celle des autres listes : une seule feuille pour cinq écrans.
  styleUrls: ['./admin-list.scss', './admin-audit.component.scss'],
})
export class AdminAuditComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly route = inject(ActivatedRoute);
  private readonly searchInput$ = new Subject<void>();

  readonly entries = signal<AdminAuditEntry[]>([]);
  readonly actions = signal<AdminAuditAction[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly total = signal(0);

  readonly targetTypes: { value: AuditTargetType; label: string }[] = [
    { value: 'USER', label: 'Utilisateur' },
    { value: 'CLUB', label: 'Club' },
    { value: 'ATHLETE', label: 'Athlète' },
    { value: 'INVITATION', label: 'Invitation' },
    { value: 'PLATFORM', label: 'Plateforme' },
  ];

  search = '';
  filterAction = '';
  filterTarget = '';
  /** 0 = sans limite de temps. 30 jours par défaut : le journal se lit d'abord au présent. */
  filterDays = 30;
  /** Posé par un lien « historique » venant d'une fiche : le journal d'une seule ressource. */
  targetId = '';

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
    this.admin.auditActions().subscribe({
      next: (a) => this.actions.set(a),
      error: () => this.actions.set([]),
    });
    const qp = this.route.snapshot.queryParamMap;
    this.targetId = qp.get('targetId') ?? '';
    this.filterAction = qp.get('action') ?? '';
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
    this.filterAction = '';
    this.filterTarget = '';
    this.filterDays = 30;
    this.targetId = '';
    this.onFilterChange();
  }

  get hasFilters(): boolean {
    return !!(this.search || this.filterAction || this.filterTarget || this.targetId || this.filterDays !== 30);
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin
      .audit({
        action: this.filterAction || undefined,
        targetType: this.filterTarget || undefined,
        targetId: this.targetId || undefined,
        days: this.filterDays || undefined,
        q: this.search || undefined,
        page: this.page(),
      })
      .subscribe({
        next: (p) => {
          this.entries.set(p.content);
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

  /** La fiche correspondante, quand la cible en a une. */
  targetRoute(e: AdminAuditEntry): string[] | null {
    if (!e.targetId) return null;
    if (e.targetType === 'USER') return ['/admin/users', e.targetId];
    if (e.targetType === 'CLUB') return ['/admin/clubs', e.targetId];
    if (e.targetType === 'ATHLETE' || e.targetType === 'INVITATION') {
      return ['/admin/athletes', e.targetId, 'edit'];
    }
    return null;
  }
}
