# Audit de bêta ouverte — DARI Lab (juillet 2026)

> **Question posée** : « si j'ouvre l'inscription à 30 coachs inconnus demain, qu'est-ce qui va les
> faire décrocher ? » — et la même question pour leurs athlètes, sur téléphone, sans formation.
>
> **Périmètre** : complétude fonctionnelle (coach / athlète), parcours et ergonomie, design,
> accessibilité, réglages disponibles, politique de notification. **Hors périmètre** :
> infrastructure et exploitation (`AUDIT-BETA-READINESS-2026-07.md`), juridique, comparaison
> répétition par répétition (lot 9, décidé après bêta).
>
> **Méthode** : l'application a été **réellement lancée et parcourue**. Docker n'était pas
> disponible dans l'environnement d'audit, la stack a donc été montée à la main — PostgreSQL 16
> local, `mvn spring-boot:run` (profil `dev`, seed de démo), `ng serve` — et pilotée au navigateur
> (Chromium/Playwright) sur trois sessions : **coach démo** (1440 px), **athlète** (390 px, tactile)
> et **coach fraîchement inscrit** (parcours d'inscription réel). Contrastes, cibles tactiles,
> débordements à 320 px et styles calculés ont été **mesurés dans le navigateur**, pas déduits du
> code. Chaque constat cite un fichier et une ligne.

---

## Verdict en trois lignes

**Presque prêt — mais pas demain.** Le socle métier est solide et au-dessus du marché : le cockpit
par exception, le calendrier, l'éditeur en blocs et la boucle de retour athlète tiennent la route,
et les lots 1 à 8 de `AUDIT-RC-2026-07.md` sont bien livrés (vérifiés par sondage, voir §0).

Ce qui bloque n'est pas le cœur, ce sont **quatre trous sur les bords** : l'athlète qui ouvre la PWA
tombe sur le cockpit coach en erreur ; la politique d'e-mails va saturer le quota Resend dès le
premier jour et emporter avec elle les liens de réinitialisation ; le seul écran de gestion des
notifications est une maquette figée qui ment ; et la file « Retours à traiter » n'a ni fenêtre ni
pagination, donc elle devient illisible en trois semaines.

**Comptez 3 à 4 jours de correctifs ciblés avant d'ouvrir.** Aucun n'est structurel.

---

## 0. Vérification par sondage des points déjà cochés

Consigne explicite : ne pas refaire le travail des audits précédents, mais **vérifier que les cases
cochées le sont vraiment**. Sondage sur onze points.

| Point coché | Vérifié ? | Preuve |
|---|---|---|
| RC #3 — e-mail d'invitation athlète envoyé | ✅ vrai | `AthleteService.java:227` appelle bien `notifyAthleteInvitation` (`NotificationService.java:314`) |
| RC #6 — cockpit du premier jour | ✅ vrai | Inscription réelle → trois étapes numérotées avec boutons (capture `n01-first-screen`) |
| RC #9 — indisponibilités athlète | ✅ vrai | `AthletePortalController.java:488/497`, formulaire `profile.component.ts:212-262` |
| RC #23 — « Ma semaine » | ✅ vrai | `AthletePortalController.java:292`, rendu constaté sur `/athlete/today` |
| RC #26 — `/dev/*` derrière `adminGuard` | ✅ vrai | `app.routes.ts:62, 70` |
| UX — polices auto-hébergées | ✅ vrai | `styles.scss:16-56`, plus aucun `@import` Google Fonts |
| UX — plancher typo 11 px | ✅ vrai | `--text-2xs: 0.6875rem` (`styles.scss:162`), aucun texte en dessous |
| UX — `--ink-4` remonté | ⚠️ **partiel** | Remonté à 3,2–3,5:1 : conforme au seuil **non-textuel** (3:1), pas au **4,5:1** exigé pour le texte qu'il porte réellement (libellés d'axes, unités) — cf. §5.2 |
| RC #20 — quota de stockage par club | ⚠️ **livré mais inutilisable** | Le compteur `GET /clubs/{id}/storage` (`ClubController.java:45`) n'a **aucun consommateur front**, et aucun `DELETE` n'existe sur les messages — cf. §1.6 |
| BETA-READINESS §5 — « le plan gratuit Resend couvre largement une bêta de 10–30 coachs » | ❌ **faux** | Le calcul ne tient pas : ~11 000 e-mails/mois attendus contre 3 000/mois et 100/jour — cf. §6.1 |
| BETA-READINESS §9 — « états vides / loaders / erreurs ✅ » | ⚠️ **partiel** | Vrai côté coach ; l'écran `/app/notifications` est une maquette figée qui affiche un état vide même avec des notifications — cf. §3.3 |

Deux cases sur onze sont trompeuses, une est fausse. Les trois concernent des lots récents (7, 8) et
l'audit d'exploitation — pas le cœur historique.

---

## 1. Complétude fonctionnelle — côté coach

### 1.1 🔴 Les plans périodisés existent entièrement côté serveur et n'ont **aucune interface**

**Nature : présent mais inatteignable.**

Le cahier des charges §3.2.4 demande la construction d'un plan sur N semaines, son application à un
athlète et le suivi d'avancement. **C'est intégralement implémenté côté backend** :

- `back/src/main/java/com/coachrun/controller/TrainingPlanController.java:33` → `/clubs/{clubId}/training-plans`
  avec CRUD (`:40`, `:45`, `:50`, `:56`, `:62`), application à un athlète (`:68`), à un groupe
  (`:80`), avancement (`:88`) et désassignation (`:96`).
- `back/.../service/TrainingPlanService.java:39` — « Plans périodisés : CRUD scopé club + application
  à un athlète (génère les séances) », avec idempotence et `PlanAssignment` daté (`:105-120`).
- Côté athlète : `AthletePortalController.java:71` expose `GET /me/plans` — « Mon programme : plans
  attribués avec leur avancement ».

Côté front, **zéro occurrence de `training-plans`** dans tout `front/src`. Et le service athlète
porte la cicatrice de la suppression : `front/src/app/core/services/athlete-portal.service.ts:89`
contient le commentaire orphelin `/** Mon programme : plans attribués avec avancement. */` suivi…
d'aucune méthode.

