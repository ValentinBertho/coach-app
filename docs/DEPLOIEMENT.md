# Déploiement — DARI Lab

> Front sur **Vercel**, back + PostgreSQL sur **Railway**.
> **Ordre recommandé : Railway d'abord** (pour obtenir l'URL de l'API) → configurer le front avec
> cette URL → déployer Vercel → reporter l'URL du front dans le CORS Railway.

## Domaine (en place)

| Élément | Valeur |
|---|---|
| Registrar / DNS | **OVH** (zone `darilab.app`) |
| URL canonique | **`https://www.darilab.app`** |
| Apex | `darilab.app` → redirection 308 vers `www` |

Zone DNS (hors NS/MX/SPF gérés par OVH) :

| Sous-domaine | Type | Cible |
|---|---|---|
| `@` | A | `216.198.79.1` (IP Vercel ; `76.76.21.21` reste valide) |
| `www` | CNAME | cible CNAME propre au projet Vercel (`*.vercel-dns-0NN.com.`) |

> ⚠️ L'onglet **« Redirection »** d'OVH doit rester **vide** : ses entrées recréent des
> enregistrements `A` vers l'IP de parking OVH (`213.186.33.5`) et cassent la configuration Vercel.

---

## 0. Bascule en production, pas à pas

> Cette section existe parce que la bascule échouait toujours de la même façon : on posait
> `SPRING_PROFILES_ACTIVE=prod`, l'application refusait de démarrer, et le journal de l'hébergeur
> affichait une trace de deux cents lignes dont la seule phrase utile — la liste des variables
> manquantes — était noyée au milieu. Le garde-fou disait juste ; il n'était pas lisible.
>
> Depuis, **l'échec de démarrage en profil `prod` s'affiche en clair**, en dernier bloc du
> journal, sous la forme d'une liste numérotée des réglages à poser (`FailureAnalyzer`). Et le
> script ci-dessous permet de répondre **avant** de pousser.

### Étape 1 — répondre avant de pousser

```bash
# depuis un shell où les variables de production sont chargées
./ops/preflight-prod.sh
```

Le script rejoue, hors de l'application, les contrôles du démarrage — plus ceux qu'elle ne peut
pas faire (comptes de démonstration restés en base, cohérence de la topologie de proxy). Sortie
`0` = le démarrage en profil `prod` passera. Sur un hébergeur qui redéploie à chaud, chaque secret
oublié coûte sinon un déploiement raté et un retour arrière.

### Étape 2 — générer les secrets

```bash
openssl rand -base64 48                # JWT_SECRET           (≥ 512 bits)
openssl rand -hex 32                   # FIELD_ENCRYPTION_KEY (64 hex)
openssl rand -hex 16                   # STRAVA_WEBHOOK_VERIFY_TOKEN
npx web-push generate-vapid-keys       # VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY
```

⚠ **Les clés VAPID ne se régénèrent pas.** Un abonnement de navigateur est lié à la clé publique
avec laquelle il a été créé : changer la paire coupe le push de **tous** les athlètes, sans un
message. On les génère une fois, et on les garde.

### Étape 3 — les variables qui font refuser le démarrage

Ce sont les seules bloquantes. Tout le reste est optionnel et n'empêche jamais de servir.

| Variable | Pourquoi elle est exigée |
|---|---|
| `JWT_SECRET` | ≥ 512 bits, et jamais une valeur commençant par `dev-` |
| `FIELD_ENCRYPTION_KEY` | 64 hex, et jamais la clé nulle de développement |
| `FRONTEND_URL` | restée sur `localhost`, les liens d'invitation et de réinitialisation n'ouvrent rien |
| `CORS_ORIGINS` | une origine de développement ne doit pas parler à la production |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | sans elles le push est inerte — et « séance planifiée » comme « commentaire du coach » n'ont **aucun repli e-mail** |
| `RESEND_API_KEY` | seulement si `MAIL_ENABLED=true` : sinon chaque envoi échoue pendant que l'interface annonce « envoyé » |
| `RATE_LIMIT_TRUSTED_PROXY_HOPS` | ≥ 1. Le navigateur appelle l'API directement : **1** est la bonne valeur |
| `REGISTRATION_MODE` | doit valoir `request`, `invite` ou `open`. Une valeur non reconnue fait refuser le démarrage — une faute de frappe ne doit pas rouvrir la création de club |
| `REGISTRATION_INVITE_CODE` | seulement si `REGISTRATION_MODE=invite` : sans code, plus personne ne peut s'inscrire |
| `BETTER_STACK_*` | contrôlées **seulement si un token est posé** (cf. § 3) |

`PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` n'empêchent pas le démarrage mais sont à poser
au premier déploiement : sans eux, `/admin` est inatteignable — ni arbitrage des demandes de club,
ni révocation d'invitation, ni suppression de compte. Le compte est créé au premier démarrage en
profil `prod` puis **jamais modifié** ; la rotation du mot de passe se fait depuis l'application.

### Étape 4 — poser le profil et déployer

```bash
SPRING_PROFILES_ACTIVE=prod
```

Si le démarrage échoue, le journal se termine désormais par un bloc de ce genre — c'est ce qu'il
faut lire, et rien d'autre :

```
***************************
APPLICATION FAILED TO START
***************************

Description:

Le profil « prod » est actif, mais la configuration est incomplète.
…
Il manque 2 réglage(s) :
  1. VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY manquantes : …
  2. CORS_ORIGINS manquant ou contenant localhost : …

Action:

Poser les variables d'environnement ci-dessus, puis redéployer.
…
```

### Étape 5 — vérifier ce qui tourne réellement

- `GET /api/actuator/health` → `{"status":"UP"}`
- Back-office → **Configuration** (`/admin/platform`) : chaque réglage y est dit *posé* ou non,
  avec le nom de la variable en cause. Aucune valeur de secret n'y est jamais affichée.
- Back-office → **Tableau de bord** : les anomalies actionnables (plafond d'e-mails, comptes
  bloqués, demandes de club en attente) y remontent en premier.

### Étape 6 — activer le webhook Strava

Le webhook n'a aucun effet sur le démarrage : il se pose après, une fois l'instance joignable.
Voir § 6 — et surtout, **l'adresse contient le préfixe `/api`**.

---

## 1. Railway — backend (Docker) + PostgreSQL

1. **Créer un projet** Railway → *New Project*.
2. **Ajouter PostgreSQL** : *New → Database → PostgreSQL*. Railway expose `PGHOST`, `PGPORT`,
   `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `DATABASE_URL`.
3. **Ajouter le service backend** : *New → GitHub Repo* → sélectionner le dépôt.
   - **Root Directory** : `back`
   - Railway détecte le `Dockerfile` dans `/back` (multi-stage Maven → JRE 21).
4. **Variables d'environnement** du service backend (cf. tableau § 3) :
   ```
   SPRING_PROFILES_ACTIVE=prod
   JDBC_DATABASE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
   PGUSER=${{Postgres.PGUSER}}
   PGPASSWORD=${{Postgres.PGPASSWORD}}
   JWT_SECRET=<openssl rand -base64 48>
   FIELD_ENCRYPTION_KEY=<openssl rand -hex 32>
   FRONTEND_URL=https://www.darilab.app
   CORS_ORIGINS=https://www.darilab.app,https://darilab.app
   ```
   > **URL canonique : `https://www.darilab.app`** (l'apex `darilab.app` y redirige en 308).
   > C'est elle qui doit figurer dans `FRONTEND_URL` (liens des e-mails) et dans le redirect Strava.
   > En profil `prod`, l'application **refuse de démarrer** si `JWT_SECRET` / `FIELD_ENCRYPTION_KEY`
   > sont absents ou laissés aux valeurs de développement.
5. **Exposer le domaine** : *Settings → Networking → Generate Domain* →
   `https://coachrun-back.up.railway.app`. L'API sera servie sous `…/api`.
6. **Healthcheck** : *Settings → Healthcheck Path* → `/api/actuator/health`.
7. Au démarrage, **Liquibase** applique automatiquement les migrations.

---

## 2. Vercel — frontend Angular (PWA)

1. **Importer le dépôt** → *Add New → Project* → repo GitHub.
2. **Configurer le projet** :
   - **Root Directory** : `front`
   - **Framework Preset** : Angular (ou *Other*).
   - **Build Command** : `npm run build`
   - **Output Directory** : `dist/front/browser`
