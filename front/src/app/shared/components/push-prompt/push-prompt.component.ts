import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { PushError, PushService } from '../../../core/services/push.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/** Mémoire du refus : qui a dit non ne doit pas se le voir redemander chaque matin. */
const DISMISSED_KEY = 'darilab.push-prompt.dismissed';

/**
 * Invitation à activer les notifications, posée sur l'écran ouvert chaque matin.
 *
 * <p><b>Pourquoi elle existe.</b> Le bouton d'activation ne vivait que dans un écran de réglages
 * — qu'on ouvre pour changer son mot de passe, c'est-à-dire presque jamais. Tout le dispositif de
 * notifications reposait donc sur un opt-in que sa cible ne rencontrait pas.</p>
 *
 * <p><b>Pourquoi elle est partagée.</b> Elle a d'abord servi l'athlète. Le coach avait exactement
 * le même trou, en plus coûteux : ses notifications à lui portent les blessures déclarées et les
 * séances manquées, et il n'avait qu'un bouton texte noyé dans une barre d'en-tête. Le besoin est
 * le même, le composant aussi ; seul le texte change, d'où {@link lead}.</p>
 *
 * <p><b>Pourquoi ce n'est pas un contrôle de plus.</b> La carte disparaît dès qu'on accepte — ou
 * qu'on refuse —, et ne revient pas.</p>
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
          <strong>{{ title() }}</strong>
          <span class="field-hint">{{ lead() }}</span>
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
  /** Titre de la carte. Par défaut celui de l'athlète, à qui elle s'adresse d'abord. */
  readonly title = input('Être prévenu·e');

  /** Ce que l'activation apporte, dans les mots de celui qui la lit. */
  readonly lead = input(
    "La séance de demain, le retour de ton coach, ses messages — sur ton téléphone, sans "
    + "ouvrir l'application.");

  private readonly push = inject(PushService);
  private readonly toast = inject(ToastService);

  readonly busy = signal(false);
  private readonly dismissed = signal(read(DISMISSED_KEY));

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

  /** Refus mémorisé localement : le réglage reste accessible dans les paramètres. */
  dismiss(): void {
    try { localStorage.setItem(DISMISSED_KEY, '1'); } catch { /* stockage indisponible */ }
    this.dismissed.set(true);
  }
}

function read(key: string): boolean {
  try { return localStorage.getItem(key) === '1'; } catch { return false; }
}
