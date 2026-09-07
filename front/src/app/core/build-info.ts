import { environment } from '../../environments/environment';

/**
 * Version réellement déployée : « <version>+<commit> ».
 *
 * <p>Elle est gravée dans l'`index.html` au moment du build (`scripts/stamp-build.mjs`), et lue
 * ici une fois pour toutes. C'est ce qui relie une erreur remontée à un commit précis : la
 * `release` Sentry et le contexte joint aux retours de bêta en dépendent tous les deux.</p>
 *
 * <p>Avant, cette valeur était une chaîne recopiée à la main dans `environment.ts`, « à tenir à
 * jour à chaque déploiement notable ». Elle ne l'était pas : tous les événements portaient le
 * même numéro, et « ça marchait hier » restait indécidable (OPS-09).</p>
 *
 * <p>Repli sur `environment.appVersion` quand la balise est absente ou non tamponnée — un build
 * lancé sans passer par npm, une page servie hors du `dist` construit. Le front fonctionne, il
 * est simplement moins précis sur son identité.</p>
 */
let cached: string | undefined;

export function buildStamp(): string {
  if (cached !== undefined) return cached;
  cached = readStamp() ?? environment.appVersion;
  return cached;
}

function readStamp(): string | null {
  if (typeof document === 'undefined') return null;
  const meta = document.querySelector('meta[name="dari-build"]');
  const value = meta?.getAttribute('content')?.trim();
  // « dev » est la valeur du gabarit : elle ne désigne aucun déploiement.
  return value && value !== 'dev' ? value : null;
}

/** Pour les tests : oublie la valeur mémorisée. */
export function resetBuildStampCache(): void {
  cached = undefined;
}