3. **Brancher le front sur l'API** — deux options :
   - **Option A (recommandée, sans CORS)** — proxifier `/api` vers Railway via `front/vercel.json`.
     Garder `apiUrl: '/api'` dans `environment.ts` et ajouter une règle de rewrite **avant** le
     fallback SPA :
     ```json
     {
       "rewrites": [
         { "source": "/api/(.*)", "destination": "https://coachrun-back.up.railway.app/api/$1" },
         { "source": "/(.*)", "destination": "/index.html" }
       ]
     }
     ```
   - **Option B (appel direct + CORS)** — mettre l'URL complète dans
     `front/src/environments/environment.ts` :
     ```ts
     export const environment = { production: true, apiUrl: 'https://coachrun-back.up.railway.app/api' };
     ```
     et s'assurer que `CORS_ORIGINS` (Railway) contient l'URL Vercel.
4. Le `front/vercel.json` fourni contient déjà le **fallback SPA** (`/(.*) → /index.html`).
5. **Déployer** → `https://<ton-app>.vercel.app`.
6. **Boucler le CORS** : reporter l'URL Vercel dans `CORS_ORIGINS` et `FRONTEND_URL` côté Railway,
   puis redéployer le backend.

---

## 3. Variables d'environnement

> `[PROD-REQUIS]` obligatoire en prod (l'app refuse de démarrer sinon) · `[DÉFAUT]` valeur par défaut ·
> `[OPT]` optionnel selon les modules activés. Modèle complet : [`.env.example`](../.env.example).

| Variable | Portée | Description | Exemple / génération |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | back | `dev` ou `prod` | `prod` |
| `JDBC_DATABASE_URL` | back | URL JDBC PostgreSQL **[PROD-REQUIS]** | `jdbc:postgresql://host:5432/coachrun` |
| `PGUSER` / `PGPASSWORD` | back | Identifiants DB **[PROD-REQUIS]** | fournis par Railway |
| `JWT_SECRET` | back | Secret JWT ≥512 bits **[PROD-REQUIS]** | `openssl rand -base64 48` |
| `JWT_ACCESS_TTL` | back | Durée de l'access token (s) **[DÉFAUT 900]** | `900` |
| `FIELD_ENCRYPTION_KEY` | back | Clé AES-256, 64 hex **[PROD-REQUIS]** | `openssl rand -hex 32` |
| `FRONTEND_URL` | back | URL du front (liens emails) **[PROD-REQUIS]** | `https://www.darilab.app` |
| `CORS_ORIGINS` | back | Origines autorisées (CSV) **[PROD-REQUIS]** | `https://www.darilab.app,https://darilab.app` |
| `MAIL_ENABLED` | back | Active l'envoi d'emails **[DÉFAUT false]** | `true` |
| `RESEND_API_KEY` | back | Clé API Resend **[OPT]** | `re_...` |
| `MAIL_FROM` | back | Adresse expéditrice vérifiée **[OPT]** | `Darilab <no-reply@darilab.app>` |
| `STRAVA_CLIENT_ID` / `STRAVA_CLIENT_SECRET` | back | App Strava **[OPT — Intégrations]** | console Strava |
| `STRAVA_WEBHOOK_CALLBACK_URL` / `STRAVA_WEBHOOK_VERIFY_TOKEN` | back | Remontée immédiate des activités **[OPT]** — sans elles, la synchro reste horaire (cf. § Synchronisation Strava). ⚠ L'adresse **contient le préfixe `/api`** : l'API est servie derrière `server.servlet.context-path` | `https://api.darilab.app/api/public/strava/webhook`, `openssl rand -hex 16` |
| `GARMIN_*` / `COROS_*` | back | OAuth Garmin / Coros **[OPT]** | — |
| `STORAGE_TYPE` | back | `local` ou `s3` **[DÉFAUT local]** | `s3` |
| `S3_ENDPOINT` / `S3_BUCKET` / `S3_ACCESS_KEY` / `S3_SECRET_KEY` / `S3_PUBLIC_URL` | back | Stockage FIT/GPX **[OPT]** | R2 / S3 |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT` | back | Push WebPush **[PROD-REQUIS]** — « séance planifiée » et « commentaire du coach » partent en push **sans repli e-mail** : sans clés, ces notifications ne partent nulle part, et aucun appareil ne peut même s'abonner. En local, une paire est fabriquée automatiquement (cf. § Notifications push) | `npx web-push generate-vapid-keys` |
| `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` | back | Paiements **[OPT — Billing]** | console Stripe |
| `REGISTRATION_MODE` | back | `request` (demande validée par un admin), `invite` (code partagé) ou `open` (libre) **[défaut `request` en prod]**. Une valeur non reconnue fait **refuser le démarrage** — une faute de frappe ne doit pas rouvrir la porte | `request` |
| `REGISTRATION_INVITE_CODE` | back | Code partagé de la cohorte **[REQUIS uniquement si mode=invite]** | `BETA-2026-XXXX` |
| `RATE_LIMIT_TRUSTED_PROXY_HOPS` | back | Relais de confiance **devant l'API** **[DÉFAUT 1]**. Le navigateur appelle l'API directement (le front est sur Vercel, l'API sur Railway — d'où le CORS) : **1** est la bonne valeur. Annoncer plus fait compter l'adresse du proxy, la même pour tout le monde, et toute la plateforme partage alors un seul compteur | `1` |
| `REMINDERS_CRON` | back | Heure du point de programme du soir **[DÉFAUT 21 h]**, lu dans `APP_TIMEZONE` | `0 0 21 * * *` |
| `SENTRY_DSN` | back + front | Monitoring erreurs **[OPT]** | `https://...@sentry.io/...` |
| `BETTER_STACK_SOURCE_TOKEN` | back | Journaux centralisés **[OPT, recommandé]** — sans lui l'appender se désactive seul (un avertissement au démarrage, rien de plus). Cf. [`OPERATIONS.md` §9](./OPERATIONS.md) | source « HTTP » Better Stack |
| `BETTER_STACK_INGEST_URL` | back | Hôte d'ingestion **de la source** **[OPT]** — ⚠ souvent régional, à recopier depuis les réglages de la source ; le défaut ne convient pas à toutes | `https://sXXXXXX.eu-nbg-2.betterstackdata.com` |
| `apiUrl` (`environment.ts`) | front | URL de l'API | `/api` ou URL Railway |

---

## 4. Local (Docker Desktop)

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Frontend | http://localhost:4200 |
| API | http://localhost:8081/api |
| Swagger | http://localhost:8081/api/swagger-ui.html |
| Health | http://localhost:8081/api/actuator/health |
| PostgreSQL | localhost:5432 (`postgres` / `postgres`) |

La page d'accueil appelle `GET /api/public/ping` ; si le statut « En ligne » s'affiche,
front et back communiquent. ✅

---

## 5. Notifications push

La chaîne compte **quatre maillons**, et il suffit qu'un seul manque pour que rien n'arrive —
sans le moindre message. Les voici dans l'ordre où il faut les vérifier.

| Maillon | Comment savoir | S'il manque |
|---|---|---|
| **Service worker enregistré** (front) | `navigator.serviceWorker.getRegistration()` dans la console | `ng serve` n'en produit pas : **aucun push ne peut arriver en développement**. Utiliser `npm run start:pwa` (cf. ci-dessous) |
| **Clés VAPID** (back) | ligne `Push VAPID : …` au démarrage du serveur | Le navigateur ne peut même pas s'abonner. En prod : poser `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` |
| **Appareil abonné** | Notifications › Mes appareils (coach), Profil (athlète) | Activer les notifications sur l'appareil. Sur iPhone, l'application doit être **installée** sur l'écran d'accueil |
| **Remise acceptée** | bouton **« Tester les notifications »** | Le message dit l'appareil et la cause : signature refusée, abonnement expiré, service injoignable |

Le bouton de test envoie **en synchrone** et rapporte ce que le service de push a réellement
accepté. C'est le point d'entrée du diagnostic : il distingue les quatre causes ci-dessus,
là où « rien ne s'affiche sur mon téléphone » les confond toutes.

### Éprouver le push en local

`ng serve` ne sert pas `ngsw-worker.js` : les notifications y sont structurellement hors de
portée. Deux façons d'avoir la chaîne complète en local :

```bash
# a) Pile Docker : le front y est déjà servi par nginx depuis un build de production,
#    service worker compris. Le back tourne en profil dev, donc génère ses clés.
docker compose up --build

# b) Sans Docker, en gardant le code du poste :
cd back && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # génère .vapid-dev.json au 1er lancement
cd front && npm run start:pwa                                     # build AVEC service worker, servi sur :4200
```

Puis : ouvrir http://localhost:4200 (contexte sécurisé, `localhost` suffit), activer les
notifications depuis Profil, et cliquer « Tester les notifications ».

Les clés de développement sont fabriquées au premier démarrage et **conservées** dans
`back/.vapid-dev.json` (non versionné). La persistance n'est pas du confort : un abonnement de
navigateur est lié à la clé publique avec laquelle il a été créé, donc une paire régénérée à
chaque redémarrage couperait les abonnements du poste sans rien dire. En production, aucune
génération automatique : une identité de serveur qui changerait au redéploiement couperait les
abonnements de tous les athlètes.

---

## 6. Synchronisation Strava — webhook

Les activités remontent par **deux chemins**, et l'ordre compte :

| Chemin | Délai | Rôle |
|---|---|---|
| **Webhook** (`POST /public/strava/webhook`) | quelques secondes | chemin principal : Strava prévient dès qu'une activité est enregistrée |
| **Synchro planifiée** (`STRAVA_SYNC_CRON`, toutes les heures) | ≤ 1 h | filet : rattrape ce que le webhook a pu manquer (redéploiement en cours, coupure réseau, abonnement pas encore créé) |

Sans abonnement créé, tout continue de fonctionner — simplement au rythme de la passe horaire.
C'est l'état par défaut : rien ne se crée tout seul.

### Créer l'abonnement (une fois par environnement)

Strava n'accepte **qu'un seul abonnement par application**, avec une seule URL de rappel. Si
chaque instance créait le sien au démarrage, production et préproduction se voleraient le flux à
tour de rôle. L'abonnement se pose donc à la main, depuis l'environnement qui doit le recevoir.

> ⚠ **L'adresse contient `/api`.** L'API est servie derrière un préfixe de contexte
> (`server.servlet.context-path: /api`) : le webhook est donc à
> `https://api.darilab.app/api/public/strava/webhook`, et non à `.../public/strava/webhook`.
> C'est l'erreur qui coûte le plus cher ici, parce qu'elle est muette : l'adresse sans préfixe
> renvoie une 404, Strava la valide par un GET immédiat, ne la trouve pas, et refuse l'abonnement
> sur un « callback url not verifiable » qui ne dit pas ce qu'il a appelé. La documentation et
> `.env.example` donnaient jusqu'ici la variante sans préfixe.
>
> Le back-office contrôle désormais la forme de l'adresse **avant** d'appeler Strava, et l'écran
> « Tableau de bord » affiche le chemin attendu sous le bouton « Activer ».

1. Poser les deux variables sur l'instance, et **la redéployer** :

   ```bash
   STRAVA_WEBHOOK_CALLBACK_URL=https://api.darilab.app/api/public/strava/webhook
   STRAVA_WEBHOOK_VERIFY_TOKEN=$(openssl rand -hex 16)   # secret partagé avec Strava
   ```

2. Vérifier que l'adresse est joignable **depuis l'extérieur** — Strava la valide dans la seconde
   qui suit la demande, et un 404 fait échouer la création :

   ```bash
   curl "https://api.darilab.app/api/public/strava/webhook?hub.mode=subscribe&hub.challenge=test&hub.verify_token=<le-jeton>"
   # attendu : {"hub.challenge":"test"}
   ```

   Une réponse vide ou une page d'erreur = mauvaise adresse (préfixe `/api` oublié, le plus
   souvent). Un `403` = le jeton passé ne correspond pas à `STRAVA_WEBHOOK_VERIFY_TOKEN`, ou
   l'instance n'a pas été redéployée depuis que la variable a été posée.

