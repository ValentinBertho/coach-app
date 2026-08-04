import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone, inject, signal } from '@angular/core';
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

  /**
   * Cet appareil est-il réellement abonné ? Lu depuis le navigateur, pas depuis un souvenir de
   * session : le bouton « Notifications » se masquait sur un simple drapeau local, remis à faux
   * à chaque rechargement. Un athlète qui avait déjà accepté revoyait donc la proposition
   * indéfiniment, sans jamais pouvoir dire si les notifications étaient actives ou non.
   */
  readonly subscribed = signal(false);

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
    this.watchSubscription();
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

  /**
   * Suit l'abonnement du navigateur et le <b>réaligne</b> sur le serveur.
   *
   * <p>Les deux moitiés du dispositif vivent des vies séparées : le navigateur garde son
   * abonnement, le serveur garde la ligne qui lui dit où pousser. Elles se désynchronisent — le
   * serveur supprime une ligne sur un 404/410 passager, une restauration de sauvegarde la perd,
   * ou le navigateur renouvelle ses clés. L'appareil se croit alors abonné et le serveur ne sait
   * plus où écrire : l'athlète cesse d'être prévenu <b>sans aucun signe</b>, et le repli e-mail
   * ne se déclenche pas puisque personne ne le sait. Un ré-enregistrement au démarrage coûte une
   * requête et referme ce trou de lui-même.</p>
   */
  private watchSubscription(): void {
    this.swPush.subscription.subscribe((sub) => {
      this.subscribed.set(!!sub);
      if (sub) {
        this.http.post(`${environment.apiUrl}/push/subscribe`, sub.toJSON()).subscribe({
          error: () => { /* hors ligne ou non connecté : le prochain démarrage réessaiera */ },
        });
      }
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
    this.subscribed.set(true);
    return true;
  }

  /**
   * Coupe les notifications de ce compte à la déconnexion.
   *
   * <p>Sans ça, l'abonnement du navigateur restait rattaché au compte précédent côté serveur :
   * sur un téléphone partagé — le cas d'usage que la purge des caches et de la file de retours
   * couvre déjà — l'appareil continuait d'afficher « Retour de votre coach : <titre de séance> »
   * pour quelqu'un d'autre. Le titre d'une séance n'est pas une donnée de santé, mais c'est bien
   * la vie d'entraînement d'un tiers.</p>
   *
   * <p>Best-effort et sans attente : la déconnexion ne doit jamais dépendre du réseau. On
   * prévient le serveur, puis on désabonne le navigateur lui-même — l'un sans l'autre laisserait
   * soit une ligne morte en base, soit un abonnement local orphelin.</p>
   */
  async disable(): Promise<void> {
    this.subscribed.set(false);
    try {
      await firstValueFrom(this.http.delete(`${environment.apiUrl}/push/subscriptions`));
    } catch {
      // Hors ligne ou session déjà expirée : la ligne serveur sera retirée au premier 404/410.
    }
    try {
      if (this.swPush.isEnabled) {
        await this.swPush.unsubscribe();
      }
    } catch {
      // Aucun abonnement actif : rien à faire.
    }
  }
}
