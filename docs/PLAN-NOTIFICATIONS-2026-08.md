# Plan d'évolution — Notifications (août 2026)

> **Source** : `docs/AUDIT-NOTIFICATIONS-2026-08.md`.
>
> **Principe de séquencement** : on arrête d'abord ce qui détruit de la valeur (le spam), on
> comble ensuite ce que l'utilisateur attend et qui n'arrive pas, on rend le canal réglable, et
> seulement à la fin on cherche l'engagement. Ajouter des notifications avant d'avoir corrigé
> l'attribution de plan reviendrait à verser de l'eau dans un seau percé : un athlète qui a coupé
> les push ne recevra jamais le message d'encouragement du lot 4.
>
> **Convention de livraison** : chaque nouveau type de notification arrive derrière une propriété
> `app.notifications.<type>.enabled` (le produit fait déjà ça avec `app.debrief.enabled`), pour
> pouvoir couper un flux en production sans redéployer. Prochaine migration disponible : **073**.

---

## Vue d'ensemble

| Lot | Thème | Effort | Sortie attendue |
|---|---|---:|---|
| **0** ✅ | Socle + anti-spam | ~3 j | Le canal cesse de se saborder — **livré** |
| **1** | Les manques criants | ~4 j | Ce que l'utilisateur attend arrive enfin |
| **2** | Hygiène et réglage | ~5 j | Le canal devient acceptable dans la durée |
| **3** | Robustesse d'exploitation | ~3 j | Le canal survit au scale-out et se supervise |
| **4** | Engagement | ~5 j | Le canal donne envie de rester abonné |

Total ≈ 20 jours-homme. Les lots 0 à 2 sont un prérequis de bêta ouverte ; les lots 3 et 4 peuvent
suivre.

---

## Lot 0 — Socle et anti-spam ✅ *livré*

> **Écarts constatés à l'implémentation**, tous dans le sens d'un périmètre un peu plus large :
>
> - La méthode d'attribution s'appelle `TrainingPlanService.applyToAthlete`, et non `assign`.
> - Le lien « calendrier à la date annoncée » supposait que le calendrier athlète sache lire une
>   date : il ne le savait pas (`anchor` était toujours initialisé à aujourd'hui). Le correctif
>   emporte donc une petite évolution front, `?date=AAAA-MM-JJ`, sans quoi 0.3 n'aurait rien
>   corrigé du tout.
> - `tag` seul aurait **dégradé** le comportement : sur Chrome, une notification qui en remplace
>   une autre arrive silencieusement. `renotify` l'accompagne obligatoirement.
> - Trois déclencheurs avaient un corps différent entre l'in-app et le push (retour d'athlète,
>   digest, indisponibilité). Le point d'entrée unique conserve cette distinction plutôt que de
>   l'aplatir : le refactoring reste sans changement de comportement.
> - Vérifié au passage : la duplication de semaine et la génération de mésocycle écrivent
>   directement en base, sans passer par `create`. Elles ne spammaient donc pas — mais elles
>   n'annoncent rien non plus, ce qui est un manque à traiter au lot 1.

### 0.1 Point d'entrée unique de notification

**Problème.** Le motif `record(...) + if (user.isNotifyPushEnabled()) pushService.sendToUser(...)`
est recopié six fois dans `NotificationService`. Toute règle transversale à venir — heures de
silence, préférence par catégorie, anti-rafale, métrique — devrait être ajoutée six fois, et
oubliée à la septième.

**Correctif.** Une méthode privée unique :

```java
private void notify(User target, String type, String title, String body, String link,
                    List<QuickAction> actions)
```

qui enregistre l'in-app, applique les préférences, puis pousse. Les huit déclencheurs existants
deviennent des appels d'une ligne. **Aucun changement de comportement** — c'est un refactoring pur,
et c'est ce qui rend les lots 2 à 4 tenables.

- Fichier : `NotificationService`
- Recette : `NotificationServiceTest` et `NotificationCenterTest` passent sans modification.
- Effort : 0,5 j — Risque : nul

### 0.2 Une notification par attribution de plan, pas par séance

**Problème.** `TrainingPlanService.assign()` boucle sur les items du plan ; chaque itération
descend jusqu'à `WorkoutService.create` → `notifyWorkoutPlanned`. Un plan de 12 semaines à
4 séances produit ~48 notifications in-app et ~48 push en une salve. La méthode étant
idempotente-régénérante (elle supprime les séances `PLANNED` du plan avant de les recréer), chaque
réattribution rejoue la salve entière.

**Correctif.**

