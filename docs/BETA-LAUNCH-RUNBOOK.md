# Runbook — Mise en service de la bêta DARI Lab

> Checklist opérationnelle à dérouler dans l'ordre. Chaque phase est autonome :
> si tu t'arrêtes en cours de route, l'application reste dans un état cohérent.
>
> Contexte : audit complet dans [`AUDIT-BETA-READINESS-2026-07.md`](./AUDIT-BETA-READINESS-2026-07.md).
> Détails techniques : [`OPERATIONS.md`](./OPERATIONS.md) · [`DEPLOIEMENT.md`](./DEPLOIEMENT.md).

## État au démarrage du runbook

| Fait | Reste à faire |
|---|---|
| ✅ Domaine `darilab.app` (OVH → Vercel), HTTPS | ⬜ Variables Railway alignées sur le domaine |
| ✅ Pages légales + consentement RGPD horodaté | ⬜ Emails (Resend) |
| ✅ `state` OAuth Strava signé, rate limiting élargi | ⬜ Sentry (DSN à renseigner) |
| ✅ Workflow de sauvegarde chiffrée écrit | ⬜ Uptime (Better Stack) |
| ✅ CI alignée sur PostgreSQL 18 (= prod) | ⬜ Test de restauration BDD |

---

## Phase 1 — Aligner la configuration sur le domaine ⏱️ 5 min

### 1.1 Railway
Service **backend** → onglet **Variables** :
```
FRONTEND_URL=https://www.darilab.app
CORS_ORIGINS=https://www.darilab.app,https://darilab.app
STRAVA_REDIRECT_URI=https://www.darilab.app/app/strava/callback
```
> L'URL canonique est `www.darilab.app` : l'apex y redirige en 308.
> Railway redéploie automatiquement à l'enregistrement.

