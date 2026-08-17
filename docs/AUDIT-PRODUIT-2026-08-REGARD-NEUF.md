# DARI Lab — Audit produit / UX / métier

> Lecture directe du code (550 fichiers Java, ~46 000 lignes front Angular, 19 moteurs de calcul,
> 50 contrôleurs, 46 entités). Aucun audit ni roadmap antérieur n'a servi de base : les seuls
> éléments repris d'un document existant sont les faits publics sur Nolio (§6), et ils sont
> signalés comme tels.

---

## 1. Verdict produit

**DARI Lab n'est pas un planificateur auquel on a ajouté de la physiologie. C'est un moteur de
raisonnement d'entraînement auquel on a ajouté un calendrier.** C'est rare, et c'est là que tout
se joue.

Le produit sait déjà des choses que très peu de concurrents savent :

- qu'un 6 × 1000 a été couru 8 s/km trop vite sur les trois premières reps, et savoir le **dire en
  français** — « départ plus rapide que prescrit sur 3 répétitions » (`ComplianceEngine`) ;
- qu'une semaine **prévue** sortira à ACWR 1,52 avec deux séances dures collées, **avant** qu'elle
  ne soit courue (`WeekOutlookEngine`) ;
- qu'une douleur ne se traite pas en enlevant des kilomètres mais en enlevant les allures rapides,
  et que la fatigue fait l'inverse (`SessionAdaptationEngine`) ;
- qu'un objectif est hors de portée **au rythme de progression propre de cet athlète-là**, pas
  d'une table générique (`TrajectoryEngine`) ;
- qu'un ACWR calculé sur 12 jours d'historique est un mensonge, et refuser de l'afficher plutôt que
  d'alarmer un débutant (`LoadEngine.ratioReady`).

Et il possède un actif structurel : **`AthleteProposal`**, porte d'écriture unique de toute
automatisation, avec sa raison en clair, sa clé de déduplication, son expiration distincte du refus
et sa signature humaine (`decidedBy`). Ce n'est pas une prudence technique, c'est une position
produit — *un plan est l'engagement d'un coach, pas une variable que l'application ajuste pendant
la nuit*. Cette contrainte-là ne se rétro-installe pas dans un produit mûr : Nolio ne peut pas la
copier sans réécrire son cœur.

**Le problème n'est donc pas ce que DARI Lab ne sait pas faire. C'est que la moitié de ce qu'il
sait ne sort jamais de l'endroit où il l'a calculé.**

Trois constats qui résument l'audit entier :

1. **Le verdict de conformité n'existe que sur l'écran d'une séance.** Il est calculé, superbe,
   narratif — et jamais agrégé. Personne ne saura jamais que cet athlète part trop vite 7 fois
   sur 10. L'information la plus coûteuse à produire du produit est aussi la moins exploitée.
2. **`WeekOutlookEngine` juge la semaine et ne propose rien.** Le moteur qui saurait la corriger
   (`SessionAdaptationEngine.lighten`) vit dans le même dossier et n'est jamais appelé à l'échelle
   de la semaine. Le coach lit un diagnostic, puis cherche à la main quelle séance alléger.
3. **Le moment où le produit a le plus raison est celui où il devient le plus faux.** Quand le coach
   accepte « Ton niveau a bougé », `AthletePhysioService` recalcule VDOT → allures → zones. Mais
   `Workout.calculatedPaces` est **figé à l'assignation** (écrit dans `WorkoutService.updateStructure`
   / `assign`, jamais relu ni recalculé ensuite). Les six semaines déjà posées au calendrier
   continuent d'afficher les allures de l'ancien niveau, et **rien ne le dit à personne**.

La roadmap qui suit n'est donc pas une roadmap d'ajout. C'est une roadmap de **restitution et de
bouclage** : faire sortir ce qui est déjà calculé, et refermer les boucles ouvertes.

---

## 2. Forces actuelles

### 2.1 Ce qui est déjà excellent, et sous-estimé

