/**
 * L'application tourne-t-elle **installée** sur l'appareil (écran d'accueil, dock), plutôt que
 * dans un onglet de navigateur ?
 *
 * <p>`display-mode` couvre Android et les navigateurs de bureau ; `navigator.standalone` est la
 * réponse d'iOS, qui n'a longtemps pas implémenté la media query. Les deux sont nécessaires, et
 * l'absence des deux (contexte de test, navigateur ancien) vaut « non installée » — le
 * comportement public, donc le plus sûr.</p>
 *
 * <p>Le test vivait en double, dans le garde de route et dans le service de push, avec deux
 * écritures différentes. Une troisième copie allait naître avec l'invitation au premier
 * lancement : autant n'en garder qu'une, puisque la question est la même.</p>
 */
export function isInstalledApp(): boolean {
  const modes = ['standalone', 'fullscreen', 'minimal-ui'];
  const byMediaQuery = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    && modes.some((mode) => window.matchMedia(`(display-mode: ${mode})`).matches);
  const iOS = typeof navigator !== 'undefined'
    && (navigator as { standalone?: boolean }).standalone === true;
  return byMediaQuery || iOS;
}
