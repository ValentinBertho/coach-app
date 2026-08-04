import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { PushService } from '../../../core/services/push.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Bouton d'activation des notifications push (affiché si le service worker est actif).
 *
 * <p>Il ne se montre que tant que <b>cet appareil</b> n'est pas abonné — état lu du navigateur,
 * pas d'un drapeau de session. Il disparaissait auparavant sur un simple booléen local, remis à
 * faux à chaque rechargement : la proposition revenait indéfiniment à qui l'avait déjà acceptée,
 * et un clic de plus ne changeait rien de visible.</p>
 */
@Component({
  selector: 'app-push-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (push.available && !push.subscribed()) {
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy()" (click)="enable()">
        <app-icon name="bell" [size]="15" /> {{ busy() ? 'Activation…' : 'Notifications' }}
      </button>
    }
  `,
})
export class PushButtonComponent {
  readonly push = inject(PushService);
  private readonly toast = inject(ToastService);
  readonly busy = signal(false);

  async enable(): Promise<void> {
    this.busy.set(true);
    try {
      const ok = await this.push.enable();
      if (ok) this.toast.success('Notifications activées sur cet appareil');
      else this.toast.info('Notifications indisponibles.');
    } catch {
      this.toast.error('Autorisation refusée.');
    } finally {
      this.busy.set(false);
    }
  }
}
