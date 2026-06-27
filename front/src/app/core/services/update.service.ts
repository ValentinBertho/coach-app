import { Injectable, NgZone, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs/operators';

/**
 * Mise à jour automatique de la PWA.
 *
 * Le service worker Angular télécharge la nouvelle version en arrière-plan ; ce service
 * détecte qu'elle est **prête** (`VERSION_READY`) et l'**applique sans intervention** :
 * automatiquement à la prochaine navigation (point de rupture naturel), ou immédiatement
 * via la bannière « Mettre à jour ». Il vérifie aussi régulièrement la présence d'une
 * nouvelle version (démarrage, retour au premier plan, toutes les heures).
 *
 * No-op si le service worker est inactif (dev / navigateur non compatible).
 */
@Injectable({ providedIn: 'root' })
export class UpdateService {
  private readonly swUpdate = inject(SwUpdate);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);

  /** Une nouvelle version est téléchargée et prête à être activée. */
  readonly updateAvailable = signal(false);
  private applying = false;

  init(): void {
    if (!this.swUpdate.isEnabled) return;

    // Nouvelle version prête → on le signale (bannière) et on l'appliquera à la 1re navigation.
    this.swUpdate.versionUpdates
      .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
      .subscribe(() => this.updateAvailable.set(true));

    // Service worker dans un état irrécupérable → rechargement propre.
    this.swUpdate.unrecoverable.subscribe(() => this.reload());

    // Application automatique au prochain changement d'écran (sans couper l'action en cours).
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe(() => { if (this.updateAvailable()) void this.apply(); });

    // Détection : au démarrage, au retour de l'app au premier plan, puis toutes les heures.
    void this.checkForUpdate();
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') void this.checkForUpdate();
    });
    this.zone.runOutsideAngular(() => {
      setInterval(() => void this.checkForUpdate(), 60 * 60 * 1000);
    });
  }

  /** Interroge le serveur pour une nouvelle version (silencieux si hors ligne). */
  async checkForUpdate(): Promise<void> {
    if (!this.swUpdate.isEnabled) return;
    try { await this.swUpdate.checkForUpdate(); } catch { /* hors ligne / indisponible */ }
  }

  /** Active la nouvelle version et recharge l'application. */
  async apply(): Promise<void> {
    if (this.applying || !this.swUpdate.isEnabled) return;
    this.applying = true;
    try { await this.swUpdate.activateUpdate(); } catch { /* déjà activée */ }
    this.reload();
  }

  private reload(): void {
    document.location.reload();
  }
}
