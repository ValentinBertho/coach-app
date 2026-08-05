# Plan d'évolution — DARI Lab Training (août 2026)

> **Source** : le tableau public de demandes de fonctionnalités de **Nolio** (« Prochaines
> fonctionnalités » + « Vote et propose des fonctionnalités »), soit ~60 demandes votées par sa
> base d'utilisateurs, plus les 4 chantiers que Nolio affiche comme en cours.
>
> **Ce que c'est vraiment** : la liste, chiffrée et classée, de ce que le leader du marché **ne
> fait pas encore** — alors que le cahier des charges positionne explicitement DARI Lab comme une
> « alternative à Nolio ». C'est une étude de marché que personne n'a eu à commander.
>
> **Ce que ce n'est pas** : une spécification produit. Un tableau de votes ne mesure que la
> demande **résiduelle**. Ce que Nolio fait bien n'y apparaît jamais — personne ne vote pour ce
> qui marche déjà. Lire cette liste comme un backlog serait construire un produit fait uniquement
> des trous d'un autre.

---

## Verdict en trois lignes

**La demande n°1 de la base Nolio (1270 votes) n'est pas une fonctionnalité, c'est le
positionnement de DARI Lab** : « détecter de nouvelles valeurs suite à un entraînement — seuils,
FTP, FC max ». Les utilisateurs de Nolio demandent que la physiologie se mette à jour toute seule.
DARI Lab a déjà les 13 moteurs qui calculent ces valeurs ; il lui manque uniquement la boucle qui
les **propose** après une sortie.

**Le centre de gravité du marché n'est pas la prescription, c'est le suivi long terme de
l'athlète.** Blessures (1118), nutrition (757), HRV (403), cycle menstruel (44), préparation
mentale (43) : plus de 2 300 votes portent sur la santé et la durée, pas sur la séance. Or
l'indisponibilité est aujourd'hui **purement déclarative** chez DARI Lab (cf. audit fonctionnel
B3).

**Sept demandes votées chez Nolio sont déjà livrées chez DARI Lab** — thème sombre (223), zones
multi-métriques (8), bibliothèque organisée (14), tests lactate/VO2max (13), vitesse critique
(23), formats EMOM/AMRAP/For Time, messagerie temps réel. Le chantier n'est pas de les
construire : c'est de les **dire**.

---

## 1. Ce que le tableau raconte, par thème

Votes agrégés par famille (une demande peut porter deux thèmes ; le total dépasse la somme brute).

| Thème | Votes | Demandes représentatives |
|---|---:|---|
| **Santé & suivi long terme** | ~1 230 | Suivi des blessures (1118), menstruation (44), préparation mentale (43), Withings/tension (11), glycémie Dexcom (15) |
| **Calendrier & vues de planification** | ~940 | Vue annuelle par semaine (738), logo par discipline (53), vue « Plan »/« Cycle » (46), fusion prévu/réalisé (34), Google Agenda (34), agenda perso coach (13), widget (11), offline (9) |
| **Analyse & comparaison de séances** | ~880 | Comparer 2+ séances (724), score de réalisation (37), HRR (21), échelle de graphique (18), puissance/FC (17), intensités mises en évidence (17), lier des séances similaires (15), découplage (10), dérive cardiaque (10), événements sur les courbes (9) |
| **Multi-sport (vélo, natation)** | ~830 | PPR vélo (493), natation/SWOLF (204), MyWhoosh (32), VC natation (23), Rouvy (15), inclure/exclure des sports (14), FC max par sport (13), W' running (13), TSS saison (11), Coggan route ≠ home trainer (10) |
| **Nutrition** | ~780 | Intégration nutrition (757), MyFitnessPal (21) |
| **Export & bilans** | ~610 | Bilan d'une période (539), timeline unique (26), bilan hebdo des qualités (22), impression paysage (17), charge par mésocycle (9) |
| **Détection automatique de valeurs physio** | 1 270 | Seuils, FTP, FC max détectés après un entraînement |
| **HRV / VFC** | ~400 | Import auto HRV (304), Elite HRV (88), VFC via capteur Coros (11) |
| **Trail** | ~75 | Dénivelé négatif (44), VMA ascensionnelle (21), VAP corrigée du D+ (10) |
| **Social / communauté** | 114 | Fil d'actualité des séances type Strava, côté coach |
| **Protection du coach** | 67 | Propriété intellectuelle du travail du coach quand l'athlète part |

