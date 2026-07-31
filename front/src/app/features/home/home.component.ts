import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { InstallButtonComponent } from '../../shared/components/install-button/install-button.component';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * Page d'accueil publique : promesse produit et entrées connexion / création de club.
 * Elle tutoie comme le reste de l'app — le premier contact ne doit pas être la première
 * incohérence. L'état de l'API a été déplacé sous /dev/api : c'est un outil d'équipe.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink, LogoComponent, InstallButtonComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {
  private readonly auth = inject(AuthService);

  readonly isAuthenticated = this.auth.isAuthenticated;
}
