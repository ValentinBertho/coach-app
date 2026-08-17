# Audit technique de bêta ouverte — DARI Lab (août 2026)

> **Question posée** : les audits de juillet ayant traité le produit et l'ergonomie, que reste-t-il
> qui empêche d'ouvrir à des inconnus ?
>
> **Méthode** : lecture du code (`back/src`, `front/src`), de la configuration, des migrations, de
> `ops/`, de `docker-compose.yml` et de la CI. Chaque constat cite un fichier et une ligne.
> L'application n'a pas été relancée — c'est délibéré, l'audit de juillet l'a fait, et ce qui
> suit ne se voit pas à l'écran.
>
> **Périmètre** : ce qui a bougé depuis les deux audits de juillet — bêta ouverte et préparation
> à la bêta — et ce qui manque encore. Les constats déjà tracés par ces deux documents ne sont pas
> répétés ; ils ont été retirés du dépôt une fois clos, et leur texte reste dans l'historique git.

> **État au 3 août 2026 — lot de correctifs livré.** Tout ce qui est marqué ✅ ci-dessous est
> implémenté et couvert par des tests. Trois points restent à la main de l'exploitant (variables
> Railway, test de restauration, identité de l'éditeur) : ils sont listés en fin de document.

---

## Verdict en trois lignes

**Le basculement e-mail → push de juillet était la bonne décision, et il a déplacé le risque.**
Les protections construites pour l'e-mail — appels bornés, envoi après commit, remontée des
échecs — n'ont pas suivi sur le chemin du push, devenu le chemin chaud.

**Le consentement était traité comme une case à l'entrée, pas comme un état.** Il était écrit une
fois à l'acceptation de l'invitation et jamais relu avant d'écrire une donnée de santé — alors que
le parcours normal du coach est de remplir la fiche *avant* d'inviter.

**Et le back-office d'administration était inatteignable en production**, faute de compte
administrateur : ni révocation d'invitation, ni suppression de compte coach, ni support.

---

## 1. Ce que les audits de juillet disent encore de faux

| Affirmation | Vérification |
|---|---|
| READINESS §4 — « Access token TTL 900 s (prod) » | ❌ **faux**. `application.yml:71` : `${JWT_ACCESS_TTL:3600}`, non surchargé par `application-prod.yml`. La fenêtre de révocation est d'**1 h**. |
| READINESS §2 — « 57 changelogs » | ⚠️ obsolète : 67 aujourd'hui. |
| READINESS §12 — « 151 tests backend » | ⚠️ 83 classes back, mais **8 specs front sur 209 fichiers TS** avant ce lot. |
| Runbook Phase 7 — « Canal de feedback ✅ livré » | ⚠️ c'était un `mailto:` (`support-link.ts:15`). Voir §5. |

---

## 2. Bloquants corrigés

### 2.1 ✅ Aucun compte `PLATFORM_ADMIN` n'existait en production

Le seul code qui en créait un était `DemoSeedService.java:142`, exécuté par `DevSeedConfig.java:17`
(`@Profile("dev")`), désactivé en production. Aucune migration n'en insérait, aucune route ne
permettait d'en promouvoir un — `AdminUserService.update()` ne change un rôle que depuis un compte
déjà administrateur.

`/admin` était donc inatteignable, et avec lui : la **révocation d'invitation**
(`admin-invitations.component.ts:39`, seul chemin du produit), la **suppression d'un compte coach**
(seule réponse possible à une demande d'effacement RGPD, que `legal.component.ts` promet par
e-mail), les statistiques et toute la gestion des clubs.

