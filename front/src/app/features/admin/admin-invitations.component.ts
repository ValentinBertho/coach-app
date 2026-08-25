import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { InvitationAdmin } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PaginatorComponent } from '../../shared/components/paginator/paginator.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * File de travail des invitations athlète en attente.
 *
 * <p><b>Ce qui manquait.</b> Seule la <i>révocation</i> existait : une invitation expirée n'avait
 * d'autre issue que de supprimer l'athlète et de le recréer — donc d'effacer son historique — ou
 * de demander à un coach du club de refaire le geste depuis son propre compte. Le renvoi et la
 * copie du lien règlent les deux cas, y compris celui de l'athlète sans adresse connue, dont
 * l'invitation ne peut voyager que de la main à la main.</p>
 */
@Component({
  selector: 'app-admin-invitations',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [SkeletonComponent, EmptyStateComponent, PaginatorComponent, IconComponent, RouterLink, DatePipe],
  templateUrl: './admin-invitations.component.html',
  styleUrls: ['./admin-list.scss', './admin-invitations.component.scss'],
})
export class AdminInvitationsComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly invitations = signal<InvitationAdmin[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly busy = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(1);
  readonly total = signal(0);

  /** Dernier lien régénéré, gardé à l'écran pour pouvoir le copier. */
  readonly lastLink = signal<{ athleteId: string; url: string } | null>(null);

  readonly expiredCount = computed(() => this.invitations().filter((i) => this.expired(i)).length);

  ngOnInit(): void {
    this.load();
  }

  goToPage(p: number): void {
    this.page.set(p);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin.invitations(this.page()).subscribe({
      next: (p) => {
        this.invitations.set(p.content);
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

  /** Le serveur le dit déjà (`expired`) ; le calcul local couvre les fronts encore en cache. */
  expired(inv: InvitationAdmin): boolean {
    if (inv.expired !== undefined) return inv.expired;
    return !!inv.expiresAt && new Date(inv.expiresAt) < new Date();
  }

  async resend(inv: InvitationAdmin): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Renvoyer l’invitation',
      message:
        `Un nouveau lien sera généré pour ${inv.firstName} ${inv.lastName}, valable 14 jours. `
        + 'Le lien précédent cessera de fonctionner — c’est voulu : il a pu circuler.',
      confirmLabel: 'Renvoyer',
    });
    if (!ok) return;

    this.busy.set(true);
    this.admin.resendInvitation(inv.athleteId).subscribe({
      next: (link) => {
        this.busy.set(false);
        this.lastLink.set({ athleteId: inv.athleteId, url: link.url });
        this.toast.success(
          link.emailSent
            ? 'Invitation renvoyée par e-mail.'
            : 'Lien régénéré. Aucune adresse connue : copie-le et transmets-le.',
        );
        this.load();
      },
      error: () => this.busy.set(false),
    });
  }

  /**
   * Copie dans le presse-papiers, avec repli explicite : l'API n'existe pas hors contexte
   * sécurisé, et un bouton qui ne fait rien sans le dire est pire que pas de bouton.
   */
  async copy(url: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(url);
      this.toast.success('Lien copié.');
    } catch {
      this.toast.warning('Copie impossible depuis ce navigateur — sélectionne le lien à la main.');
    }
  }

  async revoke(inv: InvitationAdmin): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Révoquer l’invitation',
      message:
        `Le lien de ${inv.firstName} ${inv.lastName} cessera de fonctionner. L'athlète et ses `
        + "données restent en place : seule l'invitation est annulée.",
      confirmLabel: 'Révoquer',
      danger: true,
    });
    if (!ok) return;
    this.admin.revokeInvitation(inv.athleteId).subscribe(() => {
      this.toast.success('Invitation révoquée.');
      this.lastLink.set(null);
      this.load();
    });
  }
}
