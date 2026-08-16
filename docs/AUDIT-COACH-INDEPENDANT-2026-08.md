# Audit — l'application est-elle prête pour tous les coachs ? (août 2026)

> **La question posée.** L'application est-elle prête pour un **coach indépendant**, paiement mis
> à part ? Un coach peut-il avoir à la fois des athlètes indépendants et des athlètes de club ?
> Et plus largement : quelle configuration de coaching le produit sait-il servir, et laquelle
> l'arrête ?

---

## Méthode, et ses limites

**Ce sur quoi il s'appuie.** Le modèle de domaine, les validateurs d'accès, les requêtes de
périmètre et les écrans des deux rôles. Chaque affirmation ci-dessous a été vérifiée dans le code :
quand j'écris « c'est supporté », j'ai lu l'entité, la requête et l'écran ; quand j'écris « c'est
bloqué », j'ai identifié la ligne qui bloque.

**Ce qui lui manque.** Je n'ai parlé à aucun coach indépendant. Ce document dit ce que la structure
permet et ce qu'elle empêche ; il ne dit pas ce que les coachs réclament. Les priorités de §6
reposent sur la logique du métier, pas sur des faits d'usage.

**Une correction au passage.** `PLAN-PRODUIT-2026-08` §1.3 annonçait qu'un coach qui s'inscrit
n'a « pas une zone d'intensité ». C'était faux : les zones sont provisionnées paresseusement à la
première lecture du club (`TrainingZoneSeedService`, appelé par `TrainingZoneService#list`). Le
moteur physiologique n'a jamais été muet. Ce qui manquait — et qui est désormais posé — c'étaient
les **catégories, les séances types et les éducatifs**.

---

## 0. La réponse courte

**Oui pour le coach indépendant, à trois réserves près — et oui, franchement, pour le mélange
privé / club.**

| Question | Réponse | Où ça coince |
|---|---|---|
| Un coach indépendant peut-il travailler normalement ? | **Oui** | Le vocabulaire lui parle de « club » partout (§2) |
| Peut-il avoir des athlètes privés **et** des athlètes de club ? | **Oui, nativement** | Rien. C'est modélisé, filtrable et testé (§1) |
| Un athlète peut-il n'exister que pour lui, invisible des autres coachs ? | **Oui** | Rien (§1) |
| Peut-il suivre un athlète qui n'a pas de compte ? | **Oui** | Rien (§3) |
| Peut-il intervenir dans **plusieurs clubs** ? | **Le modèle, oui. L'interface, non.** | Aucun sélecteur de club (§4) — c'est le vrai trou |
| Peut-il facturer ? | Non | Hors périmètre de cet audit, cf. `PLAN-PRODUIT` §1.5 |

---

## 1. Athlètes privés et athlètes de club : c'est déjà là

C'était la question principale, et la réponse est meilleure que ce à quoi je m'attendais.

**Le modèle.** `CoachAthleteRelation` porte un club **nullable**, et c'est cette nullité qui fait
toute la différence :

- `club == null` → athlète **privé** : visible du seul coach référent, jamais des autres coachs du
  club, quel que soit leur rôle ;
- `club != null` → athlète **club** : visible selon `AthleteCoachPermission` et le rôle club.

**L'interface.** La case existe à la création d'un athlète — *« Athlète privé (coaching hors club —
invisible des autres coachs du club) »* — et le tableau de bord comme la file de retours proposent
quatre périmètres : *Tout le club / Mes athlètes / Privés / Club*.

**L'étanchéité est testée.** `AthleteAccessControlTest#privateAthleteIsReferentOnly` vérifie qu'un
coach assistant reçoit un **403** sur un athlète privé — pas une liste filtrée, un refus — pendant
que le coach référent y accède normalement.

**Donc le cas mixte fonctionne** : un coach salarié d'un club qui prend trois athlètes en
particulier les crée en privé, les voit dans son périmètre « Privés », et personne d'autre au club
n'y a accès. C'est exactement la configuration décrite dans la question.

> ⚠️ **Une nuance de vocabulaire, pas de fonctionnement.** « Privé » ne veut pas dire « sans
> club » : `Athlete.club` est **non nullable**, donc tout athlète appartient techniquement au club
> du coach. « Privé » signifie « non partagé avec les autres coachs de ce club ». Pour un coach
> indépendant dont le club n'a qu'un membre, la distinction est sans objet — et c'est très bien.

---

## 2. Le coach indépendant travaille, mais on lui parle un autre métier

