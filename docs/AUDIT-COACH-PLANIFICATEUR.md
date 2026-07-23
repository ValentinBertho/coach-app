# Audit ergonomique & plan d'amélioration — Partie « Coach / Planificateur »

> Référence : rendre l'outil aussi clair, rapide et agréable que **Nolio**, voire meilleur.
> Périmètre : navigation coach, catégories de séances, bibliothèques, calendrier / drag & drop.
> **Cette itération = audit + plan. Aucun code produit — en attente de validation.**

Stack confirmée : Angular standalone, signals, `ChangeDetectionStrategy.OnPush`, `@angular/cdk/drag-drop`, design tokens `var(--…)`.

---

## 0. Synthèse express

| # | Axe | État | Verdict |
|---|-----|------|---------|
| 1 | Clarté & navigation | Menu plat 10 entrées ; **mobile = 3 items, 6 destinations inaccessibles** | 🔴 Critique (bug mobile) |
| 2 | Catégories personnalisées | 3 taxonomies figées (`WorkoutType`, `ExerciseCategory`, `RunDrillCategory`), 0 catégorie coach | 🟠 Chantier structurant |
| 3 | Liste séances à gauche par catégorie | Tiroir **à droite**, fermé par défaut, non catégorisé, sans recherche | 🟠 Chantier structurant |
| 4 | Drag & drop généralisé | DnD partiel ; chemin principal = overlay picker ; pas de réordonnancement intra-jour | 🟡 Extension |

**3 problèmes à traiter en priorité absolue (quick wins / bug) :**
1. **Navigation mobile cassée** : 6 pages du coach sont totalement inaccessibles sur mobile (`coach-layout.component.scss:128-131`).
2. **Bibliothèque du calendrier à droite et fermée par défaut** (`calendar.component.ts:409-411`) → à l'opposé de la cible (panneau gauche, ouvert).
3. **Réordonnancement d'une séance dans un même jour impossible** (`calendar.component.ts:543`).

---

## 1. Axe 1 — Clarté & navigation des menus

### 1.1 État actuel (constaté dans le code)

**Desktop — sidebar plate à 10 entrées** (`coach-layout.component.html:20-33`) :

```
Tableau de bord · Athlètes · Groupes · Calendrier · Bibliothèque ·
Éducatifs · Prépa physique · Club · Paramètres · Aide
```

Aucun regroupement, aucun séparateur, aucune hiérarchie visuelle. Les 10 items sont au même niveau. Le rail repliable en icônes existe (`navCollapsed`, persisté en `localStorage`, `coach-layout.component.ts:33-46`) — bon point — mais sans libellés de groupe, un rail de 10 icônes indifférenciées est peu lisible.

**Mobile — bottom-nav à 3 items seulement** (`coach-layout.component.html:74-84`) :

```
Accueil · Athlètes · Calendrier
```

Et surtout (`coach-layout.component.scss:128-134`) :

```scss
@media (max-width: 900px) {
  .desktop-only { display: none !important; }  /* toute la sidebar disparaît */
  .bottom-nav   { display: flex; }             /* seuls 3 items subsistent */
}
```

### 1.2 Frictions

| # | Constat (`fichier:ligne`) | Impact utilisateur | Gravité |
|---|---------------------------|--------------------|---------|
| 1.1 | **Sur mobile, la sidebar entière est masquée et aucun menu de remplacement n'existe** (`coach-layout.component.scss:129`). Groupes, Bibliothèque, Éducatifs, Prépa physique, Club, Paramètres = **6 destinations inaccessibles**. `Aide` survit via la topbar (`:51`). | Un coach sur téléphone ne peut ni ouvrir sa bibliothèque, ni sa prépa physique, ni ses réglages. Blocage fonctionnel. | 🔴 **Haute (bug)** |
| 1.2 | Menu desktop **plat, 10 entrées** sans regroupement (`:20-33`). | Charge cognitive, pas de modèle mental (« où est quoi ? »). Viole *Recognition rather than recall* (Nielsen). | Moyenne |
| 1.3 | **3 bibliothèques distinctes** dans le menu : `Bibliothèque` (course), `Éducatifs`, `Prépa physique` (`:27-29`) → 3 routes (`/templates`, `/run-drills`, `/strength`). | Le coach doit savoir *a priori* dans quel silo ranger/chercher une séance. Parcours fragmenté. | Moyenne |
| 1.4 | Libellé **« Bibliothèque »** ambigu : ne couvre que le course, alors que Éducatifs et Prépa sont *aussi* des bibliothèques. | Confusion sémantique. | Basse |
| 1.5 | `Calendrier` (cœur du métier planif) est noyé en 4ᵉ position, au même poids visuel que `Aide` ou `Club`. | Pas de hiérarchie « actions fréquentes vs secondaires ». | Basse |

