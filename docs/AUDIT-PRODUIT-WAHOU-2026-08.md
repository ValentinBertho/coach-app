# Audit produit / UX / métier — DARI Lab (août 2026)

> **La question posée** : comment rendre DARI Lab plus utile, plus simple, plus intelligent et plus
> indispensable — sans devenir un Nolio avec moins de fonctionnalités ?

**Méthode.** Lecture du code, pas des documents : 517 fichiers Java (~37 000 lignes), 331 fichiers
front (~44 000 lignes), 48 contrôleurs, 13 moteurs de calcul, 47 entités, 8 tâches planifiées, les
deux portails et leurs parcours. Les documents internes (`ANALYSE-CONCURRENTIELLE-NOLIO`,
`PLAN-PRODUIT`, `PLAN-EVOLUTION`, `AUDIT-FONCTIONNEL`) ont été lus **après** l'exploration, pour
recouper — pas pour orienter. Ce qui suit contredit ou reformule certains de leurs arbitrages.

**Ses limites.** Aucun coach interrogé, aucune métrique d'usage, aucun accès direct à Nolio (les
faits Nolio viennent de l'analyse concurrentielle interne d'août, elle-même bâtie sur de la
recherche web). Ce qui est affirmé sur DARI Lab est vérifiable dans le code ; ce qui est affirmé sur
Nolio est de seconde main.

---

## 1. Verdict produit

**DARI Lab a un moteur de calcul remarquable et une interface de saisie remarquable. Il lui manque
la couche du milieu : celle qui conclut.**

Treize moteurs tournent. Aucun ne produit une décision.

| Le moteur calcule | Ce que l'utilisateur reçoit | Ce qu'il doit faire du chiffre |
|---|---|---|
| `FormStatusEngine` | une pastille 🟢🟡🔴 | l'interpréter |
| `LoadEngine` | ACWR 1,42 · monotonie 2,1 · répartition 68/24/8 % | l'interpréter |
| `ProgressionEngine` | « +2,5 kg » | **le ressaisir à la main** dans la séance suivante |
| `SessionCalculatorEngine` | 3:45–3:52 /km | le lire |
| `VdotEngine` | VDOT 45,2 + huit équivalences | le comparer mentalement à son objectif |
| `PlannedLoadEngine` | 412 UA prévues cette semaine | rien : la valeur est additionnée, jamais confrontée |

La boucle **prescription → réalisation → retour → analyse → adaptation** est complète jusqu'à
« analyse », et **entièrement manuelle** à « adaptation ». Le menu contextuel du calendrier a bien
une entrée « Adapter » : elle ouvre l'éditeur de structure
(`calendar.component.ts:1892`). C'est tout. Rien, nulle part, ne réécrit une séance à partir de ce
que le produit a compris.

Ce n'est pas un manque de fonctionnalités — c'est un manque de **conclusions**. Et c'est une bonne
nouvelle : la couche de conclusion est peu coûteuse, parce que tout ce dont elle a besoin est déjà
calculé, déjà stocké, déjà exposé.

> **La phrase de positionnement** : Nolio est un tableau de bord. DARI Lab doit devenir un
> **copilote**. Pas plus de fonctionnalités que Nolio — la phrase que Nolio ne dit pas.

---

## 2. Forces actuelles

Ce qui est déjà au-dessus du marché, et qu'il ne faut surtout pas abîmer.

**1. La chaîne de recalcul automatique.** Une performance saisie déclenche : VDOT → huit allures
d'équivalence → resynchronisation des zones `AUTO` non verrouillées → cibles de toutes les séances
prescrites (`AthletePhysioService#addPerformance` → `ZoneValueSyncService#resync`). Cette cascade
est la fondation du produit, et très peu d'outils l'ont. Elle est sous-exploitée (§3.5) mais elle
existe et elle est propre.

**2. La charge unifiée course + force**, avec des garde-fous que presque personne ne pose : pas
d'ACWR avant 21 jours **et** 8 séances (`LoadEngine.RATIO_MIN_*`), durée réelle déclarée > durée
mesurée par la montre > durée prescrite (`AthleteLoadService#durationSeconds`), sorties hors
programme comptées si le RPE existe. Ce sont les détails qui font qu'un coach fait confiance au
chiffre.

**3. La prescription figée au moment de l'assignation** : `Workout.sessionSnapshot` +
`Workout.calculatedPaces` conservent la structure **et** les fourchettes calculées pour cet athlète
ce jour-là. Conséquence peu exploitée mais énorme : le prévu et le réalisé sont comparables bloc par
bloc, pour toujours (§4.2).

**4. Le pilotage par exception.** Alertes actionnables triées par gravité, file « retours à
traiter », digest à 7 h avec anti-répétition (`AlertDigestLog`), « vu 👏 », bilans hebdomadaires
dimanche 18 h / lundi 7 h 30. Le produit sait déjà **interrompre au bon moment** — c'est l'ossature
sur laquelle brancher les recommandations.

