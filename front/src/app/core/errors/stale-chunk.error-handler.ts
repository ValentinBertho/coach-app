import { ErrorHandler } from '@angular/core';

/**
 * Un écran chargé en différé dont le fichier a disparu du serveur : on recharge.
 *
 * <h2>Ce que cela corrige</h2>
 *
 * <p>Tous les écrans sont chargés à la demande (`loadComponent`). Ouvrir une fiche de séance
 * télécharge donc un fichier au moment du clic. Après un déploiement, un onglet resté ouvert
 * demande le fichier de l'<b>ancienne</b> version : il n'existe plus, et Angular annule la
 * navigation <b>sans rien afficher</b>. Vu du coach, le clic ne marche pas — pas de message, pas
 * d'écran, rien. Relevé en production :</p>
 *
 * <pre>
 * Failed to load module script: Expected a JavaScript-or-Wasm module script but the server
 * responded with a MIME type of "text/html".
 * TypeError: Failed to fetch dynamically imported module: https://…/chunk-B6CRKH3L.js
 * </pre>
 *
 * <p>Recharger la page remettait tout d'aplomb — encore fallait-il deviner qu'il fallait
 * recharger. C'est ce que fait ce gestionnaire à la place de l'utilisateur. Il ne masque pas un
 * défaut : le fichier demandé n'existe réellement plus, et la seule issue est de repartir de
 * l'`index.html` courant.</p>
 *
 * <h2>Le garde-fou</h2>
 *
 * <p>Au plus un rechargement par tranche de dix minutes et par onglet. Une page qui échouerait
 * encore aussitôt après ne boucle donc pas : l'erreur suit son cours normal (journal, Sentry).
 * Le délai laisse en revanche passer un déploiement ultérieur — un onglet ouvert plusieurs jours
 * reste rattrapé à chaque fois. Le repère vit dans {@code sessionStorage} : propre à l'onglet,
 * effacé à sa fermeture.</p>
 */
export class StaleChunkErrorHandler implements ErrorHandler {

  /** Deux échecs rapprochés ne viennent pas d'un déploiement : on cesse de recharger. */
  private static readonly COOLDOWN_MS = 10 * 60 * 1000;
  private static readonly LAST_RELOAD_KEY = 'darilab.stale-chunk-reload';

  /**
   * Signatures des trois moteurs. Le libellé diffère, le fait est le même : le navigateur n'a
   * pas pu charger un module demandé à la volée.
   */
  private static readonly SIGNATURES = [
    'failed to fetch dynamically imported module',   // Chrome, Edge
    'error loading dynamically imported module',     // Firefox
    'importing a module script failed',              // Safari
    'failed to load module script',                  // type MIME inattendu (index.html renvoyé)
  ];

  /**
   * @param delegate gestionnaire en aval : Sentry en production, celui d'Angular sinon.
   * @param reloadPage le rechargement lui-même. Paramétré parce que `location.reload` n'est ni
   *     remplaçable ni espionnable dans les navigateurs récents : sans cette prise, aucun test
   *     ne peut vérifier ce gestionnaire sans recharger la page qui l'exécute.
   */
  constructor(
    private readonly delegate: ErrorHandler,
    private readonly reloadPage: () => void = () => location.reload(),
  ) {}

  handleError(error: unknown): void {
    if (StaleChunkErrorHandler.isStaleChunk(error) && this.reload()) return;
    this.delegate.handleError(error);
  }

  private static isStaleChunk(error: unknown): boolean {
    const message = (error instanceof Error ? error.message : String(error ?? '')).toLowerCase();
    return StaleChunkErrorHandler.SIGNATURES.some((signature) => message.includes(signature));
  }

  /** Recharge si le dernier rechargement est assez ancien. Vrai si le rechargement est lancé. */
  private reload(): boolean {
    let last = 0;
    try {
      last = Number(sessionStorage.getItem(StaleChunkErrorHandler.LAST_RELOAD_KEY)) || 0;
      if (Date.now() - last < StaleChunkErrorHandler.COOLDOWN_MS) return false;
      sessionStorage.setItem(StaleChunkErrorHandler.LAST_RELOAD_KEY, String(Date.now()));
    } catch {
      // Navigation privée, stockage refusé : sans mémoire, rien ne garantit qu'on ne rechargera
      // pas en boucle. On s'abstient — l'erreur remonte, et l'utilisateur recharge lui-même.
      return false;
    }
    this.reloadPage();
    return true;
  }
}