### 1.3 Comparatif Nolio

Nolio structure sa navigation en **sections logiques** (Athlètes/Groupes · Planification/Calendrier · Bibliothèque unifiée de contenus · Réglages) et conserve la planification en accès primaire permanent, mobile inclus. L'app actuelle a les briques mais pas la **taxonomie de navigation**.

### 1.4 Architecture de l'information cible

Regrouper les 10 entrées en **4 sections libellées** :

```
COACHING           ← quotidien, accès direct
  Tableau de bord
  Athlètes
  Groupes
  Calendrier

BIBLIOTHÈQUES      ← contenus réutilisables (unifiés à terme, cf. Axe 3)
  Séances course
  Prépa physique
  Éducatifs

CLUB
  Club / staff

RÉGLAGES
  Paramètres
  Aide
```

- Accès direct (toujours visible) : **Tableau de bord, Athlètes, Calendrier**.
- Secondaire (sous en-tête de section, repliable) : le reste.
- **Mobile** : bottom-nav à 4 slots dont un **« Plus »** ouvrant un panneau (sheet) avec la nav complète groupée. Corrige 1.1.

Wireframes en §6.

---

## 2. Axe 2 — Catégories personnalisées pour les séances

### 2.1 État actuel

Trois taxonomies **figées dans le code**, non modifiables par le coach :

| Domaine | Type | Valeurs | Fichier |
|---------|------|---------|---------|
| Course | `WorkoutType` (union) | 10 : ENDURANCE, RECOVERY, TEMPO, THRESHOLD, INTERVALS, LONG_RUN, RACE, STRENGTH, CROSS_TRAINING, REST | `workout.model.ts:1-3` |
| Prépa physique | `ExerciseCategory` | 10 : FORCE_MAX, HYPERTROPHIE, PUISSANCE… | `strength.model.ts:1-3` |
| Éducatifs | `RunDrillCategory` | 2 : TECHNIQUE, AMPLITUDE | `run-drill.model.ts:1` |

Le type course pilote couleur + icône + nature « clé » via `TYPE_META` (`calendar.component.ts:61-72`) et les libellés via `WORKOUT_TYPE_LABELS` (`workout.model.ts:54-65`). À la création d'un modèle, le coach **choisit dans une liste fermée** (`template-list.component.html:12-16`, `template-list.component.ts:36`).

### 2.2 Frictions

| # | Constat (`fichier:ligne`) | Impact | Gravité |
|---|---------------------------|--------|---------|
| 2.1 | Impossible de créer « VMA courte », « Fartlek », « PPG haut du corps »… Le coach doit détourner un type générique (ex. INTERVALS) (`template-list.component.html:12`). | Perte de finesse ; classement approximatif ; recherche difficile. | Moyenne |
| 2.2 | `WorkoutType` sert **deux rôles** : sémantique moteur (couleur, icône, « clé », calcul de conflit `calendar.component.ts:154,169`) ET rangement bibliothèque. Coupler les deux empêche d'ajouter du rangement libre sans toucher au moteur. | Rigidité ; migration risquée si mal séparée. | Moyenne (dette) |
| 2.3 | Trois taxonomies incompatibles → aucune catégorie transverse (ex. « Bloc spécifique 10 km » mêlant course + prépa). | Pas de vision unifiée. | Basse |