**5. Les invariants métier tenus.** La forme n'est jamais dérivée du RPE. Un signal périmé devient
`STALE` plutôt que vert. Une indisponibilité déclarée éteint les alertes de charge, de séances
manquées et de silence. Une semaine de décharge ne déclenche pas d'alerte. Ces règles valent plus
que dix fonctionnalités : elles évitent le bruit qui fait désinstaller.

**6. L'ergonomie du calendrier coach** (rectangle de sélection, copier/coller, `mod+Z`, Alt+glisser,
mésocycle, calendrier de groupe) et le **mode séance de force plein écran** côté athlète. Sur les
deux gestes les plus répétitifs du métier, DARI Lab est probablement devant Nolio.

**7. La restitution pédagogique** : `load-explainer` dit trois fois chaque notion (ce que c'est,
comment ça se lit, ce que *ton* chiffre dit), `session-stats` affiche le réalisé en grand et le
prévu dessous avec l'écart écrit. La culture de restitution est là — elle n'est simplement pas
appliquée partout (§3.2).

---

## 3. Faiblesses et opportunités

Six trous de raisonnement, chacun vérifié dans le code. Ce sont eux qui portent les propositions.

### 3.1 L'adaptation n'existe pas

Tout ce que le produit détecte meurt en notification. Douleur 6/10 déclarée au réveil → une alerte
push au coach. Et la séance de fractionné du jour ? Toujours là, inchangée, avec ses cibles. ACWR à
1,52 → « risque de blessure » dans le digest de 7 h. Et la semaine prévue ? Inchangée. Le dernier
maillon de la boucle est un humain qui ouvre un éditeur de structure et retape des chiffres.

### 3.2 Le calcul s'arrête juste avant la conclusion

`LoadResponse` expose `distribution7d` / `distribution28d` — la répartition par domaine
d'intensité, exactement le chiffre qui dit si un athlète est polarisé ou coincé en zone grise.
Restitution actuelle (`load.component.html:37-47`) : trois segments colorés avec des pourcentages
écrits dessus. Aucune phrase. Aucun repère. C'est le contre-exemple parfait de « données →
graphique » là où il fallait « données → compréhension ».

