import { HttpInterceptorFn } from '@angular/common/http';
import { InjectionToken, inject } from '@angular/core';

/**
 * L'origine à préfixer aux appels d'API pendant le pré-rendu. `null` dans le navigateur.
 *
 * <p>Fournie uniquement par la configuration serveur, qui la lit dans l'environnement de
 * compilation. Le navigateur ne la voit jamais, et c'est le but : en production, l'application
 * appelle `/api` en relatif, ce que le proxy Vercel achemine vers Railway. Écrire l'adresse
 * absolue de Railway dans le paquet du navigateur contournerait ce proxy <b>et</b> violerait la
 * politique de sécurité de contenu, qui n'autorise `connect-src` que vers `'self'`.</p>
 */
export const PRERENDER_API_ORIGIN = new InjectionToken<string | null>('PRERENDER_API_ORIGIN', {
  providedIn: 'root',
  factory: () => null,
});

/**
 * Rend absolues, au pré-rendu seulement, les adresses d'API écrites en relatif.
 *
 * <p>`environment.apiUrl` vaut `/api` en production. Un navigateur résout cette adresse contre
 * l'origine de la page ; <b>Node n'a pas d'origine</b>, et `HttpClient` échoue donc sur toute
 * requête relative pendant le pré-rendu — c'est-à-dire sur celle qui va chercher la fiche du
 * coach, la seule raison d'être de l'opération.</p>
 *
 * <p>Inerte dans le navigateur : le jeton n'y est jamais fourni, la requête passe telle quelle.</p>
 */
export const prerenderBaseUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const origin = inject(PRERENDER_API_ORIGIN);
  if (!origin || !req.url.startsWith('/')) {
    return next(req);
  }
  return next(req.clone({ url: origin.replace(/\/$/, '') + req.url }));
};