Rien ne l'empêche de travailler. Tout lui rappelle qu'il n'était pas la cible principale.

**À l'inscription**, « Nom du club » est **obligatoire**, avec « Running Club Lyon » en exemple.
Un coach indépendant doit donc inventer un club, ou y mettre son propre nom — et ce choix le suit
partout ensuite, puisque c'est ce nom qui s'affiche.

**Dans la navigation**, une entrée « Club » ouvre un écran de gestion de membres, qui pour lui ne
contiendra jamais que lui-même.

**Dans les écrans**, le périmètre par défaut s'appelle « Tout le club », les filtres opposent
« Privés » et « Club », le bilan hebdomadaire que je viens d'ajouter s'intitule « Ta semaine de
club ». Pour un coach de club, ces mots sont justes. Pour un indépendant, ils décrivent une
organisation qui n'existe pas.

**Proposition — la moins chère de tout ce document.** Une question à l'inscription : *« Vous
coachez : en club / en indépendant »*.
- En indépendant : le champ devient « Votre nom d'activité » (pré-rempli avec le nom du coach,
  et **facultatif**), l'entrée « Club » disparaît de la navigation, et les libellés de périmètre se
  réduisent à ce qui a un sens pour lui.
- Aucun changement de modèle : le club implicite continue d'exister, il cesse simplement d'être
  affiché comme tel. C'est un drapeau sur `Club` et une poignée de libellés.

*Effort : S. C'est de la première impression, pas de la fonctionnalité — et la première impression
est ce qui décide un coach indépendant qui teste trois produits le même soir.*

---

## 3. Ce qui marche déjà pour un indépendant, et qu'il faut savoir

Vérifié, et qui répond aux objections habituelles :

- **L'athlète n'a pas besoin de compte.** `Athlete.email` est facultatif, l'invitation est un geste
  séparé (`POST /athletes/{id}/invitation`). Un coach peut donc gérer un athlète qui ne veut pas de
  l'application : il saisit lui-même les retours. C'est le cas d'usage « coach à l'ancienne », et
  il passe.
- **Le moteur physiologique tourne dès le premier athlète.** Zones seedées d'office, allures
  dérivées des records, prescription en fourchettes. C'est la valeur du produit, et elle n'attend
  ni un club, ni un effectif.
- **Depuis ce lot de travaux, la bibliothèque n'est plus vide** : six catégories, six éducatifs et
  onze séances types prescrites par zone sont posés à la création du compte. Tout est supprimable.
- **Les permissions fines existent** (`AthleteCoachPermission` : READ / WRITE, avec expiration).
  Un indépendant qui prend un remplaçant pendant ses vacances a le mécanisme sous la main.
- **La PWA coach existe** (vagues 1 à 3), avec « Ma journée », les notifications et les actions
  rapides. Un indépendant travaille souvent depuis un stade, pas depuis un bureau.

---

## 4. Le vrai trou : un coach ne peut travailler que dans un seul club

C'est le seul point où le produit **empêche** une configuration légitime, et il mérite d'être vu
pour ce qu'il est : **le modèle est prêt, l'interface ne l'utilise pas.**

**Ce qui existe côté serveur.** `User.additionalClubs` (many-to-many), `ClubMember` (adhésion par
club, avec rôle), et `ClubAccessValidator#hasAccess` qui accepte explicitement « le club principal
**et** les clubs additionnels ». `ClubMembershipService` remplit `additionalClubs` quand on invite
un coach déjà titulaire d'un autre club. Toute l'API est scopée `/clubs/{clubId}/…` et
fonctionnerait avec n'importe lequel de ses clubs.

**Ce qui manque côté client.** Le front lit `currentUser().clubId` — **un seul identifiant, celui
du club principal** — et le passe à tous les appels. Il n'existe :
- **aucun endpoint** qui liste les clubs d'un coach (rien en `/me/clubs`) ;
- **aucun sélecteur** de club dans l'interface.

**Conséquence concrète.** Un coach salarié du club A, invité comme entraîneur au club B, reçoit son
invitation, la ligne `ClubMember` est créée, le validateur d'accès l'autorise — et il ne verra
jamais le club B. Sa seule issue est un second compte avec une autre adresse e-mail.

**Proposition.** Trois pièces, aucune ne touche au modèle :
1. `GET /me/clubs` — club principal + clubs additionnels, avec le rôle dans chacun ;
2. un **sélecteur de club** dans l'en-tête, dès qu'il y en a plus d'un (invisible sinon — un
   indépendant ne doit jamais le voir) ;