1. `WorkoutService.create(...)` reçoit un paramètre `boolean notifyAthlete` (surcharge existante
   conservée à `true`, aucun appelant actuel à toucher).
2. `WorkoutTemplateService.apply(..., planId, multiplier)` — la variante utilisée par
   l'attribution — le passe à `false`. La variante appelée depuis le contrôleur (pose d'une séance
   à l'unité) reste à `true`.
3. `TrainingPlanService.assign()` émet **un seul** `notifyPlanAssigned(athlete, plan, created,
   startDate, endDate)` en fin de boucle :
   *« Ton plan « Prépa 10 km » est en ligne — 48 séances jusqu'au 12 novembre »*, lien
   `/athlete/calendar`.

> **Variante envisagée puis écartée** : un tampon de coalescence par transaction, vidé en
> `afterCommit`, qui regrouperait automatiquement toute salve. Plus élégant et couvrant les futurs
> chemins en lot (imports, duplication de semaine), mais trois fois plus coûteux et plus difficile
> à tester. À reprendre si un deuxième chemin de création en lot apparaît.

- Fichiers : `WorkoutService`, `WorkoutTemplateService`, `TrainingPlanService`, `NotificationService`
- Recette : attribuer un plan de 12 semaines à un athlète produit **1** ligne en base
  `notifications` et **1** push. Poser une séance à l'unité en produit toujours 1.
- Effort : 1 j — Risque : faible (le chemin unitaire est couvert par les tests existants)

### 0.3 Libellés lisibles par un humain

Trois défauts visibles par l'utilisateur, tous dans des chaînes de caractères :

| Défaut | Aujourd'hui | Correctif |
|---|---|---|
| Statut brut de l'enum | « Séance mise à jour — Marie **COMPLETED** » | `WorkoutStatus.label()` (le modèle existe : `reasonLabel()` pour les indisponibilités) |
| Date ISO | « Sortie longue — **2026-08-19** » | « Sortie longue — mercredi 19 août » |
| Lien faux | `WORKOUT_PLANNED` pointe sur `/athlete/today` | `/athlete/calendar?date=…` : une séance annoncée pour dans trois semaines n'est pas sur l'écran « Aujourd'hui » |

- Fichiers : `WorkoutStatus`, `NotificationService`
- Effort : 0,5 j — Risque : nul

### 0.4 Fiabilité du payload push

- **Échappement JSON.** `PushNotificationService.json()` n'échappe que `\` et `"`. Un titre de
  séance saisi par le coach avec un retour à la ligne produit un JSON invalide : le service worker
  n'affiche **rien**, sans erreur nulle part. → sérialisation par Jackson.
- **`tag` de collapse.** Sans `tag`, les notifications s'empilent sur l'écran de verrouillage au
  lieu de se remplacer. Un `tag` par catégorie (`workout`, `feedback`, `message`…) suffit.

- Fichier : `PushNotificationService`
- Recette : un titre contenant `"`, `\n` et une apostrophe typographique s'affiche correctement.
- Effort : 0,5 j — Risque : nul

---

## Lot 1 — Les manques criants

### 1.1 Notifier les messages *(le manque n° 1)*

`MessageService` n'appelle jamais `NotificationService`. Il diffuse en SSE, donc la messagerie
fonctionne *si l'application est déjà ouverte* — exactement le cas où une notification est inutile.

**Correctif.** Dans `persist(...)` **et** `persistWithAttachment(...)` (deux chemins, ne pas en
oublier un) : un type `NEW_MESSAGE` in-app + push, dans les deux sens.

- Coach → athlète : destinataire = compte de l'athlète.
- Athlète → coach : destinataire = coach référent (`referentCoach`), pas tout le club.
- **Le corps ne reprend jamais le contenu du message** : il peut parler de santé, et l'invariant
  du produit l'interdit. → *« Nouveau message de Marie Dupont »*, lien vers le fil.
- **Anti-rafale** : pas de second push si un `NEW_MESSAGE` non lu du même fil date de moins de
  15 minutes. Sans ça, une conversation de dix messages en fait dix.

- Fichiers : `MessageService`, `NotificationService`, `NotificationRepository`
- Effort : 1,5 j — Risque : faible

### 1.2 Séance déplacée ou annulée

`WorkoutService.reschedule()` et la suppression n'émettent rien. Un athlète peut se déplacer pour
une séance annulée la veille. C'est le type de défaut qui coûte la confiance d'un coup.