**Impact concret.** Un coach qui veut « préparer un semi en 12 semaines » doit aujourd'hui
construire une semaine type puis lancer le générateur de mésocycle (`calendar.component.ts:706`),
qui **recopie la semaine source en la multipliant par un facteur**. C'est de la périodisation de
volume, pas un plan : pas de nom, pas de réutilisation d'un athlète à l'autre, pas de progression de
contenu (une semaine spécifique ≠ une semaine de base ×1,1), pas de suivi d'avancement, pas de
désassignation. Le coach qui cherche « mes plans » — le vocabulaire de tous les concurrents — ne
trouve rien, et l'athlète ne voit jamais « où j'en suis dans ma prépa ».

**Correction.** Deux options honnêtes :
- **(a) Ouvrir l'existant** : un écran « Plans » (liste + éditeur d'items + bouton « Appliquer à… »)
  et un bloc « Mon programme » côté athlète. Le serveur est prêt et testé. — **2 à 3 jours.**
- **(b) Assumer l'absence en bêta** et le dire dans l'aide (« la périodisation passe par le
  générateur de mésocycle »), puis livrer (a) au premier mois. — **1 heure.**

Ne **pas** laisser l'état actuel : du code serveur maintenu, migré et testé que personne n'atteint.

> 🔴 pour la décision (a/b), 🟠 pour la livraison de l'écran · **(b) 1 h · (a) 2-3 j**

### 1.2 🔴 « Retours à traiter » n'a ni fenêtre temporelle, ni pagination, ni action groupée

**Nature : présent mais cassé à l'échelle.**

`back/.../repository/WorkoutRepository.java:65-73` :

```
where w.athlete.id in :athleteIds
  and w.coachReviewedAt is null
  and (w.rpe is not null or w.pain is not null or w.athleteComment is not null)
order by w.scheduledDate desc, w.createdAt desc
```

Aucun `limit`, aucun `minusDays`, aucun `Pageable`. Le front charge tout d'un coup
(`feedback-queue.component.ts:149-151`) et rend une `<ul>` intégrale (`:53`), sans filtre ni
sélection multiple : la seule action est « Marquer comme traité » **une ligne à la fois** (`:88`).

**Constaté** : avec le seed de démo — **6 athlètes** — l'écran rend **148 lignes** et le KPI du
cockpit affiche « RETOURS À TRAITER 148 ». La pastille de navigation est bloquée sur « 9+ ».

**Impact concret.** C'est l'écran du matin, la destination du KPI, et le meilleur pari produit de
l'app. Un coach de bêta avec 15 athlètes accumule ~200 lignes en un mois. Il ne peut pas les vider
autrement qu'en 200 clics. Au bout de deux semaines il arrête d'ouvrir l'écran, et la pastille « 9+ »
permanente désensibilise à tout le reste de la navigation. Un compteur qui ne redescend jamais est
pire que pas de compteur.

**Correction.** Fenêtre par défaut à 14 jours (paramètre `?days=`), pagination serveur, et
« Tout marquer comme traité » sur le lot affiché. Trois lignes de JPQL, un `@RequestParam`, un
bouton.

> 🔴 · **0,5 j**

### 1.3 🟠 La liste des athlètes ne filtre pas par statut, et affiche le niveau en anglais brut

**Nature : deux régressions locales sur l'écran le plus ouvert.**

- Le backend accepte le filtre : `AthleteController.java:51` — `@RequestParam(required = false)
  AthleteStatus status`. Le front ne l'envoie **jamais** (`athlete-list.component.ts:84` ne passe que
  `q`, `groupId`, `page`). Résultat constaté : les athlètes **archivés** sont mélangés aux actifs,
  sans moyen de les masquer.
- `athlete-list.component.html:53` affiche `{{ a.level }}` — soit **`BEGINNER`**, **`INTERMEDIATE`**,
  **`ADVANCED`**, **`ELITE`** en toutes lettres anglaises, dans une interface française. La table de
  traduction existe déjà (`athlete-shell.component.ts:20` : `LEVEL_LABELS`), et le formulaire
  (`athlete-form.component.html:56-59`) comme l'admin (`admin-athlete-edit.component.html:36-38`)
  affichent bien « Débutant / Intermédiaire / Avancé / Élite ». Seule la liste a été oubliée.

**Impact concret.** Le coach saisit « Débutant » dans le formulaire et relit « BEGINNER » dans la
liste : il croit à un bug de sauvegarde. Et il ne peut pas se débarrasser visuellement des athlètes
qu'il a archivés — ce qui vide l'archivage de son sens.

**Correction.** Réutiliser `LEVEL_LABELS` ; ajouter un `app-segmented-control` Actifs / En pause /
Archivés / Tous qui passe `status` au service.

> 🟠 · **2 h**

### 1.4 🟠 Un coach ne peut pas renommer son club, ni y poser un logo, ni activer de modules

**Nature : absent.**

`ClubController.java:33-112` expose les membres (`:50`, `:60`, `:73`), les permissions par athlète
(`:79`-`:99`), les défauts d'intensité (`:107`, `:112`) et le compteur de stockage (`:45`).
**Il n'y a aucun `PUT /clubs/{clubId}`.** L'écran Club (`club.component.html:3`) affiche
`{{ user()?.clubName }}` en titre — en lecture seule.

Le nom du club est saisi **une seule fois**, à l'inscription (`AuthService.java:66`). Il apparaît
ensuite dans le badge de la barre latérale, dans **l'en-tête de chaque e-mail transactionnel**
(`AthleteService.java:226-227` → `notifyAthleteInvitation`) et sur la page d'acceptation d'invitation
(`InvitationInfoResponse`). Une faute de frappe à l'inscription est donc gravée, visible par tous
les athlètes invités, et **corrigeable uniquement en base ou via le back-office plateforme**.

Le cahier des charges §3.9 classe « Paramétrage club : coachs, groupes, **logo**, **modules
activés** » en **M** (indispensable MVP). Logo et modules activables sont absents.

**Impact concret.** Sur 30 coachs inconnus, la loi des grands nombres garantit plusieurs fautes de
frappe le premier jour — et autant de tickets support qui ne peuvent être traités que par vous, en
SQL.

**Correction.** `PUT /clubs/{clubId}` (nom) + champ éditable côté Club. Logo et modules : 🟢, après
bêta.

> 🟠 · **3 h** (nom seul)

### 1.5 🟠 Un coach ne peut pas révoquer une invitation qu'il a mal adressée

**Nature : absent côté coach, présent côté admin.**