3. Créer l'abonnement, connecté en `PLATFORM_ADMIN` — depuis l'écran d'administration
   (« Tableau de bord » → *Synchronisation Strava en direct* → **Activer**), ou en ligne de
   commande :

   ```bash
   curl -X POST https://api.darilab.app/api/admin/strava/webhook -H "Authorization: Bearer <jeton-admin>"
   ```

   `GET` sur la même adresse montre l'abonnement en place ; `DELETE /{id}` le retire.

En cas de refus, Strava répond en clair et le message est remonté tel quel
(« callback url not verifiable », « already exists » si un abonnement existe déjà — le supprimer
d'abord).

### Ce que le webhook ne fait pas

- **Il n'importe rien lui-même.** Il déclenche la synchronisation existante, avec le jeton de
  l'athlète : aucune donnée n'entre par ce canal, et un identifiant d'athlète inconnu de
  l'instance est ignoré sans effet. C'est ce qui rend une URL publique acceptable.
- **Il ne supprime pas.** Une activité effacée côté Strava reste chez nous : elle a pu être notée,
  un ressenti déposé, une séance rapprochée — ce travail ne disparaît pas sur un signal externe.
- **Il ne réimporte pas en boucle.** Une même sortie génère plusieurs événements (création, puis
  renommage) ; ils se coalescent en une seule synchronisation, le quota Strava étant de 100
  requêtes par quart d'heure pour toute l'application.

---

## 7. Inscription — le régime « sur demande »

En profil `prod`, `REGISTRATION_MODE` vaut **`request`** : c'est le régime de la bêta ouverte.

### Pourquoi ce régime

Les deux autres échouent pour des raisons opposées :

- **`open`** — `/auth/register` créait un club et un compte propriétaire sur la seule unicité de
  l'adresse. N'importe qui, robot compris, repartait avec un espace complet ; et chaque tentative
  consommait le quota d'envoi d'e-mails, partagé avec les réinitialisations de mot de passe.
- **`invite`** — un code partagé ferme la porte, mais il faut le distribuer à la main à chaque
  nouveau coach, il se transfère, se colle dans un message, et ne dit jamais qui s'en est servi.
  Tenable pour cinq coachs qu'on connaît ; plus au-delà.

En **`request`**, le formulaire « Créer mon club » reste ouvert à tous, mais il dépose une
**demande** : rien n'est créé avant décision.

### Le parcours, des deux côtés

**Côté candidat** — `/register` affiche un formulaire sans mot de passe (nom, club, e-mail,
téléphone et message facultatifs, acceptation des CGU). Au dépôt, il reçoit un accusé de
réception par e-mail et l'écran lui rappelle l'adresse qu'il a saisie — c'est ce qui permet de
repérer une faute de frappe avant d'attendre une réponse qui n'arriverait jamais.

**Côté administrateur** — back-office → **Demandes de club** (`/admin/club-requests`) :

| Geste | Ce qu'il fait |
|---|---|
| **Valider** | Crée le club, le compte `HEAD_COACH`, le rattachement `OWNER` et la bibliothèque de départ, puis envoie au coach un lien pour choisir son mot de passe (valable 7 jours). Le lien est **aussi affiché à l'écran** : si l'envoi d'e-mails est éteint ou que l'adresse rebondit, c'est le seul moyen de débloquer le coach qu'on vient d'accepter. |
| **Refuser** | Demande un motif, qui part au demandeur. La demande reste en base : un refus se relit, et se conteste. |

Les deux gestes sont consignés au **journal d'audit** (`CLUB_REQUEST_APPROVED` /
`CLUB_REQUEST_REJECTED`), et les demandes en attente remontent en anomalie sur le tableau de bord
d'administration — parce que de l'autre côté, un coach attend d'entrer et n'a aucun autre moyen
de le faire.

> Le mot de passe n'est **jamais** transmis. Le compte créé reçoit un secret aléatoire que
> personne ne connaît ; le coach pose le sien via le lien reçu à l'adresse déposée. Ce lien fait
> donc deux choses : il ouvre le compte, et il prouve que le demandeur est bien titulaire de
> cette adresse — c'est pourquoi aucun second e-mail de vérification n'est envoyé.

### Rouvrir ou refermer

```bash
REGISTRATION_MODE=open      # inscription libre (ouverture publique)
REGISTRATION_MODE=invite    # cohorte fermée — exige REGISTRATION_INVITE_CODE
REGISTRATION_MODE=request   # bêta ouverte, sur demande validée (défaut prod)
```

Un redéploiement suffit ; la page d'inscription lit le régime actif au chargement et affiche le
formulaire correspondant.
