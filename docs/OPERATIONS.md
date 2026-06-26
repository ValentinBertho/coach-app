# Darilab — Exploitation, fiabilité & suivi

Guide opérationnel : sauvegardes BDD, restauration, monitoring (Sentry,
Actuator) et CI. À garder à jour avec l'infra réelle.

---

## 1. Sauvegardes PostgreSQL

### Principe
- Script : [`ops/backup-db.sh`](../ops/backup-db.sh) — `pg_dump` format **custom** (`-Fc`),
  compressé, avec **rotation** (purge > `RETENTION_DAYS`) et **vérification d'intégrité**
  (`pg_restore --list`).
- **En complément du `pg_dump` logique, activer le PITR** (archivage WAL) si la base
  est auto-hébergée, ou utiliser un PostgreSQL **managé** (Railway, Neon, RDS, Cloud SQL)
  avec backups automatiques + *point-in-time recovery*.

### Lancer une sauvegarde
```bash
DATABASE_URL="postgresql://USER:PASS@HOST:5432/darilab" \
BACKUP_DIR=/var/backups/darilab RETENTION_DAYS=14 \
./ops/backup-db.sh
```

### Pas-à-pas (serveur Linux)
1. **Installer le client PostgreSQL** (fournit `pg_dump` / `pg_restore`) — la version doit être
   ≥ à celle du serveur :
   ```bash
   sudo apt-get update && sudo apt-get install -y postgresql-client
   ```
2. **Récupérer l'URL de connexion** de la base (Railway → PostgreSQL → *Connect* →
   `DATABASE_URL`, ou variables `PGHOST/PGUSER/PGPASSWORD/PGDATABASE`).
3. **Premier dump manuel** (vérifie les droits et la connexion) :
   ```bash
   DATABASE_URL="postgresql://USER:PASS@HOST:5432/darilab" \
   BACKUP_DIR=/var/backups/darilab RETENTION_DAYS=14 \
   ./ops/backup-db.sh
   ```
   → un fichier `darilab-darilab-<horodatage>.dump` apparaît et l'intégrité est vérifiée.
4. **Planifier** via cron (sauvegarde quotidienne à 02:30 UTC) — `crontab -e` :
   ```cron
   30 2 * * *  DATABASE_URL="postgresql://USER:PASS@HOST:5432/darilab" BACKUP_DIR=/var/backups/darilab RETENTION_DAYS=14 /chemin/absolu/ops/backup-db.sh >> /var/log/darilab-backup.log 2>&1
   ```
5. **Externaliser** les dumps (sync vers un bucket chiffré, ex. quotidien) :
   ```bash
   aws s3 sync /var/backups/darilab s3://mon-bucket-backups/darilab --sse AES256
   ```
6. **Programmer un test de restauration** récurrent (cf. §2) et **surveiller** le job
   (alerte si `backup-db.sh` échoue, ex. via le code de sortie + un *dead man's switch*
   type Healthchecks.io).

> Alternative la plus simple : un **PostgreSQL managé** (Railway, Neon, RDS, Cloud SQL) avec
> **backups automatiques + PITR** activés — garder malgré tout un `pg_dump` logique externe.
> Stocker les dumps **hors du serveur applicatif** et **chiffrés au repos**.

---

## 2. Restauration

> ⚠️ La restauration **écrase** les données. Toujours tester d'abord sur une base jetable.

### Restauration complète sur une base neuve
```bash
# 1. Créer une base vide
createdb -h HOST -U USER darilab_restore

# 2. Restaurer le dump (custom format)
pg_restore --no-owner --no-privileges --clean --if-exists \
  -h HOST -U USER -d darilab_restore \
  /var/backups/darilab/darilab-darilab-YYYYmmdd-HHMMSS.dump

# 3. Vérifier puis basculer l'application sur darilab_restore
```

### Restauration sélective (une table)
```bash
pg_restore --list mon.dump            # repérer l'entrée
pg_restore --no-owner -h HOST -U USER -d darilab \
  --table=athlete_1rm_profile mon.dump
```

### Test de restauration (à planifier — trimestriel)
Un backup **non testé n'existe pas**. Restaurer le dernier dump sur une base
jetable, lancer l'app dessus (`SPRING_PROFILES_ACTIVE=prod`, pointée sur la base
restaurée) et vérifier `/api/actuator/health = UP` + un parcours clé.

