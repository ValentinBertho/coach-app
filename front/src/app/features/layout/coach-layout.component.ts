import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';

/**
 * Coquille de l'espace coach : en-tête (club, utilisateur, déconnexion), navigation,
 * et router-outlet pour les sous-pages (tableau de bord, athlètes…).
 */
@Component({
  selector: 'app-coach-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './coach-layout.component.html',
  styleUrl: './coach-layout.component.scss',
})
export class CoachLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly user = this.auth.currentUser;

  ngOnInit(): void {
    if (!this.auth.currentUser()) {
      this.auth.loadCurrentUser().subscribe({ error: () => this.logout() });
    }
  }

  logout(): void {
    this.auth.logout();
    this.toast.info('Vous êtes déconnecté.');
    this.router.navigate(['/login']);
  }
}
