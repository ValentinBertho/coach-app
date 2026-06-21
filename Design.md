# Design.md — Design System (Plateforme de coaching course à pied)

> Design system réutilisable pour une app de coaching running (« **Pace Design System** »).
> Reprend la structure tokenisée éprouvée du SaaS source, avec une **identité athlétique** :
> énergie, performance, lisibilité des données (allures, FC, charge).
> Sections _(hypothèse)_ = propositions cohérentes, à ajuster selon la marque du coach/club.

---

## 1. Philosophie & identité visuelle

**Direction artistique** : _« track & data »_ — un univers **dynamique, focalisé, sportif**, qui met en
valeur **la donnée** (allure, fréquence cardiaque, charge) sans surcharge. Deux ambiances :

- **App coach (back-office)** : claire, dense, efficace — beaucoup de tableaux/calendriers/graphes.
- **App athlète (PWA mobile)** : plus immersive, surfaces sombres « night track » possibles, focus sur la séance du jour et le ressenti.

Principes directeurs :
- **Énergie maîtrisée** : un accent vif (effort) sur une base sobre — jamais d'arc-en-ciel.
- **Données d'abord** : chiffres en tabulaire (`tnum`), unités claires (km, /km, bpm, m D+), zones colorées.
- **Tactilité** : boutons pleine confiance, `:active scale`, micro-animations de progression.
- **Mobile-first athlète** : bottom-nav, cibles ≥44px, lecture rapide « séance du jour ».
- **Tokens d'abord** : tout passe par `var(--…)`, aucune valeur en dur → rebrand facile par club.

---

## 2. Couleurs (design tokens)

### Surfaces
| Token | Valeur _(hypothèse)_ | Usage |
|---|---|---|
| `--canvas` | `#F6F7FB` | Fond de page (app coach) |
| `--paper` | `#FFFFFF` | Surface par défaut |
| `--paper-2` | `#FFFFFF` | Cards surélevées |
| `--paper-sunk` | `#EEF1F7` | Surface enfoncée |
| `--night` | `#0E1422` | Surfaces premium / app athlète / hero |
| `--night-2` | `#161E30` | Surface sombre surélevée |
| `--bg-overlay` | `rgba(10,16,26,.55)` | Overlay modale |

### Encre (texte) — 4 niveaux
`--ink #0E1422` · `--ink-2 #36405A` · `--ink-3 #6B7590` (muted) · `--ink-4 #A7AFC4` (placeholder).
Sur fond sombre : `--night-text #EAF0FF` · `--night-text-2 #9AA6C2`.

### Marque & énergie
| Token | Valeur _(hypothèse)_ | Usage |
|---|---|---|
| `--primary` | `#3D5AFE` (electric blue) | Marque, CTA, allure cible |
| `--primary-hover` | `#2D45D6` | Hover/pressé |
| `--primary-light` | `#E0E5FF` | Fond teinté |
| `--primary-wash` | `#F1F3FF` | États actifs (nav/onglet) |
| `--accent` (Go) | `#16C47F` (green) | Réalisé/positif/progression |
| `--energy` (Effort) | `#FF5630` (coral) | Effort intense / alerte surcharge |

### Zones d'intensité (FC / allure) — code couleur **canonique**
| Zone | Token | Couleur | Sens |
|---|---|---|---|
| Z1 | `--zone-1` | `#7C8AA5` (gris-bleu) | Récupération |
| Z2 | `--zone-2` | `#16C47F` (vert) | Endurance fondamentale |
| Z3 | `--zone-3` | `#F2B807` (jaune) | Tempo / marathon |
| Z4 | `--zone-4` | `#FF8A1E` (orange) | Seuil |
| Z5 | `--zone-5` | `#E5392E` (rouge) | VO2max / anaérobie |

### Statuts (chacun `bg` / `text` / `border`)
| Statut | Base | Fond | Texte |
|---|---|---|---|
| Succès | `--success #119D6B` | `--success-bg #D8F3E7` | `#0B5E40` |
| Avertissement | `--warning #C77A0A` | `--warning-bg #FBEED2` | `#7A4A06` |
| Danger | `--danger #DC3024` | `--danger-bg #FBE0DD` | `#8A1810` |
| Info | `--info #3D5AFE` | `--info-bg #E0E5FF` | `#1E2A9E` |

**Mapping statut métier → badge** :
- 🟢 Vert : `COMPLETED`, `MATCHED`, `ACTIVE` (plan), `DONE` (course)
- 🔵 Bleu : `PLANNED`, `UPCOMING`, `IMPORTED`
- 🟠 Ambre : `PARTIAL`, `PAST_DUE`, charge « élevée »
- 🔴 Rouge : `MISSED`, `CANCELLED`, alerte surcharge / `REVOKED`
- 🟣 Violet/Or : `RACE` (séance course / objectif A) — accent spécial
- ⚪ Neutre : `DRAFT`, `ARCHIVED`, `REST`

