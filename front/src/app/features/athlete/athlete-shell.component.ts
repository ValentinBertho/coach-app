import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Shell mobile-first du portail athlète (PWA) : contenu + bottom-nav persistante.
 * Navigation au pouce, quasi-native (blueprint §3A). Respecte la safe-area iOS.
 */
@Component({
  selector: 'app-athlete-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    <div class="ashell">
      <div class="ashell__content"><router-outlet /></div>

      <nav class="ashell__nav" aria-label="Navigation athlète">
        <a routerLink="/athlete/today" routerLinkActive="active">
          <app-icon name="house" [size]="22" /><span class="lb">Séance</span>
        </a>
        <a routerLink="/athlete/calendar" routerLinkActive="active">
          <app-icon name="calendar" [size]="22" /><span class="lb">Agenda</span>
        </a>
        <a routerLink="/athlete/progress" routerLinkActive="active">
          <app-icon name="trending-up" [size]="22" /><span class="lb">Progrès</span>
        </a>
        <a routerLink="/athlete/messages" routerLinkActive="active">
          <app-icon name="message-square" [size]="22" /><span class="lb">Messages</span>
        </a>
        <a routerLink="/athlete/profile" routerLinkActive="active">
          <app-icon name="user" [size]="22" /><span class="lb">Profil</span>
        </a>
      </nav>
    </div>
  `,
  styles: [`
    .ashell { min-height: 100dvh; background: var(--canvas); }
    .ashell__content { padding-bottom: calc(68px + env(safe-area-inset-bottom, 0px)); }

    .ashell__nav {
      position: fixed; left: 0; right: 0; bottom: 0; z-index: 200;
      display: grid; grid-template-columns: repeat(5, 1fr);
      background: var(--glass); backdrop-filter: saturate(180%) blur(16px);
      -webkit-backdrop-filter: saturate(180%) blur(16px);
      border-top: 1px solid var(--hairline);
      padding-bottom: env(safe-area-inset-bottom, 0px);
    }
    .ashell__nav a {
      display: flex; flex-direction: column; align-items: center; gap: 2px;
      padding: var(--sp-2) 0 var(--sp-1); text-decoration: none;
      color: var(--ink-3); font-size: 10px; font-weight: 600;
      min-height: 56px; justify-content: center;
      transition: color var(--duration-fast) var(--ease);
    }
    .ashell__nav a .lb { white-space: nowrap; }
    .ashell__nav a .ic { font-size: 20px; line-height: 1; filter: grayscale(0.4); opacity: 0.75; }
    .ashell__nav a.active { color: var(--primary); }
    .ashell__nav a.active .ic { filter: none; opacity: 1; }
  `],
})
export class AthleteShellComponent {}
