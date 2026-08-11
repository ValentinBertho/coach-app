import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Bandeau permanent pendant une impersonation : « tu regardes l'application en tant que X ».
 *
 * <p><b>Pourquoi il est permanent, et pourquoi il est laid.</b> Une session empruntée qui ne se
 * voit pas est un piège : au bout de deux écrans on oublie, et on finit par écrire dans le compte
 * de quelqu'un d'autre en croyant être chez soi — un commentaire de coach, un ressenti, une séance
 * supprimée. Le bandeau ne se referme donc pas, il est visible sur tous les écrans, et il porte la
 * seule couleur d'alerte du produit. C'est le prix d'un accès qui n'est pas le sien.</p>
 *
 * <p>Il porte aussi la sortie : revenir à son compte doit être à un tap de n'importe où, sinon la
 * tentation est de rester.</p>
 */
@Component({
  selector: 'app-impersonation-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (auth.isImpersonating()) {
      <div class="imp" role="status">
        <app-icon name="eye" [size]="16" />
        <span class="imp__txt">
          Tu vois l'application en tant que <strong>{{ label() }}</strong>.
        </span>
        <button type="button" class="imp__back" (click)="back()">Revenir à mon compte</button>
      </div>
    }
  `,
  styles: [`
    .imp {
      position: sticky; top: 0; z-index: 1200;
      display: flex; align-items: center; gap: var(--sp-2);
      padding: var(--sp-2) var(--sp-3);
      padding-top: max(var(--sp-2), var(--safe-top, 0px));
      background: var(--form-red); color: #fff;
      font-size: var(--text-sm); font-weight: 700;
    }
    .imp__txt { flex: 1; min-width: 0; }
    .imp__back {
      flex-shrink: 0; min-height: 36px; padding: 0 var(--sp-3);
      border: 1px solid rgba(255, 255, 255, 0.6); border-radius: var(--radius-full);
      background: transparent; color: #fff; font-weight: 700; font-size: var(--text-sm);
      cursor: pointer;
    }
    .imp__back:active { transform: scale(0.96); }
  `],
})
export class ImpersonationBannerComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Nom du compte emprunté, ou son e-mail si le profil n'a pas de nom complet. */
  protected label(): string {
    const user = this.auth.currentUser();
    return user?.fullName?.trim() || user?.email || 'un autre compte';
  }

  protected back(): void {
    if (this.auth.stopImpersonation()) {
      this.toast.success('Retour à ton compte.');
      void this.router.navigateByUrl(this.auth.homeRoute());
      return;
    }
    // Rien à restaurer : la session de l'administrateur a été purgée entre-temps (autre onglet,
    // stockage vidé). Mieux vaut le renvoyer se connecter que le laisser sur un compte emprunté.
    this.toast.warning('Session administrateur introuvable — reconnecte-toi.');
    void this.router.navigateByUrl('/login');
  }
}