`AthleteService.invite()` (`:213-231`) génère un jeton de 32 octets valable `INVITE_VALIDITY_DAYS` et
l'envoie à l'adresse saisie. La révocation existe — mais **uniquement dans le back-office
plateforme** : `front/src/app/features/admin/admin-invitations.component.ts:39`. Rien d'équivalent
côté coach (`InvitationController.java` n'expose que `GET /{token}` et `POST /{token}/accept`).

Atténuation réelle : réinviter écrase le jeton (`:221`), ce qui invalide l'ancien lien. Mais cela
renvoie au **même destinataire erroné** ; pour vraiment couper l'accès il faut d'abord corriger
l'adresse de l'athlète, puis réinviter — un enchaînement que personne ne devine.

**Impact concret.** Un lien d'invitation donne accès à une fiche athlète contenant des données de
santé (art. 9 RGPD : blessures, douleur, FC). Une adresse mal tapée envoie ce lien à un inconnu, et
le coach n'a aucun bouton pour l'annuler.

**Correction.** `DELETE /clubs/{clubId}/athletes/{athleteId}/invitation` (met `inviteToken` à
`null`) + bouton « Annuler l'invitation » sur le badge « Invité » de la liste.

> 🟠 · **3 h**

### 1.6 🟠 Le quota de stockage est un cul-de-sac : ni visible, ni libérable

**Nature : livré au lot 7, mais inatteignable.**

Le lot 7 point 20 est coché : plafond de 200 Mo par club, `413` nommant l'espace consommé, et
`GET /clubs/{clubId}/storage` pour exposer le compteur. Vérification :

- `ClubController.java:45` — l'endpoint existe.
- **Zéro consommateur dans `front/src`** (`grep -rn storage front/src/app` → aucun résultat).
- `MessageController.java:27-75` — les sept routes de la messagerie sont `GET`/`POST`.
  **Aucun `DELETE`** : ni sur un message, ni sur une pièce jointe.

**Impact concret.** Le club atteint 200 Mo. À partir de là, **toute** pièce jointe est refusée par un
413, définitivement. Le coach ne peut pas voir combien il consomme (pas d'écran), ne peut pas faire
de place (pas de suppression), et n'a aucune action possible : le seul recours est vous, en base. Le
message d'erreur nomme l'espace consommé mais ne dit pas quoi en faire — c'est exactement le
« message d'erreur qui n'indique pas la suite ».

**Correction.** Barre de consommation sur l'écran Club (l'API existe) + `DELETE` sur ses propres
messages avec purge de la pièce jointe.

> 🟠 · **0,5 j**

### 1.7 Ce qui va bien, et qu'il ne faut pas toucher

Vérifié en parcourant les écrans : la gestion multi-athlètes au quotidien tient (coquille
persistante, switcher, précédent/suivant), le décrochage d'un athlète **est** couvert
(`CoachDashboardService.java:232-246` : alertes `MISSED` et `SILENCE`, triées par gravité,
`CoachAlertResponse` documenté), la bibliothèque de séances a recherche, favoris, « fréquentes »,
duplication (`template-list.component.html:94`) et partage club (`club.component.html:128`), et le
calendrier reste le meilleur écran de l'app.

---

## 2. Complétude fonctionnelle — côté athlète

### 2.1 🟠 L'athlète ne peut renseigner **aucune** de ses propres données physiologiques

**Nature : absent.**

`AthletePortalController.java:230` expose `GET /me/physio`. Il n'existe **ni `PUT` ni `PATCH`**. Le
front est cohérent : `profile.component.ts:81-114` rend le bloc « Mon profil physio » en lecture
seule, avec pour état vide « *Ton profil sera renseigné par ton coach après tes premiers tests.* »
(`:111`).

Ce que l'athlète peut faire seul, en revanche, est réel et bien fait : chronos
(`POST /me/performances`, `:360`), courses cibles (`POST/PATCH/DELETE /me/races`, `:258-276`),
sortie libre et import GPX (`:332`, `:342`), rapprochement manuel (`:316`), Strava (`:455-474`),
indisponibilités (`:488`), déplacement de séance (`:156`), export RGPD et suppression de compte
(`:505`, `:511`).

**Impact concret.** La **FC max** et le **poids** sont exactement les deux valeurs que l'athlète lit
sur sa montre et que le coach n'a pas. Aujourd'hui il faut envoyer un message au coach, qui ouvre la
fiche et saisit à la main. Sur un portail dont toute la promesse est « ton moteur physio se calcule
tout seul », c'est le point où l'athlète se dit que l'outil ne lui appartient pas.

**Correction.** `PATCH /me/physio` limité à `fcMax`, `fcRepos`, `poids` (les seuils restent calculés
et pilotés par le coach), avec le tag `origin="saisi"` qui existe déjà
(`data-origin-tag.component`), et une notification in-app au coach référent.

> 🟠 · **0,5 j**

### 2.2 🟠 L'athlète ne peut changer ni son mot de passe, ni son adresse, ni son nom

**Nature : présent mais inatteignable.**

`AuthController.java:61` (`PATCH /auth/me`) et `:68` (`POST /auth/change-password`) **ne sont pas
restreints par rôle**. Mais leur unique consommateur front est
`front/src/app/features/settings/settings.component.ts:218/236/258` — c'est-à-dire `/app/settings`,
un écran de la coquille **coach**. Le portail athlète n'a aucun équivalent : `profile.component.ts`
ne contient aucun champ de mot de passe (vérifié : `grep -i "password\|mot de passe"` → aucun
résultat).

**Impact concret.** Un athlète qui veut changer son mot de passe doit passer par « mot de passe
oublié » et son e-mail. S'il a changé d'adresse, il est bloqué et doit écrire à son coach, qui n'a
pas non plus le pouvoir de le faire. Trois clics d'écart avec le standard de n'importe quelle app.

**Correction.** Un bloc « Mon compte » dans `/athlete/profile` réutilisant les deux méthodes déjà
présentes dans `AuthService`.

> 🟠 · **3 h**

### 2.3 🟠 « Mes progrès » sert à l'athlète le tableau de bord de charge du coach, en l'état

**Nature : présent mais mal cadré.**

`athlete-progress.component.ts:141-153` affiche à l'athlète, sur son téléphone :
**ACWR 1,51 « Risque »** en rouge avec une icône d'alerte, **Charge aiguë (7 j) 2373 UA**,
**Charge chronique (28 j) 1567 UA**, **Monotonie 1,90**. Constaté à l'écran.

Le cahier des charges §3.4 range la charge d'entraînement dans les fonctions **coach** ; rien ne
demande de l'exposer à l'athlète. Et la doctrine du produit — répétée jusque dans les Paramètres —
est que l'état de forme se lit en fatigue + douleur, pas en indicateurs de charge.

**Impact concret.** Un athlète lit « Risque » en rouge et ne sait ni ce que c'est, ni quoi en faire,
ni s'il doit s'arrêter. Deux issues possibles : il écrit à son coach (support), ou il s'inquiète et
lève le pied de lui-même — en contournant précisément la décision que le coach est censé prendre.
« UA » et « monotonie » ne sont expliqués nulle part dans le centre d'aide athlète.

**Correction.** Soit remplacer le bloc par une lecture qualitative (« ta charge est en hausse — ton
coach en est informé »), soit le conserver derrière un repli « Pour aller plus loin » avec une
définition en une phrase par indicateur. La deuxième option est plus honnête et coûte moins cher.

> 🟠 · **3 h**

### 2.4 🟢 Une journée vide de l'agenda athlète ne propose rien

`/athlete/calendar` rend « — » sur les jours sans séance, sans aucune action (constaté). L'athlète
qui a couru spontanément un mardi libre ne peut pas consigner sa sortie **depuis le jour concerné** :
il doit deviner que cela se passe dans « Mes activités » (accessible via Progrès → Mes activités,
soit trois taps). Le coach, lui, a un « + » sur chaque case vide.

**Correction.** Rendre le « — » cliquable → feuille « Consigner une sortie » pré-datée.

> 🟢 · **2 h**

---

## 3. Parcours et ergonomie

### 3.1 🔴 L'athlète qui ouvre la PWA atterrit dans le cockpit coach, couvert d'erreurs

**Nature : présent mais cassé. C'est le constat le plus grave de cet audit.**

Chaîne complète, reproduite au navigateur :

1. `front/src/manifest.webmanifest:9` — `"start_url": "./"`. La PWA installée s'ouvre donc sur la
   **landing publique**, pas sur l'espace de l'utilisateur.
2. `front/src/app/features/home/home.component.html:7` et `:25` — pour tout utilisateur
   authentifié, quelle que soit sa **rôle**, les deux seuls CTA sont « Mon espace » et « Accéder à
   mon espace », tous deux vers **`/app`**.
3. `front/src/app/core/guards/auth.guard.ts` — le garde de `/app` (`app.routes.ts:76`) ne vérifie
   **que l'authentification**, jamais le rôle. Il existe un `athleteGuard` pour `/athlete` et un
   `adminGuard` pour `/admin` ; **il n'existe aucun `coachGuard`**.
4. `admin.guard.ts:13` — un non-admin qui touche `/admin` est renvoyé… vers `/app`. Pour un athlète,
   c'est la mauvaise destination.

**Résultat mesuré** (session athlète réelle, 390 px) : la page `/app` affiche « Bonjour Chloé
Dubois » dans la coquille **coach**, avec la bottom-nav coach (Accueil / Athlètes / Calendrier /
Plus), et **cinq toasts « Accès refusé. » empilés qui recouvrent l'écran** — un par appel 403
(`/dashboard`, `/dashboard/form`, `/dashboard/alerts`, `/messages/unread-count`, `/athletes`).
Capture : `y-pwa-2-apres-clic.png`.

Aucune donnée n'est exposée — le backend fait correctement son travail (403 sur toute la surface
club). Le problème est entièrement côté parcours, et il est massif.

