# Audit ergonomique & plan d'amélioration — Partie « Coach / Planificateur »

> Référence : rendre l'outil aussi clair, rapide et agréable que **Nolio**, voire meilleur.
> Périmètre : navigation coach, catégories de séances, bibliothèques, calendrier / drag & drop.
> **Cette itération = audit + plan. Aucun code produit — en attente de validation.**

Stack confirmée : Angular standalone, signals, `ChangeDetectionStrategy.OnPush`, `@angular/cdk/drag-drop`, design tokens `var(--…)`.

---

## 0. Posture de conception : **Desktop-first**

**Un coach planifie sur Desktop.** C'est là que le métier se fait : grand écran, souris (donc drag & drop naturel), clavier (donc raccourcis et saisie rapide), largeur suffisante pour afficher **panneau de séances + calendrier côte à côte** en permanence. C'est le poste de travail que Nolio optimise en premier, et c'est celui que nous devons rendre excellent.

Conséquences directes sur cet audit :
- On **optimise le poste desktop** : densité, permanence du panneau gauche, DnD souris fluide, raccourcis clavier, menus contextuels (clic droit), survol.
- Le **mobile est un usage de consultation / ajustement ponctuel** (vérifier la semaine, déplacer une séance en déplacement), **pas** un poste de construction de plan. Ses lacunes restent à corriger, mais **en priorité secondaire**.
- L'**accessibilité clavier** n'est pas qu'une case a11y : sur desktop c'est un **accélérateur pour power-user** (Nolio en joue beaucoup).

> Révision par rapport à la V1 de l'audit : la nav mobile incomplète était classée « critique ». En posture desktop-first, elle **n'est plus bloquante** (le coach ne planifie pas sur mobile) → reclassée priorité moyenne, dans un chantier « consultation mobile » distinct.

---

## 1. Synthèse express

| # | Axe (desktop-first) | État | Verdict |
|---|---------------------|------|---------|
| 1 | Clarté & navigation desktop | Menu **plat, 10 entrées**, sans regroupement ni hiérarchie (`coach-layout.component.html:20-33`) | 🟠 Prioritaire |
| 2 | Catégories personnalisées | 3 taxonomies figées (`WorkoutType`, `ExerciseCategory`, `RunDrillCategory`), 0 catégorie coach | 🟠 Chantier structurant |
| 3 | Panneau de séances **à gauche**, permanent, par catégorie | Tiroir **à droite**, **fermé par défaut**, non catégorisé, sans recherche (`calendar.component.ts:409-411`) | 🔴 **Cœur du poste desktop** |
| 4 | Drag & drop souris généralisé | DnD partiel ; chemin principal = overlay picker ; pas de réordonnancement intra-jour ni de raccourcis | 🟠 Extension |
| 5 | Consultation mobile | 6 destinations inaccessibles (`coach-layout.component.scss:128-131`) | 🟡 Secondaire (hors planif) |

