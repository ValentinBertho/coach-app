import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  input,
  signal,
} from '@angular/core';
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
          @case ('loading') {
            <p class="field-hint">Confirmation en cours…</p>
          }
          @case ('ok') {
            <span class="badge badge-success">E-mail confirmé</span>
            @if (requestSentTo(); as coach) {
              <!-- La demande écrite à l'inscription vient de partir : c'est ce que la personne
                   attendait de ce clic, bien plus que la confirmation elle-même. -->
              <p class="field-hint">
                Votre demande est partie chez <strong>{{ coach }}</strong>. Vous serez prévenu
                de sa réponse.
              </p>
              <a class="btn btn-primary btn-lg" routerLink="/athlete/demandes">Voir ma demande</a>
            } @else {
              <p class="field-hint">Ton adresse est vérifiée. Merci !</p>
              <a class="btn btn-primary btn-lg" [routerLink]="home()">Accéder à mon espace</a>
            }
          }
          @case ('invalid') {
            <span class="badge badge-danger">Lien invalide</span>
            <p class="field-hint">
              Ce lien de vérification est invalide ou expiré. Reconnecte-toi
              puis renvoie l'e-mail depuis le bandeau.
            </p>
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
  readonly requestSentTo = signal<string | null>(null);

  /** L'accueil du rôle réel : un athlète n'a rien à faire sous /app. */
  home(): string {
    return this.auth.isAuthenticated() ? this.auth.homeRoute() : '/login';
  }

  ngOnInit(): void {
    this.auth.verifyEmail(this.token()).subscribe({
      next: (res) => {
        this.requestSentTo.set(res.requestSentTo ?? null);
        this.auth.markEmailVerified();
        this.state.set('ok');
      },
      error: () => this.state.set('invalid'),
    });
  }
}
