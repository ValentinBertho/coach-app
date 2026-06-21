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
  return auth.currentUser()?.role === 'PLATFORM_ADMIN' ? true : router.createUrlTree(['/app']);
};