| Force | Où c'est dans le code | Pourquoi ça compte |
|---|---|---|
| **La porte unique d'automatisation** | `AthleteProposal` + `ProposalService.accept()` — 6 types, dispatcher exhaustif, payload JSON, dedupeKey, `EXPIRED ≠ DISMISSED` | Le produit peut devenir très intelligent sans jamais devenir inquiétant. C'est la condition pour que le coach fasse confiance à l'automatisation suivante. |
| **La structure adaptée recalculée à l'acceptation** | `ProposalService.applySessionAdaptation` | Accepter le matin une proposition faite la veille n'écrase pas la retouche du coach. Ce détail-là, personne ne le pense avant d'avoir été mordu. |
| **Le refus de publier un chiffre faux** | `LoadEngine.ratioReady` (21 j + 8 séances), `TrajectoryEngine.weeklyProgress` (null sous 28 j), `ComplianceEngine.NOT_ASSESSED`, `WeekOutlookEngine` sans chronique | Aucun concurrent ne fait ça. C'est ce qui fait qu'un coach croit le deuxième chiffre parce que le premier n'était pas absurde. |
| **La séparation forme / RPE tenue partout** | `FormStatusEngine`, `DailyCheckIn`, `SessionAdaptationEngine` (commentaire explicite) | Un invariant métier tenu sur trois ans de code, c'est une signature. |
| **Le calculateur en fourchettes** | `SessionCalculatorEngine` : référentiel (LT1/LT2/VC/allures VDOT/VMA) × % min–max → allure, vitesse, FC, RPE, durée, distance, + `paceEstimated` | « 6 × 1000 à 102–106 % de VC » se prescrit une fois et se traduit pour 40 athlètes. C'est le geste de coaching le plus fin du marché. |
| **Les zones comme donnée, pas comme table figée** | `ZoneSet` / `ZoneMetric` (ancre + règle %) / `AthleteZoneValue` (AUTO / MANUAL / `locked`) / `ZoneValueSyncService.resync` idempotent | Plusieurs modèles de zones en parallèle, valeurs manuelles jamais écrasées. Profondeur rare. |
| **La charge unifiée course + force** | `LoadEngine` (sRPE Foster) alimenté par les deux disciplines via `AthleteLoadService` | Un coach hybride n'a pas deux vérités. Nolio n'a pas de module force du tout. |
| **La qualité de l'appariement prévu/réalisé** | `MatchingService` : asymétrie de date (une séance de demain n'est jamais candidate), séance `COMPLETED` encore rapprochable, `MISSED` écartée | Ce sont exactement les trois pièges du sujet, tous les trois évités. |
| **Le calendrier coach** | `calendar.component.ts` : DnD, sélection au lasso, copier/coller, undo/redo, duplication de semaine, décalage de semaine, lignes de groupe, bandeaux de cycle, indispos, courses, tests, activités en surimpression, totaux prévu/réalisé/UA | Densité et vitesse dignes d'un outil de production. Rien à envier. |
| **La discipline de restitution** | Partout : `title` + `reason` générés au moment de la détection, phrases affichables telles quelles, « 40 % au-dessus de ton habituel » plutôt que « ACWR 1,4 » | Le produit parle déjà la langue du coach. C'est un capital, pas un détail. |

### 2.2 Les boucles déjà fermées (à ne pas refaire)

- **Prescription → réalisation** : Strava webhook → import → `MatchingService` → statut de la séance
  → `ComplianceEngine` sur les tours de la montre.
- **Réalisation → profil** : `PhysioDetectionEngine` sur chaque import → proposition PHYSIO_UPDATE →
  acceptation → `addPerformance` → cascade VDOT / allures / zones.
- **État → adaptation** : check-in matinal → `SessionAdaptationEngine` → conseil affiché à l'athlète
  → demande → proposition → décision du coach en un tap.
