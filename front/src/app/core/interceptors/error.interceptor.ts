import { HttpErrorResponse, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, tap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { FeedbackService } from '../services/feedback.service';
import { NetworkStatusService } from '../services/network-status.service';
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
 * Tuyauterie de fond : ces appels partent tout seuls, sans que l'utilisateur ait rien demandé.
 * Leur échec ne doit donc jamais lui être annoncé — mais il reste un échec ordinaire, qui
 * profite du rafraîchissement de jeton comme les autres (contrairement aux deux listes
 * ci-dessus, qui court-circuitent tout).
 *
 * <p>Les notifications push en sont l'exemple : l'application réenregistre l'abonnement du
 * navigateur à chaque démarrage, y compris avant toute connexion. Sur l'écran de connexion,
 * cet appel répondait 401 et l'utilisateur voyait « Session expirée, reconnecte-toi » avant
 * même d'avoir tapé son mot de passe. Et lorsqu'il activait les notifications, l'échec
 * s'affichait deux fois : une par ce toast, une par le bouton.</p>
 */
const BACKGROUND_PATTERNS = [/\/push\//];

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
 * Fin de session subie : on oublie la session <b>localement</b>, sans rien révoquer côté serveur.
 *
 * <p>Un rafraîchissement qui échoue parce que le réseau a sauté (statut 0) ou parce que le
 * serveur redémarre (5xx) ne dit rien de la validité des jetons : on garde la session et
 * l'utilisateur réessaiera. Seul un refus explicite du serveur (401/403 : jeton révoqué,
 * expiré, mot de passe changé) met fin à la session.</p>
 */
function endSession(auth: AuthService, toast: ToastService, error: HttpErrorResponse): void {
  // Seul un refus explicite du serveur met fin à une session. Tout le reste — réseau coupé,
  // serveur en redéploiement, plafond de requêtes atteint — dit « réessaie », pas « ton compte
  // n'est plus valable ».
  //
  // Le 429 était le plus coûteux des cas mal classés : le rafraîchissement de jeton partageait
  // le plafond anti-devinage de mot de passe, si bien qu'une application ouverte deux fois dans
  // la minute suffisait à le franchir. Le client en concluait une session morte et déconnectait
  // un utilisateur parfaitement authentifié.
  if (error.status !== 401 && error.status !== 403) {
    toast.error(error.status === 429
      ? 'Trop de requêtes — réessaie dans quelques secondes.'
      : 'Service momentanément indisponible — réessaie dans un instant.');
    return;
  }
  // Une salve échoue en bloc. Au retour au premier plan, le premier écran lance une dizaine de
  // requêtes d'un coup : avec un jeton refusé, chacune passait ici et redemandait un
  // rafraîchissement — d'où « Session expirée, reconnecte-toi ×9 », et surtout neuf appels de
  // rotation là où un seul avait un sens. La première fin de session vide le jeton ; les
  // suivantes n'ont plus rien à terminer.
  if (!auth.isAuthenticated()) {
    return;
  }
  auth.expireSession();
  toast.error('Session expirée, reconnecte-toi.');
}

/**
 * Intercepteur d'erreurs global → toasts par code. Sur 401 d'une route protégée, tente d'abord
 * un rafraîchissement silencieux de l'access token (via le refresh token) puis rejoue la requête ;
 * ne met fin à la session que si le rafraîchissement lui-même est refusé.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastService);
  const auth = inject(AuthService);
  const network = inject(NetworkStatusService);
  const feedback = inject(FeedbackService);
  /** L'appelant se charge de l'erreur : ni toast, ni rafraîchissement, ni fin de session. */
  const callerHandles =
    SILENT_PATTERNS.some((re) => re.test(req.url))
    || FORM_HANDLED_PATTERNS.some((re) => re.test(req.url));
  /** Rien à annoncer à l'utilisateur — mais le reste du traitement s'applique normalement. */
  const quiet = callerHandles || BACKGROUND_PATTERNS.some((re) => re.test(req.url));

  return next(req).pipe(
    // Une réponse reçue, quel qu'en soit le code, prouve que l'API répond : c'est ce qui fait
    // retomber le bandeau « service momentanément indisponible » sans attendre de sonde dédiée.
    tap((event) => { if (event instanceof HttpResponse) network.reportApiSuccess(); }),
    catchError((error: HttpErrorResponse) => {
      // Aucune réponse exploitable : réseau coupé, ou serveur en cours de redéploiement. Le
      // distinguer permet d'afficher un bandeau honnête au lieu d'accuser le réseau de
      // l'utilisateur (Railway redémarre l'instance à chaque déploiement).
      if (error.status === 0 || error.status === 502 || error.status === 503 || error.status === 504) {
        network.reportApiFailure();
      } else {
        network.reportApiSuccess();
      }
      // 401 sur une route protégée : rafraîchir puis rejouer une fois avant d'abandonner.
      if (error.status === 401 && !callerHandles && auth.token() && auth.refreshTokenValue()) {
        return auth.refresh().pipe(
          // Le catchError est placé ICI, sur le rafraîchissement seul, et non après le rejeu.
          // En aval, il attrapait aussi l'échec de la requête rejouée : un 500, un 404 ou une
          // coupure réseau d'une demi-seconde — la vie ordinaire d'un téléphone — mettait fin
          // à la session alors que le rafraîchissement venait de réussir.
          catchError((refreshError: HttpErrorResponse) => {
            endSession(auth, toast, refreshError);
            return throwError(() => refreshError);
          }),
          switchMap(() => {
            const retried = req.clone({
              setHeaders: { Authorization: `Bearer ${auth.token()}` },
              withCredentials: true,
            });
            return next(retried);
          }),
        );
      }

      if (!quiet) {
        switch (error.status) {
          case 0:
            // Ne plus accuser le réseau de l'utilisateur quand l'appareil est en ligne : la
            // cause la plus fréquente en bêta est un redéploiement du serveur.
            toast.error(network.online()
              ? 'Service momentanément indisponible — nouvelle tentative dans un instant.'
              : 'Connexion impossible — vérifie ton réseau.');
            break;
          case 502:
          case 503:
          case 504:
            toast.error('Service momentanément indisponible — réessaie dans un instant.');
            break;
          case 401:
            endSession(auth, toast, error);
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
            // Le serveur joint un identifiant de corrélation à chaque erreur interne
            // (GlobalExceptionHandler) ; personne ne le récupérait. Mémorisé ici, il part avec
            // le prochain retour de bêta et relie « ça a planté » à une trace précise.
            feedback.rememberCorrelation(error.error?.correlationId);
            toast.error(error.error?.message ?? 'Une erreur est survenue.');
        }
      }
      return throwError(() => error);
    })
  );
};