**Impact concret.** L'athlète est la moitié des utilisateurs de la bêta, il est sur téléphone, il n'a
reçu aucune formation, et le produit **lui demande activement d'installer la PWA**
(`app-install-button` sur la landing et dans son profil). Son premier geste après installation
l'amène sur un écran cassé qui n'est pas le sien. Il ne reviendra pas le lendemain.

**Correction.** Trois changements, tous petits :
- `start_url` → `"/"` avec une redirection de `/` vers `/athlete/today` ou `/app` **selon le rôle**
  quand l'utilisateur est authentifié (le `HomeComponent` a déjà `auth.currentUser()`).
- Un `coachGuard` (rôle ∈ {COACH, HEAD_COACH, PLATFORM_ADMIN}) sur `/app`, renvoyant un athlète vers
  `/athlete`.
- `adminGuard` : rediriger vers l'espace du rôle réel, pas vers `/app` en dur.

> 🔴 · **3 h**

### 3.2 🟠 Les toasts identiques s'empilent au lieu de se fondre

Corollaire du constat précédent, mais indépendant : cinq toasts « Accès refusé. » strictement
identiques s'affichent simultanément et recouvrent le viewport mobile ; il faut cinq taps pour les
fermer. `Design.md` §8 pose le toast comme le canal d'erreur global — il ne prévoit pas la
déduplication.

**Correction.** Dans `ToastService`, fusionner un message identique déjà affiché (compteur « ×5 »)
et plafonner la pile à trois.

> 🟠 · **2 h**

### 3.3 🔴 `/app/notifications` est une maquette figée, inatteignable, et c'est la cible des e-mails

**Nature : présent, cassé, ET inatteignable. Les trois à la fois.**

`front/src/app/features/settings/notifications.component.ts` fait 51 lignes et **n'injecte aucun
service HTTP**. Concrètement :

