# Audit — ouvrir la plateforme aux coachs indépendants et aux athlètes (septembre 2026)

> **La question posée.** Que faut-il changer, et dans quel ordre, pour que l'application cesse
> d'être un outil « club → coach → athlète » et devienne un **hub** où des coachs indépendants et
> des athlètes se trouvent, se choisissent et travaillent ensemble ?
>
> Un athlète crée son compte seul, parcourt un annuaire de coachs, demande à être coaché, le coach
> accepte — et la relation bascule dans l'outil existant sans rupture.

---

## 0. La réponse courte

**Le produit sait faire tout ce qui vient *après* la poignée de main, et rien de ce qui vient
avant.** Prescription, physiologie, calendrier, messagerie, permissions fines : tout cela existe et
fonctionne dès qu'une relation coach↔athlète est posée. Ce qui manque, c'est **la porte d'entrée
côté athlète, la vitrine côté coach, et le geste qui les relie**.

| Brique du hub | État | Où ça coince |
|---|---|---|
| Un athlète crée son compte seul | 🔴 **Impossible** | Aucun parcours : un compte `ATHLETE` ne naît que d'une invitation de coach (`AuthService:302`) |
| Un coach s'inscrit sans club | 🟡 Possible, mais mal dit | `clubName` est `@NotBlank` (`RegisterRequest:21`) ; le club implicite existe déjà |
| Profil coach public (spécialités, tarifs, diplômes) | 🔴 **N'existe pas** | Aucune entité, aucune colonne |
| Annuaire / recherche de coachs | 🔴 **N'existe pas** | Aucun endpoint, aucun écran |
| Demande de coaching (athlète → coach) | 🔴 **N'existe pas** | `AthleteProposal` ne sert **pas** à ça (cf. §2.4) |
| Acceptation ⇒ relation de travail | 🟡 La moitié est là | `CoachAthleteRelation` existe, mais sans statut ni cycle de vie |
| Travailler ensemble une fois liés | ✅ **Complet** | Rien à faire : c'est le produit actuel |
| Échanger avant la relation | 🔴 Bloqué | `Conversation.club` et `Message.club` sont `NOT NULL` |
| Mettre fin à une relation | 🔴 **Pire que manquant** | La désactiver **élève** l'accès du coach au lieu de le couper (§2.5) |
| Facturer / encaisser | 🔴 Absent | Aucune entité ; à ne pas mettre au lancement (§3.7) |
| Avis et réputation | 🔴 Absent | À ne pas mettre au lancement (§3.8) |

**Les deux verrous structurels** sont `Athlete.club NOT NULL` (`Athlete.java:41-44`) et le **rôle
unique** par utilisateur (`User.java:42-44`). Aucun des deux n'exige la migration destructive qu'on
pourrait craindre : §3.1 et §3.2 proposent de les contourner proprement plutôt que de les casser.

**Un défaut de sécurité, trouvé en chemin et indépendant du hub, est à corriger avant lui** : §2.5.

---

## 1. Méthode et limites

**Ce sur quoi cet audit s'appuie.** Les 53 entités, les 96 changesets Liquibase, les 56 contrôleurs,
les validateurs d'accès et les 87 composants Angular du dépôt, à l'état de la branche courante.
Chaque affirmation ci-dessous cite le fichier et la ligne qui la fonde. Quand j'écris « c'est
bloqué », j'ai lu la ligne qui bloque.

**Ce qu'il ne sait pas.**
- **Je n'ai parlé à aucun coach ni à aucun athlète.** Ce document dit ce que la structure permet et
  ce qu'elle coûte ; il ne dit pas ce que le marché réclame. Les arbitrages du §3 sont argumentés
  par le métier et par le coût, pas par des faits d'usage.
- **Je n'ai rien exécuté.** Pas de build, pas de tests, pas de mesure de performance. Les
  estimations d'effort sont des ordres de grandeur relatifs (S/M/L), pas des devis.
- **Le volet juridique est signalé, pas tranché.** Statut d'intermédiaire, avis vérifiés, mineurs :
  §7 dit ce qui est en jeu ; un avocat dit ce qu'il faut écrire.

**Documents dont il dépend.** [`AUDIT-COACH-INDEPENDANT-2026-08`](./AUDIT-COACH-INDEPENDANT-2026-08.md)
(configurations de coaching), [`PLAN-PRODUIT-2026-08`](./PLAN-PRODUIT-2026-08.md) §1.5 (facturation),
[`PLAN-CONFORMITE-BETA-2026-08`](./PLAN-CONFORMITE-BETA-2026-08.md) (RGPD),
[`AUDIT-ADMIN-2026-08`](./AUDIT-ADMIN-2026-08.md) (back-office).

**Deux corrections aux documents existants**, relevées en vérifiant :
- `PLAN-PRODUIT` §1.2 annonçait que `Message.athlete` était obligatoire. Ce n'est plus vrai depuis
  le changeset 088 : la colonne est nullable et l'identité du fil est portée par `Conversation`
  (`Message.java:42-43`, `Conversation.java:33-64`). L'annonce à un groupe n'est plus bloquée par
  là.
- `AUDIT-COACH-INDEPENDANT` §5 classait « athlète d'un club **et** d'un coach externe » comme non
  supporté faute de multi-club. C'est exact, mais la cause profonde est ailleurs : elle est dans
  `Athlete.club NOT NULL`, qui interdit qu'un athlète existe **avant** d'appartenir à quelqu'un
  (§2.1).

---

## 2. État des lieux vérifié

### 2.1 Le premier verrou : un athlète n'existe qu'à l'intérieur d'un club

```java
// back/src/main/java/com/coachrun/entity/Athlete.java:41-44
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "club_id", nullable = false)
private Club club;
```

Tout part de là. Un `Athlete` est une **fiche de suivi ouverte par un coach dans son espace**, pas
une personne. La création le confirme : `AthleteService.create` pose le club en première instruction
(`AthleteService.java:135-137`), et la case « athlète privé » ne change pas cela — elle met à `null`
le club **de la relation**, pas celui de l'athlète (`AthleteService.java:186`). L'audit d'août l'avait
déjà noté : « privé » ne veut pas dire « sans club ».

