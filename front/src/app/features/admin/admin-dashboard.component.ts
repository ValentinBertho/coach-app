import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { AdminStats } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-dashboard.component.html',
})
export class AdminDashboardComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly stats = signal<AdminStats | null>(null);
  readonly resetAvailable = signal(false);
  readonly resetting = signal(false);

  ngOnInit(): void {
    this.admin.stats().subscribe((s) => this.stats.set(s));
    this.admin.resetAvailable().subscribe((r) => this.resetAvailable.set(r.available));
  }

  async resetDemo(): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Réinitialiser la démo',
      message:
        'Cette action efface TOUTES les données et recharge le jeu de démo. Continuer ?',
      confirmLabel: 'Tout réinitialiser',
      danger: true,
    });
    if (!ok) return;

    this.resetting.set(true);
    this.admin.reset().subscribe({
      next: () => {
        this.toast.success('Démo réinitialisée. Rechargement…');
        setTimeout(() => window.location.reload(), 900);
      },
      error: () => this.resetting.set(false),
    });
  }
}
