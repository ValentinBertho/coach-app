import { ChangeDetectionStrategy, Component, OnInit, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

/** Ce qu'un raccourci de l'écran d'accueil sait viser, et où cela mène selon le rôle. */
const DESTINATIONS: Record<string, { coach: string; athlete: string }> = {
  journee: { coach: '/app/journee', athlete: '/athlete/today' },
  messages: { coach: '/app/messages', athlete: '/athlete/messages' },
  retours: { coach: '/app/feedback', athlete: '/athlete/today' },
};

/**
 * Destination d'un raccourci du manifeste (<code>/go/journee</code>, <code>/go/messages</code>…).
 *
 * <p><b>Pourquoi passer par ici.</b> Le manifeste est <b>unique</b> et sert les deux métiers :
 * un coach et son athlète installent la même application. Ses <code>shortcuts</code> — ce que
 * propose l'appui long sur l'icône — ne peuvent donc pas pointer vers <code>/app/…</code>, qui
 * renverrait l'athlète sur un écran interdit, ni vers <code>/athlete/…</code>, qui ferait
 * l'inverse. Ils visent ces chemins neutres, qui décident au moment du tap.</p>
 *
 * <p>Sans session, on passe par la connexion : elle ramène ensuite chez soi, ce qui reste plus
 * juste que d'ouvrir une page interdite pour la refermer aussitôt.</p>
 */
@Component({
  selector: 'app-shortcut',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p class="field-hint" style="padding: var(--sp-6)">Ouverture…</p>`,
})
export class ShortcutComponent implements OnInit {
  /** Segment d'URL du raccourci (`journee`, `messages`, `retours`). */
  readonly target = input<string>('');

  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (!this.auth.isAuthenticated()) {
      void this.router.navigateByUrl('/login');
      return;
    }
    // Au lancement à froid — le cas normal d'un raccourci — la session existe mais le profil
    // n'est pas encore chargé : sans cette attente, on ne connaîtrait pas le rôle et l'on
    // renverrait tout le monde vers la connexion.
    if (this.auth.currentUser()) {
      void this.router.navigateByUrl(this.destination());
      return;
    }
    this.auth.loadCurrentUser().subscribe({
      next: () => void this.router.navigateByUrl(this.destination()),
      error: () => void this.router.navigateByUrl('/login'),
    });
  }

  private destination(): string {
    const dest = DESTINATIONS[this.target()];
    if (!dest) {
      return this.auth.homeRoute();
    }
    return this.auth.isCoach() ? dest.coach : dest.athlete;
  }
}