**Correctif** : `PlatformAdminBootstrap` — création idempotente au démarrage depuis
`PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD`, sans jamais modifier un compte existant (un
redéploiement ne doit pas réinitialiser l'accès administrateur à la valeur d'une variable
d'environnement). Documenté au runbook §1.1 bis.

### 2.2 ✅ Le push appelait FCM en synchrone, sans délai, dans les transactions

`PushNotificationService.sendToUser` portait `@Transactional` et appelait `service().send()` en
boucle, en synchrone. Trois appelants, tous transactionnels : `WorkoutService.create:102`,
`ReminderScheduler` (toutes les séances du lendemain, tous clubs confondus, en une transaction) et
`AlertDigestScheduler` (**tous les clubs en une seule transaction**).

Le client web-push n'avait aucun délai : `HttpClients.java` borne Resend et Strava —
`OutboundResilienceTest` le vérifie — mais `nl.martijndwars:web-push` construit son propre client.

C'est mot pour mot le défaut corrigé pour l'e-mail (`NotificationService.java:421-424`), sauf que
le push est désormais le geste quotidien. Un endpoint lent immobilisait une connexion Hikari ; sur
le digest, il empêchait le commit, donc le départ de **tous** les e-mails de digest de la journée.

**Correctif** : envoi après commit, remise sur un exécuteur dédié à file bornée, requête préparée
par la bibliothèque mais exécutée par un client HTTP à délais explicites (3 s / 10 s), digest
découpé en une transaction par club avec poursuite du balayage en cas d'échec.

### 2.3 ✅ Les abonnements push morts coupaient le repli e-mail du rappel J-1

Les réponses `404`/`410` — abonnement révoqué par le navigateur — étaient avalées en `log.debug`,
inactif en production. Les lignes mortes s'accumulaient, et surtout `canReach()` les comptait comme
joignables : `notifyWorkoutReminder` partait « en push » dans le vide et **retournait sans envoyer
d'e-mail**. L'athlète cessait silencieusement d'être prévenu de ses séances.

**Correctif** : suppression de l'abonnement sur 404/410, remontée des autres échecs en `warn` +
Sentry.

### 2.4 ✅ Bug annexe — le rappel J-1 n'était jamais enregistré dans le centre de notifications

`ReminderScheduler` portait `@Transactional(readOnly = true)`, or `NotificationService.record()`
**écrit**. Une transaction en lecture seule met Hibernate en `FlushMode.MANUAL` et ne vide jamais
le contexte au commit : l'insertion était perdue, sans erreur. Seul le push partait, ce qui rendait
le défaut invisible pour qui avait accepté les notifications système.

### 2.5 ✅ `RATE_LIMIT_TRUSTED_PROXY_HOPS` : le commentaire disait 2, le défaut valait 1

`application.yml` documentait « Vercel → Railway = 2 en production » puis posait `:1`. Le runbook ne
mentionnait pas la variable, `StartupSecretsValidator` ne la contrôlait pas. À 1,
`RateLimitFilter.clientIp` retenait l'adresse du relais Vercel — **la même pour tous** — et toute la
plateforme partageait un seul compteur : cinq mots de passe erronés, et plus personne ne se
connecte.

**Correctif** : défaut à 2, et refus de démarrer en dessous.

### 2.6 ✅ Les flux SSE et les pièces jointes échappaient à tout rate limiting

`RateLimitFilter.bearerKey()` ne lisait que l'en-tête `Authorization`. Or `EventSource` ne peut pas
en poser : `JwtAuthenticationFilter.allowsQueryToken:89` autorise `access_token` en paramètre sur
`/stream` et `/attachment`. Ces routes n'avaient donc **aucune** limite, alors que le runbook
documente lui-même que le proxy Vercel coupe mal les connexions longues — et que le navigateur
reconnecte tout seul, indéfiniment.

**Correctif** : le jeton en paramètre alimente la même clé de comptage, et
`NotificationStreamService` plafonne à six flux simultanés par utilisateur.

### 2.7 ✅ `MAIL_ENABLED=true` ouvrait un amplificateur d'e-mails authentifié

`/auth/resend-verification` et `PATCH /auth/me` retombaient sur le plafond général de 300
requêtes/minute. Le premier régénère un lien à chaque appel, le second envoie une vérification à
une adresse **arbitraire**. Le plan Resend est à 100 e-mails/jour, partagé avec les
réinitialisations de mot de passe : le quota tombait en vingt secondes.

**Correctif** : bucket dédié, 3 envois par heure et par porteur de jeton.

### 2.8 ✅ Le désabonnement push d'un tiers était possible

`DELETE /push/subscribe?endpoint=…` supprimait l'abonnement correspondant sans vérifier son
propriétaire — le seul endroit du produit où une ressource d'autrui était modifiable sans contrôle.
**Correctif** : suppression bornée au porteur du jeton, et désabonnement automatique à la
déconnexion (sur un appareil partagé, l'abonnement restait rattaché au compte précédent).

---

## 3. Consentement et RGPD

### 3.1 ✅ Le retrait du consentement santé n'existait pas

`legal.component.ts` promet le retrait « à tout moment ». `healthDataConsentAt` était écrit une fois
(`AuthService.java:276`) et n'était plus relu que par l'export. Aucun endpoint, aucun écran.
L'article 7-3 exige qu'il soit aussi simple de retirer que de donner.

**Correctif** : migration 066 (`health_data_consent_withdrawn_at`),
`GET`/`POST /me/consent{,/withdraw,/grant}`, bloc « Mes données de santé » dans le profil athlète.
Le retrait efface exactement ce que la politique désigne comme donnée de l'article 9 — tests de
lactate, douleur et fatigue déclarées, motif médical d'indisponibilité, notes médicales — et
prévient le coach référent. Le compte n'est pas touché : retrait et droit à l'oubli sont deux
droits distincts.

### 3.2 ✅ Le coach saisissait des données de santé sans base légale

Aucune vérification de consentement dans `AthleteService` ni `LactateTestService`, et
`AthleteResponse` n'exposait pas `healthDataConsentAt` : le coach ne pouvait pas savoir où il en
était. Or le parcours normal est de créer l'athlète, remplir son profil et ses tests, **puis**
l'inviter — et un athlète qui n'accepte jamais laissait des données de santé sans base légale,
indéfiniment.

**Correctif** : `HealthDataConsentValidator`, sur le modèle des validateurs anti-IDOR existants,
appelé avant la collecte ; consentement exposé au coach par un badge « Santé non consentie ».

### 3.3 ✅ Les athlètes n'acceptaient jamais les CGU

`InvitationAcceptRequest` exigeait le consentement santé mais ne portait aucun `termsAccepted`, et
la page d'invitation n'affichait ni case ni lien vers les CGU. Le chemin coach le faisait pourtant,
à l'inscription (`AuthService.java:98`) comme à l'invitation (`:335`). Les athlètes sont la moitié
des utilisateurs et les personnes concernées par les données de santé ; ce sont les CGU qui portent
l'avertissement santé et la clause de bêta.

**Correctif** : `termsAccepted` exigé côté serveur (`@AssertTrue`), case et lien dans la page
d'invitation, horodatage en base. Mot de passe et e-mail deviennent obligatoires au passage — un
compte accepté sans mot de passe n'avait plus aucune voie d'entrée, le jeton d'invitation étant
effacé dans la foulée.

### 3.4 ⚠️ Identité de l'éditeur — **reste à faire, et c'est bloquant**

`LEGAL_OWNER` ne portait qu'un nom commercial et un e-mail. L'exemption LCEN pour éditeur non
professionnel — correctement invoquée — couvre les mentions légales, **pas l'article 13 du RGPD**,
qui impose l'identité et les coordonnées du responsable de traitement. Le Service traite des données
de santé.

Les champs `legalName` et `address` ont été ajoutés et les blocs correspondants s'affichent dès
qu'ils sont remplis. **Ils sont vides.**

---

## 4. Gestion d'erreurs

### 4.1 ✅ Une UUID malformée renvoyait « erreur interne »

`GlobalExceptionHandler` ne dérivait pas de `ResponseEntityExceptionHandler` et ne traitait ni
`MethodArgumentTypeMismatchException`, ni `MaxUploadSizeExceededException`, ni
`HttpRequestMethodNotSupportedException`, ni `DataIntegrityViolationException`. Toutes tombaient
dans le filet à `Exception` → **500 + trace + capture Sentry**.

Deux effets : un lien tronqué ou une pièce jointe de 11 Mo s'affichaient comme une panne serveur
alors que le front sait très bien rendre un 413 ; et Sentry se remplissait de bruit dès le premier
robot d'indexation — ce qui rend inexploitable la règle d'alerte « nouvelle anomalie → e-mail »
recommandée par le runbook.

### 4.2 ✅ La suppression de compte athlète tenait en un tap

Un `confirm.ask()`, un clic, et une suppression en cascade irréversible dont le seul recours est la
restauration sélective d'une sauvegarde — procédure jamais exécutée à ce jour. Sur un écran de
profil consulté au téléphone.

**Correctif** : confirmation à recopie (`ConfirmService.askForText`), texte qui nomme ce qui sera
perdu, y compris pour le coach.

### 4.3 ✅ Le badge de notifications mourait silencieusement après une heure

`notification.service.ts:38` sortait immédiatement si un flux existait : l'effet se redéclenchait à
chaque rotation du jeton mais ne reconnectait jamais, et l'URL du flux gardait l'`access_token`
d'origine. Passé le TTL, la première coupure produisait une reconnexion avec un jeton expiré ; le
serveur répond 401 et `EventSource` cesse alors définitivement de réessayer. Aucun `onerror` ne
s'en apercevait.

**Correctif** : reconnexion avec jeton frais, recul exponentiel plafonné, resynchronisation du
compteur par requête HTTP classique (qui bénéficie du rafraîchissement automatique).

### 4.4 ✅ « Vérifie ton réseau » pour une panne serveur

`NetworkStatusService` ne lisait que `navigator.onLine`. Pendant un redéploiement — une à deux
minutes, plusieurs fois par semaine — l'utilisateur est « en ligne » et recevait une pluie de toasts
accusant son wifi.

**Correctif** : état « API injoignable » distinct, alimenté par l'intercepteur (statuts 0, 502, 503,
504), bandeau et messages dédiés.

---

## 5. ✅ Canal de retour

Le `mailto:` de `support-link.ts` supposait un client mail configuré — rare sur PWA mobile — et ne
laissait aucune trace : ni file, ni statut, ni recoupement avec Sentry. Il n'existait qu'en
navigation, c'est-à-dire jamais au moment du problème.

**Correctif** : `POST /feedback` (migration 067), formulaire court ouvrable depuis n'importe quel
écran, contexte joint automatiquement — page, version, navigateur, et l'**identifiant de
corrélation** que `GlobalExceptionHandler` produit déjà à chaque erreur et que personne ne
récupérait. File de traitement sur `/admin/feedback`. Le `mailto:` reste en repli.

---

## 6. Tests

Avant ce lot : **8 specs front pour 209 fichiers TS**, et rien sur les gardes de rôle — le correctif
le plus grave de l'audit de juillet, livré sans test de non-régression. Le back était bien mieux
tenu (83 classes).

Ajouté : gardes de rôle (12 cas, entrées **et** destinations de refus), `NetworkStatusService`,
consentement santé (8 cas), rate limiting des routes à jeton en paramètre et des routes à e-mail,
relais de confiance au démarrage. **Front : 50 tests, 0 échec. Back : suite complète verte.**

Reste ouvert : aucun test sur l'intercepteur d'erreurs et sa boucle refresh/rejeu, ni sur le
`FeedbackQueueService`, ni de bout en bout.

---

## 7. Ce qui reste à faire

### À la main de l'exploitant — bloquant

| # | Quoi | Où |
|---|---|---|
| 1 | `PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` sur Railway | Runbook §1.1 bis |
| 2 | **Test de restauration de la base** — toujours non coché, seule étape irrattrapable | Runbook §5.4 |
| 3 | Identité et adresse de l'éditeur dans `LEGAL_OWNER` | §3.4 |

### Non bloquant, premières semaines

- **Mesure d'usage** : aucun compteur produit n'existe. Sentry dit ce qui casse, pas ce qui sert.
- **Tags git par déploiement** : `appVersion` est passé à 0.2.0 et aligné aux trois endroits, mais
  sans tag, tous les événements Sentry porteront la même version.
- **Purge des comptes inactifs** : la politique annonce 24 mois avec préavis ; rien ne l'implémente.
- **Préproduction** : la CI reste le seul filet entre un commit et la production.
- Pagination du fil de messages, pièces jointes vers S3/R2, impersonation admin.

Et les points fonctionnels déjà tracés par l'audit de juillet, inchangés : renommage du club,
révocation d'invitation côté coach, stockage visible et libérable, `PATCH /me/physio`, compte
athlète, arbitrage des plans périodisés.

---

*Audit technique — DARI Lab, août 2026. Lecture de code, configuration, migrations et CI ;
correctifs implémentés et couverts par des tests dans le même lot.*
