import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { authErrorMessage } from '../../core/utils/auth-error';
import { readPendingStravaAuth } from '../../core/services/strava-pending.storage';
import { MfaChallenge } from '../../core/models/user.model';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { FocusProbeComponent } from './focus-probe.component';

/** Étapes de la connexion : mot de passe, puis code (admins), puis codes de secours (1er enrôlement). */
type Step = 'password' | 'code' | 'recovery';

@Component({
  selector: 'app-login',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, LogoComponent, FocusProbeComponent],
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

  readonly step = signal<Step>('password');
  readonly challenge = signal<MfaChallenge | null>(null);
  /** QR code du secret (data URL), généré dans le navigateur : le secret ne part chez aucun tiers. */
  readonly qrCode = signal<string | null>(null);
  readonly recoveryCodes = signal<string[]>([]);

  private readonly codeInput = viewChild<ElementRef<HTMLInputElement>>('codeInput');

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly codeForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(32)]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: (res) => {
        if (res.mfa) {
          this.askForCode(res.mfa);
          return;
        }
        this.done();
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(authErrorMessage(err));
      },
    });
  }

  submitCode(): void {
    const challenge = this.challenge();
    if (!challenge || this.codeForm.invalid) {
      this.codeForm.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.errorMessage.set(null);
    this.auth.verifyMfa(challenge.challengeToken, this.codeForm.getRawValue().code.trim()).subscribe({
      next: (res) => {
        this.submitting.set(false);
        if (res.recoveryCodes?.length) {
          this.recoveryCodes.set(res.recoveryCodes);
          this.step.set('recovery');
          return;
        }
        this.done();
      },
      error: (err) => {
        this.submitting.set(false);
        this.codeForm.reset();
        if (err instanceof HttpErrorResponse && err.status === 410) {
          // Défi expiré ou épuisé : retour au mot de passe, avec le message du serveur.
          this.backToPassword();
        }
        this.errorMessage.set(authErrorMessage(err));
      },
    });
  }

  backToPassword(): void {
    this.step.set('password');
    this.challenge.set(null);
    this.qrCode.set(null);
    this.form.controls.password.reset();
  }

  /** Secret affiché par groupes de quatre, plus facile à recopier à la main. */
  groupedSecret(secret: string | null): string {
    return (secret ?? '').replace(/(.{4})/g, '$1 ').trim();
  }

  copyRecoveryCodes(): void {
    navigator.clipboard?.writeText(this.recoveryCodes().join('\n')).then(
      () => this.toast.success('Codes copiés'),
      () => this.toast.warning('Copie impossible : recopie-les à la main.'),
    );
  }

  /** Après l'affichage des codes de secours : on entre dans l'application. */
  continueAfterRecovery(): void {
    this.recoveryCodes.set([]);
    this.done();
  }

  private askForCode(challenge: MfaChallenge): void {
    this.submitting.set(false);
    this.challenge.set(challenge);
    this.codeForm.reset();
    this.step.set('code');
    if (challenge.enrollment && challenge.otpauthUri) {
      void this.renderQr(challenge.otpauthUri);
    }
    setTimeout(() => this.codeInput()?.nativeElement.focus());
  }

  private async renderQr(uri: string): Promise<void> {
    try {
      // Chargé à la demande : seuls les administrateurs, une fois, en ont besoin.
      const { toDataURL } = await import('qrcode');
      this.qrCode.set(await toDataURL(uri, { margin: 1, width: 200 }));
    } catch {
      this.qrCode.set(null); // le secret reste affiché en clair
    }
  }

  private done(): void {
    this.toast.success('Connexion réussie');
    // Autorisation Strava mise en attente (retour d'OAuth hors PWA, où la session manquait) :
    // on reprend la finalisation avant d'envoyer l'utilisateur chez lui.
    this.router.navigateByUrl(
      readPendingStravaAuth() ? '/strava/callback' : this.auth.homeRoute(),
    );
  }
}
