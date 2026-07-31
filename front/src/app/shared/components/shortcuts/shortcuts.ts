/** Un raccourci : la combinaison à afficher et ce qu'elle fait. */
export interface Shortcut {
  /** Touches déjà formatées pour l'affichage, ex. `['Maj', 'clic']` ou `['mod', 'C']`. */
  readonly keys: readonly string[];
  readonly label: string;
}

export interface ShortcutGroup {
  readonly title: string;
  readonly items: readonly Shortcut[];
}

/**
 * Vrai sur les claviers Apple. La touche de commande n'a pas le même nom ni le même symbole
 * selon la plateforme : afficher « Ctrl » à un utilisateur de Mac, c'est lui donner un
 * raccourci qui ne marche pas.
 */
export function isApplePlatform(): boolean {
  if (typeof navigator === 'undefined') return false;
  return /Mac|iPhone|iPad|iPod/.test(navigator.platform || navigator.userAgent);
}

/** `'mod'` → « ⌘ » ou « Ctrl » selon la plateforme ; les autres touches passent telles quelles. */
export function displayKey(key: string): string {
  if (key !== 'mod') return key;
  return isApplePlatform() ? '⌘' : 'Ctrl';
}

/** Rend un raccourci en une chaîne (infobulles, entrées de menu) : « ⌘ + C ». */
export function shortcutText(keys: readonly string[]): string {
  return keys.map(displayKey).join(' + ');
}
