#!/usr/bin/env node
/**
 * Grave la version déployée dans l'`index.html` produit par le build.
 *
 * ## Pourquoi
 *
 * La version du front était une chaîne recopiée à la main dans `environment.ts`, « à tenir à jour
 * à chaque déploiement notable ». Elle ne l'était pas — et comme c'est elle qui sert de `release`
 * à Sentry et de contexte aux retours de bêta, tous les événements portaient la même valeur : une
 * erreur ne pointait sur aucun code en particulier, et « ça marchait hier » restait indécidable.
 * C'est le point OPS-09 du plan de conformité.
 *
 * ## Pourquoi dans `dist/`, et pas dans les sources
 *
 * Écrire un fichier TypeScript généré avant le build salirait l'arbre de travail à chaque `npm
 * run build` (le tampon change à chaque commit) et casserait la compilation dès que quelqu'un
 * appelle `ng build` sans passer par npm. Ici, rien n'est généré côté source : on remplace un
 * attribut dans le HTML déjà construit. Sans ce script, le front fonctionne — il affiche
 * simplement la version de repli d'`environment.ts`.
 *
 * ## D'où vient le commit
 *
 * De la plateforme de build, dans l'ordre : Vercel (`VERCEL_GIT_COMMIT_SHA`), GitHub Actions
 * (`GITHUB_SHA`), une variable posée à la main (`APP_COMMIT`), puis le dépôt local. Faute de
 * tout cela, `inconnu` — dit explicitement plutôt que passé sous silence.
 *
 * Usage : node scripts/stamp-build.mjs [chemin/vers/index.html]
 */
import { execSync } from 'node:child_process';
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const target = resolve(process.argv[2] ?? join(here, '..', 'dist', 'front', 'browser', 'index.html'));

const version = JSON.parse(readFileSync(join(here, '..', 'package.json'), 'utf8')).version;

function commit() {
  const fromEnv = process.env.VERCEL_GIT_COMMIT_SHA
    || process.env.GITHUB_SHA
    || process.env.APP_COMMIT;
  if (fromEnv) return fromEnv.slice(0, 7);
  try {
    return execSync('git rev-parse --short=7 HEAD', { stdio: ['ignore', 'pipe', 'ignore'] })
      .toString().trim();
  } catch {
    return 'inconnu';
  }
}

const stamp = `${version}+${commit()}`;

if (!existsSync(target)) {
  // Pas une erreur : `ng build` a pu écrire ailleurs (configuration `pwa`, sortie personnalisée).
  // Le front reste fonctionnel, avec la version de repli.
  console.warn(`[stamp-build] ${target} introuvable — tampon "${stamp}" non gravé.`);
  process.exit(0);
}

const html = readFileSync(target, 'utf8');
const stamped = html.replace(
  /(<meta\s+name="dari-build"\s+content=")[^"]*(")/,
  `$1${stamp}$2`,
);

if (stamped === html) {
  console.warn(`[stamp-build] balise <meta name="dari-build"> absente de ${target}.`);
  process.exit(0);
}

writeFileSync(target, stamped);
console.log(`[stamp-build] version déployée : ${stamp}`);
