import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NetworkStatusService } from '../../../core/services/network-status.service';

/**
 * Bandeau d'état de connectivité. Deux messages distincts, parce que ce sont deux situations
 * distinctes et que la conduite à tenir n'est pas la même :
 *
 * <ul>
 *   <li><b>hors ligne</b> — c'est l'appareil ; l'application continue en mode dégradé ;</li>
 *   <li><b>service indisponible</b> — c'est le serveur ; il n'y a rien à faire côté utilisateur,
 *       et surtout rien à vérifier sur sa box. Le cas se produit à chaque redéploiement, une à
 *       deux minutes, plusieurs fois par semaine pendant la bêta. Sans ce message, l'utilisateur
 *       recevait une pluie de toasts « vérifie ton réseau » pour une panne qui n'est pas la
 *       sienne.</li>
 * </ul>
 */
@Component({
  selector: 'app-offline-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (!network.online()) {
      <div class="banner offline" role="status">
        <span class="dot"></span> Mode hors ligne — tes données seront synchronisées au retour du réseau.
      </div>
    } @else if (network.apiUnreachable()) {
      <div class="banner unreachable" role="status">
        <span class="dot"></span> Service momentanément indisponible — une mise à jour est
        probablement en cours. Tes données sont intactes, réessaie dans une minute.
      </div>
    }
  `,
  styles: [`
    .banner {
      display: flex; align-items: center; justify-content: center; gap: var(--sp-2);
      font-size: var(--text-sm); font-weight: 600;
      padding: var(--sp-2) var(--sp-4);
      text-align: center;
    }
    .offline { background: var(--warning-bg); color: var(--warning-text); }
    .offline .dot { background: var(--warning); }
    .unreachable { background: var(--danger-bg); color: var(--danger-text); }
    .unreachable .dot { background: var(--danger); }
    .dot { width: 8px; height: 8px; border-radius: var(--radius-full); flex-shrink: 0; animation: pulseDot 1s infinite; }
  `],
})
export class OfflineBannerComponent {
  readonly network = inject(NetworkStatusService);
}
