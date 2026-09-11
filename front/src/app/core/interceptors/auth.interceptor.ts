import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { AuthService } from '../services/auth.service';

/** Routes qui ne doivent pas recevoir le Bearer (entrée publique de l'auth). */
const NO_AUTH = ['/public/', '/auth/login', '/auth/register', '/auth/refresh'];

/**
 * Ajoute le Bearer JWT et `withCredentials`, sauf sur les routes publiques — et annonce la
 * version du front sur **toutes** les requêtes.
 *
 * <p><b>Pourquoi la version voyage ici.</b> Le front est une PWA à service worker : un téléphone
 * peut rester des jours sur une version antérieure (cf. Claude.md §4 bis). Quand quelqu'un
 * signale « ça ne marche pas », la première question est « tu es sur quelle version ? » — et
 * personne ne sait y répondre. L'en-tête la donne sans avoir à demander ; le serveur la garde
 * sur le compte, dans une écriture qui avait déjà lieu.</p>
 *
 * <p>Posée aussi sur les routes publiques : une connexion qui échoue est précisément le moment
 * où l'on veut savoir depuis quelle version elle a été tentée.</p>
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const bypass = NO_AUTH.some((p) => req.url.includes(p));

  req = req.clone({ setHeaders: { 'X-App-Version': environment.appVersion } });

  const token = auth.token();
  if (token && !bypass) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
      withCredentials: true,
    });
  }
  return next(req);
};
