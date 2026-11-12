# Dossier juridique — ouverture du hub coachs / athlètes (septembre 2026)

> **À qui ce document s'adresse.** À l'avocat qui relira l'ouverture de la plateforme aux coachs
> indépendants et aux athlètes, et au porteur du produit qui l'accompagne.
>
> **Ce que ce document n'est pas.** Un avis juridique. Il est écrit par le développeur qui a
> implémenté le hub, et il décrit **ce que le code fait**, pas ce que le droit exige. Chaque
> question du §3 attend une réponse que je ne suis pas en mesure de donner.

---

## 0. Pourquoi ce dossier existe maintenant

Le plan prévoyait la relecture juridique **avant** la mise en relation. Le code l'a précédée : la
mise en relation est écrite, testée et déployable. Ce n'est pas confortable, et il faut le dire tel
quel — le dossier est donc en retard sur la réalité, et il y a **au moins une inexactitude déjà en
ligne** (§1.1).

**Rien n'est ouvert au public** : la plateforme reste en régime « sur demande », aucun coach n'a de
fiche publiée, et l'annuaire est vide. La fenêtre pour corriger est donc entière.

---

## 1. Ce qui a changé, factuellement

### 1.1 ⚠️ Les textes en ligne sont devenus faux

Les CGU publiées affirment aujourd'hui :

> « Les comptes athlètes sont créés sur **invitation d'un coach**. » — CGU §3, et à l'identique
> dans la FAQ de la page support.

**C'est désormais inexact.** Un athlète crée son compte seul, sans invitation et sans coach. Une
CGU qui décrit un fonctionnement que le produit n'a plus est un problème en soi, indépendamment de
tout le reste : elle n'est opposable à personne sur ce point, et elle donne une image erronée du
service à qui l'accepte.

**Action immédiate, indépendante de la relecture** : corriger l'inexactitude. C'est fait dans le
même lot que ce document, avec des textes **provisoires marqués comme tels**, à remplacer par la
version relue.

### 1.2 Les cinq changements de fond

| # | Ce qui existe désormais | Ce que ça change juridiquement |
|---|---|---|
| 1 | **Un athlète s'inscrit seul**, sans coach ni club | Il devient responsable de ses propres données ; la plateforme n'est plus seulement l'outil d'un professionnel, elle a des utilisateurs finaux directs |
| 2 | **Un annuaire public** de coachs, avec tarifs affichés | La plateforme présente des professionnels tiers à des consommateurs |
| 3 | **Une mise en relation** demande → acceptation | La plateforme intervient dans la formation d'une relation contractuelle entre deux tiers |
| 4 | **Des données de santé** transmises à un coach que la plateforme n'a pas choisi | Le destinataire des données de l'article 9 est désormais désigné par l'athlète, parmi une liste que la plateforme publie |
| 5 | **Une fin de relation** exerçable des deux côtés | L'ancien coach garde la **lecture** de l'historique qu'il a tenu (cf. §3.6) |

### 1.3 Ce qui n'a PAS changé, et qui simplifie le dossier

- **Aucun paiement ne transite par la plateforme.** Les tarifs s'affichent, l'accord se conclut
  entre les deux personnes, le règlement se fait hors plateforme. Aucune obligation d'intermédiaire
  de paiement, aucun encaissement pour compte de tiers.
- **Aucune garantie n'est donnée sur les diplômes.** Ils sont déclarés par le coach, et l'interface
  l'écrit à côté d'eux — pas dans des conditions générales.
- **Aucun avis, aucune note.** Rien à modérer, rien à qualifier d'« avis vérifié ».
- **Aucun mineur en inscription libre** : 16 ans minimum, contrôlé côté serveur.

Ces quatre points découlent de décisions produit prises le 4 septembre 2026 (cf.
[`AUDIT-HUB-COACHS-ATHLETES-2026-09`](./AUDIT-HUB-COACHS-ATHLETES-2026-09.md) §8). Ils réduisent
considérablement la surface à couvrir — mais ils ne la suppriment pas.

---

## 2. Ce que le code fait, précisément

Une relecture juridique utile suppose de savoir ce qui se passe vraiment. Voici les faits, vérifiables.

### 2.1 L'inscription d'un athlète

- Route publique, plafonnée, e-mail vérifié par lien.
- **Deux consentements distincts et tous deux obligatoires** : acceptation des CGU, et consentement
  au traitement des données de santé. Les deux sont horodatés en base
  (`athlete_accounts.terms_accepted_at`, `health_data_consent_at`).