Le compte de connexion suit la même logique. Un `User` de rôle `ATHLETE` ne naît **que** de
l'acceptation d'une invitation, et il hérite du club de sa fiche :

```java
// back/src/main/java/com/coachrun/service/AuthService.java:324-330
user = new User();
user.setRole(UserRole.ATHLETE);
user.setClub(athlete.getClub());
user.setAthlete(athlete);
```

Quand l'athlète n'a pas donné d'adresse, on lui en fabrique une :
`ath-<uuid>@athlete.coachrun.local` (`AuthService.java:318`). C'est cohérent avec le modèle actuel —
un athlète est une donnée du coach — et frontalement incompatible avec un hub, où l'athlète arrive
en premier et n'appartient à personne.

**Conséquence.** Il n'existe aujourd'hui **aucun chemin** par lequel un athlète obtient un compte
sans qu'un coach ait d'abord créé sa fiche et cliqué sur « inviter » (`AthleteService.java:215`).
Le rendre possible est le chantier n° 1.

### 2.2 Le second verrou : un utilisateur n'a qu'un rôle

```java
// back/src/main/java/com/coachrun/entity/User.java:42-44
@Enumerated(EnumType.STRING)
@Column(name = "role", nullable = false, length = 32)
private UserRole role;
```

Quatre valeurs, exclusives : `PLATFORM_ADMIN`, `HEAD_COACH`, `COACH`, `ATHLETE` (`UserRole.java:6-11`).
Le rôle décide de tout : le filtre JWT le porte, les guards Angular routent dessus
(`app.routes.ts:9-11`), et `AthleteAccessValidator` refuse d'emblée tout accès coach à un compte
`ATHLETE` (`AthleteAccessValidator.java:69-72`).

**Conséquence.** Un coach du hub ne peut pas être coaché par un confrère sans ouvrir un second
compte avec une autre adresse. C'est une limite réelle d'une place de marché — un coach est souvent
aussi un athlète — mais elle est **contournable** et son levée est chère. §3.2 tranche.

### 2.3 Ce qui est déjà là, et qui portera le hub sans être touché

C'est la bonne nouvelle, et elle est substantielle.

- **La relation coach↔athlète est déjà un objet de première classe.** `CoachAthleteRelation`
  (coach, athlète, club nullable, `referent`, `active`) et `AthleteCoachPermission`
  (`READ < COMMENT < WRITE`, avec expiration) portent déjà un modèle multi-coach que
  `AthleteAccessValidator` applique. Un athlète privé est étanche, y compris au propriétaire du
  club, et c'est testé.
- **Le multi-club existe côté modèle** : `User.additionalClubs` (`User.java:58-63`), `ClubMember`
  avec `ClubRole` (`OWNER`, `COACH_PRINCIPAL`, `COACH_ASSISTANT`), `ClubAccessValidator`. Ce qui
  manque est l'interface, cf. `AUDIT-COACH-INDEPENDANT` §4.
- **La messagerie a des fils typés** depuis le changeset 088 : `ConversationKind.ATHLETE_COACH`
  identifie un binôme par `dedupKey` (`Conversation.java:72-74`), et deux coachs suivant le même
  athlète ne se lisent pas. C'est exactement le fil dont une relation issue du hub a besoin.
- **Le RGPD est outillé** : `GdprService` sait exporter (`export:72`), effacer
  (`deleteAthleteData:122`), retirer et redonner le consentement santé (`withdrawHealthConsent:161`,
  `grantHealthConsent:236`), et `HealthDataConsentValidator` refuse toute écriture de donnée
  d'article 9 sans base légale active. Le hub hérite d'un socle sérieux.
- **Le patron « demande arbitrée par un administrateur » est déjà écrit**, et c'est celui dont la
  validation des profils coachs a besoin : `ClubCreationRequest` porte
  `PENDING/APPROVED/REJECTED`, `reviewedAt`, `reviewedByUserId`, `reviewNote`, `createdClubId`, plus
  l'IP et le User-Agent du dépôt (`ClubCreationRequest.java:51-110`). Il se recopie presque tel quel.
- **L'ouverture d'un espace de travail est déjà factorisée** : `ClubProvisioningService.openClub`
  crée le club, le compte `HEAD_COACH`, le rattachement `OWNER` et la bibliothèque de départ
  (`ClubProvisioningService.java:58-88`). Un coach indépendant du hub passe par le même chemin.
- **Le rate limiting est déjà segmenté par nature de route**, avec un bucket dédié aux routes
  anonymes qui envoient des e-mails (`RateLimitFilter.java:56, 108-118`). L'annuaire public ajoutera
  le sien.

### 2.4 `AthleteProposal` n'est pas la demande de coaching

Le nom invite à la confusion, et il faut la lever tout de suite : `ProposalType` ne contient que des
**ajustements d'entraînement** — `SESSION_LIGHTEN`, `SESSION_EASY`, `SESSION_MOVE`, `SESSION_DROP`,
`PHYSIO_UPDATE`, `STRENGTH_LOAD` — chacun adossé à une branche du dispatcher `ProposalService#apply`
et à une charge utile JSON. `ProposalStatus` (`PENDING/ACCEPTED/DISMISSED/EXPIRED`) décrit le cycle
de vie d'une suggestion sur une séance, pas d'une mise en relation.

**Réutiliser cette entité serait une erreur** : on chargerait le dispatcher d'un cas qui n'écrit pas
dans le calendrier, et la file « propositions à traiter » du coach mélangerait « alléger la séance
de mardi » et « Marie voudrait que vous la coachiez ». Le patron à copier est
`ClubCreationRequest`, pas `AthleteProposal`.

### 2.5 ⚠️ Le défaut trouvé en chemin : on ne peut pas retirer un coach, et essayer l'aggraverait

Ce point est indépendant du hub, il concerne le produit d'aujourd'hui, et il doit être corrigé
**avant** d'ouvrir quoi que ce soit.