### 2.3 Comparatif Nolio

Nolio laisse le coach **taguer / catégoriser librement** ses contenus et filtrer dessus. C'est le principal écart de flexibilité (Nielsen *Flexibility & efficiency*).

### 2.4 Modèle cible : entité `Category`

Nouvelle entité **coexistant** avec les enums (ne rien casser) :

```ts
// core/models/category.model.ts (proposé)
export type CategoryScope = 'COACH' | 'CLUB';
export type CategoryDomain = 'COURSE' | 'STRENGTH' | 'DRILL' | 'ANY';

export interface Category {
  id: string;
  name: string;         // « VMA courte »
  color: string;        // token ou hex ; fallback var(--…)
  icon: string;         // clé IconComponent
  order: number;        // tri manuel (drag, cf. Axe 4)
  scope: CategoryScope; // perso coach ou partagé club
  domain: CategoryDomain;
  builtinType?: WorkoutType | null; // adossement à l'enum existant (migration douce)
}
```

Rattachement : ajouter un `categoryId: string | null` **optionnel** sur `WorkoutTemplate`, `StrengthSession`, `RunDrill` (et `Workout` pour l'affichage calendrier). Optionnel ⇒ aucune rupture.

**Impact front :**
- Modèles TS : `category.model.ts` + champ `categoryId?` sur les 3 modèles de contenu + `Workout`.
- Service : `CategoryService` (CRUD + cache signal), injecté dans calendrier, bibliothèques, éditeurs.
- Formulaires : remplacer/compléter le `<select type>` figé (`template-list.component.html:12-16`) par un sélecteur de catégorie + bouton « + Nouvelle catégorie » (couleur + icône).
- Rendu : `TYPE_META` devient une **fonction** qui lit d'abord `categoryId` (couleur/icône custom), sinon retombe sur l'enum (`calendar.component.ts:61-72,177`). La notion « clé » (charge/conflit) reste portée par le type moteur ou un flag `isKey` sur la catégorie.

**Impact back à signaler :**
- Table `category` (id, club_id, coach_id, name, color, icon, order, scope, domain, builtin_type).
- FK nullable `category_id` sur `workout_template`, `strength_session`, `run_drill`, `workout`.
- Endpoints CRUD `/api/categories` + réordonnancement (`PATCH order`).
- Portée : filtrage par coach/club, héritage club→coach.

**Stratégie de migration (recommandée) — coexistence puis bascule douce :**
1. **Phase A** : `Category` s'ajoute *à côté* de l'enum. Le type moteur reste la source de vérité pour couleur/icône/« clé ». `categoryId` = simple étiquette de rangement/filtre. Zéro régression.
2. **Phase B** : seed automatique d'une `Category` par valeur d'enum (adossée via `builtinType`) → tout contenu existant reçoit une catégorie par défaut, mapping 1:1.
3. **Phase C** : l'UI privilégie `categoryId`, l'enum devient interne (calcul de charge, compat athlète). Ne **pas** supprimer l'enum (rôle moteur + données historiques).

> ⚠️ Ne pas remplacer l'enum : il porte la logique métier (zones, « séance clé », conflit de charge). Les catégories perso sont une **couche de rangement** au-dessus, pas un remplacement.

---

## 3. Axe 3 — Liste des séances en menu déroulant à gauche, par catégorie

### 3.1 État actuel

**Dans le calendrier**, la bibliothèque est un **tiroir à droite, fermé par défaut** :

- `sidebarOpen = signal(false)` (`calendar.component.ts:409-411`).
- Rendu `<aside class="cal-sidebar">` dans `.cal-layout` (`calendar.component.html:123-165`).
- Regroupée en **3 sous-sections fixes** (Course / Force / Éducatifs, `:132-163`) — pas par catégorie, **pas de recherche**, pas de compteurs, pas de favoris/récents.
- Le chemin principal de planification n'est **pas** ce tiroir mais l'**overlay picker** ouvert au clic sur « + » d'un jour (`pickerDate`, `calendar.component.ts:407,420-424` ; `calendar.component.html:260-302`) — qui ne liste que les modèles course.

**Dans les bibliothèques**, chaque page a sa propre UI :
- `template-list` : recherche + filtre par type + switch cartes/liste (`template-list.component.html:29-46`) — plutôt bon, mais isolé.
- `strength` : navigation par **onglets** (exercices/séances/cycles/tests/analyse, `strength.component.ts:26,41`).
- `run-drills` : grille par catégorie, sans recherche (`run-drills.component.ts:48-66`).

### 3.2 Frictions

| # | Constat (`fichier:ligne`) | Impact | Gravité |
|---|---------------------------|--------|---------|
| 3.1 | Bibliothèque du calendrier **à droite** et **fermée par défaut** (`calendar.component.ts:409`). | À l'opposé de la cible (gauche, ouverte). Le coach ne « voit » pas son stock de séances en planifiant. | Moyenne |
| 3.2 | **Aucune recherche/filtre** dans le tiroir calendrier (`calendar.component.html:132-163`). Avec 50+ séances, on scrolle. | Retrouver une séance = lent. Viole *Flexibility & efficiency*. | Moyenne |
| 3.3 | Regroupement par **domaine technique figé** (Course/Force/Éducatifs), pas par catégorie métier. | Pas d'accordéon « VMA », « Seuil », « PPG »… (dépend de l'Axe 2). | Moyenne |
| 3.4 | **Deux chemins concurrents** pour planifier : picker (clic +) et DnD (tiroir). Le picker ne montre que le course, pas la prépa ni les éducatifs (`calendar.component.html:285-299`). | Incohérence ; le coach ne sait pas quel chemin donne accès à quoi. | Moyenne |
| 3.5 | **3 UIs de bibliothèque hétérogènes** (filtre / onglets / grille). | Pas de cohérence (*Consistency & standards*). | Basse |
| 3.6 | Pas de **favoris / récents** exposés, alors que `favorite` et `useCount` existent déjà (`strength.model.ts:116-118`, `PpExercise.favorite/useCount`). | Données présentes mais non exploitées pour accélérer. | Basse |

