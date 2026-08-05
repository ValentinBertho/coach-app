# Audit des notifications — DARI Lab Training (août 2026)

> **Question posée** : quand l'application doit-elle sortir de l'écran pour aller chercher un
> athlète ou un coach, et le fait-elle aujourd'hui au bon moment, avec le bon message, sans
> devenir un bruit qu'on coupe ?
>
> **Méthode** : lecture des trois canaux (in-app, Web Push, e-mail) depuis leurs déclencheurs
> réels dans le code métier, pas depuis la documentation. Chaque constat renvoie à un fichier.

---

## Verdict en trois lignes

**L'infrastructure est saine, la politique éditoriale ne l'est pas encore.** Le transport push est
sérieux — envoi après commit, hors du thread appelant, délais bornés, abonnements caducs purgés.
Ce qui manque est en amont : *qui* déclenche, *combien de fois*, et *sous quel libellé*.

**Attribuer un plan d'entraînement envoie une notification par séance générée.** Un plan de
12 semaines à 4 séances, c'est ~48 notifications in-app et ~48 push à l'athlète en une salve —
pour un seul geste du coach. C'est le meilleur moyen de faire couper les notifications à
l'utilisateur qu'on cherchait justement à engager.

**Le seul message que l'utilisateur attend vraiment n'est pas notifié : le message.** La
messagerie coach ↔ athlète n'appelle jamais `NotificationService`. Elle a un flux temps réel, donc
elle fonctionne *si l'application est déjà ouverte* — c'est-à-dire exactement dans le cas où la
notification ne sert à rien.

---

## 1. État des lieux

### Architecture (bonne)

Trois canaux, avec un critère de répartition explicite et défendable
(`NotificationService`, javadoc de classe) :

| Canal | Rôle | Support |
|---|---|---|
| **In-app** | toujours actif, non désactivable, historique | table `notifications` + badge SSE (`NotificationStreamService`) |
| **Push** | le quotidien | WebPush/VAPID (`PushNotificationService`), no-op sans clés |
| **E-mail** | ce qui ne peut pas passer ailleurs (compte, lien à usage unique) + digest | Resend |

Le basculement de la routine e-mail → push est documenté et chiffré (≈11 000 e-mails/mois → few
hundred), avec la bonne raison : un quota Resend épuisé emporte les réinitialisations de mot de
passe. C'est le bon arbitrage.

### Les 8 notifications applicatives existantes

| Code | Destinataire | Canaux | Déclencheur | Pertinence |
|---|---|---|---|---|
| `WORKOUT_PLANNED` | athlète | in-app + push | création d'une séance de course | ⚠️ pertinent à l'unité, toxique en lot |
| `COACH_COMMENT` | athlète | in-app + push | commentaire du coach sur une séance | ✅ |
| `WORKOUT_REMINDER` | athlète | in-app + push, **e-mail en repli** | cron 18 h, J-1 | ✅ le repli est bien pensé |
| *(débriefing)* | athlète | **push seul**, actions rapides RPE 3/6/8 | cron horaire, heure habituelle + 2 h | ✅ meilleure idée du lot, mais fragile |
| `ATHLETE_FEEDBACK` | coach référent | in-app + push | retour d'un athlète | ⚠️ libellé cassé (cf. §2) |
| `COACH_ALERTS` | coach référent | in-app + push + e-mail | cron 7 h, digest, dédup 7 j | ✅ le seul flux vraiment maîtrisé |
| `ATHLETE_UNAVAILABILITY` | coach référent | in-app + push + e-mail | déclaration d'indisponibilité | ✅ |
| `HEALTH_CONSENT_WITHDRAWN` | coach référent | in-app + push | retrait du consentement santé | ✅ signal indispensable |

Plus quatre e-mails transactionnels (vérification, mot de passe, invitation coach, invitation
athlète), correctement hors du périmètre « notification ».

### Séparation athlète / coach : correcte

Le routage vers le **coach référent** avec repli sur le head coach (`referentCoach`) évite qu'en
multi-coach une notification parte au mauvais destinataire. L'invariant « aucune donnée de santé
dans le corps » est tenu partout, y compris dans le digest, qui ne transmet qu'une catégorie
générique. Rien à redire ici — c'est la partie la plus mûre du dispositif.

### Qualité technique du transport : au-dessus de la moyenne

