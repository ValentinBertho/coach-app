# Design.md — Charte graphique & Design System de **Dies**

> **« Écritoire »** — le design system de Dies. Univers *papier & encre* : sobre, dense, lisible,
> imprimable. Il reprend la structure tokenisée de DARI Lab (tout passe par `var(--…)`), avec une
> identité opposée : là où l'app de coaching est énergique et colorée, celle-ci est **calme, sérieuse et
> hiérarchisée**. Sections _(hypothèse)_ = propositions à ajuster avec l'utilisatrice.

---

## 1. Philosophie & identité visuelle

**Direction artistique** : *le dossier bien tenu*. Un fond ivoire de papier, une encre bleu-nuit, un
filet de laiton pour les accents. L'outil doit inspirer **la fiabilité**, pas l'enthousiasme.

Cinq principes, dans cet ordre :

1. **La date est l'information reine.** Elle est toujours lisible, toujours au format `JJ/MM/AAAA`, toujours accompagnée du temps restant (« dans 12 jours »). Jamais de format ambigu, jamais de date sans contexte.
2. **L'urgence se voit avant de se lire.** Une échelle d'urgence canonique (§ 2.3) traverse tout le produit, doublée d'une icône et d'un libellé — **jamais la couleur seule**.
3. **Densité maîtrisée.** Elle travaille sur des listes de 40 lignes, pas sur des tuiles décoratives. Interlignes serrés, mais respiration entre les blocs.
4. **Rien ne clignote.** Aucune animation gratuite, aucune couleur vive hors alerte. Un outil consulté trois fois par jour pendant dix ans ne doit pas fatiguer.
5. **Ça doit s'imprimer.** La vue mois et la fiche dossier ont une feuille de style d'impression propre, en noir et blanc lisible.

---

## 2. Couleurs (design tokens)

### 2.1 Surfaces & encre

| Token | Valeur _(hypothèse)_ | Usage |
|---|---|---|
| `--canvas` | `#F7F5F1` | Fond de page — ivoire papier |
| `--paper` | `#FFFFFF` | Cartes, tableaux, panneaux |
| `--paper-sunk` | `#EFECE6` | Surfaces enfoncées, en-têtes de tableau |
| `--paper-line` | `#E3DED5` | Filets, séparateurs, bordures |
| `--night` | `#141C2B` | Barre latérale, pied de page, surfaces sombres |
| `--overlay` | `rgba(20,28,43,.55)` | Fond de modale |

**Encre — 4 niveaux** : `--ink #141C2B` · `--ink-2 #3A465C` · `--ink-3 #6D7789` (secondaire) ·
`--ink-4 #A3AAB8` (indications). Sur fond sombre : `--night-text #EDEAE3`.

### 2.2 Marque

| Token | Valeur _(hypothèse)_ | Usage |
|---|---|---|
| `--primary` | `#24406B` (bleu encre) | Marque, actions principales, liens |
| `--primary-hover` | `#1B3153` | Survol / pressé |
| `--primary-light` | `#DCE4F0` | Fonds teintés, sélection |
| `--primary-wash` | `#F0F3F8` | Onglet actif, ligne survolée |
| `--brass` | `#A6802E` (laiton) | Accent discret : dossier stratégique, jalon important, sceau |
| `--brass-light` | `#F3E9D2` | Fond d'accent |

### 2.3 Échelle d'urgence — **code couleur canonique du produit**

C'est l'équivalent des zones d'intensité de DARI Lab : **une seule échelle, partout, sans exception**.

| Niveau | Token | Couleur | Condition | Libellé UI | Icône |
|---|---|---|---|---|---|
| **Dépassée** | `--urg-retard` | `#B3261E` | date < aujourd'hui, non faite | « En retard » | ⛔ |
| **Aujourd'hui** | `--urg-jour` | `#C2410C` | date = aujourd'hui | « Aujourd'hui » | ● |
| **Imminente** | `--urg-imminent` | `#B4690E` | ≤ 7 jours | « Dans N jours » | ▲ |
| **Proche** | `--urg-proche` | `#8A6D1F` | ≤ 30 jours | « Dans N jours » | ◆ |
| **À venir** | `--urg-avenir` | `#4B5A72` | > 30 jours | date | — |
| **Faite** | `--urg-faite` | `#17795E` | statut `FAITE` | « Faite le … » | ✓ |
| **Sans objet** | `--urg-neutre` | `#8E96A3` | `SANS_OBJET` | « Sans objet » | – |

