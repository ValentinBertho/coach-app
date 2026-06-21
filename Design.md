# Design.md — Design System réutilisable

> Système de design extrait de *Mon Petit Atelier* (« **Forge Design System v2** »).
> Pensé comme une base directement réutilisable : tokens CSS, composants, patterns UX, états.
> Les sections _(hypothèse)_ sont des déductions issues des choix observés dans le code.

---

## 1. Philosophie & identité visuelle

**Direction artistique** : _« atelier chaleureux & artisanal »_ — un univers **warm, tactile, confiant**,
à l'opposé du SaaS bleu-froid générique. Métaphores de l'établi : surfaces « papier » (`--paper`),
encre (`--ink`), tickets perforés (`.ticket`), cartes « nuit » premium (`.card-night`).

Principes directeurs :

- **Chaleur** : palette beige/orange forgé, ombres teintées chaud (jamais de gris neutre froid).
- **Tactilité** : boutons en pilule, `:active { transform: scale(0.97) }`, ressorts (`--ease-spring`).
- **Confiance** : typographie display affirmée, contrastes nets, hiérarchie claire.
- **Mobile-first** : tout écran fonctionne d'abord sur téléphone (bottom-nav, cibles ≥44px).
- **Tokens d'abord** : aucune valeur en dur — tout passe par des variables CSS (`var(--…)`).

---

## 2. Couleurs (design tokens)

> Tous les tokens sont déclarés dans `:root` de `styles.scss`. **Toujours référencer le token**, jamais l'hex.

### Surfaces (papier)
| Token | Valeur | Usage |
|---|---|---|
| `--canvas` | `#F4EFE7` | Fond de page |
| `--paper` | `#FBF7F1` | Surface par défaut |
| `--paper-2` | `#FFFFFF` | Surface surélevée (cards) |
| `--paper-sunk` | `#EFE9DF` | Surface enfoncée |
| `--bg-overlay` | `rgba(20,17,14,.48)` | Overlay modale |

### Encre (texte) — 4 niveaux
| Token | Valeur | Usage |
|---|---|---|
| `--ink` | `#15110D` | Texte principal |
| `--ink-2` | `#4A3F36` | Texte secondaire |
| `--ink-3` | `#847569` | Texte atténué / muted |
| `--ink-4` | `#B8A99B` | Placeholder |

### Marque & accent
| Token | Valeur | Usage |
|---|---|---|
| `--primary` / `--orange` | `#E8531A` | Orange forgé — couleur signature, CTA |
| `--primary-hover` | `#B83A0A` | Hover/pressé |
| `--primary-light` | `#FCE4D5` | Fond teinté |
| `--primary-wash` | `#FFF4EC` | Fond très léger (états actifs sidebar) |
| `--accent` | `#C97A0F` | Ambre chaud (accent secondaire) |
| `--night` | `#14110E` | Surfaces premium / boutons secondaires |

### Statuts (harmonisés chaud) — chacun avec `bg` / `text` / `border`
| Statut | Base | Fond | Texte |
|---|---|---|---|
| Succès | `--success #2D6A4F` | `--success-bg #DEEEDF` | `--success-text #1B4332` |
| Avertissement | `--warning #C97A0F` | `--warning-bg #FBEDD4` | `--warning-text #7A4A09` |
| Danger | `--danger #B2382A` | `--danger-bg #F8DCD7` | `--danger-text #7A1F14` |
| Info | `--info #2F5D86` | `--info-bg #D8E4EF` | `--info-text #1E3F5F` |

**Mapping statut métier → couleur** (badges) :
- 🟢 Vert : `CONFIRMED, COMPLETED, PAID, ACCEPTED, INVOICED`
- 🟠 Ambre : `PENDING, WAITING_PARTS, PARTIALLY_PAID` (point animé sur `WAITING_PARTS`)
- 🔴 Rouge : `CANCELLED, REFUSED, OVERDUE`
- 🔵 Bleu : `IN_PROGRESS, ISSUED`
- 🟣 Prune (`#ECDDEC`/`#6B3A6B`) : `SENT, CREDIT_NOTE`
- ⚪ Neutre : `DRAFT, EXPIRED`