### 3.3 Comparatif Nolio

Volet gauche **permanent**, catégorisé (accordéons), avec recherche instantanée et glisser vers le calendrier — c'est exactement la cible. L'app a le contenu et le DnD mais le panneau est du mauvais côté, fermé et non catégorisé.

### 3.4 Cible : panneau latéral gauche réutilisable

Composant partagé **`<app-session-library-panel>`** (dans `shared/`), utilisé à la fois par le calendrier et par une page « Bibliothèque unifiée » :

- **Position gauche**, ouvert par défaut sur desktop large ; repliable ; en drawer sur mobile.
- **Accordéons par `Category`** (Axe 2), avec repli mémorisé (signal + `localStorage`, comme `navCollapsed`).
- En-tête : **recherche instantanée** (déjà éprouvée dans `template-list`, `computed filtered()`), filtre par domaine (course/prépa/éducatif), **compteurs** par catégorie.
- Sections épinglées en haut : **★ Favoris** et **🕑 Récents** (via `favorite`/`useCount`).
- Chaque item = **carte draggable** (`cdkDrag`, réutilise `[cdkDragData]`), source unique pour le DnD de l'Axe 4.
- **Unifie les 3 bibliothèques** dans une même arborescence (course + prépa + éducatifs sous accordéons de catégorie), résolvant 1.3 et 3.5.

**Impact front :** nouveau composant `shared/components/session-library-panel/`, alimenté par `CategoryService` + les 3 services existants ; remplace `.cal-sidebar` (`calendar.component.html:123-165`) et sert de socle à une route `/app/library` unifiée. Le picker overlay (`:260-302`) est **conservé comme fallback** (clic + / accessibilité / mobile), mais alimenté par la même source.

---

## 4. Axe 4 — Généraliser le drag & drop

### 4.1 État actuel (CDK déjà en place)

