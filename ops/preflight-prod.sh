#!/usr/bin/env bash
# ============================================================================
# Darilab — contrôle de pré-vol avant de passer en profil « prod ».
#
# Rejoue, hors de l'application, les contrôles de StartupSecretsValidator, plus ceux qu'il ne
# peut pas faire : comptes de démonstration restés en base, cohérence de la topologie de proxy.
#
# Pourquoi un script séparé : le validateur s'exécute au démarrage et fait échouer le boot. Sur
# un hébergeur qui redéploie à chaud, un secret manquant coûte donc un déploiement raté et un
# retour arrière. Ici, on répond avant de pousser.
#
# Usage :
#   # depuis un shell où les variables de production sont chargées
#   ./ops/preflight-prod.sh
#
#   # ou en les passant explicitement
#   JWT_SECRET=... FIELD_ENCRYPTION_KEY=... FRONTEND_URL=https://darilab.app \
#   CORS_ORIGINS=https://darilab.app VAPID_PUBLIC_KEY=... VAPID_PRIVATE_KEY=... \
#   REGISTRATION_INVITE_CODE=... DATABASE_URL=postgresql://... ./ops/preflight-prod.sh
#
# Sortie : 0 si tout est prêt, 1 sinon (utilisable en garde dans un pipeline).
# ============================================================================
set -uo pipefail

FAIL=0
WARN=0

red()   { printf '\033[31m✗\033[0m %s\n' "$1"; FAIL=$((FAIL + 1)); }
amber() { printf '\033[33m!\033[0m %s\n' "$1"; WARN=$((WARN + 1)); }
green() { printf '\033[32m✓\033[0m %s\n' "$1"; }

echo "── Secrets et réglages exigés au démarrage ──────────────────────────────"

# JWT_SECRET : HS512, donc 64 octets minimum, et jamais une valeur de développement.
if [ -z "${JWT_SECRET:-}" ]; then
  red "JWT_SECRET absent."
elif [ "${JWT_SECRET#dev-}" != "${JWT_SECRET}" ]; then
  red "JWT_SECRET commence par « dev- » : valeur de développement refusée en prod."
elif [ "${#JWT_SECRET}" -lt 64 ]; then
  red "JWT_SECRET trop court (${#JWT_SECRET} octets, 64 minimum pour HS512)."
else
  green "JWT_SECRET (${#JWT_SECRET} octets)."
fi

# FIELD_ENCRYPTION_KEY : 64 caractères hexadécimaux, et pas la clé nulle.
if [ -z "${FIELD_ENCRYPTION_KEY:-}" ]; then
  red "FIELD_ENCRYPTION_KEY absente."
elif ! printf '%s' "${FIELD_ENCRYPTION_KEY}" | grep -Eq '^[0-9a-fA-F]{64}$'; then
  red "FIELD_ENCRYPTION_KEY invalide : 64 caractères hexadécimaux attendus."
elif [ "${FIELD_ENCRYPTION_KEY}" = "$(printf '0%.0s' $(seq 64))" ]; then
  red "FIELD_ENCRYPTION_KEY laissée à la clé nulle de développement."
else
  green "FIELD_ENCRYPTION_KEY."
fi

# Les liens d'invitation et de réinitialisation sont construits sur cette URL.
for var in FRONTEND_URL CORS_ORIGINS; do
  value="${!var:-}"
  if [ -z "${value}" ]; then
    red "${var} absente."
  elif printf '%s' "${value}" | grep -qiE 'localhost|127\.0\.0\.1'; then
    red "${var} pointe encore sur une adresse locale (${value})."
  else
    green "${var} = ${value}"
  fi
done

# Sans clés VAPID, le push est inerte — et c'est silencieux hors profil prod.
if [ -z "${VAPID_PUBLIC_KEY:-}" ] || [ -z "${VAPID_PRIVATE_KEY:-}" ]; then
  red "VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY manquantes : aucune notification push ne partira."
else
  green "Clés VAPID présentes."
fi

# Le profil prod force REGISTRATION_MODE=invite : sans code, plus personne ne s'inscrit.
mode="${REGISTRATION_MODE:-invite}"
if [ "${mode}" = "invite" ] && [ -z "${REGISTRATION_INVITE_CODE:-}" ]; then
  red "REGISTRATION_MODE=invite sans REGISTRATION_INVITE_CODE : aucune inscription possible."
else
  green "Inscription : mode ${mode}."
fi

# Mail activé sans clé : chaque envoi échoue pendant que l'interface annonce « envoyé ».
if [ "${MAIL_ENABLED:-false}" = "true" ] && [ -z "${RESEND_API_KEY:-}" ]; then
  red "MAIL_ENABLED=true sans RESEND_API_KEY."
else
  green "Configuration e-mail cohérente (MAIL_ENABLED=${MAIL_ENABLED:-false})."
fi

echo
echo "── Topologie de proxy ───────────────────────────────────────────────────"

# Doit refléter le nombre de relais traversés par les appels API. Trop grand, la lecture de
# X-Forwarded-For échoue et tous les clients tombent dans un compteur unique.
hops="${RATE_LIMIT_TRUSTED_PROXY_HOPS:-1}"
if ! printf '%s' "${hops}" | grep -Eq '^[0-9]+$' || [ "${hops}" -lt 1 ]; then
  red "RATE_LIMIT_TRUSTED_PROXY_HOPS=${hops} : au moins 1 attendu."
elif [ "${hops}" -gt 1 ]; then
  amber "RATE_LIMIT_TRUSTED_PROXY_HOPS=${hops}. À ne garder que si l'API est réellement derrière
   ${hops} relais. Le navigateur appelant l'API directement, c'est 1 dans le cas courant — une
   valeur trop grande met tous les utilisateurs dans le même seau de rate limiting."
else
  green "RATE_LIMIT_TRUSTED_PROXY_HOPS=${hops}."
fi

echo
echo "── Données de démonstration ─────────────────────────────────────────────"

# Le jeu de démo est idempotent et ne se recharge pas en prod : mais s'il a été créé pendant la
# phase de développement, il reste en base — avec des mots de passe publics, dont un compte
# administrateur de plateforme.
if [ -n "${DATABASE_URL:-}" ] && command -v psql >/dev/null 2>&1; then
  demo=$(psql "${DATABASE_URL}" -tAc \
    "select count(*) from users where email in
       ('admin@coachrun.fr','demo@coachrun.fr','coach@coachrun.fr','athlete@coachrun.fr')" 2>/dev/null)
  if [ -z "${demo}" ]; then
    amber "Comptes de démonstration : vérification impossible (connexion refusée ?)."
  elif [ "${demo}" -gt 0 ]; then
    red "${demo} compte(s) de démonstration en base, mot de passe public « password123 »
   — dont admin@coachrun.fr, administrateur de la plateforme. À supprimer avant la bascule."
  else
    green "Aucun compte de démonstration en base."
  fi
else
  amber "Comptes de démonstration : non vérifié (DATABASE_URL absente ou psql indisponible).
   Contrôler à la main la présence de admin@coachrun.fr."
fi

echo
if [ "${FAIL}" -gt 0 ]; then
  printf '\033[31m%s bloquant(s)\033[0m, %s avertissement(s) — le démarrage en profil prod échouerait.\n' \
    "${FAIL}" "${WARN}"
  exit 1
fi
printf '\033[32mPrêt pour SPRING_PROFILES_ACTIVE=prod\033[0m — %s avertissement(s).\n' "${WARN}"