- **Âge minimum 16 ans**, contrôlé sur la date de naissance, refus explicite en dessous.
- Aucune fiche, aucun club, aucune relation n'est créé à ce stade.

### 2.2 Ce que le coach voit avant d'accepter

Prénom, nom, **âge en années** (pas la date de naissance), discipline, niveau, ville, objectif écrit
par l'athlète, et son message. **Aucune coordonnée** : ni e-mail, ni téléphone. C'est une décision
technique délibérée — si une demande livrait l'adresse, il suffirait d'en recevoir pour se
constituer un fichier de prospection.

### 2.3 Ce que l'acceptation déclenche

En une transaction : création d'une fiche de suivi dans l'espace du coach, rattachement du compte de
l'athlète à cet espace, création d'une relation **privée** (invisible des autres coachs du même
espace), et **report du consentement santé** du compte vers la fiche. C'est ce report qui autorise
la collecte ultérieure de données de l'article 9 ; sans lui, le serveur la refuse.

### 2.4 Ce que la fin de relation fait

- Les deux parties peuvent l'exercer, sans préavis, motif facultatif.
- L'athlète est détaché : plus de fiche courante, plus d'espace ; il redevient un compte libre.
- **La fiche et son historique restent chez le coach**, qui les garde **en lecture seule**.
- L'export et l'effacement RGPD de l'athlète restent des gestes distincts, à sa main.

### 2.5 Les données de santé, et leur garde

Seuils physiologiques, mesures de lactate, douleurs, fatigue, indisponibilités médicales et notes
médicales sont **chiffrés au repos** et gardés par un validateur applicatif qui refuse toute
écriture sans consentement actif. Le retrait du consentement est implémenté et arrête la collecte.

**Le compte athlète autoporté ne porte aucune de ces données** : elles restent sur la fiche tenue
par le coach.

---

## 3. Les questions posées à l'avocat

Elles sont classées par ce qu'elles bloquent, pas par difficulté.

### 3.1 🔴 Quel est le statut de la plateforme ?

Simple hébergeur d'annonces, intermédiaire de mise en relation, ou opérateur de plateforme en ligne
au sens du code de la consommation ? La réponse commande les obligations d'information, la
responsabilité, et ce qu'il faut afficher sur chaque fiche.

*Élément de contexte : aucun paiement ne transite, aucun classement payant, aucune commission.*

*Élément ajouté depuis la rédaction de ce dossier : un **dispositif de signalement** existe désormais
sur chaque fiche, ouvert aux visiteurs sans compte, avec une file d'arbitrage humaine et la
possibilité de retirer une fiche de l'annuaire. Rien n'est automatique. Je l'ai construit comme la
contrepartie de l'absence de vérification des diplômes ; **je ne sais pas s'il satisfait un régime
de notification au sens de la LCEN**, ni s'il en déclenche les obligations formelles (accusé de
réception, délais, information de la personne visée). C'est précisément ce que cette question
demande de trancher.*

### 3.2 🔴 Les CGU doivent-elles séparer deux contrats ?

Aujourd'hui un seul texte régit tout. Or il y a désormais **deux relations distinctes** : celle
entre chaque utilisateur et la plateforme, et celle entre l'athlète et son coach — dont la
plateforme n'est pas partie. Faut-il deux documents, ou un seul avec deux sections clairement
séparées ?

*Ce qui est en jeu : qu'un athlète mécontent de son coach ne se retourne pas contre la plateforme
en s'appuyant sur des CGU qui ne distinguent pas les deux.*

### 3.3 🔴 Qui est responsable de traitement pour les données de santé ?

L'athlète consent à l'inscription, **avant de savoir quel coach le suivra**. Le consentement est
ensuite reporté sur la fiche à l'acceptation, et le coach devient destinataire.

- Ce consentement anticipé est-il valable, ou faut-il un second consentement **nommant le coach**
  au moment de l'acceptation ?
- La plateforme est-elle responsable conjoint, ou sous-traitant du coach ?
- Faut-il un accord de sous-traitance type, à faire accepter à chaque coach publié ?

*C'est la question la plus lourde du dossier, et celle qui peut demander une modification du code —
d'où sa place ici plutôt qu'en fin de liste.*

### 3.4 🟡 Que doit-on afficher sur une fiche coach ?

La plateforme présente des professionnels indépendants. Faut-il exiger et publier : un numéro SIRET,
une attestation d'assurance responsabilité civile professionnelle, une carte professionnelle
d'éducateur sportif (obligatoire en France pour l'encadrement contre rémunération) ?