`CoachAthleteRelation` porte un booléen `active` (`CoachAthleteRelation.java:51-52`) qui suggère
qu'on peut clore une relation. **Aucun code ne le met jamais à `false`** : la seule désactivation du
dépôt porte sur `ClubMember` (`ClubMembershipService.java:169`). La fin de relation n'est donc pas
implémentée.

Le problème n'est pas seulement qu'elle manque, c'est ce qui se passerait si on l'implémentait
naïvement. Le validateur cherche la relation référente **active** ; s'il n'en trouve pas, il ne
refuse pas — il retombe sur l'accès club historique :

```java
// back/src/main/java/com/coachrun/security/AthleteAccessValidator.java:97-99
if (referent == null) {
    return clubLevelFallback(coachId, athleteId);
}
```

```java
// back/src/main/java/com/coachrun/security/AthleteAccessValidator.java:169-177
private Optional<PermissionLevel> clubLevelFallback(UUID coachId, UUID athleteId) {
    Athlete athlete = athleteRepository.findById(athleteId).orElse(null);
    if (athlete == null || athlete.getClub() == null) { return Optional.empty(); }
    return hasClubAccess(coachId, athlete.getClub().getId())
            ? Optional.of(PermissionLevel.WRITE)   // ← écriture, pas lecture
            : Optional.empty();
}
```

Ce repli est justifié pour ce qu'il visait — les athlètes antérieurs au modèle multi-coach ne
devaient pas devenir inaccessibles — mais il est indifférent à la **raison** pour laquelle la
relation référente est absente. Passer `active = false` sur la relation d'un coach **propriétaire du
club qui porte la fiche** ne lui retire donc rien : il repasse par le repli et récupère `WRITE`.
Dans le hub, où l'espace de travail du coach indépendant *est* le club de tous ses athlètes, c'est
le cas nominal — la fin de relation serait purement décorative.

**Correction.** Distinguer « aucune relation référente n'a jamais existé » (legacy → repli légitime)
de « la relation référente a été close » (→ refus). Concrètement : ne déclencher `clubLevelFallback`
que si `relationRepository.existsByAthleteId(athleteId)` est faux, et faire porter à la relation une
date de fin explicite plutôt qu'un simple booléen (§4.1). *Effort : S. À faire avant tout le reste.*

### 2.6 Ce qui n'existe nulle part

Vérifié par recherche exhaustive sur le dépôt : aucune entité, aucune colonne, aucun endpoint,
aucun écran pour le **profil public d'un coach**, la **recherche de coachs**, la **demande de
coaching**, les **avis**, les **tarifs**, la **facturation**. Le mot « annuaire » n'apparaît pas. Ce
n'est pas une adaptation à faire : c'est un module à écrire.

### 2.7 Le vocabulaire, et son ampleur mesurée

« Club » apparaît **931 fois dans 113 fichiers** du front. À l'inscription, l'écran s'intitule
« Créer ton club » et exige « Nom du club » avec `vous@club.fr` en exemple
(`register.component.html:107, 121-125, 132`). Pour un coach indépendant du hub, chacun de ces mots
décrit une organisation qui n'existe pas. `AUDIT-COACH-INDEPENDANT` §2 proposait déjà la parade —
une question à l'inscription et un drapeau sur `Club` — et elle reste la bonne ; le hub la rend
obligatoire au lieu d'agréable.

---

## 3. Les décisions à trancher

Dix questions. Pour chacune : les options réelles, ce qu'elles coûtent, et ce que je recommande.

### 3.1 Qui est l'athlète ? — la décision la plus structurante

`Athlete.club` est `NOT NULL`, et le hub a besoin d'un athlète qui existe sans coach.

| Option | Ce qu'elle fait | Ce qu'elle coûte |
|---|---|---|
| **A. Rendre `Athlete.club` nullable** | `Athlete` devient l'identité unique d'une personne | Très cher. Le club scope toute l'API (`/clubs/{clubId}/…`), `Conversation.club` et `Message.club` sont `NOT NULL`, `clubLevelFallback` en dépend, et des dizaines de requêtes `findByClubId…` deviennent fausses. Ordre de grandeur : la moitié des 56 contrôleurs |
| **B. Un club technique « hub »** | Tous les athlètes du hub dans un club fourre-tout | Faux-ami. Un club unique rend tous ses athlètes mutuellement visibles par le repli club (§2.5) et par `clubDefaultLevel`. Fuite de données par construction |
| **C. Séparer le compte de la fiche** ✅ | Un nouveau `AthleteAccount` (1:1 avec `User`) porte l'identité, l'inscription et le profil public. `Athlete` reste la fiche de suivi d'un coach, créée **à l'acceptation**, et pointe vers le compte | Une entité, une colonne FK nullable sur `athletes`, zéro requête existante cassée |

**Recommandation : C.** C'est la lecture honnête de ce que `Athlete` est déjà — le dossier qu'un
coach tient sur quelqu'un — et elle laisse l'invariant `club NOT NULL` intact, ce qui est la seule
raison pour laquelle ce chantier tient en quelques lots plutôt qu'en un trimestre.

**Le coût assumé, et il est réel.** Un athlète suivi par deux coachs de deux espaces différents aura
**deux fiches** `Athlete`, donc deux profils physiologiques et deux calendriers. Ce n'est pas un
détail : c'est la limite principale de l'architecture proposée. Elle est acceptable au lancement
(un athlète a un coach), elle devient gênante le jour où « course + prépa physique chez deux coachs »
se répand. Le chemin de sortie existe et reste ouvert : `athlete_account_id` étant posé dès le
départ, on peut plus tard **remonter la physiologie au compte** et faire des fiches de simples vues
— une migration additive, pas une reprise.

*À ne pas faire : commencer par A « pour être propre ». On paierait aujourd'hui, en risque de
régression sur un produit en bêta, une généralité dont on n'a pas encore l'usage.*

### 3.2 Un coach peut-il être athlète, et réciproquement ?