---

## 3. Typographie

Trois familles via Google Fonts, exposées en tokens :

| Token | Police | Usage |
|---|---|---|
| `--font-display` | **Bricolage Grotesque** (fallback Poppins) | Titres `h1–h6`, classes `.display-*` |
| `--font-ui` | **Geist** (fallback Inter) | Corps, UI, formulaires |
| `--font-mono` | **JetBrains Mono** | Code, numéros (`.mono`, `.tnum`) |

- Base body : `14px`, `line-height 1.6`, anti-aliasing activé, `font-feature-settings: "ss01","ss03","cv11"`.
- Titres : `letter-spacing -0.025em`, `line-height 1.25`.
- Échelle (tokens) : `--text-xs .75rem` → `--text-3xl 2rem`. Display utilitaires : `.display-lg 56px`, `.display-md 32px`, `.display-sm 24px`.
- Eyebrow : `.label-eyebrow` (9.5px, uppercase, `letter-spacing .10em`).
- Nombres tabulaires : `.tnum` (`font-variant-numeric: tabular-nums`) pour montants/totaux.
- **Mobile** : `input/select/textarea` forcés à `16px` (anti-zoom iOS).

---

## 4. Espacements, rayons, ombres

### Grille d'espacement — base 4px
`--sp-1 4px` · `--sp-2 8px` · `--sp-3 12px` · `--sp-4 16px` · `--sp-5 20px` · `--sp-6 24px` · `--sp-8 32px` · `--sp-10 40px` · `--sp-12 48px` · `--sp-16 64px`.

### Rayons (généreux)
`--radius-xs 6px` · `--radius-sm 10px` · `--radius 14px` · `--radius-lg 20px` · `--radius-xl 28px` · `--radius-2xl 36px` · `--radius-full 9999px` (pills).

### Ombres (teintées chaud)
`--shadow-xs` → `--shadow-lg`, plus `--shadow-colored` (lueur orange CTA), `--shadow-night` (cards premium), `--shadow-inset`. Toujours via token — pas d'ombre custom.

### Conteneurs de largeur
`.container-narrow` (≤720px) · `.container-standard` (≤900px) · `.container-wide` (≤1200px), pattern `min(Xpx, 100% - 32px)`.

---

## 5. Composants UI récurrents

### Boutons — pilule, hauteur 48px
`.btn` (base, `border-radius: full`, `:active scale(.97)`, `:focus-visible` ring orange) + variantes :
`.btn-primary` (orange + lueur) · `.btn-secondary` (night) · `.btn-danger` / `.btn-danger-ghost` · `.btn-success` · `.btn-ghost` (bordure) · `.btn-accent` (ambre).
Tailles : `.btn-sm 36px` · `.btn-lg 56px` · `.btn-xl 60px` · `.btn-icon` (rond 44px) · `.btn-block` (100%).

### Cards
`.card` (paper-2, bordure `--line`, `radius-lg`, padding 20px). Modificateurs : `.card-hover` (élévation), `.card-interactive` (bordure orange + ring au hover). `.card-night` (premium sombre). `.ticket` + `.ticket-dash` (effet ticket perforé). `.card-header` (titre + actions, séparateur).

### Formulaires
`.form-group` (label + champ), `.form-control` (bordure 1.5px, focus ring orange `--primary-ring`, état `.ng-invalid.ng-touched` rouge). `select.form-control` avec chevron SVG inline. Grilles `.form-row` (2 col) / `.form-row-3` (3 col, responsive). `.form-hint` / `.field-hint` / `.field-optional` / `.error-message`.

### Badges (status pills)
`.badge` (pill 24px, point coloré `::before`). Classe par statut `.badge-<STATUS>` (insensible casse/format). Voir mapping § 2.

