# Proposition — Zones / métriques & éditeur de séance (parité Nolio, en mieux)

> **Statut : proposition à valider. Aucune ligne de code avant ton accord.**
> Périmètre : (A) le modèle de zones/métriques, (B) son application sur la fiche athlète,
> (C) son application dans la prescription de séance, (D) la refonte de l'éditeur de séance.
> Référence à égaler puis dépasser : **Nolio**.

---

## 0. Résumé exécutif (TL;DR)

**Ce que fait Nolio, en une phrase :** le coach configure **une fois** *comment* calculer les zones
(un modèle : « % de la VMA », « Vitesse Critique », « % FC max »…), et Nolio **dérive et recalcule
automatiquement** les fourchettes de chaque athlète à partir de ses valeurs de référence (VMA, FC max,
VC, PMA). Le coach ne saisit (presque) jamais de chiffres à la main.

**Ce que j'ai déjà construit (Z1–Z4) :** un système très proche — catalogue de métriques, zones du
club, valeurs **par athlète** (min/max, source AUTO/MANUAL, verrou), pré-remplissage automatique
depuis le moteur physio (VDOT/seuils), prescription **par zone** dans l'éditeur, lecture directe des
cibles. **C'est un socle solide.** Mais **3 écarts** le séparent de Nolio :

1. **La règle de calcul est codée en dur** (une `Map` Java `nom de zone → référentiel + %`), donc
   ni visible ni éditable par le coach, et fragile (dépend du **nom** de la zone).
2. **Pas de recalcul automatique** quand une valeur de référence de l'athlète change (VMA, FC max,
   nouveau chrono → VDOT). Il faut cliquer « Resynchroniser ».
3. **Les ancres de référence ne sont pas mises en avant** : le coach ne voit pas clairement *d'où*
   viennent les fourchettes (« cette allure = 95 % de ta VMA »).