Le rôle est unique (§2.2). Le lever proprement demande une table `user_roles` et une reprise de tous
les guards, du filtre JWT et des `@PreAuthorize`.

**Recommandation : ne pas le lever au lancement.** Un coach qui veut être coaché ouvre un second
compte ; c'est documenté, c'est un irritant, ce n'est pas un blocage.

**Mais une précaution obligatoire, qui ne coûte rien maintenant et tout plus tard** : le profil
public du coach doit être une **entité séparée, clé sur `user_id`** — surtout pas des colonnes
ajoutées à `users`. Le jour où un compte porte deux casquettes, on ajoute une ligne dans une table
au lieu de déplacer des colonnes d'une table de comptes en production.

### 3.3 Comment un coach indépendant s'inscrit-il ?

Le formulaire exige `clubName` (`RegisterRequest.java:21`) et `openClub` crée toujours un club
(`ClubProvisioningService.java:60-64`).

**Recommandation : garder le club, cesser de le nommer ainsi.** Une question à l'inscription —
*« Vous coachez : en club / en indépendant »* — et, en indépendant, le champ devient « Nom de votre
activité », facultatif, pré-rempli avec le nom du coach. Le club implicite continue d'exister comme
**espace de travail** ; il disparaît simplement du vocabulaire et de la navigation. C'est la
proposition de `AUDIT-COACH-INDEPENDANT` §2, et le hub la rend nécessaire.

*Le modèle ne bouge pas. C'est un drapeau sur `Club` et une passe de libellés.*

### 3.4 Faut-il valider les profils coachs à la main ?

| Option | Pour | Contre |
|---|---|---|
| Publication immédiate | Aucun frein à la croissance | Un annuaire est jugé sur son pire profil, et les faux profils arrivent le premier mois |
| **Validation manuelle** ✅ | La confiance est le produit d'une place de marché à ses débuts ; le patron existe déjà (`ClubCreationRequest`) | Une file à tenir tous les matins |
| Validation automatique | — | Rien à automatiser tant qu'on n'a pas vu cent dossiers |

**Recommandation : validation manuelle**, en recopiant `ClubCreationRequest` : statuts
`DRAFT → PENDING → PUBLISHED`, plus `SUSPENDED`, avec `reviewedBy` et `reviewNote`. La plateforme
est déjà en régime `REQUEST` pour les clubs : on prolonge une pratique établie plutôt que d'en
inventer une.

**Les diplômes : déclaratifs, et dits comme tels.** Le coach déclare ses certifications, joint un
justificatif que l'administrateur regarde, et la fiche affiche « certifications vérifiées par
l'équipe » **ou** « déclarées par le coach » — jamais un badge ambigu. Se porter garant d'un diplôme
qu'on n'a pas contrôlé auprès de l'organisme émetteur est un engagement qu'on ne veut pas prendre.

### 3.5 L'annuaire est-il public, et quel est le classement par défaut ?

**Public, sans authentification.** C'est la vitrine ; la cacher derrière un compte tue l'acquisition.
`SecurityConfig` ouvre déjà `/public/**` en entier (`SecurityConfig.java:37`), donc les nouvelles
routes s'y logent sans toucher à la configuration — **à condition** de ne publier que des données de
vitrine : jamais d'e-mail, jamais de téléphone, jamais d'identifiant d'athlète, et un bucket de
rate limiting dédié (§4.4).

**SEO : à reporter, et à dire.** Le front est une SPA Angular sans SSR ni prerender (aucune trace
de `@angular/ssr` dans `front/`). Un annuaire non indexable perd l'essentiel de son intérêt
d'acquisition. Ce n'est pas une raison de bloquer le lancement — c'est une raison d'inscrire le
prerender des fiches coachs au lot suivant, en connaissance de cause.

