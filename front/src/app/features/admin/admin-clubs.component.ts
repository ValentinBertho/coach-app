import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ClubAdmin, ClubStatus } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-admin-clubs',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  templateUrl: './admin-clubs.component.html',
})
export class AdminClubsComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly clubs = signal<ClubAdmin[]>([]);
  readonly loading = signal(true);
  readonly statuses: ClubStatus[] = ['ACTIVE', 'SUSPENDED'];
  search = '';
  newName = '';

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.admin.clubs(this.search || undefined).subscribe({
      next: (p) => {
        this.clubs.set(p.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  create(): void {
    if (!this.newName.trim()) return;
    this.admin.createClub({ name: this.newName.trim() }).subscribe(() => {
      this.toast.success('Club créé.');
      this.newName = '';
      this.load();
    });
  }

  save(club: ClubAdmin): void {
    this.admin.updateClub(club.id, { name: club.name, status: club.status }).subscribe(() => {
      this.toast.success('Club mis à jour.');
    });
  }

  async remove(club: ClubAdmin): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Supprimer le club',
      message: `Supprimer « ${club.name} » et toutes ses données (coachs, athlètes, séances) ?`,
      confirmLabel: 'Supprimer',
      danger: true,
    });
    if (!ok) return;
    this.admin.deleteClub(club.id).subscribe(() => {
      this.toast.success('Club supprimé.');
      this.load();
    });
  }
}