- `cdkDropListGroup` sur `.cal-layout` (`calendar.component.html:123`), `cdkDropList` par jour (`:209`) et par liste biblio (`:133,144,155`).
- `onDrop()` (`calendar.component.ts:505-552`) gère 4 cas depuis la biblio : éducatif → séance technique ad hoc (`:517-520,434-449`), force → planif (`:523-531`), modèle course → planif (`:534-540`), et **déplacement** d'une séance existante jour→jour avec **update optimiste + rollback** (`:547-551`) — bien fait.
- Éditeur de **structure force** : réordonnancement des blocs par DnD (`strength-session-editor.component.html:33-37`, `cdkDrag` + `cdkDragHandle`).

### 4.2 Frictions & manques

| # | Constat (`fichier:ligne`) | Impact | Gravité |
|---|---------------------------|--------|---------|
| 4.1 | **Réordonnancement intra-jour impossible** : `onDrop` sort si même conteneur (`calendar.component.ts:543` `if (event.previousContainer === event.container) return;`). | On ne peut pas ordonner 2 séances d'un même jour (matin/soir). | Moyenne |
| 4.2 | **Pas de duplication par glisser + modificateur** (Alt/Ctrl). Duplication seulement via « Dupliquer la semaine », **désactivée** (`advancedPlanning = false`, `calendar.component.ts:123`). | Geste courant (copier une séance sur un autre jour) absent. | Moyenne |
| 4.3 | Éditeur de **structure course** (`session-editor`) : **aucun DnD** pour réordonner les blocs (aucun `cdkDrag` dans `session-editor.component.html`), contrairement à la force. | Incohérence ; réordonnancement de blocs course impossible. | Moyenne |
| 4.4 | **Feedback visuel pauvre** : pas de placeholder/preview custom ni de mise en évidence des drop zones ; `onLibDrop()` est un no-op (`calendar.component.ts:404`). | Le coach ne voit pas clairement où « ça va tomber ». Viole *Visibility of system status*. | Moyenne |
| 4.5 | **Pas d'alternative clavier** au DnD (le déplacement ne passe que par la souris/tactile). | Accessibilité : DnD = seul chemin pour déplacer/ordonner. | Moyenne (a11y) |
| 4.6 | **Tactile/mobile** : DnD depuis un tiroir droit sur petit écran est peu praticable ; le picker sauve la planif mais pas le déplacement. | DnD peu fiable sur mobile. | Moyenne |
| 4.7 | Le drop de **force** ne fait pas d'update optimiste/rollback comme le course (`calendar.component.ts:523-531` : pas de `error` handler ni de rollback). | Incohérence de robustesse ; en cas d'échec back, pas de retour visuel clair. | Basse |
| 4.8 | Détection du type d'élément par **inspection de champs** (`'structure' in rec`, `rec['category']`, `!('scheduledDate' in rec)`, `:517-534`). | Fragile : dépend de la forme des objets ; un futur champ peut casser le routage. | Basse (dette) |
| 4.9 | Glisser une **semaine / un bloc méso** entier : inexistant (duplication de semaine = bouton, désactivé). | Planification par cycles non gestuelle. | Basse |

### 4.3 Comparatif Nolio

Le DnD est le **geste central** chez Nolio : glisser depuis le volet gauche, déplacer, dupliquer (modificateur), réordonner dans un jour, manipuler des semaines. L'app couvre le « glisser depuis biblio » et le « déplacer jour→jour » ; le reste manque.

### 4.4 Cible