**Ma proposition « en mieux » :** rendre la règle de calcul **une donnée éditable** (ancre + %/formule,
par zone et par métrique), **recalculer automatiquement** à chaque changement d'ancre, tout en
**gardant mes atouts que Nolio n'a pas** : la **zone unifiée** (une prescription = allure **et** FC
**et** RPE d'un coup, au lieu de 2 échelles séparées à mapper mentalement) et le **verrou par valeur**
(épingler la cible d'un athlète précis sans figer tout le reste).

Et pour l'**éditeur de séance** : le simplifier réellement façon Nolio — blocs en cartes claires,
**groupes de répétitions** visuels (le `×N` et sa récup dans un même cadre), cibles lues en évidence,
et masquage par défaut de tout ce qui est secondaire (aperçu calculé détaillé, éducatifs, bootstrap).

👉 **6 décisions à valider en §5** avant que je code quoi que ce soit.

---

## 1. Audit Nolio (détaillé)

### 1.1 Le modèle de zones

D'après les captures et le fonctionnement de Nolio :

| Élément Nolio | Détail |
|---|---|
| **Zones par dimension de métrique** | Onglets **Cardiaque** / **Allure** / **+**. Chaque dimension a **sa propre échelle** ordonnée et indépendante. |
| **Échelle allure = 13 zones** | Footing récup · EF · Steady · Seuil 1 · Tempo · Seuil 2 bas · Seuil 2 haut · 10k · 5k · 3k · 1500m · 800m · 400m. Très granulaire (jusqu'aux allures de compétition). |
| **Fourchettes affichées** | ex. `5:33 – 4:46 min/km`, `4:46 – 4:10`… Contiguës (le haut d'une zone = le bas de la suivante). |
| **Calculées depuis un modèle** | « **Configuration utilisée : Course à pied – VC** » → les allures sont **dérivées de la Vitesse Critique**. Autres modèles possibles (VMA, seuils…). |
| **Recalcul automatique** | *« Les zones sont recalculées dès qu'une valeur utilisée dans le calcul est mise à jour (FC Max, PMA, VMA..) »*. |
| **Multi-sport** | Bouton « tout sport » : zones communes à tous les sports **ou** spécifiques. |
| **Personnalisation** | Bouton « Personnaliser » : le coach ajuste le modèle/les bornes. |
| **Unité explicite** | « Unité : min/km ». |

**À retenir :** chez Nolio, une zone n'est **pas** une valeur saisie, c'est le **résultat d'un calcul**
`f(modèle, valeurs de référence de l'athlète)`. Le coach paramètre le *modèle*, pas les nombres.

### 1.2 Application sur l'athlète

- L'athlète porte des **valeurs de référence** (« ancres ») : **VMA**, **FC max**, **VC** (vitesse
  critique), **PMA** (puissance, pour le vélo), seuils lactiques…
- Nolio combine `ancres × modèle` → **fourchettes concrètes par zone** pour **cet** athlète.
- Mise à jour d'une ancre (ex. nouveau test VMA) → **toutes les zones dépendantes se recalculent**.

### 1.3 Application en séance (prescription)

- Un bloc de séance est prescrit **par zone** (« 6 × 1000 m en **VO2** »).
- Nolio affiche la **cible concrète** de l'athlète pour cette zone (allure + FC), dérivée de ses zones.
- À l'exécution, Nolio calcule le **temps passé dans chaque zone** à partir du flux FC / puissance de
  la montre (capture 3 : barre « Zones d'entraînement · Allure » 19 % / 39,3 %…).

### 1.4 Le builder de séance Nolio (capture 3)

- Séance **structurée** : Échauffement → Corps → Récupération, avec des **groupes de répétitions**
  encadrés : `14×` { 400 m *Corps de séance* + 100 m *Récupération* }, puis `4×` { 400 m … }.
- Chaque bloc affiche : **allure** (`03:28 – 03:16 min/km`) · **vitesse** (`17.31 – 18.37 km/h`) ·
  **distance/temps** (`01:18 – 01:23`), plus un **commentaire coach** par bloc (« 1'13-1'14, voir plus
  vite si ok pour toi »).
- Actions : **« Séance structurée envoyée vers Coros app »** (export montre), **« Copier dans le
  réalisé »**, réactions emoji, « Poser une question ».
- Visuel épuré : peu de contrôles visibles, hiérarchie claire, les cibles sont **lues** (pas saisies).

### 1.5 Temps-en-zone (réalisé)

- Nécessite le **flux** FC / allure / puissance **par seconde** de l'activité importée.
- Nolio parse ce flux + les zones de l'athlète → répartition du temps par zone.

---

## 2. État actuel du dépôt (ce que j'ai construit en Z1–Z4)

### 2.1 Modèle de données

```
MetricType (catalogue)            TrainingZone (club)              AthleteZoneValue (par athlète)
  code PACE/HR/SPEED/               name, color, description         athlete × zone × metric
  PCT_HRMAX/POWER/RPE               sortOrder, scope, discipline     valueMin / valueMax
  unit/format/direction             is_builtin                       source AUTO | MANUAL
  global ou custom club             ZoneMetric[] (métriques portées) locked
                                    seed 6 zones (PACE+HR chacune)   UNIQUE(athlete,zone,metric)
```

- **Prescription** (Z3) : `CoursePrescription.zoneId` → cible **lue** directement depuis
  `AthleteZoneValue` (plus de base × %). Chemin legacy `ref + %` conservé pour les snapshots figés.
- **Pré-remplissage** : `ZoneValueSyncService` calcule les valeurs AUTO via une **table figée** :

  ```java
  "Récupération"          → PCT_LT1  60–72 %
  "Endurance fondamentale"→ PCT_LT1  80–92 %
  "Marathon"              → PCT_LT1  95–102 %
  "Seuil"                 → PCT_LT2  96–103 %
  "VO2"                   → PCT_VC   100–107 %
  "Anaérobie / Sprint"    → PCT_PACE_800M 98–110 %
  ```

  Le moteur (`SessionCalculatorEngine`) applique ces `réf + %` aux allures VDOT / seuils de l'athlète.
- **Ancres de l'athlète** déjà présentes : `hrMax`, `hrRest`, `vma`, `lt1Ms`, `lt2Ms`, `vcMs`, `fcLt1`,
  `fcLt2`, `fcDomain1/2Pct`, `vdot`, + `AthleteVdotPace` (allures 800 m → marathon dérivées du VDOT).
- **Déclenchement** : resync **manuel** (bouton) ou **paresseux** au premier affichage.

### 2.2 Ce qui marche déjà (à conserver — Nolio ne fait pas tout mieux)

- ✅ **Zone unifiée** : une zone porte **plusieurs** métriques (allure **+** FC). Prescrire « Seuil »
  donne les deux cibles d'un coup. **Nolio oblige à raisonner en 2 échelles séparées.**
- ✅ **Valeurs par athlète** avec **source AUTO/MANUAL** + **verrou par valeur** : on peut épingler
  « 4:05/km au seuil » pour Marie sans figer les autres zones ni les autres athlètes.
- ✅ **Prescription par zone** dans l'éditeur, cible en lecture (Z3), + migration douce du legacy (Z4).
- ✅ **Catalogue de métriques extensible** (ajouter Puissance, %FCmax… = données, pas refonte).
- ✅ **Écran club en table ordonnée** (n° · zone · métriques · description) + écran athlète (tableau
  zones × métriques, édition, verrou, resync).

### 2.3 Écarts vs Nolio (les vrais manques)

| # | Écart | Impact |
|---|-------|--------|
| **E1** | **Règle de calcul codée en dur** (`Map` Java, clé = **nom** de zone) | Le coach ne peut pas la voir ni l'éditer ; une zone renommée ou custom n'a **aucun** pré-remplissage ; non transparent (« d'où sort 4:05 ? »). |
| **E2** | **Pas de recalcul automatique** sur changement d'ancre (VMA, FC max, nouveau chrono) | Les zones se désynchronisent silencieusement ; il faut penser à cliquer « Resynchroniser ». |
| **E3** | **Ancres de référence peu visibles** sur la fiche athlète | Le coach ne comprend pas le lien ancre → zone ; pas de « source de vérité » claire. |
| **E4** | **Un seul modèle** (VDOT/seuils) implicite | Pas de choix explicite VMA vs VC vs seuils vs %FCmax ; pas de bornes contiguës façon échelle. |
| **E5** | **Granularité fixe (6 zones)** | Pas d'échelle fine allures de compétition (5k/3k/1500/800/400) comme les 13 zones Nolio. |
| **E6** | **Éditeur de séance encore dense** (voir §3.7) | Malgré Z3, trop d'éléments à l'écran (aperçu calculé complet, éducatifs, bootstrap, sélecteur d'athlète) → perçu « complexe et pas clair ». |
| **E7** | **Temps-en-zone réalisé absent** | Pas de flux FC/allure stocké (résumés seulement). Chantier data séparé. |

---

## 3. Modèle cible proposé (mieux que Nolio)

### 3.1 Principe directeur

> **Une zone n'est plus « une valeur qu'on saisit », mais « une règle qu'on configure ».**
> La règle vit au niveau du club (transparente, éditable) ; la **valeur concrète** de chaque athlète
> en est **dérivée automatiquement** et reste **surchargeable/verrouillable** au cas par cas.

On **garde la zone unifiée** (atout §2.2) et on lui ajoute, **par métrique**, une **règle de calcul**
explicite : *quelle ancre* et *quel pourcentage/formule*. Résultat : le meilleur des deux mondes —
la puissance de calcul de Nolio **+** la simplicité de prescription de mon modèle.

### 3.2 Entités (évolution, additive)

```
TrainingZone (inchangée)         ZoneMetricRule  ← NOUVEAU (remplace la Map figée)
  name, color, description         zone_id × metric_type_id
  sortOrder, scope, discipline     anchor      (VMA | VC | LT1 | LT2 | VDOT_5K | FCMAX | LTHR | …)
                                   low_pct / high_pct         (bornes en % de l'ancre)
AthleteZoneValue (inchangée)       formula     (PCT_OF_ANCHOR par défaut ; extensible)
  valueMin/valueMax                is_builtin
  source AUTO | MANUAL             → « Endurance · Allure = 80–92 % de la VMA »
  locked

AthleteReference  ← NOUVEAU (ancres, façade claire des champs physio existants)
  athlete_id · anchor · value · source (TEST | MANUAL | DERIVED) · measured_at
  (VMA, FC max, VC, LT1, LT2, VDOT… — s'appuie sur les colonnes Athlete déjà là)
```

- **`ZoneMetricRule`** rend E1/E4 obsolètes : la correspondance `zone × métrique → (ancre, %)` devient
  une **donnée** seedée **et** éditable. Une zone custom peut avoir sa règle ; plus de dépendance au nom.
- **`AthleteReference`** (E3) : une façade lisible des ancres (déjà stockées sur `Athlete`), avec la
  **provenance** (test / manuel / dérivé) et la date. C'est *la* source de vérité affichée au coach.
- **`AthleteZoneValue`** ne change pas : toujours la valeur concrète, mais désormais **recalculée**
  depuis `ZoneMetricRule × AthleteReference` au lieu de la `Map` figée.

### 3.3 Calcul & recalcul automatique (E2)

- `ZoneValueSyncService` v2 : pour chaque `(zone, métrique)`, lit la **règle** (`anchor`, `low/high pct`,
  `formula`), lit l'**ancre** de l'athlète, calcule `[min, max]`. Plus de `Map` figée, plus de
  dépendance au nom de la zone.
- **Recalcul auto** : on branche la resync (valeurs AUTO **non verrouillées** uniquement) sur les
  points où une ancre change — mise à jour physio, nouveau chrono/performance (recalcul VDOT),
  nouveau test lactate/VC. → parité E2 avec Nolio, sans bouton.
- Le **verrou** et la **source MANUAL** restent intouchables au recalcul (mon atout > Nolio).

### 3.4 UX — écran Zones (club)

- Onglet **par métrique** façon Nolio (Allure / FC / +) **en complément** de la table unifiée : on
  bascule pour voir/éditer les **règles** d'une métrique (ancre + %) zone par zone.
- Colonne **« Définition »** : `95–102 % · VMA` éditable inline (menu d'ancre + deux %).
- **Aperçu** : un petit sélecteur « athlète témoin » montre les fourchettes concrètes résultantes
  (comme « Configuration utilisée » chez Nolio), pour valider le paramétrage d'un coup d'œil.
- **Bornes contiguës** optionnelles (E4/E5) : mode « échelle » où le haut d'une zone = bas de la
  suivante, pour une échelle fine type Nolio (jusqu'aux allures 5k/3k/1500/800/400 si souhaité).

### 3.5 UX — fiche athlète

- **Bloc « Références »** en tête (E3) : VMA, FC max, VC, LT1/LT2, VDOT — éditables, avec provenance
  (test / manuel) et date. C'est ce qui **pilote** tout le reste.
- **Tableau zones × métriques** (déjà là) : chaque cellule montre la valeur **dérivée**, un badge
  **AUTO 🔄 / MANUAL ✎ / verrou 🔒**, et **au survol : la règle** (« 95–102 % de la VMA = 3:35–3:45 »).
- Changement d'une référence → **recalcul instantané** visible (les AUTO bougent, les 🔒 restent).
- CTA « Resynchroniser » conservé (utile après un ajustement de règle côté club), mais **plus
  obligatoire** au quotidien.

### 3.6 Prescription en séance

- Inchangée sur le principe (Z3) : bloc = **type · volume · zone**, cible **lue** depuis
  `AthleteZoneValue`.
- Amélioration : la cible affichée peut montrer **allure + vitesse + FC** (déjà ajouté sur la fiche
  séance) et, au survol, **la règle** (traçabilité « pourquoi cette allure »).

### 3.7 Nouvel éditeur de séance — plus simple et intuitif (façon Nolio, en mieux)

**Constat (E6) :** l'éditeur actuel, même après Z3, empile à l'écran : sélecteur « calculateur pour »,
bandeau de complétude du profil + saisie de chrono (bootstrap), presets, puis **par bloc** :
poignée · type · reps · bascule mesure · champ · pastille zone · sélecteur zone · suppression,
**+** un aperçu calculé complet (allure/vitesse/FC/RPE/volume) **+** une rangée d'éducatifs.
→ beaucoup d'informations simultanées, densité forte.

**Cible — un builder guidé et épuré :**

1. **Sections claires** Échauffement · Corps · Retour au calme (conservé).
2. **Groupes de répétitions** façon Nolio : un bloc à `reps > 1` s'affiche dans un **cadre `×N`** qui
   **englobe sa récupération** (l'effort **et** sa récup dans le même bloc visuel), au lieu de deux
   lignes séparées.
3. **Carte de bloc minimale** : `[type] [ ×N · volume (toggle dist/durée) ] [ ● zone ]` + une cible
   **lue en petit** dessous (`3:35–3:45/km · 178–185 bpm`). 3 contrôles, pas 7.
4. **Secondaire replié par défaut** : l'aperçu détaillé (vitesse, RPE), les éducatifs et le
   commentaire par bloc passent derrière un **« ⋯ / Détails »**. On ne les voit que si on les demande.
5. **Commentaire coach par bloc** (parité Nolio) : champ optionnel « 1'13-1'14, plus vite si ok ».
6. **Ajout guidé** : gros boutons `+ Intervalles` · `+ Seuil` · `+ Endurance` · `+ Bloc` avec zone
   **pré-sélectionnée par type** (déjà en place), pour construire une séance en quelques clics.
7. **Sélecteur d'athlète / bootstrap** : sortis du flux principal (repliés en haut), car en mode
   **modèle** on prescrit des **zones** (les cibles concrètes apparaissent à la planification sur un
   athlète). Zéro cul-de-sac : zone non renseignée → chip cliquable → fiche athlète.
8. **Total de séance** (durée · distance estimées) conservé en pied, discret.
9. *(Optionnel, plus tard)* **export montre** (« envoyer vers Garmin/Coros ») façon Nolio.

Résultat : un écran qui **ressemble à Nolio** (blocs encadrés, cibles lues, peu de contrôles) mais qui
**reste supérieur** grâce à la prescription par zone unifiée (une zone = allure **+** FC).

### 3.8 En quoi c'est « mieux que Nolio »

1. **Prescription atomique** : une zone porte **toutes** ses métriques → le coach prescrit un concept
   (« Seuil »), pas deux échelles à croiser mentalement.
2. **Transparence de la règle** : `95–102 % · VMA` est **affiché et éditable**, pas enfoui dans une
   config opaque.
3. **Verrou fin par valeur** : épingler une cible d'athlète précise sans figer le reste (Nolio
   recalcule tout, ou rien).
4. **Extensibilité métrique** native (Puissance, %FCmax, RPE…) sans refonte.
5. **Traçabilité en séance** : au survol d'une cible, « d'où elle vient » (règle + ancre).

---

## 4. Plan de mise en œuvre (phases, additif & réversible)

| Phase | Contenu | Effort | Risque |
|---|---|---|---|
| **V2-1** | `ZoneMetricRule` (entité + migration + seed des règles standard) ; `ZoneValueSyncService` v2 **lit les règles** au lieu de la `Map` (comportement identique, mais data-driven). Non-régression. | M | Faible (additif) |
| **V2-2** | **Recalcul automatique** : brancher la resync (AUTO non verrouillées) sur maj physio / nouveau chrono / test. | S | Faible |
| **V2-3** | **Fiche athlète — bloc Références** (ancres visibles + provenance) + tooltip « règle » sur chaque cellule. | M | Faible |
| **V2-4** | **Écran Zones club — éditeur de règles** (onglets par métrique, colonne Définition ancre + %, aperçu athlète témoin). | M | Faible |
| **V2-5** | **Refonte éditeur de séance** (§3.7 : groupes ×N encadrés, carte minimale, secondaire replié, commentaire par bloc). | M/L | Moyen (écran très utilisé → tests + captures) |
| **V2-6** *(option)* | Échelle fine / bornes contiguës + zones de compétition (5k…400m) ; multi-sport « tout sport ». | M | Faible |
| **V2-7** *(option, data)* | Ingestion des **flux** d'activité → **temps-en-zone réalisé** (barre Nolio côté réalisé). | L | Élevé (dépend des API montres) |

Contraintes respectées : migrations Liquibase additives/réversibles ; moteur historique conservé
comme socle ; snapshots figés immuables ; AUTO/MANUAL/verrou préservés ; budget CSS par composant.

---

## 5. Décisions à valider (avant de coder)

1. **Zone unifiée vs échelles séparées.** Je garde la **zone unifiée** (une zone = allure + FC + …),
   qui est plus simple à prescrire que les échelles séparées de Nolio — d'accord ? (Recommandé.)
2. **Modèle de règle.** `ancre + %` **par zone et par métrique** (ex. Allure = %VMA, FC = %FCmax),
   transparent et éditable — ça te va, ou tu veux des modèles nommés façon Nolio (« VC », « VMA »,
   « Daniels ») en plus ?
3. **Ancres à surfacer** sur la fiche athlète : VMA, FC max, VC, LT1/LT2, VDOT. Il en manque / en trop ?
4. **Granularité** : je garde 6 zones par défaut, avec une **option** d'échelle fine (zones de
   compétition 5k/3k/1500/800/400) — ou tu veux les 13 zones façon Nolio d'emblée ?
5. **Recalcul auto** : je le déclenche sur maj physio + nouveau chrono + test. OK pour ces
   déclencheurs (silencieux, seules les AUTO non verrouillées bougent) ?
6. **Éditeur de séance** : je pars sur la refonte §3.7 (groupes ×N encadrés, carte à 3 contrôles,
   détails repliés, commentaire par bloc). Priorité **haute** (tu l'as pointé), à faire **avant** ou
   **après** le moteur de règles (V2-1→V2-4) ?

*Dis-moi tes réponses (ou « pars sur tes recommandations ») et je lance l'implémentation phase par
phase, avec build + tests + captures à chaque étape.*
