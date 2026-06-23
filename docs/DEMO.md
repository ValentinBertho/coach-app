# Démo — comptes & réinitialisation

> Le jeu de données de démonstration est chargé **uniquement en profil `dev`** (ou si
> `app.seed.enabled=true`). Rien n'est activé en production par défaut.

## Comptes de démonstration

Tous les mots de passe : **`password123`**

| Rôle | Email | Accès après connexion |
|---|---|---|
| **PLATFORM_ADMIN** | `admin@coachrun.fr` | `/admin` (back office) |
| **HEAD_COACH** | `demo@coachrun.fr` | `/app` (espace coach, club « Running Club Lyon ») |
| **COACH** | `coach@coachrun.fr` | `/app` (même club) |
| **ATHLETE** | `athlete@coachrun.fr` | `/athlete/today` (séance du jour) |

> La connexion est **role-aware** : chaque compte est redirigé vers son espace.
> Les autres coachs des clubs secondaires suivent le motif `head-<slug>@coachrun.fr` /
> `coach-<slug>@coachrun.fr`.

## Contenu du jeu de démo

- **3 clubs** (Running Club Lyon, Trail Académie Annecy, Marathon Team Paris).
- Par club : 1 responsable + 1 coach, **10–15 athlètes** (sexes, niveaux, statuts variés,
  dates d'inscription échelonnées sur ~1 an).
- **Invitations** dans plusieurs états (en attente, acceptée, sans compte).
- **Séances** (semaine passée + à venir) et **activités** importées (rapprochées / non rattachées)
  pour quelques athlètes → calendrier et dashboard parlants.

## Réinitialiser la démo (RAZ)

Repart d'un état propre avant chaque démo : **purge toutes les données** puis **recharge le jeu de démo**.

### Garde-fous
- Réservé au rôle **PLATFORM_ADMIN**.
- Activé seulement si **`app.demo.reset.enabled=true`** (défaut `false`) — **vrai en profil `dev`**.
- **Interdit en production** (refusé si le profil `prod` est actif), quelle que soit la valeur du flag.

### Depuis l'interface
1. Se connecter en `admin@coachrun.fr`.
2. Aller sur **/admin** (Dashboard).
3. Cliquer **« Réinitialiser la démo »** → confirmer dans la modale.
4. Toast de succès puis rechargement automatique de l'UI.

### Via l'API
```bash
curl -X POST https://<api>/api/admin/demo/reset \
  -H "Authorization: Bearer <token-admin>"
```

### Activer la RAZ hors dev (staging)
Définir la variable d'environnement :
```
DEMO_RESET_ENABLED=true
```
(et ne **pas** utiliser le profil `prod`).

---

## DARI Lab — lancer et présenter la démo

### Lancer l'application (le plus simple)

```bash
docker-compose up --build
```

Cela démarre PostgreSQL + le backend (profil `dev`, **seed automatique idempotent**) + le
frontend. Une fois prêt, ouvrir le front (port indiqué par `docker-compose`) et se connecter
avec **`demo@coachrun.fr` / `password123`**.

> Démarrage manuel (sans Docker) : `cd back && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
> (PostgreSQL requis), puis `cd front && npm install && npm start` (proxy `/api` → `:8080`).

### Données DARI Lab pré-chargées (club principal)

- **6 athlètes** avec profil physiologique : discipline (route/trail), seuils **LT1/LT2/VC**,
  FC, et **performances 5/10 km** → **VDOT et allures d'équivalence calculés automatiquement**.
- **Test lactate complet** (6 paliers) pour l'athlète démo → **détection LT1/LT2** (Dmax modifié).
- **Bibliothèque d'exercices de force** (Squat, Soulevé de terre, Gainage, Fentes) + **1RM testé**
  (Squat 120 kg) pour l'athlète démo.

### Parcours de démonstration suggéré (coach)

1. **Dashboard** (`/app`) — les athlètes sont répartis **Route / Trail** avec leur **pastille
   d'état de forme** (vert/orange/rouge, calculée sur fatigue + douleur, jamais le RPE).
2. **Fiche athlète** (cliquer une pastille) — carte **Profil physiologique** : **VDOT**, seuils
   **LT1/LT2/VC** (km/h + FC) et **allures calculées** par distance.
3. **API physiologie & force** (Swagger `/api/swagger-ui.html` ou appels directs) — montrer les
   moteurs : `POST …/session-calc` (prescription en fourchettes → allures/FC/RPE),
   `POST …/lactate-tests/detect` (LT1/LT2 temps réel), `POST …/pp/calc/e1rm` (1RM Nuzzo + zones),
   `GET …/load` (ACWR/monotonie), `GET …/pp/sessions/{id}/calculated` (charges en kg depuis le 1RM).

> Le socle DARI Lab (physiologie, lactate, charge, préparation physique) est **entièrement
> opérationnel côté API** (91 tests). Les écrans coach **Dashboard** et **Profil physiologique**
> sont câblés ; les autres écrans DARI Lab consomment ces mêmes endpoints.
