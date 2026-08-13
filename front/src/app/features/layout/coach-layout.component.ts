import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AppBadgeService } from '../../core/services/app-badge.service';
import { AuthService } from '../../core/services/auth.service';
import { BreadcrumbService } from '../../core/services/breadcrumb.service';
import { CoachDashboardService } from '../../core/services/coach-dashboard.service';
import { CommandPaletteService } from '../../core/services/command-palette.service';
import { FeedbackService } from '../../core/services/feedback.service';
import { MessageService } from '../../core/services/message.service';
import { ToastService } from '../../core/services/toast.service';
import { HelpService } from '../help/help.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';
import { PushButtonComponent } from '../../shared/components/push-button/push-button.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';
import { supportMailto as supportLink } from '../../shared/components/support-link';

/**
 * Coquille de l'espace coach : en-tête (club, utilisateur, déconnexion), navigation,
 * et router-outlet pour les sous-pages (tableau de bord, athlètes…).
 */
@Component({
  selector: 'app-coach-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LogoComponent, OfflineBannerComponent, PushButtonComponent, IconComponent, NotificationBellComponent],
  templateUrl: './coach-layout.component.html',
  styleUrl: './coach-layout.component.scss',
})
export class CoachLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly help = inject(HelpService);
  readonly palette = inject(CommandPaletteService);
  private readonly breadcrumb = inject(BreadcrumbService);
  private readonly messages = inject(MessageService);
  private readonly dashboard = inject(CoachDashboardService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly feedback = inject(FeedbackService);
  private readonly badge = inject(AppBadgeService);

  /**
   * « Signaler un problème » : ouvre le panneau de retour, qui enregistre le message en base
   * avec son contexte (page, version, navigateur, dernière erreur serveur).
   *
   * <p>C'était un `mailto:` — mieux que rien, mais il suppose un client mail configuré, ce qui
   * est rarement le cas sur une PWA mobile, et il ne laissait aucune trace exploitable : ni file
   * à traiter, ni statut, ni recoupement possible avec les erreurs remontées par Sentry.</p>
   */
  openFeedback(): void {
    this.feedback.open();
  }

  /**
   * Repli par e-mail, conservé pour l'aide : si le panneau ne part pas — session expirée,
   * hors ligne — il reste une adresse à qui écrire.
   */
  supportMailto(): string {
    return supportLink('Signalement depuis l’espace coach');
  }

  /** Non-lus de la messagerie, tous athlètes confondus (badge de l'entrée « Messages »). */
  readonly unreadMessages = this.messages.unread;

  /** Retours d'athlètes non traités (badge de l'entrée « Retours »). */
  readonly pendingReviews = this.dashboard.pendingReviews;

  /**
   * Pastille de l'icône de l'application : la somme de ce qui attend le coach.
   *
   * <p>Un effet plutôt qu'un appel après chaque rafraîchissement — les deux compteurs sont déjà
   * des signaux, et les recopier à la main quelque part finirait par oublier un chemin : un
   * retour traité depuis « Ma journée », un fil ouvert depuis une notification. Ici, la pastille
   * suit ce que la navigation affiche, par construction.</p>
   */
  private readonly badgeSync = effect(() => {
    this.badge.set(this.unreadMessages() + this.pendingReviews());
  });

  /** Fil d'Ariane de la barre supérieure : « où suis-je » quand on est dans un contexte. */
  readonly trail = this.breadcrumb.trail;

  readonly user = this.auth.currentUser;
  readonly resending = signal(false);

  /** Nav latérale repliée en rail d'icônes — préférence mémorisée entre sessions. */
  private static readonly NAV_KEY = 'coach-nav-collapsed';
  readonly navCollapsed = signal(this.readNavPref());

  private readNavPref(): boolean {
    try { return localStorage.getItem(CoachLayoutComponent.NAV_KEY) === '1'; }
    catch { return false; }
  }

  toggleNav(): void {
    this.navCollapsed.update((v) => !v);
    try { localStorage.setItem(CoachLayoutComponent.NAV_KEY, this.navCollapsed() ? '1' : '0'); }
    catch { /* stockage indisponible : préférence non persistée, sans gravité */ }
  }

  /** Panneau « Plus » mobile : donne accès à la nav complète groupée (bottom-nav limitée à 4 slots). */
  readonly moreOpen = signal(false);
  toggleMore(): void { this.moreOpen.update((v) => !v); }
  closeMore(): void { this.moreOpen.set(false); }

  ngOnInit(): void {
    if (!this.auth.currentUser()) {
      this.auth.loadCurrentUser().subscribe({ error: () => this.logout() });
    }
    // Badges : au chargement, puis à chaque navigation (un fil ouvert vient d'être marqué lu,
    // un retour vient d'être traité, un nouveau message a pu arriver entre-temps).
    this.refreshBadges();
    this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshBadges());
  }

  private refreshBadges(): void {
    // Le club n'est connu qu'une fois l'utilisateur chargé ; la navigation suivante réessaiera.
    if (!this.auth.clubId()) return;
    const silent = { error: () => { /* badge absent plutôt que bloquant */ } };
    this.messages.refreshUnread().subscribe(silent);
    this.dashboard.refreshPendingReviews().subscribe(silent);
  }

  /** Renvoie l'e-mail de vérification (bandeau « e-mail non confirmé »). */
  resendVerification(): void {
    if (this.resending()) return;
    this.resending.set(true);
    this.auth.resendVerification().subscribe({
      next: () => { this.resending.set(false); this.toast.success('E-mail de vérification renvoyé.'); },
      error: () => { this.resending.set(false); this.toast.error('Envoi impossible.'); },
    });
  }

  logout(): void {
    // La pastille survivrait à la déconnexion : sur un téléphone partagé, elle annoncerait à la
    // personne suivante le travail en attente de quelqu'un d'autre.
    this.badge.clear();
    this.auth.logout();
    this.toast.info('Tu es déconnecté·e.');
    this.router.navigate(['/login']);
  }
}
