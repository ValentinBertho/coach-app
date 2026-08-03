# PROMPT-MAQUETTES.md — Faire dessiner **Dies** par Claude avant de coder

> Trois prompts, à utiliser dans l'ordre. Ils sont **autonomes** : Claude n'a pas besoin du reste du
> dossier de spécifications pour les exécuter, tout le contexte utile y est recopié.
>
> **Pourquoi maquetter d'abord** : une maquette HTML se juge en trente secondes et se jette sans regret.
> Un lot 1 codé se juge en deux heures et se jette avec douleur. On fait valider l'identité visuelle et
> la vue Jour **par elle**, puis on code.
>
> **Où les utiliser** : sur claude.ai (les artefacts s'affichent directement). Les maquettes obtenues se
> rangent dans `docs/maquettes/` du dépôt `dies` et servent de référence visuelle au lot 0.

---

## Prompt 1 — Explorer l'identité visuelle (3 directions, un seul écran)

> À utiliser en premier. Objectif : trancher l'identité **avant** de dessiner dix écrans dans une
> direction qui ne plaira pas.

```
Tu es directeur artistique et designer produit, spécialisé dans les outils métier denses
(logiciels de gestion, back-offices professionnels). Tu maîtrises la typographie, la
hiérarchie de l'information et l'accessibilité.

CONTEXTE
Je conçois « Dies », une application web privée destinée à UNE SEULE utilisatrice : une
juriste (droit des sociétés) qui suit ses dossiers juridiques et surtout leurs échéances
datées — tenir l'assemblée d'approbation des comptes dans les six mois de la clôture,
déposer les comptes au greffe dans le mois qui suit, dénoncer un bail six mois avant
l'échéance triennale, respecter un délai d'appel d'un mois. L'application calcule ces
dates à partir de règles de droit, les rappelle par e-mail, et tient aussi son agenda
(rendez-vous, audiences, assemblées).

Elle ouvrira cet outil trois fois par jour pendant dix ans. Il doit inspirer la fiabilité,
pas l'enthousiasme. Rien ne clignote, rien ne décore.

CE QUE JE TE DEMANDE
Propose 3 directions artistiques distinctes pour le MÊME écran : la vue « Ma journée ».
Un seul artefact HTML contenant les 3 directions les unes sous les autres, séparées par un
titre et deux lignes expliquant le parti pris de chacune.

Contraintes de direction (à respecter dans les 3) :
- densité maîtrisée : elle lit des listes, pas des tuiles décoratives ;
- la DATE et l'HEURE sont l'information reine, en chiffres tabulaires, format JJ/MM/AAAA ;
- une échelle d'urgence lisible : en retard / aujourd'hui / ≤ 7 j / ≤ 30 j / plus tard /
  fait — toujours doublée d'une icône et d'un libellé, jamais la couleur seule ;
- une échéance (date limite, sans heure) et un rendez-vous (créneau horaire) doivent se
  distinguer au premier coup d'œil ;
- accessible : contraste AA, lisible en noir et blanc à l'impression.

Fais varier entre les 3 directions : la palette, la typographie (une serif quelque part,
au moins une direction sans), la densité, le traitement des surfaces (fond clair chaud vs
fond neutre froid), la place de la navigation. Ne fais pas trois fois la même chose en
changeant la couleur d'accent.

CONTENU À AFFICHER (identique dans les 3, données fictives fournies plus bas)
On est le lundi 15 juin 2026.
- 3 échéances du jour ou en retard,
- 4 rendez-vous horodatés,
- un bloc-notes du jour.

DONNÉES FICTIVES (à utiliser telles quelles, ne rien inventer d'autre)
Échéances :
- EN RETARD depuis le 30/05/2026 — « Mise à jour du registre des bénéficiaires effectifs »
  — Hélios Participations — bloquante — art. R.561-55 CMF
- AUJOURD'HUI — « Convocation des associés à l'AG d'approbation » — Atelier Verrières
  — bloquante — art. L.223-26 C. com.
- DANS 5 JOURS (20/06/2026) — « Conclusions d'appelant » — Novaform c/ Delcourt
  — bloquante — art. 908 CPC — délai de procédure
Rendez-vous du 15/06/2026 :
- 09:30–10:00 Point hebdomadaire (interne)
- 11:00–12:00 Rendez-vous notaire — cession de parts — SCI Les Tilleuls (visioconférence)
- 14:30–16:00 Assemblée générale d'approbation des comptes — Atelier Verrières — au siège
- 16:30–17:00 Appel avocat — dossier Novaform

FORMAT DE SORTIE
Un seul artefact HTML autonome : tout le CSS en ligne, aucune police ni image externe
(utilise des piles de polices système, y compris une serif système si tu en veux une),
aucun script indispensable à l'affichage. Français partout. Aucun lorem ipsum.

Termine par 5 lignes maximum : quelle direction tu recommandes, et pourquoi.
```

---

## Prompt 2 — La maquette complète (après avoir choisi une direction)

> À utiliser une fois la direction retenue. Remplacer le bloc `DIRECTION RETENUE` par la description de
> celle qu'elle a choisie (ou coller les tokens de couleur du § 2 de `Design.md` si vous partez de la
> charte déjà écrite).

