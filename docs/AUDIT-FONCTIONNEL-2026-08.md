# Audit fonctionnel — DARI Lab Training (août 2026)

> **Question posée** : un vrai coach de course à pied / préparation physique et son athlète
> peuvent-ils utiliser l'application au quotidien sans se sentir bloqués, perdus, ou trahis par
> une prescription incohérente ?
>
> **Méthode** : trois mésocycles de 4 à 6 semaines déroulés dans le code — responsable de club
> (25 athlètes), coach assistant, athlète en PWA. Pas de happy path : athlète blessé en cours de
> cycle, séance écourtée, retour incohérent, test 1RM qui contredit le profil, semaine saturée par
> les déplacements. Aucun constat technique ici — uniquement ce qui change le geste métier.

---

## Verdict en trois lignes

**Une seule ligne de code casse la crédibilité de toute la prescription côté athlète** : sans test
lactate — c'est-à-dire pour la quasi-totalité d'une bêta — chaque bloc de chaque séance affiche
« RPE 2–4 », y compris un 10×400 m que le coach a prescrit à RPE 8.

**L'application ne sait pas qu'un athlète est blessé, même quand c'est elle qui l'a enregistré.**
L'indisponibilité est purement déclarative : les séances restent planifiées, les alertes
« séances manquées » et « athlète silencieux » se déclenchent, et le digest les renvoie tous les
matins. Le coach de 25 athlètes reçoit chaque jour un rappel de ce qu'il sait déjà.

**La charge d'entraînement — le chiffre de sécurité — est calculée sur une durée qui n'a pas eu
lieu.** Une séance abandonnée au tiers compte pour 100 %, et fait monter l'ACWR d'un athlète qui
s'est justement entraîné moins.

---

## 🔴 BLOQUANT

### B1 — L'athlète lit « RPE 2–4 » sur toutes ses séances tant qu'il n'a pas fait de test lactate

**Le mécanisme.** `SessionCalculatorEngine.calculate` dérive le RPE affiché du domaine d'intensité
de l'allure moyenne du bloc :

```java
IntensityDomain domain = domainEngine.classify(meanSpeedMs, null, ctx.lt1Ms(), ctx.lt2Ms(), ...);
```
*(`SessionCalculatorEngine.java:88-90`)*

Dans `IntensityDomainEngine.classify` :
- la branche physiologique (`:29`) exige `lt1Ms` **et** `lt2Ms` non nuls ;
- la branche de repli FC (`:38`) exige `hrBpm != null` — or l'appelant passe `null` **en dur** ;
- reste le défaut conservatif `DOMAIN_1` (`:47`) → `rpeBand` → **RPE 2–4** (`SessionCalculatorEngine.java:246`).

`Athlete.lt1Ms` / `lt2Ms` ne sont jamais dérivés du VDOT : ils viennent d'un test lactate ou d'une
saisie manuelle (`AthletePhysioService.updateProfile:66-68`). Un athlète qui a seulement renseigné
un chrono sur 10 km a donc `lt1Ms = null`.

**Ce que ça donne sur le terrain.** Le coach construit « échauffement 20' + 10×400 m à 105 % VMA +
retour au calme », met RPE 8 sur le bloc principal. L'athlète ouvre sa séance du jour et lit :

> Allure **3:45–3:52 /km** · *estimée* · **RPE 2–4**

L'allure est juste et honnêtement marquée « estimée ». Le RPE est faux, et rien ne le signale. Le
composant athlète n'affiche **que** le RPE calculé, jamais celui saisi par le coach
(`course-prescription-view.component.ts:56-57` — il n'existe aucune branche sur `e.block.rpe`).

Un athlète non expert calibre son effort sur le RPE : c'est le seul repère qui ne demande ni
montre ni terrain plat. Lui annoncer « effort 2 à 4 sur 10 » sur une séance de VMA, c'est soit
une séance ratée, soit un athlète qui cesse de croire les chiffres. Et le coach ne le voit pas :
son propre écran lui montre le RPE qu'il a saisi (`workout-detail.component.ts:149-163` utilise
bien `e.block.rpe`).

