# Audit UI / UX & Design — DARI Lab

> Regard *Lead Product Designer / Head of UX*, pas revue de code.
> Périmètre : l'intégralité des écrans coach (laptop-first), athlète (mobile-first), admin et public.
> Base : lecture exhaustive du front Angular (`front/src/app`), du design system (`front/src/styles.scss`) et de `docs/`.
> Complète — sans les répéter — `docs/audit-produit-dari-lab.md` (fonctionnel) et `docs/ux-redesign-blueprint.md` (cible).
> Note : une grande partie des correctifs de l'audit produit est **déjà en place** (palette Cmd+K, boîte de réception, fusion des bibliothèques, duplication de modèle, commentaire coach, undo au déplacement, courbe de charge, échelle RPE verbale, dates localisées). Ce document part de l'état réel du code, pas de l'audit précédent.

---

# 0. Verdict en une page

**Le produit est déjà au-dessus du marché sur la substance** (moteur physio, prévu/réalisé, course+force unifiées, coquille athlète). Il n'a **pas encore l'apparence ni la vitesse d'un outil premium**. Trois causes, toutes réparables :

1. ~~**Le calendrier ne se pilote pas au clavier ni au geste.**~~ ✅ **Corrigé** : multi-sélection (clic, Cmd+clic, Maj+clic, rectangle), presse-papier `Cmd+C/V/D`, `Suppr`, pile d'annulation `Cmd+Z` / `Cmd+Maj+Z`, sélection de plage sur les jours vides, `Alt`+glisser = copier, duplication de semaine et mésocycle réactivés.
2. **La boucle athlète fuit.** Seule la séance **du jour** est notable (`portal.today()`). ✅ *Corrigé depuis : toute séance non clôturée des 7 derniers jours est notable depuis Aujourd'hui (bandeau), l'agenda et l'historique.*
3. **Le système visuel est bon mais pas tenu.** Deux palettes sémantiques concurrentes, quatre teals. ✅ *Corrigé depuis : glyphes texte remplacés par Lucide, skeletons partout, `--ink-4` conforme, polices auto-hébergées, plancher typo 11px. Reste ouvert : la fusion des deux palettes sémantiques et les quatre teals.*

Rien ici n'est structurel. Le socle est sain ; c'est un travail de **finition et d'accélération**, pas de refonte.

---

# 1. Système de design (transversal)

### ✅ Ce qui fonctionne
- Tokenisation complète et rebrandable (`--*` partout, aucune valeur en dur), thème sombre complet par `data-theme`.
- Direction artistique tenue et différenciante : papier chaud `#f4f2ec`, encre neutre, signature petrol-teal. Ça ne ressemble à aucun concurrent — c'est un actif.
- Typo cohérente : Archivo (display) / IBM Plex Sans (UI) / IBM Plex Mono (données, `tnum`). Les métriques en mono tabulaire sont appliquées partout.
- Icônes Lucide via un wrapper unique `<app-icon>`, taille et stroke paramétrés.
- `:focus-visible` global, `prefers-reduced-motion` respecté, cibles ≥44px sur les boutons.

### ⚠️ Ce qui est confus
- **Deux systèmes sémantiques coexistent** : `--zone-1…5` (canonique Z1–Z5) *et* `--dari-teal / orange / violet / green / yellow / red`, `--domain-1…3`, `--form-*`, `--block-*`. Le vert `#16c47f` (Z2) et le vert `#10b981` (forme OK) sont deux verts différents pour deux sens différents, à 3 pixels d'écart.
- **Quatre teals** : `--primary #0e6e78`, `--accent-2 #0aa3bf`, `--dari-teal #2dd4bf` (route ET activité réalisée ET e1RM). Le teal ne veut plus rien dire.
- `--ink-4 #aaaab2` sur `--paper` ≈ **2,4:1** → sous le seuil AA. Il porte du texte réel : `.wt-lb` (9px majuscules dans les totaux hebdo), `.crumbs__sep`, `.day-density__val`, `.ovl-kind`. Du 9px à 2,4:1 est illisible.
- `.stat-card::after` : blob dégradé flouté décoratif sur des cartes de données — contredit la règle « pas de couleur décorative sur une donnée » du blueprint.
- `.btn { min-height: 48px }` par défaut : dimension mobile appliquée à des barres d'outils coach denses. `btn-sm` (40px) est utilisé en rattrapage presque partout — signe que le défaut est faux côté laptop.
- Vocabulaire de glyphes mixte : `←` `→` `✕` `×` `＋` `↻` `▮` `▮` en texte brut à côté d'un jeu Lucide impeccable.

### 💡 Améliorations
- Fusionner les deux palettes : **une seule échelle par concept** (intensité, forme, discipline, format de bloc), documentée dans `/dev/ui-kit`, et supprimer les alias `--dari-*` redondants.
- Remonter `--ink-4` à ~`#8b8d96` (≥3:1) et **interdire le 9px** : plancher à 11px pour tout texte lisible.
- Ajouter une densité coach : `--control-h-sm: 32px` / `--control-h: 40px` / `--control-h-lg: 48px`, `.btn` par défaut à 40px, 48px réservé au mobile athlète.
- Remplacer les 7 glyphes texte restants par des icônes Lucide (`x`, `plus`, `arrow-left/right`, `rotate-ccw`).
- Self-héberger Archivo/IBM Plex : le `@import url('https://fonts.googleapis.com/…')` de `styles.scss:9` est **bloquant au rendu**, provoque un FOUT au premier écran et crée une dépendance réseau tierce. `<link rel=preload>` + `font-display: swap` sur des fichiers locaux.
- Homogénéiser le chargement : **skeleton partout**, zéro « Chargement… » textuel (26 fichiers concernés, dont Athlètes, Bibliothèque, Éditeur de séance, Résumé athlète, Club, Prépa physique).

