import { PLATFORM_ID, Injectable, NgZone, computed, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Subject } from 'rxjs';

/**
 * État de connectivité. Distingue deux pannes que l'utilisateur ne vit pas de la même façon :
 *
 * <ul>
 *   <li><b>Hors ligne</b> — l'appareil n'a plus de réseau (`navigator.onLine`). L'application
 *       fonctionne en mode dégradé, les retours sont mis en file et repartiront.</li>
 *   <li><b>API injoignable</b> — l'appareil est en ligne mais le serveur ne répond pas. C'est le
 *       cas d'un redéploiement (Railway redémarre l'instance une à deux minutes à chaque poussée
 *       sur `main`), et cela arrivera plusieurs fois par semaine pendant la bêta.</li>
 * </ul>
 *
 * <p>Le service ne regardait que `navigator.onLine`. Pendant un redéploiement, l'utilisateur est
 * donc « en ligne » : aucun bandeau, et à la place une série de toasts « Connexion impossible —
 * vérifie ton réseau » qui accusent son wifi d'une panne serveur. C'est la première chose que
 * verront les testeurs, et elle les enverra vérifier leur box.</p>
 */
@Injectable({ providedIn: 'root' })
export class NetworkStatusService {
  /**
   * Vrai côté navigateur, faux au pré-rendu.
   *
   * <p>`navigator` et `window` n'existent pas dans Node, où les fiches coachs sont fabriquées à la
   * compilation : sans cette garde, le service levait avant d'avoir rendu quoi que ce soit. On se
   * déclare en ligne — une page fabriquée hors ligne n'a pas de sens, et le bandeau de panne n'a
   * rien à faire dans un fichier statique servi à tout le monde.</p>
   */
  private readonly inBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  readonly online = signal<boolean>(this.inBrowser ? navigator.onLine : true);
  readonly reconnected$ = new Subject<void>();

  /**
   * Échecs réseau consécutifs signalés par l'intercepteur d'erreurs. Un échec isolé peut être une
   * requête annulée ou un incident ponctuel ; c'est la répétition qui caractérise une API à terre.
   */
  private readonly consecutiveApiFailures = signal(0);

  /** Seuil d'affichage : deux échecs d'affilée, pour ne pas clignoter sur un incident isolé. */
  private static readonly FAILURE_THRESHOLD = 2;

  /** L'appareil est en ligne mais le serveur ne répond plus. */
  readonly apiUnreachable = computed(
    () => this.online() && this.consecutiveApiFailures() >= NetworkStatusService.FAILURE_THRESHOLD,
  );

  constructor(zone: NgZone) {
    if (!this.inBrowser) {
      return;
    }
    window.addEventListener('online', () =>
      zone.run(() => {
        this.online.set(true);
        this.consecutiveApiFailures.set(0);
        this.reconnected$.next();
      })
    );
    window.addEventListener('offline', () => zone.run(() => this.online.set(false)));
  }

  /** Une requête a échoué sans réponse exploitable du serveur (statut 0, 502, 503, 504). */
  reportApiFailure(): void {
    this.consecutiveApiFailures.update(
      (n) => Math.min(n + 1, NetworkStatusService.FAILURE_THRESHOLD),
    );
  }

  /** Une requête a abouti : le serveur répond de nouveau, on repart de zéro. */
  reportApiSuccess(): void {
    if (this.consecutiveApiFailures() > 0) {
      this.consecutiveApiFailures.set(0);
      this.reconnected$.next();
    }
  }
}
