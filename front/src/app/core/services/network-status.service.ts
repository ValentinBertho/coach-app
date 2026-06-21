import { Injectable, NgZone, signal } from '@angular/core';
import { Subject } from 'rxjs';

/**
 * État de connectivité réseau. Expose un signal `online` et un flux `reconnected$`
 * (émis au retour du réseau) pour déclencher la synchronisation/rafraîchissement.
 */
@Injectable({ providedIn: 'root' })
export class NetworkStatusService {
  readonly online = signal<boolean>(navigator.onLine);
  readonly reconnected$ = new Subject<void>();

  constructor(zone: NgZone) {
    window.addEventListener('online', () =>
      zone.run(() => {
        this.online.set(true);
        this.reconnected$.next();
      })
    );
    window.addEventListener('offline', () => zone.run(() => this.online.set(false)));
  }
}
