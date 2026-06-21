import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/** Routes pour lesquelles on n'affiche pas de toast d'erreur global. */
const SILENT_PATTERNS = [/\/auth\//, /\/oauth-callback/, /\/public\/invitations\//];

/**
 * Intercepteur d'erreurs global → toasts par code, logout sur 401 (cf. Techno.md §2).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const auth = inject(AuthService);
  const silent = SILENT_PATTERNS.some((re) => re.test(req.url));

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
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
