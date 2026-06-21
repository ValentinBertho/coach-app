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