*Aujourd'hui, rien de tout cela n'est demandé. C'est peut-être le point le plus exposé : encadrer
une activité physique contre rémunération sans carte professionnelle est un délit, et la plateforme
en publie l'offre.*

### 3.5 🟡 La mention « diplômes déclarés » suffit-elle ?

L'interface écrit, à côté des diplômes : « déclarés par le coach, la plateforme ne les a pas
vérifiés auprès des organismes qui les délivrent ». Est-ce suffisant pour écarter la responsabilité,
ou la publication même d'un diplôme non vérifié engage-t-elle ?

### 3.6 🟡 L'ancien coach peut-il garder l'historique en lecture ?

À la fin d'une relation, l'ancien coach référent conserve l'accès **en lecture** à la fiche qu'il a
tenue — y compris ses données de santé. La justification technique est qu'il en est l'auteur et que
la lui retirer rendrait cet historique illisible par tout le monde.

- Est-ce licite, et sur quelle base légale après la fin de la relation ?
- Faut-il une durée maximale de conservation, et laquelle ?
- L'athlète doit-il pouvoir exiger que son ancien coach perde cet accès ?

*C'est un arbitrage produit qui a été pris pour éviter de la donnée morte. Il se défait si le droit
l'exige — c'est une ligne dans le validateur d'accès.*

### 3.7 🟡 L'âge minimum de 16 ans est-il le bon seuil ?

Choisi par référence à l'âge du consentement numérique. Un mineur de 16 ou 17 ans consent donc seul
au traitement de ses données de santé et sollicite seul un coach adulte. Faut-il 18 ans, ou un
accord parental entre 16 et 18 ?

### 3.8 🟢 Que doit dire la politique de confidentialité en plus ?

Nouvelles catégories de personnes (athlètes sans coach), nouveau destinataire (le coach choisi),
nouvelle durée de conservation (la fiche après la fin de la relation), nouveau traitement (l'annuaire
public, qui publie nom, ville et photo d'un coach).

### 3.9 🟢 La photo d'un coach

Publiée sans authentification. Le serveur la ré-encode, ce qui **efface les métadonnées EXIF** —
notamment les coordonnées GPS du lieu de la prise de vue. Le consentement à la publication est
implicite (le coach l'ajoute lui-même à sa fiche publique). Faut-il le rendre explicite ?

---

## 4. Ce qui est fait en attendant, et ce qui ne l'est pas

**Fait dans le même lot que ce document** — parce qu'une inexactitude en ligne ne peut pas attendre :

- les CGU et la page support ne disent plus que les comptes athlètes naissent d'une invitation ;
- une section décrit la mise en relation, ce que la plateforme fait et ce qu'elle ne fait pas ;
- l'absence de vérification des diplômes et l'absence de paiement sont écrites noir sur blanc ;
- la politique de confidentialité mentionne le coach comme destinataire et l'annuaire public.

**Fait depuis, au lot 7** :

- un **signalement de fiche** ouvert à tous, y compris sans compte, avec sa file d'arbitrage ;
  aucun seuil ne retire une fiche automatiquement, et le coach signalé n'est pas notifié tant que
  rien n'est établi ;
- une clause des CGU (§4 bis) qui le décrit et annonce qu'aucune réponse individuelle n'est faite
  au signalant.

Ce dispositif est un choix de produit, pas une réponse juridique : voir la note ajoutée en 3.1.

**Ces textes sont provisoires.** Ils sont écrits par un développeur pour cesser d'être faux, pas par
un juriste pour être opposables. Ils portent une mention le disant, et ils sont à remplacer.

**Pas fait, et volontairement** : rien qui suppose une réponse aux questions du §3. Aucun accord de
sous-traitance, aucune exigence de SIRET ou de carte professionnelle, aucun second consentement
nommant le coach. Les inventer serait pire que les laisser manquants — cela donnerait l'apparence
d'une conformité qui n'a été validée par personne.

---

## 5. Recommandation de séquence

1. **Faire relire ce dossier** avant toute ouverture publique. Les questions 3.1 à 3.3 peuvent
   demander des modifications de code, et il vaut mieux les faire maintenant que sur un produit
   ouvert.
2. **Trancher 3.4 en priorité** : c'est la seule question dont la réponse pourrait empêcher de
   publier des fiches — si une carte professionnelle est exigée, il faut la collecter avant, pas
   après.
3. **Remplacer les textes provisoires** par la version relue.
4. **Ouvrir ensuite.**

L'annuaire est vide et le régime d'inscription reste « sur demande » : rien ne presse
techniquement. La séquence ci-dessus ne coûte que du délai.
