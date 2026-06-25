import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

interface InvitationInfo {
  athleteFirstName: string;
  clubName: string;
}

type State = 'loading' | 'ok' | 'invalid';

/**
 * Page publique d'invitation athlète (lien magique). Le bouton crée la session ATHLETE
 * (sans mot de passe) et ouvre le portail.
 */
@Component({
  selector: 'app-invitation',
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
            <h1 class="display-sm">Bienvenue {{ info()?.athleteFirstName }}</h1>
            <p class="field-hint">
              Votre coach vous invite à rejoindre CoachRun. Accédez à votre espace pour suivre
              votre séance du jour et donner votre ressenti.
            </p>
            <label class="consent">
              <input type="checkbox" [(ngModel)]="consent" />
              <span>J'accepte la collecte de mes <strong>données physiologiques</strong> (FC, allure, ressenti)
                pour le suivi de mon entraînement (RGPD, art. 9).</span>
            </label>
            <button type="button" class="btn btn-primary btn-lg" [disabled]="joining() || !consent" (click)="accept()">
              {{ joining() ? 'Connexion…' : 'Accéder à mon espace' }}
            </button>
          }
          @case ('invalid') {
            <span class="badge badge-danger">Lien invalide</span>
            <h1 class="display-sm">Invitation expirée</h1>
            <p class="field-hint">Ce lien d'invitation n'est plus valide. Demandez-en un nouveau à votre coach.</p>
          }
        }
      </div>
    </main>
  `,
  styles: [`
    .invite-page { min-height: 100dvh; display: flex; align-items: center; justify-content: center; padding: var(--sp-4); background-color: var(--night); background-image: var(--mesh); }
    .invite-card { max-width: 440px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: var(--sp-3); border-radius: var(--radius-xl); box-shadow: var(--shadow-lg); }
    .consent { display: flex; gap: var(--sp-2); text-align: left; font-size: var(--text-sm); color: var(--ink-2); align-items: flex-start; }
    .consent input { margin-top: 3px; width: 18px; height: 18px; flex-shrink: 0; }
    .btn-lg { width: 100%; }
  `],
})
export class InvitationComponent implements OnInit {
  readonly token = input.required<string>();
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly state = signal<State>('loading');
  readonly info = signal<InvitationInfo | null>(null);
  readonly joining = signal(false);
  consent = false;

  ngOnInit(): void {
    this.http
      .get<InvitationInfo>(`${environment.apiUrl}/public/invitations/${this.token()}`)
      .subscribe({
        next: (info) => {
          this.info.set(info);
          this.state.set('ok');
        },
        error: () => this.state.set('invalid'),
      });
  }

  accept(): void {
    if (!this.consent) return;
    this.joining.set(true);
    this.auth.acceptInvitation(this.token(), this.consent).subscribe({
      next: () => this.router.navigate(['/athlete/today']),
      error: () => this.joining.set(false),
    });
  }
}
