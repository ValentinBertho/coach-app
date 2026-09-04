import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { RegistrationMode } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { authErrorMessage } from '../../core/utils/auth-error';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * La porte d'entrée de la plateforme, dans les trois régimes qu'elle peut avoir.
 *
 * <p><b>Ce qui a changé.</b> L'écran montrait toujours le même formulaire — nom, club, e-mail,
 * mot de passe, et un champ « code d'invitation » présenté comme facultatif. Le serveur, lui,
 * pouvait très bien exiger ce code, ou refuser l'inscription directe : le candidat choisissait
 * un mot de passe, cochait les conditions, et découvrait le régime réel dans un message d'erreur,
 * pour un compte qui n'allait pas exister. Le régime est désormais lu avant d'afficher quoi que
 * ce soit, et chaque régime a son formulaire.</p>
 *
 * <p>En régime « sur demande » — celui de la bêta ouverte — le formulaire ne crée rien : il
 * dépose une demande qu'un administrateur valide. Il ne demande donc pas de mot de passe, mais il
 * demande de quoi décider : qui, quelle structure, et deux lignes dessus.</p>
 *
 * <p><b>La première question est « comment coachez-vous ».</b> L'écran s'intitulait « Créer mon
 * club » et exigeait un nom de club, avec « Running Club Lyon » en exemple. La plateforme s'adresse
 * pourtant autant aux indépendants qu'aux clubs : la moitié de la cible butait donc sur le premier
 * champ, contrainte d'inventer une organisation qui n'existe pas — et ce nom la suivait ensuite
 * partout, puisque c'est lui qui s'affiche. En indépendant, le champ devient « Nom de votre
 * activité », facultatif : à défaut, l'espace prend le nom du coach.</p>
 */
@Component({
  selector: 'app-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, LogoComponent],
  templateUrl: './register.component.html',
  styleUrl: './auth.scss',
})
export class RegisterComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly submitting = signal(false);
  /** Message d'erreur du serveur, rendu à côté du formulaire (e-mail déjà utilisé, 429…). */
  readonly errorMessage = signal<string | null>(null);

  /** `null` tant que le serveur n'a pas répondu : on n'affiche aucun formulaire d'ici là. */
  readonly mode = signal<RegistrationMode | null>(null);

  /**
   * Comment le candidat coache. Porté hors des deux formulaires : la question précède le choix du
   * régime, et sa réponse pilote les libellés des deux.
   */
  readonly solo = signal(false);

  /** Vrai une fois la demande déposée : l'écran devient un accusé de réception. */
  readonly submitted = signal(false);
  readonly submittedEmail = signal('');
  readonly submittedClubName = signal('');

  /** Inscription directe (régimes « open » et « invite »). */
  readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    clubName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    termsAccepted: [false, [Validators.requiredTrue]],
    invitationCode: ['', [Validators.maxLength(120)]],
  });

  /** Demande de création de club (régime « request »). Aucun mot de passe : rien n'est créé. */
  readonly requestForm = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    clubName: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.maxLength(40)]],
    message: ['', [Validators.maxLength(2000)]],
    termsAccepted: [false, [Validators.requiredTrue]],
  });

  ngOnInit(): void {
    this.auth.registrationMode().subscribe({
      next: (info) => {
        this.mode.set(info.mode);
        // Le code n'est obligatoire que là où le serveur l'exige : ailleurs, un champ requis de
        // plus n'apporterait qu'un blocage sans raison.
        if (info.mode === 'INVITE') {
          this.form.controls.invitationCode.addValidators(Validators.required);
          this.form.controls.invitationCode.updateValueAndValidity();
        }
      },
      // L'API injoignable ne doit pas laisser la page vide : on retombe sur l'inscription
      // directe, dont le serveur reste de toute façon le seul arbitre.
      error: () => this.mode.set('OPEN'),
    });
  }

  /**
   * Bascule club ↔ indépendant, et avec elle l'obligation de nommer la structure.
   *
   * <p>Le validateur suit le mode plutôt que d'être posé une fois pour toutes : un indépendant
   * n'a rien à nommer, un club doit l'être. Le champ n'est pas vidé — quelqu'un qui hésite entre
   * les deux modes ne doit pas perdre ce qu'il vient de taper.</p>
   */
  setPractice(solo: boolean): void {
    this.solo.set(solo);
    for (const control of [this.form.controls.clubName, this.requestForm.controls.clubName]) {
      if (solo) {
        control.removeValidators(Validators.required);
      } else {
        control.addValidators(Validators.required);
      }
      control.updateValueAndValidity();
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.register({ ...this.form.getRawValue(), soloPractice: this.solo() }).subscribe({
      next: () => {
        this.toast.success(this.solo() ? 'Espace créé, bienvenue sur Darilab'
                                       : 'Club créé, bienvenue sur Darilab');
        this.router.navigate(['/app']);
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(authErrorMessage(err));
      },
    });
  }

  submitRequest(): void {
    if (this.requestForm.invalid) {
      this.requestForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    const value = { ...this.requestForm.getRawValue(), soloPractice: this.solo() };
    this.auth.submitClubRequest(value).subscribe({
      next: () => {
        // Retenus avant de basculer l'écran : l'accusé de réception les rappelle, et c'est ce
        // qui permet au candidat de vérifier qu'il n'a pas fait de faute dans son adresse.
        this.submittedEmail.set(value.email);
        this.submittedClubName.set(value.clubName);
        this.submitted.set(true);
        this.submitting.set(false);
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(authErrorMessage(err));
      },
    });
  }
}
