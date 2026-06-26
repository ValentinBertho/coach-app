import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

type State = 'loading' | 'ok' | 'invalid';

/**
 * Choix d'un nouveau mot de passe à partir d'un lien de réinitialisation. Le lien est
 * validé à l'ouverture ; une fois le mot de passe redéfini, une session est ouverte.
 */
@Component({
  selector: 'app-reset-password',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, LogoComponent],
  template: `
    <main class="auth-page">
      <form class="card auth-card" [formGroup]="form" (ngSubmit)="submit()">
        <header class="auth-head">
          <app-logo [size]="44" [showText]="false" />
          <h1 class="display-sm">Nouveau mot de passe</h1>
        </header>

        @switch (state()) {
          @case ('loading') { <p class="field-hint">Vérification du lien…</p> }
          @case ('invalid') {
            <p class="field-hint">Ce lien de réinitialisation est invalide ou expiré.</p>
            <a class="btn btn-primary btn-lg" routerLink="/forgot-password">Demander un nouveau lien</a>
          }
          @case ('ok') {
            <p class="field-hint">Choisissez un nouveau mot de passe pour votre compte.</p>
            <div class="form-group">
              <label for="password">Mot de passe</label>
              <input id="password" type="password" class="form-control" formControlName="password"
                     autocomplete="new-password" placeholder="8 caractères minimum" />
              @if (form.controls.password.touched && form.controls.password.invalid) {
                <span class="error-message">8 caractères minimum.</span>
              }
            </div>
            <button type="submit" class="btn btn-primary btn-lg" [disabled]="submitting()">
              {{ submitting() ? 'Enregistrement…' : 'Réinitialiser et se connecter' }}
            </button>
          }
        }
      </form>
    </main>
  `,
  styleUrl: './auth.scss',
})
export class ResetPasswordComponent implements OnInit {
  readonly token = input.required<string>();
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly state = signal<State>('loading');
  readonly submitting = signal(false);

  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.auth.checkReset(this.token()).subscribe({
      next: (res) => this.state.set(res.valid ? 'ok' : 'invalid'),
      error: () => this.state.set('invalid'),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.auth.resetPassword(this.token(), this.form.getRawValue().password).subscribe({
      next: (res) => {
        this.toast.success('Mot de passe mis à jour');
        this.router.navigateByUrl(this.homeFor(res.user.role));
      },
      error: () => {
        this.submitting.set(false);
        this.state.set('invalid');
      },
    });
  }

  private homeFor(role: string): string {
    return role === 'PLATFORM_ADMIN' ? '/admin' : role === 'ATHLETE' ? '/athlete/today' : '/app';
  }
}
