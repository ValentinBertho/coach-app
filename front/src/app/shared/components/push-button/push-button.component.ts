import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { PushError, PushService } from '../../../core/services/push.service';
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

  /**
   * Un seul message, et le bon. L'activation en affichait deux — celui de l'intercepteur global
   * et « Autorisation refusée » quel que soit l'échec réel, y compris quand l'utilisateur venait
   * précisément d'autoriser. Les routes push sont désormais muettes côté intercepteur, et le
   * service décrit ce qui a échoué.
   */
  async enable(): Promise<void> {
    this.busy.set(true);
    try {
      await this.push.enable();
      this.toast.success('Notifications activées sur cet appareil');
    } catch (err) {
      this.toast.error(err instanceof PushError
        ? err.message
        : "Activation impossible — réessaie dans un instant.");
    } finally {
      this.busy.set(false);
    }
  }
}
