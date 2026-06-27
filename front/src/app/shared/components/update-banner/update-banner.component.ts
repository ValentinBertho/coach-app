import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { UpdateService } from '../../../core/services/update.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Pastille discrète signalant qu'une nouvelle version est prête. La mise à jour
 * s'applique de toute façon automatiquement à la prochaine navigation ; ce bouton
 * permet de l'appliquer tout de suite.
 */
@Component({
  selector: 'app-update-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (update.updateAvailable()) {
      <div class="upd" role="status">
        <span class="upd-lb"><app-icon name="refresh-cw" [size]="15" /> Nouvelle version disponible</span>
        <button type="button" class="upd-btn" (click)="update.apply()">Mettre à jour</button>
      </div>
    }
  `,
  styles: [`
    .upd {
      position: fixed; z-index: 300;
      left: 50%; transform: translateX(-50%);
      bottom: calc(72px + env(safe-area-inset-bottom, 0px));
      display: flex; align-items: center; gap: var(--sp-3);
      max-width: calc(100vw - 2 * var(--sp-4));
      padding: var(--sp-2) var(--sp-2) var(--sp-2) var(--sp-4);
      background: var(--ink, #15182a); color: #fff;
      border-radius: var(--radius-pill, 999px);
      box-shadow: var(--shadow-lg, 0 10px 30px rgba(0,0,0,0.25));
      font-size: var(--text-sm); font-weight: 600;
      animation: updIn 220ms var(--ease, ease);
    }
    .upd-lb { display: inline-flex; align-items: center; gap: var(--sp-2); white-space: nowrap; }
    .upd-btn {
      border: none; cursor: pointer; white-space: nowrap;
      background: #fff; color: var(--ink, #15182a);
      padding: var(--sp-2) var(--sp-3); border-radius: var(--radius-pill, 999px);
      font-weight: 700; font-size: var(--text-sm);
    }
    .upd-btn:active { transform: scale(0.96); }
    @keyframes updIn { from { opacity: 0; transform: translate(-50%, 8px); } to { opacity: 1; transform: translate(-50%, 0); } }
  `],
})
export class UpdateBannerComponent {
  readonly update = inject(UpdateService);
}