### Tables
`.table-container` (scroll horizontal, bordure, radius). `th` uppercase muted sur fond enfoncé, lignes au hover, `.actions-cell` (boutons inline).

### Navigation par statut
`.status-tabs` (scroll horizontal sans scrollbar) + `.status-tab` (pill, `.active` = wash orange) avec `.tab-count` (compteur).

### Autres composants clés
- **Recherche** : `.search-bar` + `.search-input` (icône loupe en CSS pur).
- **Pagination** : `.pagination` (boutons carrés, `.active` orange).
- **Avatar** : `.avatar` (initiales, 6 variantes de tons déterministes `.avatar-1..6`), tailles `sm/lg`.
- **Icon box** : `.icon-box` + variantes colorées (`.icon-primary`, `.icon-success`…).
- **Quick action** : `.quick-action` (tuile dashboard, hover translateY + ring).
- **Timeline** : `.timeline` / `.timeline-item` (ligne dégradée + pastilles colorées).
- **Alert** : `.alert` + `.alert-{info,success,warning,danger}` (bordure gauche 4px).
- **Stat card** : `.stats-grid` (auto-fit minmax 180px) + composant `app-stat-card`.
- **Chips templates** : `.msg-templates` / `.msg-tpl-btn` (messages pré-rédigés, scroll horizontal mobile).

### Composants Angular partagés (`shared/components/`)
`toast`, `confirm-dialog`, `modal`, `searchable-select`, `client-selector`, `vehicle-selector`,
`line-items-editor`, `badge`, `card`, `stat-card`, `empty-state`, `spinner`, `skeleton-list`,
`skeleton-card`, `sparkline`, `breadcrumb`, `command-palette`, `internal-notes`, `cookie-banner`,
`pwa-install-banner`. → **Réutiliser avant de créer.**

---

## 6. Conventions UX

- **Feedback obligatoire** : toast sur chaque action (succès vert / erreur rouge). Jamais `alert()`.
- **Confirmations** : `ConfirmDialogService` (modale stylée) plutôt que `confirm()` natif ; afficher l'avant/après pour les actions sensibles (ex. déplacement de RDV).
- **Actions destructrices** : double garde pour l'irréversible (saisie du nom exact + revalidation back).
- **Cibles tactiles ≥44px**, boutons pleine largeur empilés sur mobile (`.actions-row-mobile`).
- **Pré-remplissage contextuel** : enchaînement d'écrans via queryParams (`?clientId=…`).
- **Filtrage** : onglets de statut + recherche + pagination serveur, partout pareil.
- **Layout détail 2 colonnes** desktop (contenu principal + sidebar contextuelle), empilé mobile.
- **Auto-save brouillon** (localStorage, debounce ~2s) + bannière de restauration sur les formulaires longs.
- **Labels en français**, statuts traduits via pipe dédié (`StatusLabelPipe`).

---

## 7. Animations & easing

Tokens de timing :
| Token | Valeur | Usage |
|---|---|---|
| `--ease` | `cubic-bezier(.32,.72,0,1)` | Transition par défaut |
| `--ease-spring` | `cubic-bezier(.34,1.4,.64,1)` | Entrées tactiles (toasts) |
| `--ease-out` | `cubic-bezier(0,0,.2,1)` | Sorties |
| `--duration-fast` | `110ms` | Micro-interactions |
| `--duration` | `180ms` | Standard |
| `--duration-slow` | `280ms` | Apparitions |

Keyframes prêtes : `fadeIn`, `slideIn`, `slideInUp`, `slideInLeft`, `toastIn`, `bounceIn`, `shake` (erreurs), `pulse`, `pulseDot` (badges actifs), `shimmer` (skeletons), `spin`.
Classes utilitaires : `.fade-in`, `.slide-in-up`, `.bounce-in`, `.shake`, `.pulse`, `.celebration` (🎉).
**Règle** : animations subtiles et rapides (≤280ms), au service du feedback, jamais décoratives gratuites.

---

## 8. États d'interface (à toujours couvrir)