- **Réordonnancement intra-jour** : autoriser le drop même-conteneur (retirer le `return` `:543`) + `moveItemInArray` + persistance d'un `orderIndex` (déjà présent sur `WorkoutStep`, à ajouter au niveau séance/jour côté back).
- **Duplication au glisser** : détecter `event.event.altKey`/`ctrlKey` dans `onDrop` → appeler un `copy` au lieu d'un `reschedule`.
- **Structure course** : ajouter `cdkDrag` + `cdkDragHandle` par bloc dans `session-editor` (aligner sur la force) + `moveItemInArray` sur `structure().main/warmup/cooldown`.
- **Feedback** : `*cdkDragPlaceholder`, `*cdkDragPreview`, classe `.cdk-drop-list-dragging` sur les jours cibles, curseur/halo sur drop zones.
- **A11y clavier** : menu contextuel « Déplacer vers… / Dupliquer vers… » sur chaque carte (chemin non-DnD), + focus visible. Le DnD ne doit jamais être le seul chemin (couvre aussi mobile).
- **Robustesse** : généraliser le pattern optimiste+rollback (`:547-551`) à **tous** les drops (force incluse, 4.7).
- **Méso** (option) : poignée de semaine pour glisser/dupliquer un bloc de 7 jours, une fois `advancedPlanning` réactivé.

---

## 5. Comparatif Nolio synthétique

| Capacité | Nolio | App actuelle | Cible |
|----------|-------|--------------|-------|
| Nav groupée par sections | ✅ | ❌ plate, 10 items | ✅ 4 sections |
| Nav mobile complète | ✅ | ❌ 3 items, 6 pages inaccessibles | ✅ bottom-nav + « Plus » |
| Catégories libres coach | ✅ | ❌ 3 enums figés | ✅ entité `Category` |
| Bibliothèque unifiée | ✅ | ❌ 3 silos | ✅ panneau unique catégorisé |
| Volet séances à gauche | ✅ permanent | ❌ tiroir droit fermé | ✅ panneau gauche accordéons |
| Recherche dans le volet planif | ✅ | ❌ (existe seulement dans `/templates`) | ✅ recherche + filtres + compteurs |
| Favoris / récents | ✅ | ⚠️ données présentes, non exposées | ✅ sections épinglées |
| Glisser biblio → jour | ✅ | ✅ | ✅ (via panneau gauche) |
| Déplacer jour → jour | ✅ | ✅ (optimiste+rollback) | ✅ fiabilisé partout |
| Réordonner dans un jour | ✅ | ❌ (`calendar:543`) | ✅ |
| Dupliquer au glisser (Alt) | ✅ | ❌ | ✅ |
| Réordonner blocs (course) | ✅ | ❌ (force seulement) | ✅ |
| Alternative clavier au DnD | ✅ | ❌ | ✅ menu « Déplacer/Dupliquer vers » |
| Feedback drop zones/preview | ✅ | ⚠️ minimal | ✅ placeholder + preview |

---

## 6. Wireframes (ASCII)

### 6.1 Navigation cible — desktop (déplié / rail)

```
┌────────────────────────┐        ┌──────┐
│ ◎ CoachApp   🔍 🔔      │        │  ◎   │
│ [Club Untel]           │        │      │
├────────────────────────┤        ├──────┤
│ COACHING               │        │ ▦    │  ← Tableau de bord
│  ▦ Tableau de bord      │        │ 👥   │
│  👥 Athlètes            │        │ 👪   │
│  👪 Groupes             │        │ 📅   │  (tooltips au survol)
│  📅 Calendrier          │        ├──────┤
│ BIBLIOTHÈQUES           │        │ 🏃   │
│  🏃 Séances course      │        │ 🏋   │
│  🏋 Prépa physique      │        │ 🎓   │
│  🎓 Éducatifs           │        ├──────┤
│ CLUB                    │        │ 🏢   │
│  🏢 Club                │        ├──────┤
│ RÉGLAGES                │        │ ⚙    │
│  ⚙ Paramètres          │        │ ⛑    │
│  ⛑ Aide                │        └──────┘
├────────────────────────┤
│ Valentin B. · Déconnex. │
└────────────────────────┘
```

### 6.2 Navigation cible — mobile (bottom-nav + sheet « Plus »)

```
   Écran                         Sheet « Plus »
┌───────────────┐             ┌───────────────────┐
│   contenu     │             │  COACHING         │
│               │             │   Groupes         │
│               │             │  BIBLIOTHÈQUES    │
│               │             │   Séances course  │
├───────────────┤   tap ▸     │   Prépa physique  │
│ ▦   👥   📅  ⋯ │  ────────▶  │   Éducatifs       │
│Acc Ath Cal Plus│             │  CLUB · RÉGLAGES  │
└───────────────┘             └───────────────────┘
```