3. le club actif en signal partagé, à la place du `currentUser().clubId` codé en dur.

*Effort : M. Et c'est le seul point de ce document qui débloque une configuration aujourd'hui
impossible, plutôt que d'en rendre une plus agréable.*

### 4 bis. Une incohérence trouvée en chemin

Deux requêtes traitent différemment les athlètes multi-clubs :

- `AthleteRepository#search` et `#findByIdAndClubMembership` acceptent le club principal **ou** un
  club additionnel (`left join a.additionalClubs`) — le commentaire du code dit d'ailleurs
  explicitement qu'ils évitent « les faux 404 sur les athlètes multi-clubs » ;
- `#findByClubIdOrderByLastNameAsc` ne regarde que le club principal. Or c'est **elle** que
  `CoachDashboardService#athletesInScope` utilise pour le périmètre « Tout le club ».

Un athlète rattaché au club B en club additionnel apparaît donc dans la **liste** des athlètes de
B, mais pas dans son **tableau de bord**, ni dans ses alertes, ni dans son bilan hebdomadaire. Le
défaut est aujourd'hui invisible parce que rien ne permet de créer cette situation depuis
l'interface (§4) — mais il se réveillerait le jour même où le sélecteur de club serait livré.

*À corriger **avec** le sélecteur, pas après.*

---

## 5. Les autres cas de figure, passés en revue

| Configuration | Supporté ? | Détail |
|---|---|---|
| Indépendant, quelques athlètes, tous privés | **Oui** | Le club implicite ne le gêne que par son vocabulaire (§2) |
| Indépendant qui grandit et recrute un second coach | **Oui** | `ClubMember` + rôles + permissions par athlète |
| Coach de club, salarié, tous athlètes partagés | **Oui** | C'est la configuration de référence du produit |
| Coach de club **avec** des athlètes en propre | **Oui** | Case « privé » à la création (§1) |
| Coach intervenant dans deux clubs | **Non** | Modèle prêt, interface absente (§4) |
| Athlète suivi par deux coachs du même club | **Oui** | `AthleteCoachPermission`, référent + assistants |
| Athlète d'un club **et** d'un coach externe | **Non** | Demande le multi-club (§4) et une API de rattachement additionnel, qui n'existe pas |
| Athlète sans compte ni e-mail | **Oui** | Saisie par le coach (§3) |
| Athlète dans plusieurs groupes d'entraînement | **Non** | `Athlete.group` est mono-valué — déjà noté `PLAN-PRODUIT` §1.2 |
| Annonce à tout un groupe | **Non** | `Message.athlete` obligatoire — déjà noté `PLAN-PRODUIT` §1.2 |
| Coach qui facture ses athlètes | **Non** | Hors périmètre, cf. `PLAN-PRODUIT` §1.5 |

---

## 6. Ce que je ferais, dans cet ordre

1. **Le sélecteur de club** (§4) + la correction de `athletesInScope` (§4 bis). *Effort M.* C'est
   le seul chantier qui débloque quelque chose d'impossible aujourd'hui, et le seul dont l'absence
   oblige un coach à ouvrir un second compte.
2. **Le mode indépendant** (§2). *Effort S.* Une question à l'inscription et des libellés. Meilleur
   rapport effet/coût du document : c'est la première impression de la moitié de la cible annoncée.
3. **Le rattachement d'un athlète à un club additionnel** — l'entité le permet, aucune API ne
   l'expose. *Effort S/M.* À faire après le sélecteur, dont il dépend pour être utile.

Rien de tout cela ne demande une migration destructive : le modèle a été conçu multi-club et
multi-coach depuis le début. **Ce qui manque, c'est l'interface qui s'en sert.**

---

## 7. Les questions à poser à de vrais coachs avant de construire

Je n'ai pas ces réponses, et elles changent l'ordre du §6 :

1. Un coach indépendant se reconnaît-il dans le mot « club » s'il y met le nom de son activité, ou
   est-ce un motif d'abandon dès l'inscription ?
2. Le cas « deux clubs » est-il courant, ou marginal au point d'attendre ? (Il est coûteux à
   contourner — un second compte — mais peut-être rare.)
3. Un coach de club prend-il réellement des athlètes en privé, ou sépare-t-il ses deux activités
   dans deux outils ?
4. Un athlète suivi par son club **et** par un coach externe : configuration réelle, ou théorique ?