### 1.2 Strava
[developers.strava.com](https://developers.strava.com) → ton application →
**Authorization Callback Domain** = `darilab.app`

### 1.3 Vérifier
- [ ] `https://www.darilab.app` s'affiche en HTTPS
- [ ] Connexion avec un compte existant → OK
- [ ] `https://www.darilab.app/legal/confidentialite` s'affiche

---

## Phase 2 — Emails (Resend) ⏱️ 30 min · 🔴 BLOQUANT

Sans emails, « mot de passe oublié » est une impasse : c'est le dernier vrai bloquant.

### 2.1 Créer le compte et le domaine
1. [resend.com](https://resend.com) → créer un compte (gratuit : 3 000 emails/mois)
2. **Domains** → **Add Domain** → `darilab.app` → région **EU (Ireland)**
3. Resend affiche 3 à 4 enregistrements DNS à créer

### 2.2 Reporter les enregistrements chez OVH
**Zone DNS** → **Ajouter une entrée** pour chacun.

⚠️ **Règle OVH** : le champ « sous-domaine » est **relatif**. Si Resend indique
`resend._domainkey.darilab.app`, tu saisis seulement `resend._domainkey`.

| Ce que donne Resend (typique) | Sous-domaine OVH | Type |
|---|---|---|
| `send.darilab.app` → `feedback-smtp.eu-west-1.amazonses.com` | `send` | MX (priorité 10) |
| `send.darilab.app` → `v=spf1 include:amazonses.com ~all` | `send` | TXT |
| `resend._domainkey.darilab.app` → `p=MIGf...` | `resend._domainkey` | TXT |

🚨 **Piège critique** : **ne crée jamais un second SPF sur `@`**. La zone contient déjà
`v=spf1 include:mx.ovh.com -all`. Deux enregistrements SPF sur le même nom invalident
**toute** l'authentification du domaine. Le SPF de Resend va sur `send`, pas sur l'apex —
si Resend demande quelque chose sur `@`, vérifier avant d'ajouter.

- Valeurs TXT : coller **sans guillemets** (OVH les ajoute)
- Cibles MX/CNAME : **terminer par un point**

### 2.3 Vérifier et activer
1. Resend → **Verify DNS Records** → statut **Verified** (15–30 min de propagation)
2. **API Keys** → **Create API Key** → nom `darilab-prod`, permission **Sending access**
   → copier la clé `re_…` (affichée une seule fois)
3. Railway → Variables :
   ```
   MAIL_ENABLED=true
   RESEND_API_KEY=re_xxxxxxxx
   MAIL_FROM=Darilab <no-reply@darilab.app>
   ```

### 2.4 Tester les 4 emails critiques
- [ ] **Mot de passe oublié** : `/forgot-password` → email reçu → le lien fonctionne
- [ ] **Invitation athlète** : fiche athlète → générer une invitation
- [ ] **Invitation coach** : page Club → ajouter un coach par email
- [ ] **Vérifier le dossier spam** — si l'email y atterrit, voir 2.5

### 2.5 (Recommandé) DMARC
Améliore nettement la délivrabilité. **Ajouter une entrée** OVH :
- Sous-domaine : `_dmarc` · Type : **TXT**
- Valeur : `v=DMARC1; p=none; rua=mailto:contact@darilab.app`

`p=none` = observation seule, aucun risque de blocage. À durcir plus tard.

---

## Phase 3 — Monitoring des erreurs (Sentry) ⏱️ 15 min · 🔴 BLOQUANT

Le code est déjà branché des deux côtés : il ne manque que les DSN.

1. [sentry.io](https://sentry.io) → compte gratuit → **région EU** à la création de l'organisation
2. **Create Project** → **Spring Boot** → nom `darilab-backend` → copier le **DSN**
3. **Create Project** → **Angular** → nom `darilab-frontend` → copier son DSN (différent)
4. Railway → Variables :
   ```
   SENTRY_DSN=<DSN backend>
   SENTRY_ENV=production
   ```
5. **DSN frontend** : il se renseigne dans `front/src/environments/environment.ts`
   (clé `sentryDsn`). Ce DSN n'est pas un secret — il est public par conception.
   → **transmettre le DSN pour commit**, ou l'éditer soi-même puis pousser.
6. **Alertes** : Sentry → **Alerts** → règle *« When a new issue is created → email »*
   sur chaque projet.

- [ ] Backend : provoquer une erreur → événement visible dans Sentry
- [ ] Frontend : idem après redéploiement

---

## Phase 4 — Disponibilité (Better Stack) ⏱️ 10 min · 🔴 BLOQUANT

1. [betterstack.com](https://betterstack.com) → **Uptime** → compte gratuit (10 moniteurs)
2. **Monitors** → **Create monitor** :
   - URL : `https://coach-app-production-5674.up.railway.app/api/actuator/health`
   - Fréquence : **3 min** · Alerte : **email**
   - Options avancées → *Required keyword* : `UP`
3. **Heartbeat du backup** (fortement recommandé) :
   **Heartbeats** → **Create heartbeat** → nom `db-backup`, période **1 day**,
   grace period **6 hours** → copier l'URL générée
   → **transmettre cette URL** : elle sera ajoutée en fin de workflow de sauvegarde.
   Sans elle, un backup qui cesse de tourner passe inaperçu — le scénario le plus dangereux.

---

## Phase 5 — Sauvegardes : finaliser ⏱️ 30 min · 🔴 BLOQUANT

### 5.1 Secrets GitHub (si pas déjà fait)
Repo → **Settings** → **Secrets and variables** → **Actions** :
- `BACKUP_DATABASE_URL` = URL publique Railway (PostgreSQL → Connect → **Public Network**)
- `BACKUP_ENCRYPTION_KEY` = `openssl rand -hex 32`
  ⚠️ **conserver aussi hors de GitHub** (gestionnaire de mots de passe) : sans cette clé,
  les dumps sont définitivement illisibles.

### 5.2 Lancer et vérifier
- [ ] Actions → **Sauvegarde BDD** → **Run workflow** → job vert
- [ ] Un artefact `darilab-….dump.enc` apparaît en bas du run

### 5.3 Backups Railway (complément)
- [ ] Railway → service PostgreSQL → **Backups** → activer les backups quotidiens

### 5.4 Test de restauration — **ne pas sauter cette étape**
Un backup non testé n'existe pas. Procédure complète : `OPERATIONS.md` §2.
```bash
# Déchiffrer l'artefact téléchargé
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 \
  -in darilab-YYYYmmdd-HHMMSS.dump.enc -out darilab.dump \
  -pass pass:'<BACKUP_ENCRYPTION_KEY>'

# Restaurer sur une base jetable (locale via docker compose up postgres)
createdb -h localhost -U postgres darilab_restore
pg_restore --no-owner --no-privileges -h localhost -U postgres \
  -d darilab_restore darilab.dump
```
- [ ] Restauration réussie, données présentes (quelques `SELECT count(*)`)
- [ ] Durée chronométrée (savoir combien de temps coûte un incident réel)

---

## Phase 6 — Tests de bout en bout ⏱️ 45 min

À faire **avant** d'inviter le premier coach externe, sur `https://www.darilab.app`.

### Parcours coach
- [ ] Inscription d'un nouveau club (avec la case CGU) → email de vérification reçu
- [ ] Créer un athlète → l'inviter → l'email arrive
- [ ] Créer une séance, la planifier au calendrier
- [ ] Mot de passe oublié → réinitialisation complète

### Parcours athlète
- [ ] Accepter l'invitation (avec consentement santé) → accès au portail
- [ ] Voir la séance du jour, saisir un retour (RPE / fatigue / douleur)
- [ ] Connecter Strava → import d'activités (valide le `state` signé)

### Points de fragilité connus
- [ ] **Messagerie temps réel (SSE)** : ouvrir une conversation dans 2 navigateurs,
      envoyer un message après 1–2 min d'inactivité. Le SSE passe par le rewrite Vercel,
      qui supporte mal les connexions longues sur le plan Hobby.
      → Si le message n'arrive pas instantanément : créer un sous-domaine
      `api.darilab.app` pointant directement sur Railway (contourne le proxy Vercel).
- [ ] **Déploiement en journée** : Railway redémarre l'instance (~1–2 min d'indisponibilité).
      Déployer hors des heures d'entraînement.

---

## Phase 7 — Après le feu vert 🟠

Non bloquant, mais à traiter dans les deux premières semaines :

- [ ] **Impersonation admin** (lecture seule) — outil de support n° 1
- [ ] **Canal de feedback** dans l'app (lien « Signaler un problème »)
- [ ] **Onboarding coach** : checklist « premier jour » sur le dashboard
- [ ] **Dump avant chaque déploiement** contenant une migration (règle à appliquer)
- [ ] Tags git + `appVersion` incrémenté (corrélation bug ↔ version dans Sentry)
- [ ] État front « API indisponible » propre pendant les redéploiements
- [ ] Relecture des pages légales par un œil juridique

---

## Feu vert bêta 🚦

La bêta peut s'ouvrir quand **toutes les cases 🔴 des phases 2 à 5 sont cochées**
et que la phase 6 est passée sans blocage.

**Cohorte 1 recommandée : 5 à 8 coachs sur invitation.** Élargir à 15–20 après deux
semaines si Sentry reste calme. L'architecture encaisse 20–30 coachs ; la limite réelle
est la capacité de support d'une seule personne.
