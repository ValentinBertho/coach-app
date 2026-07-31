import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * Page 404.
 *
 * Une redirection silencieuse vers l'accueil (l'ancien `redirectTo: ''`) laisse croire que le lien
 * a marché : l'utilisateur ne comprend ni pourquoi il n'est pas là où il voulait aller, ni qu'il
 * doit corriger son URL. On le dit, et on propose le bon point d'entrée selon son rôle.
 */
@Component({
  selector: 'app-not-found',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, LogoComponent],
  template: `
    <main class="nf">
      <div class="card nf-card">
        <app-logo [size]="44" [showText]="false" />
        <p class="nf-code">404</p>
        <h1 class="display-sm">Cette page n'existe pas</h1>
        <p class="field-hint">
          Le lien est peut-être périmé, ou l'adresse comporte une faute de frappe.
        </p>
        <a class="btn btn-primary" [routerLink]="home()">{{ homeLabel() }}</a>
      </div>
    </main>
  `,
  styles: [`
    .nf {
      min-height: 100dvh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--sp-4);
      background-color: var(--night);
      background-image: var(--mesh);
    }
    .nf-card {
      width: 100%;
      max-width: 440px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--sp-3);
      text-align: center;
      border-radius: var(--radius-xl);
      box-shadow: var(--shadow-lg);
    }
    .nf-card h1 { margin: 0; }
    .nf-code {
      margin: 0;
      font-family: var(--font-data);
      font-size: var(--text-2xl);
      font-weight: 800;
      color: var(--ink-3);
      letter-spacing: 0.08em;
    }
  `],
})
export class NotFoundComponent {
  private readonly auth = inject(AuthService);

  /** Point d'entrée pertinent : l'athlète n'a rien à faire sur le cockpit coach. */
  readonly home = computed(() => {
    if (!this.auth.isAuthenticated()) {
      return '/';
    }
    return this.auth.homeRoute();
  });

  readonly homeLabel = computed(() =>
    this.auth.isAuthenticated() ? 'Retour à mon espace' : "Retour à l'accueil",
  );
}
