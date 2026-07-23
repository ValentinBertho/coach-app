import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/** Routes pour lesquelles on n'affiche pas de toast d'erreur global. */
const SILENT_PATTERNS = [/\/auth\//, /\/oauth-callback/, /\/public\/invitations\//];

/**
 * Intercepteur d'erreurs global → toasts par code. Sur 401 d'une route protégée, tente d'abord
 * un rafraîchissement silencieux de l'access token (via le refresh token) puis rejoue la requête ;
 * ne déconnecte qu'en cas d'échec du refresh. Évite la déconnexion à chaque expiration (15 min).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const auth = inject(AuthService);
  const silent = SILENT_PATTERNS.some((re) => re.test(req.url));

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // 401 sur une route protégée : rafraîchir puis rejouer une fois avant d'abandonner.
      if (error.status === 401 && !silent && auth.token() && auth.refreshTokenValue()) {
        return auth.refresh().pipe(
          switchMap(() => {
            const retried = req.clone({
              setHeaders: { Authorization: `Bearer ${auth.token()}` },
              withCredentials: true,
            });
            return next(retried);
          }),
          catchError((refreshError) => {
            auth.logout();
            toast.error('Session expirée, veuillez vous reconnecter.');
            return throwError(() => refreshError);
          }),
        );
      }

      if (!silent) {
        switch (error.status) {
          case 0:
            toast.error('Connexion impossible — vérifiez votre réseau.');
            break;
          case 401:
            auth.logout();
            toast.error('Session expirée, veuillez vous reconnecter.');
            break;
          case 403:
            toast.error('Accès refusé.');
            break;
          case 404:
            toast.error('Ressource introuvable.');
            break;
          default:
            toast.error(error.error?.message ?? 'Une erreur est survenue.');
        }
      }
      return throwError(() => error);
    })
  );
};
