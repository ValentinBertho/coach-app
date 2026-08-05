import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { authErrorMessage } from '../../core/utils/auth-error';
import { readPendingStravaAuth } from '../../core/services/strava-pending.storage';
import { LogoComponent } from '../../shared/components/logo/logo.component';

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, LogoComponent],
  templateUrl: './login.component.html',
  styleUrl: './auth.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly submitting = signal(false);
  /** Message d'erreur du serveur, rendu à côté du formulaire (identifiants, compte suspendu, 429). */
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        this.toast.success('Connexion réussie');
        // Autorisation Strava mise en attente (retour d'OAuth hors PWA, où la session manquait) :
        // on reprend la finalisation avant d'envoyer l'utilisateur chez lui.
        this.router.navigateByUrl(
          readPendingStravaAuth() ? '/strava/callback' : this.auth.homeRoute(),
        );
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(authErrorMessage(err));
      },
    });
  }
}
