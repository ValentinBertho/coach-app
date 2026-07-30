import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { IconComponent } from '../../shared/components/icon/icon.component';

/**
 * Coquille « Bibliothèque » à onglets : Course · Prépa physique · Éducatifs.
 *
 * La navigation portait quatre entrées de bibliothèque, dont une vue lecture seule qui
 * dupliquait les trois autres — confusion garantie à l'audit. Une seule entrée désormais, et
 * les écrans existants (modèles course, prépa physique, éducatifs) sont montés tels quels en
 * routes enfants : ils gardent leur propre en-tête, leurs actions et leurs sous-onglets.
 */
@Component({
  selector: 'app-library-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  template: `
    <nav class="status-tabs lib-tabs" aria-label="Bibliothèque">
      <a routerLink="/app/library/course" routerLinkActive="active">
        <app-icon name="library" [size]="15" /> Course
      </a>
      <a routerLink="/app/library/strength" routerLinkActive="active">
        <app-icon name="dumbbell" [size]="15" /> Prépa physique
      </a>
      <a routerLink="/app/library/drills" routerLinkActive="active">
        <app-icon name="graduation-cap" [size]="15" /> Éducatifs
      </a>
    </nav>

    <router-outlet />
  `,
  styles: [`
    /* Les onglets remplacent l'ancienne poignée de rubriques de la nav latérale : ils doivent
       se lire avant l'en-tête de l'écran enfant, d'où la marge basse.
       .status-tabs ne stylise que les <button> ; ici ce sont des liens (navigation réelle). */
    .lib-tabs { margin-bottom: var(--sp-4); }
    .lib-tabs a {
      display: inline-flex; align-items: center; gap: var(--sp-2);
      border: 1px solid var(--hairline); background: var(--paper); color: var(--ink-2);
      padding: var(--sp-2) var(--sp-4); border-radius: var(--radius-full);
      font-weight: 600; font-size: var(--text-sm); text-decoration: none;
    }
    .lib-tabs a:hover { color: var(--ink); }
    .lib-tabs a.active { background: var(--primary-wash); color: var(--primary); border-color: var(--primary-light); }
  `],
})
export class LibraryShellComponent {}
