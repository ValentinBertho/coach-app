# Referentiel-juridique.md — Le cœur métier de **Dies**

> **Ce document est la spécification métier de l'application.** Il décrit le moteur de calcul des
> délais, le catalogue des règles livrées en base, et les modèles de procédure qui génèrent les
> échéances. C'est le document qui transforme Dies en outil de juriste plutôt qu'en simple agenda.

---

## 0. Avertissement — à lire avant tout développement

1. **Ce référentiel est un outil de travail, pas une consultation juridique.** Il rappelle des délais
   d'usage courant ; il ne dispense jamais de vérifier le texte applicable, les statuts de la société
   concernée ou le contrat en cause.
2. **État du droit retenu : droit français, sources connues jusqu'en mai 2026.** Chaque règle porte en
   base un champ `verifieLe`. Une échéance interne annuelle « revue du référentiel » est créée dans
   l'application elle-même (cf. § 3.7).
3. **Aucune règle n'est écrite en dur dans le code.** Le catalogue ci-dessous est un **jeu de données
   initial** (`seed` Liquibase) : chaque ligne devient un enregistrement `regle_delai` éditable depuis
   l'interface. Le code ne connaît que des formules génériques.
4. **Les délais de procédure (nature `JUDICIAIRE`) portent un avertissement permanent dans l'UI** :
   forclusion possible, computation à revérifier au cas d'espèce, délais de distance éventuels.
5. **Les statuts priment souvent sur le supplétif** (SAS notamment). Toute règle sensible aux statuts est
   marquée ⚠️ **statuts** et l'UI demande confirmation à l'application du modèle.
