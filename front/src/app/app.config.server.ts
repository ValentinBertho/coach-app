import { ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering } from '@angular/platform-server';
import { appConfig } from './app.config';
import { PRERENDER_API_ORIGIN } from './core/interceptors/prerender-base-url.interceptor';

declare const process: { env: Record<string, string | undefined> };

/**
 * Configuration de l'application au pré-rendu.
 *
 * <h2>Pourquoi ce fichier existe</h2>
 *
 * <p>Le produit est une application qui se construit dans le navigateur : l'adresse
 * `/coachs/marie-dupont` sert un `index.html` vide, puis le JavaScript va chercher la fiche et
 * remplit la page. Un navigateur exécute ce JavaScript ; <b>les robots d'aperçu de lien n'en font
 * rien</b>. WhatsApp, Slack, LinkedIn ou iMessage lisent le HTML brut et s'arrêtent là — ils
 * affichaient donc le titre générique de la page d'accueil sous le lien d'un coach.</p>
 *
 * <p>À dix coachs, le bouche-à-oreille est le seul canal d'acquisition, et c'est précisément
 * celui-là qui était cassé. Le pré-rendu fabrique à la compilation un vrai fichier HTML par fiche,
 * déjà rempli, que ces robots savent lire.</p>
 *
 * <p>Pré-rendu <b>seulement</b> : aucun serveur Node en production. Le site reste servi en
 * statique (Vercel, et nginx dans la pile Docker), et c'est ce qui rend ce changement peu risqué
 * — il ajoute des fichiers, il ne déplace pas l'hébergement.</p>
 */
const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(),
    // L'origine de l'API pour la durée du pré-rendu seulement. `environment.apiUrl` vaut `/api`
    // en production : un navigateur résout cette adresse contre l'origine de la page, Node n'en a
    // aucune. Elle est lue ici, dans le seul fichier que le paquet du navigateur n'embarque
    // jamais — y écrire l'adresse de Railway contournerait le proxy Vercel et violerait la CSP.
    //
    // ATTENTION : c'est une ORIGINE — protocole et hôte, sans chemin. Le `/api` vient déjà
    // d'`environment.apiUrl`, et l'y remettre produit `.../api/api/...`, qui répond 404.
    //   ✅ https://coach-app-production-5674.up.railway.app
    //   ❌ https://coach-app-production-5674.up.railway.app/api
    {
      provide: PRERENDER_API_ORIGIN,
      useValue: process.env['PRERENDER_API_ORIGIN'] ?? null,
    },
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
