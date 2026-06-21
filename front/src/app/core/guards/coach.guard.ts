import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Réserve l'accès aux rôles coach (HEAD_COACH / COACH). */
export const coachGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }
  const role = auth.currentUser()?.role;
  // currentUser peut ne pas être encore chargé : on autorise si authentifié,
  // le backend reste l'autorité (403 si rôle insuffisant).
  if (!role || role === 'HEAD_COACH' || role === 'COACH') {
    return true;
  }
  return router.createUrlTree(['/']);
};
