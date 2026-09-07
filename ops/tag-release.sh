#!/usr/bin/env bash
# ============================================================================
# Darilab — pose le tag Git d'une version, et le pousse.
#
# ## Pourquoi ce script existe
#
# Rien ne reliait une instance en ligne à un commit (OPS-09 du plan de conformité). Une erreur
# remontée par un utilisateur portait un identifiant de corrélation, une heure, un écran — et
# aucune façon de savoir quel code l'avait produite. Deux déploiements du même « 0.3.0 » étaient
# indiscernables.
#
# Deux moitiés répondent à cette question, et elles sont complémentaires :
#
#   1. Le COMMIT, exposé à chaud — /api/actuator/info côté back, balise <meta name="dari-build">
#      côté front, et `release` Sentry des deux côtés. C'est ce qui répond à « quel code tourne
#      là, maintenant ? », automatiquement, sans rien à tenir à jour.
#   2. Le TAG, posé ici — un nom lisible sur un commit, pour parler d'une version entre humains,
#      comparer deux livraisons, et revenir en arrière sans chercher une empreinte dans un
#      journal.
#
# ## Ce que le script vérifie avant de taguer
#
#   - l'arbre de travail est propre (un tag sur un état non commité ne désigne rien) ;
#   - la version du back (`back/pom.xml`) et celle du front (`front/package.json`) sont les
#     mêmes — sinon « v0.4.0 » voudrait dire deux choses ;
#   - le tag n'existe pas déjà, ni en local ni sur le dépôt distant.
#
# ## Usage
#
#   ./ops/tag-release.sh              # tague la version lue dans les manifestes
#   ./ops/tag-release.sh --dry-run    # vérifie tout, ne pousse rien
#
# La version se change dans `back/pom.xml` ET `front/package.json`, dans le commit qui précède.
# ============================================================================
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

DRY_RUN=0
[ "${1:-}" = "--dry-run" ] && DRY_RUN=1

red()   { printf '\033[31m✗\033[0m %s\n' "$1"; }
green() { printf '\033[32m✓\033[0m %s\n' "$1"; }
info()  { printf '  %s\n' "$1"; }

# --- Les deux versions, lues dans leurs manifestes ---------------------------
# La version du back est la PREMIÈRE <version> qui suit </parent> : celle du parent Spring Boot
# la précède dans le fichier, et c'est elle qu'attrape une lecture naïve.
BACK_VERSION=$(sed -n '/<\/parent>/,$p' back/pom.xml \
  | grep -m1 -oE '<version>[^<]+</version>' \
  | sed -E 's:</?version>::g')
FRONT_VERSION=$(node -p "require('./front/package.json').version" 2>/dev/null)

if [ -z "$BACK_VERSION" ] || [ -z "$FRONT_VERSION" ]; then
  red "Version illisible (back='$BACK_VERSION', front='$FRONT_VERSION')."
  exit 1
fi

if [ "$BACK_VERSION" != "$FRONT_VERSION" ]; then
  red "Les deux moitiés n'annoncent pas la même version :"
  info "back/pom.xml       : $BACK_VERSION"
  info "front/package.json : $FRONT_VERSION"
  info "Aligner les deux avant de taguer — sinon le tag désigne deux choses."
  exit 1
fi
green "Version : $BACK_VERSION (back et front alignés)"

TAG="v$BACK_VERSION"

# --- Arbre propre ------------------------------------------------------------
if [ -n "$(git status --porcelain)" ]; then
  red "L'arbre de travail n'est pas propre : le tag ne désignerait pas ce que tu as sous les yeux."
  git status --short
  exit 1
fi
green "Arbre de travail propre"

# --- Le tag est libre --------------------------------------------------------
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  red "Le tag $TAG existe déjà en local."
  info "Changer la version dans les manifestes, ou supprimer le tag (git tag -d $TAG)."
  exit 1
fi
if git ls-remote --exit-code --tags origin "$TAG" >/dev/null 2>&1; then
  red "Le tag $TAG existe déjà sur origin — il a donc déjà désigné une livraison."
  exit 1
fi
green "Le tag $TAG est libre"

COMMIT=$(git rev-parse --short=7 HEAD)
BRANCH=$(git rev-parse --abbrev-ref HEAD)
info "Commit : $COMMIT (branche $BRANCH)"

if [ "$DRY_RUN" -eq 1 ]; then
  green "--dry-run : tout est prêt, rien n'a été posé."
  exit 0
fi

# --- Pose et pousse ----------------------------------------------------------
# Tag ANNOTÉ : il porte un auteur et une date, et `git describe` ne le confond pas avec un
# marqueur temporaire. C'est ce qui en fait une trace de livraison plutôt qu'un signet.
git tag -a "$TAG" -m "DARI Lab $BACK_VERSION — $COMMIT" || exit 1
git push origin "$TAG" || { red "Échec du push du tag."; exit 1; }

green "Tag $TAG posé sur $COMMIT et poussé."
info "Reporter ce commit dans SENTRY_RELEASE côté back au prochain déploiement,"
info "et vérifier /api/actuator/info une fois en ligne."