**Priorités du poste de planification desktop (dans l'ordre) :**
1. **Panneau de séances à gauche, permanent et catégorisé** (Axe 3) — c'est le geste central de la planif desktop, aujourd'hui à droite/fermé/non catégorisé.
2. **Réordonnancement + DnD souris complet** (Axe 4) — réordonner dans un jour (`calendar.component.ts:543`), dupliquer au glisser, réordonner les blocs course.
3. **Navigation desktop groupée** (Axe 1) + **raccourcis clavier** de planification.
4. **Catégories perso** (Axe 2) — socle de données du panneau catégorisé.

---

## 2. Axe 3 (prioritaire) — Panneau de séances à gauche, permanent, par catégorie

*Placé en tête car c'est le cœur ergonomique du poste desktop.*

### 2.1 État actuel

Dans le calendrier, la bibliothèque est un **tiroir à droite, fermé par défaut** :

- `sidebarOpen = signal(false)` (`calendar.component.ts:409-411`), ouvert seulement via un bouton « Bibliothèque » de la toolbar (`calendar.component.html:43-46`).
- Rendu `<aside class="cal-sidebar">` **après** le calendrier dans le flux (`calendar.component.html:123-165`) — à droite.
- Regroupée en **3 sous-sections techniques figées** (Course / Force / Éducatifs, `:132-163`), **pas** par catégorie métier, **sans recherche**, sans compteurs, sans favoris/récents.
- Le chemin principal de planification n'est **pas** ce tiroir mais un **overlay picker** ouvert au clic sur « + » d'un jour (`pickerDate`, `calendar.component.ts:407,420-424` ; `calendar.component.html:260-302`), qui ne liste **que les modèles course**.

### 2.2 Frictions

| # | Constat (`fichier:ligne`) | Impact sur le poste desktop | Gravité |
|---|---------------------------|------------------------------|---------|
| 3.1 | Panneau **à droite** et **fermé par défaut** (`calendar.component.ts:409`) alors que le desktop a la largeur pour l'afficher en permanence à gauche. | Le coach ne « voit » pas son stock de séances en planifiant ; il faut un clic pour ouvrir, et le geste de drag va de droite à gauche (contre-intuitif). | 🔴 Haute |
| 3.2 | **Aucune recherche/filtre** dans le tiroir (`calendar.component.html:132-163`). Avec 50+ séances → scroll. | Retrouver une séance = lent. Viole *Flexibility & efficiency*. La recherche existe pourtant déjà dans `/templates` (non réutilisée). | Haute |
| 3.3 | Regroupement par **domaine technique** (Course/Force/Éducatifs), pas par **catégorie** (« VMA », « Seuil », « PPG »…). | Pas d'accordéon métier ; dépend de l'Axe 2. | Moyenne |
| 3.4 | **Deux chemins concurrents** : picker (clic +) vs DnD (tiroir). Le picker ne montre que le course (`calendar.component.html:285-299`). | Incohérence ; le coach ne sait pas quel chemin donne accès à quoi. | Moyenne |
| 3.5 | **Favoris / récents non exposés**, alors que `favorite`/`useCount` existent (`strength.model.ts:116-118`). | Données présentes non exploitées pour accélérer. | Basse |
| 3.6 | **3 UIs de bibliothèque hétérogènes** (filtre `/templates` · onglets `/strength` · grille `/run-drills`). | Pas de cohérence (*Consistency & standards*). | Basse |

### 2.3 Comparatif Nolio

Volet gauche **permanent**, catégorisé (accordéons), recherche instantanée, glisser vers le calendrier. L'app a le contenu et le DnD mais le panneau est du mauvais côté, fermé et non catégorisé.

### 2.4 Cible : `<app-session-library-panel>` (composant `shared/`)

- **Position gauche**, **ouvert par défaut** sur desktop large ; repliable (préférence persistée, comme `navCollapsed` `coach-layout.component.ts:33-46`) ; en drawer sur mobile seulement.
- **Accordéons par `Category`** (Axe 2), repli mémorisé par catégorie.
- En-tête : **recherche instantanée** (réutiliser le `computed filtered()` de `template-list.component.ts:49-59`), filtre par domaine, **compteurs** par catégorie.
- Sections épinglées : **★ Favoris** et **🕑 Récents** (via `favorite`/`useCount`).
- Chaque item = **carte draggable** (`cdkDrag` + `[cdkDragData]`) → source **unique** du DnD (Axe 4).
- **Unifie les 3 bibliothèques** (course + prépa + éducatifs) sous les mêmes accordéons ; sert aussi de socle à une route `/app/library`.
- Le picker overlay est **conservé en fallback** (clic +, clavier, mobile), alimenté par la même source.

**Impact front :** nouveau composant `shared/components/session-library-panel/` alimenté par `CategoryService` + les 3 services existants ; remplace `.cal-sidebar` (`calendar.component.html:123-165`). **Back :** lecture des sources existantes ; catégories via Axe 2.

---

## 3. Axe 4 — Drag & drop souris généralisé (+ raccourcis clavier)

*Le DnD souris est le geste desktop par excellence : on le fait le geste central.*

### 3.1 État actuel (CDK déjà en place)

- `cdkDropListGroup` sur `.cal-layout` (`calendar.component.html:123`), `cdkDropList` par jour (`:209`) et par liste biblio (`:133,144,155`).
- `onDrop()` (`calendar.component.ts:505-552`) gère 4 cas biblio→jour : éducatif → séance technique (`:517-520,434-449`), force → planif (`:523-531`), modèle course → planif (`:534-540`), et **déplacement** jour→jour avec **update optimiste + rollback** (`:547-551`) — bien fait.
- Éditeur **structure force** : réordonnancement des blocs par DnD (`strength-session-editor.component.html:33-37`, `cdkDrag` + `cdkDragHandle`).

### 3.2 Frictions & manques

| # | Constat (`fichier:ligne`) | Impact desktop | Gravité |
|---|---------------------------|----------------|---------|
| 4.1 | **Réordonnancement intra-jour impossible** : sort si même conteneur (`calendar.component.ts:543`). | Pas d'ordre matin/soir dans un jour ; geste souris naturel absent. | Haute |
| 4.2 | **Pas de duplication au glisser** (Alt/Ctrl). Duplication seulement via « Dupliquer la semaine », **désactivée** (`advancedPlanning = false`, `calendar.component.ts:123`). | Copier une séance sur un autre jour (geste très fréquent) impossible. | Haute |
| 4.3 | Éditeur **structure course** sans DnD (aucun `cdkDrag` dans `session-editor.component.html`), contrairement à la force. | Réordonner des blocs course impossible ; incohérence. | Moyenne |
| 4.4 | **Feedback visuel pauvre** : pas de placeholder/preview custom, pas de surbrillance des drop zones ; `onLibDrop()` no-op (`calendar.component.ts:404`). | Le coach ne voit pas où « ça tombe ». Viole *Visibility of system status*. | Moyenne |
| 4.5 | **Aucun raccourci clavier / menu contextuel** (clic droit) pour déplacer/dupliquer. | Sur desktop, prive les power-users d'accélérateurs ; DnD = seul chemin (a11y). | Moyenne |
| 4.6 | Drop **force** sans rollback optimiste, contrairement au course (`calendar.component.ts:523-531` vs `547-551`). | Robustesse incohérente. | Basse |
| 4.7 | Détection du type par forme d'objet (`'structure' in rec`, `rec['category']`, `!('scheduledDate' in rec)`, `:517-534`). | Fragile ; un futur champ peut casser le routage. | Basse (dette) |
| 4.8 | Glisser une **semaine / bloc méso** entier : inexistant. | Planification par cycles non gestuelle. | Basse |

### 3.3 Comparatif Nolio

DnD **central** : glisser depuis le volet gauche, déplacer, **dupliquer (modificateur)**, réordonner dans un jour, manipuler des semaines — le tout à la souris, complété de raccourcis. L'app couvre « glisser depuis biblio » et « déplacer jour→jour » ; le reste manque.

### 3.4 Cible

- **Réordonnancement intra-jour** : autoriser le drop même-conteneur (retirer le `return` `:543`) + `moveItemInArray` + `orderIndex` persisté (déjà présent sur `WorkoutStep`, à étendre au niveau séance/jour côté back).
- **Duplication au glisser** : lire `event.event.altKey`/`ctrlKey` dans `onDrop` → `copy` au lieu de `reschedule`.
- **DnD blocs course** : `cdkDrag` + `cdkDragHandle` dans `session-editor` (aligner sur la force) + `moveItemInArray`.
- **Feedback** : `*cdkDragPlaceholder`, `*cdkDragPreview`, classe `.cdk-drop-list-dragging` sur les jours cibles, halo/curseur sur drop zones.
- **Accélérateurs desktop** : **menu contextuel clic droit** (« Déplacer vers… / Dupliquer vers… / Supprimer ») + **raccourcis clavier** (flèches semaine, `T` = aujourd'hui, `Suppr`…). Double bénéfice power-user + a11y (chemin non-DnD).
- **Robustesse** : généraliser optimiste+rollback (`:547-551`) à **tous** les drops (force incluse).
- **Méso** (option) : poignée de semaine pour glisser/dupliquer 7 jours, une fois `advancedPlanning` réactivé.

---

## 4. Axe 1 — Clarté & navigation (desktop d'abord)

### 4.1 État actuel

**Desktop — sidebar plate à 10 entrées** (`coach-layout.component.html:20-33`) :

```
Tableau de bord · Athlètes · Groupes · Calendrier · Bibliothèque ·
Éducatifs · Prépa physique · Club · Paramètres · Aide
```

Aucun regroupement, aucun séparateur, aucune hiérarchie. Rail repliable en icônes présent et persisté (`navCollapsed`, `coach-layout.component.ts:33-46`) — bon point — mais 10 icônes indifférenciées restent peu lisibles.

### 4.2 Frictions

| # | Constat (`fichier:ligne`) | Impact desktop | Gravité |
|---|---------------------------|----------------|---------|
| 1.1 | Menu **plat, 10 entrées** sans regroupement (`:20-33`). | Charge cognitive, pas de modèle mental. Viole *Recognition rather than recall*. | Moyenne |
| 1.2 | **3 bibliothèques distinctes** dans le menu (`:27-29` → `/templates`, `/run-drills`, `/strength`). | Le coach doit savoir *a priori* dans quel silo chercher. Parcours fragmenté. | Moyenne |
| 1.3 | Libellé **« Bibliothèque »** ambigu (ne couvre que le course, alors qu'Éducatifs et Prépa sont aussi des bibliothèques). | Confusion sémantique. | Basse |
| 1.4 | `Calendrier` (cœur métier) au même poids visuel que `Aide` ou `Club` (`:26,30,32`). | Pas de hiérarchie fréquent/secondaire. | Basse |

### 4.3 Cible (IA)

Regrouper les 10 entrées en **4 sections libellées** ; accès direct au quotidien, secondaire replié :

```
COACHING           ← accès direct
  Tableau de bord
  Athlètes
  Groupes
  Calendrier
BIBLIOTHÈQUES      ← contenus réutilisables (unifiés à terme, Axe 3)
  Séances course
  Prépa physique
  Éducatifs
CLUB
  Club / staff
RÉGLAGES
  Paramètres · Aide
```

Complément desktop : **raccourcis clavier** de navigation (aller au calendrier, athlète suivant/précédent…), cohérents avec l'Axe 4.

---

## 5. Axe 2 — Catégories personnalisées

### 5.1 État actuel

Trois taxonomies **figées** :

| Domaine | Type | Valeurs | Fichier |
|---------|------|---------|---------|
| Course | `WorkoutType` (union) | 10 (ENDURANCE…REST) | `workout.model.ts:1-3` |
| Prépa | `ExerciseCategory` | 10 (FORCE_MAX…) | `strength.model.ts:1-3` |
| Éducatifs | `RunDrillCategory` | 2 (TECHNIQUE, AMPLITUDE) | `run-drill.model.ts:1` |

`WorkoutType` pilote couleur/icône/« clé » via `TYPE_META` (`calendar.component.ts:61-72`) et les libellés (`workout.model.ts:54-65`). Création = liste fermée (`template-list.component.html:12-16`).

### 5.2 Frictions

| # | Constat (`fichier:ligne`) | Impact | Gravité |
|---|---------------------------|--------|---------|
| 2.1 | Impossible de créer « VMA courte », « Fartlek », « PPG haut du corps »… → on détourne un type générique (`template-list.component.html:12`). | Classement approximatif ; recherche difficile ; accordéons du panneau limités. | Moyenne |
| 2.2 | `WorkoutType` cumule **rôle moteur** (couleur, icône, « clé », conflit de charge `calendar.component.ts:154,169`) **et rangement**. | Rigidité ; couple à séparer avant tout rangement libre. | Moyenne (dette) |
| 2.3 | 3 taxonomies incompatibles → pas de catégorie transverse. | Pas de vision unifiée. | Basse |

### 5.3 Modèle cible : entité `Category` (coexistence avec l'enum)

```ts
// core/models/category.model.ts (proposé)
export type CategoryScope  = 'COACH' | 'CLUB';
export type CategoryDomain = 'COURSE' | 'STRENGTH' | 'DRILL' | 'ANY';

export interface Category {
  id: string;
  name: string;        // « VMA courte »
  color: string;       // token var(--…) ou hex
  icon: string;        // clé IconComponent
  order: number;       // tri manuel (drag)
  scope: CategoryScope;
  domain: CategoryDomain;
  builtinType?: WorkoutType | null; // adossement à l'enum (migration douce)
}
```

Rattachement : `categoryId: string | null` **optionnel** sur `WorkoutTemplate`, `StrengthSession`, `RunDrill` (+ `Workout` pour l'affichage). Optionnel ⇒ aucune rupture.

**Front :** `CategoryService`, sélecteur + « + Nouvelle catégorie » dans les formulaires, `TYPE_META` transformé en **fonction** (lit `categoryId` puis retombe sur l'enum). **Back :** table `category`, FK nullable, CRUD `/categories` + réordonnancement.

**Migration recommandée — coexistence puis bascule douce :**
1. `Category` s'ajoute à côté de l'enum (rangement/filtre) — zéro régression.
2. Seed d'une catégorie par valeur d'enum (via `builtinType`) → mapping 1:1 du contenu existant.
3. L'UI privilégie `categoryId` ; l'enum reste **interne** (charge, « clé », compat historique) — **on ne le supprime pas**.

---

## 6. Axe 5 (secondaire) — Consultation mobile

*Hors du poste de planification : le coach ne construit pas ses plans sur mobile, il consulte/ajuste.*

### 6.1 État actuel

Sur mobile (`coach-layout.component.scss:128-134`), la sidebar entière passe en `display:none` et seule la bottom-nav à 3 items subsiste (`coach-layout.component.html:74-84`) :

```
Accueil · Athlètes · Calendrier
```

→ Groupes, Bibliothèque, Éducatifs, Prépa physique, Club, Paramètres = **6 destinations inaccessibles** (Aide survit via la topbar `:51`).

### 6.2 Friction

| # | Constat (`fichier:ligne`) | Impact | Gravité |
|---|---------------------------|--------|---------|
| 5.1 | 6 pages inaccessibles sur mobile (`coach-layout.component.scss:129`). | En **consultation** mobile, le coach ne peut pas ouvrir sa biblio ni ses réglages. Gênant, **non bloquant** pour la planif (qui se fait sur desktop). | Moyenne |

### 6.3 Cible

Bottom-nav à **4 slots** dont un **« Plus »** ouvrant un sheet avec la nav complète groupée (réutilise l'IA de l'Axe 1). Corrige 5.1 sans surcharger l'écran. Le DnD mobile reste secondaire ; le picker overlay suffit comme chemin tactile.

---

## 7. Comparatif Nolio synthétique

| Capacité | Nolio | App actuelle | Cible |
|----------|-------|--------------|-------|
| **Volet séances à gauche (permanent)** | ✅ | ❌ tiroir droit fermé | ✅ panneau gauche accordéons |
| **Recherche dans le volet planif** | ✅ | ❌ (seulement dans `/templates`) | ✅ recherche + filtres + compteurs |
| Bibliothèque unifiée | ✅ | ❌ 3 silos | ✅ panneau unique catégorisé |
| Favoris / récents | ✅ | ⚠️ données présentes, non exposées | ✅ sections épinglées |
| Catégories libres coach | ✅ | ❌ 3 enums figés | ✅ entité `Category` |
| Glisser biblio → jour | ✅ | ✅ | ✅ |
| Déplacer jour → jour | ✅ | ✅ (optimiste+rollback) | ✅ fiabilisé partout |
| **Réordonner dans un jour** | ✅ | ❌ (`calendar:543`) | ✅ |
| **Dupliquer au glisser (Alt)** | ✅ | ❌ | ✅ |
| Réordonner blocs (course) | ✅ | ❌ (force seulement) | ✅ |
| **Raccourcis clavier / clic droit** | ✅ | ❌ | ✅ menu contextuel + raccourcis |
| Feedback drop zones/preview | ✅ | ⚠️ minimal | ✅ placeholder + preview |
| Nav desktop groupée | ✅ | ❌ plate, 10 items | ✅ 4 sections |
| Nav mobile (consultation) | ✅ | ❌ 6 pages inaccessibles | ✅ bottom-nav + « Plus » (secondaire) |

---

## 8. Wireframes (ASCII)

### 8.1 Poste desktop cible — panneau gauche permanent + calendrier

```
┌──────────────────────┬─────────────────────────────────────────────┐
│ BIBLIOTHÈQUE      🔍  │  [Athlète ▼]  Semaine|Mois  ← Auj →  42 km   │
│ ★ Favoris (3)        ├──────┬──────┬──────┬──────┬──────┬──────┬─────┤
│  ⠿ VMA 10×400        │ Lun  │ Mar  │ Mer  │ Jeu  │ Ven  │ Sam  │ Dim │
│ 🕑 Récents (5)       │      │┌────┐│      │┌────┐│      │      │     │
│ ▾ VMA courte    (4)  │ +    ││VMA ││ +    ││Seuil│ +    │ SL   │ Rep │
│  ⠿ 10×400  ⠿ 30/30   │      ││⠿   ││      │└────┘│      │      │     │
│ ▾ Seuil         (6)  │      │└────┘│      │      │      │      │     │
│  ⠿ Seuil 3×10'       │  glisser ⠿ → un jour · réordonner ↕ ·        │
│ ▸ Sortie longue (3)  │  Alt+glisser = dupliquer · clic droit = menu │
│ ▸ PPG           (8)  │                                              │
│ ▸ Éducatifs     (12) │  (drop zone surlignée pendant le drag)       │
└──────────────────────┴─────────────────────────────────────────────┘
   panneau permanent, repliable ◂ (préférence persistée)
```

### 8.2 Menu contextuel (clic droit sur une séance) — accélérateur desktop / a11y

```
┌─────────────────────────┐
│  Ouvrir la séance        │
│  Adapter la structure    │
│  ─────────────────────   │
│  Déplacer vers…      ▸    │
│  Dupliquer vers…     ▸    │
│  ─────────────────────   │
│  Supprimer          Suppr │
└─────────────────────────┘
```

### 8.3 Navigation desktop cible (déplié / rail)

```
┌────────────────────────┐        ┌──────┐
│ ◎ CoachApp   🔍 🔔      │        │  ◎   │
├────────────────────────┤        ├──────┤
│ COACHING               │        │ ▦ 👥 │
│  ▦ Tableau de bord      │        │ 👪 📅 │  (tooltips au survol)
│  👥 Athlètes  👪 Groupes│        ├──────┤
│  📅 Calendrier          │        │ 🏃🏋🎓│
│ BIBLIOTHÈQUES           │        ├──────┤
│  🏃 Course 🏋 Prépa 🎓Éduc│       │ 🏢   │
│ CLUB · RÉGLAGES         │        │ ⚙ ⛑ │
└────────────────────────┘        └──────┘
```

---

## 9. Plan d'amélioration priorisé (desktop-first)

### 9.1 Quick wins (faible effort / fort impact desktop)

| # | Objectif | Changements front | Back | Effort | Dépend. | Risques |
|---|----------|--------------------|------|--------|---------|---------|
| QW1 | **Panneau biblio à gauche, ouvert par défaut** (desktop large) | `calendar.component.ts:409` défaut `true` (desktop) ; `.scss` déplacer `.cal-sidebar` à gauche | — | **S** | — | Faible (CSS + défaut) |
| QW2 | **Recherche + compteurs** dans le panneau | Réutiliser `filtered()` de `template-list` dans le calendrier | — | **S** | — | Faible |
| QW3 | **Réordonnancement intra-jour** | Retirer `return` `calendar.component.ts:543` + `moveItemInArray` | `orderIndex` séance (PATCH) | **S/M** | — | Moyen (persistance ordre) |
| QW4 | **DnD blocs dans l'éditeur course** (aligner sur la force) | `session-editor.component.html` : `cdkDrag`/`cdkDragHandle` + `moveItemInArray` | — | **S** | — | Faible |
| QW5 | **Feedback DnD** : placeholder + preview + surbrillance drop zone | Templates + `.scss` (`.cdk-drop-list-dragging`) | — | **S** | — | Faible |
| QW6 | **Grouper la nav desktop** en 4 sections | `coach-layout.component.html/scss` : en-têtes de section | — | **S** | — | Faible |
| QW7 | **Exposer Favoris / Récents** (données déjà là) | Tri `favorite`/`useCount` | — | **S** | — | Faible |
| QW8 | **Réparer la nav mobile** (consultation) : bottom-nav 4 slots + « Plus » | `coach-layout.component.{html,scss,ts}` | — | **S** | — | Faible |

### 9.2 Chantiers structurants

| # | Objectif | Changements front | Back | Effort | Dépend. | Risques |
|---|----------|--------------------|------|--------|---------|---------|
| C1 | **Panneau gauche unifié réutilisable** `<app-session-library-panel>` | Composant `shared/` (accordéons, recherche, favoris) branché calendrier + route `/library` | lecture des 3 sources | **L** | C2 (catégories pour accordéons) | Refactor tiroir calendrier ; a11y |
| C2 | **Catégories personnalisées** (`Category`) | `category.model.ts`, `CategoryService`, `categoryId?` sur 3 modèles + `Workout`, formulaires, `TYPE_META`→fonction | table `category`, FK nullable, CRUD `/categories` | **L** | — | Migration enum (mitigée par coexistence §5.3) |
| C3 | **Unifier les 3 bibliothèques** sous arborescence catégorisée | Route `/app/library` réutilisant C1 ; conserver `/strength` (tests/cycles) tant que non migré | — | **M** | C1, C2 | Ne pas casser les parcours prépa |
| C4 | **DnD souris avancé + accélérateurs** : dupliquer au glisser (Alt), **menu contextuel clic droit**, **raccourcis clavier**, rollback généralisé | `calendar.component.ts` `onDrop` (modificateurs, menu, robustesse) | `copy` séance, `orderIndex` | **M/L** | QW3 | Complexité ; a11y |

### 9.3 Séquencement en phases

```
Phase 1 — Poste desktop : quick wins        [QW1→QW7 (+QW8 mobile en fin)]
  Justif : QW1/QW2/QW3 transforment immédiatement l'expérience de planif
           (panneau gauche ouvert + recherche + réordonnancement), sans back
           lourd et sans risque. QW8 (mobile) fermé en fin de phase, secondaire.

Phase 2 — Catégories perso                   [C2]        effort L, back requis
  Justif : socle de données des accordéons et filtres du panneau.
           Coexistence avec l'enum = zéro régression.

Phase 3 — Panneau gauche unifié              [C1 → C3]   effort L puis M
  Justif : dépend des catégories (Phase 2) ; réutilise recherche/favoris (Phase 1).

Phase 4 — DnD souris avancé + raccourcis     [C4]        effort M/L
  Justif : s'appuie sur le panneau (source unique de drag) et sur le
           réordonnancement livré en Phase 1 (QW3). Rend le geste central
           du poste desktop complet (dupliquer, clic droit, clavier).
```

**Ordre justifié (desktop-first)** : on rend d'abord le **poste de planification** efficace (panneau gauche ouvert, recherche, réordonnancement — Phase 1), puis on installe la **donnée fondatrice** (catégories — Phase 2), qui conditionne le **panneau catégorisé** (Phase 3), prérequis d'un **DnD central et outillé** (Phase 4). Le mobile (consultation) est traité en fin de Phase 1, sans jamais bloquer la planif.

---

## 10. Bugs / frictions avérés (vs. améliorations)

**Bugs / frictions confirmés dans le code :**
- 🔴 Panneau biblio à droite + fermé par défaut (`calendar.component.ts:409`) — à l'opposé du besoin desktop.
- 🟠 Réordonnancement intra-jour bloqué (`calendar.component.ts:543`).
- 🟠 Éditeur course sans DnD alors que la force en a (`session-editor.component.html` vs `strength-session-editor.component.html:33-37`).
- 🟡 Drop force sans rollback optimiste, contrairement au course (`calendar.component.ts:523-531` vs `547-551`).
- 🟡 Deux chemins de planif incohérents ; picker limité au course (`calendar.component.html:285-299`).
- 🟡 Détection de type par forme d'objet, fragile (`calendar.component.ts:517-534`).
- 🟡 Nav mobile : 6 destinations inaccessibles (`coach-layout.component.scss:128-131`) — gênant en consultation, non bloquant pour la planif.

**Améliorations proposées (pas des bugs) :** catégories perso, panneau gauche unifié, recherche/compteurs, favoris/récents, duplication au glisser, menu contextuel + raccourcis clavier, feedback drop zones, méso au glisser.

---

## 11. Contraintes respectées

- **Stack** : standalone + signals + OnPush + CDK DragDrop (déjà en place).
- **Non-régression** : `categoryId` optionnel, enums conservés, picker gardé en fallback, phases indépendantes.
- **Design system** : tokens `var(--…)` (comme `TYPE_META`), composants `shared/`.
- **A11y & mobile** : menu contextuel + raccourcis clavier comme alternative au DnD (bénéfice power-user desktop **et** a11y), panneau en drawer mobile, correctif nav mobile pour la consultation.

---

*Prochaine étape : validation de ce plan avant toute implémentation. Suggestion de démarrage : Phase 1 (QW1 en tête — panneau de séances à gauche, ouvert par défaut sur desktop).*
