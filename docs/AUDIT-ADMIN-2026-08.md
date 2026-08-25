# Audit & refonte de l'espace d'administration — août 2026

> Portée : `/admin` (front Angular) et `/admin/**` (API Spring). Objectif : passer d'une
> succession d'écrans CRUD à un **centre de pilotage** — comprendre l'état de la plateforme,
> retrouver n'importe quelle ressource, aider un utilisateur et diagnostiquer un incident
> **sans ouvrir psql**.

---

## 1. État des lieux (avant)

### 1.1 Ce qui existait et fonctionnait

| Élément | Fichier | Verdict |
|---|---|---|
| Coquille `/admin` + navigation | `front/src/app/features/admin/admin-layout.component.*` | Correcte, mais 8 liens à plat |
| Garde de rôle | `front/src/app/core/guards/admin.guard.ts` | Correcte (renvoi vers l'accueil du rôle réel) |
| `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` | 5 contrôleurs admin, **au niveau classe** | Correct, aucune route admin non protégée |
| Impersonation | `service/ImpersonationService.java` | Bien bornée (pas d'admin→admin, pas de refresh, log WARN) |
| Observabilité e-mail | `AdminController#mailStats/#mailLog`, `admin-mail.component.*` | **La référence** : plafonds, histogramme, journal, motifs d'échec |
| Webhook Strava | `AdminStravaWebhookController` | Bon geste explicite, refus de Strava relayé en clair |
| Retours bêta | `admin-feedback.component.ts` | Triage simple et suffisant |
| RAZ démo | `DemoResetService` | Garde-fou hors prod |

### 1.2 Ce qui était incomplet ou peu exploitable

1. **Tableau de bord sans pilotage.** `AdminStatsResponse` = 7 compteurs bruts
   (`clubs, headCoaches, coaches, athletes, pendingInvitations, workouts, activities`).
   Aucune tendance, aucun « nouveaux comptes », aucun « utilisateurs actifs », aucun signal
   d'anomalie. On ne peut **rien décider** en regardant cet écran.
2. **Aucune fiche.** Ni fiche utilisateur, ni fiche club. `AdminClubService#get` et
   `AdminService.club(id)` existaient… sans aucun appelant. Tout se passait par édition en ligne
   dans le tableau, ce qui interdit d'afficher le contexte (clubs rattachés, intégrations,
   dernière activité, historique).
3. **Pas de recherche globale.** Il fallait déjà savoir si « Dupont » était un utilisateur, un
   athlète ou un club pour choisir le bon onglet.
4. **Aucune journalisation des actions d'administration.** Rien en base. La seule trace de
   l'impersonation était une ligne `WARN` dans les logs applicatifs. Suppression de compte,
   changement de rôle, suspension de club : **aucune traçabilité**.
5. **Pas de notion d'activité.** `User` ne portait ni `lastLoginAt` ni `lastSeenAt` : « utilisateurs
   actifs » était structurellement impossible à répondre.

### 1.3 Fonctionnalités mortes (back sans front)

| Endpoint | Statut |
|---|---|
| `PUT /admin/users/{id}/clubs/{clubId}` | Existait, **aucun appel front** — le multi-club de coach n'était pilotable que par SQL |
| `DELETE /admin/users/{id}/clubs/{clubId}` | Idem |
| `GET /admin/clubs/{id}` | Existait, appelé par `AdminService.club()`, **jamais utilisé** |
| Filtre `status` sur `GET /admin/users` | Accepté par le contrôleur, **jamais envoyé** par `AdminService.users()` alors que le composant déclarait `statuses` |
| Filtre `status` sur `GET /admin/athletes` | Idem |

### 1.4 Doublons et frictions

- **Athlètes / Invitations** : deux écrans pour la même table. `AdminAthleteResponse` porte déjà
  `invitationPending` ; l'écran « Invitations » n'apporte que la date d'expiration.
- **Sélecteur de club** chargé en page 0 uniquement (`this.admin.clubs(undefined, 0)`) dans
  `admin-users` et `admin-athletes` : **au-delà de 20 clubs, le filtre devient faux** — les clubs
  suivants sont introuvables dans la liste déroulante.
- Statuts affichés en brut (`ACTIVE`, `SUSPENDED`, `INVITED`) dans une UI française.
- Pas de total d'éléments, pas de tri, pas de taille de page.
- États `error` absents : `error: () => this.loading.set(false)` laisse un tableau vide
  indiscernable d'un « aucun résultat ».

### 1.5 Actions qui exigeaient une manipulation technique

| Besoin support | Avant |
|---|---|
| « Je ne reçois pas l'e-mail de vérification » | `UPDATE users SET email_verified = true` ou rien |
| « Réinitialise-moi mon mot de passe » | Demander à l'utilisateur de passer par « mot de passe oublié » |
| « Déconnecte toutes mes sessions » | `UPDATE users SET sessions_invalidated_at = now()` |
| « Renvoie l'invitation à cet athlète » | Impossible : seule la **révocation** existait |
| « Ce coach doit intervenir sur 2 clubs » | `INSERT INTO user_clubs` |
| « Qui a supprimé ce compte ? » | Impossible |
| « Cet athlète a-t-il bien connecté Strava ? » | `SELECT * FROM device_connections` |
| « Que va détruire la suppression de ce club ? » | Aucune idée avant de cliquer |

### 1.6 Risques identifiés

| # | Risque | Gravité |
|---|---|---|
| R1 | **Auto-verrouillage** : `AdminUserService#update` laissait un admin changer son propre rôle ; `#delete` laissait supprimer le dernier administrateur → plateforme sans back-office | Élevée |
| R2 | **Aucun audit** des actions sensibles (suppression, changement de rôle, impersonation, suspension) | Élevée |
| R3 | **Suspension sans effet** : passer un compte à `SUSPENDED` bloquait le prochain *login* mais pas les sessions en cours (le JWT reste valide jusqu'à expiration) | Moyenne |
| R4 | **Données de santé** : `AdminAthleteService#update` écrit `medicalNotes`, `hrMax`, `hrRest`, `vma`, `weightKg` — chiffrées au repos, mais modifiables par l'admin **sans trace** | Moyenne |
| R5 | Suppression de club en cascade **sans aperçu d'impact** | Moyenne |
| R6 | Création d'un `PLATFORM_ADMIN` par un admin, avec mot de passe choisi, **sans trace** | Moyenne |
| R7 | Pas d'IDOR détecté (l'admin est global par construction) — mais toute nouvelle route admin doit rester sur `@PreAuthorize` de classe | Faible |

**Points sains à préserver** : aucun secret exposé (`AdminStravaWebhookController#view` ne rend
que l'URL de rappel, jamais le `verifyToken`) ; aucun token OAuth exposé ; pas de donnée de santé
dans les logs.

---

## 2. Architecture cible

Six zones, une par question que se pose l'administrateur.

| Zone | Route | Question à laquelle elle répond |
|---|---|---|
| **Pilotage** | `/admin` | « La plateforme va-t-elle bien, et sinon où ça coince ? » |
| **Utilisateurs** | `/admin/users`, `/admin/users/:id` | « Qui est cette personne, et que puis-je faire pour elle ? » |
| **Clubs** | `/admin/clubs`, `/admin/clubs/:id` | « Que se passe-t-il dans ce club ? » |
| **Athlètes & invitations** | `/admin/athletes`, `/admin/invitations` | « Où en est cet athlète ? » |
| **Supervision** | recherche globale, `/admin/mail`, `/admin/feedback`, `/admin/platform` | « Comment je diagnostique ce ticket ? » |
| **Audit** | `/admin/audit` | « Qui a fait quoi, quand, sur quoi ? » |

Principes retenus :

- **Un signal vaut mieux qu'un graphique.** Le tableau de bord affiche des *anomalies
  actionnables* (plafond e-mail proche, comptes non vérifiés qui traînent, invitations qui
  expirent, club sans coach, webhook Strava éteint) avec un lien vers l'écran qui les résout.
- **Toute mutation admin est journalisée**, sans exception et sans donnée sensible dans le
  résumé.
- **Aucune deuxième architecture** : mêmes DTO `Request`/`Response`, même `PageResponse`, mêmes
  composants partagés (`app-paginator`, `app-skeleton`, `app-empty-state`, `ConfirmService`,
  `ToastService`), mêmes jetons de design.

---

## 3. Priorisation

### P0 — livré

1. Journal d'audit administratif (entité, migration, service, écran, câblage sur **toutes** les
   mutations admin + impersonation).
2. Traçage d'activité (`users.last_login_at`, `users.last_seen_at`) → « utilisateurs actifs » réel.
3. Tableau de bord de pilotage : santé, croissance, engagement, **signaux d'anomalie**,
   intégrations, dernières actions d'administration.
4. Recherche globale (utilisateurs / clubs / athlètes en un appel), accessible depuis l'en-tête.
5. Fiche utilisateur + actions de support sécurisées (suspendre / réactiver, réinitialiser le mot
   de passe, renvoyer la vérification, fermer les sessions, rattacher/détacher un club,
   impersonation, suppression).
6. Fiche club : composition, activité, intégrations, **aperçu d'impact avant suppression**.
7. Durcissement : impossible de se démettre soi-même, de se suspendre, de se supprimer, ou de
   supprimer le dernier administrateur actif ; suspension = fermeture immédiate des sessions.

### P1 — livré

8. Filtres `status` câblés (utilisateurs et athlètes), libellés français, totaux, tri.
9. États `loading` / `empty` / `error` sur tous les tableaux admin.
10. Renvoi d'invitation athlète + copie du lien.
11. Navigation regroupée en sections.
12. Écran « Configuration plateforme » en lecture seule (profil, version, mode d'inscription,
    plafonds, intégrations configurées) — **sans aucun secret**.

### P2 — non livré, volontairement

- Export CSV des tableaux (utile, mais pas bloquant pour un ticket de support).
- Fusion des écrans « Athlètes » et « Invitations » (doublon assumé : le second sert de file de
  travail).
- Rétention/purge automatique du journal d'audit (à décider avec le métier — un journal de
  sécurité se conserve plutôt longtemps).