Et les 4 chantiers que Nolio affiche comme en cours : **constructeur de séance + player mobile V2**
(EMOM, AMRAP, For Time), **serveur MCP & API v2** (agents IA), **nouvelle messagerie**.

---

## 2. L'avance à défendre — déjà livré ici, encore demandé là-bas

Avant de construire quoi que ce soit : ces sept points sont des arguments commerciaux immédiats,
pas des tickets.

| Demande Nolio | Votes | État DARI Lab |
|---|---:|---|
| Un thème sombre | 223 | ✅ `ThemeService` + tokens, clair/sombre sur toute l'app |
| Calculateur de vitesse critique | 23 | ✅ `CriticalSpeedEngine` (course à pied ; natation non) |
| Bibliothèque d'entraînements organisée | 14 | ✅ catégories **et** sous-catégories, recherche, cartes ↔ liste dense |
| Test lactate ou VO2max incorporé | 13 | ✅ `LactateTestController` + `LactateThresholdEngine` (LT1/LT2, Dmax modifié) |
| Modèle de zones selon différentes métriques | 8 | ✅ `ZoneSet` + `MetricType` : zones nommées par le coach, plusieurs modèles, recalcul auto |
| Formats EMOM / AMRAP / For Time (Nolio : *en cours*) | — | ✅ 7 formats + 5 types de série (drop-set, super-set, myo-reps, cluster, iso) |
| Messagerie moderne (Nolio : *planifié*) | — | ✅ SSE temps réel + pièces jointes images/PDF |

**Conséquence concrète** : la page publique et le discours commercial doivent afficher ces sept
points comme des acquis. Une demande à 223 votes chez le leader est un argument de vente, pas une
ligne de changelog.

**Deux quasi-acquis à finir plutôt qu'à refaire** :
- *Fusionner prévu et réalisé* (34) — le portail athlète a déjà le bascule prévu / réalisé / les
  deux. Il manque la même lecture **côté coach**.
- *Scénarios de semaine d'entraînement* (19) — `MesocycleTemplate` couvre les trois quarts du
  besoin ; il manque le choix entre plusieurs variantes pour une même semaine.

---

## 3. Vague 0 — non négociable, et prioritaire sur tout ce document

Rien de ce qui suit ne se construit avant que les bloquants déjà identifiés soient purgés. Les
ignorer pour ajouter des fonctionnalités reviendrait à empiler du produit sur une prescription
fausse.

| Réf | Point | Pourquoi il bloque ce plan |
|---|---|---|
| B1 | RPE 2–4 affiché sur toutes les séances sans test lactate | Toute la crédibilité physio, donc tout l'argument face à Nolio |
| B3 | L'athlète blessé devient l'athlète le plus alarmant du club | La vague 2 (dossier blessure) construit **dessus** |
| B4 | ACWR calculé sur la durée prescrite, jamais réalisée | Le score de réalisation (vague 1) corrige la même racine |
| B5 | L'athlète ne peut pas dire « je n'ai pas fait la séance » | Idem |
| — | [`PLAN-CONFORMITE-BETA-2026-08.md`](./PLAN-CONFORMITE-BETA-2026-08.md) | La vague 2 touche des données de santé art. 9 ; le socle de consentement doit être sain d'abord |

Détail : [`AUDIT-FONCTIONNEL-2026-08.md`](./AUDIT-FONCTIONNEL-2026-08.md).

---

## 4. Vague 1 — exposer la physiologie qui tourne déjà (le meilleur ratio du plan)

C'est ici que se trouve le rendement le plus élevé : les moteurs existent, testés unitairement.
Ce qui manque est la couche qui les rend visibles.

### 1.1 — Détection automatique de nouvelles valeurs physio · 1270 votes · **effort M**

