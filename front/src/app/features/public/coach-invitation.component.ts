import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

interface CoachInvitationInfo {
  email: string;
  fullName: string;
  clubName: string | null;
}

type State = 'loading' | 'ok' | 'invalid';

/**
 * Page publique d'invitation coach (lien magique) : le coach définit son mot de passe,
 * ce qui active son compte et ouvre l'espace coach.
 */
@Component({
  selector: 'app-coach-invitation',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, LogoComponent],
  template: `
    <main class="invite-page">
      <div class="card invite-card">
        @switch (state()) {
          @case ('loading') { <p>Vérification de l'invitation…</p> }
          @case ('ok') {
            <app-logo [size]="44" [showText]="false" />
            <span class="badge badge-info">{{ info()?.clubName }}</span>
            <h1 class="display-sm">Bienvenue {{ info()?.fullName }}</h1>
            <p class="field-hint">
              Vous êtes invité·e à rejoindre <strong>{{ info()?.clubName }}</strong> comme coach.
              Choisissez un mot de passe pour activer votre compte ({{ info()?.email }}).
            </p>
            <form class="form" (ngSubmit)="accept()">
              <input type="password" class="form-control" name="pwd" placeholder="Mot de passe (8 caractères min.)"
                     [(ngModel)]="password" minlength="8" required autocomplete="new-password" />
              <button type="submit" class="btn btn-primary btn-lg" [disabled]="joining() || password.length < 8">
                {{ joining() ? 'Activation…' : 'Activer mon compte' }}
              </button>
            </form>
            @if (error()) { <p class="field-hint err">{{ error() }}</p> }
          }
          @case ('invalid') {
            <span class="badge badge-danger">Lien invalide</span>
            <h1 class="display-sm">Invitation expirée</h1>
            <p class="field-hint">Ce lien d'invitation n'est plus valide. Demandez-en un nouveau au club.</p>
          }
        }
      </div>
    </main>
  `,
  styles: [`
    .invite-page { min-height: 100dvh; display: flex; align-items: center; justify-content: center; padding: var(--sp-4); background-color: var(--night); background-image: var(--mesh); }
    .invite-card { max-width: 440px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: var(--sp-3); border-radius: var(--radius-xl); box-shadow: var(--shadow-lg); }
    .form { display: flex; flex-direction: column; gap: var(--sp-2); width: 100%; }
    .btn-lg { width: 100%; }
    .err { color: var(--danger-text); }
  `],
})
export class CoachInvitationComponent implements OnInit {
  readonly token = input.required<string>();
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly state = signal<State>('loading');
  readonly info = signal<CoachInvitationInfo | null>(null);
  readonly joining = signal(false);
  readonly error = signal('');
  password = '';

  ngOnInit(): void {
    this.http
      .get<CoachInvitationInfo>(`${environment.apiUrl}/public/coach-invitations/${this.token()}`)
      .subscribe({
        next: (info) => { this.info.set(info); this.state.set('ok'); },
        error: () => this.state.set('invalid'),
      });
  }

  accept(): void {
    if (this.password.length < 8) return;
    this.joining.set(true);
    this.error.set('');
    this.auth.acceptCoachInvitation(this.token(), this.password).subscribe({
      next: () => this.router.navigate(['/app']),
      error: () => { this.joining.set(false); this.error.set('Activation impossible. Le lien a peut-être expiré.'); },
    });
  }
}
