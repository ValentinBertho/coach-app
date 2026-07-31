import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { Router, RouterLink } from '@angular/router';
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
export class HomeComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly isAuthenticated = this.auth.isAuthenticated;

  /**
   * Destination de « Mon espace ». Elle pointait vers `/app` pour tout le monde : un athlète
   * connecté — et c'est le cas de tous ceux qui installent la PWA, dont le `start_url` est
   * cette page — atterrissait dans le cockpit coach.
   */
  readonly home = computed(() => this.auth.homeRoute());

  /**
   * Un utilisateur déjà connecté n'a rien à faire sur la page de vente : on l'emmène droit
   * chez lui. C'est ce qui fait que la PWA installée (`start_url` = cette page) s'ouvre sur
   * « Aujourd'hui » pour un athlète et sur le cockpit pour un coach, au lieu d'exiger un clic
   * sur un bouton qui, avant ce correctif, menait tout le monde au même endroit.
   */
  ngOnInit(): void {
    if (this.auth.isAuthenticated()) {
      this.router.navigateByUrl(this.auth.homeRoute(), { replaceUrl: true });
    }
  }
}
