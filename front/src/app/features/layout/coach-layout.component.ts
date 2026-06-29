import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { HelpService } from '../help/help.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';
import { PushButtonComponent } from '../../shared/components/push-button/push-button.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';

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

  ngOnInit(): void {
    if (!this.auth.currentUser()) {
      this.auth.loadCurrentUser().subscribe({ error: () => this.logout() });
    }
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
    this.auth.logout();
    this.toast.info('Vous êtes déconnecté.');
    this.router.navigate(['/login']);
  }
}
