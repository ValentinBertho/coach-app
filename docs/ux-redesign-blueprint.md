# DARI Lab Training — Blueprint de refonte UI/UX

> Document exécutable pour la refonte Angular 17 (standalone + signals + PWA).
> Stack confirmée dans le repo : Angular 17.3, `@angular/cdk` (drag-drop, overlay), `@angular/service-worker` (PWA), Leaflet (cartes GPX), signals.
> Feature folders existants réutilisés : `athlete`, `dashboard`, `calendar`, `strength`, `messages`, `physio`, `analytics`, `club`, `activities`, `races`, `workouts`, `templates`, `groups`, `settings`, `admin`.
> Principe directeur transversal : **les moteurs de calcul backend sont la source de vérité. L'UI affiche, hiérarchise et alerte — elle n'invente jamais de logique métier et ne masque jamais un état critique.**

---

# 1. Product UI/UX Vision

## Direction artistique
**« Laboratoire de performance, pas salle de sport gamifiée. »**
On vise la précision d'un instrument scientifique avec la chaleur d'un produit grand public sportif. Référence mentale : la rigueur data de **TrainingPeaks**, la lisibilité émotionnelle de **Whoop**, l'énergie de **Strava**, la propreté structurelle de **Linear/Superhuman**.

- **Premium sportif** : surfaces sombres profondes par défaut (dark-first côté coach et athlète en extérieur), accents vifs mais rares, typographie nette.
- **Data-driven** : chaque pixel coloré porte une sémantique métier. Pas de couleur décorative sur une donnée.
- **Physiologie lisible** : les valeurs critiques (VDOT, LT1/LT2, VC, ATL/CTL/ACWR) sont toujours en chiffres tabulaires, jamais noyées.
- **Énergie maîtrisée** : un seul accent « action » dominant par écran. Le reste est neutre.
- **Sophistication calme** : beaucoup d'air, peu de bordures, hiérarchie portée par le contraste et l'espacement, pas par les traits.
- **Précision sans froideur clinique** : on garde de l'arrondi (radius 12–16), des micro-animations sobres, une typo humaniste — mais zéro gadget.

## Ambiance produit
Sérieux, fiable, rapide. L'athlète doit sentir « mon coach m'a préparé ça, je sais exactement quoi faire aujourd'hui ». Le coach doit sentir « je vois l'état de mon écurie en 3 secondes et je sais qui a besoin de moi maintenant ».

## Promesse visuelle
> « En un coup d'œil, je sais quoi faire (athlète) ou qui aider (coach). En un geste, j'agis. »

## Posture de marque
Coach-driven, scientifique, actionnable. On n'impressionne pas par la quantité de données affichées, mais par la **pertinence** de ce qui est mis en avant.

## Philosophie de navigation
- **Athlète** : navigation par destination (bottom nav), surface de décision minimale, 1 action prioritaire par écran.
- **Coach** : navigation par module persistant (sidebar), drill-down sans perte de contexte, panneaux latéraux plutôt que changements de page.
- **Admin** : navigation utilitaire, dense, tabulaire, zéro fioriture.

## Philosophie mobile vs desktop
Ce ne sont **pas** les mêmes écrans redimensionnés. Mobile athlète = flux de complétion rapide (lire → faire → renseigner). Desktop coach = surface d'analyse multi-panneaux. Certains modules (calendrier, builder force, dashboard) **changent de pattern**, pas seulement de largeur (cf. §6).

## Principes UX fondateurs
1. **Information d'abord**, esthétique au service de la lecture.
2. **L'accent visuel sert la décision** (où regarder, quoi faire), jamais la déco.
3. **Contraste fonctionnel** : ce qui est important est plus contrasté, point.
4. **Densité maîtrisée** : dense ≠ encombré. On densifie par alignement et tabular numbers, pas en collant les éléments.
5. **Cohérence des patterns** : une zone d'intensité a la même couleur/forme partout (card, badge, chart, calendrier).
6. **Lecture en couches** : titre → métrique clé → contexte → détail à la demande.
7. **Progressive disclosure** : le détail physiologique est accessible, pas imposé.
8. **Interaction rapide athlète / analyse profonde coach** : deux régimes d'effort cognitif assumés.

---

# 2. UX Principles Non-Negotiables

