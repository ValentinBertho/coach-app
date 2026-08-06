import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { PushService, PushTestResult } from '../../../core/services/push.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Bouton « envoyer une notification test ».
 *
 * <p>Le push était le seul canal du produit qu'on ne pouvait pas vérifier. On autorise les
 * notifications, puis plus rien ne se passe pendant deux jours — et rien ne dit si c'est parce
 * qu'il n'y avait rien à annoncer, parce que l'abonnement a été révoqué par le navigateur, parce
 * que le serveur n'a pas ses clés, ou parce que le canal est coupé dans les réglages. Le support
 * n'avait pas plus d'information que l'utilisateur.</p>
 *
 * <p>Le bouton ne se contente donc pas d'envoyer : il rapporte ce que le serveur a trouvé. Un
 * essai qui part vers zéro appareil n'est pas un succès, et le dire est le seul intérêt de la
 * manœuvre.</p>
 */
@Component({
  selector: 'app-push-test-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <button type="button" class="btn btn-outline btn-sm" [disabled]="busy()" (click)="send()">
      <app-icon name="bell" [size]="15" />
      {{ busy() ? 'Envoi…' : 'Tester les notifications' }}
    </button>
  `,
})
export class PushTestButtonComponent {
  private readonly push = inject(PushService);
  private readonly toast = inject(ToastService);
  readonly busy = signal(false);

  send(): void {
    this.busy.set(true);
    this.push.sendTest().subscribe({
      next: (result) => { this.busy.set(false); this.report(result); },
      // Les routes push sont muettes côté intercepteur (cf. error.interceptor) : sans ce message,
      // un clic sur « Tester » ne produirait rien du tout — le pire résultat pour un bouton dont
      // tout l'objet est de lever un doute.
      error: () => {
        this.busy.set(false);
        this.toast.error("Envoi impossible — réessaie dans un instant.");
      },
    });
  }

  /** Chaque issue a son message : « envoyé » sans destinataire ne veut rien dire. */
  private report(result: PushTestResult): void {
    if (!result.enabled) {
      this.toast.error("Les notifications ne sont pas activées sur ce serveur.");
      return;
    }
    if (result.devices === 0) {
      this.toast.error("Aucun appareil abonné : active d'abord les notifications sur ce téléphone.");
      return;
    }
    const target = result.devices === 1 ? 'ton appareil' : `tes ${result.devices} appareils`;
    if (result.channelMuted) {
      this.toast.success(`Test envoyé à ${target}. Attention : le canal push est coupé dans tes `
        + 'réglages, les notifications de routine ne partent pas.');
      return;
    }
    this.toast.success(`Test envoyé à ${target} — il arrive en quelques secondes.`);
  }
}
