# Audit produit DARI Lab — juillet 2026

> ⚠️ **Document historique — juillet 2026, non maintenu.** Photographie de l'état du produit à
> cette date. La majorité de ses constats a été traitée depuis (palette Cmd+K, boîte de réception,
> fusion des bibliothèques, duplication de modèle, file de retours, Paramètres réels…).
> **Pour l'état actuel, lire [`../AUDIT-BETA-OUVERTE-2026-08.md`](../AUDIT-BETA-OUVERTE-2026-08.md).**

> Audit réalisé du point de vue coach utilisateur / Product Manager / UX Designer.
> Périmètre : fonctionnalités, logique métier, ergonomie, cohérence — pas le code.
> Base : lecture exhaustive des écrans (front Angular) + documentation (`docs/`).

---

## 0. Écarts documentation ↔ application

- ✅ *(traité)* Deux cahiers des charges contradictoires coexistaient : celui d'origine décrivait une stack **Next.js 14 + Supabase** quand l'app réelle est **Angular + Spring Boot**. Il a été retiré du dépôt pour cette raison même — `docs/Cahier-des-charges.md` fait seul foi.
- ✅ Le cœur fonctionnel Darilab est bien implémenté : VDOT, LT1/LT2 (Dmax modifié), VC + D', domaines d'intensité, prescription en fourchettes, sRPE Foster unifié course+force, Nuzzo par défaut, forme = fatigue+douleur (jamais RPE), athlète qui déplace sans modifier. Les invariants métier sont respectés.
- ⚠️ Manques vs CdC Darilab : fiche exercice riche (matériel, niveau, contre-indications, progression/régression, image), types de série avancés réellement configurables (drop set, cluster, myo-reps, iso : le sélecteur existe mais sans panneau de config `set_config`), `required_fields` adaptatifs par niveau d'athlète, cycles force avec progression de charge % hebdo, EMOM minute par minute, Garmin (Strava seul), push de séance vers la montre, groupes d'athlètes M2M avec couleur/icône (un seul groupe par athlète aujourd'hui).
- ⚠️ Le toast au drop d'une séance devait afficher « les charges calculées pour cet athlète » (CdC §8) ; il affiche seulement « X planifiée le… ».

---

## 1. Dashboard (cockpit coach)

- ✅ Vrai pilotage par exception : alertes → « à surveiller » → KPI → club → courses. Hiérarchie conforme au blueprint, c'est le meilleur écran de l'app.
- ✅ Jauge de forme avec fatigue + douleur visibles sans clic, tri par criticité.
- ✅ Sélecteur de périmètre (Tout le club / Mes athlètes / Privés / Club).
- 🐛 Le changement de périmètre ne recharge que forme + alertes ; les KPI (« Athlètes actifs », « Séances à valider »…) ignorent le scope.
- ⚠️ « Séances à valider » pointe vers le calendrier… où aucune file de validation n'existe. Le clic ne mène nulle part d'actionnable.
- ⚠️ « Prochaines courses » n'affiche pas **de quel athlète** est la course, et la ligne n'est pas cliquable.
- ⚠️ Périmètre par défaut « Tout le club » : pour un coach solo, le libellé n'a pas de sens.
- 💡 La carte « Gérer mes athlètes » en bas fait onboarding permanent : la masquer dès qu'il y a des athlètes.
- 💡 Ajouter un mini-flux « derniers retours athlètes » (RPE + commentaires des dernières 24 h) : c'est LA première chose qu'un coach regarde le matin.

## 2. Navigation générale

- ✅ Sidebar claire par groupes (Coaching / Bibliothèques / Club / Réglages), repliable, fil d'Ariane contextuel, bottom-nav mobile + panneau « Plus ».
- ✅ Aide contextuelle bien pensée (recherche d'aide, hints par écran).
- ⚠️ Quatre entrées « bibliothèque » (Bibliothèque, Séances course, Prépa physique, Éducatifs) dont une (« Bibliothèque ») est une vue lecture seule qui duplique les trois autres → confusion garantie. Fusionner en une seule entrée à onglets.
- ⚠️ Pas d'entrée « Messages » dans la nav coach : la messagerie n'est accessible qu'athlète par athlète. Aucun endroit ne montre « 3 messages non lus, tous athlètes confondus ».
- 💡 Recherche globale Cmd+K (athlète, séance) — prévue au blueprint §3B, absente.

## 3. Gestion des athlètes (liste)

- ✅ Recherche, filtre par groupe, pagination, badges statut / invitation en attente.
- ✅ Invitation par lien magique, parcours d'onboarding simple.
- ⚠️ Aucun indicateur de forme/alerte sur les cartes : il faut retourner au dashboard pour savoir qui va mal.
- ⚠️ Pas de tri (nom, dernière activité, prochaine course), pas de vue tableau dense.
- ⚠️ Pas d'affichage de la prochaine course ni de la dernière séance réalisée.
- 💡 La recherche de la liste et celle du switcher de la coquille athlète pourraient être le même composant/geste.

## 4. Fiche athlète (coquille + résumé)

- ✅ Coquille persistante remarquable : identité + onglets qui restent à l'écran, switcher d'athlète depuis le nom, précédent/suivant en conservant l'onglet, bandeau compact au scroll. Pattern au-dessus de Nolio.
- ✅ Export PDF du programme, invitation, statut privé/club visibles.
- ✅ Indisponibilités (blessure/absence) directement sur le résumé et reportées dans le calendrier.
- ⚠️ Pas de pastille de forme (fatigue/douleur) dans le bandeau : l'info clé du dashboard disparaît une fois sur la fiche.
- ⚠️ L'export PDF est figé « 4 prochaines semaines » sans choix de période.
- 🐛 Le bandeau affiche la **VMA saisie à la main** (formulaire) alors que tout le moteur repose sur VDOT/LT2/VC : deux sources de vérité jamais réconciliées. Une VMA obsolète restera affichée à côté d'un VDOT à jour.
- ⚠️ « Activités » n'est pas dans les onglets (route existante mais orpheline visuellement, seulement via Programme).

## 5. Physiologie (LT1, LT2, VDOT, VMA, VC)

- ✅ Panneau physio lisible : VDOT en héros, LT1/LT2/VC avec FC associées, allures d'équivalence.
- ✅ Détection LT1/LT2 en temps réel pendant la saisie des paliers, méthode Dmax conforme au CdC, valeurs au repos.
- ✅ Test VC par régression distance–temps avec D', applicable au profil en un clic.
- ✅ Profil lactate comparé multi-tests.
- ⚠️ Saisie des paliers en **m/s** alors que tout l'affichage est en km/h ou min/km — aucun coach ne pense en m/s sur tapis. Le toggle d'unité ne s'applique qu'à l'affichage, pas à la saisie.
- ⚠️ Historique des tests : on peut « Charger » mais ni supprimer ni annoter un test.
- ⚠️ Le CdC prévoit l'axe X inversé (lent à gauche) pour le profil lactate comparé — non mentionné/visible dans l'implémentation, à vérifier visuellement.
- 💡 Indiquer sur le profil la **date/origine** de chaque seuil (mesuré le X / estimé VDOT) — le tag existe dans l'éditeur de séance (« Seuils mesurés / Allures estimées ») mais pas sur la fiche physio.

## 6. Zones d'intensité

- ✅ Architecture à deux niveaux très solide : modèle de zones au niveau club (nom, couleur, ordre, métriques, règles % d'ancre) + valeurs par athlète auto/manuel/verrouillé avec resync qui respecte le manuel. Façon Nolio, en mieux tracé (infobulle « d'où vient la cible »).
- ✅ Échelle contiguë par métrique, onglets par métrique, légende auto/manuel/verrouillé.
- ⚠️ Créer une zone et définir ses règles (% ancre) sont deux gestes séparés (icône ⚙ discrète) : une zone fraîchement créée reste vide sans qu'on comprenne pourquoi.
- ⚠️ Pas d'aperçu avant resync (« ces 6 valeurs vont changer ») — le coach découvre après coup.
- 💡 « Zones du club » comme libellé est faux pour un coach solo → « Mes zones ».

## 7. Allures & records

- ✅ Saisir un chrono → VDOT + allures dérivées recalculés automatiquement, avec toast explicite. Boucle de calcul automatique exemplaire.
- ✅ Bootstrap malin dans l'éditeur de séance : « profil incomplet — saisis un chrono » sans quitter l'écran.
- ⚠️ Distances fermées (800 m → marathon) : rien pour le trail (pas de perf par temps/D+, pas de KV) alors que le produit se veut route **et** trail.
- ⚠️ Un record se supprime mais ne s'édite pas (faute de frappe = supprimer + resaisir).
- ⚠️ Pas de graphique d'évolution du VDOT / des records dans le temps — la donnée existe, la progression n'est jamais montrée.

## 8. Bibliothèque de séances (course)

- ✅ Recherche instantanée, filtre catégorie, compteur, vues cartes/liste, favoris, section « Fréquentes » (use_count) — très bon.
- ✅ Modale de consultation avec réaffectation de catégorie.
- ⚠️ **Impossible de dupliquer un modèle** — le geste n° 1 d'un coach (variante d'une séance existante).
- ⚠️ Création en deux temps (métadonnées puis structure) avec deux champs quasi identiques « Nom » et « Titre » : personne ne comprend la différence.
- ⚠️ Les cartes n'aperçoivent pas la structure (« 5 étape(s) » ne dit rien) — afficher la barre de zones comme sur les chips calendrier.
- ⚠️ Pas de partage de bibliothèque entre coachs du club (prévu CdC §3.2.3).

## 9. Création / édition de séance (course)

- ✅ Éditeur en blocs excellent : reps × volume × zone + RPE, récup imbriquée, presets par section, bascule distance/durée, mode « séance simple » vs structurée, drag pour réordonner, éducatifs par bloc, note par bloc, total temps/km en tête.
- ✅ Cibles calculées en direct pour un athlète d'aperçu, avec lien direct « régler sur la fiche → » si valeur manquante.
- ✅ Le même éditeur sert au modèle et à l'adaptation d'une séance planifiée (« Adapter la structure »).
- ⚠️ Pas de duplication de bloc (une pyramide = tout ressaisir).
- ⚠️ Le RPE de bloc est un input libre 1–10 sans repère verbal (échelle CR10 : « très dur », etc.).
- 💡 Aperçu « ce que verra l'athlète » (le rendu Today) avant d'enregistrer.

## 10. Calendrier & drag-and-drop

- ✅ Le plus complet de l'app : semaine/mois, prévu/réalisé/les deux, D&D depuis la bibliothèque (course, force, éducatifs), menu contextuel clic droit (ouvrir, adapter, déplacer/dupliquer à une date, supprimer), duplication de semaine, **générateur de mésocycle** avec modèles réutilisables et cible groupe, totaux hebdo prévu/réalisé, densité km/jour, drapeau « charge » sur les jours à risque, indispos, notes, objectifs, tests, mode lecture seule selon droits.
- ✅ Rapprochement activité ↔ séance visible (check sur l'activité).
- 🐛 **Les chips force sont inertes** : ni clic (pas de détail), ni drag (pas de déplacement), ni suppression depuis le calendrier. Une séance de force planifiée est intouchable là où tout le reste se manipule.
- 🐛 Cliquer une note = suppression directe (le titre dit « cliquer pour supprimer ») : destructif sans confirmation, et aucune édition possible.
- 🐛 Les dates affichées à l'utilisateur (titre du picker, toasts « planifiée le 2026-07-30 ») sont en ISO brut au lieu de « mer. 30 juil. ».
- ⚠️ Pas de vue multi-athlètes / par groupe : un coach de club planifie athlète par athlète. C'est LE manque face à Nolio.
- ⚠️ Le toast de drop n'affiche pas les charges calculées (spec CdC §8).
- ⚠️ Pas d'undo après déplacement/suppression.
- ⚠️ Le total hebdo est en km/durée mais pas en **charge (UA)** alors que le moteur la calcule.
- ✅ Côté athlète : déplacement via bottom sheet avec le message « le contenu reste inchangé » — invariant CdC parfaitement respecté.

## 11. Catégories

- ✅ CRUD catégories course avec garde-fou (« la suppression détache sans effacer »), réaffectation depuis la modale.
- ⚠️ Trois systèmes de catégorisation étanches : catégories course, catégories force (« perso. »), groupes d'athlètes. Même concept, trois UI différentes.
- ⚠️ Sur l'onglet Exercices force : deux selects « catégorie » côte à côte (type d'exercice + catégorie perso) sans distinction claire — illisible.
- ⚠️ Pas de couleur/icône de catégorie (prévu CdC §11 pour les groupes).

## 12. Préparation physique

- ✅ Les 5 onglets du CdC sont là : Exercices, Séances, Cycles, Tests 1RM, Suivi & Analyse.
- ✅ Calculateur Nuzzo/Epley/Brzycki + zones Lacourpaille en kg pour l'athlète, tests 4 protocoles, source tested > estimated.
- ✅ Éditeur de séance : blocs typés, formats (EMOM/AMRAP/circuit…), double référentiel charge × effort en fourchettes, tempo, aperçu athlète, charge cible calculée depuis le e1RM.
- ✅ Côté athlète : saisie série par série, progression suggérée (« prochaine fois : +2,5 kg ») et alertes affichées.
- 🐛 Suivi & Analyse : la colonne « Exercice » du profil 1RM affiche **un UUID tronqué** (`a3f29b1c…`) au lieu du nom de l'exercice.
- 🐛 Le retour de séance force côté athlète ne propose **pas la douleur** (fatigue + RPE seulement) alors que le champ existe dans le modèle et que les alertes douleur / réathlétisation sont au cœur du CdC → `pain` part toujours à null, les alertes douleur force ne peuvent jamais se déclencher.
- ⚠️ Fiche exercice réduite au minimum (nom, type, muscle, vidéo, consignes) vs CdC (matériel, niveau, contre-indications, progression/régression, image).
- ⚠️ Cycles = liste de séances répétées à l'identique chaque semaine : pas de progression de charge hebdomadaire (le cœur du concept de cycle).
- ⚠️ Assignation de cycle : le sélecteur athlète+date est dans une carte séparée des cartes cycles — le lien entre les deux n'est pas évident.
- ⚠️ Pas de graphe e1RM (une liste texte), pas le « graphique multi-courbes » du CdC.
- ⚠️ Types de série avancés : le select existe mais sans configuration spécifique (drops, clusters, mini-séries…).

## 13. RPE & performances perçues

- ✅ Retour athlète en ~10 s : bottom sheet RPE + fatigue + douleur + commentaire, « réalisée / partiellement ». Exactement le bon niveau de friction.
- ✅ Séparation stricte forme (fatigue+douleur) vs RPE, répétée jusque dans Paramètres.
- ✅ RPE cible par bloc en prescription + RPE ressenti en retour → la boucle prescription/ressenti existe.
- ⚠️ Aucun écran ne confronte **RPE prescrit vs RPE ressenti** (la donnée des deux côtés existe).
- ⚠️ Le coach ne peut pas **commenter une séance réalisée** in situ (il faut passer par la messagerie) — standard chez tous les concurrents.
- ⚠️ Pas de file « retours non lus » : un RPE 9 avec commentaire inquiet peut passer inaperçu si la forme reste verte.

## 14. Charge d'entraînement

- ✅ ACWR avec bande de sécurité, charges 7 j/28 j, monotonie avec seuil d'alerte, répartition par domaine 7/28 j, volume prévu/réalisé + adhérence par statut, fusion réussie de deux anciens écrans.
- ✅ Charge méca vs métabolique (UA) pour la force — différenciant, aucun concurrent grand public ne le fait.
- ⚠️ Pas de **courbe temporelle** ACWR/ATL/CTL : uniquement des valeurs instantanées. Le blueprint prévoit explicitement aire CTL + ligne ATL + bande ACWR annotée.
- ⚠️ Charge visible seulement athlète par athlète — pas de vue d'équipe (qui est dans le rouge cette semaine ?). L'alerte dashboard aide mais ne remplace pas la vue comparative.
- ⚠️ La répartition 7/28 j en domaines ne montre pas la cible (ex. 80/20) pour juger la distribution.

## 15. Paramètres

- ✅ Thème clair/sombre/système.
- ⚠️ Écran essentiellement **vitrine** : profil coach non éditable (ni nom, ni mot de passe), « Domaines d'intensité (défauts) » et « Prescription » sont du texte statique non réglable, facturation placeholder bêta.
- ⚠️ Aucune préférence d'unités (min/km vs km/h par défaut), de premier jour de semaine, de langue.
- 💡 Les « règles Darilab » affichées sont une bonne idée pédagogique — les déplacer vers l'aide.

## 16. Messagerie

- ✅ Réponses rapides, pièces jointes (image/PDF), chip « séance liée », séparateurs de jour.
- ⚠️ Pas de boîte de réception coach ni de badge non-lus agrégé (cf. §2).
- ⚠️ Pas d'envoi d'un message depuis une séance (« commenter cette séance » → message pré-lié).

## 17. Responsive / PWA (audit statique)

- ✅ Stratégie assumée : bottom-nav + « Plus » mobile, sidebar desktop, PWA athlète (install, offline banner, push, update banner).
- ✅ Tables en overflow-x, bandeau athlète compact au scroll.
- ⚠️ Le calendrier 7 colonnes + sidebar bibliothèque sur mobile coach risque d'être inutilisable ; le CdC prévoit « appui long » mobile pour replanifier — le menu contextuel est clic droit only (pas d'équivalent tactile visible).
- ⚠️ Saisie force sur mobile : 3 inputs number par série × n séries — OK mais un pavé « répéter la valeur précédente » économiserait 80 % des frappes.

