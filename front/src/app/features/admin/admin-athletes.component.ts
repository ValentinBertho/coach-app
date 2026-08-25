import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { AdminAthlete, ATHLETE_STATUS_LABELS, ClubAdmin } from '../../core/models/admin.model';
import { AthleteStatus } from '../../core/models/athlete.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Liste des athlètes, tous clubs confondus.
 *
 * <p>Le filtre par statut existait côté serveur et n'était jamais envoyé ; la colonne
 * « invitation » ne disait pas si le lien était encore valable, et rien n'indiquait qu'un athlète
 * n'avait aucun coach — le cas le plus silencieux et le plus problématique : personne ne lui
 * prescrit rien, et personne ne s'en aperçoit.</p>
 */
@Component({
  selector: 'app-admin-athletes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    SkeletonComponent, FormsModule, RouterLink, PaginatorComponent, EmptyStateComponent,
    IconComponent, DatePipe,
  ],
  templateUrl: './admin-athletes.component.html',
  styleUrl: './admin-list.scss',
})
export class AdminAthletesComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly searchInput$ = new Subject<void>();

  readonly athletes = signal<AdminAthlete[]>([]);
  readonly clubs = signal<ClubAdmin[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly total = signal(0);
  readonly busy = signal(false);

  readonly statuses: AthleteStatus[] = ['ACTIVE', 'PAUSED', 'ARCHIVED'];
  readonly statusLabels = ATHLETE_STATUS_LABELS;

  search = '';
  filterClub = '';
  filterStatus: AthleteStatus | '' = '';

  ngOnInit(): void {
    this.searchInput$.pipe(debounceTime(300)).subscribe(() => {
      this.page.set(0);
      this.load();
    });
    const qp = this.route.snapshot.queryParamMap;
    this.search = qp.get('q') ?? '';
    this.filterClub = qp.get('clubId') ?? '';
    this.filterStatus = (qp.get('status') as AthleteStatus) ?? '';

    // Taille explicite : la première page seule rendait le filtre faux au-delà de 20 clubs.
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
    this.filterClub = '';
    this.filterStatus = '';
    this.onFilterChange();
  }

  get hasFilters(): boolean {
    return !!(this.search || this.filterClub || this.filterStatus);
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin
      .athletes({
        clubId: this.filterClub || undefined,
        status: this.filterStatus || undefined,
        q: this.search || undefined,
        page: this.page(),
      })
      .subscribe({
        next: (p) => {
          this.athletes.set(p.content);
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

  /** Vrai si le lien d'invitation ne mène plus nulle part. */
  inviteExpired(a: AdminAthlete): boolean {
    return !!a.invitationPending && !!a.inviteExpiresAt && new Date(a.inviteExpiresAt) < new Date();
  }

  async resendInvitation(a: AdminAthlete): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Renvoyer l’invitation',
      message:
        `Un nouveau lien sera généré pour ${a.firstName} ${a.lastName} et le précédent cessera de `
        + 'fonctionner. Il partira par e-mail si une adresse est connue.',
      confirmLabel: 'Renvoyer',
    });
    if (!ok) return;

    this.busy.set(true);
    this.admin.resendInvitation(a.id).subscribe({
      next: (link) => {
        this.busy.set(false);
        this.toast.success(
          link.emailSent
            ? 'Invitation renvoyée par e-mail.'
            : "Lien régénéré. Aucune adresse connue : à transmettre à la main depuis les invitations.",
        );
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  async remove(a: AdminAthlete): Promise<void> {
    const ok = await this.confirm.askForText({
      title: 'Supprimer l’athlète',
      message:
        `Supprimer ${a.firstName} ${a.lastName} efface ses séances, ses sorties importées et ses `
        + "ressentis. Cet historique n'a pas de sauvegarde côté utilisateur et ne se reconstitue "
        + 'pas. Passer l’athlète en « archivé » depuis sa fiche le retire des listes sans rien '
        + 'détruire.',
      confirmLabel: 'Supprimer définitivement',
      danger: true,
      requiredText: 'SUPPRIMER',
    });
    if (!ok) return;
    this.admin.deleteAthlete(a.id).subscribe(() => {
      this.toast.success('Athlète supprimé.');
      this.load();
    });
  }
}
