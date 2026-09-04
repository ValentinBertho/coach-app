import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CoachingRequestService } from '../../core/services/coaching-request.service';
import { ToastService } from '../../core/services/toast.service';
import { authErrorMessage } from '../../core/utils/auth-error';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * L'inscription d'un athlète qui vient de lui-même.
 *
 * <p>Jusqu'ici, un athlète n'obtenait un compte que par l'invitation d'un coach : quelqu'un
 * l'attendait, et le formulaire n'avait qu'à activer un accès. Ici personne ne l'attend, et l'écran
 * doit donc faire deux choses de plus — dire ce que cette inscription ne crée pas (ni club, ni
 * coach), et recueillir deux consentements distincts.</p>
 *
 * <p><b>Deux cases, et non une.</b> Les CGU régissent l'usage du service ; le consentement santé
 * autorise le traitement de données de l'article 9. Les fondre priverait le second de la clarté
 * qu'il exige — et c'est celui-là qui, plus tard, permettra à un coach d'enregistrer une douleur
 * ou un test de lactate.</p>
 */
@Component({
  selector: 'app-athlete-signup',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, LogoComponent],
  templateUrl: './athlete-signup.component.html',
  styleUrl: '../auth/auth.scss',
})
export class AthleteSignupComponent {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CoachingRequestService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(120)]],
    lastName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    birthDate: ['', [Validators.required]],
    goal: ['', [Validators.maxLength(1000)]],
    termsAccepted: [false, [Validators.requiredTrue]],
    healthDataConsent: [false, [Validators.requiredTrue]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const v = this.form.getRawValue();
    this.service.register(v).subscribe({
      next: () => {
        // Le serveur rend déjà une session ; on se connecte pour la même raison qu'un coach après
        // inscription — renvoyer vers la page de connexion ferait retaper ce qu'on vient de saisir.
        this.auth.login({ email: v.email, password: v.password }).subscribe({
          next: () => {
            this.toast.success('Bienvenue — trouvez le coach qui vous convient');
            this.router.navigate(['/coachs']);
          },
          error: () => this.router.navigate(['/login']),
        });
      },
      error: (err) => {
        this.submitting.set(false);
        // Le serveur explique l'âge minimum et le chemin qui reste ouvert : c'est ce message-là
        // qu'il faut montrer, pas un « inscription impossible » générique.
        this.errorMessage.set(err?.error?.message ?? authErrorMessage(err));
      },
    });
  }
}
