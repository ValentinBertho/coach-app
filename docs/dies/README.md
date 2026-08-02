# **Dies** — dossier de spécifications

> Application web **privée et mono-utilisatrice** de suivi des dossiers et des échéances juridiques.
> Ce dossier contient tout ce qu'il faut pour lancer les développements avec Claude Code, sans rien
> devoir réexpliquer.

---

## 1. Le produit en quelques lignes

Une juriste suit un portefeuille de dossiers dont chacun porte des **dates non négociables** : tenir
l'assemblée d'approbation des comptes dans les six mois de la clôture, déposer les comptes au greffe dans
le mois qui suit, dénoncer un bail six mois avant l'échéance triennale, renouveler une marque, respecter
un délai d'appel. **Dies calcule ces dates, les génère automatiquement chaque année, et la prévient
avant** — par e-mail, dans une vue mensuelle, et dans son agenda habituel. Il tient aussi **son agenda
de travail** : rendez-vous, audiences, assemblées, réunions, avec horaires et lieux, affichés dans le
même calendrier que les échéances (vues Jour / Semaine / Mois).

**Nom de code : *Dies*** — du latin des délais de procédure (*dies a quo*, *dies ad quem* : le jour de
départ, le jour d'échéance). Court, prononçable, propre à un usage professionnel.
Alternatives si le nom ne plaît pas : **Quantième**, **Échéancier**, **Greffe**, **Ad Quem**.
Le nom n'est utilisé que dans les libellés et le nom du dépôt : le changer coûte cinq minutes.

---

## 2. Les documents, et ce que chacun tranche

| Document | Ce qu'il tranche | À lire par |
|---|---|---|
| [`Cahier-des-charges.md`](./Cahier-des-charges.md) | **Le périmètre** : fonctions, parcours, exigences non fonctionnelles, priorisation MoSCoW, critères d'acceptation | Tout le monde, en premier |
| [`Referentiel-juridique.md`](./Referentiel-juridique.md) | **Le métier** : moteur de calcul des délais, catalogue des règles avec bases légales, modèles de procédure, 30 cas de test obligatoires | La juriste (validation) et le développement |
| [`Techno.md`](./Techno.md) | **La technique** : pile, architecture, modèle de données, API, sécurité, jobs, déploiement | Le développement |
| [`Design.md`](./Design.md) | **La charte graphique** : identité, tokens de couleur, échelle d'urgence, typographie, composants, accessibilité, impression | Le développement |
| [`CLAUDE.md`](./CLAUDE.md) | **Les conventions de code et les six invariants** — à placer à la racine du dépôt `dies` sous le nom `CLAUDE.md` | Claude Code, en premier |
| [`PLAN-DEVELOPPEMENT.md`](./PLAN-DEVELOPPEMENT.md) | **Le découpage en 8 lots**, avec les consignes prêtes à copier | Le développement |
| [`env.example`](./env.example) | **Les variables d'environnement** commentées, dont les identifiants de connexion | Le déploiement |

---

## 3. Comment lancer les développements

1. **Créer un dépôt neuf** `dies` (application distincte de DARI Lab — la justification est au § 0 du cahier des charges).
2. Copier ce dossier dans `dies/docs/`, et **`CLAUDE.md` à la racine** du dépôt.
3. Ouvrir une session Claude Code à la racine et donner la consigne du **lot 0** de `PLAN-DEVELOPPEMENT.md`.
4. Enchaîner lot par lot. **Ne pas sauter le lot 2** (moteur de délais) ni le lot 7 (mise en service, sauvegardes).
5. Après le lot 3, faire tester par l'utilisatrice sur des dossiers réels — c'est le moment où les vraies demandes apparaissent.

> Trois choses à ne jamais laisser passer, quel que soit le lot : les **30 tests du moteur de délais**,
> l'**idempotence des rappels**, et une **restauration de sauvegarde réellement effectuée**.

---

## 4. Questionnaire de cadrage — les 13 questions à lui poser

Les spécifications sont complètes et cohérentes sous les hypothèses ci-dessous. Ses réponses affineront
le référentiel sans rien remettre en cause de l'architecture.

**Son métier**
1. Juriste **d'entreprise** (service juridique interne), **en cabinet**, ou **indépendante** ? — détermine le régime de confidentialité applicable aux documents (référentiel § 6.3).
2. Quelles **matières** représentent 80 % de son temps ? (droit des sociétés, contrats, contentieux, baux, social, IP, conformité) — hypothèse retenue : **droit des sociétés / secrétariat juridique** en tête.
3. Combien de **sociétés** suit-elle, et combien de **dossiers actifs** en moyenne ?

**Ses échéances**
4. Quelles sont les **trois échéances** qu'elle redoute le plus de manquer ?
5. Suit-elle les **échéances fiscales** (liasse, DAS2) ou est-ce l'expert-comptable ? — elles sont livrées désactivées.
6. A-t-elle des **échéances récurrentes internes** (comité juridique, reporting mensuel, revue de contrats) ?
7. Combien de temps **avant** veut-elle être prévenue ? Les paliers proposés (J-60/J-30/J-15/J-7/J-2/J-0 pour les échéances bloquantes) lui conviennent-ils ?

**Ses habitudes**
8. Quel **agenda** utilise-t-elle aujourd'hui (Outlook professionnel, Google, papier) — et **a-t-elle le droit de publier ce calendrier** vers un outil personnel ? C'est la question qui décide du niveau d'intégration (cahier des charges § 3.4.4) : abonnement iCal dans les deux sens (recommandé) ou agenda interne seul.
9. Veut-elle **tout son agenda** dans Dies (y compris les réunions internes sans lien avec un dossier), ou seulement les rendez-vous liés à ses dossiers ? — cela change ce qu'elle devra saisir deux fois.
10. Sur quel **support** consultera-t-elle le plus souvent : ordinateur, téléphone, ou les deux ?
11. Peut-elle fournir son **tableur de suivi actuel** ? — il sert de jeu de test et de base à l'import du lot 3.
12. Veut-elle **stocker des documents** dans l'outil, ou seulement des liens vers son arborescence existante ?
13. Y a-t-il des **contraintes de son employeur** sur l'hébergement des données (obligation de rester sur un outil interne, validation de la DSI) ? — **à poser avant le lot 7**, c'est la seule question qui peut changer le déploiement.

---

## 5. Ce que ce dossier suppose, et qu'il faut confirmer

| Hypothèse | Conséquence si elle est fausse |
|---|---|
| Droit français, spécialité principale droit des sociétés | Le catalogue de règles change ; le moteur et le modèle de données, non |
| Utilisatrice unique, aucun partage | Un second utilisateur nécessiterait de revoir l'authentification (le modèle de données, lui, prévoit déjà un champ `responsable`) |
| Hébergement en UE chez un fournisseur grand public | Une contrainte employeur pourrait imposer un hébergement interne — architecture inchangée, déploiement différent |
| Agenda tenu dans Dies, Outlook seulement en lecture | Si l'employeur impose Outlook comme agenda unique, Dies reste la source des échéances et exporte vers Outlook (niveau 1 du § 3.4.4) |
| Documents stockés dans l'outil | Si elle préfère son arborescence existante, le lot 5 se réduit à des liens et des métadonnées |
| Budget d'hébergement < 15 €/mois | Une contrainte plus serrée justifierait la pile légère évoquée dans `Techno.md` § 6 |

---

## 6. Avertissement

Le référentiel juridique est un **outil de travail**, arrêté sur la base des sources connues jusqu'en
mai 2026. Il ne constitue pas une consultation juridique : chaque règle porte sa base légale et sa date
de vérification, reste **éditable dans l'application**, et fait l'objet d'une **revue annuelle** inscrite
comme échéance dans l'outil lui-même. Les délais de procédure portent un avertissement permanent dans
l'interface. **Dies rappelle et trace ; il ne décide pas à sa place.**

---

*Dossier de spécifications Dies v1.0 — 2 août 2026.*
