import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AdminAthlete, ClubAdmin } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-admin-athletes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  templateUrl: './admin-athletes.component.html',
})
export class AdminAthletesComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly athletes = signal<AdminAthlete[]>([]);
  readonly clubs = signal<ClubAdmin[]>([]);
  readonly loading = signal(true);
  search = '';
  filterClub = '';

  ngOnInit(): void {
    this.admin.clubs(undefined, 0).subscribe((p) => this.clubs.set(p.content));
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.admin.athletes({ clubId: this.filterClub || undefined, q: this.search || undefined }).subscribe({
      next: (p) => {
        this.athletes.set(p.content);
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