**Qui est touché** : tout athlète sans test lactate — dans une bêta ouverte, la quasi-totalité.
**Effort** : ~1 h. Trois options, la première suffit : dériver le domaine du référentiel et du %
prescrits (un bloc à 105 % VMA est en domaine 3 par construction) plutôt que de repasser par une
classification de vitesse qui a besoin de seuils mesurés.

### B2 — Une pastille de forme rouge ne périme jamais

`AthleteFeedbackService.lastFeedback:48-75` prend le dernier retour connu, **sans aucune borne de
fraîcheur**, et `CoachDashboardService.formDashboard:183-186` le classe tel quel.

**Sur le terrain.** Un athlète déclare fatigue 9 / douleur 6 fin octobre après une compétition,
puis part six semaines sans rien saisir. Le 15 janvier, il est toujours 🔴 sur le tableau de bord,
au même titre qu'un athlète qui a déclaré une douleur ce matin. La date du dernier retour est bien
renvoyée par l'API, mais le tableau de bord ne l'affiche nulle part.

C'est exactement une donnée de forme qui induit en erreur sur l'état physique réel : le coach qui
trie « à surveiller » par criticité (`dashboard.component.ts:59-68`) voit remonter en tête un
athlète dont l'information a six semaines, et redescendre celui qui a signalé une gêne hier.

**Effort** : 0,5 j — fenêtre de fraîcheur (7 à 10 jours), au-delà l'athlète bascule en « pas de
signal récent » plutôt qu'en vert ou en rouge.

### B3 — Un athlète blessé devient l'athlète le plus alarmant du club

L'indisponibilité n'écrit que des dates et un motif (`UnavailabilityService.apply:97-105`). Aucun
autre service ne la consulte : ni le calendrier, ni la charge, ni les alertes. Les séances de la
période restent `PLANNED`.

**Le mésocycle réel.** Semaine 2, l'athlète se blesse et déclare « blessure, 3 semaines » depuis
son portail. Le coach référent est notifié. Puis :

| Jour | Ce que le coach reçoit |
|---|---|
| J+3 | 🔴 « 3 séances manquées » (`CoachDashboardService.alerts:276-278`) |
| J+10 | 🟠 « Athlète silencieux — dernier retour il y a 10 jours » (`:290`) |
| J+14 | 🟠 « Charge en baisse — ACWR 0,4 (désentraînement possible) » (`:256`) |
| tous les matins 7 h | le même digest, intégralement (`AlertDigestScheduler.digestForClub:88-105`) |

Le digest n'a **aucun état** : il recalcule et renvoie tout, chaque jour, sans accusé de réception
ni mise en sommeil. Sur 25 athlètes avec deux blessés et un athlète en vacances, c'est une
dizaine d'alertes quotidiennes portant sur des faits que le coach a lui-même saisis. La réponse à
« les alertes vont-elles noyer le signal utile ? » est oui, et pas à cause des seuils — à cause de
l'absence de mémoire.

Corollaire : rien n'empêche non plus de planifier une séance en pleine fenêtre d'indisponibilité,
et il n'existe aucune suppression en masse (`WorkoutController` n'expose que
`DELETE /{workoutId}`). Vider trois semaines de programme après une blessure, c'est une
quinzaine de suppressions une par une.

**Effort** : ~1 j — exclure les fenêtres d'indisponibilité des alertes MISSED / SILENCE /
ACWR_LOW, mémoriser ce qui a déjà été envoyé, et offrir une action « replanifier / vider la
période » depuis l'indisponibilité.

### B4 — L'ACWR est calculé sur la durée prescrite, jamais sur la durée réalisée

`AthleteLoadService.collectSessions:110-125` :

```java
double load = w.getRpe() * (durationS / 60.0);
```

où `durationS` vient de `targetDurationS` — la cible — ou de la somme des étapes prescrites
(`durationSeconds:147-160`). `Workout` ne porte **aucune** durée réalisée (`Workout.java:66` :
seul `targetDurationS` existe), et le statut n'est même pas consulté : il suffit qu'un RPE soit
présent pour que la séance compte à 100 %.