### ✨ Effet waouh
- Un **`/dev/ui-kit` public en interne** comme la doc Linear : chaque token, chaque état, chaque composant, avec les contrastes calculés en direct. Ça force la discipline et impressionne les partenaires.
- **Transition de vue** (`view-transition-api`) sur les changements d'onglet de la coquille athlète et sur le passage semaine ↔ mois : coût nul, effet natif immédiat.

---

# 2. Écran par écran — Espace coach

## 2.1 Cockpit (Tableau de bord)

- ✅ Meilleur écran de l'app. Pilotage par exception : Alertes → À surveiller → KPI → Club → Courses. La hiérarchie se lit en 2 secondes.
- ✅ Jauge de forme fatigue+douleur visible sans clic, couleur **+ libellé** (daltonisme respecté), sélecteur de périmètre qui pilote bien les trois requêtes.
- ✅ Carte d'onboarding masquée dès qu'un athlète existe.
- ⚠️ Sur 4 KPI, **3 sont cliquables et 1 ne l'est pas** (« Réalisées cette semaine »). Affordance incohérente : l'œil ne sait pas ce qui est actionnable.
- ⚠️ « Prochaines courses » est le seul bloc qui ne montre **rien du plan** : ni « prêt / pas prêt », ni volume de la semaine d'avant-course.
- ⚠️ Aucun écran ne répond à « **qui court quoi aujourd'hui ?** » — la question n°1 d'un coach de club le matin.
- 💡 Ajouter une zone « Aujourd'hui » : liste compacte séance × athlète du jour, avec pastille de statut, en tête de cockpit.
- 💡 Inliner les 3 derniers retours (RPE + extrait de commentaire) sous le KPI « Retours à traiter » — aujourd'hui il faut cliquer pour savoir s'il y a urgence.
- ✨ **Cockpit temporel** : un ruban « ma semaine » (7 colonnes, densité de charge du club) au-dessus des alertes. En un regard : lundi chargé, jeudi vide, samedi course.
- ✨ Salutation contextuelle réelle : « 3 retours, 1 alerte douleur, 12 séances aujourd'hui » — une phrase, pas un titre décoratif.

## 2.2 Calendrier (cœur de l'app)

