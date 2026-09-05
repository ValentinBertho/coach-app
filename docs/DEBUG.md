# Déboguer DARI Lab — mémo

> Deux chemins, au choix : **tout en Docker** (rien à installer, on attache le débogueur au
> conteneur) ou **dans l'IDE** (IntelliJ / VS Code, exécution locale). Les deux se rejoignent sur
> les mêmes leviers de journalisation, en fin de page.
>
> Repères utiles : backend `http://localhost:8081/api` en Docker, `http://localhost:8080/api` en
> local · classe principale `com.coachrun.CoachRunApplication` · comptes de démo dans le
> [README](../README.md#comptes-de-démonstration).

---

## A. Déboguer avec Docker

Le fichier [`docker-compose.debug.yml`](../docker-compose.debug.yml) est une **surcouche** :
il ouvre le port de débogage Java (5005) et passe l'application en `DEBUG`. Rien d'autre ne change.

### A1. Lancer la stack en mode debug

```bash
docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d --build
docker compose logs -f backend        # doit afficher : Listening for transport dt_socket at address: 5005
```

Ports : front **4200**, API **8081**, débogueur **5005**, PostgreSQL **5432**.

### A2. Attacher le débogueur

| IDE | Chemin |
|---|---|
| **IntelliJ** | Run → Edit Configurations → **+** → *Remote JVM Debug* → Host `localhost`, Port `5005` → OK → **Debug** |
| **VS Code** | configuration `Attach backend (Docker 5005)` du `launch.json` [plus bas](#b4-vs-code--launchjson) → F5 |

Poser un point d'arrêt (ex. `AuthController.login`), se connecter depuis http://localhost:4200 :
**la requête HTTP se fige** sur le point d'arrêt, et reprend quand on continue (F9 / F5).

> ⚠️ Le code source ouvert dans l'IDE doit correspondre au jar construit dans l'image. Après une
> modification : `docker compose -f docker-compose.yml -f docker-compose.debug.yml up -d --build backend`.
> Il n'y a **pas** de rechargement à chaud en Docker (pas de `spring-boot-devtools` au projet) —
> pour une boucle courte, préférer le [mode IDE](#b-déboguer-dans-lide).

### A3. Déboguer le démarrage lui-même

Migrations Liquibase, seed, bootstrap Spring : remplacer `suspend=n` par `suspend=y` dans
`docker-compose.debug.yml`. Le conteneur **attend** alors le débogueur avant de démarrer
(le healthcheck le déclarera *unhealthy* entre-temps : c'est normal).

### A4. Déboguer le front en Docker : ne pas le faire

L'image du front est un **build de production** servi par nginx, sans source maps : les points
d'arrêt navigateur tombent dans du code minifié. Bonne méthode — garder la base et l'API en Docker,
et servir le front en mode développement :

```bash
BACKEND_PORT=8080 docker compose up -d postgres backend   # l'API sur 8080, port attendu par le proxy Angular
cd front && npm start                                     # http://localhost:4200, source maps activées
```

Pourquoi 8080 : en `ng serve`, le front appelle l'API en **absolu**, `http://localhost:8080/api`
(`environment.development.ts`) — c'est aussi la cible de `front/proxy.conf.json`. L'origine
`http://localhost:4200` est bien dans l'allowlist CORS de la stack.

### A5. Inspecter la base et les conteneurs

```bash
docker compose exec postgres psql -U postgres -d coachrun     # \dt, \d+ table, select …
docker compose exec backend sh                                # shell dans le conteneur backend
docker compose logs -f --tail=100 backend                     # journaux, en continu
docker compose ps                                             # santé des trois services
```

---

## B. Déboguer dans l'IDE

Prérequis : **JDK 21**, **Node 20+**, et une base accessible — la plus simple étant celle de la
stack Docker, seule : `docker compose up -d postgres`.

### B1. IntelliJ IDEA — backend

1. **Ouvrir le projet** : *File → Open* → sélectionner **`back/pom.xml`** → *Open as Project*
   (ouvrir la racine du dépôt ne donne pas de projet Maven).
2. **SDK** : *File → Project Structure → Project SDK* = **21**.
3. **Lancer en debug** : ouvrir `com.coachrun.CoachRunApplication`, clic droit sur `main` →
   **Debug**. IntelliJ crée une configuration *Spring Boot*. Le profil `dev` est le défaut, aucune
   variable d'environnement n'est nécessaire.
4. **Variables d'environnement** (Strava, Resend, Sentry… — optionnelles) : *Edit Configurations →
   Environment variables*, en s'inspirant de [`.env.example`](../.env.example).
5. **Vérifier** : `curl http://localhost:8080/api/public/ping` → `{"status":"ok","version":"0.2.0"}`.

Confort IntelliJ qui paie sur ce projet :

- **Point d'arrêt conditionnel** (clic droit sur la pastille) : `athleteId.toString().equals("…")`
  — indispensable sur les moteurs de calcul, appelés en boucle sur tous les athlètes.
- **Evaluate Expression** (`Alt+F8`) pour rejouer un moteur (`VdotEngine`, `LoadEngine`) sur l'état
  courant sans relancer.
- **Database** (*View → Tool Windows → Database* → **+** → *Data Source → PostgreSQL*) :
  `localhost:5432`, base `coachrun`, `postgres` / `postgres`.
- **Tests** : clic droit sur `src/test/java` → *Debug 'All Tests'* — ils tournent sur **H2**, pas
  sur PostgreSQL (cf. README, section CI).

### B2. IntelliJ — attacher au conteneur

Voir [A2](#a2-attacher-le-débogueur) : *Remote JVM Debug*, `localhost:5005`.

### B3. VS Code — extensions

| Extension | Pour quoi |
|---|---|
| **Extension Pack for Java** (Microsoft) | exécution, debug et tests Java |
| **Spring Boot Extension Pack** | reconnaissance du projet Spring, navigation dans les routes |
| **Angular Language Service** | templates, autocomplétion, erreurs de template |

### B4. VS Code — `launch.json`

Créer `.vscode/launch.json` (le dossier `.vscode/` est ignoré par git, la configuration reste
personnelle) :

```jsonc
{
  "version": "0.2.0",
  "configurations": [
    {
      // 1. Backend lancé par VS Code (profil dev, port 8080)
      "type": "java",
      "name": "Backend (local)",
      "request": "launch",
      "mainClass": "com.coachrun.CoachRunApplication",
      "projectName": "coachrun-back",
      "cwd": "${workspaceFolder}/back"
    },
    {
      // 2. Backend qui tourne dans Docker (docker-compose.debug.yml)
      "type": "java",
      "name": "Attach backend (Docker 5005)",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005,
      "projectName": "coachrun-back"
    },
    {
      // 3. Front : points d'arrêt TypeScript dans VS Code, `npm start` lancé à côté
      "type": "chrome",
      "name": "Front (Chrome sur ng serve)",
      "request": "launch",
      "url": "http://localhost:4200",
      "webRoot": "${workspaceFolder}/front"
    }
  ]
}
```

Ordre habituel : `docker compose up -d postgres` → **Backend (local)** (F5) → dans un terminal
`cd front && npm start` → **Front (Chrome sur ng serve)** (F5).

### B5. Déboguer le front (les deux IDE)

`npm start` construit en configuration *development* : **source maps activées**, code non minifié.

- Points d'arrêt directement dans les `.ts`, depuis VS Code (configuration 3 ci-dessus) ou depuis
  les DevTools du navigateur (`Sources` → `webpack://` / fichiers d'origine).
- Onglet **Network** : en `ng serve` les appels partent **en absolu** vers
  `http://localhost:8080/api/...` (`environment.development.ts`), pas en relatif comme en
  production. Une erreur réseau ou CORS sur ces appels = backend éteint, ou publié sur un autre
  port que 8080.
- **Service worker** : désactivé en `ng serve` (`serviceWorker: false`), donc aucun cache parasite.
  Il n'est actif qu'en build de production — c'est-à-dire dans l'image Docker : en cas d'écran figé
  sur une vieille version, DevTools → *Application* → *Service Workers* → *Unregister*.
- Tests front : `npm test` (Karma, Chrome headless).

---

## C. Leviers communs (Docker comme IDE)

### C1. Niveaux de journalisation

Tous pilotés par variable d'environnement, sans recompilation
(cf. `back/src/main/resources/application-dev.yml`) :

| Variable | Défaut (dev) | Effet |
|---|---|---|
| `LOG_LEVEL_APP` | `DEBUG` | tout `com.coachrun` |
| `LOG_LEVEL_WEB` | `INFO` | `org.springframework.web` (routage, négociation de contenu) |
| `LOG_LEVEL_SQL` | `WARN` | `DEBUG` → **affiche chaque requête SQL** |
| `LOG_LEVEL_SQL_PARAMS` | `WARN` | `TRACE` → affiche les **paramètres** liés aux requêtes |

```bash
# En Docker : décommenter les deux lignes correspondantes dans docker-compose.debug.yml
# En local :
cd back && LOG_LEVEL_SQL=DEBUG LOG_LEVEL_SQL_PARAMS=TRACE ./mvnw spring-boot:run
```

> Le SQL est volumineux (une ouverture d'écran = des dizaines de requêtes) : à rallumer le temps
> d'une mise au point, pas en permanence.

### C2. Remonter d'une erreur 500 à sa trace

Une 500 renvoie un **`correlationId`** dans le corps JSON :

```json
{ "status": 500, "message": "Une erreur interne est survenue", "path": "/api/…",
  "correlationId": "79e70d54-36d1-4025-9876-1a2229452a44" }
```

Le même identifiant est écrit dans les journaux (et indexé côté Better Stack en production) :

```bash
docker compose logs backend | grep 79e70d54      # Docker
```

En local, la journalisation va **uniquement sur la console** (aucun fichier de log n'est
configuré) : chercher l'identifiant dans la sortie du terminal ou de l'IDE.

### C3. Points d'entrée pour reproduire un cas

| Outil | Où |
|---|---|
| **Swagger UI** (profil `dev`) | http://localhost:8081/api/swagger-ui.html (Docker) · :8080 (local) |
| **Health / info / metrics** | `/api/actuator/health`, `/api/actuator/info`, `/api/actuator/metrics` |
| **Ping** | `/api/public/ping` |
| **Comptes de démo** | `demo@coachrun.fr` / `password123` (et `admin@`, `coach@`, `athlete@`) |
| **Jeton pour `curl`** | `POST /api/auth/login` → `accessToken`, puis en-tête `Authorization: Bearer …` |

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@coachrun.fr","password":"password123"}' | jq -r .accessToken)
curl -s http://localhost:8081/api/clubs/<clubId>/athletes -H "Authorization: Bearer $TOKEN"
```

### C4. Repartir d'un état propre

```bash
docker compose down -v && docker compose up -d    # base vierge, re-migrée et re-seedée
```

Le jeu de démo est **déterministe et idempotent** : un redémarrage ne duplique rien, et le même
identifiant d'athlète revient d'une réinitialisation à l'autre — pratique pour un point d'arrêt
conditionnel.

---

## D. Pannes de débogage courantes

| Symptôme | Cause | Solution |
|---|---|---|
| `ERROR: transport error 202: socket creation failed: Address family not supported by protocol` | l'agent JDWP configuré en `address=*:5005` tente IPv6, absent | utiliser `address=0.0.0.0:5005` (c'est déjà le cas dans `docker-compose.debug.yml`) |
| L'IDE ne se connecte pas au port 5005 | stack lancée sans la surcouche debug | relancer avec `-f docker-compose.yml -f docker-compose.debug.yml` ; vérifier `docker compose ps` |
| Le point d'arrêt ne se déclenche jamais | jar de l'image plus à jour que les sources ouvertes | `… up -d --build backend` |
| Le point d'arrêt tombe dans du code illisible | build de production (image Docker) | déboguer le front via `npm start` ([A4](#a4-déboguer-le-front-en-docker--ne-pas-le-faire)) |
| Le front appelle `/api` et reçoit 404 en `ng serve` | backend éteint ou publié sur 8081 | `BACKEND_PORT=8080 docker compose up -d postgres backend` |
| Tout est figé après un point d'arrêt oublié | requête suspendue, healthcheck en échec | continuer l'exécution (F9 / F5) ou détacher le débogueur |
| Les tests passent mais l'application échoue en Docker | les tests tournent sur **H2**, la stack sur **PostgreSQL** | reproduire sur la stack Docker ; le smoke test CI couvre ce cas |
