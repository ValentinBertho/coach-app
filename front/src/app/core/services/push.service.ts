import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Notifications push côté client (SwPush). Disponible uniquement quand le service worker
 * est actif (build de production) et le navigateur compatible.
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  private readonly swPush = inject(SwPush);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);

  get available(): boolean {
    return this.swPush.isEnabled;
  }

  /**
   * Branche la navigation au clic sur une notification : ouvre l'écran ciblé
   * (`data.url`, ex. /athlete/today). Appelé une fois au démarrage de l'app.
   */
  init(): void {
    if (!this.swPush.isEnabled) return;
    this.swPush.notificationClicks.subscribe(({ notification }) => {
      const url = (notification.data as { url?: string } | undefined)?.url;
      if (!url) return;
      try {
        const path = new URL(url, document.baseURI).pathname;
        this.zone.run(() => this.router.navigateByUrl(path));
      } catch { /* URL invalide : on ignore */ }
    });
  }

  /** Demande l'autorisation, s'abonne et enregistre l'abonnement côté serveur. */
  async enable(): Promise<boolean> {
    if (!this.swPush.isEnabled) return false;
    const cfg = await firstValueFrom(
      this.http.get<{ enabled: boolean; publicKey: string }>(`${environment.apiUrl}/push/public-key`)
    );
    if (!cfg.enabled || !cfg.publicKey) return false;

    const sub = await this.swPush.requestSubscription({ serverPublicKey: cfg.publicKey });
    await firstValueFrom(
      this.http.post(`${environment.apiUrl}/push/subscribe`, sub.toJSON())
    );
    return true;
  }
}
