import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/services/auth.service';
import { PushError, PushService } from '../../../core/services/push.service';
import { ToastService } from '../../../core/services/toast.service';
import { isInstalledApp } from '../../../core/utils/installed-app';
import { IconComponent } from '../icon/icon.component';

/** Mémoire de la question posée : on ne la repose pas, quelle qu'ait été la réponse. */
const ASKED_KEY = 'darilab.push-onboarding.asked';

/**
 * Délai avant l'apparition, au premier lancement.
 *
 * <p>Deux raisons, et la seconde est la vraie. D'abord laisser voir l'application avant de
 * demander quoi que ce soit. Ensuite et surtout : l'état d'abonnement est <b>asynchrone</b> — il
 * vient de {@code swPush.subscription}, et vaut « non abonné » pendant les premiers instants. Sans
 * cette attente, un appareil déjà abonné verrait la fenêtre s'afficher puis disparaître.</p>
 */
const APPEAR_DELAY_MS = 1_500;

/**
 * Invitation à activer les notifications au <b>premier lancement de l'application installée</b>,
 * comme le fait une application native.
 *
 * <h2>Pourquoi une fenêtre, et pourquoi seulement installée</h2>
 *
 * <p>L'opt-in existait déjà à deux endroits : une carte dans « Aujourd'hui » (athlète seulement)
 * et un bouton dans les réglages. Les deux supposent qu'on passe par un écran précis — or celui
 * qui vient d'installer l'application ne connaît encore aucun écran. Le dispositif tout entier
 * (rappel de séance, message du coach, débriefing) reposait donc sur une autorisation que sa
 * cible pouvait ne jamais croiser.</p>
 *
 * <p>Réservée à l'application <b>installée</b>, délibérément. Sur iOS, le push n'existe que là :
 * proposer autre chose serait promettre l'impossible. Et sur le web ouvert, une demande
 * d'autorisation à la première visite est le geste que les navigateurs sanctionnent et que les
 * visiteurs refusent par réflexe — la carte et les réglages restent la bonne porte.</p>
 *
 * <p><b>Ce n'est pas la demande du navigateur.</b> C'est une fenêtre applicative qui explique
 * d'abord, et n'appelle l'autorisation système qu'au clic sur « Activer ». Cet ordre-là compte :
 * un refus navigateur est définitif et ne se rattrape que dans les réglages du téléphone, donc on
 * ne le déclenche jamais sans que la personne ait compris ce qu'on lui propose.</p>
 */
@Component({
  selector: 'app-push-onboarding',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (visible()) {
      <div class="po-backdrop" role="presentation">
        <section class="po card" role="dialog" aria-modal="true" aria-labelledby="po-title">
          <span class="po__ic"><app-icon name="bell" [size]="26" /></span>
          <h2 id="po-title">Ne rien manquer</h2>
          <p class="po__txt">{{ pitch() }}</p>
          <div class="po__act">
            <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="enable()">
              {{ busy() ? 'Activation…' : 'Activer les notifications' }}
            </button>
            <button type="button" class="btn btn-ghost" [disabled]="busy()" (click)="later()">
              Plus tard
            </button>
          </div>
          <p class="po__foot field-hint">Modifiable à tout moment dans tes réglages.</p>
        </section>
      </div>
    }
  `,
  styles: [`
    .po-backdrop {
      position: fixed; inset: 0; z-index: 1200;
      background: var(--bg-overlay, rgba(0, 0, 0, 0.5));
      display: flex; align-items: center; justify-content: center;
      padding: var(--sp-4);
      animation: fadeIn var(--duration) var(--ease);
    }
    .po {
      width: 100%; max-width: 380px; text-align: center;
      display: flex; flex-direction: column; align-items: center; gap: var(--sp-3);
      animation: slideInUp var(--duration) var(--ease);
      /* Sous la barre d'état et au-dessus de la barre d'onglets : la fenêtre est centrée, mais
         sur un petit écran elle doit rester entièrement atteignable au pouce. */
      margin-block: max(var(--sp-4), env(safe-area-inset-top, 0px)) var(--sp-4);
    }
    .po__ic {
      width: 56px; height: 56px; border-radius: var(--radius-full);
      display: flex; align-items: center; justify-content: center;
      background: var(--primary-wash); color: var(--primary);
    }
    .po h2 { margin: 0; }
    .po__txt { margin: 0; color: var(--ink-2); }
    .po__act { display: flex; flex-direction: column; gap: var(--sp-2); width: 100%; }
    .po__act .btn { width: 100%; min-height: 46px; }
    .po__foot { margin: 0; }
  `],
})
export class PushOnboardingComponent implements OnInit {
  private readonly push = inject(PushService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly busy = signal(false);
  private readonly asked = signal(localStorage.getItem(ASKED_KEY) === '1');
  /** Passe à vrai après le délai d'apparition : rien ne s'affiche avant. */
  private readonly ready = signal(false);

  ngOnInit(): void {
    // Une seule fois, et seulement si la question a un sens sur cet appareil.
    if (this.asked() || !this.push.available || !isInstalledApp()) {
      return;
    }
    const timer = setTimeout(() => this.ready.set(true), APPEAR_DELAY_MS);
    this.destroyRef.onDestroy(() => clearTimeout(timer));
  }

  /**
   * Quatre conditions, toutes nécessaires : une session ouverte (sans compte, il n'y a personne à
   * qui rattacher l'abonnement), un appareil capable, pas déjà abonné, et la question jamais posée.
   */
  readonly visible = computed(() =>
    this.ready()
    && !this.asked()
    && this.auth.isAuthenticated()
    && this.push.available
    && !this.push.subscribed());

  /** Ce que la personne va réellement recevoir, selon ce qu'elle fait dans le produit. */
  readonly pitch = computed(() => this.auth.isCoach()
    ? 'Les retours de tes athlètes, leurs messages et les alertes de charge — sur ton téléphone, '
      + 'sans ouvrir l’application.'
    : 'La séance de demain, le retour de ton coach, ses messages — sur ton téléphone, sans ouvrir '
      + 'l’application.');

  async enable(): Promise<void> {
    this.busy.set(true);
    try {
      await this.push.enable();
      this.toast.success('Notifications activées sur cet appareil');
    } catch (err) {
      this.toast.error(err instanceof PushError
        ? err.message
        : 'Activation impossible — réessaie dans un instant.');
    } finally {
      this.busy.set(false);
      // Posée une fois, quelle qu'ait été l'issue : une invitation qui revient est une nuisance,
      // et le réglage reste accessible dans les écrans dédiés.
      this.remember();
    }
  }

  later(): void {
    this.remember();
  }

  private remember(): void {
    localStorage.setItem(ASKED_KEY, '1');
    this.asked.set(true);
  }
}
