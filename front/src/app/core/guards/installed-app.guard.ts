import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * L'application installée ne montre pas de page de vente : elle ouvre sur la connexion.
 *
 * <p>Le `start_url` du manifeste est la racine, donc la landing publique. Un athlète qui lance
 * Darilab depuis son écran d'accueil tombait sur l'argumentaire produit et devait chercher le
 * bouton « Se connecter » — là où toute autre application affiche directement l'écran de
 * connexion. Une fois la session ouverte, la landing renvoie déjà chez soi ; c'est le cas
 * déconnecté qui manquait.</p>
 *
 * <p>La règle ne vaut que pour l'application <b>installée</b> : sur le web ouvert, la racine
 * reste la page publique, qui est son seul point d'entrée pour un visiteur.</p>
 */
export const installedAppGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated() || !isInstalledApp()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};

/**
 * Application lancée depuis l'écran d'accueil ?
 *
 * <p>`display-mode` couvre Android et les navigateurs de bureau ; `navigator.standalone` est la
 * réponse d'iOS, qui n'a longtemps pas implémenté la media query. Les deux sont nécessaires,
 * et l'absence des deux (contexte de test, navigateur ancien) vaut « non installée » — le
 * comportement public, donc le plus sûr.</p>
 */
function isInstalledApp(): boolean {
  const modes = ['standalone', 'fullscreen', 'minimal-ui'];
  const byMediaQuery = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    && modes.some((mode) => window.matchMedia(`(display-mode: ${mode})`).matches);
  const iOS = typeof navigator !== 'undefined'
    && (navigator as { standalone?: boolean }).standalone === true;
  return byMediaQuery || iOS;
}
