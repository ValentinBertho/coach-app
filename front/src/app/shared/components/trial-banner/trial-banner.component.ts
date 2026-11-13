import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ToastService } from '../../../core/services/toast.service';
import { IconComponent } from '../icon/icon.component';

/**
 * Bandeau des espaces d'essai (« Essayer sans inscription »), sur tous les écrans.
 *
 * <p>Il dit trois choses : ce n'est pas un vrai compte (tout part au bout de 24 heures), on peut
 * voir l'autre côté de l'application (celui de l'athlète, ou revenir à celui du coach), et on peut
 * créer son vrai espace quand on a vu ce qu'on voulait voir.</p>
 */
@Component({
  selector: 'app-trial-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (auth.isTrial()) {
      <div class="trial" role="status">
        <app-icon name="play" [size]="16" />
        <span class="trial__txt">
          <strong>Espace d'essai.</strong>
          Rien que pour vous, effacé dans les 24 heures.
        </span>
        <span class="trial__actions">
          <button type="button" class="trial__btn" [disabled]="switching()" (click)="switchSide()">
            {{ switchLabel() }}
          </button>
          <button type="button" class="trial__btn trial__btn--main" (click)="createAccount()">
            Créer mon vrai espace
          </button>
        </span>
      </div>
    }
  `,
  styles: [
    `
      .trial {
        position: sticky;
        top: 0;
        z-index: 1190;
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: var(--sp-2);
        padding: var(--sp-2) var(--sp-3);
        padding-top: max(var(--sp-2), var(--safe-top, 0px));
        background: #0a4f58;
        color: #fff;
        font-size: var(--text-sm);
      }
      .trial__txt {
        flex: 1;
        min-width: 12rem;
      }
      .trial__actions {
        display: flex;
        gap: var(--sp-2);
        flex-wrap: wrap;
      }
      .trial__btn {
        min-height: 36px;
        padding: 0 var(--sp-3);
        border: 1px solid rgba(255, 255, 255, 0.6);
        border-radius: var(--radius-full);
        background: transparent;
        color: #fff;
        font: inherit;
        font-weight: 700;
        cursor: pointer;
      }
      .trial__btn--main {
        background: #fff;
        color: #0a4f58;
        border-color: #fff;
      }
      .trial__btn:disabled {
        opacity: 0.6;
        cursor: progress;
      }
    `,
  ],
})
export class TrialBannerComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly switching = signal(false);
  protected readonly switchLabel = computed(() =>
    this.auth.currentUser()?.role === 'ATHLETE' ? 'Revenir côté coach' : 'Voir côté athlète');

  switchSide(): void {
    this.switching.set(true);
    this.auth.switchTrialSide().subscribe({
      next: () => {
        this.switching.set(false);
        void this.router.navigateByUrl(this.auth.homeRoute());
      },
      error: () => {
        this.switching.set(false);
        this.toast.error("Impossible de changer de côté pour le moment.");
      },
    });
  }

  /** On quitte l'essai (il s'effacera seul) pour l'inscription, sans rien emporter. */
  createAccount(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/register');
  }
}
