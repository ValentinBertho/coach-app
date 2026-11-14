import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CoachDirectoryService } from '../../core/services/coach-directory.service';
import { CoachingRequestService } from '../../core/services/coaching-request.service';
import { ToastService } from '../../core/services/toast.service';
import { authErrorMessage } from '../../core/utils/auth-error';
import { FormTrap, FormTrapComponent } from '../../shared/forms/form-trap';
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
  imports: [ReactiveFormsModule, RouterLink, LogoComponent, FormTrapComponent],
  templateUrl: './athlete-signup.component.html',
  styleUrl: '../auth/auth.scss',
})
export class AthleteSignupComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CoachingRequestService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly directory = inject(CoachDirectoryService);

  /**
   * Le coach que le visiteur venait de choisir sur l'annuaire (`?coach=slug`), s'il y en a un.
   *
   * <p>Sa demande s'écrit ici, dans le même formulaire : avant, il fallait s'inscrire, confirmer
   * son e-mail, retrouver la fiche et tout recommencer. Elle part d'elle-même à la confirmation
   * de l'adresse.</p>
   */
  readonly coach = signal<{ slug: string; name: string } | null>(null);

  readonly trap = new FormTrap();
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(120)]],
    lastName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(12)]],
    birthDate: ['', [Validators.required]],
    goal: ['', [Validators.maxLength(1000)]],
    requestMessage: ['', [Validators.maxLength(2000)]],
    termsAccepted: [false, [Validators.requiredTrue]],
    healthDataConsent: [false, [Validators.requiredTrue]],
  });

  ngOnInit(): void {
    const slug = this.route.snapshot.queryParamMap.get('coach');
    if (!slug) return;
    // Une fiche introuvable ou fermée n'empêche pas de s'inscrire : on retombe simplement sur
    // l'inscription sans demande, plutôt que de bloquer le visiteur sur un lien périmé.
    this.directory.bySlug(slug).subscribe({
      next: (c) => {
        if (!c.acceptingAthletes) return;
        this.coach.set({ slug: c.slug, name: c.name });
        this.form.controls.requestMessage.addValidators([Validators.required, Validators.minLength(20)]);
        this.form.controls.requestMessage.updateValueAndValidity();
      },
      error: () => this.coach.set(null),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const { requestMessage, ...v } = this.form.getRawValue();
    const coach = this.coach();
    this.service.register({
      ...v,
      coachSlug: coach?.slug ?? null,
      requestMessage: coach ? requestMessage.trim() : null,
    }, this.trap).subscribe({
      next: () => {
        // Le serveur rend déjà une session ; on se connecte pour la même raison qu'un coach après
        // inscription — renvoyer vers la page de connexion ferait retaper ce qu'on vient de saisir.
        this.auth.login({ email: v.email, password: v.password }).subscribe({
          next: () => {
            if (coach) {
              // L'écran des demandes dit ce qui reste à faire : confirmer l'e-mail.
              this.toast.success(`Compte créé — confirmez votre e-mail pour que ${coach.name} reçoive votre demande`);
              this.router.navigate(['/athlete/demandes']);
            } else {
              this.toast.success('Bienvenue — trouvez le coach qui vous convient');
              this.router.navigate(['/coachs']);
            }
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