Même schéma pour la monotonie (un 2,1 sans mode d'emploi) et pour `ProgressionEngine.suggest()`, qui
renvoie un `Suggestion(recommended=true, +2,5 kg)` affiché dans un écran de séance passée que
personne ne rouvre.

### 3.3 L'objectif est un post-it

`RaceObjective` porte une date, une distance, un chrono visé, une priorité A/B/C — et le service est
un CRUD pur (`RaceObjectiveService`, 5 méthodes, zéro calcul). Aucun lien avec le VDOT que le
produit calcule pourtant. Aucun lien avec le plan. Aucune notion d'affûtage. **Aucun résultat
enregistré après la course** : le jour d'après, l'objectif passe `PAST` et l'information disparaît.
La donnée la plus motivante du produit ne sert à rien.

### 3.4 La périodisation est un multiplicateur, pas un plan

`WorkoutService#generateMesocycle` recopie une semaine source en multipliant distances et durées
par un facteur (`copyWeek(..., multiplier)`). Il ignore la date de course, les indisponibilités
déjà déclarées, la charge chronique réelle de l'athlète, et démarre systématiquement « la semaine
prochaine ». Et quand le facteur ≠ 1, il **abandonne** le snapshot et les cibles calculées (commentaire
assumé dans `copyWeek`) : les semaines générées perdent leur prescription structurée. C'est utile,
c'est rapide, mais ce n'est pas de la périodisation.

### 3.5 Le niveau ne bouge que si quelqu'un le tape

Le VDOT vient exclusivement de `AthletePerformance`, saisi à la main par le coach ou l'athlète. À
côté : des activités importées avec flux échantillonné (`streamJson`), tours de montre (`lapsJson`),
splits calculés, temps-en-zone, FC max observée. **Rien de tout cela n'alimente le profil.** Un
athlète qui court un 10 km en compétition et synchronise sa montre garde ses allures de travail
d'il y a trois mois — alors que la cascade de recalcul (§2.1) n'attend qu'un chiffre.

### 3.6 Le check-in du matin ne change rien à la journée

Trois curseurs, dix secondes, très bien conçu. Résultat : une pastille pour le coach, une alerte si
la douleur est haute. La séance du jour, elle, est strictement identique. On demande à l'athlète de
déclarer son état et **on ne lui répond pas**. C'est le meilleur moyen de faire s'arrêter une saisie
quotidienne — et c'est aussi, symétriquement, le meilleur endroit du produit pour poser une
recommandation.

*(Deux manques structurels déjà bien instruits dans `PLAN-PRODUIT` ne sont pas repris ici : la
séance sans heure ni lieu (§1.1) et le club sans collectif (§1.2). Le premier conditionne
partiellement la proposition n° 5.)*

---

## 4. Top 10 des évolutions

Classées dans l'ordre où je les construirais.

---

### 4.1 — Feu vert du matin

**Problème.** L'athlète déclare fatigue 8 et douleur 4 à 7 h. À 18 h il ouvre l'app : « 6 × 1000 m à
3:45–3:52 ». Personne ne lui a dit quoi faire de sa fatigue. Il fait la séance entière, ou il ne
fait rien — les deux mauvaises réponses.

**Expérience.** Le check-in ne se replie plus sur « enregistré ». Il répond :

> 🟡 **Séance à alléger aujourd'hui.**
> Fatigue 8/10, et ta charge est déjà 40 % au-dessus de ton habituel.
> Prévu : 6 × 1000 m à 3:45–3:52 · **Proposé : 4 × 1000 m, mêmes allures.**
> [ Je fais la version allégée ] [ Je fais comme prévu ] [ Je décale à demain ]

Un tap. La séance est réécrite (la version d'origine reste consultable), le coach reçoit
« Léa a allégé sa séance du jour — fatigue 8, douleur 4 ». Côté coach, un réglage unique par athlète :
*proposer à l'athlète* / *me demander d'abord* / *ne rien proposer*.

**Intelligence métier.** `DailyCheckIn` (sommeil/fatigue/douleur) + `FormStatusEngine` +
`LoadEngine` (ACWR, monotonie) + `Workout.plannedLoadUa` + `Workout.calculatedPaces` + les
indisponibilités déclarées. Le seul élément neuf est un jeu de règles d'adaptation : réduire le
volume à intensité constante (rouge fatigue), retirer l'intensité en gardant le volume (rouge
douleur), décaler (ACWR élevé + séance D3). Une trentaine de lignes, testables comme les autres
moteurs.

**Valeur.** C'est la question n° 1 de tout athlète suivi : *je fais quoi aujourd'hui, vu comment je
me sens ?* Et c'est la boucle qui donne enfin une raison de remplir le check-in.

**Effet wahou.** L'application répond au lieu d'enregistrer. Le premier matin où elle dit « on
allège », l'athlète comprend que quelque chose le suit vraiment.

**Différenciation.** Nolio collecte plus de métriques quotidiennes (poids, FC repos, HRV) et les
affiche mieux. Aucune ne modifie la séance du jour. C'est la démonstration la plus courte de
« meilleures fonctionnalités, pas plus de fonctionnalités ».

**Complexité : moyenne.** Un moteur de règles + une carte étendue dans `morning-check-in.component`
+ une variante de séance (le chemin d'écriture `updateStructure` existe déjà).
**Priorité : impact fort.**

---

### 4.2 — Séance tenue ?

**Problème.** Un 10 × 400 couru à l'allure du dimanche est aujourd'hui « réalisé à 100 % » : la
validation se fait sur le volume total. Ni le coach ni l'athlète ne savent si les allures prescrites
ont été tenues, alors que les deux moitiés de l'information dorment dans la même base.

**Expérience.** Sur la fiche de séance et dans la file de retours du coach, une ligne, en haut :

> ✅ **Séance tenue à 92 %** — 8 répétitions sur 10 dans la fourchette.
> Les deux dernières ont dérivé de 8 s/km : fin de séance en difficulté.

Puis, dépliable, une puce verte/orange par bloc prescrit avec l'allure visée et l'allure obtenue.

**Intelligence métier.** `Workout.calculatedPaces` (les fourchettes figées, bloc par bloc, déjà
calculées pour cet athlète) × `Activity.lapsJson` (les tours relevés par la montre) ou, à défaut,
`SplitCalculator` (splits kilométriques depuis le flux). **Les deux structures existent, sont
stockées, et ne se sont jamais rencontrées.** Un seul service de rapprochement à écrire.

**Valeur.** Trois dettes soldées d'un coup : la validation au volume (G5 de l'audit fonctionnel),
la question « il l'a faite comment ? » que le coach pose aujourd'hui par message, et la matière
première des recommandations suivantes (un athlète qui part trop vite trois fois de suite, ça se
détecte).

**Effet wahou.** Le coach ouvre sa file du matin et lit une phrase au lieu d'ouvrir une courbe.

**Différenciation.** Nolio compare aussi prévu/réalisé, mais à l'échelle de la séance. DARI Lab peut
le faire **à l'échelle du bloc prescrit en fourchette physiologique** — parce que lui seul prescrit
en fourchettes ancrées sur LT2 ou VC. Fonctionnalité impossible à copier sans le modèle de données
qui va dessous.

**Complexité : moyenne.** Un `SessionComplianceService` (lecture de deux JSON déjà en base) + un
composant d'affichage. Aucune migration.
**Priorité : impact fort.**

---

### 4.3 — Trajectoire

**Problème.** L'athlète a déclaré « Marathon de Paris, 12 avril, objectif 3 h 15 ». Le produit
connaît son VDOT et sait inverser Daniels. Il ne lui a jamais dit s'il était sur la bonne
trajectoire.

**Expérience.** Un bandeau, sur l'accueil athlète et sur le résumé coach :

> 🎯 **Marathon de Paris — J-63**
> Objectif 3 h 15 · Projection actuelle **3 h 24** (VDOT 45,2)
> Il manque 2,8 points de VDOT. Atteignable : tu en as pris 1,9 sur les 8 dernières semaines.
> ⚠️ Ta charge chronique stagne à 280 UA/sem depuis 3 semaines.

Et après la course : un champ « chrono réalisé » qui **est** une performance — donc qui recalcule le
VDOT, les allures, les zones, et clôt la boucle.

**Intelligence métier.** `RaceObjective` (date, distance, targetTimeS) + `VdotEngine.vdot()` /
`racePaceSecPerKm()` / `timeForVdot()` (déjà écrits, déjà testés) + l'historique
`AthletePerformance` pour la pente de progression + `LoadEngine` pour la charge chronique.

**Valeur.** C'est la raison de rouvrir l'application chaque semaine. Aujourd'hui rien, dans DARI Lab,
ne relie l'effort du jour au but de la saison.

**Effet wahou.** « L'app m'a dit que j'étais à 9 minutes de mon objectif, et pourquoi. »

**Différenciation.** Nolio a des prédictions de performance. Ce qu'il n'a pas, c'est la boucle
fermée : objectif → écart → **cause identifiée dans la charge** → recommandation. Et le chrono de
course qui réinjecte automatiquement dans le profil physiologique.

**Complexité : faible à moyenne.** Tous les moteurs existent ; c'est un service d'assemblage et un
composant. Le champ « résultat de course » est une colonne.
**Priorité : impact fort.**

---

### 4.4 — Le plan part de la course

**Problème.** Le geste fondateur du métier — *« 14 semaines jusqu'à Paris : 4 de reprise, 6 de
spécifique, 2 de volume, 2 d'affûtage »* — n'existe nulle part. Le mésocycle actuel multiplie une
semaine type par un facteur, sans savoir qu'une course approche.

**Expérience.** Depuis la fiche de course : **« Construire le plan jusqu'à cette course »**. Un
aperçu s'affiche — la courbe de volume semaine par semaine, les phases nommées, l'affûtage calé sur
la date, les semaines de coupure déjà déclarées **sautées** (`AthleteUnavailability` est là), la
course posée comme séance `RACE`. Le coach déplace deux curseurs (volume de départ, nombre de
semaines de spécifique), puis « Poser dans le calendrier ». Ensuite, il édite séance par séance
comme aujourd'hui.

**Intelligence métier.** `generateMesocycle` + `MesocycleTemplate` + `RaceObjective.raceDate` +
`AthleteUnavailability` + charge chronique actuelle (point de départ réaliste plutôt qu'arbitraire).
Corriger au passage la perte de snapshot quand le facteur ≠ 1 — sinon les semaines générées perdent
leurs cibles.

**Valeur.** Fait passer la planification de « je duplique et j'ajuste » à « je pars de la date qui
compte ». Et rend enfin utile le module `TrainingPlan` du back, aujourd'hui sans interface.

**Effet wahou.** Douze semaines cohérentes posées en dix secondes, avec l'affûtage au bon endroit
et les vacances de février déjà contournées.

**Différenciation.** Nolio a des plans (et une marketplace pour les vendre). Ce sont des plans
**génériques** qu'on applique. Ici, la trame est construite depuis *cette* course, pour *cet*
athlète, avec *sa* charge actuelle et *ses* indisponibilités. Ce n'est pas le même objet.

⚠️ Ne jamais présenter ça comme un générateur automatique de plans : la machine pose la trame, le
coach garde chaque séance. C'est aussi ce qui protège du reproche « l'IA remplace le coach ».

**Complexité : moyenne à forte.** Le plus gros morceau du document, et celui qui demande le plus de
validation auprès de vrais coachs.
**Priorité : impact fort.**

---

### 4.5 — Le rattrapage intelligent

**Problème.** L'athlète déclare « pas fait — pas eu le temps ». Impasse : la séance reste `MISSED`,
la semaine est trouée, personne ne propose rien. Symétriquement, quand le coach saisit une
indisponibilité de 12 jours, les 9 séances qui tombent dedans restent dans le calendrier et
deviendront des séances manquées.

**Expérience.**
*Côté athlète*, juste après la déclaration : « On la décale à jeudi ? C'est ton seul jour libre
cette semaine et ta charge le permet. » → un tap → déplacée, coach notifié.
*Côté coach*, juste après la saisie d'une indisponibilité : « 9 séances tombent dans cette période.
[ Les supprimer ] [ Les décaler après la reprise ] [ Reprise progressive : −40 % la 1re semaine
puis retour ] ».

**Intelligence métier.** `Workout.status` + `MissedReason` + `AthleteUnavailability` +
`LoadEngine` (la charge autorise-t-elle le report ?) + les créneaux libres de la semaine. Tous les
chemins d'écriture existent (`reschedule`, `moveByAthlete`, `delete`, et `movedByAthlete` est déjà
tracé).

**Valeur.** Ce sont les deux moments de friction les plus fréquents du produit, et les deux où
l'application laisse l'utilisateur seul.

**Effet wahou.** Exactement le « pourquoi les autres ne le proposent pas ? ». Une indisponibilité
saisie qui nettoie et reconstruit le calendrier toute seule, c'est un quart d'heure de travail
épargné à chaque blessure.

**Différenciation.** Personne ne le fait, chez Nolio comme ailleurs, parce que ça suppose de savoir
ce qu'une reprise progressive veut dire — c'est-à-dire d'avoir un modèle de charge.

**Complexité : faible à moyenne.** Aucune donnée neuve, aucune migration.
**Priorité : impact fort.**

---

### 4.6 — La semaine en une phrase (l'alerte *avant*)

**Problème.** `PlannedLoadEngine` calcule déjà la charge **prévue** de chaque séance
(`Workout.plannedLoadUa`), et le calendrier l'additionne par semaine. Cette valeur n'est jamais
confrontée à la charge chronique de l'athlète. Résultat : l'ACWR alerte le lundi suivant, sur une
semaine déjà vécue. L'information existait avant, elle n'a pas été utilisée.

**Expérience.** Dans la colonne de totaux du calendrier coach, sous les kilomètres :

> **412 UA · +34 % vs habituel** ⚠️
> Trois séances dures en quatre jours. ACWR projeté 1,48 dimanche.

Et le même chiffre, pour l'athlète, sur son agenda du mois : « semaine chargée » / « semaine de
décharge ».

**Intelligence métier.** `Workout.plannedLoadUa` sommé par semaine + `LoadEngine`
`chronicLoad28dWeekly` + espacement des séances D3 (le RPE prescrit est dans le snapshot).
Zéro donnée nouvelle.

**Valeur.** Transforme un outil de constat en outil de prévention. Un coach qui voit +34 % pendant
qu'il construit la semaine la corrige tout de suite ; le même chiffre lundi prochain ne sert plus à
rien.

**Effet wahou.** Le calendrier devient un simulateur.

**Différenciation.** Nolio affiche la charge prévue. Il ne la confronte pas à l'historique de
l'athlète pour prédire l'ACWR de dimanche.

**Complexité : faible.** Une agrégation et une phrase.
**Priorité : impact fort.** Meilleur rapport valeur/effort du document.

---

### 4.7 — Progression de force appliquée, pas suggérée

**Problème.** `ProgressionEngine.suggest()` sait dire « toutes les séries réussies, RIR au-dessus de
la cible, aucune douleur → +2,5 kg ». Cette suggestion s'affiche dans l'écran d'une séance **passée**
et n'a aucun effet. Pour l'appliquer, le coach rouvre la bibliothèque et retape la charge.

**Expérience.** Quand une séance de force est planifiée, les charges arrivent **déjà ajustées**,
avec leur raison visible et réversible :

> Développé couché · **45 kg** ~~42,5~~ ↗
> *3 × 8 réussies à RIR 3 la semaine dernière*
> Squat · **60 kg** = *maintenu — douleur 3/10 signalée*

Et l'inverse : une douleur répétée sur un exercice propose sa **régression** — le lien
progression/régression existe déjà dans `PpExercise`.

**Intelligence métier.** `ProgressionEngine` + `StrengthResult` (séries réalisées avec RIR, RPE,
douleur) + `Athlete1rmProfile` + `StrengthScheduleService#schedule(..., adjustPct)` — **le chemin
d'écriture avec ajustement en pourcentage existe déjà**, il est utilisé par les cycles.

**Valeur.** C'est la promesse de la surcharge progressive, tenue automatiquement. Aujourd'hui, elle
est calculée et perdue.

**Effet wahou.** Le coach n'a plus jamais à se souvenir de ce que l'athlète a soulevé la semaine
dernière — et il voit pourquoi la charge a bougé.

**Différenciation.** Nolio **n'a pas de module musculation** (annoncé en roadmap). Chaque euro
investi ici creuse un écart que le concurrent ne peut pas combler avant plusieurs trimestres.

**Complexité : faible à moyenne.** Le moteur et le chemin d'écriture existent ; il manque le
branchement et l'écran de validation.
**Priorité : impact moyen à fort** (fort sur le segment prépa physique, qui est le différenciateur
le plus net).

---

### 4.8 — Ton niveau a bougé

**Problème.** Le VDOT ne bouge que si un humain saisit un chrono. Pendant ce temps, les activités
importées contiennent tout ce qu'il faut : flux, tours, FC max observée, efforts maximaux.

**Expérience.** Après un import, une proposition — jamais une écriture :

> 🚀 **Ta sortie de dimanche vaut mieux que ton profil.**
> 10 km en 41:20 → VDOT **47,5** (+2,3). Tes allures de travail gagneraient 6 s/km.
> [ Mettre à jour mes allures ] [ C'était une course, pas un test ] [ Ignorer ]

Côté coach, ces propositions s'empilent dans une petite file, avec l'origine et la date.

**Intelligence métier.** `Activity.lapsJson` / `streamJson` + `VdotEngine` + `CriticalSpeedEngine`
(deux efforts maximaux suffisent) + la cascade de recalcul de §2.1. Garde-fou identique à celui du
1RM : une valeur **mesurée** en test n'est jamais écrasée par une valeur **détectée** sans validation
humaine.

**Valeur.** Effet de bord majeur : un athlète sans test lactate finit par avoir des seuils estimés à
partir de ses sorties, au lieu de subir des prescriptions dégradées.

**Effet wahou.** L'application remarque le progrès avant l'athlète.

**Différenciation.** C'est la demande n° 1 du tableau public de Nolio (~1 270 votes) et elle n'est
pas livrée là-bas. Ici, les quatre moteurs nécessaires sont déjà écrits et testés.

**Complexité : moyenne.** Un moteur d'orchestration + une file de propositions.
**Priorité : impact fort.**

---

### 4.9 — « Ma journée » devient une file de décisions

**Problème.** L'écran du matin du coach affiche quatre listes (alertes, retours, messages, séances).
Excellent inventaire — mais chaque ligne demande encore d'ouvrir un écran pour agir.

**Expérience.** Chaque ligne porte l'action que le produit recommande, exécutable sur place :

> 🔴 **Marc — douleur 6/10 au mollet** → [ Alléger sa séance de demain ] [ Écrire ] [ Ignorer 7 j ]
> 🟠 **Léa — ACWR 1,52** → [ Décharger sa semaine −30 % ] [ Voir sa charge ]
> 🟡 **Antoine — 3 séances manquées** → [ Replanifier la semaine ] [ Écrire ]

**Intelligence métier.** `CoachDashboardService#alerts` (qui produit déjà un `type` par alerte :
`PAIN`, `ACWR_HIGH`, `MONOTONY`, `MISSED`, `SILENCE`, `STRENGTH_*`) + les actions des propositions
4.1, 4.5 et 4.6. C'est une table de correspondance type d'alerte → action, pas un nouveau moteur.

**Valeur.** Le coach traite sa matinée sans quitter un écran, depuis son téléphone.

**Effet wahou.** L'écran ne dit plus ce qui va mal : il propose ce qu'on en fait.

**Différenciation.** C'est l'aboutissement du « pilotage par exception » que DARI Lab a déjà choisi,
et que Nolio (tableaux de bord personnalisables, philosophie « toutes les données ») n'a pas choisi.

**Complexité : faible à moyenne** — mais dépendante de 4.1 / 4.5 / 4.6 pour les actions.
**Priorité : impact fort.**

---

### 4.10 — Le fil de l'athlète

**Problème.** Six mois de suivi sont éparpillés : douleurs par séance (`injuriesJson` porte pourtant
zone / type / côté, en structuré), indisponibilités, tests lactate, 1RM, records, courses, charge.
Aucun endroit ne raconte l'histoire, et il n'existe aucun livrable à remettre à l'athlète en fin de
cycle.

**Expérience.** Sur le résumé de l'athlète, une frise verticale : tests, records, courses,
blessures (avec la zone du corps), coupures, pics de charge. Et un bouton **« Bilan de période »** :
deux dates → un PDF (volume, répartition par domaine, charge, tests, courses, blessures,
progression force) que le coach envoie à son athlète.

**Intelligence métier.** Tout existe déjà, dispersé. `ProgramPdfService` (OpenPDF) sait déjà générer
un document.

**Valeur.** Deux valeurs distinctes : la mémoire pour le coach, et **le livrable qui fait exister
son travail** vis-à-vis de l'athlète qui le paie.

**Effet wahou.** « J'ai six mois de suivi en une page, et je peux l'envoyer. »

**Différenciation.** C'est le coût de sortie. Après six mois, cette frise n'existe nulle part
ailleurs, et elle ne se re-crée pas par un export CSV.

**Complexité : moyenne** (assemblage + PDF).
**Priorité : impact moyen** — mais impact fort sur la **rétention**, ce qui n'est pas la même chose.

---

### Ce que je ne construirais pas

| À écarter | Pourquoi |
|---|---|
| **Multi-sport (vélo, triathlon)** | Le jour où DARI Lab devient généraliste, il perd son seul avantage défendable et devient un Nolio moins mûr. |
| **Tableaux de bord personnalisables** | Le contraire du positionnement. Un copilote n'offre pas 40 widgets : il dit quoi faire. |
| **Questionnaires configurables** | Chaque champ ajouté est un champ que l'athlète ne remplira pas. Trois curseurs qui déclenchent une action valent mieux que douze qui alimentent une courbe. |
| **HRV maison / mesure par caméra** | Chantier lourd, faible spécificité, terrain où Nolio a des apps natives. À prendre en entrée si un partenaire la fournit, pas à mesurer soi-même. |
| **Marketplace / plans vendables** | Modèle de Nolio, avec 3 000 coachs pour l'alimenter. Sans base installée, une marketplace vide est un signal négatif. |
| **Badges, séries, gamification** | La série existe déjà côté athlète — c'est suffisant. Le renforcement qui compte ici est le « vu 👏 » du coach : humain, pas décoratif. |
| **Modèles de charge multiples (TSS, TRIMP)** | Parler la langue du marché a un coût : trois vérités concurrentes sur le même écran. À reconsidérer seulement si des coachs le demandent explicitement à la migration. |

---

## 5. Les trois fonctionnalités « Wahou » à construire en premier

**4.1 Feu vert du matin · 4.2 Séance tenue ? · 4.3 Trajectoire**

Pas parce que ce sont les trois « meilleures » prises isolément, mais parce qu'ensemble elles
racontent **une seule histoire sur trois échelles de temps** :

| | Question de l'utilisateur | Ce que le produit répond |
|---|---|---|
| **Le matin** (4.1) | Je fais quoi aujourd'hui ? | Une séance adaptée à mon état |
| **Le soir** (4.2) | Ça a donné quoi ? | Un verdict, pas une courbe |
| **La saison** (4.3) | Où je vais ? | Un écart chiffré à mon objectif, et sa cause |

Trois écrans, trois petits moteurs, **zéro donnée nouvelle à collecter** : tout ce dont ils ont
besoin est déjà dans la base. Et ce sont les trois seuls endroits où un utilisateur dit à voix haute
« ça, c'est bien pensé » — parce que ce sont les trois moments où il attendait une réponse et n'en
recevait aucune.

Livrées ensemble, elles suffisent à changer la phrase de vente : *« DARI Lab ne vous montre pas vos
données. Il vous dit quoi faire. »*

---

## 6. Différenciation DARI Lab vs Nolio

### Le rapport de force, honnêtement

| | Nolio | DARI Lab |
|---|---|---|
| **Fait mieux** | Écosystème montres (≈18 marques, **export des séances vers la montre**), apps natives, multi-sport, monétisation (marketplace, Stripe), 9 ans de production, 20 000 utilisateurs, métriques quotidiennes riches (poids, FC repos, HRV) | Physiologie mesurée (LT1/LT2 Dmax, VC, VDOT, domaines d'intensité), **préparation physique complète** (Nolio ne l'a pas), charge unifiée course + force, prescription en fourchettes recalculée automatiquement, calendrier coach, rigueur RGPD/santé |
| **À ne pas copier** | Marketplace, multi-sport, dashboards personnalisables, questionnaires configurables | — |
| **Fenêtre de tir** | Le module musculation est annoncé en roadmap : il n'existe pas encore | Chaque trimestre d'avance sur la prépa physique intégrée est un trimestre gagné |
| **Trou rédhibitoire** | — | **L'export des séances vers la montre.** Sans lui, l'athlète recopie sa séance à la main. Programme développeur Garmin fermé ; COROS ouvert — c'est la seule porte praticable aujourd'hui. |

### Pourquoi un coach choisirait DARI Lab plutôt que Nolio ?

Trois raisons, et une seule suffit à faire basculer un profil donné :

1. **Il prescrit à partir de la physiologie mesurée**, pas de zones en % de FCmax. « 6 × 1000 à
   102–106 % de vitesse critique » se saisit tel quel et se recalcule tout seul quand le seuil
   bouge. Aucun généraliste ne descend à ce niveau.
2. **La préparation physique est dans le même calendrier et dans la même charge.** Un coach de trail
   qui fait deux séances de renfo par semaine tient aujourd'hui deux outils. Ici, un seul ACWR.
3. **L'application lui dit quoi décider.** C'est la partie à construire — et c'est celle qui
   transforme un « outil correct » en « outil qu'on recommande ».

### Pourquoi, après six mois, un coach aurait du mal à revenir à Nolio ?

Parce que le coût de sortie ne sera pas dans les données — elles s'exportent — mais dans **tout ce
que le produit aura appris à décider à sa place**.

Après six mois, il aurait à réapprendre à faire manuellement :

- décider chaque matin, pour vingt-cinq athlètes, qui doit alléger (4.1) ;
- ouvrir chaque séance pour vérifier si les allures ont été tenues (4.2) ;
- calculer de tête l'écart entre le niveau actuel et l'objectif de chacun (4.3) ;
- reconstruire un calendrier après chaque blessure (4.5) ;
- se souvenir de ce que l'athlète a soulevé la semaine dernière (4.7) ;
- retenir les seuils, les 1RM, les régressions d'exercices, les zones du corps qui ont fait mal
  (4.10).

Un calendrier se remplace en une semaine. Un copilote qui a six mois de contexte, non. **La
rétention ne vient pas des données stockées, elle vient des décisions déléguées** — et c'est
précisément ce qu'un produit généraliste multi-sport ne peut pas faire, parce que ces décisions
supposent un modèle de données mono-sport profond.

---

## 7. Roadmap recommandée

Estimations calées sur le code réellement présent — classes existantes, chemins d'écriture
disponibles, migrations nécessaires.

### 🔥 Quick wins — beaucoup de valeur, peu de développement

| # | Fonctionnalité | Ce qu'il y a déjà | Ce qu'il faut écrire | Effort |
|---|---|---|---|---|
| 4.6 | **La semaine en une phrase** | `plannedLoadUa` calculé et sommé, `LoadEngine` | Une agrégation + une phrase dans la colonne de totaux | **S** — 2–3 j |
| 4.3 | **Trajectoire** | `VdotEngine` (inversion + Riegel), `RaceObjective`, historique perfs | Service d'assemblage + bandeau + colonne « résultat de course » | **S/M** — 4–6 j |
| — | **Verdict sur la répartition d'intensité** | `distribution7d/28d` déjà dans l'API, affichée en barre muette | Une phrase de lecture (« 68 % en D1 : polarisation correcte ») | **S** — 1 j |
| 4.5 | **Rattrapage intelligent** | `reschedule`, `moveByAthlete`, `AthleteUnavailability`, `MissedReason` | Règles de report + deux boîtes de dialogue | **S/M** — 5–7 j |
| 4.7 | **Progression de force appliquée** | `ProgressionEngine`, `schedule(..., adjustPct)` | Branchement + écran de validation | **S/M** — 5–7 j |

### 🚀 Gros différenciateurs — la signature DARI Lab

| # | Fonctionnalité | Point dur réel | Effort |
|---|---|---|---|
| 4.1 | **Feu vert du matin** | Écrire les règles d'adaptation sans jamais paraître prescriptif à la place du coach ; gérer la variante de séance sans détruire la prescription d'origine | **M** — 2–3 sem |
| 4.2 | **Séance tenue ?** | Aligner les tours de montre sur les blocs prescrits quand la montre n'a pas suivi la structure (heuristique nécessaire) | **M** — 2–3 sem |
| 4.8 | **Ton niveau a bougé** | Détecter un effort maximal sans confondre une compétition, un test et une sortie longue en descente | **M** — 3 sem |
| 4.9 | **File de décisions** | Rien de dur techniquement ; dépend de 4.1/4.5/4.6 | **S/M** après les précédentes |
| 4.4 | **Le plan part de la course** | Modèle de phases et d'affûtage à valider avec de vrais coachs ; corriger la perte de snapshot dans `copyWeek` | **L** — 4–6 sem |
| — | **Export séance → montre (COROS)** | Dépendance externe, hors contrôle. À lancer administrativement **maintenant**, à construire quand l'accès arrive | **M** + délai partenaire |

### 🧠 Vision long terme (12–24 mois)

- **4.10 Le fil de l'athlète** + bilan de période exportable — la mémoire et le livrable.
- **Le dossier de blessure**, construit *depuis* le fil : `injuriesJson` porte déjà zone/type/côté,
  la douleur est quotidienne, les indisponibilités sont datées. Corréler charge et douleur par zone
  du corps, c'est de la prévention, pas un tableau de bord.
- **Modèles d'adaptation appris** : au bout de N mois, savoir que *cet* athlète-là encaisse mal deux
  jours durs consécutifs. Le produit a la donnée ; il lui faudra le volume d'usage.
- **Le club comme collectif** (appartenance multi-groupes, annonce, émargement) — condition pour
  vendre à des clubs de plus de 50 athlètes, cf. `PLAN-PRODUIT` §1.2.
- **Facturation du coach indépendant** — commencer par le suivi de forfait, pas par l'encaissement.

**Ce qui n'est pas dans cette roadmap et qui la conditionne** : les cinq points d'exploitation
ouverts de l'analyse concurrentielle (identité de l'éditeur, sauvegardes **testées**, compte admin
en production, Sentry, relecture juridique). Aucune fonctionnalité de ce document ne compense une
base de données perdue.

---

## 8. Vision produit à deux ans

**DARI Lab n'est pas l'application qui fait le plus de choses. C'est celle qui sait quoi faire
ensuite.**

*Pour le coach*, la journée commence par une file de décisions, pas par un tableau de bord. Vingt-cinq
athlètes, six lignes à traiter, chacune avec l'action déjà rédigée : alléger, décaler, décharger,
répondre. Ce qui ne demande pas de décision ne s'affiche pas. Quand une course entre au calendrier,
le plan se dessine tout seul jusqu'à elle ; quand une blessure arrive, le calendrier se reconstruit
tout seul autour d'elle. Le coach garde chaque séance — la machine tient la structure.

*Pour l'athlète*, l'application répond au lieu d'enregistrer. Le matin, elle dit si la séance du jour
tient debout compte tenu de la nuit qu'il a passée. Le soir, elle dit si elle a été tenue, et
pourquoi la fin a dérivé. Chaque semaine, elle dit où il en est de son objectif, en minutes et
non en pourcentages. Il n'a jamais à interpréter un chiffre — c'est le travail du produit.

*Le fossé défendable* n'est pas une fonctionnalité, c'est **une boucle fermée** : physiologie
mesurée → prescription calculée en fourchettes → réalisation comparée bloc par bloc → état déclaré →
adaptation écrite dans le calendrier → nouvelle mesure. Chaque tour de boucle rend le suivant plus
juste. Un concurrent généraliste ne peut pas la copier sans renoncer au multi-sport, parce qu'elle
suppose un modèle de données mono-sport profond — celui qui est déjà en base ici.

Et le jour où Nolio livrera son module musculation, la question ne sera plus « qui a la
fonctionnalité ». Elle sera : **« lequel des deux sait quoi en faire ? »**

---

*Document rédigé à partir du code du dépôt (août 2026). Les propositions sont volontairement
limitées à dix : cinq excellentes valent mieux que trente moyennes, et les trois premières suffisent
à changer la promesse du produit.*
