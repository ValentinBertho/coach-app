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

### Planification (cron) — sauvegarde quotidienne à 02:30 UTC
```cron
30 2 * * *  DATABASE_URL="postgresql://USER:PASS@HOST:5432/darilab" BACKUP_DIR=/var/backups/darilab RETENTION_DAYS=14 /chemin/vers/ops/backup-db.sh >> /var/log/darilab-backup.log 2>&1
```
> Stocker les dumps **hors du serveur applicatif** (bucket S3/GCS chiffré) et chiffrer au repos.

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

## 3. Monitoring des erreurs — Sentry

Actif **uniquement si le DSN est configuré** (no-op sinon).

### Backend (Spring Boot)
- Dépendance : `io.sentry:sentry-spring-boot-starter-jakarta` (déjà dans `back/pom.xml`).
- Config : bloc `sentry:` de `application.yml`, piloté par variables d'env :
  ```bash
  SENTRY_DSN=https://xxxx@oXXXX.ingest.sentry.io/XXXX
  SENTRY_ENV=production
  ```

### Frontend (Angular)
- SDK : `@sentry/angular-ivy` (déjà installé). Init dans `src/main.ts` + `ErrorHandler`
  fourni dans `app.config.ts`.
- Renseigner `sentryDsn` dans `src/environments/environment.ts` au build de prod
  (idéalement injecté via variable d'env CI), ainsi que `appVersion` pour le suivi
  par release.

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
- [ ] Uptime monitor sur `/api/actuator/health`.
- [ ] Secrets via variables d'env (jamais commités) : `JWT_SECRET`, `FIELD_ENCRYPTION_KEY`,
      `STRAVA_*`, `VAPID_*`, `SENTRY_DSN`.
- [ ] Logs centralisés + rétention.
- [ ] Dépendances surveillées (Dependabot/Snyk).
- [ ] HTTPS + en-têtes proxy (déjà : `forward-headers-strategy: framework`).
