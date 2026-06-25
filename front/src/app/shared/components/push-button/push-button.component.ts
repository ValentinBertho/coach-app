import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { PushService } from '../../../core/services/push.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/** Bouton d'activation des notifications push (affiché si le SW est actif). */
@Component({
  selector: 'app-push-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (push.available && !done()) {
      <button type="button" class="btn btn-ghost btn-sm" [disabled]="busy()" (click)="enable()">
        <app-icon name="bell" [size]="15" /> Notifications
      </button>
    }
  `,
})
export class PushButtonComponent {
  readonly push = inject(PushService);
  private readonly toast = inject(ToastService);
  readonly busy = signal(false);
  readonly done = signal(false);

  async enable(): Promise<void> {
    this.busy.set(true);
    try {
      const ok = await this.push.enable();
      if (ok) { this.done.set(true); this.toast.success('Notifications activées 🔔'); }
      else this.toast.info('Notifications indisponibles.');
    } catch {
      this.toast.error('Autorisation refusée.');
    } finally {
      this.busy.set(false);
    }
  }
}
