import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdminOverview, AdminSignal, StravaWebhookState } from '../../core/models/admin.model';
import { AdminService } from '../../core/services/admin.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

/**
 * Tableau de bord d'administration.
 *
 * <p><b>Ce qu'il remplace.</b> Sept compteurs bruts — clubs, coachs, athlètes, séances… — dont
 * aucun ne permettait de décider quoi que ce soit : on pouvait passer devant tous les jours
 * pendant que le plafond d'e-mails se remplissait et que des coachs restaient bloqués sur un lien
 * de vérification jamais reçu.</p>
 *
 * <p><b>L'ordre de lecture est l'ordre du travail.</b> D'abord ce qui ne va pas et emmène sur
 * l'écran qui le règle ; ensuite la photographie ; puis l'usage réel ; enfin les canaux et les
 * dernières actions d'administration. On s'arrête dès qu'il n'y a plus rien à décider.</p>
 */
@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent, SkeletonComponent, DecimalPipe, DatePipe],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss',
})
export class AdminDashboardComponent implements OnInit {
  private readonly admin = inject(AdminService);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);

  readonly overview = signal<AdminOverview | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);

  readonly resetAvailable = signal(false);
  readonly resetting = signal(false);

  /** État du webhook Strava ; `null` tant qu'on n'a pas pu interroger Strava. */
  readonly strava = signal<StravaWebhookState | null>(null);
  readonly stravaBusy = signal(false);

  /** Une liste vide est un résultat : l'écran le dit, plutôt que d'afficher un trou. */
  readonly hasSignals = computed(() => (this.overview()?.signals.length ?? 0) > 0);

  ngOnInit(): void {
    this.load();
    this.admin.resetAvailable().subscribe({
      next: (r) => this.resetAvailable.set(r.available),
      error: () => this.resetAvailable.set(false),
    });
    this.loadStrava();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.admin.overview().subscribe({
      next: (o) => {
        this.overview.set(o);
        this.loading.set(false);
      },
      error: () => {
        // Un tableau vide serait indiscernable d'une plateforme vide : on nomme l'échec.
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  /**
   * Les signaux portent une route complète, éventuellement filtrée
   * (`/admin/users?verified=false`). RouterLink veut le chemin et les paramètres séparés : on
   * découpe ici plutôt que dans le gabarit, où la condition deviendrait illisible au deuxième
   * signal filtré.
   */
  signalPath(s: AdminSignal): string {
    return (s.actionRoute ?? '').split('?')[0];
  }

  signalParams(s: AdminSignal): Record<string, string> {
    const raw = (s.actionRoute ?? '').split('?')[1];
    if (!raw) return {};
    const params: Record<string, string> = {};
    new URLSearchParams(raw).forEach((value, key) => (params[key] = value));
    return params;
  }

  signalIcon(s: AdminSignal): string {
    return s.severity === 'CRITICAL' ? 'alert-triangle' : s.severity === 'WARNING' ? 'info' : 'lightbulb';
  }

  integrationBadge(status: string): string {
    return status === 'OK' ? 'badge-success' : status === 'WARNING' ? 'badge-warning' : 'badge-neutral';
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
        this.load();
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
        this.load();
      },
      error: () => this.stravaBusy.set(false),
    });
  }

  async resetDemo(): Promise<void> {
    const ok = await this.confirm.askForText({
      title: 'Réinitialiser la démo',
      message:
        'Cette action efface TOUTES les données de cette instance — comptes, clubs, athlètes, '
        + 'séances, historiques — et recharge le jeu de démonstration. Elle est irréversible.',
      confirmLabel: 'Tout réinitialiser',
      danger: true,
      requiredText: 'REINITIALISER',
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
