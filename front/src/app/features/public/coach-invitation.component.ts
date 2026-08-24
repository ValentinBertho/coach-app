import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

interface CoachInvitationInfo {
  email: string;
  fullName: string;
  clubName: string | null;
}

type State = 'loading' | 'ok' | 'invalid' | 'done';

/**
 * Page publique d'invitation coach (lien magique) : le coach définit son mot de passe, ce qui
 * active son compte et ouvre l'espace coach.
 *
 * <p><b>Ce que le coach ne doit jamais rencontrer ici : un cul-de-sac.</b> C'est son tout premier
 * écran du produit, et il y arrive depuis sa boîte mail — sans compte, sans repère, sans onglet
 * ouvert sur l'application. Deux impasses existaient.</p>
 *
 * <p>La première : un lien déjà utilisé ou périmé (le lien vaut quatorze jours, et il est consommé
 * à l'activation — donc un simple rechargement de la page suffit) affichait « Invitation expirée »
 * et rien d'autre. Pas un bouton, pas un lien : un coach dont le compte venait pourtant d'être
 * créé n'avait aucun chemin vers sa propre application.</p>
 *
 * <p>La seconde : après l'activation, la redirection partait vers {@code /app} en dur. Elle
 * n'attendait rien et n'affichait rien ; si elle échouait — garde, session non encore posée,
 * retour arrière — le coach restait sur le formulaire qu'il venait de valider, sans savoir si son
 * compte existait. Un état « c'est fait » explicite, avec un bouton visible, coûte un écran et
 * supprime la question.</p>
 */
@Component({
  selector: 'app-coach-invitation',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, LogoComponent, RouterLink],
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
              Tu es invité·e à rejoindre <strong>{{ info()?.clubName }}</strong> comme coach.
              Choisis un mot de passe pour activer ton compte ({{ info()?.email }}).
            </p>
            <form class="form" (ngSubmit)="accept()">
              <input type="password" class="form-control" name="pwd" placeholder="Mot de passe (8 caractères min.)"
                     [(ngModel)]="password" minlength="8" required autocomplete="new-password" />
              <label class="consent">
                <input type="checkbox" name="terms" [(ngModel)]="termsAccepted" />
                <span>J'accepte les <a routerLink="/legal/cgu" target="_blank">conditions d'utilisation</a>
                  et la <a routerLink="/legal/confidentialite" target="_blank">politique de confidentialité</a>.</span>
              </label>
              <button type="submit" class="btn btn-primary btn-lg"
                      [disabled]="joining() || password.length < 8 || !termsAccepted">
                {{ joining() ? 'Activation…' : 'Activer mon compte' }}
              </button>
            </form>
            @if (error()) { <p class="field-hint err">{{ error() }}</p> }
          }
          @case ('done') {
            <app-logo [size]="44" [showText]="false" />
            <span class="badge badge-success">Compte activé</span>
            <h1 class="display-sm">Bienvenue chez {{ info()?.clubName }}</h1>
            <p class="field-hint">Ton espace coach est prêt. On t'y emmène…</p>
            <a class="btn btn-primary btn-lg" [routerLink]="home">Accéder à mon espace</a>
          }
          @case ('invalid') {
            <span class="badge badge-danger">Lien invalide</span>
            <h1 class="display-sm">Ce lien n'est plus valide</h1>
            <!-- La cause de loin la plus fréquente : le lien a DÉJÀ servi. Le compte existe donc,
                 et le dire évite un aller-retour avec le club pour une invitation inutile. -->
            <p class="field-hint">
              Un lien d'invitation ne sert qu'une fois et vaut quatorze jours. Si tu as déjà choisi
              ton mot de passe, ton compte existe : connecte-toi. Sinon, demande à ton club de te
              renvoyer l'invitation.
            </p>
            <a class="btn btn-primary btn-lg" routerLink="/login">Se connecter</a>
            <a class="btn btn-ghost btn-sm" routerLink="/forgot-password">Mot de passe oublié</a>
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
    .consent { display: flex; gap: var(--sp-2); text-align: left; font-size: var(--text-sm); color: var(--ink-2); align-items: flex-start; cursor: pointer; }
    .consent input { margin-top: 3px; width: 18px; height: 18px; flex-shrink: 0; }
    .consent a { color: var(--ink-1); }
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
  /** Destination après activation : l'accueil du rôle réel, jamais /app en dur. */
  home = '/app';
  readonly error = signal('');
  password = '';
  termsAccepted = false;

  ngOnInit(): void {
    this.http
      .get<CoachInvitationInfo>(`${environment.apiUrl}/public/coach-invitations/${this.token()}`)
      .subscribe({
        next: (info) => { this.info.set(info); this.state.set('ok'); },
        error: () => this.state.set('invalid'),
      });
  }

  accept(): void {
    if (this.password.length < 8 || !this.termsAccepted) return;
    this.joining.set(true);
    this.error.set('');
    this.auth.acceptCoachInvitation(this.token(), this.password, undefined, this.termsAccepted).subscribe({
      next: () => {
        this.home = this.auth.homeRoute();
        this.state.set('done');
        this.router.navigateByUrl(this.home);
      },
      error: () => { this.joining.set(false); this.error.set('Activation impossible. Le lien a peut-être expiré.'); },
    });
  }
}