```
Tu es designer produit spécialisé dans les outils métier denses. Tu vas produire la
maquette navigable complète d'une application web.

LE PRODUIT
« Dies » — application web privée d'une juriste (droit des sociétés), utilisatrice unique.
Elle y suit ses dossiers juridiques, leurs échéances datées (calculées automatiquement à
partir de règles de droit et de la date de clôture des sociétés qu'elle suit), et son
agenda professionnel. L'outil calcule, rappelle et trace ; il ne décide pas à sa place.

DIRECTION RETENUE
[COLLER ICI la direction choisie au prompt 1, ou la charte ci-dessous]
Univers « papier & encre » : fond ivoire #F7F5F1, surfaces blanches, filets #E3DED5, encre
#141C2B / #3A465C / #6D7789, bleu de marque #24406B, accent laiton #A6802E.
Échelle d'urgence : en retard #B3261E · aujourd'hui #C2410C · ≤7 j #B4690E · ≤30 j #8A6D1F
· plus tard #4B5A72 · fait #17795E · sans objet #8E96A3.
Titres en serif, interface en sans-serif, dates et références en chiffres tabulaires.
Rayons discrets (6 à 14 px), ombres quasi absentes : les cartes se détachent par un filet.

RÈGLES DE CONCEPTION NON NÉGOCIABLES
1. Une ÉCHÉANCE est une date limite sans heure (calculée, avec sa base légale) ; un
   RENDEZ-VOUS est un créneau horaire (saisi, avec lieu et participants). Dans une vue
   d'agenda, les échéances vont dans un bandeau « toute la journée » en haut, les
   rendez-vous dans la grille horaire. Jamais l'inverse, jamais mélangés.
2. L'urgence est rendue par couleur + icône + libellé. Jamais la couleur seule
   (impression noir et blanc, daltonisme).
3. La criticité (bloquante / importante / confort) est un axe DIFFÉRENT de l'urgence :
   rends-la par un filet vertical à gauche de la ligne, pas par une couleur de fond.
4. Toute date calculée affiche son explication : « Clôture 31/12/2025 + 6 mois →
   30/06/2026 · art. L.223-26 C. com. ». C'est ce qui crée la confiance.
5. Ce qui est en retard est toujours en tête de l'écran, et ne se replie pas.
6. Densité : elle lit des listes de 40 lignes. Pas de grandes tuiles, pas d'espaces morts.
7. Vocabulaire juridique exact : « échéance », « fait générateur », « dépôt au greffe »,
   « signification », « assemblée générale ». Jamais « tâche », jamais « deadline ».

ÉCRANS À PRODUIRE (dans cet ordre, navigables par des onglets en haut de la page)
1. MA JOURNÉE — bandeau des échéances du jour + grille horaire 8 h→19 h avec les
   rendez-vous + bloc-notes du jour à droite. Ligne de l'heure courante.
2. TABLEAU DE BORD — dans cet ordre strict : En retard · Aujourd'hui · Cette semaine ·
   30 prochains jours · Alertes (dossiers sans échéance à venir).
3. VUE MOIS — calendrier mensuel à gauche (2/3), liste du mois groupée par jour à droite
   (1/3), filtres en haut, bouton « Imprimer le plan du mois ».
4. FICHE SOCIÉTÉ — Hélios Participations : identité (forme, SIREN, greffe, date de
   clôture mise en avant), frise horizontale de « l'année sociale » (clôture → arrêté des
   comptes → convocation → AG → dépôt au greffe → dividende) avec l'état de chaque jalon,
   dirigeants, dossiers rattachés.
5. FICHE DOSSIER — 2026-CTX-003 : en-tête (référence, intitulé, statut, criticité),
   onglets Échéances / Agenda / Documents / Journal / Contacts, chronologie verticale,
   colonne de droite avec la prochaine échéance et les contacts clés.
6. SEMAINE D'AGENDA — 5 colonnes, échéances en bandeau, rendez-vous en blocs horaires.

DONNÉES FICTIVES (utilise celles-ci partout, elles doivent être cohérentes d'un écran à
l'autre ; n'invente pas d'autres sociétés)
On est le lundi 15 juin 2026.

Sociétés suivies :
- Hélios Participations — SAS — SIREN 812 447 903 — greffe de Nantes — clôture 31/12 —
  commissaire aux comptes : oui — dépôt électronique
- Atelier Verrières — SARL — SIREN 534 209 118 — greffe de Rennes — clôture 30/09
- SCI Les Tilleuls — SCI — SIREN 792 003 641 — clôture 31/12
- Novaform — SA — SIREN 401 556 872 — greffe de Bordeaux — clôture 31/03

Dossiers :
- 2026-SOC-014 · Approbation des comptes 2025 · Hélios Participations · en cours · courant
- 2026-CTX-003 · Novaform c/ Delcourt — rupture de contrat · contentieux · en cours ·
  stratégique · juridiction : cour d'appel de Bordeaux · avocat : Me Aubert
- 2026-BAI-002 · Bail commercial 12 rue des Carmes · Atelier Verrières · en cours
- 2026-PI-005 · Marque « VERRIÈRES » · propriété intellectuelle · en cours
- 2026-CTR-011 · Contrat de prestation Kaliss · contrat · en attente de tiers

Échéances (avec leur base légale, à afficher) :
- 30/05/2026 — EN RETARD — Mise à jour du registre des bénéficiaires effectifs —
  Hélios Participations — bloquante — art. R.561-55 CMF
- 15/06/2026 — AUJOURD'HUI — Convocation des associés à l'AG d'approbation —
  Atelier Verrières — bloquante — art. L.223-26 C. com. — calcul : AG 30/06 − 15 jours
- 20/06/2026 — Conclusions d'appelant — Novaform c/ Delcourt — bloquante — art. 908 CPC —
  délai de procédure (afficher l'avertissement)
- 30/06/2026 — Tenue de l'AG d'approbation des comptes — Hélios Participations —
  bloquante — art. L.223-26 C. com. — calcul : clôture 31/12/2025 + 6 mois
- 10/07/2026 — Décision : donner congé ou non à l'échéance triennale — 2026-BAI-002 —
  importante
- 30/07/2026 — Dépôt des comptes annuels au greffe — Hélios Participations — bloquante —
  art. R.123-111 C. com. — calcul : AG 30/06 + 2 mois (dépôt électronique)
- 12/09/2026 — Renouvellement de la marque « VERRIÈRES » — bloquante — art. L.712-9 CPI
- 30/09/2026 — Mise en paiement du dividende — Hélios Participations — importante —
  art. L.232-13 C. com. — calcul : clôture + 9 mois
- 05/05/2026 — FAITE le 04/05/2026 — Déclaration modificative au RCS (changement de
  gérant) — Atelier Verrières — preuve jointe : récépissé du greffe

Rendez-vous du lundi 15/06/2026 :
- 09:30–10:00 Point hebdomadaire (interne)
- 11:00–12:00 Rendez-vous notaire — cession de parts — SCI Les Tilleuls — visioconférence
- 14:30–16:00 Assemblée générale d'approbation — Atelier Verrières — au siège —
  (rattachée à l'échéance du 30/06, avec un bouton « Marquer l'assemblée tenue »)
- 16:30–17:00 Appel Me Aubert — dossier 2026-CTX-003
Reste de la semaine : mardi 10:00 audience de mise en état (cour d'appel de Bordeaux),
mercredi 09:00–12:00 indisponible (formation), jeudi 15:00 signature d'acte chez le notaire.

FORMAT DE SORTIE
Un seul artefact HTML autonome et navigable :
- tout le CSS en ligne, aucune ressource externe (police, image, CDN) ;
- un peu de JavaScript uniquement pour basculer entre les onglets d'écrans ;
- responsive : sur mobile, les colonnes s'empilent et l'agenda reste lisible ;
- une feuille de style d'impression pour la vue mois et la semaine (fond blanc, encre
  noire, urgence rendue par icône et libellé) ;
- français partout, aucun lorem ipsum, aucune donnée réelle.

Termine par une note courte : les 3 arbitrages de conception que tu as dû faire, et ce
que tu recommandes de tester avec l'utilisatrice en priorité.
```

