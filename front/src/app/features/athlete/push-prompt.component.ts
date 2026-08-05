import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { PushError, PushService } from '../../core/services/push.service';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent } from '../../shared/components/icon/icon.component';

/** Mémoire du refus : un athlète qui a dit non ne doit pas se le voir redemander chaque matin. */
const DISMISSED_KEY = 'darilab.push-prompt.dismissed';

/**
 * Invitation à activer les notifications, posée sur l'écran ouvert chaque matin.
 *
 * <p><b>Pourquoi ici.</b> Le bouton d'activation n'existait, côté athlète, que dans Profil →
 * un écran qu'on ouvre pour changer son mot de passe, c'est-à-dire presque jamais. Tout le
 * dispositif de notifications — rappel de séance, débriefing, message du coach — reposait donc
 * sur un opt-in que sa cible ne rencontrait pas. C'est le même raisonnement qui a fait remonter
 * la carte Strava ici : l'invitation doit croiser celui qui en a besoin.</p>
 *
 * <p><b>Pourquoi ce n'est pas un contrôle de plus.</b> L'en-tête de cet écran est délibérément
 * limité à deux boutons. Ceci n'en est pas un troisième : la carte disparaît dès que l'athlète
 * accepte — ou refuse —, et ne revient pas.</p>
 */
@Component({
  selector: 'app-push-prompt',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (visible()) {
      <section class="pprompt card">
        <span class="pprompt__ic"><app-icon name="bell" [size]="18" /></span>
        <div class="pprompt__txt">
          <strong>Être prévenu·e</strong>
          <span class="field-hint">
            La séance de demain, le retour de ton coach, ses messages — sur ton téléphone, sans
            ouvrir l'application.
          </span>
        </div>
        <div class="pprompt__act">
          <button type="button" class="btn btn-primary btn-sm" [disabled]="busy()" (click)="enable()">
            {{ busy() ? 'Activation…' : 'Activer' }}
          </button>
          <button type="button" class="btn btn-ghost btn-sm" (click)="dismiss()">Plus tard</button>
        </div>
      </section>
    }
  `,
  styles: [`
    .pprompt { display: flex; align-items: flex-start; gap: var(--sp-3); }
    .pprompt__ic {
      width: 36px; height: 36px; flex-shrink: 0; border-radius: var(--radius-sm);
      display: flex; align-items: center; justify-content: center;
      background: var(--primary-wash); color: var(--primary);
    }
    .pprompt__txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .pprompt__act { display: flex; flex-direction: column; gap: var(--sp-2); flex-shrink: 0; }
    @media (max-width: 480px) {
      .pprompt { flex-wrap: wrap; }
      .pprompt__act { flex-direction: row; width: 100%; }
    }
  `],
})
export class PushPromptComponent {
  private readonly push = inject(PushService);
  private readonly toast = inject(ToastService);

  readonly busy = signal(false);
  private readonly dismissed = signal(localStorage.getItem(DISMISSED_KEY) === '1');

  /** Rien à proposer si le navigateur ne sait pas, si c'est déjà fait, ou si on a déjà dit non. */
  visible(): boolean {
    return this.push.available && !this.push.subscribed() && !this.dismissed();
  }

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

  /** Refus mémorisé localement : le réglage reste accessible dans Profil. */
  dismiss(): void {
    localStorage.setItem(DISMISSED_KEY, '1');
    this.dismissed.set(true);
  }
}