**Sur le terrain.** Sortie longue de 1 h 45 prescrite. L'athlète part, sent une gêne au mollet,
s'arrête à 40 minutes, note RPE 7 et coche « Partiellement ». La charge enregistrée est
**735 UA** au lieu de ~280. Deux séances écourtées dans la semaine et l'ACWR franchit 1,5 →
🔴 « Charge en forte hausse — risque de blessure » sur un athlète qui vient de s'entraîner deux
fois moins que prévu.

L'inverse est vrai aussi : une sortie longue rallongée d'une demi-heure ne se voit pas.

L'information manquante existe pourtant : l'activité rapprochée porte sa vraie durée
(`Activity.durationS`), et la feuille de ressenti l'affiche même à l'athlète
(`workout-feedback-sheet.component.ts:57-62`) — mais le moteur de charge ne la lit pas.

**Effort** : 0,5–1 j — prendre la durée de l'activité rapprochée quand elle existe, sinon demander
la durée réelle dans la feuille de ressenti quand l'athlète coche « Partiellement ».

### B5 — L'athlète n'a aucun moyen de dire « je n'ai pas fait la séance »

La feuille de ressenti n'offre que deux boutons (`workout-feedback-sheet.component.ts:76-78`) :

```html
<button ...>Séance réalisée</button>
<button ...>Partiellement</button>
```

`WorkoutStatus.MISSED` existe (`WorkoutStatus.java:8`) mais n'est atteignable que par le coach.
Le seul geste possible pour l'athlète est de **ne rien faire** — ce qui alimente ensuite l'alerte
« séances manquées » sans qu'il ait jamais pu dire pourquoi.

Trois situations parfaitement ordinaires n'ont donc pas de sortie propre : je pars en déplacement
professionnel, je suis malade trois jours, un imprévu familial. L'athlète voit la séance rester
dans son bandeau « retours en attente » pendant 7 jours (`today.component.html:66-83`) puis
disparaître, et le coach reçoit une alerte au lieu d'une information.

Et « Partiellement » ne demande ni durée, ni raison : le coach apprend qu'une séance a été
tronquée, jamais de combien ni pourquoi — alors que c'est précisément ce qui décide de l'ajustement
de la semaine suivante.

**Effort** : 0,5 j — troisième bouton « Pas faite » avec un motif court (imprévu / maladie /
fatigue / météo), et un champ durée sur « Partiellement ».

---

## 🟠 GÊNANT

### G1 — Le coach qui s'inscrit seul démarre avec des bibliothèques vides

Seuls les **zones** et les **types de métriques** sont provisionnés (`TrainingZoneSeedService`,
appelé paresseusement à la première lecture). Sont vides à l'inscription : bibliothèque
d'exercices de préparation physique, catégories de séance, modèles de séance course, éducatifs de
course. Le jeu de démonstration qui les remplit est `@Profile("dev")` (`DemoSeedService`) — donc
absent en production.

Un préparateur physique doit créer à la main chacun des 40 à 60 exercices de son répertoire (nom,
catégorie, groupe musculaire, matériel, progression, régression) **avant** de pouvoir prescrire sa
première séance de force. C'est plusieurs heures de saisie avant la première valeur perçue, sur un
produit qu'il teste. C'est le point le plus probable d'abandon d'un bêta-testeur non accompagné.

**Effort** : 1–2 j — un catalogue d'exercices et de catégories seedé comme les zones, dupliqué au
club à la création, éditable ensuite.

### G2 — Le check-in matinal ne remonte jamais au coach

L'athlète renseigne sommeil, fatigue et douleur chaque matin (`morning-check-in.component.ts`,
migration 059). Côté coach : **aucun endpoint, aucun écran**. Les seules routes sont
`GET`/`POST /me/checkin` (`AthletePortalController.java:137-152`).

Fatigue et douleur ne l'atteignent que compressées dans la pastille de forme, sans historique.
Le **sommeil n'arrive nulle part** : `LastFeedback` ne transporte que fatigue, douleur et date
(`AthleteFeedbackService:48-75`).

Un coach qui veut savoir « tu dors mal depuis quand ? » avant de décider d'un allègement doit le
demander par message. On demande à l'athlète une saisie quotidienne dont personne ne voit le
résultat — c'est le meilleur moyen qu'il arrête de la faire.