---

## 3. Typographie

| Token | Police _(hypothèse)_ | Usage |
|---|---|---|
| `--font-display` | **Space Grotesque** / Clash Display (fallback Poppins) | Titres, gros chiffres (chrono, distance) |
| `--font-ui` | **Inter** / Geist | Corps, UI, formulaires |
| `--font-mono` | **JetBrains Mono** | Métriques, allures, FC (toujours `tnum`) |

- Base body 14px, `line-height 1.6`, anti-aliasing.
- Titres : `letter-spacing -0.025em`.
- Échelle tokens : `--text-xs .75rem` → `--text-3xl 2rem`. Display : `.display-lg 56px` (ex. chrono objectif), `.display-md 32px`, `.display-sm 24px`.
- **Métriques** : `.metric` (mono, tabular-nums) pour allures `4:35 /km`, FC `156 bpm`, distance `12.4 km`, D+ `340 m`.
- **Mobile** : inputs forcés à 16px (anti-zoom iOS).

---

## 4. Espacements, rayons, ombres

- **Espacement base 4px** : `--sp-1 4px` → `--sp-16 64px`.
- **Rayons** : `--radius-sm 10px` · `--radius 14px` · `--radius-lg 20px` · `--radius-xl 28px` · `--radius-full` (pills/badges/avatars).
- **Ombres** : `--shadow-xs` → `--shadow-lg`, `--shadow-colored` (lueur bleue CTA), `--shadow-night` (cards sombres).
- **Conteneurs** : `.container-narrow ≤720px` · `.container-standard ≤900px` · `.container-wide ≤1200px` (calendrier/graphes).

---

## 5. Composants UI

### Génériques (repris du socle)
- **Boutons** pilule 48px : `.btn` + `.btn-primary` (bleu), `.btn-accent` (vert « Valider la séance »), `.btn-energy` (coral), `.btn-ghost`, `.btn-danger`, `.btn-icon`, tailles `sm/lg/xl`.
- **Cards** : `.card`, `.card-interactive` (ring bleu au hover), `.card-night` (athlète/hero).
- **Formulaires** : `.form-group`, `.form-control` (focus ring bleu), grilles `.form-row`/`.form-row-3`, `.error-message`, `.field-hint`.
- **Badges** : `.badge` + `.badge-<STATUS>` (mapping § 2).
- **Tables**, **pagination**, **status-tabs**, **search-bar**, **avatar** (initiales athlète), **alert**, **toast**, **timeline**, **stat-card** (`.stats-grid`).

### Spécifiques au coaching (à créer)
| Composant | Rôle |
|---|---|
| `app-training-calendar` | Grille semaine × jour de séances, glisser-déposer pour replanifier, vue mois/semaine, total volume hebdo |
| `app-workout-card` | Pastille de séance dans le calendrier : type (icône), durée/distance cible, barre de zone, badge statut |
| `app-workout-step-editor` | Éditeur d'étapes structurées (échauffement / répétitions ×N / récup / RAC) avec cible par zone |
| `app-zone-bar` | Barre horizontale de répartition Z1→Z5 (couleurs canoniques) |
| `app-load-chart` | Graphe charge d'entraînement (CTL/ATL/forme) — `sparkline` réutilisable pour les versions compactes |
| `app-metric-tile` | Tuile métrique : valeur mono + unité + libellé + delta (vs prévu / semaine précédente) |
| `app-rpe-selector` | Sélecteur de ressenti d'effort 1→10 (échelle colorée Z1→Z5) côté athlète |
| `app-wellness-form` | Saisie quotidienne : sommeil, fatigue, courbatures, humeur, FC repos (sliders/emoji) |
| `app-planned-vs-actual` | Comparatif prévu/réalisé d'une séance (écart distance/temps/allure) |
| `app-athlete-selector` | Sélecteur d'athlète filtrable (réutilise le pattern `searchable-select`) |
| `app-race-countdown` | Compte à rebours objectif (J-XX) + chrono visé |

---

## 6. Conventions UX