### 6.3 Calendrier + panneau gauche catégorisé (cible)

```
┌──────────────────────┬─────────────────────────────────────────────┐
│ BIBLIOTHÈQUE      🔍  │  [Athlète ▼]  Semaine|Mois  ← Auj →  42 km   │
│ ★ Favoris (3)        ├──────┬──────┬──────┬──────┬──────┬──────┬─────┤
│  ⠿ VMA 10×400        │ Lun  │ Mar  │ Mer  │ Jeu  │ Ven  │ Sam  │ Dim │
│ 🕑 Récents (5)       │      │┌────┐│      │┌────┐│      │      │     │
│ ▾ VMA courte    (4)  │ +    ││VMA ││ +    ││Seuil│ +    │ SL   │ Rep │
│  ⠿ 10×400  ⠿ 30/30   │      ││⠿   ││      │└────┘│      │      │     │
│ ▾ Seuil         (6)  │      │└────┘│      │      │      │      │     │
│  ⠿ Seuil 3×10'       │  ← glisser ⠿ vers un jour, ou réordonner ↕   │
│ ▸ Sortie longue (3)  │                                              │
│ ▸ PPG           (8)  │  (drop zone surlignée pendant le drag)       │
│ ▸ Éducatifs     (12) │                                              │
└──────────────────────┴─────────────────────────────────────────────┘
   (panneau repliable ◂ ; en drawer sur mobile)
```

---

## 7. Plan d'amélioration priorisé

### 7.1 Quick wins (faible effort / fort impact — réalisables tout de suite)

| # | Objectif | Changements front | Back | Effort | Dépend. | Risques |
|---|----------|--------------------|------|--------|---------|---------|
| QW1 | **Réparer la nav mobile** : bottom-nav 4 slots + sheet « Plus » avec nav complète | `coach-layout.component.{html,scss,ts}` : ajouter item « Plus » + panneau signal | — | **S** | — | Faible (isolé au layout) |
| QW2 | **Grouper la nav desktop** en 4 sections libellées | `coach-layout.component.html/scss` : en-têtes de section | — | **S** | — | Faible |
| QW3 | **Bibliothèque calendrier à gauche, ouverte par défaut** sur desktop large | `calendar.component.ts:409` `sidebarOpen(true)` (desktop) ; `.scss` : passer `.cal-sidebar` à gauche | — | **S** | — | Faible (CSS + défaut) |
| QW4 | **Recherche + compteurs** dans le tiroir biblio calendrier | Réutiliser le pattern `filtered()` de `template-list` dans `calendar` | — | **S** | — | Faible |
| QW5 | **Réordonnancement intra-jour** | Retirer `return` `calendar.component.ts:543` + `moveItemInArray` | `orderIndex` séance (PATCH) | **S/M** | — | Moyen (persistance ordre) |
| QW6 | **DnD blocs dans l'éditeur course** (aligner sur la force) | `session-editor.component.html` : `cdkDrag`/`cdkDragHandle` + `moveItemInArray` | — | **S** | — | Faible |
| QW7 | **Feedback DnD** : placeholder + preview + surbrillance drop zone | Templates + `.scss` (`.cdk-drop-list-dragging`) | — | **S** | — | Faible |
| QW8 | **Exposer Favoris / Récents** (données déjà là) | Tri `favorite`/`useCount` dans biblio | — | **S** | — | Faible |

### 7.2 Chantiers structurants