- ✅ Densément informatif et déjà supérieur à beaucoup de concurrents : semaine/mois, prévu/réalisé/les deux, colonne de totaux hebdo (durée + km + **UA**), densité km/jour, drapeau « charge », indispos hachurées, objectifs/tests/notes en chips distinctes, activités réalisées façon Nolio avec check de rapprochement.
- ✅ Drag & drop propre : `cdkDropListGroup`, placeholder pointillé, surbrillance des jours receveurs, déplacement optimiste **avec toast « Annuler »** (8s). Excellent.
- ✅ Menu contextuel clic droit (ouvrir / adapter / déplacer / dupliquer / supprimer) + équivalent tactile par appui long (`onChipPointerDown`). Les chips force ont désormais le même traitement.
- ✅ Vue **groupe** (ligne athlète × 7 jours) et panneau bibliothèque latéral avec favoris / fréquentes / accordéons par catégorie.
- 🔴 **Duplication de semaine et générateur de mésocycle sont désactivés** (`advancedPlanning = false`). Ce sont les deux gestes qui font gagner des heures. Un coach qui programme 40 athlètes sans « dupliquer la semaine » ne restera pas.
- ✅ **Barre d'outils condensée** *(livré)* : `[Athlète ▾] [◀ période ▶] [Aujourd'hui]` à gauche ; menu « Vue » (périmètre, prévu/réalisé, semaine/mois, bibliothèque), menu « Actions », annuler/rétablir et `?` à droite. Six éléments visibles au lieu de douze.
- ⚠️ **Le mode Groupe est une impasse** : pas de bibliothèque (donc **impossible de planifier**, seulement de déplacer), pas de vue mois, pas de totaux, pas de bannière lecture seule. On peut regarder, pas travailler.
- ✅ **Switcher d'athlète filtrable** *(livré)* : `<app-athlete-switcher>`, extrait de la coquille athlète et partagé.
- ⚠️ Le picker « + » d'un jour liste **tous** les modèles, sans recherche ni catégorie ni favoris — pendant que le panneau latéral, lui, fait tout ça. Deux UX pour le même besoin.
- ⚠️ Le « + » pointillé est affiché en permanence sur les 7 jours : 7 éléments de bruit visuel dans une grille qui doit se lire d'un coup d'œil.
- ✅ **Clavier complet** *(livré)* : navigation (`←` `→` `T` `W` `M` `B`), sélection (`Cmd+A`, `Échap`), édition (`Cmd+C/V/D`, `Suppr`, `N`), annulation (`Cmd+Z`, `Cmd+Maj+Z`), et `?` pour l'aide-mémoire.
- 💡 **Condenser la barre** : `[Athlète ▾] [◀ Sem. 31 ▶] [Aujourd'hui]` à gauche ; tout le reste (Prévu/Réalisé, Semaine/Mois, Bibliothèque, Dupliquer, Mésocycle) dans un menu « Vue » + une barre d'actions à droite.
- 💡 Réutiliser `<app-session-library-panel>` dans le picker « + » **et** en mode groupe.
- 💡 « + » révélé au survol de la colonne (conservé en permanence sur tactile).
- 💡 Réactiver `advancedPlanning` (le code est écrit et testé) et ajouter au menu contextuel de la **colonne de totaux** : « Dupliquer cette semaine vers → », « Vider la semaine », « Décaler la semaine de ±1 j ».
- ✅ **Multi-sélection + copier/coller** *(livré)*. Un lot copié depuis plusieurs jours se recolle en bloc, écarts de jour conservés.
- ✨ **Alt+glisser = copier** (au lieu de déplacer), avec curseur `copy` et chip fantôme — un standard universel, coût faible.
- ✨ **Peinture de semaine** : glisser un modèle sur l'en-tête « Lun » pour le planifier chez **tous** les athlètes du groupe affiché, avec un récapitulatif avant validation.
- ✅ **Sélection de plage** *(livré)* : un rectangle qui n'attrape aucune chip désigne des jours → « planifier une séance sur ces jours », « vider ces jours ». Le sélecteur « + » accepte N jours. *Reste ouvert : poser une indispo sur la plage.*

## 2.3 Bibliothèque (Course / Prépa physique / Éducatifs)

- ✅ Une seule entrée à onglets, recherche instantanée, filtre catégorie, compteur, bascule cartes/liste dense.
- ✅ **Aperçu de la forme de séance** (`<app-zone-bar>`) sur les cartes *et* les lignes : on distingue un footing d'une VMA sans ouvrir.
- ✅ Duplication en un clic, modale de consultation, réaffectation de catégorie.
- ⚠️ **Favoris et « Fréquentes » n'existent que dans le panneau du calendrier**, pas dans l'écran Bibliothèque lui-même. Le coach ne peut pas épingler depuis l'endroit où il range.
- ⚠️ **« Nom » + « Titre »** toujours demandés à la création, sans que rien n'explique la différence. Personne ne saura quoi mettre.
- ⚠️ Création en 2 temps (carte de formulaire poussée au-dessus de la liste, qui décale tout, puis navigation vers l'éditeur).
- ⚠️ Pas de tri (récent, plus utilisé, alphabétique), pas de multi-sélection, pas d'action groupée (ranger 12 séances dans une catégorie = 12 allers-retours).
- ⚠️ Une seule catégorie par modèle : pas de tags croisés (« VMA » + « hiver » + « piste »).
- 💡 Supprimer « Titre » ou le renommer explicitement (`Nom interne` / `Nom vu par l'athlète`) avec un exemple sous chaque champ.
- 💡 Création en **panneau latéral** (comme la note du calendrier), sans quitter la liste.
- 💡 Étoile de favori + compteur d'utilisation directement sur les cartes ; section « Fréquentes » en tête de liste.
- ✨ **Aperçu au survol** (300 ms) : la structure complète en popover, sans clic. C'est le geste Superhuman appliqué à une bibliothèque.
- ✨ **Bibliothèque partagée du club** + « importer depuis la communauté » : un socle de 30 séances types livré à l'inscription supprime la page blanche.

## 2.4 Éditeur de séance (structure)

- ✅ Le meilleur composant de l'app. Une ligne = `reps × volume × zone × RPE` + outils, récup imbriquée, bascule distance/durée en un clic, poignée de drag, duplication de bloc, total en tête.
- ✅ Cible calculée en direct pour un athlète d'aperçu, avec bootstrap « profil incomplet → saisis un chrono » **sans quitter l'écran**. Très fort.
- ✅ Panneau d'aperçu replié par défaut : ne pollue pas la construction.
- ⚠️ **Aucune auto-sauvegarde, aucun garde-fou de sortie** (pas de `canDeactivate`). Un clic sur « Calendrier » et 10 minutes de travail disparaissent sans un mot.
- ⚠️ Pas d'undo/redo dans l'éditeur.
- ⚠️ Le RPE de bloc est un `<input number>` : le repère verbal CR10 n'existe que dans le `title` (invisible à la souris rapide, inexistant au clavier).
- ⚠️ Pas d'aperçu « ce que verra l'athlète » avant d'enregistrer.
- 💡 Auto-save par debounce + pastille « Enregistré · il y a 3 s » (Notion), et `beforeunload` en filet.
- 💡 Clavier : `Entrée` = nouveau bloc frère, `Tab` = champ suivant, `Cmd+D` = dupliquer le bloc, `Cmd+Z` = annuler.
- 💡 Remplacer l'input RPE par la même échelle 1–10 avec libellé que côté athlète — pour que coach et athlète parlent du même « 7 ».
- ✨ **Saisie en langage naturel** : un champ unique où l'on tape `3x(6x400m Z5 r=1min) r=3min` qui se déplie en blocs. C'est le « waouh » absolu pour un coach expérimenté, et c'est un parseur de 200 lignes.
- ✨ Bouton « Aperçu athlète » qui affiche le rendu mobile réel dans un cadre de téléphone.

## 2.5 Fiche athlète (coquille + onglets)

- ✅ **Pattern remarquable, au-dessus de Nolio** : identité + métriques + onglets persistants, bandeau qui se compacte au scroll, nom = switcher filtrable, précédent/suivant en conservant l'onglet courant.
- ✅ Pastille de forme (fatigue/douleur + « aucun retour récent ») dans le bandeau, tag d'origine de la donnée sur la vitesse de référence, export PDF avec choix de période.
- ✅ Résumé bien hiérarchisé : physio en héros, indispos visibles, rattachements repliés, zone dangereuse isolée en bas.
- ⚠️ **« Activités » n'est pas dans les onglets** alors que la route existe : section orpheline, atteignable seulement par rebond.
- ⚠️ 7 onglets + 6 métriques + 4 badges + 3 boutons dans un bandeau qui reste à l'écran : c'est beaucoup de mobilier permanent.
- ⚠️ Pas de raccourci vers l'action la plus fréquente depuis une fiche : « planifier une séance à cet athlète ».
- 💡 Ajouter « Activités » aux onglets (ou l'absorber explicitement dans « Programme »).
- 💡 `J` / `K` (ou `←` / `→`) pour athlète précédent/suivant, `1`–`7` pour changer d'onglet.
- ✨ **Panneau de comparaison** : sélectionner 2–3 athlètes et superposer charge / VDOT / adhérence. Aucun concurrent à ce prix ne le fait.

## 2.6 Charge & progression / Analytics

- ✅ ACWR dominant avec bande de sécurité, courbe temporelle 12 semaines, monotonie avec seuil, répartition par domaine 7 j / 28 j, volume prévu vs réalisé.
- ✅ Charge méca vs métabolique côté force — vrai différenciant.
- ⚠️ La répartition par domaine n'affiche **pas la cible** (ex. 80/20) : un chiffre sans référentiel ne pilote rien.
- ⚠️ Écran athlète par athlète uniquement : pas de vue comparative d'équipe (« qui est dans le rouge cette semaine ? »).
- ⚠️ Le titre « Charge & progression » couvre deux écrans fusionnés ; la couture reste visible (deux sous-titres, deux logiques de KPI).
- 💡 Bande cible sur la répartition + un mot de verdict (« distribution polarisée, conforme »).
- ✨ **Vue club « heatmap de charge »** : athlètes en lignes, semaines en colonnes, cellules colorées par ACWR. Une seule image répond à « qui je vais casser ? ».

## 2.7 Zones d'intensité

- ✅ Architecture à deux niveaux (modèle club + valeurs athlète auto/manuel/verrouillé) avec resync qui respecte le manuel, infobulle « d'où vient la cible », onglets par métrique.
- ⚠️ Créer une zone et définir ses règles (% ancre) restent deux gestes séparés derrière une icône ⚙ discrète : une zone fraîchement créée paraît cassée.
- ⚠️ Pas d'aperçu avant resync (« ces 6 valeurs vont changer de X à Y »).
- 💡 Fusionner création + règles dans un seul formulaire.
- ✨ Diff avant/après resync, avec possibilité de décocher ligne par ligne.

## 2.8 Prépa physique

- ✅ 5 onglets conformes au cahier des charges, fiche exercice enrichie (matériel multi-select, niveau), calculateur 1RM, éditeur de blocs typés.
- ⚠️ **Deux sélecteurs « catégorie » côte à côte** dans la même barre de filtres (type d'exercice + catégorie perso.) — plus un troisième dans le formulaire. Illisible.
- ⚠️ La barre de filtres contient 4 contrôles + 1 bouton, suivie d'une carte « gérer les catégories » toujours visible : la gestion pollue la consultation.
- ⚠️ Trois systèmes de catégorisation étanches dans l'app (catégories course, catégories force, groupes d'athlètes) avec trois UI différentes.
- 💡 Renommer sans ambiguïté : « Type » (mouvement) vs « Mes dossiers » (rangement perso.).
- 💡 Gestion des catégories dans un panneau latéral, pas en carte permanente.
- ✨ Un composant « organisateur » unique réutilisé pour les trois taxonomies (couleur + icône + drag pour ranger).

## 2.9 Retours à traiter / Messages

- ✅ File de retours unifiée course+force, triée, avec badges RPE/fatigue/douleur, extrait de commentaire, ligne surlignée si douleur ≥ 3, sélecteur de périmètre.
- ✅ Boîte de réception coach avec compteur de non-lus, aperçu du dernier message, badge dans la sidebar.
- ⚠️ **« Retours à traiter » n'est pas dans la navigation** : accessible uniquement par le KPI du cockpit. C'est pourtant l'écran à ouvrir tous les matins.
- ⚠️ Pas de badge de comptage sur cette entrée (alors que Messages en a un).
- ⚠️ Traitement un par un : pas de « tout marquer comme vu », pas de réponse rapide depuis la file.
- 💡 Entrée de nav « Retours » avec badge, juste sous Tableau de bord.
- ✨ **Traitement au clavier façon Superhuman** : `J`/`K` pour naviguer, `E` pour classer, `R` pour répondre en ligne, `Cmd+Entrée` pour envoyer. Vider 20 retours en 90 secondes.

## 2.10 Paramètres

- ✅ Écran devenu réel : profil éditable, mot de passe, thème, unité d'allure, domaines d'intensité par défaut modifiables, facturation transparente sur le statut bêta.
- ⚠️ Grille de cartes hétérogènes : « Profil », « Mot de passe », « Affichage », « Domaines », « Facturation », « Règles » au même niveau visuel, sans regroupement.
- ⚠️ Pas de premier jour de semaine, pas de langue, pas de préférences de notifications regroupées ici (elles vivent sur `/app/notifications`).
- 💡 Sous-navigation gauche (Compte · Affichage · Entraînement · Notifications · Facturation) — pattern Linear/Notion, immédiat.

## 2.11 Landing, authentification, admin

- ✅ Landing sobre et crédible, aperçu des zones, CTA clair, bandeau d'installation PWA.
- ✅ Auth minimaliste, invitation par lien magique, vérification d'e-mail avec relance in-app.
- 🐛 **La landing vouvoie** (« Entraînez. Suivez. Progressez. », « Importez les activités ») alors que **toute l'app tutoie**. Premier contact = première incohérence.
- 🐛 Le pied de page public affiche **l'état de l'API** (« API en ligne · v… » / « API injoignable »). Un prospect n'a pas à voir ça ; « API injoignable » sur la page d'accueil tue la crédibilité.
- ⚠️ Admin : tables denses correctes mais « Chargement… » textuel partout, aucun état vide travaillé.
- 💡 Uniformiser le tutoiement dès la landing ; déplacer le ping dans `/dev` ou derrière l'auth.
- ✨ Une capture animée du calendrier en héro (WebM, 6 s, en boucle) vaut trois paragraphes de promesse.

---

# 3. Écran par écran — Espace athlète (mobile)

## 3.1 Aujourd'hui

- ✅ Exactement le bon écran : carte héro sombre, statut, titre, distance en gros mono, prescription en fourchettes, une seule action primaire (« Noter mon retour »).
- ✅ Compte à rebours d'objectif, incitation « ajoute un chrono » quand les allures manquent, état vide chaleureux (« Repos aujourd'hui 🌙 »).
- ✅ **Retour en bottom sheet, ~10 s** : RPE + fatigue + douleur + commentaire optionnel, avec « Réalisée » / « Partiellement ».
- ✅ **File hors ligne** : le ressenti saisi sans réseau est mis en attente et synchronisé au retour. Rare à ce niveau de produit.
- ✅ Repère verbal CR10 sous l'échelle RPE, bouton « comme la série précédente » (44px) côté force.
- 🔴 **On ne peut noter QUE la séance du jour.** `portal.today()` ne charge qu'aujourd'hui ; l'agenda ouvre une fiche en lecture seule, l'historique aussi. Un athlète qui oublie le soir même **ne peut plus jamais** donner son ressenti — et le coach perd le signal qui alimente tout son cockpit.
- ⚠️ **L'échelle RPE tient en 10 colonnes sur 375 px** : ~29 px de large par bouton (`grid-template-columns: repeat(10, 1fr)`), contre les 44 px exigés par le design system. On tape à côté, après l'effort, avec les mains moites.
- ⚠️ **La séance de force transforme l'écran en formulaire** : un tableau charge/reps/RIR par série × par exercice, directement dans le flux « Aujourd'hui ». Ce n'est plus une carte, c'est une saisie de 30 champs.
- ⚠️ Aucun check-in *avant* séance (sommeil, fraîcheur) : la forme du coach ne se met à jour qu'après coup, d'où les « aucun retour récent » fréquents.
- ⚠️ La barre supérieure porte 5 contrôles (aide, cloche, installer, push, quitter) sur un écran qui devrait en porter zéro.
- 💡 **Rendre notable toute séance non clôturée des 7 derniers jours**, avec un bandeau persistant « 2 retours en attente » sur Aujourd'hui et le même bouton dans l'agenda et l'historique.
- 💡 Passer l'échelle RPE en **2 lignes de 5** (ou en slider à gros pouce) et la colorer Z1→Z5 comme le reste du produit.
- 💡 Déplacer la saisie force dans un **mode séance plein écran** (un exercice à la fois, gros chiffres, `+`/`−` de 2,5 kg, progression « série 2/4 ») ouvert depuis la carte.
- 💡 Réduire la barre supérieure à l'avatar (menu) + cloche ; installer/push/aide vont dans Profil.
- ✨ **Ressenti en 2 taps depuis la notification push** : « Ta séance est finie ? » → RPE en actions rapides. Le taux de retour double.
- ✨ **Auto-détection** : quand une activité Strava est rapprochée d'une séance, ouvrir automatiquement la feuille de ressenti à la prochaine ouverture — l'athlète n'a plus qu'à confirmer.
- ✨ **Célébration** : le token `.celebration` est spécifié dans `Design.md` mais **jamais utilisé**. Une micro-animation + haptique (`navigator.vibrate`) à la validation, un « 12e retour d'affilée 🔥 » : c'est ce qui donne envie de remplir.

## 3.2 Agenda athlète

- ✅ Agenda vertical de la semaine, lisible, badges de zone, indispos, respect strict de l'invariant « déplacer sans modifier » avec le message « le contenu reste inchangé ».
- ✅ Bottom sheet de déplacement avec grille de jours.
- ⚠️ Le bouton « déplacer » est un petit lien texte sous chaque séance : dominant visuellement alors que c'est l'action rare, et absent d'affordance là où on l'attend (appui long).
- ⚠️ Pas de bouton de ressenti sur les séances passées non clôturées (cf. 3.1).
- ⚠️ Pas de vue mois ni de vision « ma semaine en volume ».
- 💡 Appui long = déplacer ; le tap ouvre le détail ; le bouton texte disparaît.
- ✨ Bandeau de semaine : « 42 km prévus · 28 réalisés · 3 séances restantes » en tête d'agenda.

## 3.3 Progrès / Historique / Activités / Performances

- ✅ « Mes progrès » bien pensé : streak de semaines, accès rapides, volume prévu/réalisé en barres, adhérence, répartition par zone, records.
- ✅ Historique groupé par mois, avec ressenti **et** commentaire du coach mis en valeur (fond teinté « Ton coach : »). Excellent pour la relation.
- ⚠️ 5 « accès rapides » en cartes empilées = un menu déguisé en contenu. Sur mobile, ça pousse les vraies données sous la ligne de flottaison.
- ⚠️ Les barres de volume sont dessinées en CSS sans axe ni graduation : jolies, peu lisibles.
- 💡 Transformer les accès rapides en une rangée de chips horizontale scrollable.
- ✨ **Écran « ma progression » narratif** : « +12 % de volume sur 8 semaines », « ton allure seuil a gagné 8 s/km depuis mars ». Strava gagne parce qu'il raconte, pas parce qu'il tabule.

## 3.4 Coquille athlète (navigation)

- ✅ Bottom-nav fixe, thème sombre immersif scopé au portail, safe-area iOS respectée, PWA (install, offline banner, push, update banner).
- ⚠️ **6 entrées** dans la bottom-nav (Séance · Agenda · Sorties · Progrès · Messages · Profil) — le blueprint en prescrit 5 max, et 6 icônes sur 375 px donnent ~52 px par cible, libellés en 10px.
- ⚠️ « Agenda », « Sorties » et « Progrès » se recouvrent conceptuellement pour un utilisateur non expert.
- 💡 Passer à **4** : `Séance · Agenda · Progrès · Messages`. « Sorties » entre dans Progrès, « Profil » devient l'avatar en haut à droite.
- ✨ Onglet actif animé (indicateur qui glisse) + transition de page — 20 lignes de CSS, effet natif.

---

# 4. Analyse UX transversale

## Navigation
- ✅ Évidente côté coach : sidebar groupée, repli persistant, fil d'Ariane contextuel, Cmd+K.
- ⚠️ **Deux écrans clés hors navigation** : « Retours à traiter » (`/app/feedback`) et « Activités » d'un athlète.
- ⚠️ Le mode Groupe du calendrier est une destination, pas un mode : il devrait vivre à côté d'« Athlètes » comme une vraie vue d'équipe.
- 💡 Ordre proposé : **Tableau de bord · Retours (badge) · Athlètes · Groupes · Calendrier · Messages (badge) · Bibliothèque**, puis Club · Zones, puis Réglages.

## Charge cognitive
- Trop d'information : barre d'outils du calendrier, filtres de la prépa physique, bandeau de la fiche athlète, carte « Aujourd'hui » quand il y a une séance de force.
- Trop de formulaires visibles en permanence : gestion des catégories (bibliothèque et force) affichée dans la page au lieu d'un panneau.
- Trop d'étapes : création de modèle (2 écrans), création de zone (2 gestes), planification depuis « + » (modale → liste non filtrée).
- **Règle à appliquer** : une action primaire visible par écran ; tout ce qui est « gestion » passe en panneau latéral.

## Nombre de clics — les gains les plus rentables
| Action | Aujourd'hui | Cible |
|---|---|---|
| Dupliquer la semaine d'un athlète | **impossible** (désactivé) | 1 clic (menu de la colonne totaux) |
| Planifier la même séance sur 5 athlètes | 5 × (changer d'athlète + glisser) | 1 glisser sur une ligne de groupe |
| Copier une séance sur un autre jour | clic droit → date → picker | `Alt+glisser`, ou `Cmd+C`/`Cmd+V` |
| Ranger 10 modèles dans une catégorie | 10 × (ouvrir → select → fermer) | multi-sélection + 1 action groupée |
| Noter un ressenti oublié la veille | **impossible** | 1 tap sur le bandeau « en attente » |
| Atteindre la file des retours | Cockpit → KPI | entrée de nav / `G` puis `R` |
| Changer d'athlète dans le calendrier | select natif déroulé | switcher filtrable, ou `Cmd+K` |

## Workflow « créer un athlète → suivre son évolution »
1. **Créer l'athlète** — ✅ formulaire court, invitation par lien. ⚠️ Après création, on retombe sur la liste : aucun enchaînement proposé.
2. **Configurer le profil** — ⚠️ le coach doit deviner qu'il faut aller saisir un chrono pour débloquer les allures. Le bootstrap de l'éditeur de séance est excellent : il devrait exister sur la fiche.
3. **Créer des séances** — ✅ éditeur très bon. ⚠️ page blanche à l'inscription : aucune séance type fournie.
4. **Planifier** — 🔴 pas de duplication de semaine, pas de mésocycle actif, pas de planification de groupe.
5. **Suivre** — ✅ charge, adhérence, retours. ⚠️ le signal se tarit dès qu'un athlète oublie un ressenti.

**À automatiser en priorité** : un assistant post-création (« Profil → premier chrono → appliquer un plan type → inviter ») en 4 étapes, et la relance automatique de ressenti.

## Calendrier — comparaison marché
| Critère | DARI Lab | Nolio | TrainingPeaks | Intervals.icu |
|---|---|---|---|---|
| Lisibilité de la grille | **Très bonne** (zone-bar, densité, totaux UA) | bonne | dense/vieillissante | brute |
| Aperçu de séance sans clic | **Oui** (barre de zones) | partiel | non | non |
| Drag & drop + undo | **Oui** | oui, sans undo | oui | limité |
| Duplication semaine/bloc | **Oui** (annulable) | oui | oui | oui |
| Multi-athlètes | vue groupe **éditable** | oui, éditable | oui | non |
| Copier/coller clavier | **Oui** (`Cmd+C/V/D`, `Cmd+Z`) | non | partiel | non |
| Menu contextuel | **Oui** | non | non | non |

→ DARI Lab gagne déjà sur la **lecture**. Il perd sur la **production**. Les trois gestes qui inversent le rapport : duplication de semaine, copier/coller, planification de groupe.

## Mobile athlète — le test du « je viens de finir ma séance »
> *Est-ce que j'aurais envie de remplir mon retour ?*

- **Si je l'ouvre le jour même** : oui, franchement. Bottom sheet, 3 gestes, ça marche hors ligne. C'est bon.
- **Si je l'ouvre le lendemain** : non — **je ne peux pas**. Et personne ne me le rappelle.
- **Si c'était une séance de force** : non. Je tombe sur un tableau de 30 champs à remplir sur un écran de 375 px, debout, en sueur.
- **Cible de friction** : ressenti course en 3 taps depuis une notification ; séance de force en mode plein écran guidé, un exercice à la fois.

---

# 5. Le détail qui fait la différence

| Détail premium | État | Action |
|---|---|---|
| Palette de commandes Cmd+K | ✅ présente | y ajouter des **actions** (planifier, créer, aller à la semaine du…), pas seulement de la navigation |
| Recherche globale | ✅ athlètes, séances, écrans | ajouter courses, exercices, messages |
| Undo | ✅ pile réelle `Cmd+Z` / `Cmd+Maj+Z` | l'étendre aux écrans hors calendrier |
| Auto-sauvegarde | ❌ absente | éditeurs de séance (course + force) |
| Duplication en un clic | ✅ modèle, bloc — ❌ semaine | réactiver `advancedPlanning` |
| Drag & drop | ✅ excellent | ajouter `Alt` = copier, drag multi-chips |
| Multi-sélection / actions groupées | ⚠️ calendrier fait | reste bibliothèque et file de retours |
| Menus contextuels | ✅ séances course + force | étendre aux jours, semaines, athlètes, modèles |
| Raccourcis clavier | ✅ calendrier complet + `?` = aide | reste `G`+lettre global |
| Skeleton loaders | ⚠️ 24 oui / 26 non | généraliser |
| États vides | ✅ soignés, avec CTA | ajouter aux écrans admin |
| Tooltips utiles | ✅ nombreux | ne pas y cacher d'information nécessaire (CR10 du RPE) |
| Micro-interactions | ⚠️ `:active scale` seulement | `.celebration` spécifiée mais jamais utilisée |
| Onboarding | ⚠️ une carte au cockpit | assistant 4 étapes + bibliothèque pré-remplie |
| Vues personnalisées | ❌ | filtres calendrier sauvegardables |
| Notifications | ✅ cloche, push, badges | actions rapides dans la notification push |
| Confirmations intelligentes | ✅ `ConfirmService`, jamais `confirm()` | remplacer certaines par un undo (plus rapide) |
| Copier/coller intelligent | ✅ séance, lot, semaine | reste le bloc d'éditeur |
| Favoris | ⚠️ calendrier seulement | remonter dans la bibliothèque |

---

# 6. Bilan

## ⭐ Les plus gros points forts de l'interface

1. **La coquille athlète du coach** — contexte persistant, switcher filtrable, prev/next sans perdre l'onglet. Meilleur que Nolio, meilleur que TrainingPeaks.
2. **Le cockpit par exception** — alertes → à surveiller → KPI. On sait quoi faire en 2 secondes, sans mur de graphiques.
3. **L'éditeur de séance en blocs** — dense, rapide, avec cibles calculées en direct et rattrapage de profil incomplet sur place.
4. **La lecture du calendrier** — barre de zones sur chaque chip, densité km/jour, totaux hebdo en km **et** en UA, drapeau de charge, prévu/réalisé superposés.
5. **La boucle de retour athlète** — bottom sheet en 10 s, hors ligne, avec repère verbal CR10.
6. **La rigueur du système** — tokens, mono tabulaire, tag d'origine de la donnée, couleur **+ libellé** systématique, confirmations centralisées.
7. **La direction artistique** — papier chaud + petrol-teal + IBM Plex : une identité, pas un thème Bootstrap.

## 🔴 Les principaux problèmes UX (par ordre d'importance)

1. ~~**Impossible de noter un ressenti passé** (athlète).~~ ✅ **Corrigé** : fenêtre de rattrapage de 7 jours, notable depuis les trois écrans.
2. ~~**Duplication de semaine et mésocycle désactivés**~~ ✅ **Corrigé** : les deux parcours sont actifs, le drapeau a été supprimé.
3. **Pas de multi-sélection ni de copier/coller au calendrier.** Programmer reste un travail à l'unité.
4. ~~**Le mode Groupe ne permet pas de planifier**~~ ✅ **Corrigé** : la bibliothèque y est disponible et le dépôt sur une ligne d'athlète planifie.
5. **La saisie de force côté athlète est un formulaire de 30 champs sur l'écran d'accueil.** — *reste ouvert (gros chantier n°13).*
6. ~~**Échelle RPE à 29 px de large sur mobile**~~ ✅ **Corrigé** : 2 × 5 boutons, ≥44 px, dégradé Z1→Z5.
7. **Barre d'outils du calendrier surchargée** — *reste ouvert (confort n°17).* Note : le lot livré y a ajouté deux boutons (duplication, mésocycle), la condensation devient plus urgente.
8. ~~**Aucune auto-sauvegarde dans les éditeurs**~~ ✅ **Corrigé** : auto-save + pastille d'état + `canDeactivate`.
9. ~~**« Retours à traiter » absent de la navigation**~~ ✅ **Corrigé** : entrée « Retours » avec badge.
10. ~~**Incohérences de finition**~~ ✅ **Corrigé** : tutoiement de la landing, état de l'API sous `/dev`, skeletons partout, glyphes → Lucide, `--ink-4` conforme, plancher 11px, bottom-nav athlète à 4 onglets, polices auto-hébergées.

## 🚀 Améliorations à plus fort impact

### Impact énorme / faible effort — ✅ **lot livré (juillet 2026)**
1. [x] Réactiver `advancedPlanning` (duplication de semaine + mésocycle) — drapeau et code mort supprimés.
2. [x] Rendre notable toute séance non clôturée des 7 derniers jours + bandeau « X retours en attente » (Aujourd'hui, agenda, historique — feuille de ressenti partagée).
3. [x] RPE en 2 × 5 boutons (≥44 px), coloré Z1→Z5, repère CR10 conservé.
4. [x] Entrée de nav « Retours » avec badge (sidebar + panneau « Plus »).
5. [x] Auto-save (debounce 1,5 s) + pastille d'état + `canDeactivate` dans les deux éditeurs.
6. [x] `Alt`+glisser = copier une séance, avec curseur `copy`, vignette distincte et annulation.
7. [x] Skeletons partout ; glyphes texte → Lucide ; `--ink-4` remonté (≥3:1 sur les deux surfaces) ; polices auto-hébergées ; plancher typo 11px.
8. [x] Landing : tutoiement + état de l'API déplacé sous `/dev/api`.
9. [x] Bottom-nav athlète ramenée à 4 entrées + barre supérieure allégée.
10. [x] Le picker « + » **est** le panneau bibliothèque, disponible aussi en mode Groupe.

### Impact énorme / gros chantier
11. [x] **Multi-sélection + copier/coller/undo global au calendrier** (`Shift`-clic, `Cmd+C/V/D/Z`, `Suppr`).
12. ⚠️ **Mode Groupe pleinement éditable** : bibliothèque et planification par dépôt livrées ; reste « peindre » une séance sur toute une ligne de jour.
13. **Mode séance plein écran côté athlète** pour la force (un exercice à la fois, gros chiffres, `±2,5 kg`).
14. **Assistant d'onboarding coach** (athlète → chrono → plan type → invitation) + bibliothèque de 30 séances pré-remplies.
15. **Vue club « heatmap de charge »** (athlètes × semaines, coloré par ACWR).
16. **Palette de commandes actionnable** (Raycast-like) : planifier, dupliquer, créer, naviguer.

### Amélioration de confort
17. [x] Condenser la barre d'outils du calendrier (menu « Vue »).
18. Aperçu au survol dans la bibliothèque ; favoris + tri + actions groupées.
19. ⚠️ Switcher d'athlète filtrable : composant partagé livré et posé au calendrier ; reste à remplacer les `<select>` natifs des autres écrans.
20. Sous-navigation dans Paramètres ; unification des trois taxonomies.
21. « Activités » dans les onglets de la fiche athlète.
22. Diff avant resync des zones ; création de zone + règles en un seul geste.
23. Chips horizontales au lieu des 5 cartes d'accès rapide côté athlète.

### Idées premium
24. **Saisie de séance en langage naturel** (`3x(6x400m Z5 r=1min)`).
25. **Ressenti en 2 taps depuis la notification push**, et ouverture automatique de la feuille quand une activité Strava est rapprochée.
26. **Panneau de comparaison d'athlètes** (charge / VDOT / adhérence superposés).
27. **Célébrations et haptique** à la validation de séance, streaks, records battus.
28. **Sélection de plage au calendrier** (cliquer-glisser sur plusieurs jours) pour poser un bloc entier.
29. **Vues sauvegardées** (« mes marathoniens, prévu seulement, 4 semaines »).
30. `?` → palette de raccourcis, façon Linear.

---

# 7. Si j'avais 3 mois pour en faire la meilleure app de coaching du marché

## Mois 1 — « Le calendrier devient un instrument »
**Thèse : un coach doit programmer une semaine complète pour 20 athlètes en moins de 10 minutes.**

- Réactiver duplication de semaine + mésocycle, et les sortir de la barre : **menu contextuel sur la colonne de totaux** (dupliquer vers →, vider, décaler).
- **Refonte de la barre d'outils** : `[Athlète ▾] [◀ Semaine 31 ▶] [Aujourd'hui]` + un menu « Vue » + une zone d'actions. Trois éléments visibles au lieu de douze.
- **Sélection et presse-papier** : `Shift`-clic, `Cmd+C/V/D`, `Suppr`, `Cmd+Z` avec pile d'annulation réelle (pas seulement le toast).
- `Alt`+glisser = copier. Sélection de plage sur les jours vides.
- Le picker « + » devient le panneau bibliothèque (recherche, favoris, catégories).
- Switcher d'athlète filtrable ; `Cmd+K` gagne les actions (« planifier X chez Y le Z »).

*Résultat attendu : le temps de programmation d'une semaine divisé par 3 à 5.*

## Mois 2 — « La boucle athlète ne fuit plus »
**Thèse : un retour non rempli est un bug produit, pas une négligence de l'athlète.**

- **Toute séance non clôturée des 7 derniers jours est notable** — depuis Aujourd'hui (bandeau), l'agenda et l'historique.
- **Notification push actionnable** 2 h après l'heure habituelle de séance : « Ta séance est finie ? » → RPE en actions rapides, sans ouvrir l'app.
- Quand une activité Strava est rapprochée, la feuille de ressenti s'ouvre pré-remplie (distance, durée, FC) : l'athlète confirme, il ne saisit pas.
- **Mode séance force plein écran** : un exercice à la fois, gros chiffres, `±2,5 kg`, « série 2/4 », recopie de série. Sortie du flux « Aujourd'hui ».
- **Check-in matinal optionnel** (3 sliders, 8 s) qui nourrit la forme avant la séance, pas seulement après.
- RPE à 44 px, coloré Z1→Z5. Célébration + haptique à la validation, streaks visibles.
- Bottom-nav ramenée à 4 entrées ; barre supérieure réduite à l'avatar + cloche.

*Résultat attendu : taux de retour de séance > 80 %, et une pastille de forme qui n'est plus jamais « stale ».*

## Mois 3 — « Le coach de club, et le vernis premium »
**Thèse : ce qui se vend, c'est la démo de 3 minutes.**

- **Mode Groupe éditable** : bibliothèque disponible, planification par glisser sur une ligne, « peindre » une séance sur toute une colonne de jour avec récapitulatif avant validation.
- **Heatmap de charge du club** (athlètes × semaines, coloré par ACWR) — une image qui vend l'outil à elle seule.
- **Onboarding en 4 étapes** + **30 séances types livrées** : plus jamais de page blanche.
- **Panneau de comparaison d'athlètes.**
- **File de retours au clavier** (`J`/`K`/`E`/`R`) : 20 retours traités en 90 s.
- **Passe de finition intégrale** : un seul système de couleurs sémantiques, `--ink-4` conforme AA, plancher typo 11px, polices auto-hébergées, skeletons partout, zéro glyphe texte, tutoiement uniforme, landing nettoyée, transitions de vue, `?` = palette de raccourcis.
- **Auto-save partout** avec pastille « Enregistré ».

*Résultat attendu : une démo où chaque geste est instantané et où rien ne cloche visuellement.*

## Ce que je repenserais complètement

- **La barre d'outils du calendrier** — aujourd'hui une accumulation, demain trois éléments et un menu.
- **La carte de séance de force côté athlète** — ce n'est pas une carte, c'est un écran. Elle doit sortir du flux.
- **Le picker « + » du calendrier** — supprimé, remplacé par le panneau bibliothèque déjà excellent.
- **Le mode Groupe** — aujourd'hui un affichage, demain la vue de travail par défaut d'un coach de club.
- **La création de modèle** — un seul écran, un seul nom, structure incluse.

## Ce que je ne toucherais pas

La coquille athlète, le cockpit par exception, l'éditeur en blocs, la barre de zones sur les chips, le tag d'origine de la donnée, la file hors ligne du portail athlète, et la direction artistique. C'est déjà du niveau des meilleurs.

---

*Audit UI/UX — DARI Lab, juillet 2026.*

---

# 8. Suivi d'exécution

| Lot | État |
|---|---|
| **Impact énorme / faible effort** (§6, 10 points) | ✅ **Livré** — un commit atomique par point |
| **Gros chantier — calendrier** (§6 n°11, §7 mois 1) | ✅ **Livré** : multi-sélection, presse-papier, pile d'annulation, sélection de plage, menu de semaine, barre d'outils condensée, switcher filtrable |
| Gros chantier — reste (12 → 16) | ⬜ Ouvert |
| Amélioration de confort (17 → 23) | ⬜ Ouvert |
| Idées premium (24 → 30) | ⬜ Ouvert |

**Reste explicitement ouvert au calendrier**, hors du chantier livré : la « peinture de
semaine » (glisser un modèle sur un en-tête de jour pour l'appliquer à tout un groupe), la
pose d'indisponibilité depuis une plage de jours, et la vue mois en mode Groupe.

**Reste explicitement ouvert dans le périmètre visuel**, hors du lot « faible effort » :
la fusion des deux palettes sémantiques (`--zone-*` vs `--dari-*`, les quatre teals), la
densité coach (`--control-h-*`, `.btn` par défaut à 40px) et le `.stat-card::after`
décoratif. Ce sont des refactorings de tokens à impact visuel transverse, pas des
correctifs isolés : ils demandent une passe dédiée avec revue écran par écran.
