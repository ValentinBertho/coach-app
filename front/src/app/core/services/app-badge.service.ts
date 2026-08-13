import { Injectable } from '@angular/core';

/**
 * Ce que le navigateur expose quand il sait poser une pastille sur l'icône de l'application.
 *
 * <p>Déclaré à part plutôt qu'en extension de {@code Navigator} : selon la version de la
 * bibliothèque TypeScript, ces méthodes sont absentes <b>ou</b> déclarées obligatoires — et
 * l'extension échoue alors à les rendre optionnelles, ce qu'elles sont pourtant en réalité.</p>
 */
interface BadgeCapableNavigator {
  setAppBadge?: (count?: number) => Promise<void>;
  clearAppBadge?: () => Promise<void>;
}

/**
 * Pastille de l'icône de l'application (Badging API).
 *
 * <p><b>Pourquoi.</b> Le produit compte déjà ce qui attend le coach — retours non traités,
 * messages non lus — et l'affiche dans sa navigation. Mais cette information ne vaut que
 * l'application ouverte : sur un écran d'accueil, l'icône ne disait rien, et il fallait ouvrir
 * pour savoir s'il y avait quelque chose à ouvrir. C'est précisément ce qu'une pastille évite.</p>
 *
 * <p><b>Portée.</b> Android et les navigateurs de bureau la posent sur l'icône ; iOS le fait
 * depuis 16.4, <b>en application installée uniquement</b>. Partout ailleurs les méthodes
 * n'existent pas : l'appel est alors sans effet, ce qui est le bon comportement — jamais
 * d'erreur, jamais de message, la fonctionnalité est simplement absente.</p>
 */
@Injectable({ providedIn: 'root' })
export class AppBadgeService {

  /**
   * Pose la pastille, ou la retire quand il n'y a plus rien à traiter.
   *
   * <p>Le retrait compte autant que la pose : une pastille qui reste après que le coach a vidé
   * sa file ment sur l'état du produit, et on cesse très vite de croire une pastille qui ment.</p>
   */
  set(count: number): void {
    const nav = navigator as unknown as BadgeCapableNavigator;
    try {
      const call = count > 0 ? nav.setAppBadge?.(count) : nav.clearAppBadge?.();
      // La promesse échoue si la permission manque (certains navigateurs de bureau) : sans
      // capture, ce serait un rejet non traité remonté à Sentry pour un ornement d'icône.
      void call?.catch(() => undefined);
    } catch {
      // Navigateur sans l'API : rien à faire, et rien à dire.
    }
  }

  /** Retire la pastille — déconnexion, ou changement de compte sur un téléphone partagé. */
  clear(): void {
    this.set(0);
  }
}