| # | Objectif | Changements front | Back | Effort | Dépend. | Risques |
|---|----------|--------------------|------|--------|---------|---------|
| C1 | **Catégories personnalisées** (`Category`) | `category.model.ts`, `CategoryService`, `categoryId?` sur 3 modèles + `Workout`, formulaires création catégorie, `TYPE_META`→fonction | Table `category`, FK nullable, CRUD `/categories`, réordonnancement | **L** | — | Migration enum (mitigée par coexistence §2.4) |
| C2 | **Panneau gauche unifié réutilisable** `<app-session-library-panel>` | Nouveau composant `shared/`, accordéons par catégorie, recherche/filtres/compteurs/favoris ; branché calendrier + route `/library` | (lecture des 3 sources existantes) | **L** | C1 (catégories pour accordéons) | Refactor du tiroir calendrier ; couverture a11y |
| C3 | **Unifier les 3 bibliothèques** sous une arborescence catégorisée | Route `/app/library`, réutilise C2 ; conserver `/strength` (onglets tests/cycles) tant que non migré | — | **M** | C2 | Ne pas casser les parcours prépa (tests/cycles/1RM) |
| C4 | **DnD avancé** : duplication au glisser (Alt/Ctrl), rollback généralisé, alternative clavier (« Déplacer/Dupliquer vers… »), méso au glisser | `calendar.component.ts` `onDrop` (modificateurs, menu contextuel), robustesse | `copy` séance (endpoint), `orderIndex` | **M/L** | QW5 | Complexité tactile ; a11y |

### 7.3 Séquencement en phases

```
Phase 1 — Nav & quick wins        [QW1→QW8]   effort S/M, aucun back lourd
  Justif : QW1 corrige un bug bloquant mobile ; gains visibles immédiats,
           risque quasi nul, aucune dépendance. Débloque la confiance.

Phase 2 — Catégories perso         [C1]        effort L, back requis
  Justif : socle de données de tout le reste (accordéons du panneau,
           filtres). Stratégie de coexistence = zéro régression.

Phase 3 — Panneau gauche unifié    [C2 → C3]   effort L puis M
  Justif : dépend des catégories (Phase 2) pour les accordéons.
           Réutilise recherche/favoris des quick wins.

Phase 4 — DnD avancé               [C4]        effort M/L
  Justif : s'appuie sur le panneau (source unique de drag) et sur
           le réordonnancement livré en Phase 1 (QW5).
```

**Ordre justifié** : on livre d'abord le bug mobile et les gains sans back (Phase 1), puis la donnée fondatrice (catégories, Phase 2), qui conditionne le panneau catégorisé (Phase 3), lui-même prérequis d'un DnD central et cohérent (Phase 4). Chaque phase est indépendamment livrable et ne casse rien.

---

## 8. Bugs / frictions avérés dans le code (vs. améliorations)

**Bugs / frictions confirmés (à corriger) :**
- 🔴 Nav mobile : 6 destinations inaccessibles (`coach-layout.component.scss:128-131`).
- 🟠 Réordonnancement intra-jour bloqué (`calendar.component.ts:543`).
- 🟠 Éditeur course sans DnD alors que la force en a (`session-editor.component.html` vs `strength-session-editor.component.html:33-37`).
- 🟡 Drop force sans rollback optimiste, contrairement au course (`calendar.component.ts:523-531` vs `547-551`).
- 🟡 Deux chemins de planif incohérents ; picker limité au course (`calendar.component.html:285-299`).
- 🟡 Détection de type par forme d'objet, fragile (`calendar.component.ts:517-534`).

**Améliorations proposées (pas des bugs) :** catégories perso, panneau gauche unifié, recherche dans le tiroir, favoris/récents, duplication au glisser, alternative clavier, feedback drop zones, méso au glisser.

---

## 9. Contraintes respectées

- **Stack** : tout se fait en standalone + signals + OnPush + CDK DragDrop (déjà en place).
- **Non-régression** : `categoryId` optionnel, enums conservés, picker gardé en fallback, phases indépendantes.
- **Design system** : couleurs via tokens `var(--…)` (comme `TYPE_META`), composants dans `shared/`.
- **A11y & mobile** : QW1 (nav mobile), alternative clavier au DnD (C4), panneau en drawer mobile, focus visibles — traités à chaque axe.

---

*Prochaine étape : validation de ce plan avant toute implémentation. Suggestion de démarrage : Phase 1 (QW1 en tête, correctif du bug de navigation mobile).*
