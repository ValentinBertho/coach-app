# Plan produit — ce qui manque pour que l'usage soit agréable (août 2026)

> **La question posée.** L'application fait déjà beaucoup : physiologie calculée, prescription en
> fourchettes, calendrier, force, messagerie, alertes, PWA des deux côtés. Qu'est-ce qui manque
> encore, non pas techniquement, mais **pour un coach et un athlète qui s'en servent tous les
> jours** ?
>
> Ce document ne parle pas de dette ni d'architecture. Il parle de gestes de métier : ce qu'un
> coach de club fait le mardi soir, ce qu'un athlète regarde en se levant, ce qu'un coach
> indépendant doit facturer à la fin du mois.

---

## Méthode, et ses limites

**Ce sur quoi il s'appuie.** Le modèle de domaine (44 entités), les 27 types de notification, les
écrans des deux rôles, et les documents existants : le cahier des charges (périmètre promis, avec
sa priorisation MoSCoW), `PLAN-EVOLUTION-2026-08` (construit sur le tableau public de demandes de
Nolio), `AUDIT-FONCTIONNEL-2026-08` (les dettes métier) et l'analyse concurrentielle. Chaque manque
énoncé ici a été **vérifié dans le code**, pas déduit d'un document.

**Ce qui lui manque, et qu'aucune lecture de code ne remplacera.** Je n'ai parlé à aucun coach, vu
aucune session d'usage réelle, et ne dispose d'aucune métrique d'utilisation. Les priorités
proposées reposent donc sur la logique du métier et sur ce que la structure des données rend
possible ou impossible — pas sur des faits d'usage. **§6 liste les questions à poser avant de
construire** ce qui coûte cher.

**Ce que ce document ne refait pas.** `PLAN-EVOLUTION-2026-08` couvre déjà ce que le marché
réclame (physiologie exposée, dossier de blessure, HRV, comparaison de séances, arbitrage
multi-sport). Rien n'en est répété ici. Ce plan-ci part de l'autre bout : non pas de ce que les
utilisateurs de Nolio votent, mais de ce que **la structure actuelle empêche** ou rend pénible.

**Deux bonnes nouvelles, vérifiées.** Deux points ouverts de l'audit fonctionnel de juillet sont
aujourd'hui corrigés : l'athlète peut déclarer une séance non faite avec son motif
(`Workout.missedReason`), et le check-in du matin remonte bien au coach
(`AthletePhysioController#checkIns`). Restent ouverts, et repris ci-dessous : le groupe unique
(§2.2) et les bibliothèques vides à l'inscription (§2.3).

---

## État de livraison (août 2026)

Trois points de ce plan sont **livrés**. Le document reste tel qu'il a été écrit — c'est son
intérêt : il dit ce qu'on croyait avant de construire. Ce paragraphe dit ce qu'on a trouvé en
construisant.