Envoi `afterCommit`, exécuteur dédié à file bornée, timeouts explicites (le client HTTP interne
de la bibliothèque n'en a aucun), suppression des abonnements sur 404/410 — ce qui rend
`canReach()` honnête et donc le repli e-mail fiable —, ré-enregistrement de l'abonnement au
démarrage côté navigateur, désabonnement à la déconnexion (téléphone partagé), reconnexion SSE
avec recul exponentiel et jeton rafraîchi. Ces points ont visiblement déjà été audités.

---

## 2. Améliorations prioritaires

### P0 — Regrouper les séances générées en lot (anti-spam)

`TrainingPlanService.assign()` boucle sur tous les items du plan ; chaque itération passe par
`WorkoutTemplateService.apply` → `WorkoutService.create` → `notifyWorkoutPlanned`.
`docs`/`front` n'y peuvent rien : **une attribution de plan = une notification par séance**. Et
comme la méthode est idempotente-régénérante (elle supprime les séances `PLANNED` du plan puis les
recrée), chaque réattribution rejoue la salve entière.

**Correctif** : sortir la notification de `create()` pour la remonter d'un cran. Un seul message
par lot : *« Ton plan « Prépa 10 km » est en ligne — 48 séances jusqu'au 12 novembre »*, lien vers
le calendrier. Concrètement, un paramètre `notify` sur `create(...)` (faux depuis `apply`), et un
`notifyPlanAssigned(plan, athlete, count, endDate)` appelé une fois en fin d'`assign()`. Même
raisonnement pour `applyToGroup` côté journal coach.

### P0 — Notifier les messages

`MessageService.persist()` n'appelle rien. Ajouter un `NEW_MESSAGE` in-app + push dans les deux
sens (coach → athlète, athlète → coach référent), corps **sans le contenu du message** (il peut
parler de santé) : *« Nouveau message de Marie Dupont »*, lien vers le fil. Avec un anti-rafale
simple : pas de second push si un message non lu du même fil date de moins de 15 minutes.

### P0 — Réparer les libellés cassés

`notifyAthleteFeedback` concatène `workout.getStatus()`, c'est-à-dire le nom brut de l'enum. Le
coach reçoit littéralement **« Séance mise à jour — Marie COMPLETED »**. Il manque un
`label()` sur `WorkoutStatus` (le modèle existe déjà : `reasonLabel()` pour les indisponibilités).

Dans la foulée : `WORKOUT_PLANNED` affiche une date ISO brute (`2026-08-19`) là où « mercredi
19 août » se lit d'un coup d'œil.

### P1 — Notifier aussi les séances de renforcement

`StrengthScheduleService.schedule()` ne notifie pas, alors que le rappel de débriefing, lui, relance
bien sur les séances de force. L'athlète est donc relancé sur une séance qu'on ne lui a jamais
annoncée. Asymétrie à supprimer.

### P1 — Heures de silence et valeur par défaut du débrief

`usualSessionTime` vaut **18:00 par défaut pour tout le monde** : tout athlète qui n'a jamais
touché ce réglage reçoit « Ta séance est finie ? » à 20 h, y compris s'il n'a pas de séance à
cette heure-là. Deux garde-fous : ne relancer qu'après une heure *confirmée* par l'athlète (ou
déduite de son historique de retours), et une fenêtre de silence globale 22 h – 7 h qui décale au
lendemain plutôt que d'annuler.

### P1 — Préférences par catégorie, pas par canal

Aujourd'hui : un interrupteur push, un interrupteur e-mail. L'athlète qu'agace le rappel J-1 n'a
d'autre choix que de tout couper — y compris le retour de son coach, qui est la valeur du produit.
`Notification.type` existe déjà : il suffit d'une table de préférences (user, type, canal) et de
quatre cases côté athlète (séances, retours du coach, rappels, messages), trois côté coach.

### P2 — Dette technique à solder

- **Aucune purge de la table `notifications`.** Elle croît sans limite ; l'index est
  `(user_id, created_at)` alors que le compteur filtre sur `read_at IS NULL`. Ajouter un index
  partiel et une purge des notifications lues de plus de 90 jours.
- **Payload JSON construit à la main.** `json()` n'échappe que `\` et `"` : un titre de séance
  contenant un retour à la ligne produit un JSON invalide, et le service worker n'affiche
  simplement rien. Passer par Jackson.
- **Pas de `tag` dans le payload push** → les notifications s'empilent sur l'écran de verrouillage
  au lieu de se remplacer. Un `tag` par catégorie suffirait, même après le correctif P0.
- **Schedulers mono-instance sans verrou.** Quatre crons documentés « MVP, passer à ShedLock en cas
  de scale-out ». Deux instances = toutes les notifications planifiées en double. À traiter avant
  d'ouvrir la bêta largement.
- **Débriefing sans trace ni idempotence.** Push seul, aucun `record()` : téléphone en silencieux
  ou hors ligne, l'invitation disparaît. Et sans journal d'envoi, un redéploiement à l'heure pile
  peut la rejouer.
- **Aucune métrique.** Rien ne compte les envois, les échecs par endpoint, ni les clics. On ne
  saura pas que le canal est mort avant qu'un utilisateur le signale.

### P2 — Placement dans l'interface

Côté athlète, la cloche n'est présente que sur « Aujourd'hui » (`today.component.html`), et le
bouton d'activation des push est enfoui dans le profil. Le bon moment pour demander l'autorisation
n'est ni l'onboarding ni un écran de réglages : c'est juste après avoir reçu sa première séance
(« veux-tu être prévenu la prochaine fois ? »).

---

## 3. Notifications manquantes recommandées

### Athlète

| Notification | Quand | Pourquoi |
|---|---|---|
| **Plan attribué** *(remplace la salve)* | attribution / regénération | annonce le bloc, pas les 48 séances |
| **Nouveau message du coach** | à l'envoi | le manque le plus criant |
| **Séance déplacée / annulée** | `reschedule`, suppression | l'athlète peut se déplacer pour rien ; aujourd'hui aucun signal |
| **Course J-7 puis J-1** | objectif de course approchant | `RaceObjectiveService` existe et n'émet rien ; c'est le moment le plus chargé émotionnellement de la saison |
| **Synchro montre interrompue** | échec Strava répété | `StravaSyncScheduler` se contente d'un `log.warn` : l'athlète croit ses activités remontées alors qu'elles ne le sont plus |
| **Jalon franchi** | record perso, série de 4 semaines complètes | le seul message positif du dispositif (cf. §4) |

### Coach

| Notification | Quand | Pourquoi |
|---|---|---|
| **Nouveau message d'un athlète** | à l'envoi | symétrique |
| **Invitation acceptée** | activation d'un compte athlète | le coach relance aujourd'hui à l'aveugle |
| **Séance déclarée non faite avec motif** | statut `MISSED` + motif | plus actionnable que le digest à J+1 ; ne pas attendre 7 h |
| **Douleur en hausse** | franchissement de seuil | déjà calculé comme alerte, mais noyé dans le digest quotidien — une douleur qui monte mérite le temps réel |
| **Récapitulatif de fin de semaine** | dimanche soir | taux de complétion et retours manquants ; le pendant hebdomadaire du digest quotidien |
| **Athlète inactif depuis 14 jours** | silence prolongé | `SILENCE` existe côté alerte, mais le décrochage se joue avant qu'il soit visible dans le tableau de bord |

---

## 4. Engagement et rétention

**Le dispositif actuel ne parle que d'obligations.** Sur les huit notifications, huit annoncent un
travail à faire ou un problème à traiter. Aucune ne dit à un athlète qu'il progresse. Un canal qui
ne transporte que des devoirs finit par être coupé — c'est le mécanisme de désabonnement le plus
banal du mobile.

Quatre leviers, par ordre de rapport valeur / effort :

1. **Le message positif.** Un push par semaine maximum, déclenché par un fait réel :
   record sur une distance, quatrième semaine consécutive complète, premier bloc terminé. Le
   composant `celebration-overlay` existe déjà côté front — il n'est simplement jamais atteint
   depuis l'extérieur de l'app.

2. **Généraliser les actions rapides.** Le débriefing avec RPE en deux taps est la meilleure idée
   du produit ; elle n'est utilisée qu'une fois. Le même schéma vaut pour le coach (« vu » sur un
   retour, directement depuis la notification) et pour l'athlète (« j'y serai » / « je décale » sur
   le rappel J-1 — ce qui alimenterait au passage les indisponibilités).

