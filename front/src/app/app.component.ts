import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { FeedbackQueueService } from './core/services/feedback-queue.service';
import { NetworkStatusService } from './core/services/network-status.service';
import { PwaInstallService } from './core/services/pwa-install.service';
import { ThemeService } from './core/services/theme.service';
import { ConfirmDialogComponent } from './shared/components/confirm-dialog/confirm-dialog.component';
import { ToastComponent } from './shared/components/toast/toast.component';

/**
 * Shell applicatif : router-outlet + toasts + modale de confirmation.
 * Instancie tôt les services PWA (réseau, installation, file de synchronisation).
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ToastComponent, ConfirmDialogComponent],
  template: `
    <router-outlet />
    <app-toast />
    <app-confirm-dialog />
  `,
})
export class AppComponent {
  // Injection au démarrage : capture beforeinstallprompt, écoute online/offline, vide la file.
  private readonly network = inject(NetworkStatusService);
  private readonly pwa = inject(PwaInstallService);
  private readonly queue = inject(FeedbackQueueService);
  private readonly theme = inject(ThemeService);

  constructor() {
    this.theme.init();
  }
}
