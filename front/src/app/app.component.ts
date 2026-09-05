import { isPlatformBrowser } from '@angular/common';
import { PLATFORM_ID, Component, DestroyRef, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { FeedbackQueueService } from './core/services/feedback-queue.service';
import { NetworkStatusService } from './core/services/network-status.service';
import { PushService } from './core/services/push.service';
import { PwaInstallService } from './core/services/pwa-install.service';
import { ThemeService } from './core/services/theme.service';
import { UpdateService } from './core/services/update.service';
import { CelebrationOverlayComponent } from './shared/components/celebration/celebration-overlay.component';
import { ConfirmDialogComponent } from './shared/components/confirm-dialog/confirm-dialog.component';
import { FeedbackPanelComponent } from './shared/components/feedback-panel/feedback-panel.component';
import { PushOnboardingComponent } from './shared/components/push-onboarding/push-onboarding.component';
import { ToastComponent } from './shared/components/toast/toast.component';
import { UpdateBannerComponent } from './shared/components/update-banner/update-banner.component';
import { HelpSearchOverlayComponent } from './features/help/help-search-overlay.component';
import { CommandPaletteComponent } from './features/search/command-palette.component';
import { ImpersonationBannerComponent } from './shared/components/impersonation-banner/impersonation-banner.component';

/**
 * Shell applicatif : router-outlet + toasts + célébration + confirmation + recherche globale
 * + aide + mise à jour PWA.
 * Instancie tôt les services PWA (réseau, installation, file de synchronisation, mise à jour).
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastComponent, ConfirmDialogComponent, HelpSearchOverlayComponent, CommandPaletteComponent, UpdateBannerComponent, CelebrationOverlayComponent, FeedbackPanelComponent, PushOnboardingComponent, ImpersonationBannerComponent],
  template: `
    <!-- Avant tout le reste : une session empruntée doit se voir dès le premier pixel, sur les
         deux coquilles comme sur les écrans qui n'en ont pas. -->
    <app-impersonation-banner />
    <router-outlet />
    <app-toast />
    <app-celebration-overlay />
    <app-confirm-dialog />
    <app-help-search-overlay />
    <app-command-palette />
    <app-update-banner />
    <!-- Monté ici, comme la confirmation : le retour doit pouvoir s'ouvrir depuis n'importe
         quel écran des deux coquilles, pas seulement depuis une entrée de navigation. -->
    <app-feedback-panel />
    <!-- Invitation aux notifications au premier lancement de l'application installée. À la racine
         parce qu'elle ne dépend d'aucun écran : celui qui vient d'installer n'en connaît encore
         aucun, et c'est précisément ce que les invitations existantes supposaient. -->
    <app-push-onboarding />
  `,
})
export class AppComponent {
  // Injection au démarrage : capture beforeinstallprompt, écoute online/offline, vide la file.
  private readonly network = inject(NetworkStatusService);
  private readonly pwa = inject(PwaInstallService);
  private readonly queue = inject(FeedbackQueueService);
  private readonly theme = inject(ThemeService);
  private readonly update = inject(UpdateService);
  private readonly push = inject(PushService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    // Ces quatre amorçages sont des gestes de navigateur, et aucun n'a de sens au pré-rendu des
    // fiches coachs, fabriquées dans Node à la compilation : il n'y a là ni onglet à réveiller,
    // ni service worker à mettre à jour, ni appareil à abonner aux notifications.
    //
    // `keepSessionAlive` est le plus important à écarter : il rafraîchit un jeton
    // d'authentification. Le laisser tourner ferait partir, depuis la machine qui compile, des
    // requêtes de session vers l'API — pour un fichier statique qui sera servi à tout le monde.
    if (!isPlatformBrowser(inject(PLATFORM_ID))) {
      return;
    }
    this.theme.init();
    this.update.init();
    this.push.init();
    this.keepSessionAlive();
  }

  /**
   * Garde la session vivante entre deux usages. Une PWA mobile passe l'essentiel de son temps en
   * arrière-plan : sans ce réveil, le retour au premier plan commençait par une salve de requêtes
   * expirées. On rafraîchit au démarrage, puis à chaque retour au premier plan.
   */
  private keepSessionAlive(): void {
    this.auth.ensureFreshToken();
    const onVisible = () => {
      if (document.visibilityState === 'visible') this.auth.ensureFreshToken();
    };
    document.addEventListener('visibilitychange', onVisible);
    this.destroyRef.onDestroy(() => document.removeEventListener('visibilitychange', onVisible));
  }
}
