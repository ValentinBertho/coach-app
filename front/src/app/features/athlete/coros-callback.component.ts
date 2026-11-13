import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { authErrorMessage } from '../../core/utils/auth-error';
import {
  clearPendingCorosAuth,
  readPendingCorosAuth,
  savePendingCorosAuth,
} from '../../core/services/coros-pending.storage';
import { IconComponent } from '../../shared/components/icon/icon.component';

type State = 'working' | 'done' | 'failed';

/**
 * Retour du consentement COROS : reçoit `?code` & `?state`, finalise la connexion.
 *
 * <p>Athlète uniquement : c'est lui qui relie son compte COROS, jamais son coach. Comme pour
 * Strava, l'écran est hors de toute coquille et sans garde de rôle (un garde renverrait
 * l'athlète ailleurs avant l'échange du code), et le code est mis de côté si la session manque
 * — retour dans le navigateur système depuis une PWA iOS.</p>
 */
@Component({
  selector: 'app-coros-callback',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent],
  template: `
    <div class="ccb">
      <div class="card ccb-card">
        <span class="ccb-icon" [class.ccb-icon--ko]="state() === 'failed'">
          <app-icon
            [name]="state() === 'failed' ? 'alert-triangle' : 'watch'"
            [size]="24"
          />
        </span>
        <h1 class="display-sm">Connexion COROS</h1>
        <p class="field-hint">{{ message() }}</p>

        @if (state() === 'failed') {
          <div class="ccb-actions">
            @if (canRetry()) {
              <button type="button" class="btn btn-primary" (click)="retry()">
                Réessayer
              </button>
            }
            <a [routerLink]="backLink()" class="btn btn-ghost">Retour</a>
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .ccb {
        min-height: 100dvh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: var(--sp-4);
      }
      .ccb-card {
        max-width: 420px;
        width: 100%;
        text-align: center;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--sp-2);
      }
      .ccb-card h1 {
        margin: 0;
      }
      .ccb-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 52px;
        height: 52px;
        border-radius: var(--radius-full);
        background: var(--primary-wash);
        color: var(--primary);
      }
      .ccb-icon--ko {
        background: var(--warning-bg);
        color: var(--warning-text, var(--ink));
      }
      .ccb-actions {
        display: flex;
        gap: var(--sp-2);
        flex-wrap: wrap;
        justify-content: center;
        margin-top: var(--sp-2);
      }
    `,
  ],
})
export class CorosCallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly state = signal<State>('working');
  readonly message = signal('Finalisation de la connexion…');
  readonly canRetry = signal(false);

  private code: string | null = null;
  private authState: string | null = null;

  backLink(): string {
    return this.auth.isAuthenticated() ? '/athlete/sync' : '/login';
  }

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const pending = readPendingCorosAuth();
    this.code = params.get('code') ?? pending?.code ?? null;
    this.authState = params.get('state') ?? pending?.state ?? null;

    if (params.get('error') || !this.code || !this.authState) {
      this.fail(
        'Connexion annulée côté COROS. Tu peux la relancer depuis Synchronisation.',
        false,
      );
      return;
    }

    if (!this.auth.isAuthenticated()) {
      savePendingCorosAuth(this.code, this.authState);
      this.message.set('Connecte-toi pour finaliser la connexion COROS…');
      this.toast.info('Connecte-toi pour finaliser la connexion COROS.');
      this.router.navigate(['/login'], { replaceUrl: true });
      return;
    }

    if (this.auth.currentUser()?.role !== 'ATHLETE') {
      this.fail(
        "Seul l'athlète peut relier son propre compte COROS, depuis son espace.",
        false,
      );
      return;
    }

    this.finalize();
  }

  retry(): void {
    this.state.set('working');
    this.message.set('Finalisation de la connexion…');
    this.finalize();
  }

  private finalize(): void {
    this.portal.corosConnect(this.code!, this.authState!).subscribe({
      next: () => {
        clearPendingCorosAuth();
        this.state.set('done');
        this.message.set('Compte COROS connecté.');
        this.toast.success('Compte COROS connecté');
        this.router.navigateByUrl('/athlete/sync', { replaceUrl: true });
      },
      error: (err) => {
        const status = (err as { status?: number })?.status;
        if (status === 401 && this.code && this.authState) {
          savePendingCorosAuth(this.code, this.authState);
          this.state.set('failed');
          this.canRetry.set(false);
          this.message.set(
            'Session expirée — reconnecte-toi, la connexion COROS reprendra toute seule.',
          );
          return;
        }
        // Un code ne s'échange qu'une fois : « Réessayer » seulement si l'échange n'a pas eu lieu.
        this.fail(authErrorMessage(err), status === 0 || (status ?? 0) >= 500);
      },
    });
  }

  private fail(message: string, retryable: boolean): void {
    clearPendingCorosAuth();
    this.state.set('failed');
    this.canRetry.set(retryable);
    this.message.set(message);
    this.toast.error('Échec de la connexion COROS.');
  }
}