- `:44-50` — la liste des « types d'alertes » est un tableau **en dur** : « Fatigue élevée »,
  « Douleur », « Séance déplacée », « Nouveau retour », « Activité synchronisée
  (Strava/**Garmin**) ». Aucune ne correspond aux codes réellement émis par le moteur
  (`CoachAlertResponse` : `PAIN`, `ACWR_HIGH`, `ACWR_LOW`, `MONOTONY`, `MISSED`, `SILENCE`), et
  Garmin n'existe pas dans le produit.
- `:30-34` — l'état vide « **Aucune notification pour le moment** » est **écrit en dur** dans le
  gabarit. Constaté sur le coach de démo, qui a des notifications et une pastille « 9+ » : l'écran
  affiche quand même « aucune notification ».
- **Aucun réglage.** Pas une case à cocher.
- Aucun lien vers `/app/notifications` n'existe dans tout `front/src/app`
  (`grep -rn "app/notifications"` → aucun résultat) : ni dans la barre latérale, ni dans le panneau
  « Plus » (`coach-layout.component.html:50-52` et `:138-140` ne listent que Paramètres et Aide).

Et pourtant c'est **exactement là que pointent tous les e-mails** :
`back/.../integration/MailTemplate.java:33` — `COACH("/app/notifications")`, utilisé à la fois pour
le lien « Gérer mes notifications » du pied de page (`:124`) et pour l'en-tête **`List-Unsubscribe`**
(`:65`).

**Impact concret.** Un coach reçoit un e-mail, clique « Gérer mes notifications », arrive sur un
écran qu'il n'aurait jamais trouvé autrement, qui lui explique des alertes qui n'existent pas, lui
affirme qu'il n'a aucune notification alors qu'il en a, et ne lui offre **aucun moyen de se
désabonner**. Un `List-Unsubscribe` qui ne désabonne pas, sur un volume d'envoi élevé (§6), c'est la
recette pour des signalements en spam et une réputation de domaine détruite pendant la bêta.

**Correction.** Cet écran doit devenir le centre de préférences réel — voir §6.2, où le correctif est
détaillé, parce que le problème est le même.

> 🔴 · **traité en §6.2**

### 3.4 Ce qui va bien — première ouverture et boucle quotidienne

Vérifié par un parcours d'inscription réel :

- **Coach, premier écran** : trois étapes numérotées avec bouton (Créer ton premier athlète →
  Renseigne son profil physio → Planifie sa première séance) au lieu d'un cockpit vide. Le lot 2 #6
  est bien livré et il fonctionne. Capture `n01-first-screen.png`.
- **États vides** : Calendrier (« Aucun athlète actif » + « + Nouvel athlète »), Bibliothèque,
  Groupes, Retours, Messages ont tous un texte et, sauf la bibliothèque, un CTA. C'est propre.
- **Boucle quotidienne coach** : le panneau bibliothèque à gauche du calendrier avec Favoris,
  Fréquentes et catégories, plus le glisser-déposer — le geste répété est court.
- **Boucle quotidienne athlète** : « Aujourd'hui » puis feuille de ressenti en 10 s ; les chips de
  zone du calendrier portent couleur **et** libellé (Z1/Z2/Z5) — daltonisme respecté.

Une réserve d'ordonnancement : sur `/athlete/today`, le check-in matinal
(`today.component.html:26`) occupe **tout le premier écran**, suivi de « Ma semaine » (`:31`), puis
du rappel d'allures (`:56`), puis de l'objectif, et **enfin** de la séance du jour.
`Design.md` §6 pose pourtant en premier principe : « **« Séance du jour » en première vue athlète** :
ce qu'il doit faire aujourd'hui, en un écran. » Aujourd'hui elle est en quatrième position.
**Correction** : replier le check-in en une ligne (« Comment tu te sens ? ›») qui se déploie au tap,
et remonter la séance. 🟠 · **2 h**

### 3.5 Fonctionnalités orphelines — récapitulatif

De la valeur déjà payée, aujourd'hui inatteignable :

| Fonctionnalité | Où elle vit | Atteignable ? |
|---|---|---|
| Plans périodisés (CRUD, application athlète **et groupe**, avancement) | `TrainingPlanController` + `TrainingPlanService` + `PlanAssignment` | ❌ aucun appel front |
| « Mon programme » athlète | `AthletePortalController.java:71` | ❌ méthode front supprimée, commentaire resté (`athlete-portal.service.ts:89`) |
| Compteur de stockage club | `ClubController.java:45` | ❌ aucun appel front |
| Filtre par statut de la liste d'athlètes | `AthleteController.java:51` | ❌ jamais envoyé par le front |
| Préférences e-mail / push | `User.java:82-86`, migration `035` | ❌ lues à chaque envoi, **aucune API ni UI pour les écrire** |
| Écran `/app/notifications` | `app.routes.ts:192` | ⚠️ URL directe et lien d'e-mail uniquement |

---

## 4. Design et cohérence

Le système de design est **globalement bien tenu** : tokenisation complète, icônes Lucide via
`<app-icon>`, mono tabulaire sur les métriques, thème sombre complet, couleur **plus** libellé sur
les zones. Les écarts trouvés sont peu nombreux, ce qui est en soi une bonne nouvelle. Trois méritent
d'être signalés.

### 4.1 🟠 Le calendrier écrase ses colonnes vides et désaligne son en-tête

`front/src/app/features/calendar/calendar.component.scss:5` et `:9` :

```scss
.weekday-row { grid-template-columns: repeat(7, 1fr) minmax(92px, 0.58fr); }
.grid        { grid-template-columns: repeat(7, 1fr) minmax(92px, 0.58fr); }
```

`1fr` vaut `minmax(auto, 1fr)` : une colonne dont le contenu a un `min-content` large — une pastille
de séance avec un titre long et une métrique — **déborde de sa part** et vole l'espace aux colonnes
vides. Constaté avec le panneau bibliothèque ouvert (capture `03-calendar.png`) : lundi à jeudi font
~185 px, **vendredi, samedi et dimanche tombent à ~75 px**, et la ligne d'en-tête `.weekday-row` —
qui, elle, n'a pas de contenu et reste donc parfaitement régulière — **ne s'aligne plus** sur les
jours en dessous.

Le correctif est connu du projet : la grille de groupe, dix lignes plus bas, l'applique déjà
(`:220` — `repeat(7, minmax(120px, 1fr))`).

**Impact concret.** Le glisser-déposer est **le** geste du calendrier, et les jours vides — ceux sur
lesquels on dépose — sont précisément ceux qui rétrécissent. Plus la semaine est chargée, plus la
cible de dépôt est étroite.

**Correction.** `repeat(7, minmax(0, 1fr))` sur les deux règles, plus les variantes `:98` et `:111`.

> 🟠 · **1 h**

### 4.2 🟢 Quelques valeurs en dur subsistent, toutes localisées

Sur ~42 occurrences de couleurs hexadécimales hors `styles.scss`, la grande majorité sont des
**replis** de la forme `var(--form-green, #11c08b)` — inoffensifs, le token existe bien
(`styles.scss:208-210`). Les vraies valeurs en dur sont trois :

- `athlete-activities.component.ts:243-245` — tracé Leaflet en `#0e6e78` / `#0e9e74` / `#e25e3a`.
  Ce sont les valeurs du **thème clair**, appliquées sur un portail athlète toujours sombre
  (`athlete-shell.component.ts:17` — `data-theme="dark"`).
- `training-zones.component.ts:311-359` — couleur par défaut d'une nouvelle zone : `#22c55e`, un vert
  qui n'appartient à aucune palette du produit (ni `--zone-2 #16c47f`, ni `--form-green`). Toute zone
  créée par un coach naît donc hors charte.
- `update-banner.component.ts:31/40` — `color: #fff` et `background: #fff` en dur.

> 🟢 · **2 h**

### 4.3 Densité et écrans plus anciens

Aucun écran ne se détache visuellement comme « plus ancien » : la passe de finition décrite dans
`audit-ui-ux-dari-lab.md` §6 a bien été passée partout (skeletons, Lucide, tokens). La seule
disparité de densité réellement gênante est celle déjà relevée dans cet audit — `.btn` à 48 px par
défaut, rattrapé par `btn-sm` presque partout côté coach. Elle reste ouverte ; je ne la re-signale
pas.

---

## 5. Accessibilité et robustesse d'affichage

### 5.1 🔴 Les curseurs du check-in matinal ne sont pas stylés du tout sous Chrome

**Nature : présent mais cassé — et c'est la fonctionnalité vedette du lot « boucle athlète ».**

`morning-check-in.component.ts:120-138` définit un joli curseur : piste de 6 px, **pouce de 28 px**,
`background: var(--primary)`, et un pouce vert (`--accent`) pour le sommeil dont le commentaire dit
« *la couleur le dit* » (`:135-136`).

**Mesuré dans le navigateur** sur `.ci__range` :

```
appearance: "auto" · webkitAppearance: "auto"
```

Or les pseudo-éléments `::-webkit-slider-runnable-track` et `::-webkit-slider-thumb` **ne
s'appliquent que si l'input porte lui-même `appearance: none`**. La règle pose bien
`-webkit-appearance: none` — mais sur le **pouce** (`:126`), pas sur l'input. Sous Chrome, Edge et
tous les navigateurs iOS (WebKit), **toute la mise en forme est inerte**.

Constaté à l'écran (capture `a01-today.png`) : trois curseurs natifs, piste filaire, **pouce bleu
navigateur d'environ 16 px**, sur un fond sombre teal. Le codage couleur du sommeil est mort avec le
reste. Firefox, lui, applique bien `::-moz-range-thumb` : le composant n'a **pas la même tête selon
le navigateur**.

**Impact concret.** Trois conséquences simultanées : cible tactile de ~16 px là où le cahier des
charges §5.4 exige ≥ 44 px et où le commentaire du code promet « *le curseur doit s'attraper à
moitié réveillé, sans viser* » ; rupture visuelle franche sur l'écran d'accueil de l'athlète ; et
perte du repère couleur. C'est le geste censé prendre dix secondes tous les matins.

**Correction.** Une ligne : ajouter `appearance: none; -webkit-appearance: none;` sur `.ci__range`
(`:120`).

> 🔴 · **15 min**

### 5.2 🟠 `--ink-3` échoue au contraste AA en thème clair, sur tout le texte secondaire

**Mesuré** (`styles.scss:116`, `--ink-3: #74767f`) :

| Combinaison | Ratio | Exigé (AA, texte normal) |
|---|---|---|
| `--ink-3` sur `--canvas` `#f4f2ec` | **4,04:1** | 4,5:1 ❌ |
| `--ink-3` sur `--paper` `#fffefb` | **4,49:1** | 4,5:1 ❌ *(d'un cheveu)* |
| `--ink-3` sur les surfaces sombres | 5,42 – 5,87:1 | ✅ |

`--ink-3` porte **le sous-titre de chaque en-tête de page** (`.subtitle`), **chaque `.field-hint`**
(donc tous les textes d'aide des formulaires) et les intitulés de groupe de la barre latérale
(`.snav-group`). Balayage automatisé sur six écrans coach : l'échec est systématique et identique
partout.

Le thème sombre est conforme. **Seul le thème clair — celui du coach, par défaut — est en cause.**
L'audit UI/UX précédent avait corrigé `--ink-4` sans vérifier `--ink-3`.

Note connexe : `--ink-4`, remonté à 3,2–3,5:1, satisfait le seuil **non textuel** (3:1) mais pas le
4,5:1 exigé pour le texte qu'il porte réellement (`.blab`, libellés d'axes de graphes à 11 px,
`athlete-progress.component.ts:299`).

**Correction.** `--ink-3: #6b6d76` → **4,60:1** sur `--canvas`, **5,11:1** sur `--paper`. La nuance
est imperceptible à l'œil. Même passe sur `--ink-4` pour les usages textuels.

> 🟠 · **1 h** (valeur + revue visuelle)

### 5.3 🟢 Cibles tactiles sous 44 px et un débordement à 320 px

Mesures effectuées à 320 px et 390 px sur le portail athlète :

- `/athlete/today` : bouton de notifications `.bell-btn` **40 × 40**, bouton d'aide `.help-hint`
  **30 × 30**, lien « Mes progrès → » **86 × 21**.
- `/athlete/profile` : « ← Aujourd'hui » **118 × 40**, « Déclarer une indisponibilité » **223 × 40**.
- `/athlete/progress` **déborde de 9 px à 320 px** (`scrollWidth 329` / `clientWidth 320`) : les
  libellés de l'axe des graphes sont en `white-space: nowrap`
  (`athlete-progress.component.ts:299`). Les sept autres écrans athlète tiennent à 320 px.

Le reste est bon : hiérarchie des titres propre partout (un seul `h1`, puis des `h2`, vérifié sur
trois écrans), `prefers-reduced-motion` respecté, `:focus-visible` global, bottom-nav à 56 px de
haut, et les libellés de zone doublent systématiquement la couleur.

**Correction.** Plancher de 44 px sur les boutons-icônes du portail ; `white-space: normal` +
`overflow-x: auto` sur le conteneur de graphe.

> 🟢 · **3 h**

### 5.4 Cas extrêmes

- **Aucune donnée** : couvert partout côté coach (§3.4). Un manque : l'état vide de la bibliothèque
  (`n04-library-empty.png`) dit « Crée ton premier modèle de séance » **sans bouton** — le CTA est en
  haut à droite, hors du regard. 🟢 · 30 min.
- **200 athlètes** : la pagination serveur existe (`AthleteController.java:54`,
  `@PageableDefault(size = 20)`) et le front la consomme. Tient.
- **Nom très long** : le badge de club et le nom d'athlète de la coquille se tronquent correctement ;
  rien de cassé constaté.
- **Hors ligne** : `app-offline-banner` présent sur les deux espaces, file de retours hors ligne
  côté athlète. Bien couvert.
- **La file « Retours »** est le seul écran qui ne tient pas à l'échelle — cf. §1.2.

---

## 6. Réglages, options, et politique de notification

### 6.1 🔴 Le volume d'e-mails va saturer le quota Resend le premier jour — et emporter les liens critiques

**Nature : dimensionnement faux, coché à tort dans l'audit d'exploitation.**

Inventaire réel des envois (`NotificationService.java`) :

| Déclencheur | Canal | Fréquence |
|---|---|---|
| Séance planifiée (`:52`) | in-app + push + **e-mail** | **une par séance créée**, appelé depuis `WorkoutService.java:102` et `:352` |
| Rappel J-1 (`:221`) | **e-mail seul** | **une par séance prévue le lendemain**, tous les jours à 18 h (`ReminderScheduler.java:28`) |
| Retour d'athlète → coach (`:102`) | in-app + push + **e-mail** | **une par séance renseignée** |
| Commentaire du coach (`:80`) | in-app + push + **e-mail** | une par commentaire |
| Digest d'alertes coach (`:174`) | push + e-mail | 1/jour/coach — **bien conçu** |
| Indisponibilité (`:333`) | in-app + push + e-mail | rare |
| Vérification, réinitialisation, invitations (`:273`, `:285`, `:297`, `:314`) | e-mail | transactionnel légitime |

Point important à votre crédit : les générations en lot **ne notifient pas** (`WorkoutService.java:177-182`
— « Statut PLANNED, sans retour, **sans notif** » ; `duplicateWeek` et `generateMesocycle` passent par
`copyWeek`). Le problème vient du geste **unitaire**, celui de tous les jours : un coach qui dépose
cinq séances depuis la bibliothèque envoie **cinq e-mails** à son athlète dans la minute.

**Ordre de grandeur pour 30 coachs × 8 athlètes = 240 athlètes :**

| Flux | Par semaine |
|---|---|
| Séance planifiée (4/athlète, à l'unité) | ~960 |
| Rappel J-1 (4/athlète) | ~960 |
| Retour → coach (~3/athlète) | ~720 |
| **Total** | **~2 640/semaine ≈ 11 000/mois** |

Le plan gratuit Resend : **3 000/mois et 100/jour**. Le plafond journalier tombe **le premier
jour**. `AUDIT-BETA-READINESS-2026-07.md` §5 affirme qu'il « couvre largement une bêta de 10–30
coachs » — le calcul n'avait pas été fait.

**Impact concret, et c'est le pire.** Quand le quota est atteint, Resend rejette **tout** — y compris
la **réinitialisation de mot de passe** et les **invitations**. Les deux e-mails que le runbook
classe bloquants deviennent silencieusement indisponibles, et
`NotificationService.java:388` avale l'échec en `log.warn` sans jamais le remonter à
l'utilisateur : le coach voit « e-mail envoyé », rien n'arrive, et personne n'est prévenu.

**Correction — c'est exactement votre intuition, et elle est juste.** L'e-mail doit redevenir le
canal du **transactionnel** ; le quotidien passe en push et in-app :

| Événement | Cible |
|---|---|
| Vérification d'adresse, réinitialisation, invitation athlète, invitation coach | **e-mail** (inchangé) |
| Séance planifiée | **push + in-app**, plus d'e-mail *(ou un seul e-mail « ton programme de la semaine est prêt », groupé, si l'athlète n'a pas activé le push)* |
| Rappel J-1 | **push** — aujourd'hui c'est le seul flux qui n'a **pas** de push alors que toute l'infra est là (`PushNotificationService` est utilisé par six autres notifications). C'est exactement à l'envers. |
| Retour d'athlète → coach | **in-app + pastille** ; e-mail seulement dans le digest de 7 h, qui existe déjà (`AlertDigestScheduler.java:38`) |
| Commentaire du coach | **push + in-app** |

Effet attendu : d'environ 11 000 e-mails/mois à **quelques centaines**, très en dessous du plan
gratuit, avec une meilleure réactivité (le push est instantané, l'e-mail ne l'est pas) et sans perdre
un seul signal — l'in-app et la cloche existent déjà et fonctionnent
(`notification-bell.component.ts`).

Deux compléments nécessaires :
- **Repli si push absent.** Tout le monde n'accepte pas les notifications. Regrouper les e-mails
  restants : un envoi par athlète et par jour maximum, pas un par séance.
- **Faire remonter l'échec d'envoi.** `NotificationService.java:388` doit au minimum incrémenter un
  compteur et remonter dans Sentry — un quota atteint doit se voir.

> 🔴 · **1,5 j**

### 6.2 🔴 Les préférences de notification existent en base, sont lues à chaque envoi, et **rien ne peut les écrire**

**Nature : présent mais inatteignable — et juridiquement gênant.**

- `User.java:82-86` — `notify_email_enabled` et `notify_push_enabled`, migration
  `035-notification-preferences.yaml`, valeur par défaut `true`.
- Elles sont **lues systématiquement**, douze fois : `NotificationService.java:57`, `:64`, `:85`,
  `:91`, `:107`, `:112`, `:188`, `:194`, `:227`, `:255`, `:343`, `:347`.
- Elles ne sont **jamais écrites** : aucun `setNotifyEmailEnabled` dans tout le backend, aucun
  endpoint, aucune occurrence de `notifyEmail` dans tout `front/src`.
- L'écran censé les porter, `/app/notifications`, est la maquette figée décrite en §3.3.
- Et chaque e-mail expose pourtant un lien « Gérer mes notifications » **et** un en-tête
  `List-Unsubscribe` pointant vers cet écran (`MailTemplate.java:33-34`, `:65`, `:124`).

**Impact concret.** Combiné au §6.1, on obtient : beaucoup d'e-mails, un lien de désabonnement dans
chacun, et un désabonnement impossible. C'est le scénario qui fait classer un domaine en spam par
Gmail — et il n'y a pas de retour en arrière rapide sur une réputation de domaine perdue pendant une
bêta. C'est aussi ce qu'exigent les règles d'expéditeur en volume (RFC 8058) et le RGPD.

**Correction.** Refaire `/app/notifications` en écran réel (et son pendant athlète dans
`/athlete/profile`) :
- `PATCH /auth/me/notifications` écrivant les deux drapeaux — plus, idéalement, un drapeau par
  famille (séances / retours / alertes), qui est ce que les gens veulent vraiment régler.
- Deux interrupteurs minimum : « Recevoir les e-mails », « Recevoir les notifications push ».
- Brancher la liste des notifications réelles (le `NotificationService` front alimente déjà la
  cloche) au lieu de l'état vide en dur, et corriger les libellés (retirer Garmin, aligner sur les
  codes réels).
- Ajouter l'entrée « Notifications » dans la barre latérale sous Réglages
  (`coach-layout.component.html:50`) et dans le panneau « Plus » (`:138`).

> 🔴 · **1 j**

### 6.3 Inventaire des réglages — ce qui existe, ce qui manquera

**Coach** (`/app/settings`, 284 lignes, écran réel et bien fait — l'audit produit le décrivait comme
une vitrine, ce n'est plus le cas) : nom, e-mail, mot de passe, thème clair/sombre/système, unité
d'allure, domaines d'intensité par défaut, facturation (bêta gratuite, informatif).

**Club** (`/app/club`) : coachs, rôles, permissions par athlète, bibliothèques partagées.

**Athlète** (`/athlete/profile`) : heure d'entraînement habituelle (qui pilote le rappel de
débriefing — bonne idée), installation PWA, activation du push, indisponibilités, objectifs, Strava,
export RGPD, suppression de compte.

**Admin** (`/admin`) : clubs, utilisateurs, athlètes, invitations (avec révocation).

**Ce qui manque et générera du support en bêta :**

| Réglage absent | Conséquence | Priorité |
|---|---|---|
| Préférences de notification (les deux rôles) | §6.2 — désabonnement impossible | 🔴 |
| Nom du club | §1.4 — faute de frappe gravée, corrigeable en base seulement | 🟠 |
| Mot de passe / adresse / nom de l'athlète | §2.2 — l'athlète passe par « mot de passe oublié » | 🟠 |
| FC max, FC repos, poids par l'athlète | §2.1 — il faut demander au coach | 🟠 |
| Premier jour de la semaine | Le calendrier est en lundi–dimanche en dur | 🟢 |
| Unités hors allure (km/mi, kg/lb) | Bloquant si vous ouvrez hors zone métrique | 🟢 |
| Thème du portail athlète | Sombre imposé (`athlete-shell.component.ts:17`) — non réglable | 🟢 |
| Logo et modules activables du club | CdC §3.9, classé **M** | 🟢 |

**Réglages présents mais sans effet** : aucun trouvé, à l'exception de l'écran
`/app/notifications` entier, qui n'est pas un réglage mais une illustration.

---

## Plan d'exécution ordonné

L'ordre suit une logique simple : **d'abord ce qui casse au premier contact, ensuite ce qui casse en
trois semaines, enfin ce qui manque.**

### Avant d'ouvrir — 3 à 4 jours

| # | Quoi | § | Effort | Pourquoi à cette place |
|---|---|---|---|---|
| 1 | `coachGuard` sur `/app`, `start_url` et CTA de la landing selon le rôle | 3.1 | 3 h | La moitié de vos utilisateurs ouvre l'app **là**. Rien d'autre ne compte si le premier écran de l'athlète est un cockpit coach en erreur. |
| 2 | `appearance: none` sur `.ci__range` | 5.1 | 15 min | Une ligne, et le geste quotidien de l'athlète redevient présentable. Meilleur rapport effort/effet du document. |
| 3 | Politique de notification : e-mail → transactionnel, push/in-app → quotidien | 6.1 | 1,5 j | À faire **avant** l'ouverture, pas après : le quota tombe le premier jour, et il emporte les liens de réinitialisation. Corriger après, c'est corriger une réputation de domaine déjà entamée. |
| 4 | `/app/notifications` réel + `PATCH` des préférences + entrée de nav | 6.2/3.3 | 1 j | Va avec le 3 : un `List-Unsubscribe` mort sur un volume élevé, c'est du spam signalé. Faire les deux ou aucun. |
| 5 | Fenêtre + pagination + action groupée sur « Retours à traiter » | 1.2 | 0,5 j | Le seul écran qui devient illisible **pendant** la bêta, pas après. Le corriger plus tard, c'est le corriger sur des données déjà accumulées. |
| 6 | Déduplication des toasts | 3.2 | 2 h | Petit, mais c'est le filet de sécurité du 1 : toute erreur en rafale recouvre le mobile. |
| 7 | Décider pour les plans périodisés : ouvrir ou documenter | 1.1 | 1 h (option b) | Décision, pas développement. À prendre avant l'ouverture pour que l'aide dise la vérité. |

### Premier mois — ~4 jours

| # | Quoi | § | Effort |
|---|---|---|---|
| 8 | `minmax(0, 1fr)` sur la grille du calendrier | 4.1 | 1 h |
| 9 | `--ink-3` → `#6b6d76` (+ revue de `--ink-4` textuel) | 5.2 | 1 h |
| 10 | Liste d'athlètes : libellés FR + filtre par statut | 1.3 | 2 h |
| 11 | Renommage du club (`PUT /clubs/{id}`) | 1.4 | 3 h |
| 12 | Révocation d'invitation côté coach | 1.5 | 3 h |
| 13 | `PATCH /me/physio` (FC max, FC repos, poids) | 2.1 | 0,5 j |
| 14 | Compte athlète : mot de passe, adresse, nom | 2.2 | 3 h |
| 15 | Stockage : compteur visible + `DELETE` sur ses messages | 1.6 | 0,5 j |
| 16 | Écran Plans périodisés, si l'option (a) a été retenue au point 7 | 1.1 | 2-3 j |
| 17 | Recadrer la charge côté athlète (repli « Pour aller plus loin ») | 2.3 | 3 h |
| 18 | Remonter la séance du jour au-dessus du check-in | 3.4 | 2 h |

### Plus tard

Cibles tactiles à 44 px et débordement à 320 px (5.3) · valeurs en dur restantes (4.2) · CTA sur
l'état vide de la bibliothèque (5.4) · jour vide actionnable dans l'agenda athlète (2.4) · premier
jour de semaine, unités, thème athlète, logo et modules du club (6.3).

---

## Ce que je ne toucherais pas

Le cockpit par exception, la coquille athlète du coach, l'éditeur de séance en blocs, la chaîne
physiologique (records → VDOT → zones → cibles) avec ses tags d'origine, la feuille de ressenti en
dix secondes, le panneau bibliothèque du calendrier et la direction artistique. C'est là que se
trouve votre avantage sur Nolio, et rien de ce qui précède ne le remet en cause.

---

*Audit de bêta ouverte — DARI Lab, juillet 2026. Application lancée et parcourue (PostgreSQL local +
Spring Boot profil `dev` avec seed de démo + `ng serve`), trois sessions navigateur (coach 1440 px,
athlète 390 px tactile, coach nouvellement inscrit), mesures de contraste, de cibles tactiles et de
débordement effectuées dans le navigateur.*
