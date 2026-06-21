import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NetworkStatusService } from '../../../core/services/network-status.service';

/** Bandeau discret affiché lorsque l'application est hors ligne. */
@Component({
  selector: 'app-offline-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!network.online()) {
      <div class="offline" role="status">
        <span class="dot"></span> Mode hors ligne — vos données seront synchronisées au retour du réseau.
      </div>
    }
  `,
  styles: [`
    .offline {
      display: flex; align-items: center; justify-content: center; gap: var(--sp-2);
      background: var(--warning-bg); color: var(--warning-text);
      font-size: var(--text-sm); font-weight: 600;
      padding: var(--sp-2) var(--sp-4);
    }
    .dot { width: 8px; height: 8px; border-radius: var(--radius-full); background: var(--warning); animation: pulseDot 1s infinite; }
  `],
})
export class OfflineBannerComponent {
  readonly network = inject(NetworkStatusService);
}
