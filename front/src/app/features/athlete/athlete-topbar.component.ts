import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { NotificationBellComponent } from '../../shared/components/notification-bell/notification-bell.component';

/**
 * Barre supérieure du portail athlète : marque, cloche de notifications, accès au compte.
 *
 * <p>Elle vivait dans le gabarit d'« Aujourd'hui », donc sur <b>un seul</b> des dix écrans du
 * portail. Un athlète qui ouvrait l'application sur son calendrier — l'écran d'entrée depuis que
 * le portail s'ouvre dessus — n'avait ni cloche, ni badge de non-lues, ni porte vers son profil :
 * les notifications n'existaient que pour qui passait par l'onglet du jour. Montée dans la
 * coquille, elle couvre tout le portail et porte l'encoche pour les écrans qui n'ont plus à s'en
 * préoccuper.</p>
 */
@Component({
  selector: 'app-athlete-topbar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LogoComponent, NotificationBellComponent],
  template: `
    <header class="atop">
      <!-- Le logo ramène chez soi : c'est la convention de toute application, et c'était le seul
           élément permanent de l'écran à ne rien faire. -->
      <a [routerLink]="home()" class="atop__brand" aria-label="Accueil">
        <app-logo [size]="28" [showText]="true" />
      </a>
      <!-- Deux contrôles, pas cinq : cette barre doit porter la séance, pas la gestion du compte.
           Installer / notifications / aide / déconnexion vivent dans Profil. -->
      <div class="atop__actions">
        <app-notification-bell />
        <a routerLink="/athlete/profile" class="atop__avatar" aria-label="Mon profil">
          <span class="avatar avatar-sm">{{ initials() }}</span>
        </a>
      </div>
    </header>
  `,
  styles: [`
    .atop {
      display: flex; align-items: center; justify-content: space-between; gap: var(--sp-2);
      padding: var(--sp-3) var(--sp-4);
      padding-top: max(var(--sp-3), env(safe-area-inset-top, 0px));
      background: var(--paper); border-bottom: 1px solid var(--paper-sunk);
      position: sticky; top: 0; z-index: 100;
    }
    .atop__brand { display: inline-flex; align-items: center; min-height: 44px; text-decoration: none; color: inherit; }
    .atop__actions { display: flex; align-items: center; gap: var(--sp-2); }
    /* L'avatar est la porte du compte : cible confortable, jamais un simple lien texte. */
    .atop__avatar {
      display: inline-flex; min-width: 44px; min-height: 44px;
      align-items: center; justify-content: center; text-decoration: none;
    }
  `],
})
export class AthleteTopbarComponent {
  private readonly auth = inject(AuthService);
  readonly user = this.auth.currentUser;

  /** Accueil du rôle : le portail s'ouvre sur le calendrier, le logo y ramène. */
  home(): string {
    return this.auth.homeRoute();
  }

  initials(): string {
    const parts = (this.user()?.fullName ?? '').trim().split(/\s+/).filter(Boolean);
    return (parts[0]?.[0] ?? '') + (parts[1]?.[0] ?? '');
  }
}
