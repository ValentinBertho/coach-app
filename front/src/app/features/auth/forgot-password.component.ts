import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * Demande de réinitialisation de mot de passe. La réponse est volontairement identique
 * que le compte existe ou non (pas de divulgation d'existence).
 */
@Component({
  selector: 'app-forgot-password',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, LogoComponent],
  template: `
    <main class="auth-page">
      <form class="card auth-card" [formGroup]="form" (ngSubmit)="submit()">
        <header class="auth-head">
          <app-logo [size]="44" [showText]="false" />
          <h1 class="display-sm">Mot de passe oublié</h1>
          <p class="field-hint">Indiquez votre e-mail : nous vous enverrons un lien de réinitialisation.</p>
        </header>

        @if (sent()) {
          <p class="field-hint">
            Si un compte est associé à cette adresse, un e-mail contenant un lien de
            réinitialisation vient d'être envoyé. Pensez à vérifier vos indésirables.
          </p>
          <a class="btn btn-primary btn-lg" routerLink="/login">Retour à la connexion</a>
        } @else {
          <div class="form-group">
            <label for="email">Email</label>
            <input id="email" type="email" class="form-control" formControlName="email"
                   autocomplete="email" placeholder="vous@club.fr" />
            @if (form.controls.email.touched && form.controls.email.invalid) {
              <span class="error-message">Email valide requis.</span>
            }
          </div>
          <button type="submit" class="btn btn-primary btn-lg" [disabled]="submitting()">
            {{ submitting() ? 'Envoi…' : 'Envoyer le lien' }}
          </button>
          <p class="auth-switch"><a routerLink="/login">Retour à la connexion</a></p>
        }
      </form>
    </main>
  `,
  styleUrl: './auth.scss',
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly submitting = signal(false);
  readonly sent = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.auth.forgotPassword(this.form.getRawValue().email).subscribe({
      next: () => this.sent.set(true),
      error: () => this.sent.set(true), // réponse identique : ne révèle pas l'existence
    });
  }
}