| Point | État | Ce qu'on a appris en le faisant |
|---|---|---|
| §1.3 — le coach arrive dans une maison vide | ✅ Livré | **Le diagnostic était partiellement faux.** Les zones d'intensité étaient déjà provisionnées paresseusement (`TrainingZoneSeedService`, appelé à la première lecture) : le moteur physiologique n'a jamais été muet. Ce qui manquait vraiment, c'étaient les catégories, les séances types et les éducatifs — désormais posés à la création du club (6 catégories, 6 éducatifs, 11 séances prescrites par zone), après le commit de l'inscription pour qu'un jeu de départ en panne ne puisse pas empêcher d'ouvrir un compte |
| §2.1 — le « vu 👏 » du coach | ✅ Livré | Le geste a demandé une **seconde date**, distincte de « traité ». « Traité » est une date que le coach pose pour vider sa file et que l'athlète ne voit pas ; les confondre aurait notifié l'athlète chaque fois qu'un coach fait le ménage. Le « vu » est devenu l'action principale de la file, « traité » passant en retrait : quand deux gestes coûtent le même clic, celui par défaut doit être celui qui referme la boucle |
| §2.2 — le bilan de la semaine | ✅ Livré | Deux rendez-vous (dimanche 18 h pour l'athlète, lundi 7 h 30 pour le coach — une demi-heure après le digest, deux notifications simultanées se lisant comme une seule). Trois décisions de calcul faisaient la différence entre un bilan juste et un bilan vexant : une séance écourtée compte comme réalisée, les kilomètres viennent des activités et jamais des cibles, et une semaine vide ne produit aucun message — « 0 séance sur 0 » n'est pas un bilan, c'est un reproche |

**Reste ouvert** : §1.1 (heure et lieu), §1.2 (le club comme collectif), §1.4 (plan depuis
l'objectif), §1.5 (facturation), §1.6 (poids et FC de repos), et les agréments §2.3 à §2.6.

Un audit complémentaire — [`AUDIT-COACH-INDEPENDANT-2026-08`](./AUDIT-COACH-INDEPENDANT-2026-08.md)
— reprend la question sous l'angle des configurations de coaching : il conclut que le mélange
privé / club est déjà supporté nativement, et que le seul blocage réel est le coach membre de
plusieurs clubs.

---

## 0. Le fil directeur

Trois manques structurels expliquent la plupart des irritants du quotidien. Ils ne se voient pas
écran par écran : ils se voient dans le modèle.

1. **Une séance n'a ni heure ni lieu.** `Workout` porte une `scheduledDate`, et rien d'autre.
2. **Le club n'existe pas comme collectif.** Un athlète n'appartient qu'à un groupe, la messagerie
   est strictement de un à un, et rien ne dit qui est venu.
3. **Un coach qui s'inscrit arrive dans une maison vide.** La création de compte crée un club et
   un utilisateur — pas une zone d'intensité, pas une catégorie, pas une séance, pas un éducatif.

Tout le reste du document en découle, ou s'y ajoute comme agrément.

---

## 1. Les manques structurels

### 1.1 La séance n'a pas d'heure — et tout le reste s'en ressent

`Workout` : `scheduledDate` (un jour), `title`, `type`, cibles, structure. **Aucune heure, aucun
lieu.** Les conséquences se lisent partout dans le produit :

- Le rappel de séance de la veille ne peut pas dire *quand*. Il dit « demain », et l'athlète
  regarde son calendrier pour deviner.
- Le rappel de débriefing s'ancre sur une **heure déclarée dans le profil de l'athlète**
  (`usualSessionTime`, « mon heure d'entraînement habituelle »). C'est un contournement élégant,
  mais c'est un contournement : il existe parce que la séance elle-même ne sait pas à quelle heure
  elle a lieu.
- Un club ne peut pas écrire « mardi 18 h 30, stade Colette-Besson ». C'est pourtant la phrase la
  plus prononcée d'un club de course à pied.

**Proposition.** Ajouter à la séance une **heure facultative** et un **lieu libre** (texte court,
éventuellement des coordonnées). Facultatifs : un coach privé continue de prescrire « jeudi »,
sans horaire, et rien ne change pour lui. Mais dès qu'une heure est posée, le rappel la dit, le
débrief s'y ancre au lieu de la moyenne du profil, et la journée du coach s'ordonne
chronologiquement au lieu d'être une liste alphabétique.

*Effort : S côté modèle (deux colonnes nullables), M avec les écrans et les rappels.*

### 1.2 Le club n'est pas un collectif

Trois absences, qui se tiennent :

- **Un athlète n'appartient qu'à un seul groupe** (`Athlete.group`, mono-valué). Or un club
  fonctionne en recoupements : le groupe du mardi, le groupe compétition, le stage d'été. C'était
  déjà le constat G8 de l'audit fonctionnel ; il est toujours vrai.
- **Aucune annonce de groupe.** La messagerie est *par athlète* (`Message.athlete` est obligatoire,
  les conversations se listent athlète par athlète). Prévenir vingt personnes que la séance est
  annulée pour cause d'orage demande vingt messages. Aucun coach ne le fera : il ira sur WhatsApp,
  et le produit aura perdu la conversation.
- **Aucune présence.** Rien n'enregistre qui est venu. Pour un club, c'est le registre de base —
  celui qu'on tient pour la subvention, pour l'assurance, et pour savoir qui décroche.

**Proposition.** Trois briques, dans cet ordre :
1. **Appartenance multiple** à des groupes (table de liaison, la donnée existante devient le
   premier lien — migration additive).
2. **Annonce de groupe** : un message écrit une fois, distribué dans le fil de chaque athlète du
   groupe (donc lisible et archivé là où il faut), marqué comme annonce, avec le compte de ceux
   qui l'ont lue.
3. **Séance collective avec présence** : la séance de groupe existe déjà comme *duplication* chez
   chaque athlète ; elle gagne un créneau commun (§1.1) et une liste d'émargement, que le coach
   coche au stade depuis son téléphone — ce que la vague 1 de la PWA rend enfin possible.

*Effort : M (appartenance multiple), S (annonce), M (présence).*

### 1.3 Le coach qui s'inscrit arrive dans une maison vide

`AuthService#register` crée un club, un utilisateur, une adhésion. **Rien d'autre.** Pas de jeu de
zones d'intensité, pas de catégories de séance, pas une séance de bibliothèque, pas un éducatif —
tout cela est scopé par club (`ZoneSet.club`, `SessionCategory.club`, `WorkoutTemplate`), et le
jeu de démonstration ne tourne qu'en profil `dev`.

Un coach qui découvre le produit ouvre donc : un calendrier vide, une bibliothèque vide, un
tableau de bord qui lui propose de créer son premier athlète, et un moteur physiologique — la vraie
valeur du produit — qui n'a rien à calculer parce qu'aucune zone n'est définie. **C'est la première
impression, et elle est muette.**

**Proposition.** À la création du club, poser un **jeu de départ** : un modèle de zones cohérent
avec les moteurs (domaines 1/2/3, seuils), les catégories de séance usuelles, une dizaine de
séances types (footing, seuil, VMA, sortie longue, fractionné court, côtes…), quelques éducatifs.
Tout est modifiable et supprimable — c'est un point de départ, pas un carcan. Idéalement proposé,
pas imposé : une étape d'accueil « pars d'une base toute faite / je construis la mienne ».

*Effort : M. Et probablement le meilleur rapport valeur/effort de tout ce document.*

### 1.4 L'objectif ne construit pas le plan

`RaceObjective` connaît la date, la distance et le chrono visé. `TrainingPlan` connaît une durée
en semaines et une liste d'items. **Les deux ne se parlent pas.** Personne ne rétro-planifie.

Or c'est le geste fondateur du coaching : *« Marathon de Paris le 12 avril, on est le 5 janvier,
donc 14 semaines : 3 de reprise, 6 de spécifique, 3 de volume, 2 d'affûtage. »* Aujourd'hui, ce
raisonnement se fait dans la tête du coach et s'exécute séance par séance dans le calendrier.

**Proposition.** Un **assistant de plan depuis l'objectif** : on choisit une course cible, on
obtient une trame de semaines (phases, volume progressif, affûtage calé sur la date), qu'on ajuste
avant de la poser dans le calendrier. Les briques existent toutes — mésocycles, modèles de séance,
duplication de semaine ; il manque le fil qui les relie à une date de course.

⚠️ **À ne pas confondre avec un générateur automatique de plans**, qui prétendrait remplacer le
coach. Ici, la machine pose la structure, le coach garde chaque séance.

*Effort : L. C'est le plus gros morceau du document, et celui qui demande le plus de validation
métier auprès de vrais coachs.*

### 1.5 Un coach indépendant ne peut pas se faire payer

Le cahier des charges prévoit « Facturation & abonnements » en priorité *Could* (§3.8). Rien n'est
implémenté : aucune entité, et l'écran Paramètres affiche « Bêta — gratuite » en dur.

Pour un club, ce n'est pas urgent (les cotisations passent ailleurs). Pour le **coach indépendant**
— la moitié de la cible annoncée —, c'est le nerf du métier : forfaits, échéances, relances,
attestations. Aujourd'hui, il tient ça dans un tableur à côté.

**Proposition.** Ne pas commencer par le paiement en ligne, qui est un chantier réglementaire.
Commencer par ce qui coûte peu et sert tout de suite : **le forfait de coaching** (nom, montant,
périodicité) attaché à un athlète, l'état de paiement à cocher, et l'échéancier visible dans la
journée du coach. Le paiement en ligne viendra si les coachs le réclament.

*Effort : M pour le suivi, L pour l'encaissement (à décider séparément).*

### 1.6 Le bien-être s'arrête à trois curseurs

`DailyCheckIn` : sommeil, fatigue, douleur. Le cahier des charges (§3.5) annonçait aussi
courbatures, humeur, FC de repos, HRV et **poids**. Le poids existe bien sur la fiche athlète
(`Athlete.weightKg`), mais comme **valeur figée** : aucune histoire, donc aucune courbe, alors que
le produit sait déjà tracer des courbes pour tout le reste.

**Proposition.** Deux mesures longitudinales, saisies au check-in du matin quand l'athlète le
souhaite : **poids** et **FC de repos**. Elles ne coûtent presque rien (une table, deux champs, une
courbe dans « Progrès ») et elles ferment une boucle que l'athlète attend : *voir* que quelque
chose bouge.

⚠️ Donnée de santé : consentement explicite, comme la douleur et le lactate. Et **jamais d'alerte
automatique sur le poids** — c'est un terrain où un produit sportif peut faire du mal.

⚠️ La HRV est déjà instruite dans `PLAN-EVOLUTION` §2.2 : à traiter là-bas, pas ici.

*Effort : S/M.*

---

## 2. Les gestes qui rendraient l'usage agréable

Petits, peu coûteux, et directement ressentis. C'est la partie « plaisir d'usage » de la question
posée.

### 2.1 Le « vu » du coach

Aujourd'hui, la seule réponse possible à un retour d'athlète est un commentaire écrit. Traiter
quinze retours en écrivant quinze phrases, personne ne le fait — donc l'athlète renseigne son
ressenti et **n'entend jamais rien en retour**. Le produit a pourtant tout construit pour lui
donner envie de le remplir : célébration, série, accusé de réception… côté athlète seulement.

**Proposition.** Un **« vu 👏 » en un tap** depuis la file de retours, qui remonte à l'athlète comme
une petite reconnaissance (« ton coach a vu ta séance »). Ce n'est pas un commentaire, ça n'en
prend pas la place : c'est le minimum syndical de la boucle, et il manque.

*Effort : S. Meilleur rapport effet/coût du document, avec §1.3.*

### 2.2 Le bilan de la semaine

Vingt-sept types de notification, et **aucun bilan**. Tout est événementiel : une séance, un
retour, une alerte. Rien ne dit « voilà ce que tu as fait ».

**Proposition.** Le dimanche soir pour l'athlète : *« Ta semaine : 42 km, 3 séances sur 4, une
sensation moyenne en hausse. »* Le lundi matin pour le coach : *« Ton club : 87 % de séances
réalisées, 2 athlètes en vigilance, 3 courses dans les 15 jours. »* L'infrastructure existe déjà —
le digest d'alertes de 7 h tourne tous les jours, il suffit d'un second rendez-vous hebdomadaire.

*Effort : S/M. Fort effet de rétention, et c'est le genre de message qu'on montre à ses amis.*

### 2.3 La météo de la séance

Rien dans le produit ne parle du temps qu'il fait. C'est pourtant la première variable d'ajustement
d'un entraînement en extérieur — et une info que l'athlète cherche ailleurs juste avant de partir.

**Proposition.** Sur « Aujourd'hui » côté athlète et sur la journée du coach : température, vent et
précipitations **à l'heure de la séance** (d'où l'intérêt de §1.1). Une API publique sans clé
(Open-Meteo) suffit ; en l'absence de lieu, la position du club fait l'affaire.

*Effort : S. Agrément pur, effet immédiat.*

### 2.4 Le parcours proposé

La carte n'existe que sur l'activité **réalisée** (Leaflet, tracé importé). Un coach ne peut pas
dire « voilà la boucle de 8 km » autrement qu'en l'écrivant.

**Proposition.** Attacher un parcours à une séance : au minimum un lien ou un fichier GPX déposé,
au mieux une trace affichée dans la prescription. Le décodage GPX existe déjà côté import.

*Effort : M.*

### 2.5 Les notes privées du coach

`Athlete.medicalNotes` existe, mais rien pour la note de travail : *« a changé de boulot, moins de
temps le midi »*, *« ne réagit pas bien aux séries longues »*. En club multi-coach, ces notes sont
ce qui se perd quand un coach part.

**Proposition.** Un bloc de notes privées sur la fiche, invisible de l'athlète, horodaté, avec
l'auteur. Attention au cadre : jamais de donnée de santé hors du consentement existant.

*Effort : S.*

### 2.6 Les disponibilités récurrentes de l'athlète

Les indisponibilités existent (blessure, maladie, vacances) mais seulement **ponctuelles**. Le
besoin quotidien est l'inverse : *« je peux courir lundi, mercredi, samedi »*. Le coach le sait,
le produit non — donc il pose des séances les mauvais jours, et l'athlète les déplace.

**Proposition.** Des jours d'entraînement habituels sur la fiche athlète, affichés discrètement au
moment de planifier. Aucune contrainte automatique : un simple repère visuel.

*Effort : S/M.*

---

## 3. Le plan, en quatre vagues

L'ordre suit une règle : **d'abord ce qui débloque un métier, ensuite ce qui fidélise, enfin ce qui
structure.**

| Vague | Contenu | Pourquoi là | Effort |
|---|---|---|---|
| **A — La première impression** | §1.3 jeu de départ à l'inscription · §2.1 le « vu » du coach · §2.2 bilan hebdomadaire | Ce sont les trois choses qui décident si un coach reste après dix minutes, et si un athlète continue de remplir ses retours après trois semaines. Aucune ne touche au modèle. | ~5–7 j |
| **B — Le club comme collectif** | §1.1 heure et lieu · §1.2 appartenance multiple, annonce de groupe, présence | C'est ce qui sépare « un outil de coach » d'« un outil de club ». Sans ça, la conversation d'un club continue de vivre sur WhatsApp. | ~10–12 j |
| **C — Le quotidien agréable** | §2.3 météo · §2.6 disponibilités · §2.5 notes privées · §1.6 poids et FC de repos | Petits gestes, effet cumulé. Chacun est livrable seul, dans n'importe quel ordre. | ~7–9 j |
| **D — Les chantiers de fond** | §1.4 plan depuis l'objectif · §1.5 forfaits et paiements · §2.4 parcours | Gros morceaux, à n'ouvrir qu'après validation auprès de vrais coachs (§6). | ~20 j+ |

**Si une seule vague devait être faite : A.** Elle ne demande aucune migration risquée, elle se
livre en une semaine, et elle agit exactement là où un produit se gagne ou se perd — les premières
minutes, et la boucle de récompense.

---

## 4. Ce que je n'ai pas retenu, et pourquoi

- **Nutrition et hydratation.** Hors du périmètre annoncé (cahier des charges §2.2), et terrain
  réglementaire glissant dès qu'on conseille.
- **Réseau social entre athlètes** (fil d'actualité, kudos entre pairs). Strava le fait déjà mieux,
  et cela déplacerait le produit hors de sa promesse : la relation coach ↔ athlète.
- **Marketplace de plans à vendre.** Séduisant, mais c'est un autre métier (édition, paiement,
  modération), et il faudrait déjà que §1.4 existe.
- **Multi-sport.** Instruit dans `PLAN-EVOLUTION` §7 ; c'est un arbitrage stratégique, pas un
  manque fonctionnel.
- **Intégration Garmin/COROS native.** Les demandes d'accès sont déposées, l'import FIT en tient
  lieu ; rien à décider tant que les réponses ne sont pas là.

---

## 5. Les questions à poser avant de construire

Trois propositions coûtent cher et reposent sur des hypothèses que je ne peux pas vérifier depuis
le code. À instruire auprès de trois ou quatre coachs réels, avant d'écrire une ligne :

1. **Combien de vos athlètes s'entraînent en groupe, à heure fixe ?** Si la réponse est « aucun »,
   toute la vague B tombe au profit de la C.
2. **Facturez-vous vos athlètes, et avec quoi aujourd'hui ?** Si c'est déjà un outil comptable qui
   leur convient, §1.5 n'a pas lieu d'être.
3. **Comment construisez-vous un plan pour une course, concrètement ?** §1.4 ne vaut que si la
   trame proposée ressemble à ce qu'ils font déjà — sinon ils la referont à la main, et on aura
   construit un obstacle.

Et une mesure à mettre en place, quelle que soit la vague retenue : **le taux de retours renseignés
par les athlètes**, semaine après semaine. C'est le seul indicateur qui dise si la boucle
coach ↔ athlète tourne — et c'est précisément ce que §2.1 et §2.2 cherchent à améliorer.

---

*Plan produit du 13 août 2026. Les constats sont vérifiés dans le code à cette date ; les
priorités sont une proposition, à arbitrer avec le métier.*
