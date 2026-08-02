# Cahier des charges — **Dies**

## Application privée de suivi des dossiers et des échéances juridiques

> **Statut : v1.0 — dossier de spécifications prêt pour le développement.**
> Utilisatrice unique : une juriste. Application web privée, non publique, un seul compte.
> Priorisation **MoSCoW** : **M** = indispensable au MVP · **S** = important · **C** = confort · **W** = ultérieur.
> Les éléments marqués _(à valider)_ sont des hypothèses de travail à confirmer avec l'utilisatrice
> (cf. questionnaire de cadrage dans [`README.md`](./README.md) § 4).

---

# 0. Décision préalable : module ou application distincte ?

**Recommandation : application distincte, dépôt Git distinct, base de données distincte.**

| Critère | Verdict |
|---|---|
| Métier | Aucun recouvrement avec le coaching sportif : ni données, ni utilisateurs, ni vocabulaire |
| Utilisateurs | DARI Lab = SaaS multi-tenant ouvert · Dies = **une seule personne**, jamais d'inscription |
| Données | Dossiers juridiques confidentiels : le cloisonnement doit être **physique**, pas logique |
| Surface d'attaque | Un SaaS public expose des routes d'inscription, d'invitation, de webhooks — inutile et risqué ici |
| Cycle de vie | Les deux produits n'évoluent pas au même rythme et n'ont pas les mêmes fenêtres de déploiement |

Ce qui est **réutilisé** de DARI Lab : l'ADN technique (Angular standalone + Spring Boot + PostgreSQL +
Liquibase), les conventions de code, les patterns (intercepteurs, toasts, design tokens, `BaseEntity`,
chiffrement au repos, validation des secrets au démarrage) et la chaîne CI/CD. Autrement dit : **le
savoir-faire, pas le code**.

> Le mot « module » du brief est donc entendu comme **« nouveau produit bâti sur le même socle »**.

---

# 1. Contexte & objectifs

## 1.1 Contexte

Une juriste suit en parallèle un portefeuille de dossiers (sociétés, contrats, baux, contentieux,
conformité) dont chacun porte des **échéances datées et non négociables** : tenir l'assemblée
d'approbation des comptes dans les six mois de la clôture, déposer les comptes au greffe dans le mois
qui suit, dénoncer un bail six mois avant l'échéance triennale, renouveler une marque avant sa date
anniversaire, respecter un délai d'appel d'un mois…

Ces échéances sont aujourd'hui suivies **de tête, dans un tableur et dans un agenda**, avec trois
faiblesses structurelles :

1. **Le calcul est refait à la main** à chaque fois (« clôture + 6 mois, puis + 1 mois, sauf si dépôt
   électronique… »), donc il est chronophage et faillible.
2. **Rien ne prévient** : l'alerte dépend de la mémoire ou d'un rappel posé manuellement.
3. **La vision d'ensemble manque** : impossible de répondre d'un coup d'œil à « qu'est-ce qui tombe en
   octobre ? » ou « où en est le dossier X ? ».

## 1.2 Objectifs

### Objectif principal — faire gagner du temps, en supprimant trois tâches récurrentes

| Tâche aujourd'hui manuelle | Ce que fait Dies |
|---|---|
| Calculer une date d'échéance à partir d'une date de départ et d'une règle | **Moteur de délais** : jours calendaires / ouvrés / francs / mois, report des week-ends et jours fériés, délais avant ou après l'événement |
| Recréer chaque année la même liste de tâches pour chaque société | **Modèles de procédure** : « approbation des comptes SARL » appliqué en un clic génère les 10 échéances datées ; régénération automatique à chaque exercice |
| Penser à regarder son suivi | **Rappels automatiques** par e-mail (J-60/J-30/J-15/J-7/J-2/jour J, relance si retard) + **brief hebdomadaire du lundi** + **plan du mois** |

