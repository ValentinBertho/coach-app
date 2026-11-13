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
  clearPendingGarminAuth,
  readPendingGarminAuth,
  savePendingGarminAuth,
} from '../../core/services/garmin-pending.storage';
import { IconComponent } from '../../shared/components/icon/icon.component';

type State = 'working' | 'done' | 'failed';

/**
 * Retour du consentement Garmin Connect : reçoit `?code` & `?state`, finalise la connexion.
 *
 * <p>Athlète uniquement : c'est lui qui relie son compte Garmin, jamais son coach. Comme pour
 * Strava, l'écran est hors de toute coquille et sans garde de rôle (un garde renverrait
 * l'athlète ailleurs avant l'échange du code), et le code est mis de côté si la session manque
 * — retour dans le navigateur système depuis une PWA iOS.</p>
 */
@Component({
  selector: 'app-garmin-callback',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent],
  template: `
    <div class="gcb">
      <div class="card gcb-card">
        <span class="gcb-icon" [class.gcb-icon--ko]="state() === 'failed'">
          <app-icon
            [name]="state() === 'failed' ? 'alert-triangle' : 'watch'"
            [size]="24"
          />
        </span>
        <h1 class="display-sm">Connexion Garmin</h1>
        <p class="field-hint">{{ message() }}</p>

        @if (state() === 'failed') {
          <div class="gcb-actions">
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
      .gcb {
        min-height: 100dvh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: var(--sp-4);
      }
      .gcb-card {
        max-width: 420px;
        width: 100%;
        text-align: center;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--sp-2);
      }
      .gcb-card h1 {
        margin: 0;
      }
      .gcb-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 52px;
        height: 52px;
        border-radius: var(--radius-full);
        background: var(--primary-wash);
        color: var(--primary);
      }
      .gcb-icon--ko {
        background: var(--warning-bg);
        color: var(--warning-text, var(--ink));
      }
      .gcb-actions {
        display: flex;
        gap: var(--sp-2);
        flex-wrap: wrap;
        justify-content: center;
        margin-top: var(--sp-2);
      }
    `,
  ],
})
export class GarminCallbackComponent implements OnInit {
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
    const pending = readPendingGarminAuth();
    this.code = params.get('code') ?? pending?.code ?? null;
    this.authState = params.get('state') ?? pending?.state ?? null;

    if (params.get('error') || !this.code || !this.authState) {
      this.fail(
        'Connexion annulée côté Garmin. Tu peux la relancer depuis Synchronisation.',
        false,
      );
      return;
    }

    if (!this.auth.isAuthenticated()) {
      savePendingGarminAuth(this.code, this.authState);
      this.message.set('Connecte-toi pour finaliser la connexion Garmin…');
      this.toast.info('Connecte-toi pour finaliser la connexion Garmin.');
      this.router.navigate(['/login'], { replaceUrl: true });
      return;
    }

    if (this.auth.currentUser()?.role !== 'ATHLETE') {
      this.fail(
        "Seul l'athlète peut relier son propre compte Garmin, depuis son espace.",
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
    this.portal.garminConnect(this.code!, this.authState!).subscribe({
      next: () => {
        clearPendingGarminAuth();
        this.state.set('done');
        this.message.set('Compte Garmin connecté.');
        this.toast.success('Compte Garmin connecté');
        this.router.navigateByUrl('/athlete/sync', { replaceUrl: true });
      },
      error: (err) => {
        const status = (err as { status?: number })?.status;
        if (status === 401 && this.code && this.authState) {
          savePendingGarminAuth(this.code, this.authState);
          this.state.set('failed');
          this.canRetry.set(false);
          this.message.set(
            'Session expirée — reconnecte-toi, la connexion Garmin reprendra toute seule.',
          );
          return;
        }
        // Un code ne s'échange qu'une fois : « Réessayer » seulement si l'échange n'a pas eu lieu.
        this.fail(authErrorMessage(err), status === 0 || (status ?? 0) >= 500);
      },
    });
  }

  private fail(message: string, retryable: boolean): void {
    clearPendingGarminAuth();
    this.state.set('failed');
    this.canRetry.set(retryable);
    this.message.set(message);
    this.toast.error('Échec de la connexion Garmin.');
  }
}