- **« Séance du jour » en première vue athlète** : ce qu'il doit faire aujourd'hui, en un écran.
- **Prévu vs réalisé toujours visible** : couleur verte si conforme, ambre si écart, rouge si manqué.
- **Glisser-déposer** pour replanifier (desktop) ; appui long → menu (mobile) ; confirmation avec avant/après.
- **Feedback obligatoire** : toast sur chaque action ; `ConfirmDialogService` pour confirmer (jamais `confirm()`).
- **Données lisibles d'un coup d'œil** : unités explicites, zones colorées, chiffres en mono tabulaire.
- **Saisie rapide athlète** : RPE en 1 tap, commentaire optionnel, formulaire wellness < 20s.
- **Pré-remplissage contextuel** via queryParams (`?athleteId=…&date=…`).
- **Libellés français**, statuts/zones traduits via pipes.

---

## 7. Animations & easing

Tokens : `--ease cubic-bezier(.32,.72,0,1)`, `--ease-spring` (entrées tactiles), durées `--duration-fast 110ms` / `--duration 180ms` / `--duration-slow 280ms`.
Keyframes : `fadeIn`, `slideInUp`, `toastIn`, `bounceIn`, `shake` (erreurs), `pulse`/`pulseDot` (séance du jour), `shimmer` (skeletons), `spin`, et **`progressFill`** _(hypothèse)_ pour animer les barres de zone / d'objectif.
Classes : `.celebration` (🎉) sur séance validée / objectif atteint. **Animations ≤280ms, au service du feedback.**

---

## 8. États d'interface (à toujours couvrir)

| État | Pattern |
|---|---|
| **Loading** | Skeleton shimmer (`app-skeleton-list/card`, `.skeleton`) plutôt que spinner |
| **Vide** | `app-empty-state` (ex. « Aucune séance cette semaine — Planifier ») |
| **Erreur** | Toast rouge global (intercepteur) + `.alert-danger` inline |
| **Erreur champ** | `.ng-invalid.ng-touched` + `.error-message` |
| **Succès** | Toast vert, `.celebration` (séance validée / record) |
| **Sync en cours** | État dédié sur l'import Strava/Garmin (spinner + « Dernière sync il y a … ») |
| **Non rapproché** | Badge « activité non rattachée » (`UNMATCHED`) avec action de rapprochement manuel |
| **Offline / 401** | Toast réseau (status 0) ; logout + redirect (401) via intercepteur |

---

## 9. Structure des pages & hiérarchie

### Squelette type (app coach)
```
.page-header  (h1 + .subtitle | .actions)
.status-tabs  (filtres : statut séance / groupe)
.search-bar
[contenu : calendrier / table athlètes / graphes]
.pagination
```

### Écrans clés
- **Dashboard coach** : KPI (athlètes actifs, séances à valider, charge moyenne), séances du jour, alertes (surcharge / non-conformité).
- **Fiche athlète** : hero (avatar, objectifs, prochaine course) + onglets *Plan / Activités / Charge / Wellness / Messages*. Layout 2 colonnes desktop (principal + sidebar contextuelle), stack mobile.
- **Éditeur de plan / séance** : calendrier + panneau d'édition d'étapes structurées.
- **App athlète (PWA)** : `.bottom-nav` (Aujourd'hui / Calendrier / Mes activités / Messages / Profil), surfaces `night` possibles, « séance du jour » mise en avant.

### Hiérarchie visuelle
Display (titre/chrono) → sections semibold `--text-md/lg` → corps 14px → muted secondaire. Les **métriques** sont l'élément le plus saillant après le titre (gros mono coloré par zone).

---

## 10. Responsive & accessibilité

- **Breakpoints** : `≤480px`, `≤768px` (athlète mobile, bottom-nav), `≥768px` (coach : drag&drop, split-view), `≥960px` (calendrier/graphes 2 col).
- **A11y** : `:focus-visible` ring bleu ; couleurs de zone **doublées d'un libellé/texte** (ne jamais coder l'info uniquement par la couleur — daltonisme) ; cibles ≥44px ; pinch-zoom conservé.
- **PWA** : service worker, bannière d'installation (clé côté athlète), offline-friendly pour consulter la séance du jour _(hypothèse)_.

---

## 11. Checklist « composant conforme »

- [ ] Tokens CSS uniquement (rebrandable par club).
- [ ] Métriques en mono/tabulaire avec unités ; zones via couleurs canoniques **+ libellé**.
- [ ] Couvre loading (skeleton), vide, erreur, état sync/non-rapproché si pertinent.
- [ ] Responsive ≤768px (cibles ≥44px, inputs 16px).
- [ ] Statuts via badges mappés ; libellés FR.
- [ ] Feedback toast ; confirmations via `ConfirmDialogService`.
- [ ] Animations ≤280ms tokenisées.

---

*Pace Design System — réutilisable tel quel ; rebrander en changeant `--primary`/`--accent`, la palette de zones
restant un standard métier. Prévoir un thème sombre pour l'app athlète.*