---

## Prompt 3 — Raffiner après son retour

> À utiliser autant de fois que nécessaire, en ne changeant qu'une chose à la fois.

```
Voici la maquette actuelle. Retour de l'utilisatrice après essai :
[COLLER SON RETOUR, ses mots exacts de préférence]

Modifie UNIQUEMENT ce qui est demandé ci-dessus. Ne redessine pas le reste, ne change ni
la palette, ni la typographie, ni la structure des écrans qui ne sont pas visés.

Rappelle-toi des règles non négociables : échéance ≠ rendez-vous (bandeau vs grille
horaire), urgence = couleur + icône + libellé, criticité = filet vertical, toute date
calculée affiche son explication et sa base légale, ce qui est en retard reste en tête.

Si sa demande entre en conflit avec l'une de ces règles, dis-le en une phrase, propose une
solution qui satisfait son besoin sans casser la règle, puis applique-la.
```

---

## Ce qu'on fait des maquettes ensuite

1. Les enregistrer dans `dies/docs/maquettes/` (un fichier HTML par itération, daté).
2. **Reporter dans `Design.md`** ce que la maquette a tranché : couleurs définitives, échelle
   typographique, hauteurs de lignes, comportement des composants. La maquette n'est pas la référence —
   `Design.md` l'est.
3. Donner la maquette **en pièce jointe du lot 0** à Claude Code : « les tokens CSS doivent produire
   exactement ce rendu ».
4. Ne pas chercher à convertir le HTML de la maquette en code Angular : c'est une maquette, pas une
   base de code. On en reprend les valeurs, pas la structure.

---

*PROMPT-MAQUETTES.md v1.0 — à jeter une fois le lot 1 livré et validé.*
