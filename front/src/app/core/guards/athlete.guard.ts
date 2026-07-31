import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Réserve l'accès au portail athlète (rôle ATHLETE). */
export const athleteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  // Même règle que les autres gardes : on renvoie chacun chez lui, jamais vers /app en dur.
  return auth.currentUser()?.role === 'ATHLETE' ? true : router.createUrlTree([auth.homeRoute()]);
};