### G3 — Une semaine de décharge programmée déclenche systématiquement une alerte rouge

`ProgressionEngine.alerts:76-79` lève `CHARGE_DROP` (niveau HIGH) dès que
`currentCharge < previousCharge × 0,85`. Or les deux termes ne sont pas comparables :

- `currentCharge` = **maximum** de la séance (`ProgressionService:84`, `merge(..., Math::max)`) ;
- `previousCharge` = **la série la plus récemment enregistrée** de la séance précédente, quelle
  qu'elle soit (`ProgressionService:116-124`).

Deux conséquences concrètes. Un squat 4×5 à 100 kg suivi d'un drop-set à 70 kg laisse
`previousCharge = 70` — la comparaison suivante est faussée dans un sens. Et surtout : un cycle de
force assigné par l'application applique lui-même `−40 %` en semaine de décharge
(`StrengthCycleService.assign:80-81`). **Chaque semaine de décharge lève donc une alerte HIGH
« chute de charge » sur chaque exercice** — une alerte provoquée par la planification de l'outil.

### G4 — Les alertes de force n'arrivent jamais jusqu'au coach

`ProgressionService` n'est appelé par **aucun** autre service : uniquement par
`GET /pp/scheduled/{id}/progression`, séance par séance. Les alertes douleur / RPE ≥ 9,5 / RIR 0 /
chute de charge du §6.8 du cahier des charges existent en code, mais pour les voir sur 25 athlètes
faisant deux séances de force par semaine, il faut ouvrir une cinquantaine de séances à la main.
Le tableau de bord ne remonte que la douleur, et seulement celle du dernier retour.

### G5 — Une séance structurée est validée sur le seul volume total

`MatchingService.resolvedStatus:35-42` compare la distance réalisée à `targetDistanceM` à ±15 % :
au-dessous du seuil, `COMPLETED`. La **structure** n'est jamais comparée.

Un 10×400 m (≈ 12 km avec échauffement et retour au calme) remplacé par 12 km de footing tranquille
ressort donc « réalisé ». Pour un coach de course à pied, c'est la distinction qui compte le plus,
et c'est celle que l'application efface.

Pire, `score:66-68` : si la séance n'a **ni** distance **ni** durée cible, le score retombe sur la
seule date (1,0 le jour même), le rapprochement est automatique, et `resolvedStatus:38` renvoie
`COMPLETED` par défaut. N'importe quelle activité du jour valide alors la séance.

### G6 — Aucun dédoublonnage hors Strava, et le compteur de la semaine somme tout

`ActivityService.importActivity:95-97` ne déduplique que sur le triplet
`(athleteId, source, externalId)`. Or `externalId` n'est **jamais** renseigné pour un fichier
(`createFromFile:206-210`) ni pour une saisie manuelle (`logForAthlete:175`).

Deux scénarios quotidiens : l'athlète exporte le GPX de sa montre et l'importe, puis connecte
Strava → la même sortie existe deux fois, sous deux sources différentes que même le contrôle
d'unicité ne rapprocherait pas. Ou il ré-importe le même fichier par maladresse sur mobile.

Effet visible : `AnalyticsService.weekSummary:74-81` somme **toutes** les activités de la semaine,
rapprochées ou non. L'athlète lit « 64/45 km » sur une semaine où il en a couru 32. Le cahier des
charges §11 pose pourtant « zéro doublon d'activité » en critère d'acceptation.

### G7 — Le déplacement de séance est sans limite et sans signal

`WorkoutService.moveByAthlete:440-449` change la date, pose `movedByAthlete`, et c'est tout :
aucune contrainte, **aucune notification au coach**. Le sélecteur propose 14 jours à partir du
lundi de la semaine affichée, jours passés compris — seul le jour courant est désactivé
(`athlete-calendar.component.ts:351-358`, `[disabled]="p.isCurrent"`).

Rien n'empêche donc d'empiler cinq séances sur le dimanche, ni de déplacer une séance dans le
passé. Et `movedByAthlete` / `originalDate`, pourtant renvoyés par l'API
(`WorkoutResponse.java:28-29`), sont **absents du modèle front** : jamais affichés, ni dans le
calendrier coach ni ailleurs.