**Le geste** : après chaque import d'activité, l'application balaie la sortie et **propose** au
coach (jamais n'écrase) : meilleurs efforts 5' / 12' / 20' / 30' / 60', FC max observée, vitesse
critique recalculée sur les deux meilleurs efforts, VDOT déduit d'une performance de référence.
Le coach valide, refuse, ou corrige.

**Pourquoi c'est peu coûteux ici** : `VdotEngine`, `CriticalSpeedEngine`, `LactateThresholdEngine`
et `IntensityDomainEngine` calculent déjà tout cela. Il faut un `ThresholdDetectionEngine` qui les
orchestre sur les tours (`ActivityLap`, déjà stockés) et une file de propositions.

**Garde-fou** : même logique que le test 1RM face au profil (`tested` prévaut sur l'estimé), avec
le garde-fou que l'audit réclame déjà en G9 — une valeur détectée ne remplace jamais une valeur
mesurée sans validation humaine.

**Effet de bord précieux** : c'est la réponse directe à B1. Un athlète sans test lactate finit par
avoir des seuils estimés à partir de ses sorties, au lieu de lire « RPE 2–4 » partout.

### 1.2 — Score de réalisation, bloc par bloc · 37 votes + audits G5/B4 · **effort M**

Aujourd'hui une séance est validée sur le seul volume total : un 10×400 couru à l'allure du
dimanche compte pour 100 %. Le score compare **chaque bloc prescrit** (déjà en fourchettes, via
`SessionCalculatorEngine`) aux **tours réellement relevés** (déjà en base) et sort un % de
conformité par bloc + un score de séance.

Trois audits convergent ici : G5 (validation au volume), B4 (ACWR sur du prescrit), B5 (dire « je
n'ai pas fait la séance »). Un seul chantier, trois corrections.

### 1.3 — Comparer deux ou plusieurs séances · 724 votes · **effort M**

Superposition de 2 à 4 sorties : allure, FC, dénivelé, tours. Les données sont là
(`ActivityLap` + tracé) ; c'est un écran, pas un moteur. Complément direct : *lier des séances
similaires* (15) — le même 5×2000 m sur toute une saison, sur une seule courbe.

### 1.4 — Bilan d'une période choisie, exportable · 539 votes · **effort S/M**

`ProgramExportController` (OpenPDF) exporte déjà le programme. Ici : choisir deux dates et sortir
volumes, répartition par zone (déjà dans `AnalyticsResponse`), charge, tests, compétitions,
blessures. C'est le livrable que le coach envoie à son athlète en fin de cycle — et le document
qui fait exister son travail.

Agrège aussi : *bilan hebdo des qualités développées* (22), *charge par mésocycle* (9),
*impression en paysage* (17).

### 1.5 — Prédiction de performance · 26 votes · **effort S**

`VdotEngine` applique déjà Riegel (`T2 = T1·(D2/D1)^1.06`). Il manque un écran : 5K / 10K / semi /
marathon prédits, et l'écart avec l'objectif A. Quelques heures pour une fonctionnalité que les
athlètes regardent chaque semaine.

### 1.6 — Analyses cardiaques dérivées · ~41 votes · **effort S** (par unité)

HRR (21), dérive cardiaque (10), découplage FC/allure (10) : trois calculs sur des données déjà
stockées. À grouper avec 1.3 dans le même écran d'analyse.

---

## 5. Vague 2 — santé et suivi long terme (le plus gros gisement du marché)

~1 230 votes, et le domaine où DARI Lab a une légitimité que Nolio n'a pas : chiffrement
AES-256-GCM des données de santé et consentement art. 9 déjà en place.

### 2.1 — Dossier de blessure · 1118 votes · **effort L**

Passer de l'indisponibilité déclarative (`Unavailability` + `UnavailabilityReason.INJURY`) à un
véritable suivi : **zone du corps** (carte cliquable), date d'apparition, mécanisme, intensité
dans le temps, soins, **retour progressif à l'entraînement**, historique pluriannuel des
récidives.

C'est **le même chantier que B3** : une blessure déclarée doit suspendre les alertes « séances
manquées », changer la pastille de forme, et cesser d'alimenter le digest matinal.

### 2.2 — HRV / VFC · ~400 votes · **effort M**

Champ dans le check-in matinal (qui prend déjà sommeil / fatigue / douleur), import depuis Strava
et les capteurs, **baseline glissante 7 jours** avec écart-type — la seule lecture qui ait du sens.
Puis alimentation de `FormStatusEngine`, qui n'utilise aujourd'hui que fatigue et douleur.

### 2.3 — Suivi du cycle menstruel · 44 votes · **effort M**

Phase du cycle transmise au coach, croisée avec la charge et la forme. Donnée art. 9 :
**consentement séparé**, chiffrement (déjà en place), visibilité révocable par l'athlète seule.
Peu de votes, fort effet : c'est la moitié des athlètes, et presque aucune plateforme ne le traite
sérieusement.

### 2.4 — Préparation mentale · 43 votes · **effort M**

Objectifs de process, routines, imagerie, notes d'avant-course. Cohérent avec le positionnement
« coaching physiologique **et** préparation », et sans concurrent crédible sur le marché.

---

## 6. Vague 3 — calendrier et pilotage

~940 votes, et c'est l'écran où le coach passe sa journée.

| Chantier | Votes | Effort | Note |
|---|---:|---|---|
| **Vue annuelle par semaine** (dézoomer sur la saison) | 738 | M | Une ligne par semaine, charge et volume en couleur. La vue qui manque à tout le monde |
| **Vue « Plan » / « Cycle »** | 46 | S | Bornes de début/fin de cycle sur le calendrier ; `MesocycleTemplate` porte déjà la donnée |
| Prévu/réalisé fusionné **côté coach** | 34 | S | Le portail athlète le fait déjà |
| **Timeline unique** sur le tableau de bord | 26 | M | Charge, forme, tests, blessures, compétitions sur un seul axe temps |
| Label sous-entraînement / maintien / surcharge | 12 | S | `LoadEngine` sort déjà l'ACWR ; il manque le mot |
| Séances non débriefées repérables sans cliquer | 13 | S | `CoachInbox` + `debrief-prompt` existent ; c'est un état visuel |
| Agenda perso du coach (rendez-vous, todo, rappels) | 13 | M | Le coach n'a aujourd'hui aucun espace à lui |
| Événements/notes sur les courbes de suivi | 9 | S | `CalendarNote` existe déjà |
| Import d'un plan depuis CSV/XLS | 11 | M | **Sous-évalué par les votes** : c'est le chemin de migration *depuis* Nolio ou TrainingPeaks. Levier d'acquisition, pas de confort |

---

## 7. Vague 4 — l'arbitrage multi-sport (~830 votes, décision à prendre, pas à subir)

Le cahier des charges place le multi-sport natif **hors périmètre** (§2.2, phase 4 / W). Le
tableau Nolio montre que c'est pourtant le deuxième gisement de demande. Trois options :

| Option | Contenu | Coût | Risque |
|---|---|---|---|
| **A — Statu quo** | Course à pied + préparation physique | 0 | On laisse ~830 votes de demande, et le triathlon entier, hors de portée |
| **B — Socle multi-sport (recommandé)** | Dimension `sport` sur séances et activités, FC max **par sport**, puissance comme métrique de zone, inclure/exclure des sports des statistiques, logos par discipline | M | Aucun : le modèle de zones est **déjà générique** (`MetricUnit` accepte `W`, `KMH`, `BPM`, `PCT`) |
| **C — Vélo natif complet** | PPR, W′, FTP, TSS/Coggan, route ≠ home trainer, MyWhoosh/Rouvy, natation SWOLF | XL | Dilue le positionnement physiologique course à pied avant qu'il soit établi |

**Recommandation** : B maintenant, C jamais avant que la vague 1 et la vague 2 soient livrées et
que le socle course à pied soit défendable. L'option B coûte peu parce que `MetricType` et
`ZoneSet` ont été conçus génériques — c'est une dette d'architecture déjà payée, autant l'utiliser.

Le trail (75 votes : dénivelé négatif, VMA ascensionnelle, VAP corrigée du D+) est à traiter **avec
la vague 1**, pas ici : c'est de la course à pied, le dashboard sépare déjà Route et Trail, et
`elevationGainM` est déjà en base.

---

## 8. Vague 5 — écosystème et différenciation

| Chantier | Votes | Effort | Pourquoi |
|---|---:|---|---|
| **API publique documentée + serveur MCP** | (Nolio : *en cours*) | M | ~295 endpoints REST et OpenAPI/Springdoc sont **déjà là**. Un serveur MCP au-dessus est un chantier court et un vrai marqueur : Nolio en fait un chantier phare, DARI Lab part avec l'API déjà écrite |
| **Propriété intellectuelle du coach** | 67 | M | Protéger le travail du coach quand l'athlète part chez un concurrent. Personne ne le fait. Argument de rétention **B2B** — et le coach est le client payant |
| Fil d'actualité des séances, côté coach | 114 | M | Voir en un flux ce que le club a réalisé aujourd'hui |
| Synchronisation Google Agenda (disponibilités) | 34 | M | |
| Widget météo (prévisions 7 jours sur les séances) | 15 | S | |
| Calendrier offline / widget d'écran d'accueil | 20 | M | La PWA est déjà *offline-friendly* ; le widget natif ne l'est pas |
| Webhook Strava (au lieu du polling) | — | S | Déjà listé dans les limites connues du README |

---

## 9. Ce qu'on écarte, et pourquoi

Écarter explicitement vaut mieux que laisser traîner.

| Demande | Votes | Décision |
|---|---:|---|
| **Nutrition / MyFitnessPal** | ~780 | **Non.** Hors périmètre au CDC (§2.2, W). C'est un produit entier, pas un module — et le conseil nutritionnel touche à une responsabilité réglementée. Éventuellement plus tard : un simple *lien* vers un outil tiers, jamais un plan alimentaire prescrit |
| **Glycémie (Dexcom / Abbott)** | 15 | **Non.** Dispositif médical, cadre réglementaire disproportionné pour 15 votes |
| **MyWhoosh / Rouvy / home trainer** | 47 | **Reporté**, conditionné à l'option C du §7 |
| **Natation native (SWOLF, eau libre)** | 204 | **Reporté** de même. Une vitesse critique natation seule (23 votes) est possible plus tôt, `CriticalSpeedEngine` étant agnostique |
| Réseau social public type Strava | — | **Non**, conforme au CDC §2.2. Le fil d'actualité **coach** du §8 est autre chose : il est privé et interne au club |

---

## 10. Ordre d'exécution recommandé

1. **Vague 0** — bloquants des audits d'août. Rien d'autre avant.
2. **1.1 détection de valeurs physio** — 1270 votes, moteurs déjà écrits, corrige B1 au passage.
   Si une seule chose est faite dans ce document, c'est celle-là.
3. **1.2 score de réalisation** — corrige G5, B4 et B5 d'un seul geste.
4. **2.1 dossier de blessure** — 1118 votes, et c'est déjà un bloquant (B3).
5. **1.3 comparaison + 1.5 prédiction + 1.6 dérivés cardiaques** — un écran d'analyse, trois
   fonctionnalités, effort faible.
6. **3.1 vue annuelle** — 738 votes, l'écran quotidien du coach.
7. **1.4 bilan de période** — le livrable qui fait exister le travail du coach.
8. **2.2 HRV** — puis 2.3 cycle menstruel, 2.4 préparation mentale.
9. **§7 option B** — socle multi-sport, une fois le socle course à pied défendable.
10. **§8 API + MCP**, puis le reste de la vague 5.

Et, en parallèle et sans attendre : **afficher les sept acquis du §2** dans le discours produit.
Ce sont les seuls points de ce document dont le coût de développement est nul.

---

## 11. Limites de cette analyse

- **Les votes mesurent la demande résiduelle de Nolio, pas le marché.** Ils ne disent rien de ce
  que Nolio fait bien, ni de ce qu'une base d'utilisateurs DARI Lab demanderait.
- **Ils sont biaisés vers les utilisateurs avancés** : ceux qui votent sur un board public sont
  ceux qui poussent l'outil dans ses limites. Le coach qui décroche en semaine 2 ne vote jamais.
- **Le cyclisme est surreprésenté** dans la base Nolio ; le classement le reflète.
- **Aucune estimation de charge n'a été chiffrée en jours** ici. Les tailles S/M/L/XL sont des
  ordres de grandeur relatifs, à instruire chantier par chantier.
- Les états « déjà livré » du §2 ont été vérifiés dans le code au 5 août 2026 ; ils vieilliront.
