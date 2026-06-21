import { Injectable, NgZone, signal } from '@angular/core';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

/**
 * Gère l'invite d'installation PWA (desktop + mobile). Capture beforeinstallprompt
 * et expose `canInstall` + `promptInstall()`.
 */
@Injectable({ providedIn: 'root' })
export class PwaInstallService {
  readonly canInstall = signal(false);
  private deferred: BeforeInstallPromptEvent | null = null;

  constructor(zone: NgZone) {
    window.addEventListener('beforeinstallprompt', (e: Event) => {
      e.preventDefault();
      this.deferred = e as BeforeInstallPromptEvent;
      zone.run(() => this.canInstall.set(true));
    });
    window.addEventListener('appinstalled', () =>
      zone.run(() => {
        this.canInstall.set(false);
        this.deferred = null;
      })
    );
  }

  async promptInstall(): Promise<void> {
    if (!this.deferred) return;
    await this.deferred.prompt();
    await this.deferred.userChoice;
    this.deferred = null;
    this.canInstall.set(false);
  }
}
