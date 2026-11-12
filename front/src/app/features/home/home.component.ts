import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
} from '@angular/core';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { InstallButtonComponent } from '../../shared/components/install-button/install-button.component';
import { LogoComponent } from '../../shared/components/logo/logo.component';

interface TourRow {
  img: string;
  icon: string;
  title: string;
  text: string;
  alt: string;
}

/**
 * Page d'accueil publique : promesse produit et entrées connexion / création de club.
 * Elle tutoie comme le reste de l'app — le premier contact ne doit pas être la première
 * incohérence. L'état de l'API a été déplacé sous /dev/api : c'est un outil d'équipe.
 */
@Component({
  selector: 'app-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, RouterLink, LogoComponent, InstallButtonComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly isAuthenticated = this.auth.isAuthenticated;

  /** La visite de l'outil du coach : un écran réel par promesse. */
  readonly tour: readonly TourRow[] = [
    {
      img: 'tableau-de-bord', icon: 'layout-dashboard',
      title: 'Ta journée en un coup d\'œil',
      text: 'Les athlètes à surveiller remontent seuls : fatigue, douleur, ou silence depuis '
        + 'quelques jours. Les retours de séance t\'attendent dans une seule file.',
      alt: 'Le tableau de bord du coach : athlètes à surveiller et vue d\'ensemble',
    },
    {
      img: 'programme', icon: 'calendar',
      title: 'Planifie en glissant tes séances types',
      text: 'Ta bibliothèque à côté du calendrier : glisse, copie une semaine, un athlète ou un '
        + 'groupe. Les allures se recalculent pour chacun.',
      alt: 'Le programme d\'une athlète, avec la bibliothèque de séances à côté du calendrier',
    },
    {
      img: 'fiche-athlete', icon: 'gauge',
      title: 'Des allures calculées pour chaque athlète',
      text: 'VDOT, seuils, vitesse critique : tu prescris en fourchettes, et chacun reçoit ses '
        + 'propres cibles.',
      alt: 'La fiche d\'une athlète : profil physiologique et allures d\'entraînement',
    },
    {
      img: 'charge', icon: 'trending-up',
      title: 'Vois venir la fatigue avant la blessure',
      text: 'Douze semaines de charge et le ratio aigu / chronique, sans tableur ni formule.',
      alt: 'L\'évolution de la charge d\'entraînement sur douze semaines',
    },
    {
      img: 'demandes', icon: 'inbox',
      title: 'Des athlètes viennent à toi',
      text: 'Ta fiche dans l\'annuaire présente ton approche et tes formules. Les demandes '
        + 'arrivent avec l\'objectif de l\'athlète : tu acceptes, tu refuses, ou tu poses une question.',
      alt: 'Une demande de coaching reçue depuis l\'annuaire',
    },
  ];

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
