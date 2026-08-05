import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SwPush } from '@angular/service-worker';
import { Observable, firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Charge utile `data` posée par le serveur. `onActionClick` est la convention du service
 * worker Angular : une destination par bouton d'action, plus une entrée « default ».
 */
interface NotificationData {
  url?: string;
  onActionClick?: Record<string, { operation?: string; url?: string }>;
}

/** Échec d'activation dont le message est directement affichable à l'utilisateur. */
export class PushError extends Error {}

/**
 * Un appareil abonné, tel que le serveur le décrit. L'endpoint n'en fait volontairement pas
 * partie : c'est une URL secrète, et l'identifiant opaque suffit à désigner la ligne à retirer.
 */
export interface PushDevice {
  id: string;
  label: string;
  /** SHA-256 tronqué de l'endpoint : permet à ce navigateur de reconnaître sa propre ligne. */
  fingerprint: string;
  createdAt: string;
  lastSuccessAt: string | null;
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
  private readonly auth = inject(AuthService);

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
      // Sans session, il n'y a personne à réenregistrer : cet appel partait quand même au
      // démarrage — y compris sur l'écran de connexion — et repartait en 401.
      if (sub && this.auth.isAuthenticated()) {
        this.http.post(`${environment.apiUrl}/push/subscribe`, sub.toJSON()).subscribe({
          error: () => { /* hors ligne ou non connecté : le prochain démarrage réessaiera */ },
        });
      }
    });
  }

  /**
   * Demande l'autorisation, s'abonne et enregistre l'abonnement côté serveur.
   *
   * <p>Chaque issue possible porte son propre message : refus de l'utilisateur, navigateur qui
   * ne peut pas s'abonner (iOS hors écran d'accueil), serveur sans clés VAPID, ou panne
   * d'enregistrement. L'activation affichait jusqu'ici « Autorisation refusée » pour tout, y
   * compris quand l'autorisation venait d'être accordée et que c'était l'enregistrement serveur
   * qui avait échoué — doublé du toast global de l'intercepteur.</p>
   *
   * @throws PushError avec un message affichable tel quel
   */
  async enable(): Promise<boolean> {
    if (!this.swPush.isEnabled) {
      throw new PushError(this.unsupportedMessage());
    }

    let cfg: { enabled: boolean; publicKey: string };
    try {
      cfg = await firstValueFrom(
        this.http.get<{ enabled: boolean; publicKey: string }>(`${environment.apiUrl}/push/public-key`)
      );
    } catch {
      throw new PushError('Service indisponible pour le moment — réessaie dans un instant.');
    }
    if (!cfg.enabled || !cfg.publicKey) {
      throw new PushError("Les notifications ne sont pas activées sur ce serveur.");
    }

    let sub: PushSubscription;
    try {
      sub = await this.swPush.requestSubscription({ serverPublicKey: cfg.publicKey });
    } catch (err) {
      throw new PushError(this.subscriptionErrorMessage(err));
    }

    try {
      await firstValueFrom(this.http.post(`${environment.apiUrl}/push/subscribe`, sub.toJSON()));
    } catch {
      // L'abonnement existe côté navigateur mais le serveur ne le connaît pas : on le défait,
      // sinon l'appareil se croit abonné pour toujours et n'est jamais notifié.
      await this.swPush.unsubscribe().catch(() => undefined);
      throw new PushError("Enregistrement impossible — réessaie dans un instant.");
    }
    this.subscribed.set(true);
    return true;
  }

  /**
   * Pourquoi ce navigateur ne peut pas s'abonner. Sur iOS, le web push n'existe que dans une
   * PWA <b>installée</b> : proposer « autorise les notifications » à qui navigue dans Safari,
   * c'est demander l'impossible sans le dire.
   */
  private unsupportedMessage(): string {
    const iOS = /iP(hone|ad|od)/.test(navigator.userAgent);
    const standalone = window.matchMedia('(display-mode: standalone)').matches
      || (navigator as { standalone?: boolean }).standalone === true;
    if (iOS && !standalone) {
      return "Sur iPhone, ajoute d'abord Darilab à ton écran d'accueil (Partager → « Sur l'écran "
        + "d'accueil »), puis réessaie depuis l'application.";
    }
    return "Ce navigateur ne gère pas les notifications.";
  }

  /** Message d'un échec d'abonnement, selon ce que le navigateur a refusé. */
  private subscriptionErrorMessage(err: unknown): string {
    const name = (err as { name?: string })?.name;
    // `typeof` et non `Notification?.` : sur un navigateur sans l'API, l'identifiant n'existe
    // pas du tout et sa simple lecture lève une ReferenceError — que ce message est censé éviter.
    const denied = typeof Notification !== 'undefined' && Notification.permission === 'denied';
    if (name === 'NotAllowedError' || denied) {
      return 'Notifications bloquées pour ce site. Autorise-les dans les réglages de ton '
        + 'navigateur, puis réessaie.';
    }
    if (name === 'AbortError') {
      return this.unsupportedMessage();
    }
    return "Abonnement impossible sur cet appareil.";
  }

  /**
   * Appareils abonnés du compte, tous navigateurs confondus.
   *
   * <p>Il n'existait aucun moyen de savoir ce qui recevait ses notifications, ni d'en retirer un
   * seul : un téléphone revendu restait abonné jusqu'à ce que son navigateur réponde 410, ce qui
   * peut ne jamais arriver.</p>
   */
  devices(): Observable<PushDevice[]> {
    return this.http.get<PushDevice[]>(`${environment.apiUrl}/push/devices`);
  }

  removeDevice(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/push/devices/${id}`);
  }

  /**
   * Empreinte de l'abonnement de <b>cet</b> appareil, ou `null` s'il n'y en a pas.
   *
   * <p>Sert à reconnaître sa propre ligne dans la liste des appareils. Sans ça, retirer
   * l'appareil courant ne tiendrait pas : `watchSubscription` le ré-enregistre au démarrage dès
   * qu'un abonnement navigateur existe, et la ligne réapparaîtrait d'elle-même.</p>
   */
  async currentFingerprint(): Promise<string | null> {
    if (!this.swPush.isEnabled || !crypto?.subtle) return null;
    const sub = await firstValueFrom(this.swPush.subscription);
    if (!sub) return null;
    const bytes = new TextEncoder().encode(sub.endpoint);
    const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes));
    return [...digest.slice(0, 8)].map((b) => b.toString(16).padStart(2, '0')).join('');
  }

  /**
   * Défait l'abonnement de ce navigateur, sans prévenir le serveur — il vient précisément de
   * supprimer la ligne. Le faire dans l'autre sens laisserait l'appareil se croire abonné.
   */
  async forgetLocalSubscription(): Promise<void> {
    this.subscribed.set(false);
    try {
      if (this.swPush.isEnabled) await this.swPush.unsubscribe();
    } catch {
      // Aucun abonnement actif : rien à faire.
    }
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
