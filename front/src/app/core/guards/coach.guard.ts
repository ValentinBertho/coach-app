import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Réserve le cockpit coach (`/app`) aux rôles qui en ont l'usage : COACH, HEAD_COACH et
 * PLATFORM_ADMIN (vers qui `adminGuard` renvoie).
 *
 * <p>Ce garde existait déjà mais n'était branché nulle part : `/app` se contentait de
 * `authGuard`, qui ne vérifie que l'authentification. Un athlète connecté ouvrait donc le
 * cockpit coach et le voyait se remplir de toasts « Accès refusé » — un 403 par appel, le
 * serveur faisant correctement son travail. Aucune donnée ne fuitait, mais c'était l'écran
 * d'accueil de la PWA pour la moitié des utilisateurs.</p>
 *
 * <p>La redirection va vers l'accueil du rôle réel (`AuthService.homeRoute()`), pas vers la
 * landing publique : un athlète renvoyé sur `/` n'y trouve qu'un bouton « Accéder à mon
 * espace ».</p>
 */
export const coachGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  return auth.isCoach() ? true : router.createUrlTree([auth.homeRoute()]);
};
