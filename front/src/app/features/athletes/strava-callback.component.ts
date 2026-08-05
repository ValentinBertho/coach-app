import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AthleteService } from '../../core/services/athlete.service';
import { AthletePortalService } from '../../core/services/athlete-portal.service';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { authErrorMessage } from '../../core/utils/auth-error';
import {
  clearPendingStravaAuth,
  readPendingStravaAuth,
  savePendingStravaAuth,
} from '../../core/services/strava-pending.storage';
import { IconComponent } from '../../shared/components/icon/icon.component';

type State = 'working' | 'done' | 'failed';

/**
 * Retour d'autorisation Strava : reçoit `?code` & `?state`, finalise la connexion.
 *
 * <p>Role-aware : un athlète connecte SA propre montre (portail `/me`), un coach finalise pour
 * l'athlète ciblé (`state` = athleteId). Une seule URL de redirection sert les deux flux — c'est
 * la seule que Strava accepte.</p>
 *
 * <p><b>Cet écran n'est derrière aucun garde de rôle, et c'est volontaire.</b> Il vivait sous
 * `/app`, réservé aux coachs : un athlète revenant de Strava était renvoyé par `coachGuard` vers
 * `/athlete/today` avant même que le code d'autorisation soit échangé. La connexion Strava d'un
 * athlète ne pouvait donc jamais aboutir depuis la PWA.</p>
 *
 * <p>Deuxième filet, pour le même parcours : sur iOS, une PWA installée délègue l'autorisation au
 * navigateur système, et la redirection retombe dans ce navigateur, où la session n'existe pas.
 * On met alors le code de côté ({@link savePendingStravaAuth}), on demande la connexion, et la
 * finalisation reprend toute seule juste après.</p>
 */
@Component({
  selector: 'app-strava-callback',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, IconComponent],
  template: `
    <div class="scb">
      <div class="card scb-card">
        <span class="scb-icon" [class.scb-icon--ko]="state() === 'failed'">
          <app-icon [name]="state() === 'failed' ? 'alert-triangle' : 'watch'" [size]="24" />
        </span>
        <h1 class="display-sm">Connexion Strava</h1>
        <p class="field-hint">{{ message() }}</p>

        @if (state() === 'failed') {
          <div class="scb-actions">
            @if (canRetry()) {
              <button type="button" class="btn btn-primary" (click)="retry()">Réessayer</button>
            }
            <a [routerLink]="backLink()" class="btn btn-ghost">Retour</a>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .scb { min-height: 100dvh; display: flex; align-items: center; justify-content: center; padding: var(--sp-4); }
    .scb-card {
      max-width: 420px; width: 100%; text-align: center;
      display: flex; flex-direction: column; align-items: center; gap: var(--sp-2);
    }
    .scb-card h1 { margin: 0; }
    .scb-icon {
      display: inline-flex; align-items: center; justify-content: center;
      width: 52px; height: 52px; border-radius: var(--radius-full);
      background: var(--primary-wash); color: var(--primary);
    }
    .scb-icon--ko { background: var(--warning-bg); color: var(--warning-text, var(--ink)); }
    .scb-actions { display: flex; gap: var(--sp-2); flex-wrap: wrap; justify-content: center; margin-top: var(--sp-2); }
  `],
})
export class StravaCallbackComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly athletes = inject(AthleteService);
  private readonly portal = inject(AthletePortalService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly state = signal<State>('working');
  readonly message = signal('Finalisation de la connexion…');

  /** Code + state courants, gardés pour un éventuel « Réessayer ». */
  private code: string | null = null;
  private authState: string | null = null;

  readonly canRetry = signal(false);

  backLink(): string {
    return this.auth.isAuthenticated() ? this.auth.homeRoute() : '/login';
  }

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    const error = params.get('error');

    // Reprise après reconnexion : le code a été mis de côté avant le détour par /login.
    const pending = readPendingStravaAuth();
    this.code = params.get('code') ?? pending?.code ?? null;
    this.authState = params.get('state') ?? pending?.state ?? null;

    if (error === 'access_denied') {
      this.fail("Autorisation refusée côté Strava. Tu peux relancer la connexion quand tu veux.", false);
      return;
    }
    if (error || !this.code || !this.authState) {
      this.fail('Connexion annulée ou lien incomplet. Relance la connexion depuis ton espace.', false);
      return;
    }

    // Session absente (retour dans un autre navigateur que la PWA, ou jeton expiré) : on garde
    // le code sous le coude et on repasse par la connexion — la finalisation reprend après.
    if (!this.auth.isAuthenticated()) {
      savePendingStravaAuth(this.code, this.authState);
      this.message.set('Connecte-toi pour finaliser la connexion Strava…');
      this.toast.info('Connecte-toi pour finaliser la connexion Strava.');
      this.router.navigate(['/login'], { replaceUrl: true });
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
    const code = this.code!;
    const state = this.authState!;

    // Flux athlète : je connecte ma propre montre.
    if (this.auth.currentUser()?.role === 'ATHLETE') {
      this.portal.stravaConnect(code, state).subscribe({
        // `?strava=connected` : l'écran d'accueil enchaîne sur un premier import, spinner visible
        // dans sa carte Strava, plutôt que de faire patienter ici sur un écran de transition.
        next: () => this.succeed('/athlete/today?strava=connected'),
        error: (err) => this.onConnectError(err),
      });
      return;
    }

    // Flux coach : finalisation pour l'athlète ciblé (le state porte son identifiant).
    const athleteId = state.split('.')[0];
    if (!athleteId) {
      this.fail('Connexion invalide (athlète manquant).', false);
      return;
    }
    this.athletes.stravaConnect(athleteId, code, state).subscribe({
      next: () => this.succeed(`/app/athletes/${athleteId}`),
      error: (err) => this.onConnectError(err),
    });
  }

  /**
   * Un 401 ici, c'est une session périmée (jeton non rafraîchissable) : l'intercepteur déconnecte
   * et renvoie vers la connexion. Le code d'autorisation, lui, est encore bon — on le garde pour
   * que la finalisation reprenne juste après, au lieu de faire recommencer tout le parcours.
   */
  private onConnectError(err: unknown): void {
    const status = (err as { status?: number })?.status;
    if (status === 401 && this.code && this.authState) {
      savePendingStravaAuth(this.code, this.authState);
      this.state.set('failed');
      this.canRetry.set(false);
      this.message.set('Session expirée — reconnecte-toi, la connexion Strava reprendra toute seule.');
      return;
    }
    // Un code d'autorisation ne s'échange qu'une fois : on ne propose « Réessayer » que si
    // l'échange n'a pas eu lieu (réseau, serveur indisponible), jamais sur un refus du serveur.
    this.fail(authErrorMessage(err), status === 0 || (status ?? 0) >= 500);
  }

  private succeed(target: string): void {
    clearPendingStravaAuth();
    this.state.set('done');
    this.message.set('Compte Strava connecté. Tes sorties vont arriver toutes seules.');
    this.toast.success('Compte Strava connecté');
    this.router.navigateByUrl(target, { replaceUrl: true });
  }

  /** @param retryable voir {@link onConnectError} : « Réessayer » n'a de sens que si le code n'a pas été consommé. */
  private fail(message: string, retryable: boolean): void {
    clearPendingStravaAuth();
    this.state.set('failed');
    this.canRetry.set(retryable);
    this.message.set(message);
    this.toast.error('Échec de la connexion Strava.');
  }
}