Réponse à la question « déplacer suffit-il ? » : le geste est bon et l'invariant (jamais modifier,
jamais supprimer) est juste. Ce qui manque, c'est le retour au coach. Un athlète qui décale trois
séances pour cause de déplacement professionnel produit une semaine que le coach n'a pas
prescrite, sans que rien ne le lui dise.

### G8 — Un athlète ne peut appartenir qu'à un seul groupe

`Athlete.group` est un `@ManyToOne` (`Athlete.java:46-48`). Un club qui organise « groupe piste le
mardi » et « groupe sortie longue le dimanche » — l'organisation la plus courante — ne peut pas
inscrire les mêmes athlètes dans les deux. Le coach doit choisir un axe de découpage unique
(niveau, ou séance, ou discipline) et gérer le reste à la main.

### G9 — Un test 1RM écrase le profil sans aucun garde-fou

`StrengthTestService.applyToProfile:103-115` — le commentaire l'assume : « Un test direct écrase
toujours le profil ». Aucun contrôle d'écart, aucune confirmation, aucune alerte au coach.

Un AMRAP passé en fin de séance, ou un jour de fatigue, fait chuter le e1RM de 15 %. Toutes les
charges prescrites suivent mécaniquement (`StrengthChargeEngine`), et la séance suivante déclenche
l'alerte « chute de charge » de G3 — une alerte causée par le recalcul de l'application, pas par
l'athlète. Le coach voit un athlète qui régresse ; en réalité c'est un test mal placé.

**Effort** : 2 h — signaler l'écart au-delà de ±10 % et demander confirmation avant d'écraser.

### G10 — « Tout le club » ignore la confidentialité des athlètes privés

`CoachDashboardService.athletesInScope:199-201` renvoie `findByClubIdOrderByLastNameAsc(clubId)`
sans passer par `AthleteAccessValidator` — alors que le calendrier de groupe, lui, filtre athlète
par athlète (`TrainingGroupService.calendar:60-64`). Le périmètre par défaut est `all`
(`CoachDashboardController.java:30,42,83` et `dashboard.component.ts:77`).

Un coach assistant qui ouvre son cockpit voit donc la fatigue et la douleur des athlètes **privés**
de ses collègues. C'est la promesse du modèle multi-coach — « un athlète privé n'est jamais
partagé » — qui tombe sur l'écran le plus consulté du produit.

---

## 🟢 À AMÉLIORER (post-bêta)

| # | Constat | Où |
|---|---|---|
| A1 | Pas de calendrier club global : la vue multi-athlètes n'existe que par groupe, et un athlète sans groupe n'y apparaît nulle part | `TrainingGroupController:49` |
| A2 | Aucune cible de FC sans test lactate : `hrForSpeed` exige `lt1Ms`, `lt2Ms`, `fcLt1` **et** `fcLt2`. L'athlète qui s'entraîne au cardio n'a aucune cible tant qu'il n'est pas passé au labo — un repli sur % de FC max serait immédiat | `SessionCalculatorEngine.java:225-236` |
| A3 | Wellness incomplet : HRV, humeur et poids quotidien absents du check-in (CDC §3.5, priorité C) | `DailyCheckIn.java` |
| A4 | Pas de bilan post-course (réalisé vs chrono visé) — l'objectif passe simplement en `PAST` (CDC §3.7, priorité C) | `RaceObjectiveStatus` |
| A5 | Un message peut référencer une séance (`Message.workoutId`), mais le lien n'est rendu que côté coach — « l'athlète n'a pas de vue séance dédiée » (commentaire du code). L'athlète reçoit un message qui parle d'une séance qu'il ne peut pas ouvrir depuis le fil | `chat.component.ts:144-147` |
| A6 | Pas d'export de données brutes pour un préparateur physique externe : seul l'export PDF du programme existe | `ProgramExportController` |
| A7 | Import FIT non géré (GPX/TCX seulement), Garmin/COROS non implémentés | `GpxParser.java:18` |
| A8 | Facturation et abonnements absents (CDC §3.8 / §10, priorité C) | — |

---

