import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { InvitationAdmin } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';

@Component({
  selector: 'app-admin-invitations',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  templateUrl: './admin-invitations.component.html',
})
export class AdminInvitationsComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly invitations = signal<InvitationAdmin[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.admin.invitations().subscribe({
      next: (p) => {
        this.invitations.set(p.content);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  async revoke(inv: InvitationAdmin): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Révoquer l’invitation',
      message: `Révoquer l'invitation de ${inv.firstName} ${inv.lastName} ?`,
      confirmLabel: 'Révoquer',
      danger: true,
    });
    if (!ok) return;
    this.admin.revokeInvitation(inv.athleteId).subscribe(() => {
      this.toast.success('Invitation révoquée.');
      this.load();
    });
  }
}
