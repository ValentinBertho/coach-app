import { ChangeDetectionStrategy, Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';
import { OfflineBannerComponent } from '../../shared/components/offline-banner/offline-banner.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { HelpService } from '../help/help.service';
import { AdminSearchPaletteComponent } from './admin-search-palette.component';

/** Un lien de la barre latérale. */
interface NavLink {
  path: string;
  label: string;
  icon: string;
  /** Vrai pour l'accueil, dont la route est préfixe de toutes les autres. */
  exact?: boolean;
}

/** Un regroupement de liens : huit liens à plat ne se lisent plus. */
interface NavGroup {
  label: string;
  links: NavLink[];
}

/**
 * Coquille du back-office : barre latérale groupée, recherche globale, router-outlet.
 *
 * <p><b>Ce qui a changé.</b> La navigation était une rangée de huit liens dans l'en-tête, sans
 * hiérarchie : « Clubs », « Utilisateurs » et « E-mails » y avaient exactement le même poids
 * visuel, alors qu'ils répondent à des questions sans rapport. Les zones ci-dessous suivent les
 * moments réels du travail d'administration — piloter, gérer, superviser, tracer.</p>
 */
@Component({
  selector: 'app-admin-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, LogoComponent, OfflineBannerComponent,
    IconComponent, AdminSearchPaletteComponent,
  ],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly help = inject(HelpService);

  readonly user = this.auth.currentUser;
  readonly searchOpen = signal(false);
  readonly menuOpen = signal(false);

  readonly groups: NavGroup[] = [
    {
      label: 'Pilotage',
      links: [{ path: '/admin', label: 'Tableau de bord', icon: 'layout-dashboard', exact: true }],
    },
    {
      label: 'Gestion',
      links: [
        { path: '/admin/users', label: 'Utilisateurs', icon: 'users' },
        { path: '/admin/clubs', label: 'Clubs', icon: 'building-2' },
        { path: '/admin/athletes', label: 'Athlètes', icon: 'footprints' },
        { path: '/admin/invitations', label: 'Invitations', icon: 'mail' },
        // Placé dans « Gestion » et non dans « Supervision » : ce n'est pas une chose qu'on
        // regarde, c'est une file qu'on traite — de l'autre côté, un coach attend d'entrer.
        { path: '/admin/club-requests', label: 'Demandes de club', icon: 'door-open' },
      ],
    },
    {
      label: 'Supervision',
      links: [
        { path: '/admin/feedback', label: 'Retours', icon: 'message-square' },
        { path: '/admin/mail', label: 'E-mails', icon: 'inbox' },
        { path: '/admin/platform', label: 'Configuration', icon: 'settings' },
      ],
    },
    {
      label: 'Sécurité',
      links: [{ path: '/admin/audit', label: 'Journal d’audit', icon: 'shield-check' }],
    },
  ];

  ngOnInit(): void {
    if (!this.auth.currentUser()) {
      this.auth.loadCurrentUser().subscribe({ error: () => this.logout() });
    }
  }

  /**
   * <kbd>Ctrl/⌘ K</kbd> ouvre la recherche depuis n'importe quel écran.
   *
   * <p>Le raccourci est ignoré quand le curseur est déjà dans un champ : on n'interrompt pas
   * quelqu'un en train de saisir un nom de club.</p>
   */
  @HostListener('document:keydown', ['$event'])
  onShortcut(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.openSearch();
      return;
    }
    const target = event.target as HTMLElement | null;
    // SELECT compris : les listes déroulantes réagissent aux touches pour sauter à une option,
    // et détourner « / » y ferait surgir la palette au milieu d'un choix de filtre.
    const typing = target?.tagName === 'INPUT' || target?.tagName === 'TEXTAREA'
      || target?.tagName === 'SELECT' || target?.isContentEditable === true;
    if (event.key === '/' && !typing && !this.searchOpen()) {
      event.preventDefault();
      this.openSearch();
    }
  }

  openSearch(): void {
    this.searchOpen.set(true);
    this.menuOpen.set(false);
    // La palette prend le clavier elle-même à son ngAfterViewInit : viser son champ d'ici
    // supposerait une vue déjà créée, ce qu'elle n'est pas au moment de ce set().
  }

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  logout(): void {
    this.auth.logout();
    this.toast.info('Déconnecté.');
    this.router.navigate(['/login']);
  }
}