**Classement par défaut : neutre et lisible.** Ni note (il n'y en aura pas, §3.8), ni ancienneté
(elle fige les positions le premier jour). Je recommande un tri par **pertinence des filtres, puis
disponibilité déclarée, puis aléatoire à graine tournante quotidienne** : deux coachs équivalents
apparaissent chacun leur tour en tête. Le mécanisme est simple, et il évite qu'un annuaire de
quarante coachs se transforme en avantage acquis pour les cinq premiers inscrits.

### 3.6 Peut-on se parler avant que la relation existe ?

`Conversation.club` et `Message.club` sont `NOT NULL` (`Conversation.java:35-37`, `Message.java:34-35`).
Ouvrir un fil entre deux inconnus demanderait de rendre le club nullable sur la messagerie et de
réécrire les règles de participation de `ConversationService`, qui les déduit toutes de
l'appartenance (relation référente, permissions, groupe, club).

**Recommandation : pas de messagerie libre avant acceptation.** À la place, la **demande porte son
message** — comme `ClubCreationRequest.message` (`ClubCreationRequest.java:67-68`) — et le coach
répond en acceptant, en refusant avec un motif, ou en posant **une** question de clarification à
laquelle l'athlète répond une fois. Trois échanges au maximum, dans l'objet « demande », pas dans la
messagerie.

Trois raisons, dans l'ordre d'importance : cela évite d'ouvrir un canal de spam vers tous les coachs
publiés le jour du lancement ; cela évite la modération d'une messagerie entre inconnus, qui est un
métier ; et cela laisse `ConversationService` intact, ce qui vaut plusieurs semaines. Dès
l'acceptation, le fil `ATHLETE_COACH` existant prend le relais sans une ligne de code nouvelle.

### 3.7 Le paiement au lancement ?

`PLAN-PRODUIT` §1.5 le classait déjà : suivi d'abord, encaissement ensuite. Le hub ne change pas
cette conclusion, il la renforce — encaisser pour le compte d'un tiers fait de la plateforme un
intermédiaire de paiement, avec les obligations qui vont avec.

**Recommandation : pas d'encaissement au lancement, mais les tarifs affichés et gelés.** Ce qui doit
exister dès la première ligne, parce que le rétro-ajouter coûterait cher :
- `CoachOffer` — nom, montant, périodicité, description — attachée au profil coach et **affichée
  dans l'annuaire** ;
- sur la relation, un **instantané de l'offre acceptée** (montant et libellé recopiés, pas une FK) :
  le tarif du jour de l'accord ne doit pas bouger quand le coach change sa grille six mois plus tard ;
- un champ « statut de paiement » libre, coché à la main par le coach.

Le rail d'encaissement, la TVA, les remboursements et la comptabilité viennent après, si les coachs
le réclament — et ils se brancheront sur des données déjà là.

### 3.8 Les avis au lancement ?

**Recommandation : non.** Avec quelques dizaines de coachs, les avis sont statistiquement muets et
socialement violents : un coach à une étoile pour un différend est marqué à vie, et la modération
d'un litige avis/représailles est un travail à plein temps qu'on ne peut pas assurer au premier
mois.

Ce qui remplace utilement les avis au lancement, et qui est **factuel, non manipulable et déjà
calculable** : ancienneté sur la plateforme, nombre d'athlètes actuellement suivis, délai médian de
réponse à une demande, taux de réponse. Les avis viennent quand il y a assez de relations
terminées pour qu'une moyenne veuille dire quelque chose — et ils seront alors **réservés aux
athlètes ayant eu une relation acceptée d'au moins N semaines**, ce que le modèle du §4 permet de
vérifier.

### 3.9 Les mineurs

Un athlète mineur qui s'inscrit seul et contacte un coach adulte engage la plateforme sur deux
terrains : le consentement au traitement de données de santé (article 9) et la mise en relation
elle-même.

**Recommandation : âge minimum de 16 ans à l'inscription libre, en dur.** En dessous, le seul chemin
reste celui d'aujourd'hui — le coach ou le club crée la fiche et invite, la relation étant nouée
hors plateforme avec les responsables légaux. C'est la règle la plus simple à tenir, et
`Athlete.birthDate` existe déjà (`Athlete.java:79-80`).

### 3.10 Comment une relation se termine

Aujourd'hui : elle ne se termine pas, et la terminer élèverait l'accès (§2.5).

**Recommandation.**
- **Les deux parties peuvent y mettre fin**, sans préavis ni motif obligatoire ; le motif est
  facultatif et n'est jamais publié.
- **La fiche `Athlete` et son historique restent dans l'espace du coach** : il en est l'auteur, et
  ils lui sont nécessaires (suivi de ses anciens athlètes, obligations de conservation). L'accès
  au *compte* de l'athlète, lui, est coupé net.
- **L'athlète garde son compte, son export RGPD et son droit à l'effacement** — `GdprService` sait
  déjà faire les trois.
- **Les séances futures sont déprogrammées, le passé est conservé.** Laisser un calendrier prescrit
  courir après la fin de la relation est trompeur pour l'athlète.
- **Le fil de messagerie passe en lecture seule** plutôt que de disparaître.

---

## 4. L'architecture cible

### 4.1 Le modèle de données

Cinq entités nouvelles, deux colonnes ajoutées, une correction. Aucune suppression, aucune colonne
rendue nullable sur une table existante.

**`athlete_accounts` — l'identité de l'athlète sur la plateforme.**

| Colonne | Type | Note |
|---|---|---|
| `id` | UUID | |
| `user_id` | UUID **NOT NULL**, unique | Le compte de connexion (rôle `ATHLETE`) |
| `first_name`, `last_name` | text | Saisis par l'athlète, pas par un coach |
| `birth_date` | date | Contrôle des 16 ans (§3.9) |
| `sex`, `discipline`, `level` | enums existants | `Sex`, `Discipline`, `AthleteLevel` |
| `city`, `country`, `latitude`, `longitude` | | Recherche géographique |
| `goal` | text 1000 | « Mon objectif » — c'est ce que le coach lit en premier |
| `looking_for_coach` | bool | Permet au coach de démarcher (§4.3), et de se retirer |
| `terms_accepted_at`, `health_data_consent_at` | timestamptz | Mêmes preuves que pour un coach |

Le compte ne porte **aucune donnée de santé** : ni seuil, ni note médicale, ni test. Elles restent
sur `Athlete`, chiffrées au repos, sous la garde de `HealthDataConsentValidator`. C'est volontaire :
l'article 9 ne doit pas entrer dans l'objet public.

**`coach_profiles` — la vitrine.** Clé sur `user_id` (§3.2), jamais des colonnes sur `users`.

| Colonne | Note |
|---|---|
| `user_id` NOT NULL unique | |
| `status` | `DRAFT / PENDING / PUBLISHED / SUSPENDED / CLOSED` — `CLOSED` = « ne prend plus d'athlètes », visible mais non contactable |
| `headline`, `bio` | Accroche + présentation |
| `disciplines`, `specialties`, `languages` | Tables de jointure, pas des colonnes CSV : ce sont les filtres |
| `city`, `country`, `latitude`, `longitude`, `remote_only` | |
| `experience_years`, `capacity_max`, `current_athletes` | `current_athletes` est **calculé**, pas saisi |
| `photo_attachment_id` | Réutilise le stockage de pièces jointes existant |
| `reviewed_at`, `reviewed_by_user_id`, `review_note` | Copie de `ClubCreationRequest` |
| `published_at`, `median_response_hours` | Les signaux factuels du §3.8 |

**`coach_certifications`** — libellé, organisme, année, `attachment_id`, `verified` (booléen posé par
l'administrateur seul). L'affichage distingue vérifié et déclaré (§3.4).

**`coach_offers`** — `coach_profile_id`, `name`, `amount_cents`, `currency`, `periodicity`,
`description`, `active`. Affichage seul au lancement (§3.7).

**`coaching_requests` — le cœur du hub.**

| Colonne | Note |
|---|---|
| `athlete_account_id`, `coach_user_id` | |
| `initiated_by` | `ATHLETE` ou `COACH` (démarchage, §4.3) |
| `status` | `PENDING / ACCEPTED / DECLINED / WITHDRAWN / EXPIRED` |
| `message` | Le mot de l'athlète (2000 car.) |
| `coach_question`, `athlete_answer` | L'aller-retour unique du §3.6 |
| `offer_id`, `offer_label`, `offer_amount_cents` | FK **et** instantané (§3.7) |
| `decided_at`, `decline_reason` | Motif jamais publié |
| `expires_at` | 14 jours, aligné sur les invitations athlètes (`AthleteService.INVITE_VALIDITY_DAYS`) |
| `created_athlete_id`, `created_relation_id` | Ce que l'acceptation a créé — comme `ClubCreationRequest.created_club_id` |
| `ip_address`, `user_agent` | Anti-abus, même patron que `ClubCreationRequest` |

Contrainte d'unicité partielle : **une seule demande `PENDING` par couple (athlète, coach)**. Sans
elle, un athlète insistant crée trente lignes dans la file du coach.

**Colonnes ajoutées.**
- `athletes.athlete_account_id` UUID **nullable** → `athlete_accounts.id`. Nullable est essentiel :
  les athlètes créés par un coach, avec ou sans compte, restent inchangés.
- `coach_athlete_relations.origin` (`MANUAL` / `HUB`), `started_at`, `ended_at`, `ended_by_user_id`,
  `end_reason`. `active` reste, dérivé de `ended_at IS NULL`.

**La correction du §2.5**, à livrer en premier : `clubLevelFallback` n'est appelé que si l'athlète
n'a **jamais** eu de relation référente. Une relation close doit refuser, pas retomber sur le club.

```java
// AthleteAccessValidator, remplacement de la ligne 97
if (referent == null) {
    return relationRepository.existsByAthleteId(athleteId)
            ? Optional.empty()              // relation close ⇒ refus
            : clubLevelFallback(coachId, athleteId);  // legacy ⇒ repli
}
```

### 4.2 Ce que l'acceptation déclenche

C'est la charnière du produit, et elle doit être atomique. Dans une seule transaction :

1. la demande passe à `ACCEPTED` ;
2. un `Athlete` est créé **dans le club-espace du coach** (`Athlete.club` reste satisfait), avec les
   défauts du club (`AthleteService.applyClubDefaults`) et `athlete_account_id` renseigné ;
3. le `User` de l'athlète voit son `club` aligné sur celui du coach s'il n'en avait pas — les fils
   de messagerie en dépendent (`Conversation.club NOT NULL`) ;
4. une `CoachAthleteRelation` référente est créée avec `club = null` (**athlète privé**) et
   `origin = HUB`. Privé par défaut, et ce n'est pas un détail : dans un espace de travail qui peut
   accueillir d'autres coachs, un athlète venu du hub a choisi *un* coach, pas une organisation ;
5. les zones sont provisionnées paresseusement à la première lecture, comme aujourd'hui
   (`TrainingZoneSeedService`) ;
6. le consentement santé du compte est reporté sur la fiche (`healthDataConsentAt`), faute de quoi
   `HealthDataConsentValidator` bloquera la première mesure ;
7. notification et e-mail aux deux parties (`NotificationCategory`, `MailKind` à étendre) ;
8. les autres demandes `PENDING` du même athlète **ne sont pas annulées** — il peut légitimement
   attendre deux réponses ; c'est à lui de les retirer.

### 4.3 L'API

**Public, sans authentification** — sous `/public/**`, déjà ouvert (`SecurityConfig.java:37`) :

| Route | Rend |
|---|---|
| `GET /public/coaches` | Annuaire paginé + filtres (`discipline`, `specialty`, `language`, `city`, `radius`, `remote`, `maxPrice`, `available`) |
| `GET /public/coaches/{slug}` | Une fiche publiée — sans e-mail ni téléphone |
| `GET /public/coach-facets` | Les valeurs de filtres disponibles, pour ne pas les coder en dur côté front |
| `POST /public/athlete-registration` | Inscription libre d'un athlète (bucket e-mail anonyme) |

**Athlète connecté** — sous `/me/**`, où `AthletePortalController` impose déjà
`hasRole('ATHLETE')` (`AthletePortalController.java:38-41`) :

`GET/PATCH /me/account` · `GET /me/coaching-requests` · `POST /me/coaching-requests` ·
`POST /me/coaching-requests/{id}/answer` · `DELETE /me/coaching-requests/{id}` (retrait) ·
`POST /me/relations/{id}/end`

**Coach connecté** :

`GET/PUT /me/coach-profile` · `POST /me/coach-profile/submit` (→ `PENDING`) ·
`GET/POST/DELETE /me/coach-profile/certifications` · `GET/POST/PUT/DELETE /me/coach-profile/offers` ·
`GET /me/coaching-requests` · `POST /me/coaching-requests/{id}/accept|decline|ask` ·
`POST /clubs/{clubId}/athletes/{athleteId}/end-relation`

**Administrateur** : `GET /admin/coach-profiles?status=PENDING` ·
`POST /admin/coach-profiles/{id}/approve|reject|suspend` · `POST /admin/certifications/{id}/verify` ·
`GET /admin/hub/stats`. Toutes tracées dans `AdminAuditLog`, en ajoutant les valeurs
`AdminAuditTarget.COACH_PROFILE` et `COACHING_REQUEST`.

**Et une dette existante à solder au passage** : `GET /me/clubs` + le sélecteur de club réclamés par
`AUDIT-COACH-INDEPENDANT` §4, ainsi que la correction de `CoachDashboardService#athletesInScope`
(§4 bis du même document). Le hub crée précisément les situations multi-espaces qui réveillent ce
défaut.

### 4.4 Sécurité

| Risque | Parade |
|---|---|
| Énumération des coachs et aspiration de l'annuaire | Bucket `public-directory` dédié dans `RateLimitFilter` ; pagination par curseur opaque, jamais par offset ; aucune donnée de contact rendue |
| Spam de demandes | Plafond par compte (N demandes `PENDING`, M par jour) ; unicité partielle sur `(athlete, coach, PENDING)` ; compte non vérifié par e-mail = aucune demande |
| Faux comptes athlètes | E-mail vérifié obligatoire **avant** la première demande — `EmailVerificationValidator` existe déjà |
| Un ex-coach garde l'accès | La correction du §2.5, et un test dédié |
| Fuite entre athlètes du même espace | Relation `club = null` à l'acceptation (§4.2) ; test « deux athlètes hub du même coach ne se voient pas » |
| Un coach lit un compte athlète sans relation | `AthleteAccount` n'est jamais servi à un coach hors demande reçue ou relation active |
| Contenu injurieux dans les profils et demandes | Signalement + `SUSPENDED` en back-office ; pas de modération a priori |

**Les tests à écrire, nommés** : `EndedRelationRevokesAccessTest`,
`HubAthleteIsPrivateToItsCoachTest`, `PublicDirectoryHidesContactDetailsTest`,
`CoachingRequestRateLimitTest`, `MinorSelfRegistrationRejectedTest`.

### 4.5 Les écrans

**Publics** (nouveaux, sous `front/src/app/features/hub/`) : annuaire avec filtres et carte,
fiche coach, inscription athlète. Le dossier `features/public` accueille déjà les écrans hors
session (`invitation`, `coach-invitation`, `legal`), et `features/search` fournit des primitives
réutilisables.

**Athlète** (sous `/athlete`, PWA) : « Trouver un coach » en entrée de navigation tant qu'aucune
relation n'existe, « Mes demandes » avec leur état, « Mon coach » avec la fin de relation.

**Coach** (sous `/app`) : « Demandes » avec pastille de compteur dans la coquille, le détail d'une
demande (profil de l'athlète, son objectif, son mot) et les trois actions, plus l'éditeur de profil
public dans les paramètres.

**Administrateur** : la file des profils à valider, calquée sur l'écran `club-requests` existant
(`app.routes.ts:479`), et les statistiques du hub.

---

## 5. Les parcours cibles

**A — Un coach indépendant s'inscrit et publie sa fiche.**
`/register` → il choisit « en indépendant » → « Nom de votre activité » (facultatif, pré-rempli) →
compte créé, espace ouvert, bibliothèque de départ posée, e-mail à vérifier. Il travaille
immédiatement, comme aujourd'hui. Une bannière l'invite à publier sa fiche : photo, accroche, bio,
spécialités, langues, zone, distanciel, formules, certifications. Il soumet → `PENDING`, avec un
écran qui dit franchement « votre fiche est en cours de validation, comptez 48 h ». L'administrateur
valide → `PUBLISHED`, e-mail, la fiche apparaît dans l'annuaire.

**B — Un athlète s'inscrit seul.**
Landing → « Je cherche un coach » → e-mail, mot de passe, nom, date de naissance (contrôle des
16 ans), CGU + consentement santé → compte `ATHLETE` **sans fiche `Athlete` et sans club** → e-mail
de vérification. Il atterrit sur l'annuaire, pas sur un calendrier vide. Tant qu'il n'a pas de
coach, son espace montre son profil, ses demandes et l'annuaire — rien d'autre, et surtout pas des
écrans d'entraînement inertes.

**C — Il cherche et choisit.**
Annuaire, filtres, fiche coach. « Demander à être coaché » → si non connecté, inscription puis
retour sur la fiche ; si e-mail non vérifié, blocage explicite. Formulaire court : formule
souhaitée, objectif, disponibilités, un mot. Envoi → `PENDING`, 14 jours, et il voit sa demande dans
« Mes demandes ». Il peut la retirer.

**D — Le coach répond.**
Notification et e-mail. L'écran « Demandes » montre l'objectif de l'athlète, sa discipline, son
niveau, son mot — **jamais son adresse e-mail** avant acceptation. Trois actions : accepter (avec
la formule), poser une question, refuser avec un motif. L'athlète est notifié dans les trois cas ;
le motif de refus lui est transmis et n'est jamais public.

**E — Ils travaillent.**
L'acceptation exécute la séquence du §4.2. L'athlète voit son calendrier, sa physiologie, son fil
de messagerie ; le coach voit un athlète de plus dans son périmètre « Privés ». **À partir de là,
c'est le produit d'aujourd'hui, inchangé** — et c'est bien pour cela que ce chantier est faisable.

**F — Ça se termine.**
L'un des deux clique sur « Mettre fin au coaching », confirme, motif facultatif. `ended_at` est posé,
les séances futures déprogrammées, le fil passe en lecture seule, le coach perd tout accès (§2.5
corrigé). L'athlète retrouve l'annuaire ; sa fiche et son historique restent chez son ancien coach ;
son export et son effacement RGPD restent à sa main.

---

## 6. Plan de mise en œuvre par lots

Huit lots. Les quatre premiers font un hub qui fonctionne ; les quatre suivants le rendent bon.

| Lot | Contenu | Effort | Dépend de |
|---|---|---|---|
| **0 — Solder la dette d'accès** | Correction de `clubLevelFallback` (§2.5) + `ended_at` sur la relation + `EndedRelationRevokesAccessTest` | **S** | — |
| **1 — Le compte athlète autoporté** | `athlete_accounts`, inscription libre, vérification d'e-mail, contrôle des 16 ans, `athletes.athlete_account_id`, écran « pas encore de coach » | **M** | 0 |
| **2 — La vitrine coach** | `coach_profiles`, `coach_certifications`, `coach_offers`, éditeur de profil, validation en back-office | **M** | — (parallélisable avec 1) |
| **3 — L'annuaire** | `GET /public/coaches` + filtres + facettes, écrans annuaire et fiche, bucket de rate limiting | **M** | 2 |
| **4 — La mise en relation** | `coaching_requests`, les deux files, l'acceptation transactionnelle (§4.2), la fin de relation, les notifications | **L** | 1, 2, 0 |
| — | *↑ **Ici, le hub est utilisable de bout en bout.** ↑* | | |
| **5 — Le vocabulaire indépendant** | Question à l'inscription, drapeau sur `Club`, passe de libellés | **S** | — |
| **6 — Le multi-espace** | `GET /me/clubs`, sélecteur de club, correction `athletesInScope` (dette `AUDIT-COACH-INDEPENDANT` §4/§4 bis) | **M** | — |
| **7 — Acquisition et confiance** | Prerender SEO des fiches, signaux factuels (délai de réponse, taux de réponse), signalement | **M** | 3 |
| **8 — Avis, puis tarification** | Avis réservés aux relations terminées ≥ N semaines ; encaissement à décider séparément | **L** | 4, et du recul d'usage |

**Le lot 0 se livre seul et tout de suite** : il corrige un défaut du produit actuel, il ne dépend
de rien, et tous les autres s'appuient dessus.

**Ordre recommandé si une seule personne développe** : 0 → 5 → 1 → 2 → 3 → 4 → 6 → 7 → 8. Le lot 5
remonte parce qu'il coûte deux jours, qu'il améliore le produit d'aujourd'hui, et qu'il évite
d'écrire les nouveaux écrans avec le vocabulaire qu'on s'apprête à changer.

**Ampleur globale.** 5 entités nouvelles, 2 tables de jointure, ~8 changesets Liquibase, ~30
endpoints, ~10 écrans Angular. Aucune migration destructive, aucune colonne existante rendue
nullable, aucune reprise de données obligatoire.

**Ce qui est irréversible et doit être tranché avant la première ligne :** la décision du §3.1
(compte séparé de la fiche). Tout le reste s'ajoute ou se retire ; celle-là décide de la forme des
données et se paierait en migration.

---

## 7. Risques

| Risque | P. | Impact | Parade |
|---|---|---|---|
| **La duplication de fiche** (§3.1) devient bloquante plus vite que prévu — « course + prépa physique chez deux coachs » | Moyenne | Élevé | `athlete_account_id` posé dès le lot 1 laisse la porte ouverte à la remontée de la physiologie au compte. Surveiller le nombre d'athlètes ayant ≥ 2 fiches |
| **L'annuaire vide au lancement** : un athlète qui arrive sur douze coachs repart | Élevée | Élevé | Ne pas ouvrir l'inscription athlète avant un seuil de coachs publiés (30 ?). Le lot 2 avant le lot 1 dans le calendrier public, même si le code est prêt |
| **La désintermédiation** : coach et athlète s'échangent leurs coordonnées et quittent la plateforme | Élevée | Moyen (élevé si commission un jour) | Ne pas s'y opposer tant qu'il n'y a pas d'encaissement — c'est un combat coûteux et perdu d'avance. En faire un critère de décision du §3.7 |
| **Charge de modération** sous-estimée (profils, demandes, litiges) | Moyenne | Moyen | Validation manuelle du lot 2 = découverte précoce du volume réel. Reporter les avis (§3.8) |
| **Requalification en intermédiaire** avec obligations d'information | Moyenne | Élevé | Avis juridique **avant** le lot 4. CGU distinguant explicitement mise en relation et prestation de coaching |
| **Données de santé chez un coach inconnu de la plateforme** | Moyenne | Élevé | Consentement explicite au moment de l'acceptation, pas à l'inscription ; le coach est nommé dans le texte du consentement |
| **Un coach du hub abuse de son espace** (crée des fiches sur des tiers) | Faible | Moyen | Rien de nouveau : c'est déjà le cas aujourd'hui. Le back-office le voit |
| **Absence de SEO** rend l'annuaire invisible | Élevée | Moyen | Assumé au lancement, lot 7. Ne pas fonder l'acquisition dessus d'ici là |
| **`/public/**` ouvert en bloc** : une future route publique expose plus que prévu | Faible | Élevé | Revue explicite de chaque ajout sous `/public` ; tests de non-exposition (§4.4) |

---

## 8. Ce que cet audit n'a pas tranché

Ces questions relèvent d'une décision produit, pas d'une lecture de code. Elles sont écrites pour
qu'on puisse y répondre par oui ou par non.

1. **Accepte-t-on la duplication de fiche** (un athlète suivi par deux coachs = deux dossiers), en
   échange d'un chantier trois à quatre fois plus court ? — *§3.1. C'est la seule décision
   irréversible du document.*
2. **Un coach peut-il rester incapable d'être athlète** sur la plateforme au lancement ? — *§3.2*
3. **Valide-t-on chaque profil coach à la main**, avec la charge quotidienne que cela implique ? —
   *§3.4*
4. **Se porte-t-on garant des diplômes**, ou affiche-t-on « déclaré par le coach » ? — *§3.4*
5. **Accepte-t-on qu'il n'y ait aucun échange libre avant acceptation** (une question, une réponse,
   dans la demande) ? — *§3.6*
6. **Les tarifs sont-ils affichés dès le lancement**, sans possibilité de payer sur la plateforme ? —
   *§3.7*
7. **Lance-t-on sans aucun avis** ? — *§3.8*
8. **Bloque-t-on l'inscription libre en dessous de 16 ans** ? — *§3.9*
9. **La fiche et l'historique restent-ils chez le coach** après la fin de la relation, l'athlète ne
   gardant que son compte et ses droits RGPD ? — *§3.10*
10. **Attend-on un seuil de coachs publiés avant d'ouvrir l'inscription athlète au public** — et si
    oui, lequel ? — *§7*

Et trois questions qu'il faudrait poser à de vrais coachs et à de vrais athlètes avant le lot 4,
parce que leurs réponses changeraient l'ordre du §6 :

- Un coach indépendant accepterait-il de publier ses tarifs ? (Beaucoup ne le font pas.)
- Un athlète choisit-il un coach sur un annuaire, ou sur une recommandation ? (Si c'est la seconde,
  le lot 7 — partage et référencement — vaut plus que le lot 3.)
- Combien de demandes par semaine un coach accepte-t-il de traiter avant que la file devienne une
  corvée ?

---

*Audit rédigé en septembre 2026 sur la branche `claude/audit-coach-athlete-hub-am5hao`. Chaque
affirmation renvoie au fichier et à la ligne qui la fondent ; aucun code n'a été modifié.*