## 18. Cohérence générale

- 🐛 Tutoiement/vouvoiement mélangés partout, parfois dans le même écran (« Ajustez et verrouillez… » vs « tu n'es pas référent », « Range tes séances » vs « Cliquez une séance »). Choisir (le tutoiement colle mieux au produit) et uniformiser.
- ⚠️ Terminologie flottante : « Objectifs » (onglet) = « courses » ; « Bibliothèque » désigne deux choses ; « Zones du club » vs coach solo ; « Séances course » vs « Prépa physique » vs « Éducatifs » au même niveau de nav alors que ce sont trois natures différentes.
- ✅ Système visuel cohérent : couleurs de zones identiques calendrier/éditeur/analytics, tags d'origine de donnée (calculé/saisi), fourchettes partout, tabular numbers.
- ✅ États vides soignés avec CTA quasi partout.

---

# Bilan

## Points forts (au-dessus de la moyenne du marché)

1. **Le moteur physiologique intégré** : records → VDOT → allures → zones → cibles de séance, recalculé automatiquement de bout en bout, avec traçabilité (auto/manuel/verrouillé, mesuré/estimé). Ni Nolio ni TrainingPeaks ne font cette chaîne aussi proprement.
2. **Tests lactate + VC in-app** avec détection Dmax temps réel — unique sur ce segment de prix.
3. **La coquille athlète** (contexte persistant + switcher + prev/next) : navigation coach plus fluide que les concurrents.
4. **Dashboard par exception** fondé sur fatigue+douleur (pas le RPE) — posture métier juste et différenciante.
5. **Course + force unifiées** : même calendrier, charge sRPE fusionnée, charge méca/métab — le point faible historique de TrainingPeaks.
6. **Générateur de mésocycle** avec modèles et application à un groupe.
7. **Boucle athlète en 10 secondes** (RPE/fatigue/douleur) + progression force suggérée automatiquement.

## Fonctionnalités manquantes (indispensables pour un logiciel de coaching moderne)

- **Vue calendrier multi-athlètes / groupe** (planifier et surveiller une semaine d'équipe).
- **Boîte de réception coach** : messages non lus + retours de séance à traiter, tous athlètes confondus.
- **Commentaire coach sur une séance réalisée** (feedback in situ, pas via messagerie).
- **Courbes temporelles de charge** (ACWR/ATL/CTL) et **d'évolution** (VDOT, e1RM, records).
- **Import FIT/GPX manuel + Garmin/Coros** (Strava seul = dépendance risquée, cf. risques du CdC).
- **Duplication** (modèle de séance, bloc, exercice).
- **Records/tests trail** (effort par temps, D+, KV).
- **Wellness quotidien léger** (sommeil/humeur/FC repos) pour nourrir la forme au-delà des retours de séance.
- Paramètres réels (profil, mot de passe, unités, défauts de domaines).

## Améliorations prioritaires

### 🔴 Priorité élevée (bugs ou trous dans des parcours cœur)
1. Chips force du calendrier inertes : ouvrir/déplacer/supprimer une séance force planifiée.
2. Douleur absente du retour force athlète → alertes douleur mortes (champ envoyé à null).
3. UUID affiché à la place du nom d'exercice dans Suivi & Analyse force.
4. KPI « Séances à valider » sans écran de destination : créer la file de validation/retours.
5. Vue calendrier par groupe/multi-athlètes.
6. Boîte de réception messages + retours (avec badge non-lus dans la sidebar).
7. Note calendrier : clic = suppression sans confirmation.
8. KPI dashboard qui ignorent le sélecteur de périmètre.

### 🟠 Priorité moyenne (valeur produit forte)
9. Duplication de modèle de séance + duplication de bloc dans l'éditeur.
10. Fusion des 4 entrées « bibliothèque » en une seule à onglets.
11. Graphe temporel ACWR/ATL/CTL + évolution VDOT/e1RM.
12. Commentaire coach sur séance réalisée + confrontation RPE prescrit vs ressenti.
13. Saisie des paliers lactate en km/h (ou selon l'unité choisie), édition/suppression de tests et records.
14. Cycles force avec progression de charge hebdomadaire ; fiche exercice enrichie.
15. Réconcilier VMA saisie vs moteur VDOT (une seule source de vérité affichée).
16. Athlète de la course visible + lien sur « Prochaines courses » ; total hebdo en UA.
17. Charges calculées dans le toast de drop (conformité CdC).
18. Écran Paramètres réel (profil, unités, domaines par défaut éditables).

### 🟢 Confort / qualité perçue
19. Uniformiser tutoiement/vouvoiement et la terminologie (Objectifs/Courses, Bibliothèque…).
20. Dates localisées partout (fin des `2026-07-30` visibles).
21. Pastille de forme sur la liste athlètes et dans le bandeau de la coquille.
22. Undo après déplacement/suppression de séance.
23. Recherche globale Cmd+K.
24. Aperçu de structure (barre de zones) sur les cartes de modèles.
25. Échelle RPE avec libellés verbaux ; pavé « répéter la série précédente » côté athlète.
26. Masquer la carte onboarding du dashboard après le premier athlète.
27. Alternative tactile au menu contextuel clic droit (appui long, prévu au CdC).

---

*Fin de l'audit — DARI Lab, juillet 2026.*
