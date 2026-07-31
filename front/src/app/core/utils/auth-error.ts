import { HttpErrorResponse } from '@angular/common/http';

/**
 * Message affichable pour une erreur des formulaires d'authentification.
 *
 * Le serveur renvoie déjà un libellé français utilisable (`ApiError.message` du
 * `GlobalExceptionHandler`, ou le corps du 429 du `RateLimitFilter`) : on le privilégie, et on ne
 * retombe sur un texte générique que s'il est absent. Couvre 401 (identifiants / compte suspendu),
 * 409 (e-mail déjà utilisé), 429 (rate limit) et la perte de réseau.
 */
export function authErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Une erreur est survenue. Réessaie dans un instant.';
  }
  const serverMessage: string | undefined = error.error?.message;
  switch (error.status) {
    case 0:
      return 'Connexion impossible — vérifie ton réseau.';
    case 400:
      return serverMessage ?? 'Les informations saisies sont invalides.';
    case 401:
      return serverMessage ?? 'E-mail ou mot de passe incorrect.';
    case 403:
      return serverMessage ?? 'Accès refusé.';
    case 409:
      return serverMessage ?? 'Un compte existe déjà avec cette adresse e-mail.';
    case 429:
      return serverMessage ?? 'Trop de tentatives — réessaie dans une minute.';
    default:
      return serverMessage ?? 'Une erreur est survenue. Réessaie dans un instant.';
  }
}
