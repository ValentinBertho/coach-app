import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ThemeService } from '../../core/services/theme.service';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { AthleteTopbarComponent } from './athlete-topbar.component';
import { DebriefPromptComponent } from './debrief-prompt.component';

/**
 * Shell mobile-first du portail athlète (PWA) : contenu + bottom-nav persistante.
 * Navigation au pouce, quasi-native (blueprint §3A). Respecte la safe-area iOS.
 */
@Component({
  selector: 'app-athlete-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, DebriefPromptComponent,
    AthleteTopbarComponent],
  template: `
    <!-- Peau « night-track » : le portail athlète est sombre par défaut (immersion mobile).
         Ce n'est plus une fatalité — un athlète qui choisit son thème dans son profil obtient
         le sien, y compris « Système ». Tant qu'il n'a rien choisi, le parti pris tient : le
         sous-arbre force le sombre, quel que soit le réglage de l'appareil. Dès qu'il a choisi,
         l'attribut disparaît d'ici et c'est <html> — donc ThemeService — qui décide. -->
    <div class="ashell" [attr.data-theme]="skin()">
      <!-- Marque, cloche et compte : dans la coquille, donc sur TOUS les écrans du portail. Cette
           barre ne vivait que dans « Aujourd'hui » : le calendrier, les progrès, l'historique
           n'avaient ni notifications ni accès au profil. -->
      <app-athlete-topbar />

      <div class="ashell__content"><router-outlet /></div>

      <!-- Invitation au débrief, montée dans la coquille : elle vivait dans « Aujourd'hui », et
           depuis que le calendrier ouvre le portail, un athlète pouvait traverser toute
           l'application sans qu'on lui demande jamais son ressenti. -->
      <app-debrief-prompt />

      <!-- Quatre entrées, pas six : sur 375 px, six cibles tombaient à ~52 px avec des
           libellés à 10 px. « Sorties » entre dans Progrès (accès rapide), « Profil » devient
           l'avatar de la barre supérieure.

           Le calendrier passe en première position, et devient l'écran d'ouverture du portail :
           en lançant l'application, un athlète veut d'abord voir la forme de son mois — ce qui
           l'attend, ce qu'il a fait. « Aujourd'hui » reste juste à côté : c'est l'écran de la
           journée en cours (check-in, séance, ressenti), pas celui de la vue d'ensemble. -->
      <nav class="ashell__nav" aria-label="Navigation athlète">
        <a routerLink="/athlete/calendar" routerLinkActive="active">
          <app-icon name="calendar-days" [size]="22" /><span class="lb">Calendrier</span>
        </a>
        <a routerLink="/athlete/today" routerLinkActive="active">
          <app-icon name="house" [size]="22" /><span class="lb">Aujourd'hui</span>
        </a>
        <a routerLink="/athlete/progress" routerLinkActive="active">
          <app-icon name="trending-up" [size]="22" /><span class="lb">Progrès</span>
        </a>
        <a routerLink="/athlete/messages" routerLinkActive="active">
          <app-icon name="message-square" [size]="22" /><span class="lb">Messages</span>
        </a>
      </nav>
    </div>
  `,
  styles: [`
    /* La couleur du texte est réaffirmée, et pas seulement le fond : le thème sombre du portail
       est scopé à ce sous-arbre, alors que la couleur du texte est posée sur l'élément body, où
       les jetons valent encore ceux du thème clair. Tout élément qui ne redéfinit pas sa couleur
       — un simple strong — héritait donc du noir du body sur un fond sombre : les intitulés du
       profil (« Mon heure d'entraînement habituelle », « Notifications push ») étaient
       illisibles. */
    .ashell { min-height: 100dvh; background: var(--canvas); color: var(--ink); }
    .ashell__content {
      padding-bottom: calc(68px + env(safe-area-inset-bottom, 0px));
      /* Retrait haut du portail, exposé en propriété personnalisée : chaque écran monté ici
         commence SOUS l'heure et l'encoche par un simple max(var(--sp-4), var(--safe-top, 0px)),
         sans rien savoir de la coquille. Depuis que la barre supérieure est montée ici, c'est
         ELLE qui absorbe l'encoche : la variable retombe à zéro, sinon chaque écran ajouterait
         une seconde fois la hauteur de l'encoche sous une barre qui l'a déjà prise. */
      --safe-top: 0px;
    }

    .ashell__nav {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 200;
      display: grid; grid-template-columns: repeat(4, 1fr);
      background: var(--glass); backdrop-filter: saturate(180%) blur(16px);
      -webkit-backdrop-filter: saturate(180%) blur(16px);
      border-top: 1px solid var(--hairline);
      padding-bottom: env(safe-area-inset-bottom, 0px);
    }
    .ashell__nav a {
      display: flex; flex-direction: column; align-items: center; gap: 2px;
      padding: var(--sp-2) 0 var(--sp-1); text-decoration: none;
      color: var(--ink-3); font-size: var(--text-2xs); font-weight: 600;
      min-height: 56px; justify-content: center;
      transition: color var(--duration-fast) var(--ease);
    }
    /* Chrome applicatif : on ne sélectionne pas les libellés d'une barre d'onglets native. */
    .ashell__nav { -webkit-user-select: none; user-select: none; }
    .ashell__nav a .lb { white-space: nowrap; }
    .ashell__nav a .ic { font-size: 20px; line-height: 1; filter: grayscale(0.4); opacity: 0.75; }
    .ashell__nav a.active { color: var(--primary); }
    .ashell__nav a.active .ic { filter: none; opacity: 1; }
  `],
})
export class AthleteShellComponent {
  private readonly theme = inject(ThemeService);

  /**
   * Peau du portail : `dark` imposé tant que l'athlète n'a pas choisi, rien ensuite — auquel cas
   * la préférence posée sur `<html>` s'applique d'elle-même.
   */
  readonly skin = computed(() => (this.theme.chosen() ? null : 'dark'));
}