### Objectifs secondaires
- **Voir** : tableau de bord (aujourd'hui / cette semaine / en retard), **vue mensuelle**, vue par dossier, vue par société.
- **Tenir son agenda** : rendez-vous, audiences, assemblées, réunions — avec horaires, lieu et participants — **affichés dans le même calendrier que les échéances** (§ 3.4).
- **Tracer** : historique horodaté par dossier, preuve de réalisation attachée à l'échéance (récépissé de dépôt, PV signé…).
- **Retrouver** : recherche globale instantanée sur dossiers, sociétés, contacts, documents.
- **Sécuriser** : un seul compte, identifiants en variables d'environnement, chiffrement au repos, aucune indexation, aucune inscription possible.
- **S'intégrer sans friction** : abonnement **iCal** pour voir les échéances dans son agenda habituel (Outlook / Google Agenda) sans double saisie.

### Critère de réussite (à 3 mois d'usage)
> Le tableur de suivi actuel n'est plus ouvert, et **aucune échéance n'a été découverte en retard**.

---

# 2. Périmètre

## 2.1 Utilisateur

| Rôle | Description | Accès |
|---|---|---|
| **Juriste (utilisatrice unique)** | Accès complet à toutes les fonctions | Web (desktop + mobile PWA), authentifié |

Il n'y a **pas de rôles, pas de multi-utilisateur, pas d'inscription, pas d'invitation**. L'identifiant
et le mot de passe sont provisionnés par **variables d'environnement** (cf. § 6.4 et `Techno.md` § 3.4).

> _(à valider)_ Si un second utilisateur devait exister un jour (associé, assistante), le modèle prévoit
> un champ `responsable` sur dossier et échéance dès le MVP — le multi-utilisateur serait alors une
> évolution de l'authentification, pas une refonte du modèle. **Hors périmètre v1.**

## 2.2 Hypothèse de spécialité — et pourquoi elle n'enferme rien

Le référentiel métier livré couvre en priorité le **droit des sociétés / secrétariat juridique**
(approbation et dépôt des comptes, vie sociale, mandats), qui correspond à l'exemple donné, complété par
**baux commerciaux, contrats, propriété industrielle, conformité RGPD et délais de procédure**.

**Le moteur ne connaît aucune règle en dur** : toutes les règles de délai et tous les modèles de
procédure vivent **en base de données**, sont éditables dans l'interface, et sont livrés sous forme de
**jeu de données initial** (`seed`). Changer de spécialité = changer le jeu de données, pas le code.
Voir [`Referentiel-juridique.md`](./Referentiel-juridique.md).

## 2.3 Hors périmètre (v1)

- Rédaction d'actes / génération de documents à partir de modèles Word (W — voir § 9 phase 4).
- Facturation, honoraires, temps facturable (le suivi de temps simple est en C).
- Signature électronique, dépôt automatisé sur le guichet unique INPI ou Télérecours (W — pas d'API publique stable ; le dépôt reste manuel, l'outil rappelle et trace).
- Base documentaire juridique / veille légale automatique (W).
- Partage externe avec un client, portail tiers (W).
- Application mobile native — on livre une **PWA installable**.

---

# 3. Fonctionnalités détaillées

## 3.1 Dossiers (M)

Le **dossier** est l'unité de travail : une affaire, un contrat, un contentieux, une opération.

- **Référence automatique** au format `AAAA-TYP-NNN` (ex. `2026-SOC-014`, `2026-CTX-003`), unique, non réutilisable, avec possibilité de saisir une référence libre en complément _(à valider : format)_.
- **Champs** : intitulé, type, société/entité concernée, contrepartie, statut, criticité, date d'ouverture, date de clôture, responsable, description, tags libres, montant en jeu (optionnel), lien vers un dossier parent.
- **Types de dossier** (enum, extensible) : `VIE_SOCIALE` · `CONTRAT` · `CONTENTIEUX` · `PRECONTENTIEUX` · `BAIL_IMMOBILIER` · `PROPRIETE_INTELLECTUELLE` · `CONFORMITE` · `SOCIAL` · `OPERATION` (cession, augmentation de capital, fusion) · `CONSEIL` · `AUTRE`.
- **Statuts** (machine à états) : `OUVERT` → `EN_COURS` → `EN_ATTENTE_TIERS` → `SUSPENDU` → `CLOS` → `ARCHIVE`. Transitions validées côté service ; un dossier `CLOS` avec des échéances non faites déclenche un avertissement explicite.
- **Criticité** : `STRATEGIQUE` / `COURANT` / `FAIBLE` — pilote l'ordre de tri et l'agressivité des rappels.
- Actions : créer, dupliquer (reprendre un dossier type), clore, rouvrir, archiver, supprimer (avec confirmation forte).
- Recherche et filtres : par type, statut, société, responsable, tag, échéance à venir, texte libre.

## 3.2 Sociétés / entités suivies (M)

C'est **la fiche qui fait gagner le plus de temps** : c'est d'elle que les échéances annuelles récurrentes sont déduites.

- **Identité** : dénomination, forme juridique (`SARL`, `EURL`, `SAS`, `SASU`, `SA`, `SCI`, `SNC`, `SCP`, `ASSOCIATION`, `AUTRE`), SIREN/SIRET, greffe compétent, adresse du siège, date d'immatriculation, capital social.
- **Données pilotant les échéances** :
  - **date de clôture de l'exercice** (jour + mois) → génère toute l'année sociale ;
  - présence d'un **commissaire aux comptes** + date de fin de mandat (6 exercices) ;
  - **catégorie de taille** (micro / petite / moyenne / grande) → détermine l'option de confidentialité des comptes ;
  - mode de dépôt habituel (**papier** ou **électronique**) → 1 mois ou 2 mois après l'AG ;
  - régime fiscal (IS / IR) _(indicatif)_.
- **Dirigeants et mandats** : nom, fonction, date de nomination, durée, **date de fin de mandat** → échéance de renouvellement automatique.
- **Organes** : associés/actionnaires (nombre de parts, %), pour préparer les convocations _(S)_.
- Statut : `ACTIVE` / `EN_SOMMEIL` / `RADIEE`.
- **Vue « année sociale »** : frise de l'exercice en cours (clôture → arrêté des comptes → convocation → AG → dépôt → paiement du dividende) avec l'état de chaque étape. **(M)**

## 3.3 Échéances — le cœur du produit (M)

- **Champs** : intitulé, dossier et/ou société rattachés, **date d'échéance**, **date de départ (fait générateur)**, règle de calcul appliquée, nature, criticité, statut, responsable, note, pièce justificative, paliers de rappel.
- **Nature** : `LEGALE` · `REGLEMENTAIRE` · `JUDICIAIRE` (délai de procédure) · `CONTRACTUELLE` · `FISCALE` (indicatif) · `INTERNE`.
- **Criticité** — pilote le code couleur, le tri et les rappels par défaut :
  | Criticité | Sens | Rappels par défaut |
  |---|---|---|
  | `BLOQUANTE` | Sanction, forclusion, irrecevabilité, amende | J-60, J-30, J-15, J-7, J-2, jour J, **puis tous les jours en retard** |
  | `IMPORTANTE` | Manquement gênant mais rattrapable | J-30, J-7, jour J, puis hebdomadaire |
  | `CONFORT` | Organisation interne | J-7, jour J |
- **Statuts** : `A_FAIRE` → `EN_COURS` → `FAITE` · `SANS_OBJET` · `REPORTEE`. Passage à `FAITE` = date de réalisation + possibilité d'attacher la **preuve** (récépissé, accusé de dépôt, PV).
- **Échéance calculée vs échéance saisie** : toute échéance calculée par le moteur **reste modifiable manuellement**. Une date corrigée à la main est marquée `date ajustée` et n'est plus écrasée par un recalcul ; la règle d'origine reste visible.
- **Explication du calcul, toujours affichée** : « Clôture 31/12/2025 + 6 mois → 30/06/2026 · art. L.223-26 C. com. · reportée au 30/06/2026 (jour ouvrable) ». **Non négociable : une échéance dont on ne comprend pas l'origine ne sera pas suivie.**
- Actions rapides : marquer faite, reporter (avec motif obligatoire), déléguer, dupliquer, créer l'échéance suivante.

### 3.3.1 Moteur de calcul des délais (M)

Service isolé et testé unitairement (`DeadlineEngine`), spécifié en détail dans
[`Referentiel-juridique.md`](./Referentiel-juridique.md) § 2. Il gère :

- les unités : **jours calendaires**, **jours ouvrables**, **jours ouvrés**, **jours francs**, **mois**, **années** (quantième à quantième, avec repli sur le dernier jour du mois) ;
- le **sens** : délai **après** un fait générateur (dépôt, recours) ou **avant** une échéance (préavis, convocation) ;
- le **report** : au premier jour ouvrable **suivant** pour un délai qui expire (art. 641-642 CPC), au jour ouvrable **précédent** pour un préavis — un préavis ne se rattrape jamais après coup ;
- les **jours fériés français** calculés (dont Pâques et ses dérivés), plus un calendrier de jours chômés personnels (congés) ;
- les **délais de distance** (+1 mois outre-mer, +2 mois étranger, art. 643 CPC).

### 3.3.2 Modèles de procédure (M)

Un **modèle** est une checklist ordonnée d'étapes, chacune portant une règle de délai relative à un
**fait générateur** (clôture d'exercice, signification d'un jugement, date d'effet d'un bail…).

- Appliquer un modèle à un dossier ou à une société → **génère toutes les échéances datées** en une action.
- Le modèle est **éditable** (ajouter/retirer une étape, changer un délai) et **duplicable**.
- Modèles livrés en jeu de données initial : approbation des comptes (SARL / SAS / SA), cession de parts, transfert de siège, changement de dirigeant, congé triennal de bail, renouvellement de bail, renouvellement de marque, suites d'un jugement de première instance, violation de données personnelles. Détail et bases légales : [`Referentiel-juridique.md`](./Referentiel-juridique.md) § 4.
- **Génération annuelle automatique** : à chaque nouvel exercice clos, les échéances récurrentes d'une société sont créées par un job planifié, **idempotent** (clé unique `société + exercice + code d'échéance`) — jamais de doublon.

## 3.4 Agenda — rendez-vous et journée de travail (M)

> **Une échéance n'est pas un rendez-vous.** L'échéance est une **date limite** (déposer avant le 20/07)
> calculée par le moteur ; le rendez-vous est un **créneau horaire** (AG le 26/06 à 14 h 30, salle du
> conseil, avec le notaire). Les deux vivent dans le même calendrier, mais ce sont deux objets
> différents — les confondre produirait soit un agenda incapable de calculer un délai, soit un moteur de
> délais encombré d'horaires. Dies gère les deux et **les affiche ensemble**.

### 3.4.1 Événements (M)

- **Types** : `RENDEZ_VOUS` · `AUDIENCE` · `ASSEMBLEE` (AG, conseil) · `REUNION` · `SIGNATURE` (acte, closing) · `APPEL` · `DEPLACEMENT` · `FORMATION` · `RAPPEL_PERSONNEL` · `INDISPONIBILITE` (congé, absence).
- **Champs** : intitulé, type, **date et heure de début / fin** (ou « journée entière »), lieu (adresse **ou lien de visioconférence**), participants (contacts liés), **dossier et/ou société rattachés**, notes, statut (`CONFIRME` / `A_CONFIRMER` / `ANNULE`), rappel propre (15 min / 1 h / 1 j / 1 semaine avant).
- **Récurrence** : quotidienne, hebdomadaire, mensuelle (quantième ou « 3e mardi »), annuelle, avec fin par date ou par nombre d'occurrences. Une occurrence peut être **déplacée ou annulée sans casser la série**.
- **Lien avec une échéance** : une échéance peut porter un rendez-vous (l'AG d'approbation à tenir avant le 30/06 → l'AG effectivement convoquée le 26/06 à 14 h 30). Le lien est explicite : **tenir le rendez-vous marque l'échéance comme faite**, en un clic.
- **Détection de conflit** : chevauchement de créneaux, et alerte si un rendez-vous est posé le jour d'une échéance `BLOQUANTE` ou pendant une indisponibilité.
- **Préparation** : bloc « à préparer » sur l'événement (checklist libre) — utile avant une AG ou une audience.

### 3.4.2 Vues d'agenda (M)

| Vue | Contenu |
|---|---|
| **Jour** | Colonne horaire (7 h → 21 h) avec les rendez-vous, **bandeau « toute la journée » en haut portant les échéances du jour**, et un **bloc-notes daté** libre (« ce qu'il faut faire aujourd'hui ») |
| **Semaine** | 5 ou 7 colonnes, même superposition échéances / rendez-vous, imprimable |
| **Mois** | Calendrier unifié : pastilles d'urgence pour les échéances, blocs horaires pour les rendez-vous, + **liste du mois groupée par jour** |
| **Planning** | Liste chronologique continue « ce qui vient », tous objets confondus |

Un **sélecteur de calques** permet d'afficher ou masquer : échéances · rendez-vous · indisponibilités ·
calendrier externe (§ 3.4.4).

### 3.4.3 Notes de journée (S)

Un bloc-notes daté par jour, libre, chiffré — l'équivalent numérique de la page d'agenda papier :
ce qu'elle a fait, ce qu'il reste, ce qu'on lui a demandé. Recherchable, imprimable avec la vue jour.
_(Distinct du journal de dossier § 3.8, qui est rattaché à une affaire et verrouillé.)_

### 3.4.4 Articulation avec Outlook / Google Agenda (S) — **la question à trancher avec elle**

Trois niveaux, du moins cher au plus coûteux. **Recommandation : niveaux 1 et 2, pas le 3.**

| Niveau | Principe | Coût | Limite |
|---|---|---|---|
| **1. Export** *(déjà prévu, § 3.10)* | Dies publie un flux iCal ; Outlook/Google **s'y abonnent** et affichent échéances et rendez-vous | Faible | Lecture seule côté Outlook, rafraîchissement différé (jusqu'à 24 h chez Microsoft) |
| **2. Import en lecture seule** | Elle colle dans Dies l'**URL de publication** de son agenda Outlook ou Google ; Dies l'affiche en calque grisé, non modifiable | Faible — une URL, pas d'OAuth | Lecture seule, pas de création depuis Dies |
| **3. Synchronisation bidirectionnelle** | Microsoft Graph / Google Calendar API, OAuth, jetons, gestion des conflits | **Élevé** | Complexité, tokens à maintenir, risque de doublons et de boucles de synchronisation — et souvent bloqué par la DSI de l'employeur |

Avec les niveaux 1 et 2, elle **voit tout au même endroit dans les deux sens** sans qu'aucun système
n'écrive chez l'autre. C'est le meilleur rapport valeur/risque pour une application privée.

> ⚠️ **À vérifier avec elle avant de développer quoi que ce soit ici** : si son agenda professionnel est
> imposé par son employeur (Outlook d'entreprise), la question n'est pas technique mais organisationnelle
> — a-t-elle le droit de publier son calendrier vers un outil personnel ? Si non, on reste au niveau 1
> (Dies exporte, elle s'abonne) et l'agenda interne de Dies sert aux rendez-vous liés aux dossiers.

## 3.5 Vues et navigation (M)

| Vue | Contenu | Priorité |
|---|---|---|
| **Tableau de bord** | En retard (rouge, en tête) · **Aujourd'hui : échéances + rendez-vous** · Cette semaine · 30 prochains jours · dossiers sans échéance à venir · compteurs | **M** |
| **Agenda Jour / Semaine / Mois** | Cf. § 3.4.2 — échéances et rendez-vous superposés, imprimables | **M** |
| **Vue mois** | Calendrier mensuel + **liste du mois groupée par jour** (demande explicite), filtrable par société/type/criticité, imprimable en PDF « plan du mois » | **M** |
| **Vue liste** | Toutes les échéances, filtres combinables, tri, pagination serveur, export CSV | **M** |
| **Vue dossier** | En-tête + onglets *Échéances / Agenda / Documents / Journal / Contacts* + frise chronologique | **M** |
| **Vue société** | Identité, dirigeants, **frise de l'année sociale**, dossiers rattachés, échéances récurrentes | **M** |
| **Recherche globale** | Palette `Ctrl/Cmd+K` : dossiers, sociétés, contacts, documents, échéances, rendez-vous | S |

## 3.6 Rappels et notifications (M)

- **Canal principal : e-mail** (le seul qui la trouvera où qu'elle soit).
- **Canal secondaire : notification push PWA** _(S)_.
- **Paliers** par criticité (§ 3.3), **surchargeables échéance par échéance**.
- **Rappels de rendez-vous** : propres à l'événement (15 min / 1 h / 1 j / 1 semaine avant), indépendants des paliers d'échéance. **(M avec l'agenda)**
- **Brief hebdomadaire** : e-mail le **lundi 8 h** — retards, échéances de la semaine, **rendez-vous de la semaine**, échéances des 30 jours. **(M)**
- **Point du matin** _(S, activable)_ : e-mail à 7 h 30 — **la journée en un écran** : rendez-vous du jour avec horaires, échéances du jour, retards.
- **Plan du mois** : e-mail le **1er du mois** — toutes les échéances du mois, groupées par société. **(S)**
- **Anti-bruit** : un seul e-mail par déclenchement regroupant toutes les échéances concernées ; pas de rappel pour une échéance `FAITE` ou `SANS_OBJET` ; **idempotence stricte** (clé `échéance + palier`) pour qu'un redémarrage du serveur ne renvoie jamais deux fois le même rappel.
- **Digest de retard** : tant qu'une échéance `BLOQUANTE` est en retard, relance quotidienne jusqu'à traitement ou mise en `SANS_OBJET`.

## 3.7 Documents (S, essentiel dès le lot 3)

- Pièces jointes rattachées à un dossier, une société ou une **échéance** (preuve de réalisation).
- Types : statuts, PV d'assemblée, K-bis, comptes annuels, récépissé de dépôt, contrat, jugement, courrier, acte de commissaire de justice, autre.
- Métadonnées : date de l'acte, auteur, type, note, taille, empreinte SHA-256 (intégrité).
- Stockage **chiffré au repos**, hors de la base ; nom de fichier normalisé ; téléchargement via URL signée à durée courte.
- Marquage **« Confidentiel — consultation juridique »** _(à valider selon son statut, cf. `Referentiel-juridique.md` § 6.3)_.
- Limite : 25 Mo par fichier _(à valider)_ ; formats PDF, DOCX, XLSX, images, EML.

## 3.8 Journal de dossier / main courante (S)

- Entrées horodatées : appel, e-mail, réunion, décision, envoi de courrier, note.
- Saisie en une ligne, sans quitter la vue dossier.
- **Journal non modifiable après 24 h** _(à valider)_ — un historique qu'on peut réécrire ne prouve rien.

## 3.9 Contacts / tiers (S)

- Avocat, notaire, commissaire de justice, greffe, expert-comptable, commissaire aux comptes, interlocuteur interne, contrepartie.
- Rattachement multiple aux dossiers et sociétés, avec le rôle tenu dans chaque dossier.
- Champs : nom, structure, fonction, e-mail, téléphone, adresse, note ; lien « écrire » (mailto pré-rempli avec la référence du dossier).

## 3.10 Import / export / interopérabilité (S)

| Fonction | Détail | Priorité |
|---|---|---|
| **Abonnement iCal** | URL secrète en lecture seule (jeton révocable) → ses échéances apparaissent dans Outlook/Google Agenda, sans double saisie | **S — fort effet de levier** |
| **Import CSV/XLSX initial** | Reprise du tableur existant : dossiers, sociétés, échéances, avec prévisualisation et rapport d'erreurs ligne à ligne | **M** (sinon l'outil démarre vide et ne sera pas adopté) |
| **Export CSV** | Toute liste filtrée | S |
| **Export PDF** | « Plan du mois » et « fiche dossier » | S |
| **Sauvegarde manuelle** | Bouton « exporter toutes mes données » (ZIP : JSON + documents) | S |

## 3.11 Paramètres (M)

- Compte : changement de mot de passe **hors périmètre applicatif** (il vit en variable d'environnement — la procédure est documentée dans `Techno.md` § 3.4).
- Préférences de rappel : heure d'envoi, paliers par défaut, activation du brief hebdo/mensuel, adresse e-mail de destination.
- Jours chômés personnels (congés) pris en compte par le moteur de délais.
- Gestion du référentiel : règles de délai, modèles de procédure, types de dossier, tags.
- Jeton iCal : afficher / révoquer / régénérer.

---

# 4. Parcours utilisateurs clés

### 4.1 Mettre une société sous suivi (M) — *2 minutes, une fois*
1. « Nouvelle société » → dénomination, forme, SIREN, **date de clôture**, CAC oui/non, mode de dépôt.
2. Dies propose immédiatement le **modèle d'année sociale** correspondant à la forme juridique.
3. Validation → **10 échéances datées** apparaissent, chacune avec sa base légale et son explication de calcul.
4. Les années suivantes se génèrent seules.

### 4.2 Sa journée (M) — *le premier écran du matin*
1. Elle ouvre Dies : la **vue Jour** affiche en haut le bandeau des échéances du jour, en dessous ses rendez-vous à l'heure, à côté son bloc-notes du jour.
2. Elle ajoute un rendez-vous en deux clics depuis un créneau libre, le rattache au dossier concerné.
3. À 14 h 30, l'AG se tient : elle ouvre l'événement, coche « tenue » → **l'échéance « AG d'approbation » passe à faite**, et les échéances qui en dépendent (PV, dépôt au greffe) sont datées automatiquement.

### 4.3 La boucle hebdomadaire (M) — *le vrai usage*
1. Lundi 8 h : e-mail « Votre semaine » (retards, cette semaine, 30 jours).
2. Un clic → tableau de bord.
3. Elle traite : marque faite (avec pièce jointe), reporte (avec motif), crée une échéance de suite.

### 4.4 Répondre à « qu'est-ce qui tombe en octobre ? » (M) — *5 secondes*
1. Vue mois → octobre.
2. Filtre par société si besoin.
3. Impression PDF ou export si elle doit en discuter avec quelqu'un.

### 4.5 Ouvrir un contentieux et sécuriser les délais (S)
1. Nouveau dossier `CONTENTIEUX`, saisie de la **date de signification** du jugement.
2. Application du modèle « suites d'un jugement de première instance ».
3. Délai d'appel, signification de la déclaration d'appel, conclusions : datés, avec report des week-ends et fériés, **et un avertissement rappelant que le délai de procédure doit être revérifié**.

### 4.6 Suivre un bail (S)
1. Dossier `BAIL_IMMOBILIER` : date d'effet, durée, périodicité triennale, préavis.
2. Dies calcule chaque **date limite de congé** (échéance triennale − 6 mois, reportée au jour ouvrable **précédent**) et alerte 9 mois avant pour laisser le temps de la décision.

---

# 5. Exigences non-fonctionnelles

## 5.1 Sécurité — l'exigence n° 1

L'application contient des données confidentielles d'entreprises tierces. Le niveau d'exigence est
supérieur à celui d'un outil personnel ordinaire.

| Exigence | Mise en œuvre |
|---|---|
| **Compte unique, non créable** | Identifiant + **hachage BCrypt** du mot de passe en variables d'environnement. Aucune table utilisateur, **aucune route d'inscription ni de réinitialisation par e-mail** |
| **Second facteur** _(S, recommandé)_ | TOTP (Google Authenticator / Authy), secret en variable d'environnement |
| **Session** | JWT court (30 min) + refresh en cookie `httpOnly` `SameSite=Strict` `Secure`, rotation, révocation au logout ; déconnexion après 30 min d'inactivité |
| **Anti-force brute** | 5 tentatives / 15 min par IP, temporisation progressive, journalisation de chaque tentative |
| **Invisibilité** | `X-Robots-Tag: noindex, nofollow`, `robots.txt` en interdiction totale, aucune page publique hormis l'écran de connexion |
| **Chiffrement au repos** | AES-256-GCM sur les champs sensibles (notes, journal, montants, contenus de documents) — clé `FIELD_ENCRYPTION_KEY` |
| **Transport** | HTTPS obligatoire, HSTS, CSP stricte, `frame-ancestors 'none'`, `Referrer-Policy: no-referrer` |
| **Journal d'accès** | Connexions réussies et échouées, actions sensibles (suppression, export, révocation de jeton) conservées 12 mois |
| **Sauvegardes** | Quotidiennes, **chiffrées**, hors du serveur applicatif, avec **test de restauration documenté et effectué** |
| **Hébergement** | Union européenne exclusivement (base, stockage de fichiers, e-mail) |
| **Journaux applicatifs** | Jamais de contenu de dossier, de nom de client ou de jeton dans les logs ; Sentry avec `send-default-pii: false` |

## 5.2 Confidentialité & RGPD

- L'utilisatrice est responsable de traitement pour ses données de suivi ; les personnes concernées sont
  les dirigeants, contreparties et contacts enregistrés.
- **Minimisation** : ne stocker que ce qui sert au suivi (pas de pièce d'identité, pas de RIB, pas de
  donnée sensible au sens de l'art. 9 RGPD).
- **Durée de conservation** : archivage d'un dossier clos, purge proposée au-delà de la durée de
  prescription applicable _(à valider : 5 ans par défaut, art. 2224 C. civ.)_.
- **Export intégral** disponible à tout moment (§ 3.10).
- Mention d'information à afficher aux contacts si des données leur sont demandées _(C)_.
- Si elle est **juriste d'entreprise**, tenir compte du régime de **confidentialité des consultations
  juridiques** (cf. `Referentiel-juridique.md` § 6.3) : marquage des documents concernés, traçabilité de
  l'auteur et de la date.

## 5.3 Performance & disponibilité

- Chargement des écrans clés **< 1,5 s** ; recherche globale **< 300 ms** (volumes très modestes : quelques centaines de dossiers, quelques milliers d'échéances).
- Pagination serveur sur toutes les listes ; agrégats du tableau de bord calculés en SQL.
- Disponibilité visée **99 %** — un usage bureautique, mais **les rappels doivent partir même si l'interface est indisponible** : le planificateur et l'envoi d'e-mails ne dépendent pas du front.
- Objectif de restauration : **RPO ≤ 24 h, RTO ≤ 4 h**.

## 5.4 UX & ergonomie

- **Densité maîtrisée** : elle travaille sur des listes, pas sur des tuiles ; l'information utile doit tenir à l'écran.
- **Zéro ambiguïté sur les dates** : format `JJ/MM/AAAA` partout, jour de la semaine affiché, compte à rebours (« dans 12 jours »), et **jamais** de format américain.
- **Saisie minimale** : dates au clavier (`31/12/25` accepté), champs pré-remplis par le contexte, création d'échéance depuis n'importe quelle vue.
- **Mobile** : consultation confortable (tableau de bord, vue mois, marquer une échéance faite) ; la saisie lourde reste desktop.
- **Accessibilité** : contrastes AA, focus visibles, cibles ≥ 44 px, **jamais d'information portée par la seule couleur** (une échéance en retard porte aussi une icône et un libellé).
- **Impression** : la vue mois et la fiche dossier doivent s'imprimer proprement (feuille de route papier).

## 5.5 Langue

Français exclusivement (UI, e-mails, exports). Le code reste en anglais (cf. `CLAUDE.md`).

---

# 6. Contraintes techniques

Référence complète : [`Techno.md`](./Techno.md). Résumé :

## 6.1 Stack
- **Frontend** : Angular 17+ standalone, PWA, TypeScript 5.4+.
- **Backend** : Spring Boot 3.2.x, Java 21, API REST `/api/v1`.
- **Base** : PostgreSQL 16, migrations **Liquibase** exclusivement.
- **E-mail** : Resend (ou SMTP) ; **planificateur** Spring `@Scheduled` + ShedLock.
- **Stockage documents** : S3-compatible (Scaleway / OVH / MinIO — UE) ou disque chiffré.

## 6.2 Architecture
```
        Navigateur (desktop + PWA mobile)
                    │  HTTPS
             Angular (Vercel)
                    │  REST /api/v1  (JWT + cookie httpOnly)
        Spring Boot ──── planificateur (rappels, génération annuelle)
             │                    │
        PostgreSQL            Resend (e-mail)
             │
        Stockage objet chiffré (documents)
```

## 6.3 Ce qui est délibérément **absent**
Pas de multi-tenant, pas de rôles, pas d'inscription, pas de webhooks entrants, pas de paiement, pas
d'OAuth tiers. **Chaque brique retirée est une vulnérabilité en moins et un écran de moins à maintenir.**

## 6.4 Authentification par variables d'environnement
`AUTH_USERNAME`, `AUTH_PASSWORD_HASH` (BCrypt coût 12 — **jamais le mot de passe en clair**),
`AUTH_TOTP_SECRET` (optionnel). L'application **refuse de démarrer en production** si ces variables sont
absentes, si le hachage n'est pas un hachage BCrypt valide, ou si un secret est resté à sa valeur de
développement. Détail : `Techno.md` § 3.4.

---

# 7. Modèle de données (conceptuel)

| Entité | Rôle |
|---|---|
| `Entite` | Société ou personne suivie (pilote les échéances récurrentes) |
| `Mandat` | Mandat social rattaché à une entité (dirigeant, CAC) avec date de fin |
| `Dossier` | Affaire, contrat, contentieux, opération |
| `Echeance` | Date à tenir, avec sa règle, sa criticité, son statut et sa preuve |
| `Evenement` | Rendez-vous, audience, assemblée, réunion : créneau horaire, lieu, participants, récurrence |
| `NoteJour` | Bloc-notes daté (la page d'agenda) |
| `CalendrierExterne` | Abonnement en lecture seule à un agenda Outlook / Google publié |
| `RegleDelai` | Règle de calcul réutilisable (référentiel : formule + base légale) |
| `ModeleProcedure` / `EtapeModele` | Checklist type générant des échéances |
| `Rappel` | Envoi planifié rattaché à une échéance et à un palier (idempotent) |
| `Document` | Pièce jointe chiffrée, rattachée à un dossier / une échéance |
| `Contact` | Tiers (avocat, greffe, CAC…) et son rôle par dossier |
| `EntreeJournal` | Main courante horodatée d'un dossier |
| `JourChome` | Jour férié ou congé personnel pris en compte par le moteur |
| `JournalAcces` | Traçabilité connexions et actions sensibles |
| `TempsPasse` _(C)_ | Temps consacré à un dossier |

Schéma détaillé (colonnes, contraintes, index) : [`Techno.md`](./Techno.md) § 3.2.

---

# 8. Règles de gestion structurantes

1. **Une échéance calculée explique toujours son calcul** (règle + base légale + report appliqué).
2. **Une date ajustée à la main n'est jamais écrasée** par un recalcul automatique.
3. **La génération récurrente est idempotente** : `UNIQUE (entite_id, exercice, code_echeance)`.
4. **Les rappels sont idempotents** : `UNIQUE (echeance_id, palier)` — un redémarrage ne renvoie rien deux fois.
5. **Un préavis se reporte vers l'avant** (jour ouvrable précédent), **un délai pour agir vers l'arrière** (jour ouvrable suivant, art. 642 CPC).
6. **Rien ne se supprime silencieusement** : suppression = confirmation explicite ; les dossiers se clôturent et s'archivent.
7. **Une échéance `BLOQUANTE` en retard reste visible en tête de tous les écrans** jusqu'à traitement.
8. **L'outil ne se substitue pas au raisonnement juridique** : un bandeau le rappelle sur les échéances de nature `JUDICIAIRE`.

---

# 9. Phasage / roadmap

| Lot | Contenu | Priorité |
|---|---|---|
| **Lot 0 — Socle** | Projet, CI, Liquibase, authentification mono-utilisateur par variables d'env, design system, layout | **M** |
| **Lot 1 — Dossiers & échéances** | CRUD dossiers, CRUD échéances manuelles, vue liste, vue dossier, tableau de bord | **M** |
| **Lot 2 — Moteur & référentiel** | `DeadlineEngine` testé, règles de délai, sociétés + année sociale, modèles de procédure, génération annuelle | **M** |
| **Lot 3 — Rappels & vues** | E-mails de rappel, brief du lundi, vue mois + liste du mois, import CSV initial | **M** |
| **Lot 4 — Agenda** | Événements (RDV, audiences, AG), vues Jour/Semaine/Mois unifiées, récurrence, notes de journée, lien événement ↔ échéance, calque de calendrier externe | **M** |
| **Lot 5 — Documents & journal** | Pièces jointes chiffrées, preuve de réalisation, main courante, contacts | **S** |
| **Lot 6 — Confort** | Abonnement iCal, recherche globale, exports PDF/CSV, push PWA, plan du mois | **S** |
| **Lot 7 — Ultérieur** | Suivi du temps, modèles de courriers/actes, second utilisateur, statistiques d'activité | **C/W** |

Découpage opérationnel prêt à donner à Claude Code : [`PLAN-DEVELOPPEMENT.md`](./PLAN-DEVELOPPEMENT.md).

---

# 10. Critères d'acceptation

| # | Critère | Vérification |
|---|---|---|
| 1 | Créer une société avec sa date de clôture génère l'année sociale complète, datée et sourcée | Test manuel + test d'intégration |
| 2 | Le moteur de délais est juste sur un jeu de 30 cas de référence (mois, jours francs, fériés, préavis) | **Tests unitaires obligatoires** |
| 3 | Un rappel n'est jamais envoyé deux fois, même après redémarrage | Test d'intégration sur l'idempotence |
| 4 | La vue mois affiche toutes les échéances du mois groupées par jour, filtrables et imprimables | Test manuel |
| 4 bis | La vue Jour affiche ensemble échéances et rendez-vous ; marquer un rendez-vous « tenu » met à jour l'échéance liée | Test manuel |
| 5 | Aucun accès n'est possible sans authentification (toute route non authentifiée renvoie 401) | Test de sécurité automatisé |
| 6 | L'application refuse de démarrer en production sans identifiants ni clé de chiffrement valides | Test de démarrage |
| 7 | Le tableur existant est importé sans perte, avec rapport d'erreurs | Recette avec le fichier réel |
| 8 | Une sauvegarde est restaurable — restauration réellement effectuée une fois | Procédure d'exploitation |

---

# 11. Risques & points d'attention

| Risque | Gravité | Mitigation |
|---|---|---|
| **Une règle de délai erronée dans le référentiel** donne une fausse sécurité | **Élevée** | Base légale affichée sur chaque règle · date de vérification · bandeau d'avertissement sur les délais judiciaires · règles éditables · l'outil rappelle, il ne décide pas |
| Les rappels ne partent pas (panne SMTP, planificateur arrêté) | Élevée | Supervision de l'envoi, e-mail de contrôle hebdomadaire même sans échéance (« tout est calme »), file d'envoi avec réessai, alerte Sentry sur échec |
| Fuite de données confidentielles | Élevée | Compte unique, 2FA, chiffrement au repos, hébergement UE, sauvegardes chiffrées, aucune indexation |
| Adoption : outil abandonné au profit du tableur | Moyenne | Import du tableur dès le lot 3, saisie ultra-rapide, abonnement iCal vers son agenda habituel |
| Évolution du droit (seuils, guichet unique, délais) | Moyenne | Référentiel en base, versionné et daté, revue annuelle inscrite comme échéance interne de l'outil lui-même |
| Perte du mot de passe (pas de réinitialisation par e-mail) | Faible | Procédure documentée de régénération du hachage + redéploiement ; consignation du mot de passe dans un gestionnaire |

---

# 12. Glossaire

- **Dies a quo / dies ad quem** : jour de départ / jour d'expiration d'un délai.
- **Jour franc** : délai dont ni le jour de départ ni le jour d'échéance ne comptent.
- **Jour ouvrable** : tous les jours sauf le jour de repos hebdomadaire (dimanche) et les jours fériés. **Jour ouvré** : du lundi au vendredi hors fériés. La distinction change la date : elle est explicite dans chaque règle.
- **Fait générateur** : événement qui fait courir le délai (clôture, signification, notification…).
- **AGO / AGE** : assemblée générale ordinaire / extraordinaire.
- **RCS** : registre du commerce et des sociétés. **Guichet unique** : portail INPI des formalités des entreprises.
- **RBE** : registre des bénéficiaires effectifs.
- **CAC** : commissaire aux comptes.
- **SHAL** : support habilité à recevoir des annonces légales.
- **Forclusion** : extinction du droit d'agir par expiration du délai.
- **Échéance / rendez-vous** : une échéance est une **date limite** calculée (déposer avant le 20/07) ; un rendez-vous est un **créneau horaire** (AG le 26/06 à 14 h 30). Dies gère les deux et les affiche ensemble.

---

*Cahier des charges Dies v1.0 — document vivant, à mettre à jour à chaque arbitrage de périmètre.*