- Suspension d'un club propageant aux comptes de ses coachs.

---

## 4. Hors périmètre, mais constaté

Deux choses relevées pendant l'audit qui dépassent `/admin` et n'ont donc **pas** été traitées
au-delà de la zone auditée :

- **Jeton CSS `--line` inexistant.** Il n'est défini nulle part dans `styles.scss` (le jeton réel
  est `--hairline`), et une trentaine de composants l'utilisent : la bordure retombe alors sur
  `currentColor`, donc sur la couleur du texte. Corrigé dans `admin-feedback.component.ts` ;
  ailleurs, c'est un passage sur le design system, à faire d'un bloc.
- **`AdminFeedbackComponent` appelle `HttpClient` directement** au lieu de passer par un service
  de `core/services/`. Laissé tel quel : le déplacer sans besoin ne ferait que déplacer du code,
  et l'écran est autonome et cohérent.

---

## 5. Ce qui a été livré

### Migrations

| Fichier | Contenu |
|---|---|
| `090-admin-audit-log.yaml` | Table `admin_audit_log` (acteur recopié, action, cible, résumé, adresse d'appel) + 3 index. Sans clé étrangère : une trace ne bloque ni ne suit une suppression. |
| `091-user-activity-tracking.yaml` | `users.last_login_at`, `users.last_seen_at` + index. Additifs et nullables. |

Les deux sont **additives** : aucune colonne supprimée, aucune donnée réécrite (§4 bis).

### Routes ajoutées

`GET /admin/overview` · `GET /admin/search` · `GET /admin/platform` ·
`GET /admin/audit` · `GET /admin/audit/actions` ·
`GET /admin/users/{id}/detail` · `POST /admin/users/{id}/suspend` ·
`POST /admin/users/{id}/reactivate` · `POST /admin/users/{id}/revoke-sessions` ·
`POST /admin/users/{id}/password-reset` · `POST /admin/users/{id}/resend-verification` ·
`GET /admin/clubs/{id}/detail` · `POST /admin/invitations/{athleteId}/resend`

`GET /admin/stats` est **conservé tel quel** bien que `/admin/overview` le remplace à l'écran :
des PWA en cache l'appellent encore.

### Correctifs de défauts existants

- **Perte de la date de naissance.** Le formulaire d'édition d'athlète de l'administration ne
  portait pas `birthDate`, alors que le serveur écrit ce qu'il reçoit : **tout enregistrement
  depuis cet écran effaçait la date de naissance**, sans rien dire.
- **Sélecteur de statut inerte.** `AthleteRequest` n'avait pas de champ `status` : archiver un
  athlète depuis l'administration semblait fonctionner et ne changeait rien. Champ ajouté
  (facultatif, `null` = inchangé) et appliqué par la seule administration — côté coach,
  l'archivage garde sa route dédiée.
- **Suspension sans effet immédiat.** Elle ne bloquait que la prochaine connexion ; elle ferme
  désormais les sessions ouvertes.
- **Sélecteurs de club tronqués.** Chargés en page 0 (20 éléments) : au-delà, le filtre devenait
  faux en silence.