## Règles immuables
1. **Ne jamais sacrifier la compréhension des données physiologiques à l'esthétique.** Si un graphe est joli mais ambigu, il est mauvais.
2. **Ne jamais cacher une donnée critique derrière une interaction secondaire.** Douleur, alerte de charge (ACWR), readiness : visibles sans clic.
3. **Ne jamais surcharger une vue mobile avec des métriques de second rang.** Mobile = priorités du jour uniquement, le reste en drill-down.
4. **Toujours rendre visibles les décisions prioritaires du jour** (athlète : ma séance ; coach : mes athlètes à risque).
5. **Toujours distinguer 4 registres** : Lecture / Action / Alerte / Analyse — par couleur, position et composant.
6. **Toujours distinguer données calculées vs saisies vs estimées vs alertées vs complétées** (cf. §11, jeu d'icônes + libellé, pas juste couleur).
7. **Toujours différencier données coachables / contexte / alerte.**
8. **Toujours réduire le nombre de choix visibles à un instant T.** Une action primaire par écran.
9. **Scanability > exhaustivité simultanée.**
10. **Invariants métier inviolables dans l'UI :**
    - L'athlète peut **déplacer** une séance (drag/date) mais **jamais modifier son contenu** → édition de contenu désactivée et visuellement non-affordante côté athlète.
    - L'état de forme **n'est jamais dérivé du RPE seul** → le composant readiness affiche ses entrées (RPE + fatigue + charge + sommeil/douleur) et son origine « calculé ».
    - Les prescriptions sont en **fourchettes min/max**, jamais en valeur sèche → composant `range-prescription-pill` obligatoire, pas de champ unique.
    - Multi-tenant strict : un coach ne voit jamais hors de son périmètre (privé/club/ses athlètes). L'UI affiche toujours le scope actif.

## Erreurs UX interdites (avec exemples concrets)
- ❌ **Graphe joli mais illisible** : line chart ATL/CTL/ACWR à 3 axes superposés sans seuils annotés. ✅ Aire CTL (fitness) + ligne ATL (fatigue) + bande de sécurité ACWR (0,8–1,3) annotée.
- ❌ **Rouge/orange/vert seuls** (8 % de daltoniens) : statut athlète codé uniquement par couleur de pastille. ✅ Couleur + icône + libellé (« À risque ⚠ »).
- ❌ **Donnée critique dans une card marketing** : ACWR=1,6 affiché en petit sous un gros chiffre « 142 km ce mois ». ✅ ACWR en alerte dédiée, dominante.
- ❌ **Calendrier mur de pastilles** sans priorisation : 6 events du même gris. ✅ Hiérarchie visuelle (séance clé > footing > muscu > note), densité de charge en fond.
- ❌ **Page séance = un long mur de texte** : prescription, explication, exécution mélangées. ✅ Blocs séquentiels labellisés (Prescription / Pourquoi / Exécution / Retour).
- ❌ **Dashboard coach = mur de 20 KPI.** ✅ Cockpit à 4–5 zones priorisées (cf. §7.2).

---

# 3. Navigation Architecture

## Architecture globale
3 shells distincts, sélectionnés au login selon le rôle (route guard `roleGuard` + résolution du tenant) :
- `AthleteShell` (mobile-first, bottom nav)
- `CoachShell` (desktop-first, sidebar)
- `AdminShell` (back-office sobre)

Parcours type : `login → resolver rôle/tenant → landing role-based → module → vue détail → action`, avec préservation du contexte (cf. deep-linking).

## A. Athlète — mobile-first

**Bottom navigation (5 items max, thumb-zone) :**
`Today` (défaut) · `Calendrier` · `Messages` · `Progrès` · `Profil`

- **Today** : écran central (séance du jour, readiness, retour rapide).
- Raccourci flottant contextuel « Reprendre ma séance » / « Noter mon retour » quand une séance du jour est non clôturée.
- **PWA quasi-native** : standalone display, splash, app shell caché instantané, transitions de page natives, pull-to-refresh sur Today/Calendrier.
- **Surface de décision minimale** : jamais plus d'1 CTA primaire visible.
- **Hiérarchie très simple** : 2 niveaux de profondeur max avant une action (Today → Séance → Retour).
- Navigation secondaire = **bottom sheets** (détail séance, sélecteur de date pour déplacer), pas de nouvelles pages lourdes.

**Règles navigation mobile :**
- Pas de sidebar, pas de menu hamburger pour les fonctions principales.
- Back gestuel natif respecté ; le state (filtre, scroll) est préservé via signals + route data.
- Modals plein écran réservés aux flux de saisie ; le reste en sheets non-bloquants.

## B. Coach — desktop-first

**Sidebar persistante (collapsible, icône + label) :**
`Dashboard` · `Athlètes` · `Calendrier` · `Physiologie` · `Force` · `Charge & Analytics` · `Messages` · `Sync` · `Club` · `Paramètres`

**Header contextuel (par module) :**
- Fil d'Ariane + **scope switcher** persistant : `Mes athlètes` / `Privés` / `Club` / `Tous` (multi-tenant, toujours visible).
- Recherche globale (athlète, séance) type Superhuman (Cmd+K).
- Actions contextuelles à droite (créer séance, comparer, exporter).

**Sous-navigation par module** : tabs sous le header (ex : fiche athlète → Vue d'ensemble / Physio / Charge / Calendrier / Force / Messages).

**Drill-down sans perte de contexte** : ouverture en **side panel / compare panel** (overlay CDK) plutôt que navigation de page quand on inspecte un athlète depuis le dashboard. Le dashboard reste derrière.

**Règles navigation desktop :**
- Sidebar + header toujours présents (navigation persistante).
- Filtres visibles en permanence (pas cachés dans un menu).
- Modals réservés aux actions destructrices/confirmations ; l'analyse se fait en panneaux.
- Raccourcis clavier (cf. §9).

## C. Admin — back-office
- Layout sobre, sidebar utilitaire : `Tenants/Clubs` · `Utilisateurs` · `Rôles & Permissions` · `Intégrations` · `Logs/Audit` · `Système`.
- Tout en tables denses, filtres, tri, recherche. Zéro data-viz émotionnelle. Couleur réduite au statut système (actif/suspendu/erreur).

## Navigation transversale & deep-linking
- Routes profondes adressables : `/coach/athletes/:id/load?range=42d`, `/athlete/calendar/session/:id`, `/coach/calendar?week=2026-W26&scope=club`.
- Depuis un message → lien vers la séance/objectif/feedback rattaché (ouvre le side panel séance, garde le thread).
- Depuis une alerte dashboard → fiche athlète sur l'onglet pertinent (Charge si ACWR, Physio si LT, Calendrier si conflit).
- **Préservation de contexte** : scope coach, plage de dates, filtres calendrier persistés en URL (query params) + signals → un partage de lien rouvre exactement la même vue.

---

# 4. Angular UI Architecture

## Découpage des shells
```
core/
  layout/
    athlete-shell/        # bottom-nav, app shell mobile, sheet outlet
    coach-shell/          # sidebar + header contextuel + panel outlet
    admin-shell/          # sidebar utilitaire
  navigation/
    bottom-nav/           # presentational, signals input(items, active)
    side-nav/             # collapsible, scope-aware
    context-header/       # breadcrumbs + scope switcher + actions slot
    command-palette/      # Cmd+K (CDK overlay)
  services/               # (existant) auth, tenant, realtime, sync
  models/                 # (existant) source de types
  guards/                 # roleGuard, tenantScopeGuard, athleteImmutabilityGuard
```

## Routing
- `app.routes.ts` → lazy `loadChildren` par shell, puis par feature.
- Chaque feature expose `*.routes.ts` standalone.
- Guards : `roleGuard(['COACH'])`, `tenantScopeGuard` (vérifie périmètre avant resolve), `canDeactivate` sur builder force (édition non sauvegardée).
- Resolvers légers (skeleton immédiat, données en streaming via signals).

## Feature folders (réutilise l'existant)
```
features/
  athlete/        → today, session-mobile, feedback-flow, progress
  dashboard/      → coach cockpit
  athletes/       → liste + fiche cockpit (detail)
  calendar/       → calendar (responsive switching mobile/desktop)
  strength/       → strength-builder + session-editor (existant)
  workouts/ templates/
  physio/         → lactate (LT1/LT2/VC), load (ATL/CTL/ACWR) (existant)
  analytics/      → load-analytics, compare
  messages/       → realtime threads
  activities/     → Strava/GPX, cartes Leaflet
  races/          → objectifs
  club/ groups/   → multi-tenant management
  settings/ admin/
```

## Shared libraries (à créer en priorité)
```
shared/
  ui/             # primitives pures: button, card, sheet, drawer, chip,
                  # segmented-control, badge, banner, callout, skeleton,
                  # sticky-action-bar, fab, filter-bar, empty-state
  data-viz/       # chart primitives (line, area, bar, stacked, sparkline,
                  # scatter), axis, legend, threshold-band, annotation
  physiology/     # composants métier: intensity-zone-badge, readiness-gauge,
                  # acwr-indicator, load-balance-bar, lt-progression-chart,
                  # range-prescription-pill, effort-badge, pain-fatigue-selector,
                  # data-origin-tag (saisi/calculé/estimé/alerté)
```

## Smart vs presentational
- **Presentational (dumb)** : tout `shared/ui` et `shared/data-viz`. `input()`/`output()` signals, zéro injection de service, `ChangeDetectionStrategy.OnPush`, aucune logique métier.
- **Smart containers** : composants de page features/* qui injectent services, dérivent des `computed()`, et passent des view-models aux dumbs.
- **`shared/physiology`** : semi-smart visuel — encapsule la **logique de hiérarchie/sémantique d'affichage** (mapping valeur → zone → couleur/forme, calcul de l'état d'alerte d'affichage à partir de seuils fournis par le backend) **mais jamais la logique de calcul métier** (pas de calcul d'ACWR côté front ; il reçoit la valeur + le seuil et décide seulement comment l'afficher).

## Composants purement visuels vs métier vs physio
| Catégorie | Exemples | Règle |
|---|---|---|
| Purement visuel | button, card, chip, skeleton, sheet | Réutilisables, théméables, OnPush, aucun sens métier |
| Logique de hiérarchie métier | `athlete-status-chip`, `kpi-rail`, `today-attention-list` | Décident de l'ordre/priorité d'affichage selon des règles d'UI |
| Dédiés physiologie | `intensity-zone-badge`, `acwr-indicator`, `readiness-gauge`, `range-prescription-pill`, `lt-progression-chart` | Sémantique constante, redondance couleur+forme+texte, testés visuellement |
| Optimisés rendu rapide | `session-block-card`, `calendar-chip`, `message-bubble`, `athlete-row` | Skeleton dédié, `@for track`, virtualisation si liste longue |

## Patterns
- **Dashboards complexes** : grille de widgets autonomes, chacun avec son skeleton et son flux signal → un widget lent ne bloque pas les autres.
- **Responsive switching** : `BreakpointObserver` (CDK) → signal `viewport()` ; le shell switche le composant calendrier (mois desktop vs agenda mobile), pas juste le CSS.
- **Drawers/overlays/side panels/compare** : `@angular/cdk/overlay` + un service `PanelService` (signals stack) pour empiler side panel athlète / compare panel sans navigation.

---

# 5. Design System

## 5.1 Foundations

### Palette globale (dark-first, light dérivé)
| Token | Dark | Light | Usage |
|---|---|---|---|
| `--bg-base` | `#0B0F14` | `#F7F8FA` | fond app |
| `--bg-elev-1` | `#121821` | `#FFFFFF` | cards |
| `--bg-elev-2` | `#1A2230` | `#FFFFFF`+ombre | panels, popovers |
| `--bg-elev-3` | `#222C3C` | `#FFFFFF` | overlays/sheets |
| `--border-subtle` | `#1E2733` | `#E6E9EF` | séparateurs |
| `--border-strong` | `#2C3A4D` | `#CBD2DD` | focus, actifs |
| `--accent` | `#3B82F6`→`#2DD4BF` (cyan-bleu) | idem | action primaire (1 seul) |
| `--accent-contrast` | `#0B0F14` | `#FFFFFF` | texte sur accent |

### Texte
| Token | Dark | Light | Contraste |
|---|---|---|---|
| `--text-primary` | `#F2F5F9` | `#0D1320` | ≥ 12:1 |
| `--text-secondary` | `#A7B1C0` | `#48566B` | ≥ 5:1 |
| `--text-tertiary` | `#6C7889` | `#7A879B` | ≥ 4.5:1 (jamais sur donnée critique) |
| `--text-on-data` | `#FFFFFF` | dépend du fond zone | toujours testé AA |

### Niveaux de contraste
- Donnée physiologique critique : **min 7:1 (AAA)**.
- Texte courant : min 4.5:1 (AA). Labels/micro : min 4.5:1 aussi (pas de gris décoratif sur valeurs).

### Radius
`--r-xs 6` · `--r-sm 8` · `--r-md 12` (cards) · `--r-lg 16` (sheets/panels) · `--r-pill 999`.

### Ombres (dark = ombre + halo de bordure)
`--sh-1` cards : `0 1px 2px rgba(0,0,0,.4)` · `--sh-2` panels : `0 8px 24px rgba(0,0,0,.5)` · `--sh-3` overlays : `0 16px 48px rgba(0,0,0,.6)`.

### Spacing scale (base 4)
`4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 56 · 72`. Densité coach : pas de saut < 8 entre blocs distincts.

### Grid
- Mobile : 4 col, gutter 16, marge 16.
- Tablet : 8 col, gutter 20.
- Desktop coach : 12 col, gutter 24, max-content 1440, panneaux en fractions de 12.

### Breakpoints
`xs <480` · `sm 480–767` (mobile) · `md 768–1023` (tablet) · `lg 1024–1439` (desktop) · `xl ≥1440` (coach multi-panel).

### Motion tokens
`--motion-fast 120ms` · `--motion-base 200ms` · `--motion-slow 320ms` · easing `--ease-out cubic-bezier(.2,.8,.2,1)` · `--ease-spring` pour drag/drop. `prefers-reduced-motion` → tout passe à 0/opacité simple.

### Z-index strategy
`base 0` · `sticky 100` · `bottom-nav 200` · `drawer 300` · `overlay-panel 400` · `sheet 500` · `modal 600` · `toast 700` · `command-palette 800`. Centralisé en tokens, jamais de `z-index` magique.

## 5.2 Typographie

- **Font UI principale** : **Inter** (variable) — humaniste, neutre, excellent en petit corps.
- **Font accent** (titres dashboards, hero séance) : **Inter Display** / ou un grotesque condensé optionnel ; rester sobre.
- **Font monospace tabulaire métriques** : **JetBrains Mono** ou **Inter avec `font-feature-settings:'tnum'`**. **Toutes les valeurs physiologiques utilisent des tabular numbers** (alignement vertical des chiffres dans tables et comparaisons).

### Échelle
| Rôle | Taille / line-height | Poids | Notes |
|---|---|---|---|
| H1 (hero séance, titre page) | 28/34 | 700 | rare |
| H2 (section) | 22/28 | 600 | |
| H3 (sous-section/card title) | 18/24 | 600 | |
| Body | 15/22 | 400 | |
| Body-strong | 15/22 | 600 | |
| Label | 13/18 | 500 | uppercase tracking +0.04em pour catégories |
| Micro | 11/14 | 500 | badges, légendes |
| **Metric-L** | 34/36 | 700 `tnum` | KPI dominant (VDOT, charge) |
| **Metric-M** | 22/24 | 600 `tnum` | cards |
| **Metric-S** | 15/18 | 600 `tnum` | tables, chips |

### Règles densité
- Line-height plus serré sur metrics (proches de 1.0–1.1) pour grouper visuellement.
- Letter-spacing négatif léger (-0.01em) sur gros chiffres ; positif sur labels uppercase.
- **Tabular numbers obligatoires** partout où des nombres s'alignent ou se comparent (tables, KPI rail, calendrier charge, sets force).
- Data tables : corps 13–14, padding vertical 8, en-têtes label uppercase.
- Badges/chips : micro 11, padding 2×8.

## 5.3 Couleurs sémantiques métier

**Principe : sémantique constante + redondance non-chromatique (icône/forme/texte).** Les zones d'intensité gardent la même couleur partout (card, badge, chart, calendrier).

### Zones d'intensité physiologiques (modèle 5 zones, dérivées des domaines LT1/LT2/VC)
| Zone | Domaine | Couleur (dark/light AA) | Forme/Icône redondante |
|---|---|---|---|
| Z1 | Récup / < LT1 | Gris-bleu `#5B8DEF` clair | ● plein, libellé « Z1 » |
| Z2 | Endurance fond / LT1 | Vert teal `#2DD4BF` | ◐ |
| Z3 | Tempo / LT1–LT2 | Ambre `#F5B544` | ◑ |
| Z4 | Seuil / LT2 | Orange `#FB7B3A` | ◕ |
| Z5 | VO2/VC+ / > VC | Rouge magenta `#F0476A` | ● contour épais |

> Les zones n'utilisent jamais la couleur seule : badge = pastille + « Z3 » + (optionnel) tranche d'allure/FC. Pattern de remplissage disponible pour mode haute-accessibilité.

### Autres sémantiques
| Concept | Couleur | Redondance |
|---|---|---|
| Fatigue | échelle gris→violet `#8B7FF0` | icône batterie + valeur |
| Douleur | échelle ambre→rouge `#F0476A` | icône ⚠ + échelle 0–10 + localisation |
| Charge (CTL/fitness) | bleu profond `#3B82F6` | aire |
| Charge aiguë (ATL/fatigue) | violet `#8B7FF0` | ligne |
| ACWR sweet spot | bande verte `#2DD4BF` 0,8–1,3 | bande annotée + libellé |
| ACWR risque | rouge `#F0476A` > 1,5 ; gris < 0,8 (détraining) | libellé + icône |
| Récupération | teal `#2DD4BF` | icône ✓ |
| Force | indigo `#6366F1` | icône haltère |
| Trail | terre `#B07B4F` | icône relief |
| Route | bleu `#3B82F6` | icône route |
| Alerte | `#FB7B3A` (warn) / `#F0476A` (critique) | icône + libellé |
| Succès | `#2DD4BF` | ✓ |
| Neutral | `--text-tertiary` | — |
| Disabled | opacité 0.4 + curseur | non-affordant |
| Offline | gris `#6C7889` | icône nuage barré + « Hors-ligne » |
| Syncing | accent pulsant | spinner discret |
| Pending | ambre `#F5B544` | « En attente » |

### Contraintes dark/light & plein soleil
- Toute couleur d'intensité testée AA sur `bg-elev-1` en dark ET light.
- Indicateurs fatigue/douleur : **forte saturation + gros tap target + libellé** pour rester lisibles en extérieur plein soleil (mode « high-contrast outdoor » optionnel qui booste contraste et taille).

## 5.4 Composants de base

Format : **Rôle · Contenu · Hiérarchie · Variants · États · Responsive · Erreurs à éviter.**

- **App shell** — conteneur racine par rôle. Contient nav + outlet + overlays. Variants : athlete/coach/admin. États : online/offline (bandeau). Responsive : switch de shell par breakpoint+rôle. ❌ Ne pas mélanger nav coach et athlète.
- **Sidebar (coach)** — nav modules. Icône+label, section active surlignée (barre accent + fond elev). Variants : expanded/collapsed. États : module actif, badge non-lu (messages). Responsive : drawer sous `lg`. ❌ Pas plus de 10 items ; pas de sous-arbre profond.
- **Topbar / context-header** — contexte module. Breadcrumb + scope switcher + actions + search. Hiérarchie : scope toujours visible. Responsive : actions → menu kebab en `md`. ❌ Ne jamais perdre le scope multi-tenant.
- **Bottom nav (athlète)** — 5 destinations. Icône+label, actif = accent+label. Badge non-lu. ❌ Jamais > 5, jamais d'action destructrice.
- **Section header** — titre de bloc + action contextuelle (lien « voir tout »). Micro-label catégorie au-dessus. ❌ Pas de H1 partout.
- **Metric card** — 1 métrique dominante. Label (micro) + Metric-L (tnum) + delta + sparkline optionnelle + `data-origin-tag`. Variants : neutral/alert/success. États : loading(skeleton)/empty/error. Responsive : full-width mobile, fraction grille desktop. ❌ Jamais 2 métriques égales dans la même card (ambiguïté).
- **Trend card** — métrique + mini-graph tendance + période. Delta coloré+flèche (redondance). ❌ Pas de flèche sans signe/valeur.
- **Athlete status chip** — état global athlète. Pastille couleur + icône + libellé (« OK / À surveiller / À risque »). Variants par statut. Responsive : compact (pastille+icône) en liste dense, full en card. ❌ Couleur seule interdite.
- **Physiology badge** — valeur de référence (VDOT 54, LT2 16,2 km/h). Label + valeur tnum + unité + origin-tag. ❌ Pas d'arrondi trompeur ; afficher l'unité.
- **Intensity zone badge** — zone Z1–Z5. Couleur+forme+« Zx »+plage (allure/FC). Variant compact (chip)/full (avec plage). ❌ Jamais sans le « Zx » textuel.
- **Session block card** — un bloc de séance (échauffement, corps, récup). En-tête bloc + durée/distance + zone + répétitions + range pills. Variants course/force. États : à venir/en cours/fait. ❌ Ne pas fusionner prescription et réalisé.
- **Session timeline** — vue séquentielle verticale des blocs avec rail de zone coloré à gauche. Responsive : pleine largeur mobile. ❌ Pas d'horizontal scroll sur mobile.
- **Workout summary tile** — résumé compact (calendrier, listes). Type+titre+charge estimée+zone dominante. ❌ Trop d'infos → illisible en chip.
- **Calendar chip** — event dans une cellule. Type (couleur/icône) + titre court + indicateur charge. Variants par type (séance clé, footing, muscu, objectif, indispo, note). ❌ Pas tous gris.
- **Draggable calendar chip** — idem + affordance drag (poignée desktop, long-press mobile), ghost au drag, drop zones surlignées (CDK DragDrop). États : dragging/valid-drop/invalid-drop. ❌ Athlète : drag de date OK, édition contenu interdite.
- **Compare panel** — comparaison 2–4 athlètes/périodes. Colonnes alignées, mêmes échelles, deltas. Desktop only (overlay). ❌ Échelles différentes entre colonnes = mensonge visuel.
- **Split chart panel** — 2 graphes liés (ex : charge méca vs métabolique) partageant l'axe X. Curseur synchronisé. ❌ Axes Y non étiquetés.
- **Message thread** — conversation. Bulles coach/athlète différenciées, ancrage contexte (chip séance/objectif), épinglés en haut, lus/non-lus. Responsive : plein écran mobile, panneau secondaire desktop. ❌ Pas de chat « détaché » du contexte d'entraînement.
- **Quick reply composer** — réponses rapides + champ. Chips de réponses suggérées, joindre (léger), envoi. États : envoi/échec/retry offline. ❌ Pas de gros formulaire.
- **Sticky action bar** — actions primaires fixées en bas (mobile séance). 1 CTA dominant + 1 secondaire max. États : enabled/disabled/loading. ❌ Jamais > 2 actions ; ne pas masquer du contenu critique derrière.
- **FAB mobile** — action contextuelle unique (ex : « + retour », « reprendre séance »). Apparaît selon contexte. ❌ Pas de FAB générique permanent.
- **Filter bar** — filtres persistants (coach). Segmented + chips multi-select, scope. État actif visible, reset. Responsive : scroll horizontal/sheet mobile. ❌ Filtres cachés.
- **Segmented control** — choix mutuellement exclusifs (jour/semaine/mois, simplifié/avancé). ❌ Pas > 4 segments.
- **Empty states** — absence de données : illustration sobre + explication + CTA. Variants par contexte (pas de séance / pas de message / pas encore de data physio). ❌ Page blanche.
- **Skeleton loaders** — formes des vrais composants. Par composant clé. ❌ Pas de spinner plein écran pour du contenu structuré.
- **Inline warning banner** — avertissement non bloquant (sync en attente, données estimées). Icône+texte+action. ❌ Pas de rouge pour un simple info.
- **Critical alert callout** — alerte forte (douleur élevée, ACWR risque). Bordure+fond+icône+libellé+action. Toujours au-dessus du fold concerné. ❌ Jamais réductible au point de disparaître.
- **Chart legend** — légende lisible, alignée à la sémantique globale (mêmes couleurs/formes que badges). ❌ Légende incohérente avec les badges.
- **Stat strip** — bande horizontale de 3–5 stats clés (header fiche athlète). tnum, séparateurs subtils. Responsive : wrap/scroll mobile. ❌ Trop de stats → bruit.
- **KPI rail** — colonne/ligne de KPI priorisés (dashboard). Ordre = priorité métier. ❌ Pas un mur ; max 4–6.
- **Exercise row** — exercice (force). Nom + format + cible + bouton détail. ❌ Pas tous les sets dépliés par défaut.
- **Set input row** — saisie d'un set. Cible (range pill) vs réalisé (champs charge/reps/RIR). Cases larges, clavier numérique, tnum. États : vide/saisi/validé. ❌ Confondre prescrit/cible/réalisé.
- **Range prescription pill** — fourchette min–max obligatoire (ex : « 4:10–4:20 /km », « 70–75 % 1RM »). Jamais une valeur sèche. ❌ Champ unique interdit (invariant).
- **Effort badge RPE/RIR** — effort prescrit/ressenti. Échelle visuelle + valeur. Distinction prescrit (cible) vs ressenti (saisi, origin-tag). ❌ Mélanger les deux.
- **Pain/fatigue selector** — saisie rapide 0–10. Gros segments tactiles, couleur+chiffre+libellé, douleur avec localisation optionnelle. ❌ Slider fin difficile au doigt en extérieur.
- **Offline sync status** — état de synchro. Icône+libellé (Hors-ligne/En attente/Synchronisé) + nb d'éléments en file. ❌ Échec silencieux.

## 5.5 Charts & Data Visualization

### Choix de représentation
| Donnée | Représentation | Pourquoi |
|---|---|---|
| ATL / CTL | **Aire CTL (fitness) + ligne ATL (fatigue)** sur axe partagé, plus **TSB/forme** en barres +/- sous l'axe | lecture fitness vs fatigue immédiate |
| ACWR | **Jauge/ligne avec bande de sécurité 0,8–1,3 annotée**, point courant coloré (vert/ambre/rouge+libellé) | le seuil EST l'information |
| Progression LT1/LT2/VDOT | **Line chart multi-séries fines + points de test marqués** (annotations « test labo »), axe temps | montre la tendance + jalons réels |
| Charge méca vs métabolique | **Split chart** (2 aires empilées comparées) ou **stacked bar** par semaine | comparaison de composition |
| Fatigue / douleur / compliance | **Bandes/heatmap calendaire** + sparkline | densité temporelle, repérage d'épisodes |
| Comparaison athlètes | **Small multiples** (mêmes échelles) ou **bar groupé** | jamais surimposer 6 lignes |
| Zones physiologiques | **Barre de répartition (stacked horizontal)** Z1–Z5 par couleur+%+label | distribution d'intensité sans ambiguïté |
| Valeur unique + tendance | **Métrique + sparkline** dans une table | densité, scanability |

### Règles d'axes/labels/tooltips
- Axes Y **toujours étiquetés avec unité** ; pas d'axe « mystère ».
- Grilles minimales (1–2 lignes de référence max), couleur `--border-subtle`.
- **Seuils métier annotés** (LT1, LT2, VC, bande ACWR) directement sur le graphe, pas seulement en légende.
- Tooltips : valeur tnum + unité + date + zone/contexte ; pas de tooltip qui cache la donnée.
- Couleurs **identiques** à la sémantique globale (une zone Z3 ambre est ambre partout).
- Annotations utiles uniquement (date de test, blessure, objectif), jamais décoratives.

### Quand préférer table+sparkline à un gros chart
- Listes d'athlètes (compliance, charge) : **table + sparkline + status chip** > 12 mini-charts.
- Historique de tests : table avec deltas tnum.
- Mobile : sparkline + valeur > graphe complexe (qui devient illisible).

**Insistance** : lisibilité > effet ; grilles minimales ; repères métier prioritaires ; annotations utiles ; cohérence chromatique card↔badge↔chart absolue.

---

# 6. Responsive Strategy

## 6.1 Mobile athlète-first
- **Thumb zones** : actions primaires dans le tiers inférieur ; haut d'écran = lecture seule.
- **Bottom actions / sticky action bar** pour valider/renseigner.
- **One-handed** : navigation au pouce, pas de cible critique en haut.
- **Vertical scan** : tout en colonne unique, priorité décroissante.
- **Rapid completion** : flux de retour séance en 1 écran, < 10 s.
- **Réduction du bruit** : métriques de 2nd rang masquées (drill-down).
- **Surfaces contextuelles** : mode « pré-effort » (lecture séance), « post-effort » (saisie retour), « consultation » (progrès) — l'UI met en avant ce qui correspond au moment.
- **Tap targets ≥ 44px**, espacés ≥ 8.
- **Pré/pendant/post effort** : avant = quoi faire (séance dominante) ; pendant = blocs+zones gros et lisibles, peu de texte ; après = retour rapide.

## 6.2 Tablet
- Usage hybride (coach terrain, athlète avancé).
- Split panes modérés (liste + détail).
- Panels escamotables (sidebar coach en drawer, side panel athlète).
- Calendrier : vue semaine confortable, drag tactile.

## 6.3 Desktop coach
- Densité plus élevée mais respirable (espacement ≥ 8, air entre zones).
- Multi-panels (dashboard + side panel athlète + compare).
- Colonnes comparatives, navigation persistante (sidebar+header).
- Filtres visibles, drag & drop confortable (poignées, drop zones), charts comparatifs, vues consolidées.

## Règles de transformation
| Module | Mobile | Desktop | Transformation |
|---|---|---|---|
| Today athlète | colonne unique | (n/a coach) | reflow |
| Dashboard coach | (résumé/alertes) | cockpit multi-zones | **change de pattern** |
| Calendrier | **agenda vertical / jour** | **grille semaine/mois + DnD** | **change de pattern** |
| Fiche athlète | onglets empilés | header sticky + tabs + side actions | stacked → split |
| Séance | timeline verticale + sticky bar | timeline + panneau prescription/réalisé | reflow + panel |
| Force builder | mode lecture/simplifié | builder par blocs + panneau latéral | **change de pattern** |
| Messages | plein écran | panneau secondaire | stacked → split-view |
| Analytics | sparkline+table | charts comparatifs multi-panels | reflow → split |

---

# 7. Screen-by-Screen Redesign

## 7.1 Dashboard Athlète / Today View

- **Objectif utilisateur** : « Qu'est-ce que je fais aujourd'hui, et tout va-t-il bien ? »
- **Priorité métier** : séance du jour + readiness + retour.
- **Above the fold** :
  1. **Hero séance du jour** (carte dominante) : type+titre, charge estimée, zone dominante, durée/distance, CTA « Voir / Démarrer ».
  2. **Readiness chip** compact à côté/au-dessus : « Forme : Bonne » + origine « calculé » (RPE+fatigue+charge), tap → détail des entrées.
- **Contenu (sous le fold)** :
  - **À surveiller** : alerte douleur/charge seulement si présente (callout), sinon absent.
  - **Retour rapide** post-séance (si séance du jour passée non clôturée) : entrée directe vers le flux 10 s.
  - **Messages coach** (1–2 derniers, si non lus).
  - **Progression simple** : 1 sparkline (charge ou forme), pas plus.
  - **Prochains événements** (2–3 jours).
  - **Sync status** discret en bas.
- **Hiérarchie** : « Faire maintenant » (hero) > « À surveiller » (alerte conditionnelle) > « Historique rapide » (progression/events).
- **Actions primaires** : ouvrir séance / noter retour. **Secondaires** : message, calendrier.
- **Mobile** : colonne, sticky « reprendre/noter » si pertinent. **Desktop** : (athlète rarement desktop) 2 colonnes hero+contexte.
- **Composants** : `metric card` (hero), `readiness-gauge`, `critical-alert-callout`, `quick reply`, `sparkline`, `offline sync status`, `fab`.
- **Vigilance métier** : readiness jamais = RPE seul ; afficher origine ; ne pas montrer 10 métriques.
- **Flux retour 10 s** : 1 écran → RPE (segments) + Fatigue (0–10) + Douleur (0–10 + localisation si >0) + commentaire optionnel → « Valider ». Auto-save offline. **Wow utile** : pré-remplissage intelligent (RPE suggéré depuis l'activité Strava importée, modifiable).
- ❌ Éviter : hero noyé sous des KPIs ; readiness sans origine ; retour multi-écrans.

## 7.2 Dashboard Coach

- **Objectif** : « Qui a besoin de moi maintenant, et quel est l'état de mon écurie ? »
- **Priorité métier** : signaux faibles + alertes + charge/compliance.
- **Pas un mur de cartes** → cockpit en zones :
  1. **Today Attention** (haut, dominant) : athlètes nécessitant une action aujourd'hui (douleur signalée, ACWR risque, séance ratée, feedback inquiétant). Liste priorisée, status chip + raison + action rapide.
  2. **Athlètes à risque** : ACWR > seuil, fatigue persistante, douleur — avec mini-sparkline et drill-down.
  3. **Charge & Compliance** : vue agrégée (compliance %, charge moyenne, séances à venir), filtrable par scope.
  4. **Club Overview** : répartition par groupe, volume, alertes agrégées (si périmètre club).
  5. **Actions rapides** : créer séance, message groupé, planifier.
- **Filtres scope persistants** : Mes athlètes / Privés / Club / Tous (toujours visibles, multi-tenant).
- **Drill-down sans perte de contexte** : clic athlète → **side panel** (overlay) avec l'essentiel + lien fiche complète ; le cockpit reste derrière.
- **Comparaison rapide** : sélection multiple → compare panel.
- **Mobile coach** : réduit à Today Attention + alertes (le reste en navigation). **Desktop** : multi-panels.
- **Composants** : `today-attention-list`, `athlete-status-chip`, `kpi-rail`, `acwr-indicator`, `compare panel`, `filter bar`, `side panel`.
- **Vigilance** : scope toujours affiché ; signaux faibles remontés, pas noyés ; couleur+icône+libellé.
- **Wow utile** : « 3 athlètes méritent ton attention aujourd'hui » résumé en une phrase actionnable en haut.
- ❌ Éviter : 20 KPI ; alertes sous le fold ; perte de scope.

## 7.3 Page Séance (course & force)

- **Objectif** : comprendre et exécuter exactement la séance ; renseigner le réalisé.
- **Structure (4 registres distincts et labellisés)** :
  1. **Header séance** : titre, type (route/trail/force), date, charge estimée, zone dominante, statut (à venir/fait), durée/distance.
  2. **Prescription** : blocs séquentiels (timeline) avec **range pills** (allure/FC/puissance min–max, %1RM, reps, RIR).
  3. **Pourquoi (explication)** : intention de séance, en bloc repliable court.
  4. **Exécution** : course = blocs à suivre ; force = exercices → set input rows.
  5. **Retour** : RPE/fatigue/douleur/commentaire (réutilise le flux athlète).
- **Lecture séquentielle** : rail de zone coloré à gauche, blocs du haut vers le bas.
- **Données calculées en avant** : charge estimée, zones (origin-tag « calculé »).
- **Réduction texte parasite** : explication repliée par défaut, prescription dominante.
- **Force — saisie par set** : `set input row` distinguant **prescrit** (range pill, lecture seule) / **cible** (valeur visée dans la fourchette) / **réalisé** (saisi). Calcul de charge live affiché.
- **Mobile** : timeline verticale + **sticky action bar** (« Démarrer » / « Noter retour »). **Desktop** : 2 colonnes (prescription | exécution/réalisé) ou panneau réalisé.
- **Composants** : `session timeline`, `session block card`, `range-prescription-pill`, `set input row`, `effort badge`, `sticky action bar`, `data-origin-tag`.
- **Vigilance (invariants)** : **athlète ne modifie pas le contenu** (édition désactivée, non-affordante) ; **fourchettes jamais en valeur sèche** ; distinction nette prescrit/cible/réalisé.
- **Wow utile** : superposition réalisé (Strava/GPX) vs prescrit avec zones — l'athlète voit s'il est resté dans la fourchette.
- ❌ Éviter : mur de texte ; champ unique de prescription ; prescription/réalisé confondus.

## 7.4 Calendrier

- **Objectif** : planifier (coach) / consulter et déplacer (athlète).
- **Priorité métier** : lecture des types, charge/densité, conflits, objectifs, indispos.
- **Vues** : mois / semaine / jour (segmented). **Semaine = vue par défaut coach.**
- **Chips multi-types** : séance clé / footing / muscu / objectif (race) / indisponibilité / note — couleur+icône distinctes (cf. §5.3), charge indiquée.
- **Charge/densité** : fond de cellule/jour teinté selon charge planifiée ; bande de charge hebdo sous la semaine ; **détection de conflits** (2 séances clés rapprochées, indispo chevauchant) surlignée.
- **Drag & drop desktop** (CDK DragDrop) : déplacer/réorganiser, drop zones valides surlignées, recalcul de charge au drop.
- **Mobile** : agenda vertical (jours empilés) ; déplacement par **long-press → sheet « déplacer vers… »** (sélecteur de date sans ambiguïté) plutôt qu'un drag fin.
- **Overlays détail** : clic chip → side panel (desktop) / sheet (mobile) avec résumé séance + accès page séance.
- **Filtres persistants** : type, scope, athlète/groupe (coach), persistés en URL.
- **Coach vs athlète** : coach planifie/édite/DnD libre ; **athlète déplace une séance (date) mais ne modifie pas le contenu** ; objectifs/indispos visibles selon droits.
- **Composants** : `calendar chip`, `draggable calendar chip`, `segmented control`, `filter bar`, `side panel`/`sheet`, bande de charge.
- **Vigilance** : multi-tenant (scope) ; conflits visibles ; invariant déplacement athlète.
- **Wow utile** : aperçu d'impact de charge lors du drag (« +12 % charge cette semaine »).
- ❌ Éviter : mur de pastilles grises ; pas de priorisation ; drag mobile imprécis.

## 7.5 Détail Athlète (fiche cockpit)

- **Objectif coach** : tout comprendre d'un athlète et agir vite.
- **Header sticky intelligent** : identité (photo, nom), **badge privé/club**, coach référent + permissions (si pertinent), **stat strip** (VDOT, LT2, CTL, ACWR, statut forme) en tnum. Reste visible au scroll.
- **Tabs** : Vue d'ensemble · Physiologie · Charge · Calendrier · Force · Messages.
- **Blocs rétractables** : profil physiologique (LT1/LT2/VC/VDOT + progression), charge (ATL/CTL/ACWR), historique séances, comparatif temporel (ex : ce mois vs précédent).
- **Surface d'action rapide coach** : créer séance, message, ajuster plan — en barre d'actions du header.
- **Métriques de référence** clairement affichées avec origin-tag (calculé/test labo/estimé).
- **Mobile** : header compact + tabs empilés. **Desktop** : header sticky + contenu + side actions.
- **Composants** : `stat strip`, `athlete-status-chip`, `lt-progression-chart`, `acwr-indicator`, `load-balance-bar`, `compare panel`, tabs.
- **Vigilance** : multi-tenant (afficher privé/club + permissions) ; distinguer test labo vs estimé.
- **Wow utile** : timeline de forme annotée (tests, blessures, objectifs) en haut de l'onglet Physio.
- ❌ Éviter : tout déplié (surcharge) ; métriques sans origine.

## 7.6 Force Training Builder

- **Objectif coach** : composer une séance de force structurée, sans formulaire de 200 champs.
- **Pattern : builder modulaire par blocs + panneau latéral d'édition.**
  - **Colonne centrale** : séance en blocs lisibles (échauffement / blocs de travail / accessoires / retour), chaque bloc listant ses exercices (exercise rows).
  - **Panneau latéral d'édition** (overlay/drawer) : éditer l'exercice sélectionné — format (séries×reps, EMOM, AMRAP, tempo), type de série, **prescription charge/effort en range** (%1RM ou charge min–max, RIR/RPE), `setconfig` dynamique (nb de sets → lignes générées), required fields signalés.
  - **Calcul de charge live** : tonnage/charge estimée mise à jour à chaque édition (valeur backend, pas inventée).
  - **Preview séance athlète** : bascule pour voir le rendu côté athlète (lecture seule, ranges).
  - **Mode simplifié / avancé** : simplifié = exos + séries×reps + charge ; avancé = tempo, RIR, setconfig fin, supersets.
- **Mobile** : mode lecture/ajustement léger (le build complet reste desktop/tablette).
- **Composants** : `session block card`, `exercise row`, `set input row`, `range-prescription-pill`, `effort badge`, side panel d'édition, segmented (simplifié/avancé).
- **Vigilance (invariant)** : prescription en fourchette ; criticité respectée (required fields bloquants) ; `canDeactivate` si non sauvegardé.
- **Wow utile** : duplication/templating de blocs (drag d'un template), progression auto suggérée semaine N+1.
- ❌ Éviter : formulaire monolithique ; valeur sèche ; perte de saisie.

## 7.7 Messagerie Temps Réel

- **Objectif** : communication contextualisée coach↔athlète, pas un chat isolé.
- **Structure** : liste conversations (par athlète/groupe) + thread.
  - **Rattachement contexte** : un message peut référencer une **séance / objectif / feedback** (chip cliquable → ouvre la séance en panel sans quitter le thread).
  - **Messages épinglés** en haut (consignes importantes).
  - **Quick replies** (chips suggérés contextuels).
  - **Pièces jointes légères** (image, GPX) avec preview.
  - **Indicateurs de lecture** (lu/non-lu, en cours de frappe).
- **Temps réel** : WebSocket/SSE → signals ; reconnexion auto ; file d'envoi offline (retry).
- **Mobile** : plein écran fluide, composer sticky bas, sheets pour pièces jointes. **Desktop** : panneau secondaire (à droite du module courant) → le coach répond sans quitter le dashboard/fiche.
- **Composants** : `message thread`, `message bubble`, `quick reply composer`, contexte chip, `offline sync status`.
- **Vigilance** : multi-tenant (jamais hors périmètre) ; chiffres/consignes critiques jamais ambigus ; états offline visibles.
- **Wow utile** : « répondre depuis une alerte dashboard » pré-remplit le contexte (athlète + séance concernée).
- ❌ Éviter : chat détaché du contexte ; perte de messages offline silencieuse.

---

# 8. Mobile-First UX (athlète)

- **Thumb ergonomics** : zone d'action basse, lecture haute, cibles ≥ 44px espacées.
- **Bottom sheets** : détails, sélecteurs (déplacer séance, choisir date), filtres — non bloquants, dismiss par swipe.
- **Sticky actions** : CTA principal toujours atteignable au pouce.
- **One-thumb navigation** : bottom nav + gestes ; rien de critique en haut.
- **Réduction des champs** : retour séance = 3 entrées + commentaire optionnel.
- **Saisie ultra rapide** : segments tactiles (RPE), steppers/échelles 0–10 (fatigue/douleur), clavier numérique pour la force, pré-remplissage intelligent.
- **Feedback post-séance < 10 s** : 1 écran, défaut pré-rempli, validation unique, auto-save offline.
- **Offline-first** : lecture séance + saisie retour fonctionnent hors-ligne (cache PWA + IndexedDB), synchro à la reconnexion.
- **Sync states** : indicateur clair (Hors-ligne / En attente N / Synchronisé), jamais d'échec silencieux.
- **Skeletons & pending** : app shell instantané, skeletons par composant, états pending sur actions différées.
- **Usage terrain/fatigue/soleil** : mode high-contrast outdoor (contraste+taille boostés), gros tap targets, couleurs saturées + libellés.
- **Hiérarchie tactile** : 1 action dominante visuellement plus grosse/contrastée.
- **PWA quasi-native** : standalone, splash, transitions natives, pull-to-refresh, install prompt, notifications push (rappels séance, message coach).

### Exemples concrets
- **Ouvrir sa séance en 1 geste** : ouverture app → Today → hero séance déjà visible → tap « Voir » (ou raccourci push « Ta séance du jour »).
- **Retour en < 10 s** : fin de séance → notif/FAB « Noter mon retour » → écran unique RPE+fatigue+douleur (pré-remplis) → « Valider ».
- **Déplacer une séance sans ambiguïté** : Calendrier → long-press sur la séance → sheet « Déplacer vers… » → choisir le jour → confirmation + (si coach a activé) info d'impact charge. Contenu inchangé (invariant).
- **Comprendre sa priorité du jour** : hero dominant + readiness chip ; si rien d'autre n'est urgent, l'écran ne montre qu'elle.

---

# 9. Desktop UX (coach)

- **Densité maîtrisée** : plus d'infos par écran mais air entre zones, alignement strict, tabular numbers.
- **Navigation latérale persistante** + **header contextuel** (scope, breadcrumb, actions, Cmd+K).
- **Filtres persistants** visibles (scope multi-tenant, type, groupe, période) → URL.
- **Compare mode** : sélection multiple d'athlètes/périodes → compare panel à échelles communes.
- **Vues multi-panels** : dashboard + side panel athlète + thread message simultanés.
- **Drill-down** en panneaux (jamais perdre le contexte parent).
- **Drag & drop confortable** : calendrier (planification), builder force (réordonner blocs) — poignées, drop zones, recalcul live.
- **Tables intelligentes** : tri, filtre, sparkline inline, status chip, sélection multiple, actions groupées.
- **Souris + clavier** : raccourcis — `Cmd+K` recherche, `J/K` naviguer la liste d'athlètes, `Enter` ouvrir, `Esc` fermer panel, `N` nouvelle séance, `M` message, `1–4` switch scope.
- **Sélection multiple** : message groupé, ajustement de charge, duplication de séance.
- **Responsive enhancement** (pas juste adaptation) : à `xl`, ajout de colonnes comparatives et d'un 2e panneau ; le coach gagne en capacité, pas seulement en largeur.

---

# 10. Micro-Interactions & Motion

- **Feedback** : tap → ripple/scale subtil (120ms) ; validation retour → check sobre (pas de confetti).
- **Transitions navigation** : slide/fade court (200ms) cohérent par plateforme (push natif mobile, fade desktop).
- **États de séance** : à venir → en cours → fait, transition de statut animée brièvement (couleur+icône).
- **Drag & drop** : ghost suivant le curseur/doigt, drop zones surlignées, snap au drop (ease-spring), recalcul de charge animé sur le compteur (count-up court).
- **Succès** : check + couleur teal, 200ms, pas plus.
- **Apparition panels/sheets** : slide depuis le bord (bottom sheet mobile, right panel desktop), 200–320ms, backdrop fade.
- **Transitions dashboard** : widgets apparaissent en stagger léger au load (≤ 60ms d'écart), skeletons → contenu en fondu.
- **Sync** : icône qui pulse pendant la synchro, transition vers ✓ à la fin.
- **Feedback tactile perçu** : micro-scale + (mobile) vibration légère sur validation critique.

### Règles
- Motion **au service de la compréhension** (où est passé l'élément, quel statut).
- **Jamais ralentir l'expert** : durées courtes, pas d'animation bloquante ; respect `prefers-reduced-motion`.
- **Jamais masquer une donnée critique** pendant une animation.
- Animations **courtes, nettes, sportives, premium**.
- **Hiérarchie motion mobile vs desktop** : mobile = transitions de page natives marquées ; desktop = transitions de panneaux/états plus discrètes (l'expert préfère l'instantané).

---

# 11. Accessibility & Critical Data Safety

- **Contrastes** : texte AA (4.5:1) ; **donnée physiologique critique AAA (7:1)**.
- **Taille min** : valeurs critiques ≥ 15px (Metric-S) ; libellés ≥ 13 ; jamais de donnée critique en micro 11.
- **Icône + texte** systématique sur statuts/alertes (jamais icône seule ni couleur seule).
- **Redondance sémantique des codes couleur** : couleur + icône + forme + libellé (zones, fatigue, douleur, ACWR). Mode haute-accessibilité avec patterns de remplissage.
- **Dashboards accessibles** : ordre de lecture logique, landmarks ARIA, charts avec résumé textuel/table alternative.
- **Mobile outdoor** : mode high-contrast, gros targets, anti-reflet (saturation+poids).
- **Focus/clavier** : focus visible (anneau accent 2px), ordre logique, tout actionnable au clavier, `Esc` ferme overlays, focus trap dans modals/sheets.
- **Charts accessibles** : `aria-label` + table de données alternative + tooltips clavier ; ne pas coder l'info uniquement par couleur de ligne.
- **Drag & drop accessible** : alternative clavier (déplacer via menu « déplacer vers… ») + annonces ARIA live au drop.
- **Donnée physiologique jamais ambiguë** : unité toujours affichée, pas d'arrondi trompeur, fourchettes explicites (min–max), zone toujours nommée.

### Distinction d'origine de donnée (composant `data-origin-tag`)
| État | Marqueur visuel | Exemple |
|---|---|---|
| Saisi (athlète) | icône ✎ + « saisi » | RPE 7 |
| Calculé (moteur) | icône ⚙ + « calculé » | ACWR 1,2 ; readiness |
| Estimé | icône ~ + « estimé » | VDOT depuis course |
| Mesuré (test labo) | icône 🧪 + « test » | LT2 16,2 km/h |
| Alerté | icône ⚠ + couleur alerte | douleur 8/10 |
| Complété | icône ✓ + « fait » | séance clôturée |

### Hiérarchies & prévention d'erreur
- **Critical data hierarchy** : Alerte (douleur/ACWR risque) > Décision du jour (séance/athlète à aider) > Référence physio > Contexte/historique.
- **Warning hierarchy** : info (bleu/inline) < warning (ambre/bandeau) < critique (rouge/callout bloquant la décision).
- **Error prevention** : validations inline (ranges cohérentes min<max, charge plausible), `canDeactivate` sur builder, désactivation des actions interdites (athlète : éditer contenu).
- **Confirmations nécessaires** : suppression de séance, message groupé, changement de scope ayant un effet large, déplacement d'objectif/course.
- **Validations inline** : immédiates, près du champ, message clair.
- **Logs visuels d'actions importantes** : journal d'audit (admin) ; côté coach, historique des modifications de plan affiché sur la fiche athlète.

---

# 12. Implementation Guidelines for Angular 17

### Structure des composants (standalone)
- Tous standalone, `ChangeDetectionStrategy.OnPush`, `input()`/`output()`/`model()` signals.
- Dumb components : `shared/ui`, `shared/data-viz` — aucune injection, view-models en entrée.
- Smart containers : pages features/* injectent services, exposent des `computed()`.

### Signals pour l'UI state
- Local UI state en `signal()` (ouverture sheet, onglet actif, sélection).
- État dérivé en `computed()` (ex : `attentionAthletes = computed(() => athletes().filter(isAtRisk))`).
- État partagé (scope coach, viewport, sync) en services à signals (`signalStore`-like maison ou `@ngrx/signals` si adopté).
- `effect()` pour synchroniser URL ↔ filtres (deep-linking), avec parcimonie.

### Smart vs dumb
- Container : `*.page.ts` (route component) → orchestre, fetch, mappe view-model.
- Presentational : `*.component.ts` dans `shared/*` → pur, testable isolément.

### Design tokens (CSS variables)
- `:root` (dark par défaut) + `[data-theme="light"]` override.
- Tokens groupés : couleurs, espacement, radius, ombres, motion, z-index, typo.
- **Couleurs sémantiques en tokens** (`--zone-z3`, `--acwr-risk`, `--fatigue`, …) — un seul endroit de vérité, réutilisé dans charts (via CSS vars lues en JS) et badges.

### Theming dark/light
- Toggle via attribut sur `<html>`, persistance (localStorage) ; respect `prefers-color-scheme` par défaut.
- Tous les composants consomment des tokens, zéro couleur en dur.

### Patterns
- **Layout** : shell par rôle, `router-outlet` principal + outlets nommés pour panels/sheets, ou `PanelService`+CDK overlay.
- **Dashboard widgets** : composant `widget` générique (header + slot + skeleton + error), chaque widget autonome avec son flux signal.
- **List virtualization** : `@angular/cdk/scrolling` (CdkVirtualScroll) sur listes longues (athlètes, messages, historique).
- **Skeleton loading** : composant `skeleton` paramétrable + skeletons dédiés par carte clé ; afficher dès le resolver.
- **Bottom sheet / drawer** : CDK Overlay + `BottomSheetService`/`DrawerService` à signals ; swipe-to-dismiss mobile.
- **Responsive shell** : `BreakpointObserver` → signal `viewport`, le shell choisit le composant (pattern switch, pas juste CSS).
- **Charts** : primitives maison légères (SVG) dans `shared/data-viz` (évite une grosse lib ; budget initial 500kb→1mb à respecter) ; lecture des tokens couleur via `getComputedStyle`. Sparklines en SVG pur.
- **Forms complexes (builder force)** : Reactive Forms typés + `FormArray` pour blocs/sets ; `canDeactivate` guard ; édition par panneau (sous-form isolé).
- **Calendar interactions** : `@angular/cdk/drag-drop` (déjà dispo) ; drop lists par jour ; recalcul de charge via service après drop (valeur backend).
- **Message realtime** : service WebSocket/SSE → signal stream ; `@for track msg.id` ; file offline (IndexedDB) + retry.
- **Partial offline** : service-worker (déjà présent) cache app shell + séances du jour ; IndexedDB pour feedback/messages en attente ; bandeau `offline-banner` (existant) + sync status.

### Conventions de nommage
- Pages : `today.page.ts`, `coach-dashboard.page.ts`.
- Dumb : `metric-card.component.ts`, `intensity-zone-badge.component.ts`.
- Services à signals : `scope.store.ts`, `sync.store.ts`, `realtime.service.ts`.
- Tokens : `--{categorie}-{nom}` (`--zone-z3`, `--space-16`, `--motion-base`).

### À créer en priorité (primitives à factoriser dès le départ)
1. Design tokens (CSS vars dark/light) + thème.
2. `shared/ui` : button, card, chip, badge, sheet, drawer, skeleton, sticky-action-bar, banner, callout, empty-state, segmented-control, filter-bar.
3. `shared/physiology` : `data-origin-tag`, `intensity-zone-badge`, `range-prescription-pill`, `readiness-gauge`, `acwr-indicator`, `effort-badge`, `pain-fatigue-selector`.
4. `shared/data-viz` : line/area/bar/sparkline + axis + threshold-band + legend.
5. Shells (athlete/coach/admin) + nav + `PanelService`/`SheetService`.

### Dette UX à éviter
- Couleurs en dur (casse le theming + la cohérence sémantique).
- Logique métier dans les dumbs/physiology (calculs côté front).
- Une lib de charts lourde non maîtrisée (budget, cohérence chromatique).
- Modals pour ce qui doit être un panneau (perte de contexte coach).
- Feedback offline silencieux.

### Approche incrémentale
Tokens & primitives → shells/nav → un écran phare par rôle (Today athlète + Dashboard coach) → propagation des composants aux écrans restants. Chaque écran refait consomme les primitives, jamais de one-off.

---

# 13. Refactor Priorities

Légende — Impact UX (I), Complexité (C), Dépendances (D), Valeur produit (V) ; échelle ●○○ (faible) → ●●● (fort).

| # | Chantier | I | C | D | V | Notes |
|---|---|---|---|---|---|---|
| **Quick wins** |
| 1 | Tabular numbers + échelle typo sur métriques existantes | ●●○ | ●○○ | — | ●●○ | gain de lisibilité immédiat |
| 2 | Sémantique couleur+icône+libellé sur statuts/alertes | ●●● | ●○○ | tokens | ●●● | sécurité data, accessibilité |
| 3 | Sync status + offline banner cohérents | ●●○ | ●○○ | SW existant | ●●○ | confiance athlète |
| **Fondations design system** |
| 4 | Design tokens (dark/light) + theming | ●●● | ●●○ | — | ●●● | prérequis de tout |
| 5 | `shared/ui` primitives | ●●● | ●●○ | 4 | ●●● | base réutilisable |
| 6 | `shared/physiology` (origin-tag, zone badge, range pill, readiness, acwr) | ●●● | ●●○ | 4,5 | ●●● | cœur métier UX |
| 7 | `shared/data-viz` primitives | ●●○ | ●●● | 4 | ●●○ | charts cohérents |
| **Shells & navigation** |
| 8 | Athlete shell + bottom nav + sheets | ●●● | ●●○ | 5 | ●●● | base mobile |
| 9 | Coach shell + sidebar + context-header + scope switcher | ●●● | ●●○ | 5 | ●●● | base desktop + multi-tenant |
| 10 | Admin shell sobre | ●○○ | ●○○ | 5 | ●○○ | rapide |
| **Écrans** |
| 11 | Today athlète (hero séance, readiness, retour 10 s) | ●●● | ●●○ | 6,8 | ●●● | écran phare athlète |
| 12 | Dashboard coach cockpit (attention/risque/charge/scope) | ●●● | ●●● | 6,7,9 | ●●● | écran phare coach |
| 13 | Page séance (4 registres, ranges, set rows, sticky bar) | ●●● | ●●○ | 6 | ●●● | invariants métier |
| 14 | Calendrier (responsive switch, DnD, charge, conflits) | ●●● | ●●● | 6,7,9 | ●●● | complexe, fort impact |
| 15 | Détail athlète (cockpit, header sticky, tabs) | ●●○ | ●●○ | 6,7,12 | ●●○ | drill-down coach |
| 16 | Strength builder (blocs + panneau + charge live) | ●●○ | ●●● | 6,13 | ●●○ | très complexe |
| 17 | Messagerie temps réel (contextuelle, panneau desktop) | ●●○ | ●●● | 5,9 | ●●○ | realtime + offline |
| 18 | Analytics avancées (compare, charge, LT progression) | ●●○ | ●●● | 7,15 | ●●○ | profondeur coach |

### Séquencement recommandé
**Sprint 0** : 1–3 (quick wins) en parallèle de 4. **Sprint 1** : 5–7 (primitives). **Sprint 2** : 8–10 (shells). **Sprint 3** : 11 + 13 (athlète phare). **Sprint 4** : 12 + 15 (coach phare). **Sprint 5** : 14 (calendrier). **Sprint 6+** : 16, 17, 18.

> Règle d'or de la refonte : **chaque nouvel écran consomme les primitives `shared/*` ; aucun composant one-off, aucune couleur en dur, aucun calcul métier côté front.**