Chaque niveau dispose d'un trio `--urg-X` (texte/bordure), `--urg-X-bg` (fond pâle),
`--urg-X-strong` (pastille pleine).

> **Règle d'accessibilité non négociable** : couleur **+** icône **+** libellé. Une échéance en retard
> reste identifiable en noir et blanc, à l'impression, et par une personne daltonienne.

### 2.4 Criticité — un axe distinct de l'urgence

L'urgence dit *quand*, la criticité dit *combien ça coûte si on rate*. Elle n'est **pas** rendue par la
couleur (déjà prise) mais par un **filet vertical à gauche de la ligne** et un pictogramme :

| Criticité | Rendu | Sens |
|---|---|---|
| `BLOQUANTE` | Filet plein 3 px `--urg-retard` + `⚑` | Sanction, forclusion, amende |
| `IMPORTANTE` | Filet 2 px `--brass` | Manquement rattrapable |
| `CONFORT` | Filet 1 px `--paper-line` | Organisation interne |

### 2.5 Statuts métier → badge

| Statut | Couleur de badge |
|---|---|
| Dossier `OUVERT` / `EN_COURS` | Bleu `--primary` |
| Dossier `EN_ATTENTE_TIERS` | Ambre `--urg-proche` |
| Dossier `SUSPENDU` | Neutre `--ink-3` |
| Dossier `CLOS` | Vert `--urg-faite` |
| Dossier `ARCHIVE` | Gris `--urg-neutre` |
| Échéance | Échelle d'urgence (§ 2.3) |
| Nature `JUDICIAIRE` | Badge laiton `--brass` + mention « délai de procédure » |

---

## 3. Typographie

| Token | Police _(hypothèse)_ | Usage |
|---|---|---|
| `--font-display` | **Newsreader** (ou Source Serif 4) | Titres de page, en-têtes de fiche — la serif dit « document juridique » |
| `--font-ui` | **Inter** | Corps, listes, formulaires, boutons |
| `--font-mono` | **JetBrains Mono** | **Dates, références de dossier, montants, articles de code** — toujours `tnum` |

- Corps 14 px, `line-height 1.55`. Listes denses : 13,5 px.
- Titres serif, `letter-spacing -0.01em`, graisse 600 max — jamais de titre criard.
- Échelle : `--text-xs .75rem` → `--text-2xl 1.75rem`. Pas de « display » géant : ce n'est pas une app de sport.
- **`.ref`** : référence de dossier en mono (`2026-SOC-014`), sélectionnable d'un double-clic.
- **`.date`** : mono tabulaire, `JJ/MM/AAAA` ; **`.date-jour`** ajoute le jour de la semaine en `--ink-3` (« mar. 30/06/2026 »).
- **`.compte-a-rebours`** : « dans 12 j » / « il y a 3 j », coloré par l'échelle d'urgence.
- **`.base-legale`** : petite mono `--ink-3`, en italique — « art. L.223-26 C. com. ».
- Mobile : inputs forcés à 16 px (anti-zoom iOS).

---

## 4. Espacements, rayons, ombres

- **Base 4 px** : `--sp-1 4px` → `--sp-16 64px`. Listes : `--sp-2`/`--sp-3` verticalement.
- **Rayons sobres** : `--radius-sm 6px` · `--radius 10px` · `--radius-lg 14px` · `--radius-full` (badges).
  *Volontairement plus anguleux que DARI Lab : le papier n'a pas de coins ronds.*
- **Ombres discrètes** : `--shadow-xs` (filet 1 px + ombre 2 px) → `--shadow-lg` (modales). Les cartes se
  distinguent d'abord par **une bordure `--paper-line`**, pas par une ombre.
- **Conteneurs** : `.container-narrow ≤ 760px` (formulaires) · `.container-standard ≤ 1040px` ·
  `.container-wide ≤ 1400px` (listes, vue mois).

---

## 5. Composants

### 5.1 Génériques (repris de l'ADN)
Boutons (`.btn`, `.btn-primary`, `.btn-ghost`, `.btn-danger`, `.btn-icon`, hauteur 40 px — plus compacts
que DARI Lab), cartes, formulaires (`.form-group`, `.form-control`, `.field-hint`, `.error-message`),
badges, tableaux, pagination, onglets, barre de recherche, alertes, toasts, dialogues de confirmation,
skeletons, `empty-state`.