6. **Les règles marquées ⚠️ évolutif** portent sur des textes récemment modifiés (guichet unique,
   seuils comptables, réforme de la procédure d'appel) : à confirmer avant mise en service.

---

## 1. Vocabulaire du modèle

| Terme | Définition dans Dies |
|---|---|
| **Fait générateur** | Événement daté qui fait courir un délai : clôture d'exercice, tenue d'une AG, signification d'un jugement, date d'effet d'un bail, publication d'une demande de marque… |
| **Règle de délai** (`RegleDelai`) | Formule réutilisable : *quantité + unité + sens + report + base légale*. Ex. « + 6 mois après la clôture, report au jour ouvrable suivant, art. L.223-26 C. com. » |
| **Échéance** (`Echeance`) | Instance datée d'une règle, rattachée à un dossier et/ou une société |
| **Modèle de procédure** (`ModeleProcedure`) | Suite ordonnée d'étapes, chacune portant une règle, appliquée en une action à un dossier ou une société |
| **Palier de rappel** | Point d'alerte avant l'échéance (J-60, J-30, J-15, J-7, J-2, J-0, retard) |

---

## 2. Moteur de calcul des délais (`DeadlineEngine`)

> Service backend **pur** (aucune dépendance base ni réseau hormis le calendrier des jours chômés),
> **entièrement testé unitairement**. C'est la pièce dont la justesse conditionne la confiance dans
> l'outil : elle est spécifiée ici au cas près.

### 2.1 Signature

```java
LocalDate calculer(LocalDate faitGenerateur, RegleDelai regle, CalendrierChome calendrier);
```

`RegleDelai` porte : `quantite`, `unite`, `sens`, `reportWeekEndFerie`, `delaiDistance`, `baseLegale`,
`criticiteParDefaut`, `paliersRappel`, `verifieLe`.

### 2.2 Unités (`UniteDelai`)

| Unité | Règle de calcul | Référence |
|---|---|---|
| `JOURS_CALENDAIRES` | Le jour du fait générateur ne compte pas (`dies a quo` exclu) ; le délai expire le dernier jour à 24 h | art. 641 al. 1 CPC |
| `JOURS_FRANCS` | Ni le jour du fait générateur ni le jour d'expiration ne comptent → `+ quantite + 1` jour | usage procédural |
| `JOURS_OUVRABLES` | Tous les jours sauf **dimanches et jours fériés** | droit du travail / usage |
| `JOURS_OUVRES` | **Lundi → vendredi**, hors jours fériés et jours chômés personnels | usage contractuel |
| `MOIS` | Quantième à quantième ; **à défaut de quantième identique, le dernier jour du mois** (31 janvier + 1 mois = 28/29 février) | art. 641 al. 2 CPC |
| `ANNEES` | Même règle que `MOIS` (29 février + 1 an = 28 février) | art. 641 al. 2 CPC |

### 2.3 Sens (`SensDelai`) — la subtilité qui évite les erreurs

| Sens | Usage | Report si le résultat tombe un jour non ouvrable |
|---|---|---|
| `APRES` | Délai **pour agir** à compter d'un fait (dépôt, recours, déclaration) | **Jour ouvrable suivant** — art. 642 al. 2 CPC : « le délai qui expirerait normalement un samedi, un dimanche ou un jour férié ou chômé est prorogé jusqu'au premier jour ouvrable suivant » |
| `AVANT` | **Préavis** ou formalité préalable (congé de bail, convocation d'AG, mise à disposition de documents) | **Jour ouvrable précédent** — un préavis tardif est nul ; on n'utilise jamais la prorogation pour repousser un préavis |
| `AUCUN` | Date fixe (date anniversaire, date contractuelle) | Aucun report, l'échéance reste à sa date |

> **Cette asymétrie est la règle de gestion la plus importante du moteur.** L'implémenter à l'envers
> ferait perdre un délai de congé. Elle est couverte par les cas de test T14 à T18 (§ 5).

### 2.4 Jours fériés (France métropolitaine)

Fixes : 1er janvier · 1er mai · 8 mai · 14 juillet · 15 août · 1er novembre · 11 novembre · 25 décembre.
Mobiles, calculés depuis **Pâques** (algorithme de Meeus/Butcher, calendrier grégorien) :
lundi de Pâques (`Pâques + 1`), Ascension (`Pâques + 39`), lundi de Pentecôte (`Pâques + 50`).

Options de configuration :
- **Alsace-Moselle** : Vendredi saint (`Pâques − 2`) et 26 décembre — activable dans les paramètres.
- **Jours chômés personnels** : congés de l'utilisatrice, saisis dans les paramètres, pris en compte
  uniquement pour les unités `JOURS_OUVRES` et pour les alertes internes — **jamais** pour proroger un
  délai légal (un congé personnel n'est pas un jour chômé au sens de l'art. 642 CPC : ce point est
  explicité dans l'UI).

### 2.5 Délais de distance (art. 643 CPC)

Ajoutés **avant** application du report : `+1 mois` (Guadeloupe, Guyane, Martinique, La Réunion, Mayotte,
Saint-Barthélemy, Saint-Martin, Saint-Pierre-et-Miquelon, Polynésie française, Nouvelle-Calédonie,
Wallis-et-Futuna, Terres australes), `+2 mois` (étranger). Champ optionnel sur l'échéance, jamais activé
par défaut.

### 2.6 Ordre d'application (impératif)

```
1. Point de départ = fait générateur
2. Appliquer quantité + unité (dies a quo exclu pour les jours)
3. Ajouter le délai de distance éventuel
4. Appliquer le report selon le sens (suivant / précédent / aucun)
5. Journaliser la trace de calcul lisible, stockée avec l'échéance
```

**Trace de calcul** (affichée dans l'UI, stockée en base) :
> `Clôture 31/12/2025` + `6 mois` → `30/06/2026` · report : aucun (mardi ouvrable) · base :
> art. L.223-26 C. com. · règle vérifiée le 01/06/2026.

---

## 3. Catalogue des règles de délai

Format des tables : **Code** (identifiant stable en base) · **Échéance** · **Formule** · **Base** ·
**Criticité**. Les rappels par défaut découlent de la criticité (cf. `Cahier-des-charges.md` § 3.3).

### 3.1 Approbation et dépôt des comptes annuels — le socle du suivi

| Code | Échéance | Formule | Base | Criticité |
|---|---|---|---|---|
| `SOC_ARRETE_COMPTES` | Arrêté des comptes par l'organe compétent (gérance / conseil / président) | Clôture **+ 3 mois** _(usage, à ajuster)_ | Statuts · L.232-1 C. com. | IMPORTANTE |
| `SOC_DOC_CAC` | Mise à disposition des comptes au commissaire aux comptes | AG **− 45 jours** ⚠️ **statuts / forme** | R.225-89 C. com. (SA) et pratique | IMPORTANTE |
| `SOC_RAPPORT_CAC` | Réception du rapport du CAC | AG **− 15 jours** | L.823-9 s. C. com. | IMPORTANTE |
| `SOC_CONVOC_SARL` | Convocation des associés (LRAR) + communication des documents | AG **− 15 jours** | L.223-26, R.223-18 C. com. | **BLOQUANTE** |
| `SOC_CONVOC_SA` | Convocation des actionnaires (1re convocation) | AG **− 15 jours** (10 j sur 2e convocation) | R.225-69 C. com. | **BLOQUANTE** |
| `SOC_COMM_DOC_SA` | Documents tenus à disposition des actionnaires | AG **− 15 jours** | R.225-89 C. com. | IMPORTANTE |
| `SOC_AG_APPRO` | **Tenue de l'AG d'approbation des comptes** | Clôture **+ 6 mois** | L.223-26 (SARL) · L.225-100 (SA) · SAS : renvoi L.227-1 ⚠️ **statuts** | **BLOQUANTE** |
| `SOC_AG_ALERTE` | Alerte interne « AG non tenue » | Clôture **+ 5 mois** | — (interne) | IMPORTANTE |
| `SOC_PROROG_AG` | Requête en prolongation du délai d'AG (président du tribunal de commerce) | Clôture **+ 5 mois** au plus tard | L.223-26 · L.225-100 C. com. | CONFORT |
| `SOC_PV_REGISTRE` | PV signé, feuille de présence, retranscription au registre des délibérations | AG **+ 15 jours** _(interne)_ | R.221-3 s. · pratique | IMPORTANTE |
| `SOC_DEPOT_PAPIER` | **Dépôt des comptes au RCS** (voie papier) | AG **+ 1 mois** | R.123-111 C. com. | **BLOQUANTE** |
| `SOC_DEPOT_ELEC` | **Dépôt des comptes au RCS** (voie électronique) | AG **+ 2 mois** | R.123-111 C. com. | **BLOQUANTE** |
| `SOC_CONFIDENTIALITE` | Déclaration de confidentialité jointe au dépôt (micro / petite entreprise) | Même date que le dépôt | L.232-25 C. com. ⚠️ **évolutif** | IMPORTANTE |
| `SOC_DIVIDENDE` | Mise en paiement du dividende voté | Clôture **+ 9 mois** | L.232-13 al. 2 C. com. | **BLOQUANTE** |
| `SOC_PERTE_CAPITAL` | AGE sur la poursuite d'activité (capitaux propres < moitié du capital) | Approbation des comptes **+ 4 mois** | L.223-42 (SARL) · L.225-248 (SA) ⚠️ **évolutif** (loi 9 mars 2023) | **BLOQUANTE** |

**Notes de mise en œuvre**

- Le choix `SOC_DEPOT_PAPIER` / `SOC_DEPOT_ELEC` est déduit du champ *mode de dépôt* de la société : **une seule des deux échéances est générée**.
- Les formalités passent depuis 2023 par le **guichet unique des formalités des entreprises (INPI)**, qui les transmet au greffe ⚠️ **évolutif** : le libellé de l'échéance mentionne le canal, la règle de délai reste celle de l'art. R.123-111.
- **Sanction du défaut de dépôt** — affichée en aide contextuelle : amende (art. R.247-3 C. com.) et injonction de déposer sous astreinte prononcée par le président du tribunal de commerce (art. L.611-2 II C. com.).
- **Seuils de taille** (micro / petite / moyenne) conditionnant la confidentialité : relevés par le **décret n° 2024-152 du 28 février 2024** ⚠️ **évolutif** — ordres de grandeur à revérifier avant paramétrage : micro ≈ 450 k€ de bilan / 900 k€ de CA / 10 salariés ; petite ≈ 7,5 M€ / 15 M€ / 50 salariés ; moyenne ≈ 25 M€ / 50 M€ / 250 salariés. **Champ `verifieLe` obligatoire sur ces règles.**

### 3.2 Vie sociale courante

| Code | Échéance | Formule | Base | Criticité |
|---|---|---|---|---|
| `SOC_RCS_MODIF` | Déclaration modificative au RCS (dirigeant, siège, objet, capital…) | Modification **+ 1 mois** | R.123-66 C. com. | **BLOQUANTE** |
| `SOC_ANNONCE_LEGALE` | Publication au support d'annonces légales (modification statutaire) | Décision **+ 1 mois** | R.210-9 C. com. et pratique | IMPORTANTE |
| `SOC_RBE` | Mise à jour du registre des bénéficiaires effectifs | Fait générateur **+ 30 jours** | R.561-55 CMF | **BLOQUANTE** |
| `SOC_MANDAT_DIRIGEANT` | Renouvellement d'un mandat social | Date de fin de mandat, alerte **− 3 mois** | Statuts · L.225-18 C. com. (6 ans max, SA) | IMPORTANTE |
| `SOC_MANDAT_CAC` | Renouvellement du mandat du commissaire aux comptes | AG statuant sur les comptes du **6e exercice** | L.823-3 C. com. | **BLOQUANTE** |
| `SOC_CONV_REGLEMENTEE` | Rapport spécial sur les conventions réglementées et approbation | AG d'approbation | L.223-19 (SARL) · L.225-38 s. (SA) · L.227-10 (SAS) | IMPORTANTE |
| `SOC_REGISTRE_TITRES` | Mise à jour du registre des mouvements de titres et des comptes d'actionnaires | Opération **+ 8 jours** _(interne)_ | Pratique | IMPORTANTE |
| `SOC_SEUIL_CAC` | Vérification annuelle du franchissement des seuils de nomination d'un CAC | Clôture **+ 3 mois** | L.227-9-1 · L.223-35 C. com. ⚠️ **évolutif** (loi PACTE) | IMPORTANTE |

### 3.3 Fiscal — **suivi indicatif** (relève de l'expert-comptable)

> Ces échéances sont livrées **désactivées par défaut**. Elles servent de repère de coordination, pas de
> substitut au calendrier fiscal du cabinet comptable. Libellé UI : *« indicatif — à confirmer auprès de
> l'expert-comptable »*.

| Code | Échéance | Formule | Criticité |
|---|---|---|---|
| `FISC_RESULTAT_IS` | Déclaration de résultats (IS) | Clôture **+ 3 mois** ; exercice clos au 31/12 → **2e jour ouvré suivant le 1er mai** | IMPORTANTE |
| `FISC_DAS2` | Déclaration des honoraires (DAS2) | Avec la déclaration de résultats | CONFORT |
| `FISC_CFE_CVAE` | Échéances CFE / CVAE ⚠️ **évolutif** (extinction progressive de la CVAE) | Calendrier fiscal | CONFORT |

### 3.4 Baux commerciaux

| Code | Échéance | Formule | Base | Criticité |
|---|---|---|---|---|
| `BAIL_CONGE_TRIENNAL_PRE` | **Décision** de donner ou non congé à l'échéance triennale | Échéance triennale **− 9 mois** _(interne)_ | — | IMPORTANTE |
| `BAIL_CONGE_TRIENNAL` | **Date limite de délivrance du congé triennal par le preneur** (LRAR **ou** acte de commissaire de justice) | Échéance triennale **− 6 mois**, report **jour ouvrable précédent** | L.145-4 C. com. | **BLOQUANTE** |
| `BAIL_CONGE_BAILLEUR` | Congé donné par le bailleur — **par acte extrajudiciaire uniquement** | Échéance **− 6 mois** | L.145-9 C. com. | **BLOQUANTE** |
| `BAIL_DEMANDE_RENOUV` | Demande de renouvellement par le preneur | Expiration **− 6 mois** (ou à tout moment pendant la reconduction) | L.145-10 C. com. | **BLOQUANTE** |
| `BAIL_REPONSE_BAILLEUR` | Réponse du bailleur à la demande de renouvellement — **silence = acceptation** | Demande **+ 3 mois** | L.145-10 C. com. | **BLOQUANTE** |
| `BAIL_REVISION_LOYER` | Demande de révision triennale du loyer | Date d'effet **+ 3 ans** | L.145-38 C. com. | IMPORTANTE |
| `BAIL_INDEXATION` | Indexation annuelle (ILC / ILAT) | Date anniversaire | Clause contractuelle · L.112-2 CMF | IMPORTANTE |
| `BAIL_PRESCRIPTION` | Alerte prescription biennale des actions nées du bail | Fait générateur **+ 2 ans**, alerte **− 3 mois** | L.145-60 C. com. | **BLOQUANTE** |

> **Point d'expertise à ne pas perdre à l'implémentation** : le congé du **preneur** peut être donné par
> LRAR ou acte extrajudiciaire (L.145-4), celui du **bailleur** doit l'être par **acte extrajudiciaire**
> (L.145-9). Le libellé de chaque échéance porte le mode de délivrance : c'est là que se perdent les
> dossiers.

### 3.5 Contrats

| Code | Échéance | Formule | Criticité |
|---|---|---|---|
| `CTR_FIN_DUREE` | Terme du contrat | Date d'effet **+ durée** | IMPORTANTE |
| `CTR_LIMITE_DENONCIATION` | **Date limite de dénonciation** (tacite reconduction) | Terme **− préavis contractuel**, report **jour ouvrable précédent** | **BLOQUANTE** |
| `CTR_DECISION_RENOUV` | Décision interne de renouveler ou non | Date limite de dénonciation **− 1 mois** | IMPORTANTE |
| `CTR_REVISION_PRIX` | Révision / indexation annuelle | Date anniversaire | CONFORT |
| `CTR_JALON` | Jalon contractuel (livraison, audit, rapport, garantie) | Date saisie | Selon le contrat |
| `CTR_PRESCRIPTION` | Alerte prescription quinquennale de droit commun | Fait générateur **+ 5 ans**, alerte **− 6 mois** (art. 2224 C. civ. · L.110-4 C. com.) | **BLOQUANTE** |

### 3.6 Contentieux et procédure ⚠️ nature `JUDICIAIRE` — avertissement permanent

| Code | Échéance | Formule | Base | Criticité |
|---|---|---|---|---|
| `PROC_APPEL` | Appel d'un jugement (matière contentieuse) | Signification **+ 1 mois** | art. 538 CPC | **BLOQUANTE** |
| `PROC_APPEL_GRACIEUX` | Appel en matière gracieuse | Notification **+ 15 jours** | art. 538 CPC | **BLOQUANTE** |
| `PROC_APPEL_REFERE` | Appel d'une ordonnance de référé | Signification **+ 15 jours** | art. 490 CPC | **BLOQUANTE** |
| `PROC_SIGNIF_DA` | Signification de la déclaration d'appel à l'intimé non constitué | Avis du greffe **+ 10 jours** ⚠️ **évolutif** | art. 902 CPC | **BLOQUANTE** |
| `PROC_CONCLU_APPELANT` | Conclusions de l'appelant | Déclaration d'appel **+ 3 mois** ⚠️ **évolutif** (décret n° 2023-1391 du 29 déc. 2023, procédure d'appel réformée) | art. 908 CPC | **BLOQUANTE** |
| `PROC_CONCLU_INTIME` | Conclusions de l'intimé | Notification des conclusions de l'appelant **+ 3 mois** ⚠️ **évolutif** | art. 909 CPC | **BLOQUANTE** |
| `PROC_POURVOI` | Pourvoi en cassation | Signification **+ 2 mois** | art. 612 CPC | **BLOQUANTE** |
| `PROC_OPPOSITION_IP` | Opposition à injonction de payer | Signification **+ 1 mois** | art. 1416 CPC | **BLOQUANTE** |
| `PROC_RECOURS_ADMIN` | Recours contentieux administratif | Notification **+ 2 mois** | art. R.421-1 CJA | **BLOQUANTE** |
| `PROC_PRUDH_LICENCIEMENT` | Contestation de la rupture du contrat de travail | Rupture **+ 12 mois** | L.1471-1 C. trav. | **BLOQUANTE** |
| `PROC_PRUDH_SALAIRES` | Action en paiement de salaires | Exigibilité **+ 3 ans** | L.3245-1 C. trav. | **BLOQUANTE** |
| `PROC_AUDIENCE` | Audience / plaidoirie | Date saisie, alerte **− 15 jours** et **− 3 jours** | — | **BLOQUANTE** |
| `PROC_PIECES` | Communication de pièces / calendrier de mise en état | Date fixée par le juge | art. 780 s. CPC | **BLOQUANTE** |

> **Bandeau UI obligatoire sur ces échéances** :
> *« Délai de procédure : vérifiez le point de départ (signification ou notification), l'éventuel délai
> de distance et la computation applicable. Dies rappelle, il ne calcule pas à votre place. »*

### 3.7 Propriété industrielle

| Code | Échéance | Formule | Base | Criticité |
|---|---|---|---|---|
| `PI_RENOUV_MARQUE` | Renouvellement d'une marque française ou UE | Dépôt **+ 10 ans** ; demande possible dans les **6 mois précédant** l'expiration | L.712-9, R.712-24 CPI | **BLOQUANTE** |
| `PI_RENOUV_GRACE` | Délai de grâce de renouvellement (avec surtaxe) | Expiration **+ 6 mois** | L.712-9 CPI | **BLOQUANTE** |
| `PI_ALERTE_RENOUV` | Alerte interne de renouvellement | Expiration **− 12 mois** | — | IMPORTANTE |
| `PI_OPPOSITION` | Opposition à une demande d'enregistrement | Publication **+ 2 mois** | L.712-4 CPI | **BLOQUANTE** |
| `PI_USAGE_SERIEUX` | Revue d'usage sérieux (risque de déchéance) | Enregistrement **+ 5 ans**, puis annuel | L.714-5 CPI | IMPORTANTE |
| `PI_ANNUITE_BREVET` | Paiement de l'annuité de brevet | Date anniversaire du dépôt (+ grâce 6 mois avec surtaxe) | L.612-19, R.613-46 CPI | **BLOQUANTE** |

### 3.8 Conformité / données personnelles

| Code | Échéance | Formule | Base | Criticité |
|---|---|---|---|---|
| `RGPD_VIOLATION_CNIL` | Notification d'une violation de données à la CNIL | Découverte **+ 72 heures** | art. 33 RGPD | **BLOQUANTE** |
| `RGPD_VIOLATION_PERSONNES` | Information des personnes concernées | Dans les meilleurs délais | art. 34 RGPD | **BLOQUANTE** |
| `RGPD_DEMANDE_DROITS` | Réponse à une demande d'exercice de droits | Réception **+ 1 mois** (prolongeable de 2 mois, avec information dans le mois) | art. 12.3 RGPD | **BLOQUANTE** |
| `RGPD_REGISTRE` | Revue annuelle du registre des traitements | Annuel | art. 30 RGPD | IMPORTANTE |
| `RGPD_AIPD` | Réexamen d'une analyse d'impact | Annuel ou en cas d'évolution | art. 35 RGPD | IMPORTANTE |
| `RGPD_SOUS_TRAITANTS` | Revue des contrats de sous-traitance | Annuel | art. 28 RGPD | CONFORT |

### 3.9 Échéances internes de l'outil

| Code | Échéance | Formule | Criticité |
|---|---|---|---|
| `INT_REVUE_REFERENTIEL` | **Revue annuelle du référentiel de délais** (vérifier les textes, mettre à jour `verifieLe`) | Annuel, 15 janvier | IMPORTANTE |
| `INT_TEST_RESTAURATION` | Test de restauration d'une sauvegarde | Semestriel | IMPORTANTE |
| `INT_REVUE_DOSSIERS` | Revue des dossiers sans échéance à venir | Mensuel | CONFORT |

---

## 4. Modèles de procédure livrés

Chaque modèle = un fait générateur + une liste ordonnée d'étapes référençant les codes ci-dessus.
L'application d'un modèle demande le fait générateur, propose les dates calculées, **laisse ajuster
avant création**, puis génère les échéances.

### 4.1 `MOD_COMPTES_SARL` — Approbation des comptes annuels d'une SARL
Fait générateur : **date de clôture de l'exercice**.

| # | Étape | Règle |
|---|---|---|
| 1 | Demander les comptes à l'expert-comptable | Clôture + 2 mois _(interne)_ |
| 2 | Arrêté des comptes par la gérance + rapport de gestion _(dispense possible pour les petites entreprises — L.232-1 IV)_ | `SOC_ARRETE_COMPTES` |
| 3 | Transmission au CAC le cas échéant | `SOC_DOC_CAC` |
| 4 | Rapport du CAC | `SOC_RAPPORT_CAC` |
| 5 | **Convocation des associés + envoi des documents (15 j)** | `SOC_CONVOC_SARL` |
| 6 | **Tenue de l'AGO (6 mois)** | `SOC_AG_APPRO` |
| 7 | PV, feuille de présence, registre des délibérations | `SOC_PV_REGISTRE` |
| 8 | **Dépôt des comptes au RCS** (1 ou 2 mois selon le canal) + déclaration de confidentialité | `SOC_DEPOT_*` + `SOC_CONFIDENTIALITE` |
| 9 | Mise en paiement du dividende éventuel (9 mois) | `SOC_DIVIDENDE` |
| 10 | Si capitaux propres < moitié du capital : AGE (4 mois) | `SOC_PERTE_CAPITAL` |

### 4.2 `MOD_COMPTES_SA` — Approbation des comptes d'une SA
Variantes : arrêté des comptes par le **conseil d'administration**, convocation `SOC_CONVOC_SA`
(15 j / 10 j sur 2e convocation), droit de communication `SOC_COMM_DOC_SA`, rapport de gestion et
rapport spécial du CAC sur les conventions réglementées.

### 4.3 `MOD_COMPTES_SAS` — Approbation des comptes d'une SAS ⚠️ **statuts**
Mêmes jalons, mais **modalités et délais de consultation fixés par les statuts**. À l'application du
modèle, l'UI demande explicitement : *« Vérifiez les statuts : délai de convocation, forme de la
consultation, majorité. »* Le délai de 6 mois est retenu par renvoi de l'art. L.227-1 aux règles de la SA.

### 4.4 `MOD_CESSION_PARTS` — Cession de parts sociales (SARL)
Agrément des associés (procédure et délais statutaires · L.223-14 C. com. : notification, consultation,
délai de 3 mois pour statuer, à défaut agrément réputé acquis) → acte de cession → signification à la
société ou dépôt d'un original au siège → enregistrement fiscal → mise à jour des statuts → dépôt au
RCS → mise à jour du RBE (30 j).

### 4.5 `MOD_TRANSFERT_SIEGE` — Transfert de siège social
Décision de l'organe compétent → publication au SHAL (1 mois) → déclaration modificative au RCS (1 mois,
`SOC_RCS_MODIF`) → mise à jour des statuts et des mentions légales → information des tiers (banque,
assureur, bailleur).

### 4.6 `MOD_CHANGEMENT_DIRIGEANT` — Changement de dirigeant
Décision → PV → déclaration au RCS (1 mois) → publication → mise à jour du RBE si le nouveau dirigeant
est bénéficiaire effectif (30 j) → révocation des pouvoirs bancaires et des délégations.

### 4.7 `MOD_BAIL_TRIENNAL` — Échéance triennale d'un bail commercial
Fait générateur : **date d'effet du bail**. Génère, pour chaque période triennale : alerte de décision
(− 9 mois), **date limite de congé** (− 6 mois, report vers l'arrière), et l'échéance triennale
elle-même. Mentionne le mode de délivrance selon l'auteur du congé (§ 3.4).

### 4.8 `MOD_BAIL_RENOUVELLEMENT` — Renouvellement d'un bail commercial
Demande de renouvellement (− 6 mois) → réponse du bailleur (+ 3 mois, **silence = acceptation**) →
négociation du loyer → alerte prescription biennale.

### 4.9 `MOD_MARQUE` — Cycle de vie d'une marque
Dépôt → opposition (2 mois après publication) → enregistrement → revue d'usage (5 ans) → alerte
renouvellement (− 12 mois) → **renouvellement (10 ans)** → délai de grâce (+ 6 mois, surtaxe).

### 4.10 `MOD_SUITES_JUGEMENT` — Suites d'un jugement de première instance ⚠️ `JUDICIAIRE`
Fait générateur : **date de signification**. Analyse de l'opportunité d'un recours (+ 8 j, interne) →
**délai d'appel** (1 mois, ou 15 j en référé) → signification de la déclaration d'appel (10 j) →
conclusions de l'appelant (3 mois) → conclusions de l'intimé (3 mois) → exécution provisoire /
signification / recouvrement.

### 4.11 `MOD_VIOLATION_DONNEES` — Violation de données personnelles
Qualification (immédiat) → **notification CNIL (72 h)** → information des personnes concernées →
inscription au registre des violations → plan de remédiation (30 j).

### 4.12 `MOD_ANNEE_SOCIALE` — Modèle composite appliqué automatiquement
Assemble le modèle d'approbation correspondant à la forme juridique + les échéances récurrentes de la
société (mandats arrivant à terme, seuils CAC, fiscal si activé). **C'est ce modèle que le job annuel
applique tout seul, de façon idempotente.**

---

## 5. Cas de test de référence du moteur (obligatoires)

> Ces cas sont à écrire **avant** l'implémentation du moteur. Ils constituent le critère
> d'acceptation n° 2 du cahier des charges.

| # | Cas | Entrée | Attendu |
|---|---|---|---|
| T01 | Mois simple | 31/12/2025 + 6 mois, `APRES` | 30/06/2026 |
| T02 | Quantième inexistant | 31/01/2026 + 1 mois | 28/02/2026 |
| T03 | Année bissextile | 29/02/2024 + 1 an | 28/02/2025 |
| T04 | Jours calendaires, `dies a quo` exclu | 10/03/2026 + 30 jours | 09/04/2026 |
| T05 | Jours francs | 10/03/2026 + 8 jours francs | 19/03/2026 |
| T06 | Jours ouvrés | 10/03/2026 (mardi) + 5 jours ouvrés | 17/03/2026 |
| T07 | Jours ouvrables (dimanche exclu) | 10/03/2026 + 5 jours ouvrables | 16/03/2026 |
| T08 | Report samedi → lundi | échéance calculée un samedi, `APRES` | lundi suivant |
| T09 | Report dimanche → lundi | échéance un dimanche, `APRES` | lundi suivant |
| T10 | Report jour férié | échéance le 01/05, `APRES` | 02/05 (ou jour ouvrable suivant) |
| T11 | Report en cascade | échéance le samedi précédant un lundi férié | mardi |
| T12 | Fête mobile | Pâques 2026 = 05/04/2026 → lundi de Pâques 06/04, Ascension 14/05, Pentecôte 25/05 | calcul exact |
| T13 | Fête mobile, autre année | Pâques 2027 = 28/03/2027 | calcul exact |
| T14 | **Préavis, report arrière** | échéance triennale 30/06/2026 − 6 mois = 30/12/2025 (mardi) | 30/12/2025 |
| T15 | **Préavis tombant un dimanche** | résultat = dimanche, `AVANT` | **vendredi précédent**, jamais le lundi suivant |
| T16 | **Préavis tombant le 1er janvier** | résultat = 01/01, `AVANT` | 31/12 si ouvrable, sinon jour ouvrable antérieur |
| T17 | Convocation d'AG | AG le 26/06/2026 − 15 jours, `AVANT` | 11/06/2026 |
| T18 | Convocation, report arrière | résultat = samedi | vendredi précédent |
| T19 | Délai de distance étranger | signification 10/03/2026 + 1 mois + 2 mois | 10/06/2026 (puis report) |
| T20 | Délai de distance outre-mer | + 1 mois | conforme art. 643 CPC |
| T21 | Sens `AUCUN` | date anniversaire un dimanche | inchangée |
| T22 | Dépôt papier | AG 20/06/2026 + 1 mois | 20/07/2026 |
| T23 | Dépôt électronique | AG 20/06/2026 + 2 mois | 20/08/2026 |
| T24 | Dividende | clôture 31/12/2025 + 9 mois | 30/09/2026 |
| T25 | RBE | fait 15/06/2026 + 30 jours | 15/07/2026 |
| T26 | Marque | dépôt 12/09/2016 + 10 ans | 12/09/2026, ouverture du renouvellement au 12/03/2026 |
| T27 | Grâce marque | expiration 12/09/2026 + 6 mois | 12/03/2027 |
| T28 | 72 heures RGPD | découverte 30/04/2026 18 h + 72 h | 03/05/2026 18 h — **heures, pas jours** |
| T29 | Jour chômé personnel | congé posé, unité `JOURS_OUVRES` | exclu du décompte |
| T30 | Jour chômé personnel sur délai légal | congé posé, unité `JOURS_CALENDAIRES` | **sans effet** — un congé ne proroge pas un délai légal |

> **T15, T16, T18 et T30 sont les tests qui protègent des erreurs coûteuses.** Ils ne doivent jamais
> être supprimés ni assouplis.

---

## 6. Points de vigilance juridiques et produit

### 6.1 Ce que l'outil ne fait pas
Il ne qualifie pas juridiquement une situation, ne choisit pas le point de départ d'un délai (signification
ou notification ? date de réception ou de première présentation ?), et ne connaît pas les statuts d'une
société tant qu'ils ne lui ont pas été renseignés. **Il rappelle, il trace, il calcule une formule
explicite.** L'aide en ligne le dit dans ces termes.

### 6.2 Traçabilité de la source
Chaque règle porte `baseLegale` (texte), `sourceUrl` (Légifrance), `verifieLe` (date). L'interface affiche
la date de vérification à côté de toute échéance calculée ; une règle non vérifiée depuis plus de 18 mois
est signalée par un pictogramme d'alerte dans le référentiel.

### 6.3 Confidentialité des consultations juridiques _(à valider selon son statut)_
Si l'utilisatrice est **juriste d'entreprise**, la loi n° 2023-1059 du 20 novembre 2023 (art. 58-1 de la
loi n° 71-1130) a instauré un régime de **confidentialité des consultations juridiques** rédigées par les
juristes d'entreprise, sous conditions (formation, mention apparente, traçabilité, matières couvertes,
exclusions notamment pénales et fiscales pénales) ⚠️ **évolutif — vérifier le décret d'application et le
champ exact avant de s'en prévaloir**.

Conséquence produit, si le régime s'applique : le module documents propose un marquage
**« Confidentiel — consultation juridique »**, avec auteur, date et horodatage inaltérable de l'entrée
au journal. Si elle exerce **en cabinet d'avocats**, c'est le **secret professionnel de l'avocat** qui
s'applique — mêmes exigences techniques, motif juridique différent. Dans les deux cas, la conclusion
technique est identique : **chiffrement au repos, journal d'accès, aucune donnée de dossier dans les logs
ni dans l'outil de supervision.**

### 6.4 Conservation
Durée par défaut proposée : durée de la prescription applicable au dossier, **5 ans** à défaut
(art. 2224 C. civ.), portée à **10 ans** pour les dossiers sociaux (registres, PV, comptes — obligations
comptables et sociales). Purge proposée, jamais automatique : **rien ne se supprime sans validation
explicite.**

---

*Referentiel-juridique.md v1.0 — révision annuelle obligatoire (échéance `INT_REVUE_REFERENTIEL`).
Toute modification d'une règle doit mettre à jour son champ `verifieLe`.*
