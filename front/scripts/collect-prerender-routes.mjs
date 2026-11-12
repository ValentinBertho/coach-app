/**
 * Construit la liste des adresses à pré-rendre.
 *
 * ## Pourquoi ce script existe
 *
 * Le pré-rendu d'Angular part d'une liste d'adresses fixée à la compilation. Les pages statiques
 * — l'accueil, l'annuaire, les mentions légales — se listent à la main. Les fiches de coachs, non :
 * leur adresse contient un `slug` qui n'existe qu'en base. Il faut donc demander au serveur quels
 * coachs sont publiés, au moment de compiler.
 *
 * ## Ce qui se passe si le serveur ne répond pas
 *
 * **La compilation continue**, avec les seules pages statiques. C'est le point important de ce
 * fichier, et il est délibéré : sans cette clémence, la mise en ligne du site deviendrait
 * dépendante de la disponibilité de l'API — une API en maintenance, et l'on ne pourrait plus
 * déployer une correction de style. Le prix de ce choix est qu'un déploiement fait pendant une
 * panne d'API produit un site sans fiches pré-rendues ; il est annoncé bruyamment dans la sortie,
 * et le déploiement suivant les rétablit.
 *
 * ## La variable d'environnement
 *
 * `PRERENDER_API_ORIGIN` est une ORIGINE — protocole et hôte, sans chemin :
 * `https://coach-app-production-5674.up.railway.app`. Le `/api` est ajouté ici et, côté
 * application, par `environment.apiUrl` ; l'inclure dans la variable produit `/api/api/...`.
 *
 * ## La limite à connaître
 *
 * Ces fichiers sont figés à la compilation. Un coach qui publie sa fiche après le déploiement
 * n'est pas pré-rendu : sa page fonctionne normalement dans un navigateur, mais l'aperçu d'un
 * lien partagé restera générique jusqu'au déploiement suivant. Tenable à dix coachs, à
 * réexaminer bien avant cent (cf. l'audit du hub, lot 7).
 */
import { writeFileSync } from 'node:fs';

/** Les pages publiques qui ne dépendent d'aucune donnée. */
const STATIC_ROUTES = [
  '/',
  '/coachs',
  '/inscription-athlete',
  '/legal/cgu',
  '/legal/confidentialite',
  '/legal/mentions-legales',
  // `/support` est servi par le même composant que les pages légales, sur sa propre adresse :
  // les partenaires d'intégration (COROS) exigent une page d'aide joignable sans compte.
  '/support',
];

const origin = process.env.PRERENDER_API_ORIGIN;
const output = process.argv[2] ?? 'prerender-routes.txt';
const routes = [...STATIC_ROUTES];

if (!origin) {
  console.warn(
    '[pré-rendu] PRERENDER_API_ORIGIN absent : seules les pages statiques seront pré-rendues.\n'
    + "            Les aperçus de liens de fiches coachs resteront génériques.",
  );
} else {
  try {
    // Une page large : à dix coachs elle les prend tous, et le jour où il y en aura trop pour
    // une page, la boucle manquante se verra dans le compte affiché ci-dessous.
    const response = await fetch(`${origin.replace(/\/$/, '')}/api/public/coaches?page=0&size=200`, {
      signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok) {
      throw new Error(`réponse ${response.status}`);
    }
    const body = await response.json();
    const slugs = (body.content ?? []).map((c) => c.slug).filter(Boolean);
    routes.push(...slugs.map((slug) => `/coachs/${slug}`));
    console.log(`[pré-rendu] ${slugs.length} fiche(s) de coach à pré-rendre.`);
    if (body.totalElements > slugs.length) {
      console.warn(
        `[pré-rendu] ${body.totalElements} fiches publiées mais ${slugs.length} récupérées :`
        + ' la pagination est à reprendre.',
      );
    }
  } catch (error) {
    // Volontairement non fatal : cf. l'en-tête. Bruyant, en revanche — une compilation qui perd
    // silencieusement les fiches serait pire que pas de pré-rendu du tout.
    console.warn(
      `[pré-rendu] API injoignable (${error.message}) : compilation poursuivie sans les fiches`
      + ' coachs. Leurs aperçus de liens resteront génériques jusqu’au prochain déploiement.',
    );
  }
}

writeFileSync(output, routes.join('\n') + '\n');
console.log(`[pré-rendu] ${routes.length} adresse(s) écrites dans ${output}.`);