| État | Pattern | Composant |
|---|---|---|
| **Loading initial** | Skeleton shimmer (préféré au spinner) | `app-skeleton-list` / `app-skeleton-card`, `.skeleton` |
| **Loading inline** | Spinner | `.spinner` (`sm`/`lg`), `.loading` |
| **Vide** | Icône pointillée + titre + sous-texte + CTA | `app-empty-state`, `.empty-state` |
| **Erreur** | Toast rouge (global via intercepteur) + `.alert-danger` inline si contextuel | `ToastService.error()` |
| **Erreur champ** | Bordure rouge `.ng-invalid.ng-touched` + `.error-message` | — |
| **Succès** | Toast vert, parfois `.celebration` | `ToastService.success()` |
| **Offline / réseau** | Toast « Erreur réseau » (status 0) | intercepteur d'erreur |
| **Session expirée** | Logout + toast + redirect login (401) | intercepteur d'erreur |

---

## 9. Structure des pages & hiérarchie visuelle

### Squelette de page type
```
.page-header
  ├── h1 (display) + .subtitle
  └── .actions (boutons)
.status-tabs            (filtres optionnels)
.search-bar             (recherche optionnelle)
[contenu : .card / table / liste]
.pagination
```
- **Header** : titre display + sous-titre muted à gauche, actions à droite ; empilé sur mobile.
- **Hiérarchie** : display (titre) → `--text-md/lg` semibold (sections) → corps 14px → muted pour le secondaire.
- **Page de détail** : header full-width (numéro + statut + liens), puis grille 2 colonnes (principal/sidebar) → stack mobile.
- **Mobile** : `.bottom-nav` fixe (~72px, `safe-area-inset`), pas de sidebar ; toasts ancrés en bas ; FAB sur les listes.

### Sidebar (desktop)
Tokens dédiés (`--sidebar-bg`, `--sidebar-active-bg` = wash orange, `--sidebar-active-indicator`). Items masqués selon les **modules actifs** du tenant.

---

## 10. Responsive & accessibilité

- **Breakpoints** : `≤480px` (petit mobile), `≤600px` (form-row→1col), `≤768px` (mobile, bottom-nav), `≥768px` (desktop : drag&drop, split-view), `≥960px` (grilles détail 2 col).
- **Utilitaires** : `.hide-mobile` / `.show-mobile`, `.actions-row-mobile`.
- **A11y** : `:focus-visible` ring orange systématique ; pinch-zoom conservé (seul le double-tap zoom est désactivé) ; contrastes texte ink respectés ; cibles ≥44px.
- **PWA** : service worker activé en prod, bannière d'installation, manifest/icônes générés.

---

## 11. Utilitaires (extraits)

Texte : `.text-muted/.text-secondary/.text-success/.text-danger/.text-warning`, `.text-right/.text-center`, `.font-medium/.font-semibold/.font-bold`, `.truncate`.
Layout : `.flex`, `.flex-col`, `.items-center`, `.justify-between/.justify-center`, `.gap-1..6`, `.w-full`, `.grow`.
Espacement : `.mt-0..6`, `.mb-0..6`.
Dégradés : `.gradient-warm`, `.gradient-cool`, `.gradient-success`.

---

## 12. Checklist « nouveau composant conforme »

- [ ] Utilise **uniquement** des tokens CSS (`var(--…)`), aucune valeur en dur.
- [ ] Boutons en pilule, focus ring orange, `:active scale`.
- [ ] Couvre loading (skeleton), vide (empty-state), erreur (toast/alert).
- [ ] Responsive ≤768px (cibles ≥44px, stack, inputs 16px).
- [ ] Statuts via badges mappés ; libellés en français.
- [ ] Feedback toast sur action ; confirmations via `ConfirmDialogService`.
- [ ] Animations ≤280ms avec easing tokenisé.

---

*Forge Design System v2 — réutilisable tel quel ; adapter la palette de marque (`--primary`) et les familles de police pour rebrander.*