### 5.2 Spécifiques à Dies (à créer)

| Composant | Rôle |
|---|---|
| `app-echeance-row` | **Le composant central.** Une ligne : filet de criticité · pastille d'urgence · intitulé · société · date + compte à rebours · base légale · actions rapides (fait / reporter) |
| `app-echeance-card` | Version carte pour le tableau de bord et le mobile |
| `app-calendrier-mois` | Grille mensuelle 7 colonnes, pastilles d'urgence par jour, sélection d'un jour ouvrant le panneau latéral, imprimable |
| `app-liste-du-mois` | Liste groupée par jour avec en-têtes de date collants — **la vue explicitement demandée** |
| `app-trace-calcul` | Encart dépliable expliquant le calcul : fait générateur → formule → report → base légale → date de vérification |
| `app-date-input` | Saisie tolérante (`31/12/25`, `31.12.2025`), calendrier optionnel, jours fériés grisés, jour de la semaine affiché |
| `app-selecteur-regle` | Choix d'une règle du référentiel avec **aperçu de la date calculée en direct** |
| `app-frise-annee-sociale` | Frise horizontale de l'exercice : clôture → arrêté → convocation → AG → dépôt → dividende, avec état de chaque jalon |
| `app-timeline-dossier` | Chronologie verticale mêlant échéances et entrées de journal |
| `app-application-modele` | Assistant : fait générateur → **tableau des échéances proposées, modifiables** → confirmation |
| `app-statut-selector` | Changement de statut avec motif obligatoire quand la transition l'exige |
| `app-badge-criticite` / `app-badge-urgence` | Badges canoniques (§ 2.3, 2.4) |
| `app-entite-header` | En-tête de société : dénomination, forme, SIREN, greffe, **date de clôture mise en avant** |
| `app-recherche-globale` | Palette `Ctrl/Cmd+K` |
| `app-avertissement-procedure` | Bandeau sur les échéances `JUDICIAIRE` (texte au § 3.6 du référentiel) |

---

## 6. Conventions UX