## Manquant et bloquant pour la bêta vs manquant mais acceptable en V1

**Bloquant** — sans ça, un bêta-testeur non accompagné décroche ou perd confiance dans les
chiffres : B1 (RPE faux), B2 (forme périmée), B3 (blessure ignorée + digest sans mémoire),
B4 (charge sur durée prescrite), B5 (pas de « séance non faite »), G1 (bibliothèques vides),
G6 (doublons et compteur de semaine faux), G10 (athlètes privés exposés).

**Acceptable en V1** — le manque se contourne et ne fausse aucune donnée : A1 (calendrier club
global), A3 (HRV/humeur/poids), A4 (bilan post-course), A6 (export données), A7 (FIT,
Garmin/COROS), A8 (facturation), G8 (multi-groupes — gênant en club, contournable en coaching
individuel).

---

## Ce qui tient, et qu'il ne faut pas toucher

- **L'invariant « la forme, c'est fatigue + douleur, jamais le RPE » est respecté partout.**
  `FormStatusEngine.classify(fatigue, pain)` n'a que deux appelants — `CoachDashboardService:185`
  et `GroupAnalyticsService:62` — et aucun écran ne réintroduit le RPE dans un statut de forme.
  Vérifié sur l'ensemble des parcours.
- **Les zones sont un vrai point fort.** Échelle standard seedée automatiquement (13 bandes
  d'allure + bandes cardio) donc pas de page blanche ; valeurs par athlète **calculées** depuis une
  règle (ancre + %) et resynchronisées dès qu'une référence change
  (`ZoneValueSyncService.resync`) ; plusieurs modèles de zones applicables athlète par athlète
  (route/trail, débutant/confirmé). Le coach ne saisit jamais 13 zones × N métriques à la main, et
  les valeurs manuelles ou verrouillées ne sont jamais écrasées. La réponse à « assez flexible sans
  devenir ingérable » est oui.
- **Le repli VDOT sur les allures est honnête** : quand LT1/LT2 manquent, les allures dérivent des
  chronos (marathon ≈ LT1, 10 km ≈ LT2, 3–5 km ≈ VC — correspondances de Daniels), et c'est dit —
  « estimée » côté athlète, « Allures estimées (VDOT) » vs « Seuils mesurés — cibles fiables »
  côté éditeur. C'est exactement le bon compromis. (C'est ce qui rend B1 d'autant plus dommage :
  tout le reste de ce chemin est soigné.)
- **Le rattrapage des retours sur 7 jours** évite qu'un ressenti oublié le soir soit perdu
  (`today.component.html:66-83`).
- **Le travail en groupe est mieux fait qu'on ne l'attendrait** : calendrier hebdomadaire
  multi-athlètes en un appel, analytics de groupe, application d'un plan ou d'un mésocycle à tout
  le groupe **avec filtrage des droits athlète par athlète** et compte-rendu des athlètes ignorés.
- **La confrontation prescrit ↔ ressenti côté coach** (RPE prescrit pondéré par la durée des blocs,
  écart affiché) est exactement ce qu'un coach regarde après une séance
  (`workout-detail.component.ts:149-170`).
- **Les formules sont conformes à ce qui est documenté** : Riegel `^1.06`, Nuzzo (constante 104,9)
  par défaut, zones de travail Lacourpaille, ACWR unifiant course et force sur un seul score sRPE.

---

## Ordre d'exécution suggéré

**Avant d'ouvrir (~3,5 j)** — B1 (1 h, le meilleur rapport effort/impact du lot), B5 (0,5 j),
B4 (1 j), B2 (0,5 j), G10 (2 h), G6 (0,5 j).

**Première quinzaine (~3 j)** — B3 (1 j), G1 (1–2 j, catalogue d'exercices seedé), G9 (2 h),
G3 (2 h).

**Premier mois** — G2 (vue coach des check-ins), G4 (remontée des alertes de force),
G5 (comparaison de structure et non de volume), G7 (signal de déplacement au coach).

---

*Audit fonctionnel — DARI Lab Training, août 2026. Trois profils, trois mésocycles déroulés dans
le code. Chaque constat cite le fichier et la ligne, et la situation de terrain qui le révèle.*
