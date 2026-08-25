import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  CLUB_STATUS_LABELS,
  ClubDetailAdmin,
  ClubStatus,
  clubStatusBadge,
  userStatusBadge,
  USER_STATUS_LABELS,
} from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Fiche club : qui l'anime, qui s'y entraîne, et ce que sa suppression détruirait.
 *
 * <p><b>Pourquoi l'aperçu d'impact.</b> La suppression d'un club efface en cascade ses coachs,
 * ses athlètes, leurs séances et leurs sorties importées — un historique qui ne se reconstitue
 * pas. La modale disait « et toutes ses données » sans jamais dire combien : on confirmait sans
 * savoir. Les compteurs sont maintenant sous les yeux au moment du geste, et la confirmation
 * demande de recopier un mot.</p>
 */
@Component({
  selector: 'app-admin-club-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, IconComponent, SkeletonComponent, DatePipe],
  templateUrl: './admin-club-detail.component.html',
  styleUrl: './admin-club-detail.component.scss',
})
export class AdminClubDetailComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly id = input.required<string>();

  readonly club = signal<ClubDetailAdmin | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly busy = signal(false);

  readonly statuses: ClubStatus[] = ['ACTIVE', 'SUSPENDED'];
  readonly statusLabels = CLUB_STATUS_LABELS;
  readonly statusBadge = clubStatusBadge;
  readonly memberStatusLabels = USER_STATUS_LABELS;
  readonly memberStatusBadge = userStatusBadge;

  draft = { name: '', status: 'ACTIVE' as ClubStatus };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin.clubDetail(this.id()).subscribe({
      next: (c) => {
        this.club.set(c);
        this.draft = { name: c.name, status: c.status };
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  save(): void {
    const c = this.club();
    if (!c || !this.draft.name.trim()) return;
    this.busy.set(true);
    this.admin.updateClub(c.id, { name: this.draft.name.trim(), status: this.draft.status }).subscribe({
      next: () => {
        this.toast.success('Club mis à jour.');
        this.busy.set(false);
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  async toggleStatus(): Promise<void> {
    const c = this.club();
    if (!c) return;
    const suspending = c.status === 'ACTIVE';
    if (suspending) {
      const ok = await this.confirm.ask({
        title: `Suspendre « ${c.name} » ?`,
        message:
          `${c.coaches} coach(s) et ${c.athletes} athlète(s) sont rattachés à ce club. `
          + 'Aucune donnée n\'est détruite ; le club est simplement marqué suspendu.',
        confirmLabel: 'Suspendre',
        danger: true,
      });
      if (!ok) return;
    }
    this.busy.set(true);
    this.admin
      .updateClub(c.id, { name: c.name, status: suspending ? 'SUSPENDED' : 'ACTIVE' })
      .subscribe({
        next: () => {
          this.toast.success(suspending ? 'Club suspendu.' : 'Club réactivé.');
          this.busy.set(false);
          this.load();
        },
        error: () => this.busy.set(false),
      });
  }

  async remove(): Promise<void> {
    const c = this.club();
    if (!c) return;
    const ok = await this.confirm.askForText({
      title: `Supprimer « ${c.name} » ?`,
      message:
        `Cette suppression efface en cascade ${c.coaches} compte(s) coach, ${c.athletes} `
        + `athlète(s), ${c.workouts} séance(s) prescrite(s) et ${c.activities} sortie(s) `
        + "importée(s). Cet historique n'a pas de sauvegarde côté utilisateur et ne se "
        + 'reconstitue pas. Une suspension ferme l\'accès sans rien détruire.',
      confirmLabel: 'Supprimer définitivement',
      danger: true,
      requiredText: 'SUPPRIMER',
    });
    if (!ok) return;

    this.busy.set(true);
    this.admin.deleteClub(c.id).subscribe({
      next: () => {
        this.toast.success('Club supprimé.');
        void this.router.navigate(['/admin/clubs']);
      },
      error: () => this.busy.set(false),
    });
  }
}
