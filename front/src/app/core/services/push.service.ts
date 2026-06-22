import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
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

  get available(): boolean {
    return this.swPush.isEnabled;
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