- **Manqué → rattrapage** : `RecoveryService` fabrique des propositions de report (sur indispo
  déclarée, ou sur action « Replanifier » depuis l'alerte).
- **Relances au bon moment** : débrief 2 h après l'heure habituelle **de cet athlète**, rappel J-1,
  digest coach 7 h, bilan athlète dimanche soir / coach lundi matin, rappels course J-7 / J-1.

C'est déjà beaucoup. Les propositions du §5 ne rouvrent aucune de ces boucles.

---

## 3. Principales faiblesses / opportunités

Classées par « valeur perdue », pas par gravité technique.

### 3.1 Les allures périmées — le seul vrai défaut de justesse

`Workout.calculatedPaces` est un instantané figé à l'assignation. Rien ne le recalcule quand le
profil bouge (VDOT accepté, test lactate saisi, seuil corrigé, zone modifiée, `resync`). Conséquence
concrète : un athlète accepte un nouveau VDOT le 3 mars, ses zones sont à jour, son écran de zones
est juste — et les 18 séances déjà posées jusqu'au 15 avril lui demandent toujours 3:52/km au lieu
de 3:46. **Le produit a raison dans son profil et tort dans son calendrier, sans le dire.**

Ce n'est pas un bug à corriger silencieusement : c'est une **proposition à créer** (« Ton niveau a
changé — 18 séances à venir utilisent encore les anciennes allures. Les mettre à jour ? »), parce
que recalculer en silence violerait l'invariant du produit.

### 3.2 La conformité meurt là où elle naît

`ComplianceEngine` produit un verdict, un score, un delta signé par effort, et une phrase
d'interprétation (dérive de fin vs départ trop rapide). Cet objet est servi par deux routes
(`/workouts/{id}/compliance` coach + portail) et affiché sur **une** carte. Il n'est agrégé nulle
part. Rien ne répond à :

- cet athlète tient-il ses allures, en général ?
- sur quel type de séance décroche-t-il ? (seuil oui, VMA non)
- part-il systématiquement trop vite ? (le vrai problème de 60 % des amateurs)
- ses dérives de fin de séance s'aggravent-elles depuis trois semaines ?

C'est **la plus grosse valeur dormante du dépôt** : le calcul est fait, il ne manque qu'un
`GROUP BY`.

### 3.3 La semaine est jugée mais jamais corrigée

`WeekOutlookEngine` sort une phrase (« 412 UA · +38 % vs habituel · ACWR projeté 1,52. 2 séances
dures rapprochées. ») affichée dans la colonne de totaux du calendrier. Puis plus rien. Or :

- `SessionAdaptationEngine.lighten()` sait fabriquer une version allégée d'une séance ;
- `ProposalService` sait l'appliquer sous décision humaine ;
- `WeekOutlookEngine` sait déjà **quelles** séances sont dures et **lesquelles** sont collées.

Les trois ne se parlent pas. Le coach lit un diagnostic et fait le traitement à la main.

### 3.4 La distribution d'intensité n'existe qu'en proxy RPE

`LoadEngine.domainPct7d/28d` répartit la charge en trois bandes **depuis le RPE de séance** — un
proxy grossier. À côté, `TimeInZoneService` calcule le vrai temps-en-zone depuis le flux
(`stream_json`) contre les zones de l'athlète… **par activité seulement**. Personne ne peut donc
répondre à la question la plus classique du métier : *« mon athlète court-il vraiment 80/20 ? »* —
ni la comparer à ce qui était **prescrit** (que `calculatedPaces` connaît, bloc par bloc).

### 3.5 Le check-in vit derrière le mauvais onglet

Le portail athlète atterrit sur `/athlete/calendar` (redirection par défaut dans `app.routes.ts`).
Le check-in matinal et le feu vert vivent sur `/athlete/today`. **Toute la chaîne adaptative dépend
d'une déclaration quotidienne que l'écran d'entrée ne demande pas.** C'est un frottement de deux
taps qui coûte le taux de remplissage, donc la fraîcheur de la forme, donc la pertinence des
alertes du coach.

### 3.6 Capacité morte : les plans périodisés

`TrainingPlan`, `PlanAssignment`, `TrainingPlanService` (302 lignes), `TrainingPlanController`
existent au back. **Zéro référence dans le front** (vérifié : aucun fichier `.ts` ne mentionne
`TrainingPlan`). Un module entier payé et invisible.

### 3.7 Le plan depuis la course reste une multiplication

`PlanBuilderEngine` fait le bon geste (partir de la date de course, poser phases / décharges /
affûtage, sauter les indispos). Mais `PlanBuilderService.apply` **recopie la semaine de référence
en la multipliant par un facteur**. Une semaine d'affûtage est donc la semaine de base à 60 %, pas
une semaine d'affûtage : mêmes séances, moins de volume. Le squelette est juste, la chair ne suit
pas. Et le point d'entrée est unique : **pas de course déclarée = pas de plan**.

### 3.8 La bibliothèque de séances ne sait rien de l'entraînement

Catégories hiérarchiques, favoris, recherche. Aucune mémoire : pas d'usage, pas de « ce que tu
poses habituellement le mardi », pas de détection de trou (« cet athlète n'a pas fait de VMA depuis
six semaines »), alors que le calendrier connaît chaque `WorkoutType` posé, chaque `SessionCategory`
et chaque RPE prescrit.

### 3.9 Frottements plus petits, mais réels

- **La file d'alertes n'est pas dédoublonnée par athlète.** `CoachDashboardService.alerts()` produit
  une ligne par symptôme : un athlète blessé et silencieux avec un ACWR bas génère trois cartes.
  Sur 40 athlètes, la file du matin passe de « pilotage par exception » à « inventaire ».
- **Les propositions ne sont pas triées par urgence.** `pendingFor()` rend l'ordre du repository ;
  une demande d'athlète pour la séance de ce soir se retrouve sous une suggestion de charge de force.
- **Aucune fouille rétrospective des activités.** `PhysioDetectionEngine` n'inspecte qu'à l'import.
  Les meilleurs efforts contenus dans l'historique déjà stocké (tours + flux) ne sont jamais lus.
- **La force et la course ne se parlent que par la charge.** Le e1RM progresse, le VDOT stagne :
  personne ne le remarque, alors que les deux séries sont dans la même base.
- **Le PDF de bilan de période existe mais n'est pas un rendez-vous.** Il faut penser à l'exporter ;
  rien ne le propose en fin de cycle, alors que `CalendarNote` porte les cycles et connaît leur date
  de fin.

---

## 4. Top 10 des évolutions

Format court ici, détail complet des trois premières au §5.

| # | Nom | Ce que ça fait | Données déjà là | Complexité | Impact |
|---|---|---|---|---|---|
| 1 | **Profil d'exécution** | Agrège les verdicts de conformité par type de séance : « part trop vite sur 7 fractionnés sur 10, dérive après 70 min en sortie longue » | `ComplianceEngine` par séance, `calculatedPaces`, `lapsJson`, `WorkoutType` | Moyenne | **Fort** |
| 2 | **Semaine corrigeable** | `WeekOutlookEngine` ne dit plus seulement « ACWR projeté 1,52 » : il désigne la séance et propose l'allègement, applicable en un tap | `WeekOutlookEngine`, `plannedLoadUa`, `SessionAdaptationEngine.lighten`, `ProposalService` | **Faible** | **Fort** |
| 3 | **Allures rafraîchies** | Quand le profil bouge, propose de recalculer les cibles des séances déjà posées, et dit combien | `calculatedPaces`, `SessionCalculatorService`, `ProposalService` (nouveau type) | **Faible** | **Fort** |
| 4 | **Le check-in sur l'écran d'entrée** | Déplacer les trois curseurs + le feu vert en tête du calendrier athlète (ou faire de « Aujourd'hui » l'atterrissage) | `MorningCheckIn`, `ReadinessService` | **Très faible** | **Fort** |
| 5 | **Vraie distribution d'intensité** | Temps-en-zone agrégé par semaine/cycle, **prescrit vs réalisé** — « tu as prescrit 78/22, il a couru 61/39 » | `TimeInZoneService`, `stream_json`, `calculatedPaces`, `AthleteZoneValue` | Moyenne | **Fort** |
| 6 | **Une carte par athlète, pas par symptôme** | Regrouper les alertes du cockpit par athlète, avec un verdict de tête et une action | `CoachDashboardService.alerts()` | **Faible** | Moyen |
| 7 | **Le plan qui connaît ses phases** | Le plan depuis la course pioche dans la bibliothèque selon la phase (spécifique → allure course ; affûtage → volume bas / intensité conservée) au lieu de multiplier la semaine type | `PlanBuilderEngine`, `SessionCategory`, `WorkoutType`, `WorkoutTemplate` | Forte | **Fort** |
| 8 | **Fouille rétrospective des records** | Une passe unique sur l'historique d'activités : meilleurs efforts par distance → propositions de référence | `PhysioDetectionEngine`, `lapsJson`, `stream_json` | Moyenne | Moyen |
| 9 | **Bilan de cycle proposé, pas exporté** | À la fin d'un cycle (`CalendarNote`), le produit propose le PDF de bilan déjà rempli, avec la frise | `PeriodReportPdfService`, `AthleteTimelineService`, `CalendarNote` | **Faible** | Moyen |
| 10 | **Trous de la préparation** | « Aucune séance de VMA depuis 6 semaines », « 3 sorties longues manquées d'affilée » — détecté depuis le calendrier, proposé comme séance | `Workout.type`, `SessionCategory`, `WorkoutTemplate`, `RaceObjective` | Moyenne | Moyen |

**Ce que je ne recommande pas d'ajouter** : un tableau de bord de plus, des métriques quotidiennes
supplémentaires (poids, HRV) tant que le check-in à trois curseurs n'est pas rempli quotidiennement,
des modèles de charge alternatifs (TRIMP/TSS) — c'est du vocabulaire concurrent, pas de la valeur —
et tout paramétrage additionnel.

---

## 5. Les trois fonctionnalités « Wahou » que je construirais en premier

### 🥇 1. Profil d'exécution — *« Comment il court, pas seulement combien »*

**Nom court** : **Sa signature**.

**Problème.** Un coach lit des séances une par une. Il finit par « sentir » que tel athlète part
trop vite — au bout de trois mois, s'il est attentif, sur ses cinq athlètes les plus suivis. Sur
quarante athlètes, il ne le sent jamais. Et l'athlète, lui, ne se voit pas faire : personne ne lui
a jamais montré qu'il a fait la même erreur onze fois.

**Expérience.**
*Côté coach*, sur la fiche athlète, un encart de quatre lignes maximum :

> **Sa façon de courir** — 34 séances évaluées sur 12 semaines
> • **Fractionné** : part trop vite 7 fois sur 10 (−9 s/km en moyenne sur la 1re rep)
> • **Seuil** : dans la fourchette 8 fois sur 9 — c'est là qu'il est le plus juste
> • **Sortie longue** : dérive de 14 s/km sur le dernier tiers, sur 5 des 7 dernières
> → *[Ajouter la consigne « premier 1000 à l'allure exacte » à sa prochaine séance]*

*Côté athlète*, une seule phrase sur sa fiche de séance, quand elle se répète :
« Tu es parti plus vite que prescrit sur tes 4 derniers fractionnés. La 6ᵉ rep en paie le prix. »

**Intelligence métier.** Rien de neuf n'est calculé. On agrège les `SessionCompliance` déjà
produites, groupées par `WorkoutType` (et par `SessionCategory`), sur une fenêtre glissante. Les
trois lectures — *trop vite au départ*, *dérive de fin*, *taux dans la fourchette* — sont **déjà**
les trois branches de `ComplianceEngine.detail()`. Il n'y a littéralement qu'à compter.

**Valeur.** C'est la première chose qu'un coach cherche chez un nouvel athlète et qu'il met des
mois à établir. Le produit peut la donner en trois semaines de données. Et c'est **actionnable** :
une consigne de séance, pas une courbe.

**Effet wahou.** « Il sait que je pars trop vite. » C'est le moment où l'athlète comprend que
l'application le regarde vraiment, et où le coach comprend qu'elle lui fait gagner du jugement, pas
seulement du temps.

**Face à Nolio.** Nolio compare prévu/réalisé sur des totaux et affiche des graphiques. Il ne
possède pas l'appariement bloc-par-bloc contre une **fourchette figée à l'assignation** — ça
suppose la prescription en fourchettes, qu'il n'a pas. C'est donc une fonctionnalité qu'il ne peut
pas cloner sans changer son modèle de prescription.

**Complexité : moyenne** (un service d'agrégation + une requête sur les workouts terminés avec
`lapsJson`, un composant de restitution ; l'engine ne bouge pas). **Priorité : impact fort.**

---

### 🥈 2. Semaine corrigeable — *« Le diagnostic apporte son traitement »*

**Nom court** : **Corriger la semaine**.

**Problème.** Le calendrier annonce déjà « 412 UA · +38 % vs habituel · ACWR projeté 1,52.
2 séances dures rapprochées. » Le coach acquiesce… et doit maintenant décider quelle séance
alléger, l'ouvrir, retoucher les reps, revérifier le total. Cinq minutes, par athlète, par semaine.
Sur quarante athlètes, il ne le fait pas — et l'avertissement devient un bandeau qu'on ne lit plus.

**Expérience.** Le bandeau de semaine gagne **un bouton et une phrase** :

> ⚠️ **ACWR projeté 1,52** — 2 séances dures à 1 jour d'écart
> Le seuil de jeudi porte 128 UA. En le ramenant à 4 × 1000 (au lieu de 6),
> la semaine sort à **1,31**.
> **[Proposer l'allègement]** · [Décaler jeudi au vendredi] · [Ignorer cette semaine]

Le clic ne modifie rien : il crée une `AthleteProposal` de type `SESSION_LIGHTEN` visible dans la
file du matin et sur « Ma journée ». Le coach l'accepte d'un tap, ou l'ignore. L'invariant tient.

**Intelligence métier.** Tout existe : `WeekOutlookEngine` connaît le total, la chronique, les jours
durs et l'entassement ; `plannedLoadUa` est stocké par séance ; `SessionAdaptationEngine.lighten()`
fabrique déjà la version allégée ; `PlannedLoadEngine` sait recalculer la charge de la version
allégée ; `ProposalService` applique. **Le seul code neuf est le choix de la séance à désigner**
(la plus chargée parmi les dures, ou celle qui casse l'espacement) et la simulation du nouvel ACWR.

**Valeur.** Elle transforme le signal le plus intelligent du produit — juger une semaine *avant*
qu'elle soit courue, ce que fait très peu de monde — en geste de deux secondes. Une prévention qui
demande cinq minutes n'est jamais faite ; une prévention qui demande un tap l'est toujours.

**Effet wahou.** « Il me dit **laquelle**. » La différence entre un outil qui alerte et un outil qui
conseille tient entièrement dans ce mot.

**Face à Nolio.** Nolio affiche des courbes de charge et des ratios. Personne, sur ce marché, ne
propose *l'allègement précis d'une séance précise* — parce que ça suppose d'avoir une charge prévue
par séance, un moteur d'adaptation qui respecte les allures, et une porte d'écriture validée par un
humain. DARI Lab a les trois. C'est **la** signature possible du produit.

**Complexité : faible** (un service qui compose trois moteurs existants + un bouton dans le bandeau
de semaine du calendrier). **Priorité : impact fort.** C'est le meilleur rapport valeur/effort du
dépôt.

---

### 🥉 3. Allures rafraîchies — *« Ton plan connaît ton nouveau niveau »*

**Nom court** : **Rafraîchir le plan**.

**Problème.** Aujourd'hui, accepter « Ta sortie vaut mieux que ton profil : VDOT 52,4 (+1,8) » met
à jour le profil, les allures d'équivalence et les zones — et laisse les séances déjà posées avec
les cibles de l'ancien niveau, parce que `calculatedPaces` est un instantané pris à l'assignation.
L'athlète voit des zones à jour dans un écran et des allures périmées dans un autre. C'est le seul
endroit du produit où deux chiffres justes se contredisent.

**Expérience.** Immédiatement après l'acceptation d'un changement de référence (VDOT, LT1/LT2, VC,
FC max, ou `resync` de zones), une proposition apparaît :

> **Ton niveau a changé — 18 séances à venir utilisent encore les anciennes allures.**
> Ton seuil passe de 3:52 à 3:46/km.
> **[Mettre à jour les 18 séances]** · [Garder les allures actuelles]

Acceptée, elle relance `SessionCalculatorService.calculateSession` sur chaque séance future
`PLANNED` de l'athlète, réécrit `calculatedPaces` et `plannedLoadUa`, et notifie l'athlète une seule
fois (« Tes allures ont été mises à jour »). Refusée, elle ne revient pas (`dedupeKey`).

**Intelligence métier.** Aucun calcul nouveau : `SessionCalculatorService` sait déjà tout faire, on
lui redonne les mêmes séances. Un septième `ProposalType` (`PACES_REFRESH`) et une branche dans le
dispatcher.

**Valeur.** Elle protège **la justesse**, qui est le seul terrain où DARI Lab bat frontalement le
marché. Un coach qui trouve une allure fausse une fois cesse de croire toutes les autres — et c'est
le genre de défaut qu'on découvre en bêta, chez le premier athlète qui progresse vite.

**Effet wahou.** Discret mais durable : « il a pensé à ça ». C'est de la crédibilité, pas de la
séduction — et c'est ce qui fait rester.

**Face à Nolio.** Nolio recalcule ses zones aussi. Mais il n'a pas de prescription en fourchettes
figée à l'assignation, donc il n'a pas ce problème *et* il n'a pas cette finesse. La bonne façon de
le dire commercialement : « chez nous, une séance prescrite il y a six semaines sait que ton niveau
a bougé — et te demande avant de changer. »

**Complexité : faible** (un type de proposition, une branche du dispatcher, un appel de service
existant en boucle, un déclencheur dans `AthletePhysioService`). **Priorité : impact fort.**

---

### Les deux suivantes, en une ligne chacune

- **Le check-in sur l'écran d'entrée** (§3.5) : une journée de travail, et c'est le multiplicateur
  de valeur de tout le reste — sans déclaration quotidienne, le feu vert, la forme, les alertes et
  les propositions perdent leur carburant.
- **Vraie distribution d'intensité** (§3.4) : agréger `TimeInZoneService` par semaine et le
  confronter à la distribution **prescrite** que `calculatedPaces` connaît déjà. « Tu prescris 78/22,
  il court 61/39 » est une phrase qu'aucun concurrent ne sait dire, parce qu'aucun ne connaît la
  prescription au niveau du bloc.

---

## 6. Différenciation DARI Lab vs Nolio

> Les éléments factuels sur Nolio proviennent de l'analyse concurrentielle du dépôt
> (`docs/ANALYSE-CONCURRENTIELLE-NOLIO-2026-08.md`), qui indique elle-même n'avoir eu **aucun accès
> direct** au produit. Tout jugement UX sur Nolio est donc à revalider par un essai réel. Ce qui
> suit ne dépend pas de ces incertitudes : ce sont des différences de **modèle**, pas de finition.

### 6.1 Les vraies forces de Nolio (à ne pas sous-estimer)

1. **L'export de séances vers la montre.** L'athlète court sa séance guidée au poignet, sans rien
   recopier. C'est le seul manque de DARI Lab qui peut faire perdre un client en démonstration.
2. **Dix-huit intégrations d'entrée** contre une (Strava). Une dépendance unique à un tiers est un
   risque, pas seulement un manque.
3. **Le multi-sport.** Un coach de triathlon ne peut pas utiliser DARI Lab. Ce n'est pas un défaut,
   c'est une frontière de marché — à assumer explicitement.
4. **Le business du coach** : page publique, marketplace de plans, facturation Stripe. C'est ce qui
   fait qu'un coach professionnel *reste*, indépendamment de la qualité de l'outil d'entraînement.
5. **Neuf ans de production**, apps natives, centre d'aide indexé par Google.

### 6.2 Ce que Nolio fait mieux, et qu'il faut copier quand même

Une seule chose : **l'export vers la montre**. Pas parce que c'est une belle fonctionnalité, mais
parce que sans elle, la prescription en fourchettes — la meilleure du marché — se termine par un
athlète qui recopie « 6 × 1000 à 3:46–3:52 » sur un post-it. Le raffinement en amont ne sert à rien
si la dernière marche est manuelle.

### 6.3 Ce que DARI Lab fait déjà mieux

| | Pourquoi c'est structurel |
|---|---|
| **Préparation physique complète** | Bibliothèque d'exercices, formats avancés (EMOM, AMRAP, cluster, myo-reps), 1RM par 4 formules, protocoles de test, zones Lacourpaille, cycles, progression automatique. Nolio ne l'a **pas du tout**. |
| **Profondeur physiologique** | LT1/LT2 par Dmax modifié, vitesse critique + D′, VDOT/Daniels, domaines d'intensité avec priorité physio et repli FC. Nolio reste aux zones classiques. |
| **Prescription en fourchettes de % d'un référentiel** | Personne d'autre ne prescrit « 102–106 % de VC » et ne le traduit pour chaque athlète. |
| **Refus de publier un chiffre non fiable** | Le contraire de la culture « affiche tout, l'utilisateur triera ». |
| **Porte unique d'automatisation** | Le produit peut devenir agressivement intelligent sans jamais devenir inquiétant. |
| **Pilotage par exception** | « Ma journée » est une file de décisions, pas un cockpit. La plupart des concurrents font l'inverse. |
| **RGPD / santé de niveau produit médical** | AES-256-GCM au repos, consentement santé, anti-IDOR systématique, purges de rétention. Un argument de vente en club et en fédération. |

### 6.4 Ce qu'il ne faut **pas** copier

- **Les tableaux de bord personnalisables.** C'est l'aveu qu'on ne sait pas quoi montrer. La force
  de DARI Lab est de trancher à la place de l'utilisateur.
- **Les questionnaires configurables.** Chaque question ajoutée est un taux de réponse en moins. Le
  check-in à trois curseurs est meilleur *parce qu'il est court*.
- **Les modèles de charge multiples (TSS/TRIMP/Coggan).** Trois vérités concurrentes sur le même
  écran = zéro vérité. Un seul modèle, bien expliqué, vaut mieux.
- **La HRV par la caméra du téléphone.** Mesure fragile, promesse forte, déception garantie.
- **Le multi-sport.** Une dilution qui coûterait la profondeur physiologique, qui est l'actif.

### 6.5 Pourquoi un coach choisirait DARI Lab

> *« Parce que Nolio me montre ce que mes athlètes ont fait. DARI Lab me dit ce que je devrais faire
> ce matin — et il a raison assez souvent pour que j'y jette un œil en premier. »*

Concrètement, trois raisons qui tiennent en démonstration :

1. **Il prescrit en physiologie, pas en allures recopiées.** Un test lactate saisi une fois change
   les cibles de quarante séances.
2. **Il fait la course *et* la salle**, avec une seule charge. Nolio n'a pas de module force.
3. **Il ouvre sur une file de décisions, pas sur des graphiques.** Le matin, sur un téléphone.

### 6.6 Pourquoi il aurait du mal à revenir après six mois

C'est la vraie question, et la réponse n'est pas « il aurait perdu ses données » — l'export règle
ça. La réponse est : **il aurait perdu ce que le produit a appris de ses athlètes.**

- Le **profil d'exécution** (§5.1) de chacun de ses athlètes — trois mois de conformité agrégée,
  qu'aucun export CSV ne reconstitue.
- Le **rythme de progression observé** de chacun, qui alimente `TrajectoryEngine`. Recommencer
  ailleurs, c'est repartir sur des tables génériques.
- Les **valeurs de zones affinées** athlète par athlète, avec leurs verrous et leurs sources
  (`tested` / `estimated` / `manual`).
- La **frise** de six mois (`AthleteTimelineService`) : tests, records, courses, blessures, coupures
  — la mémoire qu'un coach met un an à reconstituer et qu'il remet à son athlète en fin de cycle.
- Et surtout **l'habitude** : accepter deux propositions le matin au lieu d'ouvrir quarante fiches.

Revenir à Nolio, ce ne serait pas perdre des fonctionnalités. Ce serait recommencer à décider
seul. **C'est ça, la douve** — et chacune des trois fonctionnalités du §5 la creuse.

---

## 7. Roadmap recommandée

Estimations calibrées sur le code réel : moteurs purs testables sans Spring, services fins,
composants Angular standalone à signaux, migrations Liquibase déjà nombreuses.

### 🔥 Quick wins — 3 à 4 semaines cumulées

| Évolution | Effort réel | Pourquoi c'est peu cher |
|---|---|---|
| **Check-in sur l'écran d'entrée** (§3.5) | **~1 j** | Déplacer deux composants existants, ou changer une redirection dans `app.routes.ts`. |
| **Semaine corrigeable** (§5.2) | **~5 j** | Un service de composition (3 moteurs existants) + un bouton dans le bandeau de semaine du calendrier + réutilisation de `SESSION_LIGHTEN`. Zéro migration. |
| **Allures rafraîchies** (§5.3) | **~4 j** | Un `ProposalType`, une branche du dispatcher, une boucle sur les séances futures. Une migration triviale (valeur d'enum). |
| **Alertes groupées par athlète** (§3.9) | **~2 j** | Regroupement dans `CoachDashboardService.alerts()` + un composant de carte. |
| **Tri des propositions par urgence** (§3.9) | **~0,5 j** | `requestedByAthlete` et `targetDate` sont déjà en base : un `ORDER BY`. |
| **Bilan de cycle proposé** (§4-#9) | **~3 j** | `PeriodReportPdfService` + `AthleteTimelineService` existent ; il manque le déclencheur sur la fin de `CalendarNote` de type cycle. |

### 🚀 Gros différenciateurs — 8 à 12 semaines

| Évolution | Effort réel | Ce qui coûte |
|---|---|---|
| **Profil d'exécution** (§5.1) | **~3 sem.** | Un service d'agrégation (requête sur les séances terminées + rejeu de `ComplianceEngine`, ou stockage du verdict à la clôture pour éviter le recalcul), deux composants de restitution, la calibration des seuils de significativité (ne rien dire sous 5 séances évaluées). |
| **Distribution d'intensité prescrit vs réalisé** (§3.4) | **~3 sem.** | Agrégation multi-activités de `TimeInZoneService` (les flux sont volumineux : prévoir un pré-calcul à l'import plutôt qu'à la lecture) + extraction de la distribution **prescrite** depuis `calculatedPaces`. |
| **Export vers la montre (Garmin puis COROS)** | **~6–8 sem.** + délai partenaire | Le dossier `DEMANDES-API-GARMIN-COROS.md` existe. Le vrai coût est la traduction `SessionStructure` → format constructeur et le délai d'agrément, pas le code. **À lancer maintenant parce que l'attente est longue**, même si le développement passe après. |
| **Plan qui connaît ses phases** (§3.7) | **~4 sem.** | Il faut un modèle de « ce qu'est une semaine d'affûtage » : mappage phase → catégories de séances, et sélection dans la bibliothèque. C'est du métier, pas de la technique — donc à cadrer avec un coach avant d'écrire une ligne. |
| **Exposer les plans périodisés** (§3.6) | **~2 sem.** | Le back existe entièrement. Attention : ne l'exposer que s'il ne fait pas doublon avec `PlanBuilder` et les mésocycles — sinon c'est un troisième chemin pour le même geste, et la simplicité en souffre plus que l'absence. |

### 🧠 Vision long terme — 12 à 24 mois

- **Le coach de réserve.** Le profil d'exécution, la trajectoire, la charge et les trous de
  préparation fusionnent en une proposition hebdomadaire : *« Voici la semaine que je poserais pour
  Marc, et pourquoi. »* Le coach modifie, accepte, refuse. Le produit ne remplace pas le coach : il
  lui présente un premier jet argumenté, exactement comme un assistant. La porte
  `AthleteProposal` rend ça possible sans jamais franchir la ligne.
- **La mémoire de club.** Ce qui marche, chez *ce* club, sur *ce* profil d'athlète : « les athlètes
  de ton club qui ont progressé de 3 points de VDOT en 12 semaines avaient tous fait 2 séances de
  seuil par semaine ». Une intelligence collective bâtie sur des données que seul un club possède,
  totalement impossible à copier depuis l'extérieur.
- **La prévention de blessure sérieuse.** Aujourd'hui : douleur déclarée + ACWR + monotonie. Demain :
  croiser `injuriesJson` (structuré : zone, type, côté), la dérive de conformité en fin de séance,
  la chute de cadence et la répétition des zones douloureuses — pour dire *« même schéma qu'avant
  ton aponévrosite de mars »*. C'est le seul terrain où un avantage physiologique devient un
  avantage vital pour l'utilisateur.
- **L'ouverture aux athlètes sans coach**, mais uniquement comme entonnoir : l'athlète autonome
  utilise le moteur, et le produit lui propose un coach du réseau quand sa trajectoire décroche.
  À traiter comme un canal d'acquisition, pas comme un second produit.

---

## 8. Vision produit à 2 ans

**La phrase à tenir :**

> *DARI Lab est l'application qui comprend le mieux l'entraînement — et qui, chaque matin, dit au
> coach et à l'athlète la seule chose qu'ils ont besoin de savoir : quoi faire aujourd'hui.*

**Les trois engagements qui en découlent, et qu'il faut refuser de trahir :**

1. **Aucune écriture sans signature humaine.** Tout passe par une proposition, avec sa raison en
   clair. C'est déjà l'architecture ; ça doit rester la promesse commerciale. Le jour où une
   exception est accordée « juste pour ce cas-là », l'actif est perdu.
2. **Aucun chiffre publié qu'on ne peut pas défendre.** Le produit se tait quand il ne sait pas.
   C'est un différenciateur, pas une limitation — et c'est ce qui autorise à être audacieux
   ailleurs.
3. **Un écran de plus doit remplacer un écran, pas s'y ajouter.** Toute la valeur du §5 est
   restitutive : elle tient dans des encarts de quatre lignes posés sur des écrans existants. Aucune
   des dix propositions du §4 ne crée une nouvelle rubrique de navigation. C'est délibéré.

**Ce que ça donne concrètement en 2028.** Un coach ouvre l'application à 7 h sur son téléphone. Il
voit six lignes : deux demandes d'athlètes à trancher, une semaine à corriger avec l'allègement déjà
préparé, un athlète dont la trajectoire vient de décrocher avec la cause identifiée (« trois séances
de seuil manquées sur quatre »), un bilan de cycle prêt à envoyer, et un profil d'exécution qui
signale que Marc part trop vite depuis un mois. Il traite tout en quatre minutes. Puis il fait son
métier : parler à ses athlètes.

C'est ça, l'application qu'on ne quitte pas — non pas parce qu'elle fait plus de choses que les
autres, mais parce qu'elle décide mieux.