- Émettre `WORKOUT_RESCHEDULED` / `WORKOUT_CANCELLED` **uniquement** si la séance est à venir et
  que le déplacement dépasse la journée (déplacer une séance de lundi à mardi le lundi matin, oui ;
  ranger le calendrier trois semaines à l'avance, non).
- Ne pas émettre pendant une régénération de plan (le lot 0.2 supprime puis recrée : c'est
  précisément le cas à ne pas notifier — même drapeau `notifyAthlete`).

- Fichiers : `WorkoutService`, `NotificationService`
- Effort : 1 j — Risque : moyen — c'est le point où le lot 0.2 doit être en place, sinon toute
  attribution de plan produirait en plus une salve d'annulations.

### 1.3 Séances de renforcement annoncées

`StrengthScheduleService.schedule(...)` ne notifie pas, alors que `SessionDebriefScheduler` relance
bien sur les séances de force. L'athlète est donc relancé sur une séance qu'on ne lui a jamais
annoncée. Aligner sur `WORKOUT_PLANNED`, avec le même drapeau anti-lot pour la variante `planId`.

- Fichier : `StrengthScheduleService`
- Effort : 0,5 j — Risque : nul

### 1.4 Invitation acceptée

Le coach relance aujourd'hui à l'aveugle : rien ne lui dit qu'un athlète a activé son compte.
`ATHLETE_JOINED` in-app + push au coach référent (ou à l'invitant) à l'activation.

- Fichiers : `AthleteService` / `InvitationService`, `NotificationService`
- Effort : 0,5 j — Risque : nul

---

## Lot 2 — Hygiène et réglage du canal

### 2.1 Heures de silence

`usualSessionTime` vaut **18:00 par défaut pour tout le monde** : tout athlète qui n'a jamais
touché ce réglage reçoit « Ta séance est finie ? » à 20 h, qu'il se soit entraîné ou non.

1. Fenêtre de silence globale **22 h → 7 h** : une notification produite dans la fenêtre est
   décalée au matin, pas annulée (les transactionnels e-mail n'y sont pas soumis).
2. Ne relancer au débriefing qu'à partir d'une heure **confirmée** par l'athlète — ou déduite de
   son historique de retours — plutôt que de la valeur par défaut.

- Fichiers : `SessionDebriefScheduler`, `User`, `NotificationService` (via 0.1), migration 073
- Effort : 1,5 j — Risque : moyen (touche le seul flux réellement engageant du produit ; à mesurer
  avant/après sur le taux de retours remplis)

### 2.2 Préférences par catégorie

Aujourd'hui : un interrupteur push, un interrupteur e-mail. L'athlète qu'agace le rappel J-1 n'a
d'autre choix que de tout couper — y compris le retour de son coach, qui est la valeur du produit.
`Notification.type` existe déjà : il ne manque que la table de préférences.

- Migration 073/074 : `notification_preferences (user_id, type, channel, enabled)`, absence = actif.
- Front : quatre cases côté athlète (séances, retours du coach, rappels, messages), trois côté coach
  (retours, alertes, messages) — dans `settings/notifications.component.ts`, qui est déjà la
  destination du lien `List-Unsubscribe` des e-mails.

- Effort : 2 j — Risque : faible

### 2.3 Purge et index

- Aucun élagage de la table `notifications` : elle croît sans limite.
- L'index est `(user_id, created_at)` alors que le compteur de non-lues filtre `read_at IS NULL`
  → index partiel `(user_id) WHERE read_at IS NULL`.
- Scheduler de purge des notifications **lues** de plus de 90 jours (les non-lues restent).

- Effort : 1 j — Risque : nul

### 2.4 Trace du débriefing

Le rappel de débriefing part en push seul, sans `record()` : téléphone en silencieux ou hors
ligne, l'invitation disparaît sans laisser de trace. Et sans journal d'envoi, un redéploiement à
l'heure pile peut le rejouer. → `record()` systématique + une ligne d'idempotence
(athlète, date) sur le modèle d'`AlertDigestLog`, qui existe déjà et fonctionne bien.

- Fichiers : `SessionDebriefScheduler`, `NotificationService`, migration
- Effort : 0,5 j — Risque : nul

---

## Lot 3 — Robustesse d'exploitation

### 3.1 ShedLock sur les quatre crons

`ReminderScheduler`, `AlertDigestScheduler`, `SessionDebriefScheduler` et `StravaSyncScheduler`
portent tous le même commentaire : « mono-instance pour le MVP ». La dépendance **n'est pas dans
le `pom.xml`**. Deux instances = toutes les notifications planifiées en double, chez tous les
utilisateurs, le même matin. C'est un bloquant de mise à l'échelle, pas une dette de confort.

- Dépendance `shedlock-spring` + `shedlock-provider-jdbc-template`, table `shedlock`, `@SchedulerLock`
- Effort : 1 j — Risque : faible

### 3.2 Métriques et supervision

Rien ne compte les envois, les échecs par endpoint, ni les clics. On ne saura pas que le canal est
mort avant qu'un utilisateur le signale — et l'expérience du produit montre que ce genre de panne
reste invisible des mois (le rappel J-1 en `readOnly`, l'abonnement caduc jamais purgé).

- Compteurs Micrometer : envois par type, échecs, 404/410, taille de la file de remise.
- Alerte si le taux d'échec dépasse un seuil sur une heure.

- Effort : 1 j — Risque : nul

### 3.3 Synchro montre interrompue

`StravaSyncScheduler` se contente d'un `log.warn` sur échec. L'athlète croit ses activités
remontées alors qu'elles ne le sont plus, et le coach analyse un vide qu'il prend pour de
l'inactivité. → notifier l'athlète après N échecs consécutifs, une seule fois par reconnexion.

- Effort : 1 j — Risque : faible

---

## Lot 4 — Engagement et rétention

Sur les huit notifications actuelles, huit annoncent un devoir ou un problème. Un canal qui ne
transporte que des obligations finit par être coupé.

| # | Chantier | Détail | Effort |
|---|---|---|---:|
| 4.1 | **Demande d'autorisation contextuelle** | Proposer l'activation des push au moment où il y a quelque chose d'utile à annoncer (« ta première séance est en ligne — veux-tu être prévenu ? »), pas dans un écran de réglages. C'est le seul levier qui agit sur le **taux d'opt-in**, dont tout le reste dépend. Aujourd'hui le bouton est enfoui dans le profil athlète. | 1 j |
| 4.2 | **Le message positif** | Un push par semaine **maximum**, adossé à un fait réel : record sur une distance, 4ᵉ semaine consécutive complète, bloc terminé. Le composant `celebration-overlay` existe déjà côté front et n'est jamais atteint depuis l'extérieur de l'app. | 1,5 j |
| 4.3 | **Actions rapides généralisées** | Le débriefing RPE en deux taps est la meilleure idée du produit et n'est utilisée qu'une fois. Étendre : « vu » sur un retour côté coach ; « j'y serai » / « je décale » sur le rappel J-1 — ce second cas alimenterait au passage les indisponibilités, aujourd'hui purement déclaratives. | 1,5 j |
| 4.4 | **Récapitulatif du dimanche soir** | Côté coach : taux de complétion de la semaine et retours restés sans réponse. Ferme la boucle de réciprocité — un athlète remplit ses retours tant qu'il constate que quelqu'un les lit. | 1 j |
| 4.5 | **Course J-7 / J-1** | `RaceObjectiveService` existe et n'émet rien, alors que c'est le moment le plus chargé de la saison. | 0,5 j |
| 4.6 | **Cloche athlète sur tous les écrans** | Elle n'est présente que sur « Aujourd'hui » (`today.component.html`). | 0,5 j |

---

## Budget cible et garde-fou produit

Après le lot 0, le budget réaliste par utilisateur :

| Rôle | Cible | Composition |
|---|---|---|
| Athlète | **3 à 5 / semaine** | 1 plan attribué, 2-3 rappels, retours du coach, messages |
| Coach (25 athlètes) | **1 à 2 / jour** | digest de 7 h + faits nouveaux (message, indisponibilité, consentement) |

**Règle à inscrire dans la javadoc de `NotificationService`** : toute nouvelle notification doit
répondre à *« si l'utilisateur la voit trois heures plus tard, a-t-il perdu quelque chose ? »*. Si
la réponse est non, sa place est dans le centre de notifications ou dans un digest, pas en push.
Le digest d'alertes du coach — regroupement + dédup 7 jours — est le patron à copier ; il est déjà
le seul flux réellement maîtrisé du produit.

---

## Ordre d'exécution recommandé

```
Lot 0 ──────────► Lot 1 ──────────► Lot 2 ──────────► Lot 4
(anti-spam)       (manques)         (réglage)         (engagement)
   │                                     │
   └──────────────► Lot 3 ───────────────┘
                    (exploitation, parallélisable)
```

Le lot 3 ne dépend d'aucun autre et peut être mené en parallèle par une deuxième main. Les lots 0
à 2 conditionnent la bêta ouverte : ouvrir plus largement avec l'attribution de plan en l'état
ferait couper les notifications à une part des athlètes dès leur première semaine — et un opt-out
push ne se récupère pas.