> Liquibase pilote le schéma : un dump récent contient déjà le schéma à jour.
> Avant chaque **déploiement** (qui applique d'éventuelles migrations au démarrage),
> prendre un dump et garder le plan de rollback.

---

## 3. Monitoring des erreurs — Sentry (pas-à-pas)

Le code est **déjà branché** ; il ne reste qu'à créer le projet Sentry et fournir le DSN.
Sentry est **inactif tant que le DSN est vide** (no-op).

### 3.1 Créer le(s) projet(s) Sentry
1. Créer un compte sur **https://sentry.io** (offre gratuite suffisante au départ).
2. **Create Project** → plateforme **Java / Spring Boot** → nom `darilab-backend`.
   Copier le **DSN** affiché (`https://xxxx@oXXXX.ingest.sentry.io/XXXX`).
3. **Create Project** → plateforme **Angular** → nom `darilab-frontend`. Copier son **DSN**
   (les DSN front et back sont différents).

### 3.2 Backend (Spring Boot)
1. Dépendance déjà présente : `io.sentry:sentry-spring-boot-starter-jakarta` (`back/pom.xml`).
2. Définir les variables d'environnement du service (Railway → onglet *Variables*, ou `.env`) :
   ```bash
   SENTRY_DSN=https://<clé>@oXXXX.ingest.sentry.io/<projet-backend>
   SENTRY_ENV=production
   ```
3. Redéployer. Au démarrage, Sentry s'initialise (sinon : log « Sentry is disabled »).
4. **Vérifier** : déclencher une erreur (ex. appeler une route inexistante en étant loggué,
   ou ajouter temporairement un `throw`) → l'événement apparaît dans Sentry sous quelques secondes.

### 3.3 Frontend (Angular)
1. SDK déjà installé (`@sentry/angular-ivy`), init dans `src/main.ts` + `ErrorHandler` (`app.config.ts`).
2. Renseigner le DSN **au build de prod** dans `front/src/environments/environment.ts` :
   ```ts
   export const environment = {
     production: true,
     apiUrl: '/api',
     sentryDsn: 'https://<clé>@oXXXX.ingest.sentry.io/<projet-frontend>',
     appVersion: '0.1.0',          // incrémenter à chaque release (suivi par version)
   };
   ```
   > En CI, injecter le DSN via une variable d'environnement plutôt que de le committer.
3. Rebuild + déployer le front. **Vérifier** : provoquer une erreur JS → événement visible dans Sentry.

### 3.4 Bonnes pratiques
- Configurer une **alerte** Sentry (e-mail/Slack) sur *« a new issue is created »* et sur les pics.
- Renseigner `appVersion` / `SENTRY_ENV` pour filtrer par **release** et **environnement**.
- `tracesSampleRate` est à `0.1` (10 %) pour le suivi de performance — ajuster selon le volume.

---

## 4. Santé & métriques — Actuator

- Endpoints exposés (`application.yml`) : `health`, `info`, `metrics`, sous `/api/actuator/**`.
- **Liveness/Readiness** : sondes activées (`/api/actuator/health/liveness`, `/readiness`)
  — idéal pour Kubernetes / Railway.
- **Uptime externe** : faire pinguer `https://<host>/api/actuator/health` par un moniteur
  (BetterStack, UptimeRobot, Pingdom) avec alerte si ≠ `UP`.
- Détails de santé : `show-details: when_authorized` (pas d'exposition publique des internes).

---

## 5. Intégration continue (CI)

[`.github/workflows/ci.yml`](../.github/workflows/ci.yml) sur chaque push/PR :
- **Backend** : `mvn verify` (tests + Liquibase sur H2) **puis smoke test** de démarrage
  contre un PostgreSQL éphémère (valide les migrations via `/actuator/health`).
- **Frontend** : `npm ci`, build AOT prod, tests Karma headless.

---

## 6. Check-list de mise en production

- [ ] Backups automatisés **+ une restauration testée**.
- [ ] `SENTRY_DSN` (back) et `sentryDsn` (front) renseignés.
- [ ] Canal email **activé et testé** : `MAIL_ENABLED=true`, `RESEND_API_KEY`, domaine `MAIL_FROM` vérifié (cf. §8).
- [ ] Uptime monitor sur `/api/actuator/health`.
- [ ] Secrets via variables d'env (jamais commités) : `JWT_SECRET`, `FIELD_ENCRYPTION_KEY`,
      `STRAVA_*`, `VAPID_*`, `SENTRY_DSN`.
- [ ] Logs centralisés + rétention.
- [ ] Dépendances surveillées (Dependabot/Snyk).
- [ ] HTTPS + en-têtes proxy (déjà : `forward-headers-strategy: framework`).

---

## 7. Modèle d'accès coach ↔ athlète

L'accès d'un coach à un athlète est résolu par `AthleteAccessValidator` et appliqué
par `@PreAuthorize` sur les routes `/clubs/{clubId}/athletes/{athleteId}/**`
(`canRead` en lecture, `canWrite`/`canComment` en écriture) :

1. **Coach référent** (relation active) ⇒ écriture complète.
2. **Athlète privé** (`coach_athlete_relations.club_id IS NULL`) ⇒ accessible au seul référent.
3. **Athlète club** ⇒ coach assigné (`athlete_coaches`) ou permission explicite
   (`athlete_coach_permissions`) ⇒ niveau correspondant ; Owner/Coach principal ⇒ lecture par défaut.
4. **Fallback** : athlète sans relation référent (données antérieures) ⇒ accès club-level
   historique, pour ne jamais verrouiller.

- La **création d'athlète** crée désormais systématiquement la relation référent (coach créateur).
- Un **backfill idempotent** (`MultiCoachBackfill`, `ApplicationRunner`) crée au démarrage la
  relation référent manquante (référent = head coach du club) pour les athlètes existants. Sûr à
  relancer : il n'agit que sur les athlètes sans référent.
- `PLATFORM_ADMIN` a un accès transverse ; un compte `ATHLETE` n'emprunte jamais ces routes
  (il passe par `/me/**`).

**Membres du club.** L'inscription crée le coach comme **membre `OWNER`** de son club. Un
propriétaire/coach principal peut **ajouter un coach existant** (par e-mail) via la page Club
(`POST /clubs/{clubId}/members`) — ce qui lui donne l'accès tenant et permet de lui accorder des
permissions par athlète — et le **retirer** (`DELETE …/members/{coachId}`, l'`OWNER` est protégé).
L'invitation d'un coach **sans compte** (création + e-mail) reste à faire.

---

## 8. Emails & notifications (Resend) — pas-à-pas

Le canal email est **déjà branché** (client Resend, `NotificationService`, schedulers) ; il est
**inactif tant que `MAIL_ENABLED=false`** (les envois sont simplement loggués). Aucune donnée de
santé n'est jamais incluse dans un email.

### 8.1 Activer Resend
1. Créer un compte sur **https://resend.com**.
2. **Vérifier un domaine** d'envoi (`darilab.app`) : ajouter les enregistrements DNS (SPF/DKIM)
   fournis par Resend. Sans domaine vérifié, seuls les envois de test vers votre propre adresse passent.
3. Créer une **clé API** (`re_...`).
4. Renseigner les variables d'environnement du service :
   ```bash
   MAIL_ENABLED=true
   RESEND_API_KEY=re_xxxxxxxx
   MAIL_FROM=Darilab <no-reply@darilab.app>   # le domaine doit être vérifié
   ```
5. Redéployer. Au prochain événement (invitation athlète, rappel J-1, retour de séance), l'email part.

### 8.2 Vérifier de bout en bout
- **Invitation** : depuis une fiche athlète, générer une invitation → l'athlète reçoit le lien.
- **Rappel J-1** : planifié à 18h (`app.reminders.cron`) — notifie les athlètes ayant une séance le lendemain.
- **Digest d'alertes coach** : planifié à 7h (`ALERTS_DIGEST_CRON`) — chaque coach **référent** reçoit
  le récapitulatif des athlètes à surveiller (douleur, charge, séances manquées, silence), **sans détail
  médical**, avec un lien vers le tableau de bord. C'est le branchement des alertes du dashboard
  (cf. `CoachDashboardService.alerts`) sur le canal.

### 8.3 Routage
- Les retours de séance et le digest sont envoyés au **coach référent** de l'athlète (repli : head coach
  du club) — jamais à un coach non concerné.
- Le **push web** (VAPID) suit le même routage ; il dépend des souscriptions navigateur (renseigner
  `VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`).
