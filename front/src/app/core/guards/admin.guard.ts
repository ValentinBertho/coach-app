import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Réserve l'accès au back office d'administration (rôle PLATFORM_ADMIN). */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  // Renvoi vers l'accueil du rôle réel, et non vers /app en dur : un athlète y atterrissait
  // dans le cockpit coach.
  return auth.currentUser()?.role === 'PLATFORM_ADMIN'
    ? true
    : router.createUrlTree([auth.homeRoute()]);
};