3. **La boucle de réciprocité.** Un athlète remplit ses retours tant qu'il constate que quelqu'un
   les lit. `COACH_COMMENT` existe et c'est la bonne notification ; ce qui manque, c'est la
   pression douce côté coach — le récapitulatif du dimanche soir listant les retours restés sans
   réponse ferme la boucle.

4. **Une demande d'autorisation contextuelle.** Demander le droit de notifier au moment où l'on
   a quelque chose d'utile à annoncer (« ta première séance est en ligne — veux-tu être prévenu ? »)
   plutôt que dans un écran de réglages. C'est le seul changement de cette liste qui agit sur le
   taux d'opt-in, dont tout le reste dépend.

**Un mot sur la fréquence.** Après les correctifs P0, le budget réaliste par athlète est de l'ordre
de 3 à 5 notifications par semaine (1 plan, 2-3 rappels, quelques retours), et de 1 à 2 par jour
pour un coach de 25 athlètes (digest + faits nouveaux). C'est tenable. Sans le correctif
d'attribution de plan, un seul geste du coach dépasse à lui seul le budget mensuel de l'athlète.

---

## Récapitulatif des correctifs, par ordre

| # | Correctif | Fichier principal | Effort |
|---|---|---|---|
| 1 | Une notification par lot d'attribution de plan | `TrainingPlanService`, `WorkoutService` | M |
| 2 | Notifier les messages (2 sens, anti-rafale) | `MessageService` | S |
| 3 | Libellés : statut de séance, date lisible | `WorkoutStatus`, `NotificationService` | XS |
| 4 | Notifier les séances de renforcement | `StrengthScheduleService` | XS |
| 5 | Heures de silence + heure de débrief confirmée | `SessionDebriefScheduler`, `User` | S |
| 6 | Séance déplacée / annulée | `WorkoutService.reschedule` | S |
| 7 | Préférences par catégorie | migration + `UserNotificationService` | M |
| 8 | Purge + index partiel `read_at` | migration + scheduler | S |
| 9 | Payload JSON via Jackson, `tag` de collapse | `PushNotificationService` | XS |
| 10 | ShedLock sur les quatre crons | `scheduler/` | S |
