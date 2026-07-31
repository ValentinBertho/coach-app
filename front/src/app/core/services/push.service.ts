import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone, inject } from '@angular/core';
import { Router } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * Charge utile `data` posée par le serveur. `onActionClick` est la convention du service
 * worker Angular : une destination par bouton d'action, plus une entrée « default ».
 */
interface NotificationData {
  url?: string;
  onActionClick?: Record<string, { operation?: string; url?: string }>;
}

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
   *
   * Quand le clic vient d'une **action rapide** (« Facile / Moyen / Dur » du rappel de
   * débriefing), c'est l'URL de cette action qui fait foi — elle porte le RPE choisi
   * (`?feedback=<id>&rpe=7`). Sans ça, un tap sur « Dur » ouvrirait la même feuille vide que
   * le corps de la notification, et les deux taps promis en redeviendraient quatre.
   */
  init(): void {
    if (!this.swPush.isEnabled) return;
    this.swPush.notificationClicks.subscribe(({ action, notification }) => {
      const data = notification.data as NotificationData | undefined;
      const url = (action ? data?.onActionClick?.[action]?.url : undefined) ?? data?.url;
      if (!url) return;
      try {
        // Query comprise : le paramètre EST l'information transportée par l'action.
        const target = new URL(url, document.baseURI);
        this.zone.run(() => this.router.navigateByUrl(target.pathname + target.search));
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
