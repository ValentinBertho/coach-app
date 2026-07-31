import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

/**
 * Routes où un 401 fait partie du fonctionnement normal (jeton expiré au démarrage, refresh en
 * échec) : aucun toast, aucune déconnexion. `/auth/**` en entier — l'ancienne valeur — rendait
 * muettes les erreurs de connexion et d'inscription, que l'utilisateur ne voyait donc jamais.
 */
const SILENT_PATTERNS = [/\/auth\/refresh$/, /\/auth\/me$/];

/**
 * Routes dont l'écran appelant affiche lui-même le message d'erreur du serveur (formulaires
 * d'authentification). Pas de toast global — le message est rendu à côté du formulaire, là où
 * l'utilisateur regarde — mais l'erreur reste propagée à l'appelant.
 */
const FORM_HANDLED_PATTERNS = [
  /\/auth\/login$/,
  /\/auth\/register$/,
  /\/auth\/change-password$/,
  /\/oauth-callback/,
  /\/public\/invitations\//,
  /\/public\/coach-invitations\//,
  /\/public\/password-reset/,
];

/**
 * Message d'un 400 de validation. Le `GlobalExceptionHandler` renvoie déjà un `fieldErrors`
 * {champ: message} : afficher « Requête invalide » sans dire quel champ oblige l'utilisateur à
 * deviner. On nomme les champs fautifs (au plus trois, le toast n'est pas un formulaire).
 */
function validationMessage(error: HttpErrorResponse): string {
  const fieldErrors: Record<string, string> | undefined = error.error?.fieldErrors;
  const entries = fieldErrors ? Object.entries(fieldErrors) : [];
  if (entries.length === 0) {
    return error.error?.message ?? 'Requête invalide.';
  }
  const shown = entries.slice(0, 3).map(([field, message]) => `${label(field)} : ${message}`);
  const rest = entries.length - shown.length;
  return shown.join(' · ') + (rest > 0 ? ` (+${rest})` : '');
}

/** Nom de champ lisible : `targetDistanceM` → « Target distance m ». */
function label(field: string): string {
  const words = field.replace(/([A-Z])/g, ' $1').trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * Intercepteur d'erreurs global → toasts par code. Sur 401 d'une route protégée, tente d'abord
 * un rafraîchissement silencieux de l'access token (via le refresh token) puis rejoue la requête ;
 * ne déconnecte qu'en cas d'échec du refresh. Évite la déconnexion à chaque expiration (15 min).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const auth = inject(AuthService);
  const silent =
    SILENT_PATTERNS.some((re) => re.test(req.url))
    || FORM_HANDLED_PATTERNS.some((re) => re.test(req.url));

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
            toast.error('Session expirée, reconnecte-toi.');
            return throwError(() => refreshError);
          }),
        );
      }

      if (!silent) {
        switch (error.status) {
          case 0:
            toast.error('Connexion impossible — vérifie ton réseau.');
            break;
          case 401:
            auth.logout();
            toast.error('Session expirée, reconnecte-toi.');
            break;
          case 403:
            toast.error('Accès refusé.');
            break;
          case 404:
            toast.error('Ressource introuvable.');
            break;
          case 400:
            toast.error(validationMessage(error));
            break;
          case 413:
            toast.error(error.error?.message ?? 'Fichier trop volumineux.');
            break;
          case 429:
            toast.error(error.error?.message ?? 'Trop de requêtes — réessaie dans un instant.');
            break;
          default:
            toast.error(error.error?.message ?? 'Une erreur est survenue.');
        }
      }
      return throwError(() => error);
    })
  );
};
