# Analyse concurrentielle — DARI Lab Training vs Nolio (août 2026)

> **Question posée** : Nolio est-il un concurrent direct de DARI Lab Training, et où se situe
> l'application par rapport à lui sur les fonctionnalités, l'UX, l'UI et l'ergonomie ?

---

## Note de méthode — à lire avant le reste

**Côté DARI Lab** : analyse de première main du dépôt (231 fichiers TypeScript / ~39 000 lignes
front, 479 fichiers Java / ~30 000 lignes back, 47 contrôleurs REST, 13 moteurs de calcul,
50 tables), croisée avec les audits internes de juillet et août 2026.

**Côté Nolio** : **aucun accès direct**. Le domaine `nolio.io` (et plusieurs sites de test) sont
bloqués par la politique réseau de cet environnement — HTTP 403 au niveau du proxy. La description
de Nolio repose donc sur les **résumés de recherche web** (pages produit, centre d'aide
`help.nolio.io`, blog de mise à jour, presse spécialisée, avis App Store) et **non sur une prise en
main**. Conséquence directe :

- les **fonctionnalités** de Nolio sont fiables (documentées publiquement) ;
- l'**UX/UI/ergonomie** de Nolio sont **inférées** de son centre d'aide, de ses notes de version et
  d'avis utilisateurs, pas mesurées. Les jugements de cette partie sont explicitement marqués
  _(inféré)_ et doivent être revalidés par un essai réel (compte coach gratuit, 1 mois d'essai).

C'est la seule zone d'incertitude de ce document ; tout le reste est vérifiable dans le code.

---

## 1. Les deux produits en une page

|  | **Nolio** | **DARI Lab Training** |
|---|---|---|
| Origine | Grenoble, 2017 — F. Dupont & A. Bouquet | Projet privé, base CoachRun réorientée DARI Lab |
| Traction | > 20 000 utilisateurs, > 3 000 coachs (FR, QC, BE, CH) | Pré-bêta ouverte, jeu de démo déterministe |
| Périmètre sportif | **Multi-sport** : course, cyclisme, triathlon, natation, ski… | **Course à pied (route / trail) + préparation physique** |
| Positionnement | Plateforme d'entraînement généraliste, pilotée par la donnée | Coaching **physiologique** (LT1/LT2, VC, VDOT, 1RM) |
| Plateformes | Web + **apps natives iOS / Android** | Web + **PWA** (portail athlète installable) |
| Modèle | Athlète gratuit / Premium ~7 €/mois · Coach 19,90–39,90 €/mois · Club 29,90–49,90 € · Marketplace + facturation Stripe | **Aucun** (pas de facturation, pas d'abonnement) |
| Intégrations | ~18 marques et apps (Garmin, Coros, Polar, Suunto, Wahoo, Whoop, Oura, Apple Santé, Strava…), **export séances vers montre** | **Strava seul** (OAuth, import polling) + GPX/TCX + saisie manuelle |
| Trou majeur | **Pas de module musculation / prépa physique** (annoncé en roadmap moyen terme) | Pas d'export montre, pas de multi-sport, pas de monétisation |

---

## 2. Fonctionnalités

### 2.1 Points communs

Le socle métier est **le même**, et c'est ce qui fait de Nolio un concurrent :

- Relation **coach ↔ athlète** avec espaces distincts et invitations.
- **Calendrier de planification** avec code couleur par type de séance, glisser-déposer,
  planification à plusieurs semaines.
- **Éditeur de séances structurées** (échauffement / corps / retour au calme, intervalles) et
  **bibliothèque de séances** réutilisables.
- **Zones d'entraînement paramétrables** par athlète, cibles recalculées automatiquement.
- **Lecture prévu vs réalisé** : Nolio via deux calendriers distincts, DARI Lab via un agenda
  athlète à trois filtres (prévu / réalisé / les deux) et un **appariement automatique**
  activité ↔ séance (`MatchingService`, avec appariement manuel de rattrapage).
- **Charge d'entraînement** et suivi de forme/fatigue/récupération.
- **Messagerie intégrée** coach ↔ athlète.
- **Objectifs / compétitions** placés dans le calendrier.
- **Import d'activités** depuis une montre, **groupes d'athlètes**, **multi-coachs**.
- **Retours de l'athlète** (ressenti) et commentaire du coach sur la séance.

### 2.2 Avantages de Nolio

| Domaine | Ce que Nolio a et que vous n'avez pas |
|---|---|
| **Export vers la montre** | Les séances structurées partent **automatiquement** sur Garmin (et autres) dès l'association du compte. L'athlète court sa séance guidée au poignet. Limite connue : ~40 intervalles max côté Garmin. **C'est le manque n° 1 de votre application.** |
| **Écosystème de sync** | Garmin, Suunto, Polar, Wahoo, Coros, Fitbit, Withings, Decathlon, Oura, Whoop, Apple Watch, Concept2 + Strava, Kinomap, HealthFit, Adidas Running, Klimat, PlanMyRun, Apple Santé. Vous : **Strava uniquement**. |
| **Formats de fichier** | `.fit`, `.tcx`, `.srm`, `.gpx`. Vous : `.gpx` / `.tcx` — **pas de `.fit`**, qui est pourtant le format natif de Garmin/Coros et le seul qui porte toutes les métriques. |
| **Multi-sport** | Cyclisme (puissance, TSS/Coggan), natation, triathlon, ski. Vous : course + force. Un coach de triathlon ne peut pas vous utiliser. |
| **Modèles de charge multiples** | Foster, Coggan (TSS), TRIMP activables et configurables — le vocabulaire du marché. Vous : sRPE → ATL/CTL/ACWR/monotonie, un seul modèle (excellent, mais non reconnaissable par un coach venant de TrainingPeaks). |
| **Métriques quotidiennes & santé** | Sommeil, poids, FC de repos, **HRV mesurée dans l'app mobile** (ceinture ou caméra du smartphone), métriques rangées par famille (Cardiaque, Santé, Puissance), **tableaux de bord personnalisables**. Vous : check-in matinal à 3 curseurs (sommeil / fatigue / douleur), pas de poids, pas de FC repos, pas de HRV, dashboards figés. |
| **Questionnaires configurables** | Refondus au printemps 2026 pour un suivi du ressenti plus fin. Vous : formulaire de retour fixe (RPE / fatigue / douleur / commentaire). |
| **Recherche d'intervalles & records** | Générateur d'intervalles + recherche des **meilleurs efforts** sur durée/distance (FC, puissance, allure). Vous : pas de « best efforts », pas de records automatiques. |
| **Matériel / équipement** | Suivi du matériel (paires de chaussures, vélos), archivage sans perte d'historique, matériel requis par séance. Vous : `EquipmentType` existe mais uniquement pour la prescription d'exercices de force — **aucun suivi d'usure**. |
| **Monétisation et business du coach** | Page entraîneur publique, **marketplace** de plans, vente de coaching, **facturation Stripe** consolidée (abonnements + revenus marketplace). Vous : **rien**. C'est ce qui fait qu'un coach professionnel *reste* sur Nolio. |
| **Plans d'entraînement vendables** | Plans publics ou privés par lien, avec sport, catégorie et niveau. Vous : `TrainingPlanController` existe au back (plans périodisés, application à un groupe) mais **aucune interface front ne l'appelle** — capacité morte. |
| **Apps natives** | iOS et Android sur les stores. Vous : PWA. |
| **Maturité d'exploitation** | 9 ans de production, base installée, support, centre d'aide public fourni. |

### 2.3 Avantages de DARI Lab Training

| Domaine | Ce que vous avez et que Nolio n'a pas |
|---|---|
| **Préparation physique complète** | Bibliothèque d'exercices (catégories, groupes musculaires, matériel, vidéo, progression/régression), éditeur de structure avec **formats avancés** (EMOM, AMRAP, For Time, Circuit, Isométrie, Pliométrie) et **types de série** (drop-set, super-set, myo-reps, cluster, iso), charge **et** effort prescrits indépendamment, tempo, repos. **Nolio annonce ce module en roadmap moyen terme — il n'existe pas.** C'est votre avantage le plus net. |
| **Moteur 1RM et tests de force** | e1RM par **Nuzzo** (défaut), Epley, Brzycki, RIR-based ; 4 protocoles de test (1RM direct, rep-test 3–5, AMRAP, isométrie MVC) ; `tested` prévaut sur l'estimé ; zones de travail Lacourpaille ; charges cibles arrondies au palier 2,5 kg. |
| **Cycles de force + progression automatique** | Progression multi-semaines assignable au calendrier, suggestion de charge à la séance suivante et **alertes coach** (douleur, RPE, RIR, charge). |
| **Charge unifiée course + force** | L'ACWR agrège les deux disciplines (`AthleteLoadService`), plus charge **mécanique** et **métabolique** en UA pour la force. Un coach hybride n'a pas deux vérités séparées. |
| **Profondeur physiologique** | Test lactate par paliers → LT1 (baseline + 0,5) / LT2 (Dmax modifié), **vitesse critique** (avec FC moyenne des efforts), VDOT/Daniels + Riegel `^1.06`, domaines d'intensité 1/2/3 avec priorité physio et repli FC. Nolio reste sur des zones et des modèles de charge classiques. |
| **Prescription en fourchettes** | Chaque bloc se prescrit **par zone du club** *ou* en **fourchette de % sur mesure** (« 6 × 1000 à 102–106 % de VC »), avec calculateur live par athlète et recalcul automatique quand une valeur de référence change. Plus fin qu'une cible unique. |
| **Modèles de zones multiples** | Plusieurs jeux de zones (route / trail, débutant / confirmé) entretenus en parallèle et appliqués athlète par athlète. |
| **Séparation forme / RPE** | `FormStatusEngine` n'utilise **jamais** le RPE : la forme vient de fatigue + douleur. Principe métier tenu partout, y compris dans le check-in matinal. |
| **Pilotage par exception** | Cockpit coach à zones (alertes actionnables en tête), **file « retours à traiter »**, pastilles de forme, portée mes athlètes / privés / club, digest matinal. Orienté « qu'est-ce que je fais ce matin », pas « voici toutes les données ». |
| **Temps réel** | Messagerie **SSE** avec pièces jointes (images / PDF), notifications push Web Push/VAPID. |
| **Sécurité et RGPD de niveau produit santé** | Chiffrement **AES-256-GCM** au repos des données de santé et des jetons OAuth, anti-IDOR systématique par `@clubAccessValidator`, garde-fou de démarrage sur les secrets, CSP/HSTS, rate-limiting, consentement santé, pages légales. Peu de concurrents de cette taille vont aussi loin. |
| **Aide intégrée par rôle** | Centre d'aide athlète / coach / admin, recherche globale, liens contextuels depuis les écrans, export PDF du guide. |
| **Éducatifs de course** | Gammes technique/amplitude attachables aux blocs — absent du discours Nolio. |
| **Espace admin plateforme** | Clubs, utilisateurs, athlètes, invitations, retours de bêta. Nolio n'expose évidemment pas d'équivalent à ses clients. |
| **Qualité de code vérifiable** | ~340 tests back (moteurs purs + MockMvc), 63–64 tests front, smoke PostgreSQL réel en CI, 70 migrations Liquibase, schéma cohérent avec les entités (audit d'août : 50 tables, aucun écart). |

### 2.4 Priorités fonctionnelles pour rivaliser

1. **Export des séances structurées vers la montre** (Garmin en premier, puis COROS). Sans cela,
   l'athlète recopie sa séance à la main : c'est le motif de rejet le plus probable en bêta.
   Le dossier `docs/DEMANDES-API-GARMIN-COROS.md` existe déjà — c'est la marche à monter.
2. **Import `.fit`** et **connecteur Garmin en entrée** (Strava seul est un point de fragilité :
   dépendance à un tiers, polling, pas de puissance ni de dynamique de course).
3. **Corriger les quatre bloquants métier de l'audit fonctionnel** (voir §6) : ils coûtent plus
   cher que n'importe quelle fonctionnalité manquante, parce qu'ils détruisent la confiance dans
   les chiffres — le seul terrain où vous battez Nolio.
4. **Métriques quotidiennes** : poids, FC de repos, sommeil, et à terme HRV. Le check-in matinal
   est le bon socle, il lui manque les mesures objectives.
5. **Modèles de charge additionnels** (TRIMP, Foster, TSS) en plus du sRPE : parler la langue du
   marché sans renoncer à la vôtre.
6. **Exposer les plans périodisés dans l'UI** — le back existe, c'est le meilleur rapport
   valeur/effort du document.
7. **Records et meilleurs efforts** par distance/durée : très attendu côté athlète, peu coûteux
   à partir des activités déjà stockées.
8. **Monétisation** (abonnement coach, Stripe) dès que la bêta se stabilise : sans elle,
   l'application n'est pas une alternative *commerciale* à Nolio, seulement un outil.

---

## 3. UX — simplicité, fluidité, nombre de clics, parcours, mobile / desktop

### 3.1 Points communs

- **Deux espaces séparés** coach (desktop, dense) et athlète (mobile, focalisé).
- Le **calendrier est la porte d'entrée** des deux côtés, avec code couleur par type de séance.
- **Glisser-déposer** pour replanifier.
- Le **ressenti de l'athlète** remonte au coach sans sortir de l'outil.
- **Communication centralisée** plutôt que dispersée dans les e-mails et SMS.

### 3.2 Avantages de Nolio

- **Applications natives** iOS/Android : notifications système, arrière-plan, intégration Apple
  Santé / Apple Watch, mesure HRV par la caméra. Une PWA ne peut structurellement pas égaler ça.
- **Zéro friction de synchronisation** : la séance arrive sur la montre, l'activité revient toute
  seule. Le parcours athlète le plus court possible — aucun clic.
- **Personnalisation des tableaux de bord** : chaque coach range ses métriques comme il veut, ce
  qui réduit le nombre de clics *pour lui* spécifiquement.
- **Parcours d'entrée sans coach** : un athlète seul s'inscrit, connecte sa montre et utilise
  l'analyse gratuitement. Votre application, elle, **n'existe pas sans coach** — pas d'auto-service.
- **Maturité des parcours** _(inféré)_ : 9 ans d'itérations sur les mêmes écrans, et une refonte
  du calendrier au printemps 2026 annoncée « 3× plus rapide ».
- Onboarding documenté par un **centre d'aide public** riche, indexé par Google : l'utilisateur
  trouve sa réponse avant même d'ouvrir l'app.

### 3.3 Avantages de DARI Lab Training

- **Le calendrier coach est d'un niveau « application de bureau »**, ce qui est rare dans cette
  catégorie : multi-sélection (clic, `mod`+clic, `Maj`+clic, **rectangle de sélection**),
  copier/coller (`mod`+C / `mod`+V sur le jour survolé), duplication sur place (`mod`+D),
  suppression, **Alt+glisser pour copier au lieu de déplacer** (avec retour visuel du curseur),
  **annuler / rétablir** (`mod`+Z / `mod`+Maj+Z), navigation clavier (←/→/T/W/M/B) et aide-mémoire
  `?`. Une semaine se duplique en un geste, un mésocycle s'applique d'un coup.
  → Sur la tâche la plus répétitive du métier — poser des séances — vous êtes probablement
  **au-dessus** de Nolio en nombre de clics.
- **Bibliothèque latérale repliable** dans le calendrier : glisser un modèle depuis la biblio sur
  un jour, sans changer d'écran. Et l'inverse : une séance construite dans le calendrier
  **s'enregistre comme modèle** en un geste.
- **Recherche globale `Ctrl+K`** (command palette) dans tout l'espace coach.
- **Check-in matinal en trois curseurs, dix secondes**, replié une fois rempli, jamais bloquant :
  la forme existe *avant* la séance, pas seulement après. Design de rappel intelligent
  (`debrief-prompt` monté dans la coquille, donc impossible à contourner en changeant d'onglet).
- **Mode séance de force plein écran**, volontairement hors coquille (pas de bottom-nav, un
  exercice à la fois) : le bon parcours pour quelqu'un qui a un téléphone dans une main et une
  barre dans l'autre.
- **Premier lancement guidé** : le cockpit coach vide affiche trois étapes actionnables plutôt que
  quatre cartes vides.
- **Sécurité des gestes** : garde `unsavedChangesGuard` sur les éditeurs, autosave avec badge,
  confirmations, toasts, pile d'annulation, actions d'écriture désactivées sur un athlète en
  lecture seule.
- **Hors-ligne** : PWA, bannière offline, file de retours stockée localement.
- **Portail athlète cohérent avec l'usage réel** : ouverture sur le **mois** (ce qui m'attend),
  « Aujourd'hui » à un onglet, 4 entrées de barre basse plutôt que 6 (décision documentée : sur
  375 px, six cibles tombaient à ~52 px).

### 3.4 Priorités UX

1. **Supprimer la recopie manuelle de la séance** (export montre). C'est *le* point de friction.
2. **Prévoir un parcours athlète autonome** (sans coach) même minimal, ou assumer explicitement le
   B2B2C : aujourd'hui l'acquisition dépend entièrement du coach.
3. **Notifications natives** : à défaut d'apps natives, pousser la PWA au maximum (Web Push est
   déjà là) et documenter l'installation sur iOS, où elle est peu évidente.
4. **Tablette / iPad** : Nolio est critiqué là-dessus, c'est une brèche ouverte. Un calendrier
   coach tactile bien fait sur iPad serait un argument.
5. **Personnalisation minimale du cockpit** (choisir les colonnes/métriques affichées) : peu cher,
   très visible.
6. **Rendre le centre d'aide public et indexable** : aujourd'hui il est intégré à l'app, donc
   invisible pour un prospect.

---

## 4. UI — design, modernité, lisibilité, cohérence

### 4.1 Points communs

- Interfaces **modernes, sobres, orientées données** ; code couleur par type de séance ;
  graphiques de charge et de progression.
- Deux ambiances distinctes selon le rôle (dense côté coach, focalisée côté athlète).

### 4.2 Avantages de Nolio

- **Cohérence multi-plateforme réelle** : web + natif iOS + natif Android, avec les conventions
  de chaque OS. Vous n'avez qu'un rendu web.
- **Performance perçue** : refonte du calendrier annoncée « 3× plus rapide » au printemps 2026,
  en-tête allégé pour afficher plus d'information utile.
- **Densité maîtrisée sur beaucoup plus de métriques** _(inféré)_ : Nolio affiche davantage de
  familles de données (puissance, natation, HRV, sommeil) sans perdre en lisibilité — c'est le
  fruit de plusieurs refontes.
- **Réserve importante** : plusieurs avis App Store signalent une **application mobile instable**
  (plantages, synchronisation Apple Watch capricieuse) et **non optimisée pour iPad**. La qualité
  d'UI de Nolio n'est donc pas uniforme — c'est votre angle d'attaque.

### 4.3 Avantages de DARI Lab Training

- **Design system tokenisé et documenté** (`docs/Design.md`, « Pace Design System ») : surfaces,
  encre à 4 niveaux, marque/énergie, **couleurs de zones canoniques Z1→Z5**, statuts avec mapping
  métier explicite (`COMPLETED` → vert, `MISSED` → rouge, `RACE` → accent spécial), échelle
  typographique, espacements base 4, rayons, ombres. **Aucune valeur en dur** : un club se
  rebrande en changeant des variables CSS.
- **Typographie pensée pour la donnée** : police display pour les gros chiffres, mono
  `tabular-nums` obligatoire pour allures / FC / distances (`4:35 /km`, `156 bpm`) — les colonnes
  ne dansent pas. Inputs forcés à 16 px sur mobile (anti-zoom iOS).
- **Bibliothèque de composants métier déjà riche** (~45 composants partagés) : `acwr-indicator`,
  `load-chart`, `zone-bar`, `time-in-zone-bar`, `intensity-zone-badge`, `range-prescription-pill`,
  `rpe-scale-selector`, `pain-fatigue-selector`, `readiness-gauge`, `activity-laps`,
  `session-detail-modal`, `bottom-sheet`, `sticky-action-bar`, `skeleton`, `empty-state`,
  `celebration`… Ce sont exactement les briques qui font qu'une app de coaching a l'air finie.
- **Peau « night track » du portail athlète** : thème sombre scopé au sous-arbre athlète,
  indépendant du thème du coach, barre basse en verre dépoli avec `backdrop-filter`, respect des
  `safe-area` iOS. Rendu proche d'une app native.
- **Living styleguide** (`/dev/ui-kit`) : la cohérence est vérifiable, pas seulement espérée.
- **États non-heureux traités** : squelettes, états vides, bannière hors-ligne, bannière de mise à
  jour, toasts. C'est souvent ce qui distingue une app finie d'une démo.

### 4.4 Priorités UI

1. **Faire vérifier l'UI sur vrais appareils** (iPhone SE/375 px, Android milieu de gamme, iPad) :
   votre design system est bon, mais il n'a pas encore été confronté au parc réel.
2. **Budget de bundle** : le build front sort avec des avertissements (`leaflet`, `localforage`
   non ESM). Sur mobile 4G, le premier chargement est un élément d'UI.
3. **Accessibilité** : contrastes des couleurs de zones sur fond sombre, tailles de cible,
   `aria-*` — déjà entamé, à finir et à tester (l'audit de bêta ouverte de juillet en parle).
4. **Captures et page vitrine** : à qualité d'UI comparable, Nolio gagne parce qu'on voit son
   produit avant de s'inscrire.

---

## 5. Ergonomie — navigation, menus, accessibilité des fonctions, efficacité

### 5.1 Points communs

- Navigation par **rôle** ; calendrier central ; réglages de zones et de profil isolés du flux
  quotidien ; messagerie accessible partout.

### 5.2 Avantages de Nolio

- **Tout est atteignable sans coach ni configuration préalable** : on se connecte, la montre pousse
  les données, l'analyse existe. Chez vous, un athlète sans profil physiologique renseigné obtient
  des prescriptions dégradées (cf. §6, bloquant B1).
- **Structure de gestion mature** : groupes **et sous-groupes**, plusieurs coachs de staff,
  facturation, page publique — un club de 80 athlètes trouve chaque écran à sa place.
- **Centre d'aide externe** organisé par collections (Utiliser Nolio, Sync et capteurs…), donc
  atteignable depuis un moteur de recherche.
- **Cohérence des parcours entre web et mobile** _(inféré)_.

### 5.3 Avantages de DARI Lab Training

- **Navigation coach groupée par intention** : Coaching (Tableau de bord, Retours, Athlètes,
  Groupes, Calendrier, Messages, Bibliothèque) / Club (Club, Mes zones) / Réglages. Sidebar
  repliable, **badges de compteur** sur Retours et Messages — l'attention est dirigée.
- **Coquille d'athlète persistante** : le bandeau d'identité et les onglets (Résumé, Programme,
  Charge, Zones, Tests, Courses, Activités, Messages) ne disparaissent plus au changement de
  section, et un **fil d'Ariane** dit où l'on est. Le coach ne perd jamais son contexte.
- **Fusion des bibliothèques** en un écran à onglets (course / prépa physique / éducatifs), avec
  **redirections conservées** sur les anciennes URL — un favori ou une capture d'écran de doc ne
  casse pas.
- **Mobile coach pensé, pas subi** : bottom-nav à 4 entrées + panneau « Plus » qui reprend la nav
  complète groupée. Beaucoup d'outils de coaching abandonnent le coach sur mobile.
- **Raccourcis clavier documentés à un seul endroit** (`calendar-shortcuts.ts`), source unique pour
  l'aide `?` **et** pour les libellés des menus contextuels : ils ne peuvent pas diverger.
- **Aide contextuelle** : `<app-help-hint section="…">` ouvre la bonne rubrique depuis l'écran où
  l'on bloque, plus une recherche d'aide globale.
- **Canal de support tracé** : « Signaler un problème » écrit en base avec son contexte technique,
  au lieu d'un `mailto:` qui suppose un client mail configuré.
- **Ergonomie défensive** : permissions `write` respectées jusque dans la désactivation des
  boutons, athlète qui **déplace** mais ne modifie ni ne supprime une séance prescrite, mesures de
  montre en lecture seule tandis que RPE et commentaire restent éditables sur la sortie.

### 5.4 Priorités ergonomiques

1. **Sous-groupes** et rôles de staff plus fins pour viser les clubs de plus de 50 athlètes.
2. **Vue « semaine du club »** (tous les athlètes d'un groupe sur une même grille) si elle n'existe
   pas encore : c'est le geste quotidien d'un coach de club.
3. **Recherche globale côté athlète** (elle n'existe que côté coach).
4. **Parcours de reprise après blessure** de bout en bout (cf. bloquant B3) : c'est un moment
   ergonomique, pas seulement fonctionnel.
5. **Réduire la dépendance au profil physiologique complet** : dégrader proprement plutôt que
   d'afficher des chiffres faux (cf. B1).

---

## 6. L'état réel du passif interne

> **Correction (première version de ce document)** : cette section affirmait que les bloquants
> métier et RGPD des audits n'étaient pas corrigés, en se fiant à l'index `docs/README.md`
> (« aucun correctif appliqué »). **C'est faux.** Le plan de conformité et le code disent le
> contraire : les **huit items de code de la vague 0 et les huit de la vague 1 sont livrés et
> couverts par des tests** (295 tests avant, 316 après V0, 325 après V1). Vérifié dans le code :
> `SessionCalculatorEngine` dérive désormais le RPE du **référentiel prescrit**
> (`domainForPrescription`) et non d'une reclassification d'allure ; `HealthDataConsentValidator`
> est injecté dans `DailyCheckInService`, `StrengthResultService` et `StrengthScheduleService`.
> L'index `docs/README.md` est la source périmée, pas le plan.

### 6.1 Ce qui reste vraiment ouvert avant d'ouvrir

Aucun n'est un problème de code — c'est précisément pour ça qu'ils traînent.

| # | Reste à faire | Nature | Pourquoi c'est bloquant |
|---|---|---|---|
| **V0-09** | `legalName` et `address` sont **encore vides** (`legal.component.ts:34-35`) | Légal | Article 13 du RGPD : l'identité du responsable de traitement est obligatoire, et le service traite des données de santé. L'exemption LCEN ne couvre pas ça |
| **V0-10** | `ops/backup-db.sh` **n'est exécuté par rien** (aucun cron, aucun workflow) et la restauration n'a **jamais été jouée** | Exploitation | Seule étape irrattrapable du plan : une perte de données ne se rattrape pas après coup |
| **V0-11** | Compte administrateur de plateforme absent en production | Exploitation | `/admin` inatteignable : ni révocation d'invitation, ni suppression de compte — alors que la politique de confidentialité la promet |
| **V1-09** | DSN Sentry backend et tags de déploiement | Exploitation | Sans lui, une erreur en production est invisible |
| **L-04** | Relecture juridique des CGU et de la politique de confidentialité | Légal | Ne peut pas être remplacée par un audit de code |

Charge résiduelle : de l'ordre de **2 à 3 jours**, dont une bonne partie n'est pas du
développement (décision d'identité civile, test de restauration, relecture juridique).

### 6.2 Les limites structurelles qui mordront après l'ouverture

**SSE mono-instance** (Redis pub/sub requis pour scaler), **jeton en query param** pour SSE et
pièces jointes, **import Strava par polling** (webhook à faire), **pagination** non généralisée,
**pièces jointes en base** (`bytea`), **tests front et e2e** insuffisants (pas de
Playwright/Cypress), **assertions sur H2** plutôt que Testcontainers.

### 6.3 Deux contraintes externes qui pèsent plus que tout le reste

Elles ne sont pas dans le code, ne se corrigent pas par du développement, et conditionnent la
stratégie d'ouverture.

**A. Le programme développeur Garmin est fermé.** Votre propre dossier
(`docs/DEMANDES-API-GARMIN-COROS.md`, 4 août 2026) l'établit : Garmin a **suspendu la revue et
l'approbation des nouvelles demandes**, le formulaire public a été retiré, il n'existe **ni liste
d'attente ni date de réouverture**. Garmin exige en outre une **personne morale** — les demandes
personnelles sont refusées. Conséquence : *« je me lance quand j'aurai l'export montre »* revient à
fixer sa date d'ouverture sur une décision de Garmin qu'on ne contrôle pas. **COROS, lui, est
ouvert** et annonce un processus non discriminatoire — c'est la seule des deux demandes qui peut
aboutir aujourd'hui.

**B. Les conditions de l'API Strava restreignent l'affichage des données à un tiers.** L'accord
API mis à jour par Strava énonce que les données d'un utilisateur ne peuvent être affichées **qu'à
cet utilisateur**, et interdit de les divulguer à un autre utilisateur — les plateformes de
coaching qui montrent les données d'un athlète à son coach sont explicitement visées. Or c'est
exactement ce que fait `ActivityController` (`/clubs/{clubId}/athletes/{athleteId}/activities`,
lecture coach) sur des activités `ActivitySource.STRAVA`. Strava nuance de son côté que la
majorité des usages de coaching restent autorisés — **le texte est ambigu et son application
incertaine**. Ce document ne tranche pas : il signale que **l'unique chaîne d'ingestion automatique
de l'application repose sur un contrat qu'il faut relire avant d'ouvrir**, et qu'un repli
(import `.fit`, COROS) doit exister avant, pas après.

---

## 7. Conclusion

### 7.1 Verdict — niveau de concurrence

**Oui, Nolio est un concurrent direct — mais partiel, et sur un segment que vous n'attaquez pas
frontalement.**

- **Marché** : identique. Même pays, même langue, même acheteur (le coach, qui paye et amène ses
  athlètes), même promesse (planifier, suivre, communiquer).
- **Périmètre** : recouvrement d'environ **deux tiers**. Tout le cœur course à pied se chevauche.
  Mais Nolio est **multi-sport** là où vous êtes **mono-sport**, et vous êtes **le seul des deux à
  avoir un module de préparation physique** — Nolio l'annonce en roadmap moyen terme.
- **Positionnement** : différent, et c'est votre chance. Nolio est une plateforme **généraliste
  pilotée par la donnée de montre**. DARI Lab est un outil de **coaching physiologique spécialisé
  course + force**, où la prescription est calculée à partir de seuils mesurés. Un coach de trail
  qui fait de la prépa physique n'a pas d'équivalent chez Nolio.
- **Rapport de force réel aujourd'hui** : Nolio gagne sur l'**écosystème** (montres, export,
  natif) et le **business** (marketplace, facturation, base installée de 20 000 utilisateurs).
  Vous gagnez sur la **profondeur métier** (physiologie, force, charge unifiée), l'**ergonomie du
  calendrier coach** et la **rigueur technique** (sécurité, RGPD, tests).
- **Ce qui déciderait de l'affrontement** : le jour où Nolio livre son module musculation, votre
  différenciateur principal se réduit à la physiologie. Ce jour-là, il faudra avoir l'export montre
  et une base de coachs. Inversement, tant que ce module n'existe pas, vous avez une fenêtre.

**En clair : concurrent direct sur le running coaché francophone, concurrent indirect sur la prépa
physique — et pour l'instant, vous n'êtes pas encore sur le marché.**

### 7.2 Maturité estimée

L'estimation ci-dessous compare DARI Lab à Nolio **sur le périmètre commun**, pondérée par ce qui
compte pour un coach qui choisit un outil.

| Axe | Poids | Maturité vs Nolio | Commentaire |
|---|---|---|---|
| Fonctionnel — cœur planification/suivi | 25 % | **80 %** | Tout le socle est là, souvent mieux fait |
| Fonctionnel — physiologie & force | 15 % | **130 %** | Vous dépassez Nolio ; module force inexistant chez eux |
| Intégrations & écosystème montres | 20 % | **15 %** | Strava seul, pas d'export, pas de `.fit` |
| UX | 12 % | **85 %** | Excellent calendrier coach, PWA soignée ; friction de sync |
| UI & design system | 8 % | **95 %** | Design system tokenisé, composants métier riches |
| Ergonomie & navigation | 8 % | **90 %** | Contexte persistant, raccourcis, aide contextuelle |
| Business (monétisation, marketplace, facturation) | 7 % | **0 %** | Inexistant |
| Exploitation & confiance (production, support, base installée) | 5 % | **25 %** | Pré-bêta, audits en cours |

> ### **Maturité globale ≈ 60 % de Nolio sur le produit, ≈ 45 % en tant qu'entreprise.**
>
> - **~60 %** si l'on compare **les applications** (ce qu'un utilisateur voit et fait).
> - **~45 %** si l'on compare **les offres** (produit + écosystème + monétisation + traction).
> - **~30 %** si l'on compare **les positions de marché** (0 client payant vs 20 000 utilisateurs).
>
> Le chiffre à retenir : **60 %**, avec un profil très inhabituel — vous êtes **au-dessus** de
> Nolio sur la profondeur métier et la qualité d'interface, et **très en dessous** sur les tuyaux
> (montres) et le business. Ce n'est pas un produit immature ; c'est un produit **déséquilibré**,
> ce qui est bien plus facile à corriger.

### 7.3 Feuille de route priorisée

**Vague 0 — avant d'ouvrir à des inconnus** *(2–3 j, dont peu de développement)*
1. **V0-09** — renseigner l'identité civile et l'adresse de l'éditeur. Décision humaine, à lancer
   le premier jour.
2. **V0-10** — planifier les sauvegardes **et jouer une restauration** dans une base jetable.
   Seule étape irrattrapable.
3. **V0-11** — créer le compte administrateur de plateforme en production.
4. **V1-09** — brancher le DSN Sentry backend.
5. **L-04** — relecture juridique des CGU et de la politique de confidentialité.
6. **Relire l'accord API Strava** au regard de la lecture coach des activités importées (§6.3-B),
   et décider : restreindre l'affichage, ou accélérer un repli d'ingestion.

*(Les bloquants métier et RGPD de code — RPE prescrit, consentement, forme périmée, charge réelle,
indisponibilité, plafonds — sont **livrés**. Voir §6.)*

**Vague 1 — combler l'écart qui fait perdre des athlètes** *(0–3 mois)*
7. **Demande d'accès COROS** — la seule des deux qui peut aboutir aujourd'hui, à envoyer
   maintenant. En parallèle, ouvrir un ticket au support développeur Garmin pour l'antériorité.
8. **Import `.fit`** — indépendant de tout partenariat, couvre Garmin et COROS en entrée dès
   aujourd'hui, et réduit la dépendance à Strava.
9. **Export des séances vers la montre** dès qu'un des deux accès est obtenu (COROS d'abord,
   Garmin quand le programme rouvre). **Ne pas conditionner l'ouverture à cette étape.**
10. **Webhook Strava** à la place du polling.
11. **Exposer les plans périodisés** dans l'UI, ou retirer le module (décision NC-01, toujours
    ouverte : neuf endpoints sans écran).
12. **Métriques quotidiennes** : poids, FC de repos, sommeil.

**Vague 2 — rattraper le vocabulaire du marché** *(3–6 mois)*
12. **Modèles de charge** TRIMP / Foster / TSS en complément du sRPE.
13. **Records et meilleurs efforts** par distance/durée à partir des activités.
14. **Générateur / recherche d'intervalles**.
15. **Suivi du matériel** (usure des chaussures, archivage).
16. **Personnalisation du cockpit coach** (métriques et colonnes).
17. **Tablette / iPad** — la brèche laissée ouverte par Nolio.
18. **Tests e2e** (Playwright) et Testcontainers ; pagination généralisée ; SSE multi-instance.

**Vague 3 — devenir une offre, pas seulement un outil** *(6–12 mois)*
19. **Abonnements et facturation** (Stripe), grille alignée sur le marché (coach ~20–40 €/mois).
20. **Page coach publique** et **plans vendables** (privés par lien d'abord, marketplace ensuite).
21. **Parcours athlète autonome** (sans coach) pour ouvrir l'acquisition.
22. **Sous-groupes et staff multi-coachs** pour les clubs de plus de 50 athlètes.
23. **Site vitrine + centre d'aide public indexable**.
24. **Apps natives** (ou PWA poussée à son maximum) — à arbitrer selon les retours de bêta.
25. **Multi-sport** (vélo/puissance d'abord) — seulement si le marché le demande : c'est le point
    où vous cessez d'être un spécialiste pour devenir un Nolio moins mûr.

**Le conseil de fond** : ne cherchez pas à égaler Nolio fonctionnalité par fonctionnalité. Vous
perdriez sur son terrain (l'ampleur) alors que vous gagnez sur le vôtre (la profondeur). Livrez
l'export montre — le seul manque qui soit rédhibitoire — soldez la dette des audits, et vendez ce
que Nolio ne sait pas faire : **la prescription physiologique en fourchettes et la préparation
physique dans le même outil que la course.**

---

## Sources

**Analyse du dépôt** : `README.md`, `docs/Cahier-des-charges.md`, `docs/Design.md`,
`docs/AUDIT-FONCTIONNEL-2026-08.md`, `docs/AUDIT-BETA-OUVERTE-2026-08.md`,
`docs/AUDIT-BETA-OUVERTE-2026-07.md`, `docs/DEMANDES-API-GARMIN-COROS.md`, code front et back.

**Nolio** (recherche web — site inaccessible depuis cet environnement) :
[nolio.io](https://www.nolio.io/) ·
[nolio.io/features](https://www.nolio.io/features/) ·
[nolio.io/athlete](https://www.nolio.io/athlete/) ·
[nolio.io/premium](https://www.nolio.io/premium/) ·
[nolio.io/marketplace](https://www.nolio.io/marketplace/) ·
[Centre d'aide Nolio](https://help.nolio.io/fr/collections/9081853-sync-et-capteurs) ·
[Suivi des charges en musculation (roadmap)](https://www.nolio.io/upcoming/features/suivi-des-charges-en-musculation/) ·
[Mise à jour printemps 2026](https://www.nolio.io/blog/mise-a-jour-printemps-2026/) ·
[Distances+](https://distances.plus/entrainement/application-nolio-plateforme-entrainement-suivi-trail-running/) ·
[Lepape-Info](https://www.lepape-info.com/equipement/actualite-equipement/nolio-lapplication-francaise-pour-le-suivi-de-lentrainement/) ·
[LaBicycle](https://labicycle-leclub.fr/nolio-facile-puissant-et-gratuit-notre-guide/) ·
[Opentri](https://www.opentri.fr/nolio-presentation/) ·
[Le Petit Pignon](https://lepetitpignon.com/nolio-avis-test-plateforme-entrainement-velo/) ·
[Runagora](https://www.runagora.fr/753836-nolio-appli-mobile.html) ·
[App Store — avis](https://apps.apple.com/fr/app/nolio/id1615773607?see-all=reviews) ·
[Coachbox — tarifs Nolio](https://coachbox.app/fr/comparer/nolio-tarifs/)
