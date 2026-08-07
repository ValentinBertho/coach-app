import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { AdminStats, StravaWebhookState } from '../../core/models/admin.model';
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

  /** État du webhook Strava ; `null` tant qu'on n'a pas pu interroger Strava. */
  readonly strava = signal<StravaWebhookState | null>(null);
  readonly stravaBusy = signal(false);

  ngOnInit(): void {
    this.admin.stats().subscribe((s) => this.stats.set(s));
    this.admin.resetAvailable().subscribe((r) => this.resetAvailable.set(r.available));
    this.loadStrava();
  }

  /**
   * L'appel traverse jusqu'à Strava : sans intégration configurée il échoue, et c'est un état
   * normal — la carte reste alors masquée plutôt que d'afficher une erreur sans objet.
   */
  private loadStrava(): void {
    this.admin.stravaWebhook().subscribe({
      next: (s) => this.strava.set(s),
      error: () => this.strava.set(null),
    });
  }

  /**
   * Strava valide l'adresse de rappel dans la seconde qui suit : si l'instance n'est pas
   * joignable depuis l'extérieur, le refus arrive ici, en clair.
   */
  createStravaWebhook(): void {
    this.stravaBusy.set(true);
    this.admin.createStravaWebhook().subscribe({
      next: () => {
        this.toast.success('Abonnement créé — les activités remontent maintenant en direct.');
        this.stravaBusy.set(false);
        this.loadStrava();
      },
      error: () => this.stravaBusy.set(false),
    });
  }

  async deleteStravaWebhook(id: number): Promise<void> {
    const ok = await this.confirm.ask({
      title: 'Retirer l’abonnement Strava',
      message: 'Les activités ne remonteront plus que par la synchronisation horaire. Continuer ?',
      confirmLabel: 'Retirer',
      danger: true,
    });
    if (!ok) return;

    this.stravaBusy.set(true);
    this.admin.deleteStravaWebhook(id).subscribe({
      next: () => {
        this.toast.success('Abonnement retiré.');
        this.stravaBusy.set(false);
        this.loadStrava();
      },
      error: () => this.stravaBusy.set(false),
    });
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
