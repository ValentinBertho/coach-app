# Montres connectées — Strava, Garmin, COROS

> Ce que fait chaque intégration, ses limites, comment l'activer et la diagnostiquer.
> Variables : [`DEPLOIEMENT.md`](./DEPLOIEMENT.md) §3. État au **14 novembre 2026**.

## En bref

| | Strava | Garmin Connect | COROS |
|---|---|---|---|
| **Statut** | En production | **Bêta** : accès Garmin (Starter) obtenu le 13/11/2026, premier test réel en cours | **Bêta** : premier test réel en cours |
| **Activation** | `STRAVA_CLIENT_ID` / `_SECRET` | `GARMIN_CLIENT_ID` / `_SECRET` / `_WEBHOOK_TOKEN` | `COROS_ENABLED=true` |
| **Connexion** | OAuth, par l'athlète | OAuth 2.0 PKCE, par l'athlète | OAuth 2.0 PKCE via COROS MCP, par l'athlète (client enregistré dynamiquement, aucun identifiant à demander) |
| **Séances → montre** | — | Course **et** renforcement, 14 prochains jours | Course seulement, 14 prochains jours |
| **Sorties → DARI Lab** | Webhook + filet horaire | Poussées par Garmin dès la synchro de la montre | Interrogées toutes les heures ; 30 derniers jours au premier import |
| **Séance retirée ou déplacée** | — | Supprimée / replanifiée chez Garmin | **Réécrite « Annulée par ton coach »** : COROS ne permet ni de supprimer ni de déplacer |
| **Quotas** | Limites API Strava | 100 appels/min (app), 200/athlète/jour | 50 fichiers FIT/athlète/24 h (arrêt volontaire à 40) |

Côté athlète : **Profil → Gérer mes connexions** (`/athlete/sync`). Le coach ne relie jamais un
compte à la place de l'athlète ; il voit l'état en lecture seule.

## Principes communs

- **Envoi par réconciliation** (Garmin, COROS) : toutes les 15 min, on compare ce qui devrait être
  sur la montre (séances des 14 prochains jours) à ce qui y est, et on corrige l'écart. Aucun appel
  tant qu'une séance ne change pas ; un envoi raté est rattrapé au passage suivant. Les séances
  passées restent dans l'historique de la montre.
- **Une sortie, une seule fois** : une sortie déjà arrivée par une autre source complète l'existante au lieu de la dupliquer. Chez COROS, elle n'est pas téléchargée, ce
  qui ménage le quota.
- **Rapprochement prévu / réalisé** identique pour toutes les sources (sport, date, volume, titre).
- **Jetons chiffrés** (AES-256-GCM) et rafraîchis automatiquement. Une déconnexion révoque
  l'accès chez le fournisseur (Garmin : `DELETE user/registration`).
- Une erreur d'envoi est signalée à l'athlète sur sa carte (« n séances n'ont pas pu être
  envoyées ») et journalisée.

## Activer en production

### Strava
Voir [`DEPLOIEMENT.md`](./DEPLOIEMENT.md) §6 (webhook ; l'adresse contient `/api`).

### Garmin
1. Portail Garmin Connect Developer → app « Darilab » : *OAuth redirect URL* =
   `https://www.darilab.app/app/garmin/callback` (= `GARMIN_REDIRECT_URI`).
2. `apis.garmin.com/tools/endpoints` : la **même** adresse pour Deregistration, User Permissions,
   Activities et Activity Details :
   `https://www.darilab.app/api/public/garmin/webhook/<GARMIN_WEBHOOK_TOKEN>`
   (jeton dans le chemin : l'outil supprime les `?token=`).
3. Railway : `GARMIN_CLIENT_ID`, `GARMIN_CLIENT_SECRET`, `GARMIN_REDIRECT_URI`,
   `GARMIN_WEBHOOK_TOKEN` (`openssl rand -hex 32`).

L'athlète choisit au consentement ce qu'il partage : sans *Workout Import*, rien ne part sur la
montre ; sans *Activity Export*, aucune sortie ne revient. Sa carte le lui dit.

### COROS
1. Railway : `COROS_ENABLED=true`. `COROS_REDIRECT_URI` se déduit de `FRONTEND_URL`.
2. Rien à déclarer chez COROS : le back s'enregistre au premier usage (table
   `oauth_client_registrations`, une ligne par serveur régional).

## Avant de sortir de la bêta

- [ ] **Garmin** : connexion, séance course et séance de renforcement reçues sur une vraie
      montre (allures en m/s, poids, étapes sans catégorie d'exercice), sortie reçue sans doublon
      avec Strava.
- [ ] **COROS** : connexion, codes de cible (temps, FC, récupération) et format `idInPlan`
      vérifiés sur une vraie montre ; import d'une sortie.
- [ ] **COROS** : réponse écrite à la question des conditions d'utilisation (usage commercial,
      accès automatisé) avant ouverture générale.
- [ ] Retirer la mention « bêta » de l'aide, de la FAQ et de ce document.

## Diagnostiquer

| Symptôme | Où regarder |
|---|---|
| Carte « indisponible » / « Bientôt » | Variables absentes : `/admin/platform` dit ce qui est posé |
| Retour de connexion vers `localhost` | `FRONTEND_URL` (COROS) ou `GARMIN_REDIRECT_URI` non posées |
| Garmin : aucune sortie ne revient | Endpoints non cochés ou jeton du chemin différent de `GARMIN_WEBHOOK_TOKEN` (notifications ignorées) |
| Séance absente de la montre | Permission *Workout Import* refusée (Garmin), séance hors fenêtre de 14 jours, ou erreur affichée sur la carte de l'athlète ; la montre doit aussi se synchroniser avec son application |
| COROS : import interrompu | Quota FIT atteint : reprise automatique le lendemain |

Tables utiles : `device_connections`, `garmin_workout_syncs`, `coros_workout_syncs`,
`coros_activity_imports`.

## Autres montres

Polar, Suunto, Wahoo… : l'export **FIT** de leur application s'importe tel quel (totaux mesurés,
tours, sport déclaré). Pas d'intégration directe prévue à ce stade.
