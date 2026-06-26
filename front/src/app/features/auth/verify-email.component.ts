import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

type State = 'loading' | 'ok' | 'invalid';

/**
 * Confirmation d'adresse e-mail à partir du lien d'inscription. Valide le jeton et met à jour
 * l'état local (si le coach est connecté), puis propose de rejoindre son espace.
 */
@Component({
  selector: 'app-verify-email',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LogoComponent],
  template: `
    <main class="auth-page">
      <div class="card auth-card">
        <header class="auth-head">
          <app-logo [size]="44" [showText]="false" />
          <h1 class="display-sm">Vérification de l'e-mail</h1>
        </header>
        @switch (state()) {
          @case ('loading') { <p class="field-hint">Confirmation en cours…</p> }
          @case ('ok') {
            <span class="badge badge-success">E-mail confirmé</span>
            <p class="field-hint">Votre adresse est vérifiée. Merci !</p>
            <a class="btn btn-primary btn-lg" routerLink="/app">Accéder à mon espace</a>
          }
          @case ('invalid') {
            <span class="badge badge-danger">Lien invalide</span>
            <p class="field-hint">Ce lien de vérification est invalide ou expiré. Reconnectez-vous puis renvoyez l'e-mail depuis le bandeau.</p>
            <a class="btn btn-ghost btn-lg" routerLink="/login">Se connecter</a>
          }
        }
      </div>
    </main>
  `,
  styleUrl: './auth.scss',
})
export class VerifyEmailComponent implements OnInit {
  readonly token = input.required<string>();
  private readonly auth = inject(AuthService);

  readonly state = signal<State>('loading');

  ngOnInit(): void {
    this.auth.verifyEmail(this.token()).subscribe({
      next: () => { this.auth.markEmailVerified(); this.state.set('ok'); },
      error: () => this.state.set('invalid'),
    });
  }
}