- **Le retard est toujours en tête**, sur tous les écrans, dans un bloc rouge qui ne se replie pas tant qu'il reste une échéance dépassée.
- **Deux clics maximum** pour marquer une échéance faite depuis n'importe quelle vue.
- **Motif obligatoire** pour reporter ou passer en `SANS_OBJET` — c'est ce qui rend l'historique exploitable.
- **Toujours expliquer une date calculée** (`app-trace-calcul` accessible d'un clic sur la date).
- **Pré-remplissage contextuel** : « Nouvelle échéance » depuis une fiche société arrive avec la société et le dernier exercice déjà renseignés (`?entiteId=…&exercice=…`).
- **Confirmations** : `ConfirmDialogService`, jamais `confirm()`. Suppression d'un dossier = saisie de sa référence pour confirmer.
- **Toast systématique** sur chaque action, avec **annulation possible pendant 5 s** sur les actions réversibles (marquer faite, reporter).
- **Raccourcis clavier** _(S)_ : `n` nouvelle échéance, `d` nouveau dossier, `/` recherche, `Ctrl+K` palette.
- **Libellés français**, vocabulaire juridique exact : « échéance », « fait générateur », « dépôt au greffe », « signification » — pas « tâche », pas « deadline », pas « rappel » quand il s'agit d'une échéance.

---

## 7. Animations

Tokens : `--ease cubic-bezier(.32,.72,0,1)`, `--duration-fast 100ms`, `--duration 160ms`.
Keyframes autorisées : `fadeIn`, `slideInRight` (panneau latéral), `toastIn`, `shimmer` (skeletons),
`shake` (erreur de connexion). **Aucune animation décorative, aucune célébration, rien au-delà de 200 ms.**
`prefers-reduced-motion` respecté.

---

## 8. États d'interface

| État | Traitement |
|---|---|
| Chargement | Skeletons aux dimensions réelles (jamais de spinner plein écran) |
| Vide — première utilisation | `empty-state` avec **action d'amorçage** : « Importer votre tableur » / « Ajouter votre première société » |
| Vide — filtre trop restrictif | « Aucune échéance ne correspond » + bouton « Réinitialiser les filtres » |
| **Vide — aucune échéance à venir** | Message **rassurant mais vérifiable** : « Rien à venir dans les 30 jours. Dernière vérification : aujourd'hui 08:00 » — l'utilisatrice doit pouvoir distinguer « rien à faire » de « l'outil ne fonctionne plus » |
| Erreur | Toast global + `.alert-danger` inline |
| Erreur de champ | `.ng-invalid.ng-touched` + message sous le champ |
| Hors ligne | Bandeau « Hors ligne — dernières données du JJ/MM à HH:MM » |
| Session expirée | Redirection vers la connexion avec message explicite, et retour à la page d'origine après authentification |

---

## 9. Structure des pages

### Squelette type
```
.sidebar (Tableau de bord · Échéances · Mois · Dossiers · Sociétés · Contacts · Référentiel · Paramètres)
.page-header  (h1 serif + fil d'Ariane | .actions)
.filters-bar  (période · société · type · criticité · statut · recherche)
[contenu]
.pagination
```

### Écrans clés

- **Tableau de bord** — dans cet ordre, sans exception : ① **En retard** ② **Aujourd'hui** ③ Cette semaine ④ 30 prochains jours ⑤ Alertes (dossiers sans échéance, règles non vérifiées depuis 18 mois).
- **Vue mois** — calendrier à gauche (2/3), liste du mois groupée par jour à droite (1/3) sur desktop ; empilé sur mobile ; bouton « Imprimer le plan du mois ».
- **Fiche dossier** — en-tête (référence mono, intitulé, société, statut, criticité) + onglets *Échéances · Documents · Journal · Contacts*, colonne de droite : prochaine échéance, contacts clés, chiffres du dossier.
- **Fiche société** — identité + **frise de l'année sociale** en haut (c'est ce qu'elle vient voir) + mandats + dossiers rattachés.
- **Référentiel** — deux onglets : règles de délai (avec `verifieLe` et simulateur de calcul), modèles de procédure.

### Hiérarchie visuelle
Titre serif → **date et compte à rebours** (l'élément le plus saillant après le titre) → intitulé →
société → base légale en petit. Sur une ligne d'échéance, **l'œil doit tomber sur la date en premier**.

---

## 10. Responsive, accessibilité, impression

- **Breakpoints** : `≤ 600px` (mobile : cartes empilées, filtres dans un tiroir, bottom-nav 4 entrées), `≤ 1024px` (tablette : calendrier au-dessus de la liste), `≥ 1280px` (desktop : deux colonnes, tableaux complets).
- **Mobile** = consultation + marquage « fait ». La saisie d'une société ou d'un modèle reste desktop, sans être bloquée.
- **A11y** : contraste AA minimum (les couleurs du § 2.3 sont choisies pour passer sur `--paper` **et** sur leur fond pâle), `:focus-visible` net, cibles ≥ 44 px, navigation clavier complète sur les listes, `aria-live` sur les toasts, tableaux avec en-têtes associés, pinch-zoom conservé.
- **Impression** (`@media print`) : fond blanc, encre noire, urgence rendue par **icône + libellé + trame**, filets de criticité conservés en niveaux de gris, en-tête « Plan du mois — octobre 2026 » avec date d'édition, pas de barre latérale, sauts de page entre semaines.

---

## 11. Checklist « composant conforme »

- [ ] Tokens CSS uniquement — aucune valeur en dur.
- [ ] Dates en mono tabulaire, `JJ/MM/AAAA`, compte à rebours associé.
- [ ] Urgence rendue par couleur **+ icône + libellé** ; criticité par le filet gauche.
- [ ] États couverts : chargement (skeleton), vide, erreur, hors ligne.
- [ ] Responsive ≤ 600 px, cibles ≥ 44 px, inputs 16 px.
- [ ] Toast sur action, confirmation via `ConfirmDialogService`, motif obligatoire si la transition l'exige.
- [ ] Impression vérifiée si le composant apparaît dans la vue mois ou la fiche dossier.
- [ ] Vocabulaire juridique exact, libellés français.
- [ ] Animations ≤ 200 ms, `prefers-reduced-motion` respecté.

---

*Écritoire Design System v1.0 — rebrandable en changeant `--primary` et `--brass` ;
l'échelle d'urgence (§ 2.3), elle, est un standard métier et ne se modifie pas.*
