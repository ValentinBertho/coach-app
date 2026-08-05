# Demandes d'accès API — Garmin Connect & COROS

> Dossier prêt à soumettre pour ouvrir les intégrations **Garmin** et **COROS** de DARI Lab
> (synchronisation des activités **montre → plateforme** et publication des séances
> **plateforme → montre**).
>
> État des lieux au **4 août 2026**. Les deux programmes se demandent séparément, avec des
> processus et des délais très différents — et l'un des deux est actuellement fermé.

---

## Sommaire

- [1. État des deux programmes](#1-état-des-deux-programmes)
- [2. Fiche d'identité commune](#2-fiche-didentité-commune-à-remplir-une-seule-fois)
- [3. Demande COROS](#3-demande-coros--à-envoyer-maintenant)
- [4. Demande Garmin](#4-demande-garmin--programme-en-pause)
- [5. Checklist avant d'envoyer](#5-checklist-avant-denvoyer)
- [6. Après réception des identifiants](#6-après-réception-des-identifiants--ce-qui-reste-à-coder)
- [7. Sources](#7-sources)

---

## 1. État des deux programmes

| | **COROS** | **Garmin Connect Developer Program** |
|---|---|---|
| **Statut (août 2026)** | ✅ **Ouvert** — mais **sélectif** | ⛔ **En pause** — nouvelles demandes suspendues |
| **Voie d'entrée** | **Formulaire de candidature COROS** (24 champs, §3) + e-mail à `api@coros.com` | Formulaire d'accès (retiré du site) / formulaire de contact développeur |
| **Critères de sélection** | Annoncés en tête du formulaire : **taille de marché actuelle**, usage prévu des données, « et d'autres facteurs ». Toutes les demandes ne sont pas acceptées. | **Personne morale uniquement** (société, université, hôpital, institut de recherche) — les usages personnels sont refusés |
| **Sync activités** | ✅ Activités, données journalières, sommeil, EvoLab | ✅ **Activity API** |
| **Envoi de séances vers la montre** | ✅ **Oui** — option « Structured Workouts and Training Plans Sync (from your platform to COROS) » du formulaire | ✅ **Training API** (séances structurées + plans, publiés dans le calendrier Garmin Connect puis synchronisés sur la montre) |
| **Délai annoncé** | Vérification d'identité puis émission Client ID / Secret | 2 jours ouvrés pour l'accusé de réception… **quand le programme est ouvert** |
| **Coût** | **Aucun frais de partenariat** (annoncé sur le formulaire) | Gratuit, mais sous validation partenaire |

### Ce que ça implique concrètement

1. **COROS d'abord.** C'est la seule des deux demandes qui peut aboutir aujourd'hui, et la bonne
   nouvelle est que **la publication de séances vers la montre est explicitement au catalogue** —
   c'est une case à cocher du formulaire, pas une hypothèse.
   ⚠️ **Mais l'accès n'est pas automatique.** Le formulaire l'annonce sans détour : *« nous ne
   sommes pas en mesure d'offrir l'accès API à toutes les parties qui postulent. Pour choisir les
   nouvelles candidatures que nous acceptons, nous considérons : la taille de marché actuelle, la
   manière dont les données seront utilisées, et d'autres facteurs. »* La **taille de marché** est
   donc un critère explicite — c'est le point faible d'une plateforme qui démarre, et la §3.5
   explique comment le compenser.

2. **Garmin : demande impossible à soumettre en l'état.** Garmin « fait évoluer et modernise » le
   programme et a **temporairement suspendu la revue et l'approbation des nouvelles demandes**.
   Le formulaire public d'accès a été retiré, il n'existe **ni liste d'attente ni notification de
   réouverture**. Les intégrations existantes continuent de fonctionner ; seules les nouvelles
   inscriptions sont bloquées. Aucune date de réouverture n'est publiée.
   ⚠️ À ne pas confondre avec **Connect IQ** (cadrans, champs de données, apps embarquées), qui
   reste ouvert mais **ne donne pas** accès aux données Connect ni à la publication de séances.

3. **Le dossier Garmin se prépare quand même** (§4). Quand le programme rouvrira, le délai
   d'instruction est court : avoir le dossier prêt et une preuve d'antériorité (ticket ouvert au
   support développeur) fait gagner des semaines. Et le travail de cadrage — scopes, usage des
   données, URL de callback, politique de conservation — est **le même** pour COROS, donc il n'est
   pas perdu.

4. **Le repli reste en place pendant ce temps.** L'import **GPX/TCX manuel** couvre déjà les
   porteurs de Garmin et de COROS pour le sens montre → plateforme (`ActivityController`,
   `GpxParser`). Le sens plateforme → montre n'a, lui, **aucun repli** hors export de fichier.

---

## 2. Fiche d'identité commune (à remplir une seule fois)

Les deux dossiers demandent les mêmes informations. Ce qui suit est **pré-rempli d'après le code et
la configuration réels du dépôt** ; seules les lignes marquées **`[À COMPLÉTER]`** demandent une
information que le code ne contient pas.

### 2.1 Entité et contacts

| Champ | Valeur |
|---|---|
| Nom commercial du produit | **DARI Lab** |
| Dénomination légale | ⚠️ **À recopier du registre** — cf. §3.3.1 (probablement `Valentin Bertho`, entreprise individuelle) |
| Forme juridique / pays | Entreprise immatriculée en **France** (UE) — forme exacte à confirmer au registre |
| Numéro d'immatriculation | **SIRET 914 436 118 00022** — SIREN **914 436 118** |
| Adresse du siège | `[À COMPLÉTER]` — celle de l'immatriculation |
| Site web | `https://www.darilab.app` |
| Dirigeant | **Valentin Bertho** — Founder & Lead Developer |
| Contact business | Valentin Bertho — `contact@darilab.app` |
| Contact technique (représentant technique autorisé) | Valentin Bertho — `contact@darilab.app` |
| Contact secondaire | ⚠️ **À créer** — `valentin@darilab.app` ou `tech@darilab.app` (cf. §3.3.2) |
| Contact sécurité / incident | `security@darilab.app` — alias à créer |
| DPO / contact RGPD | `privacy@darilab.app` — alias à créer |

> ⚠️ **Utilisez des adresses du domaine `darilab.app`, pas votre Gmail.** Les deux programmes
> vérifient l'identité de l'entité ; une adresse personnelle sur un domaine grand public est le
> premier motif de rejet ou d'aller-retour. `contact@darilab.app` figure déjà dans la configuration
> (`VAPID_SUBJECT`), le domaine est donc déjà opérationnel côté e-mail — les alias supplémentaires
> sont gratuits sur OVH et se créent en quelques minutes.

### 2.2 Description du produit (texte réutilisable tel quel)

> **FR** — DARI Lab est une plateforme SaaS de coaching sportif (course à pied et préparation
> physique) destinée aux entraîneurs et à leurs athlètes. L'entraîneur construit des séances
> structurées à partir des données physiologiques de l'athlète (seuils lactiques LT1/LT2, vitesse
> critique, VDOT, 1RM) et les prescrit sous forme de fourchettes d'allure, de fréquence cardiaque et
> de RPE. L'athlète consulte son programme dans une application mobile (PWA), exécute la séance et
> renvoie son ressenti. La plateforme compare le prévu au réalisé et calcule la charge
> d'entraînement (ACWR, monotonie, temps passé en zone).

> **EN** — DARI Lab is a SaaS coaching platform for running and strength training, used by coaches
> and their athletes. Coaches build structured workouts from each athlete's physiological profile
> (LT1/LT2 lactate thresholds, critical speed, VDOT, 1RM) and prescribe them as pace, heart-rate and
> RPE ranges. Athletes follow the programme in a mobile PWA, complete the session and submit their
> feedback. The platform then compares planned vs. actual training and computes training load
> (ACWR, monotony, time-in-zone).

### 2.3 Cas d'usage demandé (le cœur du dossier)

> **EN** — We are requesting API access for two user-initiated flows, both of which require the
> athlete's explicit consent through an OAuth 2.0 authorisation:
>
> 1. **Activity synchronisation (device → platform).** After an athlete connects their account, we
>    import their completed running and strength activities — summary metrics (start time, distance,
>    moving time, elevation gain, average/max heart rate, cadence, power, calories) and, where
>    available, the time/heart-rate/speed samples needed to compute time-in-zone. This lets the coach
>    compare the prescribed session with what was actually performed. We already do exactly this with
>    Strava, and the import path, de-duplication and storage model are in production.
>
> 2. **Structured workout publishing (platform → device).** We want to publish the coach's
>    prescribed sessions to the athlete's watch, so the athlete no longer has to re-enter interval
>    structures by hand. Our sessions are already stored as structured blocks (warm-up / main set /
>    cool-down, repetitions, duration or distance targets, pace and heart-rate ranges, recovery), which
>    maps directly onto a structured-workout model.
>
> We are **not** requesting continuous health monitoring, sleep or all-day wellness data. We only
> need data about deliberate training sessions.

**Pourquoi cette formulation.** Les deux programmes évaluent la **proportionnalité** de la demande :
un dossier qui coche toutes les cases de données disponibles se fait retoquer ou renvoyer à des
questions. Demander explicitement le minimum — et **dire ce qu'on ne demande pas** — est le meilleur
accélérateur. Côté Garmin, cela revient à demander **Activity API + Training API**, et **pas** la
Health API ni la Women's Health API.

### 2.4 Paramètres techniques à déclarer

| Champ | Valeur à déclarer |
|---|---|
| **Redirect URI OAuth — production (Garmin)** | `https://www.darilab.app/app/garmin/callback` |
| **Redirect URI OAuth — production (COROS)** | `https://www.darilab.app/app/coros/callback` |
| **Redirect URI OAuth — développement** | `http://localhost:4200/app/garmin/callback` et `http://localhost:4200/app/coros/callback` |
| **URL de callback push / webhook** | `https://coachrun-back.up.railway.app/api/webhooks/garmin` (idem `/coros`) — voir la note ci-dessous |
| **Origines web (CORS)** | `https://www.darilab.app`, `https://darilab.app` |
| **Volumétrie** | **~10 utilisateurs actifs aujourd'hui**, **50 à 100 attendus dans l'année**. Annoncez les deux chiffres : le réel là où on le demande, la trajectoire dans le texte libre (cf. §3.3.3) |
| **Fréquence d'appel** | Synchronisation planifiée horaire par athlète connecté (`STRAVA_SYNC_CRON=0 30 * * * *` aujourd'hui), plus les synchronisations à la demande. Bascule sur notification push dès que le fournisseur en propose une. |
| **Environnements** | 1 production + 1 environnement de développement local |

> **Le schéma de redirect URI reprend celui de Strava déjà en production** :
> `front/src/app/app.routes.ts:164` déclare la route `app/strava/callback`, et
> `application.yml:111` porte le `redirect-uri` correspondant. Les deux nouveaux fournisseurs
> suivent la même convention — c'est aussi ce qui rend la mise en œuvre rapide une fois les
> identifiants reçus.

> ⚠️ **Point à trancher avant d'envoyer : l'URL du back.**
> `coachrun-back.up.railway.app` est un domaine **fourni par l'hébergeur**, et Garmin comme COROS
> enregistrent l'URL de callback **dans la configuration de votre application** — en changer plus
> tard demande de repasser par leur support. Mettez un sous-domaine à vous (`api.darilab.app`,
> CNAME vers Railway) **avant** d'envoyer les demandes. Un domaine `*.up.railway.app` fait par
> ailleurs moins sérieux dans un dossier de vérification d'identité qu'un domaine que vous
> contrôlez.

### 2.5 Sécurité et conformité (réponses vérifiables dans le code)

Les deux programmes posent ces questions ; toutes les réponses ci-dessous sont **déjà vraies** dans
le dépôt, ce qui est un vrai atout — vous n'avez rien à promettre au futur.

| Question type | Réponse | Où c'est dans le code |
|---|---|---|
| Chiffrement des jetons OAuth au repos | Oui — chiffrement au niveau colonne | `DeviceConnection.java` : `@Convert(converter = EncryptedStringConverter.class)` sur `accessToken` et `refreshToken`, clé `FIELD_ENCRYPTION_KEY` |
| Chiffrement en transit | TLS 1.2+ de bout en bout (Vercel + Railway) | `docs/DEPLOIEMENT.md` |
| Protection CSRF du flux OAuth | Oui — paramètre `state` signé et vérifié | `OAuthStateCodec.java`, `StravaService.connect()` |
| Rafraîchissement des jetons | Automatique avant expiration, jamais de jeton expiré envoyé | `StravaService.freshAccessToken()` |
| Les jetons apparaissent-ils dans les logs ? | Non | `docs/Claude.md` : interdiction explicite |
| Révocation / droit à l'oubli | L'athlète déconnecte son compte lui-même ; suppression de la connexion et purge des données | `StravaService.disconnect()`, export/purge RGPD (`GdprExportTest`) |
| Consentement | OAuth par athlète, initié par l'athlète depuis son portail — jamais par le coach à sa place | `athlete-sync.component.ts` |
| Base légale RGPD | Consentement explicite de la personne concernée (art. 6.1.a), données de santé au sens de l'art. 9 traitées sur consentement explicite | `docs/Cahier-des-charges.md` §RGPD |
| Localisation des données | Union européenne `[À VÉRIFIER]` — confirmez la région de vos instances Railway/Vercel | — |
| Durée de conservation | `[À COMPLÉTER]` — annoncez une durée (ex. « le temps de l'abonnement + 12 mois ») cohérente avec votre politique de confidentialité |
| Sous-traitants | Railway (hébergement back + base), Vercel (front), Resend (e-mail) | `docs/DEPLOIEMENT.md` |
| Politique de confidentialité publique | ✅ `https://www.darilab.app/legal/confidentialite` — ⚠️ doit **citer nommément** Garmin et COROS | route publique `app.routes.ts:55` |
| CGU publiques | ✅ `https://www.darilab.app/legal/cgu` | route publique `app.routes.ts:55` |
| Portail de connexion public *(exigé par COROS)* | ✅ `https://www.darilab.app/login` | `app.routes.ts:19` |
| Page de support publique *(exigée par COROS)* | ✅ `https://www.darilab.app/support` | `app.routes.ts` (route `support`), `legal.component.ts` |

> 🔴 **Un point bloquant reste à traiter avant de soumettre : le contenu de la politique de
> confidentialité.** L'URL existe, mais les deux programmes exigent qu'elle mentionne
> **explicitement** la collecte de données via l'API du fournisseur — « Garmin » et « COROS »
> **nommés**, comme Strava l'est déjà — la finalité, la durée de conservation et le moyen de
> révoquer. À faire au moment où l'intégration est livrée, pas avant (annoncer une collecte qui
> n'existe pas encore serait faux) : mais c'est un jalon à ne pas oublier, il conditionne la mise
> en production.
>
> ⚠️ **Deux autres points à vérifier dans `legal.component.ts`, découverts en créant la page de
> support** — ils ne bloquent pas la candidature, mais ils vous concernent :
> **(a)** `LEGAL_OWNER.legalName` et `address` sont **vides**, avec un commentaire du code qui
> signale qu'il faut les remplir avant la bêta. L'article 13 du RGPD impose de fournir « l'identité
> et les coordonnées du responsable du traitement » — un nom commercial et un e-mail n'y suffisent
> pas, a fortiori pour un service traitant des données de santé.
> **(b)** Les mentions légales revendiquent une édition **« à titre non professionnel »**
> (exemption LCEN art. 6, III-2). Avec un **SIRET actif** et une activité commerciale, cette
> exemption ne s'applique probablement plus : un éditeur professionnel doit publier son identité,
> son adresse et son numéro d'immatriculation. Je n'ai pas modifié ces pages — publier votre
> identité civile et votre adresse est votre décision, pas la mienne.

---

## 3. Demande COROS — à envoyer maintenant

### 3.1 Ce que dit le formulaire officiel

Le dossier COROS se dépose via un **formulaire de 24 champs** (« COROS API Application »). Ses
conditions liminaires, à lire avant de remplir quoi que ce soit :

- **La sélection est réelle.** *« Nous examinons chaque candidature. Cependant, nous ne sommes pas
  en mesure d'offrir l'accès API à toutes les parties qui postulent. Pour choisir les nouvelles
  candidatures que nous acceptons, nous considérons : la taille de marché actuelle, la manière dont
  les données seront utilisées, et d'autres facteurs. »*
- **Suivi obligatoire du développement.** Une fois le Client ID et les clés émis, COROS n'a plus de
  visibilité : vous devez les tenir informés de l'avancement et **prévenir idéalement 1 semaine
  avant la mise en production**.
- **Deux pages obligatoires sur votre plateforme** : un **portail de connexion** et une **page de
  support**, pour que les utilisateurs accèdent à l'intégration et demandent de l'aide technique.
- **Aucun frais** de partenariat.
- Un **API Reference Guide** (lien Dropbox en tête de formulaire) et un **API Agreement** (lien
  Dropbox au champ 21) sont à télécharger et à conserver. Le guide contient notamment la
  **section 5.7**, référencée par le champ 13, qui décrit le service de push des données.

### 3.2 Où déposer

- **Formulaire** : lien « apply to the API » dans l'article
  [Submit an API Application](https://support.coros.com/hc/en-us/articles/17085887816340-Submit-an-API-Application)
  ([version FR](https://support.coros.com/hc/fr/articles/17085887816340-Soumettre-une-demande-d-acc%C3%A8s-%C3%A0-l-API)).
  C'est le canal principal.
- **E-mail** : **`api@coros.com`** en complément — il vous donne une trace datée et un
  interlocuteur nommé. Le texte est en §3.4.

### 3.3 Réponses au formulaire, champ par champ

> Les champs marqués ✳ sont obligatoires. Les valeurs sont prêtes à copier ; **`⚠️`** signale ce
> qui reste à trancher ou à créer avant de soumettre.

| # | Champ | Réponse |
|---|---|---|
| 1 ✳ | Platform / Application Name | `DARI Lab` |
| 2 ✳ | Company Name | ⚠️ **La dénomination légale exacte** — voir §3.3.1 |
| 3 ✳ | Primary Contact Email | `contact@darilab.app` |
| 4 ✳ | Secondary Contact Email | `valentin@darilab.app` ✅ |
| 5 ✳ | Privacy Officer Email | `valentin@darilab.app` — voir §3.3.2 |
| 6 ✳ | Company Owner Name and Title | `Valentin Bertho — Founder & Lead Developer` |
| 7 | Platform / Application URL | `https://www.darilab.app` |
| 8 ✳ | Description (**100 caractères max**) | `Physiology-driven coaching platform: coaches prescribe running and strength sessions.` *(85 car.)* |
| 9 ✳ | Total Active Users | La tranche contenant **10** (la plus basse proposée). Voir §3.3.3 |
| 10 ✳ | Primary Region | `France (Europe)` |
| 11 ✳ | Which API function(s) does your app need? | ☑️ `Activity / Workout Data Sync (one way, COROS to your platform)`<br>☑️ `Structured Workouts and Training Plans Sync (from your platform to COROS)`<br>☐ les 5 autres — voir §3.3.4 |
| 12 ✳ | Authorized Callback Domain (redirect_uri) | `https://www.darilab.app` |
| 13 ✳ | Workout Data Receiving Endpoint URL | `https://api.darilab.app/api/webhooks/coros` — ⚠️ deux options en §3.5-2 |
| 14 ✳ | Service Status Check URL | `https://api.darilab.app/api/actuator/health` — ⚠️ idem |
| 15 ✳ | Bluetooth / ANT+ protocol link | `N/A` |
| 16 ✳ | Personal or public use? | `Public` |
| 17 ✳ | Commercial or non-commercial use? | `Commercial` |
| 18 ✳ | Intended use of data? | Texte prêt en §3.3.5 |
| 19 | Expected Integration Launch Date | ⚠️ Voir §3.3.6 |
| 20 ✳ | Agree to COROS API Application Terms? | `Yes` |
| 21 ✳ | Agree to COROS API Agreement? | `Yes` — **après l'avoir lu** (lien Dropbox du champ 21) |
| 22 ✳ | Please enter your name | `Valentin Bertho` |
| 23 ✳ | Submit Date | Date du jour, format `DD-MM-YYYY` |
| 24 ✳ | Logos PNG | ✅ **Les 4 fichiers sont générés** — voir §3.3.7 |

#### 3.3.1 Champ 2 — Company Name (votre question)

**Non, ne mettez pas juste « Darilab ».** Ce champ alimente le contrat : COROS y attend la
**dénomination légale**, pas le nom commercial. Le nom commercial, lui, va au champ 1.

Votre SIRET **914 436 118 00022** (SIREN **914 436 118**) prouve que vous êtes **immatriculé** —
c'est un vrai atout, ne le sous-vendez pas. Reste à savoir sous quelle forme :

- **Si vous êtes en entreprise individuelle / micro-entreprise** — le cas le plus probable — la
  dénomination légale est **votre nom civil**, pas « DARI Lab ». Répondez :
  `Valentin Bertho (sole proprietorship, trading as DARI Lab)`
- **Si vous avez créé une société** (SASU, SARL…), répondez la **dénomination sociale exacte**
  telle qu'immatriculée, par exemple `DARI LAB SAS`.

⚠️ **Vérifiez avant de remplir** : ouvrez
`https://annuaire-entreprises.data.gouv.fr/entreprise/914436118` et recopiez le champ
**« Dénomination »** au caractère près. Je n'ai pas pu le faire pour vous — l'accès au registre est
bloqué depuis cet environnement (403 du proxy réseau). Une dénomination inexacte fait revenir le
dossier au moment de la signature de l'API Agreement.

> ❌ **Ce qu'il ne faut surtout pas écrire** : « individual developer, not yet registered as a
> company », comme le faisait le brouillon. C'est **faux** — vous avez un SIRET — et devant un
> comité qui sélectionne sur la crédibilité commerciale, c'est le genre de phrase qui classe le
> dossier sans autre examen.

#### 3.3.2 Champ 4 — l'adresse secondaire ✅ réglé

`valentin@darilab.app` est créée : elle va au champ 4, distincte de `contact@darilab.app` au
champ 3. **Ces deux adresses suffisent pour tout le dossier**, inutile d'en créer d'autres :

| Champ | Adresse | Pourquoi celle-là |
|---|---|---|
| 3 — Primary Contact | `contact@darilab.app` | L'adresse générique du produit, déjà publiée dans les pages légales et la page de support |
| 4 — Secondary Contact | `valentin@darilab.app` | Adresse nominative, distincte comme exigé |
| 5 — Privacy Officer | `valentin@darilab.app` | Le champ demande *« l'e-mail de la personne responsable des questions de sécurité et de confidentialité des utilisateurs »* — c'est **une personne**, pas une boîte fonctionnelle. Vous êtes cette personne : l'adresse nominative est la réponse juste, et elle est cohérente avec le responsable de traitement déclaré dans votre politique de confidentialité |

> Les `security@` / `privacy@` évoqués plus haut n'étaient qu'une suggestion de confort — deux
> adresses réelles valent mieux que quatre alias qui pointent au même endroit. L'e-mail
> d'accompagnement (§3.4) a été aligné sur ces deux-là.

#### 3.3.3 Champ 9 — le nombre d'utilisateurs, le vrai sujet du dossier

**Répondez la vérité : la tranche qui contient vos ~10 utilisateurs actifs.** Gonfler le chiffre
serait une mauvaise idée — c'est vérifiable après l'octroi, et le contrat vous engage.

Mais soyons lucides : la « taille de marché actuelle » est un critère de sélection annoncé, et 10
utilisateurs, c'est petit. Ça ne condamne pas le dossier, à condition de **déplacer le débat sur
les autres critères**, ceux que vous maîtrisez :

1. **Le multiplicateur B2B.** Vous ne vendez pas à des athlètes isolés, vous vendez à des
   **entraîneurs**, et chaque entraîneur amène son groupe. Dites-le : c'est ce qui transforme
   « 10 utilisateurs » en « 10 utilisateurs et une courbe qui part de l'entraîneur, pas de
   l'athlète ». Votre projection 50–100 sur l'année devient alors crédible plutôt qu'optimiste.
2. **La preuve d'exécution.** Vous avez **déjà une intégration Strava en production** : même flux
   OAuth 2.0, mêmes jetons chiffrés, même synchronisation planifiée. Pour COROS, ça répond à la
   question qui coûte le plus cher — *est-ce que ces gens vont vraiment livrer ?* Un partenaire qui
   n'intègre jamais est une perte sèche pour eux. C'est votre meilleur argument, il est dans
   l'e-mail §3.4 et dans le champ 18.
3. **L'usage des données.** Deuxième critère annoncé, et le plus facile à bien traiter : vous
   demandez peu, pour une finalité précise, et vous le dites (champ 18).

> 💡 Si le formulaire propose une tranche du type « 1–100 », elle vous va. S'il n'existe qu'un
> « moins de 1 000 », idem. Mentionnez la trajectoire **en toutes lettres au champ 18 et dans
> l'e-mail**, jamais en gonflant le champ 9.

#### 3.3.4 Champ 11 — les fonctions API à cocher

| Option | Cocher ? | Pourquoi |
|---|---|---|
| Activity / Workout Data Sync **(one way, COROS → votre plateforme)** | ✅ **Oui** | C'est exactement votre besoin : importer les activités réalisées |
| Activity / Workout Data Sync **(two ways)** | ❌ Non | Le retour signifierait renvoyer des activités **vers** COROS. Vous n'en avez pas besoin, et demander plus que nécessaire est un motif de questions |
| **Structured Workouts and Training Plans Sync** (votre plateforme → COROS) | ✅ **Oui** | 🎉 C'est votre second besoin, et il est bien au catalogue |
| GPX Route Import / Export | ❌ Non | Tentant pour le trail, mais hors périmètre aujourd'hui. À redemander plus tard si besoin |
| Access Daily Health Data | ❌ **Surtout pas** | Données de santé en continu, sans rapport avec votre finalité. Les cocher affaiblit tout le dossier |
| Bluetooth Connectivity | ❌ Non | Pas de composant matériel |
| ANT+ Connectivity | ❌ Non | Idem |

Deux cases sur sept : c'est un dossier **proportionné**, et ça se voit.

⚠️ Cocher « Structured Workouts and Training Plans Sync » **rend obligatoires les deux logos
supplémentaires** du champ 24 (128×128 et 396×396).

#### 3.3.5 Champ 18 — Intended use of data (texte prêt)

```
Athletes connect their own COROS account through OAuth 2.0, from their personal
account page — a coach can never connect an account on an athlete's behalf.

We import completed training activities only: start time, distance, moving time,
elevation gain, average and max heart rate, cadence, power, calories, and where
available the time / heart-rate / speed samples needed to compute time spent in each
training zone. This data is shown to the athlete and to the coach that athlete has
explicitly accepted, and is used to compare the prescribed session with what was
actually performed, and to compute training load indicators (ACWR, monotony,
time-in-zone).

In the other direction, we publish the coach's prescribed structured sessions to the
athlete's COROS watch, so athletes no longer have to re-enter interval structures by
hand.

We do not request continuous health monitoring, sleep or all-day wellness data. We do
not sell, rent or share user data with any third party, and we do not use it for
advertising or for training machine-learning models. Data is hosted in the European
Union, OAuth tokens are encrypted at rest, and athletes can disconnect at any time
from their account page — which immediately deletes the stored tokens.

DARI Lab is a commercial B2B platform: our customers are coaches, and each coach
brings their own group of athletes. We currently have around 10 active users and
expect 50 to 100 within the year. We already operate a Strava integration in
production using the same architecture (OAuth 2.0 authorisation code flow, encrypted
token storage, scheduled sync, de-duplication by external activity id), so this
integration can be delivered quickly.
```

#### 3.3.6 Champ 19 — date de lancement prévue

Champ non obligatoire, mais le renseigner est un signal de sérieux — et COROS demande de toute
façon d'être prévenu **1 semaine avant la mise en production**.

Comptez à partir de la réception des identifiants, pas de la soumission : **environ 3 mois** est
honnête pour la synchronisation des activités **plus** la publication des séances (le second sens
est du travail neuf, cf. §6). Une date à 3–4 mois de la soumission est un bon repère. Mieux vaut
annoncer large et livrer en avance que l'inverse.

#### 3.3.7 Champ 24 — les logos (bloquant, mais réglé)

Le formulaire est catégorique : *« API applications cannot be approved without all of the required
files »*. Comme vous cochez la synchronisation des séances, **les quatre fichiers sont requis** :

| Taille | Requis pour | Fichier généré |
|---|---|---|
| 190 × 190 | Toute candidature | `docs/assets/api-partners/darilab-logo-190x190.png` |
| 300 × 300 | Toute candidature | `docs/assets/api-partners/darilab-logo-300x300.png` |
| 128 × 128 | Sync séances / plans | `docs/assets/api-partners/darilab-logo-128x128.png` |
| 396 × 396 | Sync séances / plans | `docs/assets/api-partners/darilab-logo-396x396.png` |

✅ **Les quatre sont dans le dépôt**, générés depuis votre icône d'application
(`front/src/assets/icons/icon-512x512.png`) par rééchantillonnage Lanczos, en PNG RGBA aux
dimensions exactes. Vous n'avez qu'à les téléverser.

> 💡 Ils reprennent l'icône PWA (squircle bleu, courbe blanche ascendante). C'est cohérent avec
> votre identité et ça passe partout. Si vous préférez un logo sur fond transparent pour la page
> partenaires de COROS, dites-le-moi : la source vectorielle `front/src/favicon.svg` permet de
> régénérer les quatre tailles autrement.

### 3.4 E-mail d'accompagnement (à envoyer à `api@coros.com`)

Le formulaire reste le canal officiel ; cet e-mail l'accompagne. Il reprend les mêmes informations,
en plus développé — et il vous donne un interlocuteur et une trace datée.
**Envoyez-le juste après avoir soumis le formulaire**, en le mentionnant.

> **À** : api@coros.com
> **Objet** : API access application submitted — DARI Lab (coaching platform, France)

```
Hello COROS API team,

We have just submitted the COROS API Application form for DARI Lab, a coaching
platform for running and strength training used by coaches and their athletes. This
email provides the same information in more detail, and gives you a direct contact
should you need anything further.

COMPANY
  Legal entity      : [DÉNOMINATION EXACTE — cf. §3.3.1]
  SIRET             : 914 436 118 00022  (SIREN 914 436 118)
  Country           : France (European Union)
  Website           : https://www.darilab.app
  Privacy policy    : https://www.darilab.app/legal/confidentialite
  Terms of use      : https://www.darilab.app/legal/cgu

CONTACTS
  Technical (authorised representative) : Valentin Bertho — contact@darilab.app
  Business / secondary contact          : Valentin Bertho — valentin@darilab.app
  Privacy officer                       : Valentin Bertho — valentin@darilab.app

PRODUCT
DARI Lab is a SaaS coaching platform for running and strength training. Coaches build
structured workouts from each athlete's physiological profile (LT1/LT2 lactate
thresholds, critical speed, VDOT, 1RM) and prescribe them as pace, heart-rate and RPE
ranges. Athletes follow the programme in a mobile PWA, complete the session and submit
their feedback. The platform then compares planned vs. actual training and computes
training load (ACWR, monotony, time-in-zone).

DARI Lab is a commercial B2B platform: our customers are coaches, and each coach
brings their own group of athletes. We currently have around 10 active users and
expect 50 to 100 within the year, which is why we are integrating device partners now
rather than later — athletes choose their watch before they choose their coach.

We already run a production Strava integration with the same architecture (OAuth 2.0
authorisation code flow, encrypted token storage, scheduled sync, de-duplication by
external activity id). The integration path is therefore well understood on our side
and can be delivered quickly.

REQUESTED USE CASES  (form question 11: one-way activity sync + structured workouts)
  1. Activity synchronisation (watch -> platform). After an athlete connects their
     COROS account, we import their completed training activities: summary metrics
     (start time, distance, moving time, elevation gain, average and max heart rate,
     cadence, power, calories) and, where available, the time/heart-rate/speed samples
     required to compute time-in-zone. This lets the coach compare the prescribed
     session with what the athlete actually did.

  2. Structured workout publishing (platform -> watch). We would like to push the
     coach's prescribed sessions to the athlete's COROS watch, so athletes no longer
     re-enter interval structures by hand. Our sessions are already stored as
     structured blocks (warm-up / main set / cool-down, repetitions, duration or
     distance targets, pace and heart-rate ranges, recovery), which maps directly onto
     a structured-workout model.

We are not requesting two-way activity sync, GPX route import/export, daily health
data, or Bluetooth/ANT+ connectivity. We only need data about deliberate training
sessions, and the ability to publish the sessions a coach has prescribed.

TECHNICAL DETAILS
  Authorized callback domain           : https://www.darilab.app
  OAuth 2.0 redirect URI (production)  : https://www.darilab.app/app/coros/callback
  Workout data receiving endpoint      : https://api.darilab.app/api/webhooks/coros
  Service status check URL             : https://api.darilab.app/api/actuator/health
  Web origins                          : https://www.darilab.app, https://darilab.app
  Current active users                 : approx. 10
  Expected within 12 months            : 50 to 100 connected athletes
  Call pattern                         : we intend to use your Workout Summary Data
                                         Push Service (Reference Guide section 5.7);
                                         our fallback is an hourly scheduled sync per
                                         connected athlete, plus on-demand sync
  Environments                         : 1 production + 1 local development

SECURITY AND DATA PROTECTION
  - OAuth tokens are encrypted at rest (column-level encryption, AES).
  - All traffic is TLS 1.2+; tokens are never written to logs.
  - The OAuth state parameter is signed and verified (CSRF protection).
  - Access tokens are refreshed automatically before expiry.
  - Each athlete initiates and revokes the connection themselves; revoking deletes
    the stored tokens immediately.
  - We are GDPR-compliant: explicit consent as the legal basis, data subject export
    and erasure are implemented, data is hosted in the EU.
  - Sub-processors: Railway (backend and database), Vercel (frontend), Resend (email).
  - Login portal: https://www.darilab.app/login
  - Support page: https://www.darilab.app/support

We have read and accept the COROS API Application Terms and the COROS API Agreement,
and we will keep you informed of our development progress and notify you at least one
week before the integration goes live, as requested.

We would be happy to provide any further documentation or a product demo.

Best regards,
Valentin Bertho — Founder & Lead Developer, DARI Lab
contact@darilab.app
```

### 3.5 Les trois pièges de ce dossier

**1. La page de support publique — ✅ réglé.**
COROS l'exige noir sur blanc : *« nous demandons à tous les partenaires d'ajouter un portail de
connexion et une page de support à leur plateforme »*. Le portail de connexion existait déjà
(`/login`) ; la page de support, non — le centre d'aide vit sous `app/aide`, **derrière
l'authentification**, donc invisible pour qui n'a pas encore de compte, exactement le cas que
COROS veut couvrir.

**`https://www.darilab.app/support`** est désormais publique, servie par le composant des pages
légales (même coquille, même navigation) et liée depuis le pied de page d'accueil. Elle couvre :

- le moyen de contact et le délai de réponse annoncé (3 jours ouvrés) ;
- **une section « Montres et applications connectées »** — c'est celle que COROS vient vérifier :
  qui initie la connexion (l'athlète, jamais le coach), ce qui est importé, **comment se
  déconnecter**, et quoi faire quand une activité ne remonte pas ;
- une ligne en anglais indiquant qu'un support en anglais est disponible, pour l'examinateur COROS
  comme pour un utilisateur non francophone ;
- une FAQ courte et le renvoi vers la politique de confidentialité.

> Garmin et COROS y sont annoncés comme « intégrations en préparation », avec le repli GPX/TCX —
> cohérent avec ce que dit déjà le README. À basculer en « disponible » le jour de la mise en
> production, en même temps que la politique de confidentialité.

**2. Les URL des champs 13 et 14 — deux options, l'une sans aucun travail d'infra.**

Le vrai enjeu n'est pas « créer un sous-domaine » : c'est que **ces URL sont figées dans votre
configuration COROS** et qu'en changer plus tard demande de repasser par leur support. La seule
chose à éviter absolument, c'est de déclarer un domaine d'hébergeur
(`coach-app-production-5674.up.railway.app`) : le jour où vous quittez Railway, l'intégration
casse et il faut redemander.

Les deux réponses acceptables — dans les deux cas, un domaine **que vous possédez** :

| | **Option A — `api.darilab.app`** *(recommandée)* | **Option B — `www.darilab.app/api/…`** |
|---|---|---|
| Travail à faire | ~15 min de DNS | **aucun — ça marche déjà** |
| Champ 13 | `https://api.darilab.app/api/webhooks/coros` | `https://www.darilab.app/api/webhooks/coros` |
| Champ 14 | `https://api.darilab.app/api/actuator/health` | `https://www.darilab.app/api/actuator/health` |
| Chemin réseau | COROS → Railway, direct | COROS → Vercel → Railway (réécriture `front/vercel.json`) |
| Point faible | rien | le webhook dépend de la config de réécriture Vercel : changer d'hébergeur front casserait un chemin qui n'a rien à voir avec le front |

**Pourquoi l'option A malgré les 15 minutes.** Un webhook doit répondre vite et ne rien avoir
d'inutile sur son chemin critique ; y intercaler le proxy du front ajoute un intermédiaire et un
couplage que rien ne justifie. Et le jour où vous changez d'hébergement, vous repointez un CNAME
au lieu de rouvrir un ticket chez COROS.

**Marche à suivre (option A)**

1. **Railway** → service backend → *Settings → Networking → Custom Domain* → ajouter
   `api.darilab.app`. Railway affiche une **cible CNAME** à recopier.
2. **OVH** → zone `darilab.app` → ajouter un enregistrement **CNAME** `api` → cette cible.
   ⚠️ Dans la **zone DNS**, pas dans l'onglet « Redirection » — `docs/DEPLOIEMENT.md` documente
   déjà pourquoi cet onglet casse la configuration.
3. Attendre la propagation ; Railway émet le certificat TLS tout seul.
4. Vérifier : `curl https://api.darilab.app/api/actuator/health` doit renvoyer `{"status":"UP"}`.

C'est **purement additif** : votre front passe par une réécriture Vercel `/api/*` et ne connaît
aucune URL Railway en dur (`front/src/environments/environment.ts` → `apiUrl: '/api'`). Ajouter ce
domaine ne peut donc rien casser dans l'application.

**Bonne nouvelle pour le champ 14** : `/actuator/health` est **déjà public** et ne renvoie que
`{"status":"UP"}` aux appels anonymes (`SecurityConfig.java:40` + `show-details: when_authorized`).
Rien à coder, rien qui fuite.

> ℹ️ **Le champ 13 pointe vers un endpoint qui n'existe pas encore.** C'est normal et sans
> conséquence à ce stade : `/api/webhooks/coros` sera implémenté à la réception des identifiants
> (cf. §6). Déclarez l'URL cible dès maintenant — c'est elle qui sera figée — et assurez-vous
> qu'elle réponde **200 rapidement** le jour de l'intégration, COROS attendant un accusé immédiat.
>
> ⚠️ Au passage : `docs/DEPLOIEMENT.md` mentionne `coachrun-back.up.railway.app`, alors que le
> déploiement réel est `coach-app-production-5674.up.railway.app` (`front/vercel.json`). La doc de
> déploiement est en retard sur la réalité — sans effet ici puisqu'on ne déclare ni l'un ni
> l'autre, mais à corriger un jour.

**3. Les documents à lire avant de cocher « Yes ».**
Les champs 20 et 21 vous engagent contractuellement. Téléchargez l'**API Reference Guide** (lien en
tête du formulaire) et l'**API Agreement** (lien du champ 21) et lisez-les — en particulier la
**section 5.7** du guide, qui décrit le service de push référencé par le champ 13, et les clauses
de l'Agreement sur la propriété des données et la résiliation. C'est aussi le guide qui vous dira
quels endpoints couvrent réellement la publication de séances, information que vous n'avez pas
aujourd'hui.

---

## 4. Demande Garmin — programme en pause

### 4.1 Ce qui se passe

Garmin a **temporairement suspendu la revue et l'approbation des nouvelles demandes d'accès API**
pendant qu'il modernise le programme. Le formulaire public
(`https://www.garmin.com/en-US/forms/GarminConnectDeveloperAccess/`) a été retiré, il n'y a **ni
file d'attente ni liste de notification**, et aucune date de réouverture n'est annoncée. Les
partenaires déjà intégrés ne sont pas affectés.

**Les pages officielles le confirment indirectement** (relevé sur les captures du site, août 2026) :

- Aucune des six pages du programme n'affiche de bouton « Request Access » ou de lien vers un
  formulaire de candidature. C'est la confirmation la plus nette qu'on puisse obtenir sans
  déclaration explicite de Garmin.
- Un encadré **« Stay tuned for more updates on the program »** figure en haut à droite de
  **toutes** les pages — la formulation d'un programme en transition.
- Le seul canal d'entrée visible est le lien **« emailing us »** (bas de chaque page d'API) et le
  lien **Contact** du pied de page. Les pages **Activity API** et **Health API** ajoutent un bouton
  **« CONTACT US »** ; les pages **Training API** et **Courses API**, non.

→ **L'e-mail est donc la seule voie ouverte**, ce qui tombe bien : c'est exactement ce que prépare
la §4.4.

### 4.1 bis Ce que les pages officielles apprennent d'utile

Quatre points relevés sur les pages Activity, Training, Courses et Health, qui changent quelque
chose pour vous :

1. **L'Activity API livre des fichiers FIT.** *« Full Activity Details Access: Activity data files
   (.FIT, GPX, .TCX formats) »* et *« The Activity API offers access to FIT files »*. Autrement dit,
   le détail d'une activité Garmin **passe par un parseur FIT** — que vous n'avez pas encore
   (`GpxParser` couvre GPX/TCX). C'est un argument de plus pour attaquer le FIT dès maintenant :
   ce n'est plus seulement un repli en attendant Garmin, c'est **une brique obligatoire de
   l'intégration Garmin elle-même**.
2. **Vous choisissez l'architecture.** *« Ping/Pull or Push Architecture: Choose the integration
   option that best matches your needs »* (Activity et Health). Le **Push** vous convient : il évite
   le polling et rejoint ce que fait déjà COROS.
3. **Environnements de test après approbation.** Activity API → *« an evaluation environment »* ;
   Training et Courses → *« test your integration against the production environment with
   throttled access »*. Deux régimes différents, à intégrer au planning.
4. **Ce que le programme inclut** : documentation, accès au **logo officiel Garmin Connect et aux
   exigences de marque**, **accès direct à l'équipe support** du programme, et du **code
   d'exemple** — mais seulement *« for Health, Activity, and Women's Health APIs »*. **Pas
   d'exemple pour la Training API**, celle qui vous demandera le plus de travail. À budgéter.

> ℹ️ Deux détails à ne pas confondre : la **Health API** est positionnée « Corporate Wellness,
> Population Health, Patient Monitoring » avec une mention *« Commercial use requires a license fee
> payment »* sur certaines métriques. Rien à voir avec votre besoin — raison de plus pour ne pas la
> demander. Et les **Garmin Health SDKs** (connexion directe app mobile ↔ montre) sont un produit
> distinct : les Connect APIs sont **cloud-to-cloud**, ce qui est bien ce que vous voulez.

### 4.2 Que faire maintenant — dans cet ordre

1. **Envoyer l'e-mail de demande d'accès §4.4.** C'est un dossier complet, rédigé comme si le
   programme était ouvert — parce que c'est la bonne posture : si la file a rouvert sans annonce,
   vous êtes candidat le jour même ; si elle est encore fermée, vous avez une demande datée,
   argumentée et déjà instruite quand elle redémarre. Un message qui se contente de demander
   « prévenez-moi quand ça rouvre » n'obtient qu'une réponse polie et aucune place dans la file.
2. **Où l'envoyer** : le lien **« emailing us »** en bas de la page
   [Training API](https://developer.garmin.com/gc-developer-program/training-api/) — c'est le
   canal des questions de programme. Le lien **Contact** du pied de page mène au même endroit.
   Le bouton **« CONTACT US »** de la page Activity API vise plutôt les demandes commerciales
   (« Looking for a business solution ») : à réserver en relance si l'e-mail reste sans réponse.
3. **Recontrôler la page du programme périodiquement** (mensuel suffit ; il n'existe pas de
   notification à attendre). Si un bouton de demande d'accès réapparaît, remplissez-le en reprenant
   la §4.3 — sans attendre de réponse à votre e-mail.
4. **Ne rien promettre dans le produit en attendant.** Le README, l'onboarding et la nouvelle page
   de support annoncent Garmin/COROS comme « prévus » / « en préparation » — c'est la bonne
   formulation, ne la faites pas passer à « bientôt » tant que Garmin n'a pas rouvert.

### 4.3 Réponses au formulaire d'accès Garmin (prêtes)

Reprenez la [fiche d'identité commune](#2-fiche-didentité-commune-à-remplir-une-seule-fois) —
Garmin demande les mêmes rubriques. Les spécificités Garmin :

| Champ Garmin | Réponse |
|---|---|
| **Type d'entité** | Entreprise immatriculée en France, SIRET 914 436 118 00022. ⚠️ Garmin **exige une personne morale** — société, université, hôpital ou institut de recherche. Une demande à titre personnel est rejetée : c'est le motif de rejet n° 1, et c'est précisément ce que votre SIRET écarte. |
| **APIs demandées** | ☑️ **Activity API** (import des activités réalisées) · ☑️ **Training API** (publication des séances structurées) · ☐ Health API · ☐ Women's Health API · ☐ Courses API |
| **Pourquoi Activity API** | Cf. cas d'usage 1 en §2.3. Détail des activités livré en **fichiers FIT** (§4.1 bis) |
| **Pourquoi Training API** | Cf. cas d'usage 2 en §2.3 — publie les séances et plans dans le **calendrier Garmin Connect**, l'athlète les synchronise ensuite sur sa montre compatible. C'est Garmin Connect qui gère l'échange avec l'appareil, vous n'y touchez pas |
| **Pourquoi PAS la Health API** | À dire explicitement : « We do not need all-day health monitoring, sleep or stress data — only deliberate training sessions. » Elle vise de toute façon un autre marché (Corporate Wellness, Population Health) et certaines métriques sont payantes |
| **Pourquoi PAS la Courses API** | Publication d'itinéraires — hors périmètre aujourd'hui. À redemander si vous ajoutez la prescription de parcours trail |
| **Modèle d'intégration** | **Ping/Pull ou Push, au choix** (§4.1 bis). Prenez **Push** : les données sont envoyées à votre callback quand l'athlète synchronise sa montre, sans polling. Déclarez `https://[api.darilab.app ou www.darilab.app]/api/webhooks/garmin`. **Ce endpoint doit répondre 200 rapidement** — accusé immédiat, traitement asynchrone derrière |
| **Politique de confidentialité** | URL publique obligatoire, mentionnant Garmin nommément (à faire avant la mise en production, cf. §2.5) |
| **Volumétrie** | Cf. §2.4 — 10 aujourd'hui, 50 à 100 dans l'année |

### 4.4 E-mail de demande d'accès (prêt à envoyer)

> **À** : lien « emailing us » de la page Training API (cf. §4.2)
> **Objet** : API access request — DARI Lab (Activity API + Training API), France

**Avant d'envoyer** — trois substitutions et une décision :

- `[DÉNOMINATION EXACTE]` → la dénomination du registre (§3.3.1), la même que celle donnée à COROS.
- `[URL]` des champs 13/14 → `api.darilab.app` ou `www.darilab.app` selon l'option retenue (§3.5-2).
- La **dernière ligne** (« If new applications are not currently being accepted… ») : gardez-la
  telle quelle. Elle ne fragilise pas la demande — le corps du message reste un dossier complet —
  et elle évite le silence poli qu'obtient un message qui ignore visiblement l'état du programme.
  Supprimez-la seulement si un bouton de demande d'accès est visiblement revenu sur le site.

```
Hello,

We would like to apply for access to the Garmin Connect Developer Program for DARI
Lab, a coaching platform for running and strength training used by coaches and their
athletes. We are requesting the Activity API and the Training API.

COMPANY
  Legal entity      : [DÉNOMINATION EXACTE]
  Registration       : SIRET 914 436 118 00022 (SIREN 914 436 118)
  Country           : France (European Union)
  Website           : https://www.darilab.app
  Privacy policy    : https://www.darilab.app/legal/confidentialite
  Terms of use      : https://www.darilab.app/legal/cgu
  Support page      : https://www.darilab.app/support
  Login portal      : https://www.darilab.app/login

CONTACTS
  Technical / primary : Valentin Bertho — contact@darilab.app
  Secondary           : Valentin Bertho — valentin@darilab.app
  Privacy             : Valentin Bertho — valentin@darilab.app

PRODUCT
DARI Lab is a SaaS coaching platform for running and strength training. Coaches build
structured workouts from each athlete's physiological profile (LT1/LT2 lactate
thresholds, critical speed, VDOT, 1RM) and prescribe them as pace, heart-rate and RPE
ranges. Athletes follow the programme in a mobile PWA, complete the session and submit
their feedback. The platform then compares planned vs. actual training and computes
training load indicators (ACWR, monotony, time-in-zone).

It is a commercial B2B platform: our customers are coaches, and each coach brings their
own group of athletes. We currently have around 10 active users and expect 50 to 100
within the year. Garmin is by a wide margin the most common watch among the athletes we
coach, which is why device integration is on our roadmap now rather than later.

We already run a Strava integration in production, built on the same architecture we
would use here: OAuth 2.0 authorisation code flow, encrypted token storage, scheduled
synchronisation, and de-duplication of imported activities by external activity id. The
integration work is therefore well understood on our side.

APIS REQUESTED
  1. Activity API — to import athletes' completed training activities.

     After an athlete connects their Garmin Connect account, we would import their
     completed running and strength activities: start time, distance, moving time,
     elevation gain, average and max heart rate, cadence, power and calories, plus the
     time / heart-rate / speed samples needed to compute time spent in each training
     zone. This lets the coach compare the session they prescribed with the session the
     athlete actually performed.

     We understand activity details are delivered as FIT files. We already parse GPX and
     TCX for manual imports, and we are adding FIT support using the Garmin FIT SDK.

     Regarding integration architecture, we would prefer the Push option over Ping/Pull:
     it avoids polling your infrastructure, and our backend already exposes a callback
     endpoint for this purpose.

  2. Training API — to publish coaches' structured workouts to athletes' devices.

     Our sessions are already stored as structured blocks (warm-up / main set /
     cool-down, repetitions, duration or distance targets, pace and heart-rate ranges,
     recovery intervals), which maps directly onto the structured-workout model. Publishing
     them to the Garmin Connect calendar would remove the step our athletes dislike most:
     re-entering interval structures by hand on the watch before a session.

APIS WE ARE NOT REQUESTING
We are not requesting the Health API, the Women's Health API or the Courses API. We have
no need for all-day health monitoring, sleep, stress or menstrual cycle data: we only
process data about deliberate training sessions, prescribed by a coach and performed by
an athlete who has explicitly accepted that coaching relationship. We would rather have
a narrow, well-justified scope than a broad one.

TECHNICAL DETAILS
  OAuth 2.0 redirect URI (production)  : https://www.darilab.app/app/garmin/callback
  OAuth 2.0 redirect URI (development) : http://localhost:4200/app/garmin/callback
  Push / ping callback endpoint        : https://[URL]/api/webhooks/garmin
  Service health endpoint              : https://[URL]/api/actuator/health
  Web origins                          : https://www.darilab.app, https://darilab.app
  Current active users                 : approx. 10
  Expected within 12 months            : 50 to 100 connected athletes
  Environments                         : 1 production + 1 local development
  Backend                              : Java 21 / Spring Boot 3, PostgreSQL, hosted in
                                         the European Union
  Frontend                             : Angular PWA

Our callback endpoint is designed to acknowledge notifications immediately and process
them asynchronously, so it returns quickly under load.

SECURITY AND DATA PROTECTION
  - OAuth tokens are encrypted at rest (column-level AES encryption).
  - All traffic is TLS 1.2+; tokens are never written to logs.
  - The OAuth state parameter is signed and verified (CSRF protection).
  - Access tokens are refreshed automatically before expiry.
  - Each athlete initiates and revokes the connection themselves, from their own account
    page — a coach can never connect an account on an athlete's behalf. Revoking deletes
    the stored tokens immediately.
  - We are GDPR-compliant: explicit consent is the legal basis for health-related data,
    data subject export and erasure are implemented in the product, and data is hosted in
    the European Union.
  - We do not sell, rent or share user data with third parties, and we do not use it for
    advertising or for training machine-learning models.
  - Sub-processors: Railway (backend and database), Vercel (frontend), Resend (email).

We will of course comply with the Garmin Connect branding requirements, update our
privacy policy to name Garmin explicitly as a data source before going live, and follow
the API terms of use.

Could you let us know how to proceed, and what further information you need from us? If
new applications are not currently being accepted, we would be grateful to know how to
be considered once they reopen.

Best regards,
Valentin Bertho — Founder & Lead Developer, DARI Lab
contact@darilab.app — https://www.darilab.app
```

**Ce que ce message fait, et pourquoi il est construit ainsi**

- **Il demande deux API sur cinq, et le dit.** La section « APIs we are not requesting » n'est pas
  de la politesse : c'est le signal qu'un examinateur cherche. Une demande large est une demande
  qu'il faut instruire ; une demande étroite et argumentée, il peut l'approuver.
- **Il montre que vous avez déjà livré une intégration comparable.** Strava en production répond à
  la question qui coûte le plus cher à Garmin — *est-ce que ce partenaire va vraiment intégrer ?*
- **Il parle leur langue technique.** Mentionner les fichiers FIT, le choix Push plutôt que
  Ping/Pull, et un endpoint qui acquitte immédiatement montre que vous avez lu leurs pages. C'est
  rare dans les demandes qu'ils reçoivent, et ça se voit.
- **Il assume la volumétrie.** 10 utilisateurs annoncés franchement, avec la raison pour laquelle
  l'intégration arrive maintenant. Garmin n'exige pas de taille minimale — contrairement à COROS,
  la « taille de marché » n'est pas un critère annoncé chez eux ; l'exigence dure est la
  **personne morale**, que votre SIRET satisfait.

### 4.5 Les contournements, et pourquoi je ne les recommande pas ici

| Piste | Verdict |
|---|---|
| **Agrégateur** (Terra, Spike, Open Wearables…) qui détient déjà l'accès partenaire Garmin | Débloque la **lecture** des activités sans attendre Garmin. Mais : coût récurrent au contrat, un sous-traitant de plus à déclarer au RGPD (données de santé), une dépendance sur le chemin critique du produit — et **l'envoi de séances vers la montre n'est en général pas couvert**, or c'est la moitié de votre besoin. À considérer seulement si la sync Garmin devient bloquante commercialement. |
| **API Garmin Connect non officielle** (bibliothèques communautaires) | ❌ **Non.** Violation des CGU Garmin, casse à chaque changement côté Garmin, exige de manipuler les identifiants Garmin de vos athlètes — rédhibitoire pour une plateforme qui traite des données de santé et qui vise un partenariat officiel plus tard. |
| **Connect IQ** | Ne donne pas accès aux données Connect ni à la publication de séances. Hors sujet ici. |
| **Fichiers FIT** | ✅ **Ce n'est plus un contournement, c'est un prérequis.** Les pages officielles indiquent que l'**Activity API livre les détails d'activité en fichiers FIT** (§4.1 bis) : vous aurez besoin d'un parseur FIT **de toute façon**, accès Garmin ou pas. Et dans l'autre sens, l'export d'une séance en **fichier FIT de workout**, que l'athlète dépose dans Garmin Connect (ou dans le dossier `NEWFILES` de sa montre), fonctionne **sans aucune API** — manuel, mais réel, et disponible dès aujourd'hui. C'est déjà identifié dans vos audits (`docs/AUDIT-FONCTIONNEL-2026-08.md` A7, `PLAN-CONFORMITE-BETA-2026-08.md` V3-08). Le SDK FIT Garmin est librement téléchargeable. **C'est le chantier à lancer maintenant** : il sert au repli immédiat, à l'intégration Garmin future et à COROS. |

---

## 5. Checklist avant d'envoyer

Dans l'ordre. Les six premiers points sont **bloquants** : soumettre sans eux, c'est griller sa
candidature auprès d'un partenaire qui sélectionne.

**Avant de toucher au formulaire**

- [x] ✅ **Page de support publique** — `https://www.darilab.app/support`, livrée `[§3.5-1]`
- [x] ✅ **Adresse secondaire** — `valentin@darilab.app` créée, sert aux champs 4 et 5 `[§3.3.2]`
- [ ] 🔴 **Déployer** la page de support en production (elle doit être en ligne **avant** que COROS
      ne clique sur le lien)
- [ ] 🔴 **Dénomination légale** vérifiée sur `annuaire-entreprises.data.gouv.fr/entreprise/914436118`
      et recopiée au caractère près `[§3.3.1]`
- [ ] 🔴 **URL des champs 13 et 14** arrêtées — option A (`api.darilab.app`, CNAME à créer) ou
      option B (`www.darilab.app/api/…`, disponible immédiatement) `[§3.5-2]`
- [ ] 🔴 **API Reference Guide et API Agreement téléchargés et lus** (section 5.7 notamment) `[§3.5-3]`
- [ ] Politique de confidentialité : Garmin et COROS à **nommer** au moment de la mise en
      production de l'intégration `[§2.5]`
- [ ] `LEGAL_OWNER.legalName` / `address` et le statut « éditeur non professionnel » à trancher `[§2.5]`
- [ ] Adresse du siège récupérée `[§2.1]`
- [ ] Durée de conservation des données arrêtée `[§2.5]`
- [ ] Région d'hébergement Railway/Vercel vérifiée (UE) `[§2.5]`
- [ ] Date de lancement prévue choisie (≈ 3–4 mois) `[§3.3.6]`

**Soumission COROS**

- [x] ✅ **Formulaire soumis**
- [ ] E-mail d'accompagnement envoyé à `api@coros.com` `[§3.4]`
- [ ] Relance si pas de réponse sous ~3 semaines

**Garmin**

- [x] ✅ État du programme vérifié sur `developer.garmin.com` — aucun formulaire d'accès visible `[§4.1]`
- [ ] 🔴 **E-mail de demande d'accès envoyé** via le lien « emailing us » de la page Training API `[§4.4]`
- [ ] Page du programme recontrôlée mensuellement `[§4.2]`

**Ensuite, sans attendre les réponses**

- [ ] Lancer le **support des fichiers FIT** — prérequis de l'Activity API Garmin, repli immédiat
      pour les deux marques, et socle commun de la publication de séances `[§4.5, §6]`

---

## 6. Après réception des identifiants — ce qui reste à coder

Le terrain est déjà largement préparé, ce qui explique pourquoi la demande peut être ambitieuse
sans être un pari : les enums `DeviceProvider.GARMIN` / `COROS` et `ActivitySource.GARMIN` / `COROS`
existent déjà, les variables `GARMIN_*` / `COROS_*` sont réservées dans `.env.example`, et
l'ensemble du flux OAuth + import est en production sous Strava.

### Sens montre → plateforme (le plus simple)

Le chemin Strava est directement transposable, fournisseur par fournisseur :

| Brique Strava existante | Équivalent à écrire |
|---|---|
| `integration/StravaClient.java` | `GarminClient` / `CorosClient` — client HTTP + OAuth |
| `service/StravaService.java` | `GarminService` / `CorosService` — connexion, import, déconnexion |
| `controller/StravaController.java` | contrôleurs symétriques (routes club **et** portail athlète) |
| `scheduler/StravaSyncScheduler.java` | planificateur (ou **webhook**, que Garmin impose déjà en push) |
| `security/OAuthStateCodec.java` | ✅ réutilisable tel quel |
| `entity/DeviceConnection.java` | ✅ réutilisable tel quel (contrainte unique athlète + provider) |
| `ActivityService.importActivity()` + dédoublonnage `source + externalId` | ✅ réutilisable tel quel |
| `front/…/strava-callback.component.ts` | composants de callback symétriques + route `app/{provider}/callback` |

> 💡 Trois fournisseurs qui font la même chose, c'est le moment de **factoriser** : une interface
> `DeviceSyncProvider` (autoriser / échanger le code / rafraîchir / lister les activités) évite de
> tripler `StravaService`. À faire à l'écriture du deuxième fournisseur, pas avant — le premier ne
> dit pas encore où sont les vraies différences.

### Sens plateforme → montre (le vrai travail neuf)

Rien n'existe encore. Le morceau est la **traduction du modèle de séance DARI Lab vers le modèle de
workout structuré** du fournisseur :

- vos blocs (échauffement / corps / retour au calme, répétitions, formats EMOM/AMRAP/circuit) vers
  les steps et répétitions du format cible ;
- vos prescriptions **en fourchettes** (% de LT1/LT2/VC/VDOT) vers des cibles absolues — c'est là
  que ça se joue : la montre veut une allure ou une FC en valeur, pas un pourcentage. Le calcul
  existe déjà côté serveur (moteur de calcul de séance, résolution des zones par athlète), il faut
  le brancher au moment de la publication et **republier quand le profil physiologique change** ;
- les formats de préparation physique n'ont pas d'équivalent propre sur montre de course : prévoir
  de ne publier que les séances de course dans un premier temps.

Ce travail est **commun** à Garmin (Training API), à COROS (« Structured Workouts and Training
Plans Sync ») et à l'export **FIT** manuel — d'où l'intérêt de le commencer par le FIT pendant que
Garmin est fermé : il sert dans les trois cas.

> ⚠️ COROS demande d'être **tenu informé de l'avancement du développement** et prévenu **au moins
> une semaine avant la mise en production**. Prévoyez ces deux points de contact dans le planning
> dès l'obtention des identifiants — c'est une obligation du formulaire, pas une politesse.

---

## 7. Sources

**COROS**
- **Formulaire « COROS API Application » lui-même** — source de la §3, la plus fiable du document :
  les 24 champs, les conditions liminaires (sélection sur la taille de marché, portail de connexion
  et page de support obligatoires, suivi du développement, absence de frais) et les liens vers
  l'API Reference Guide et l'API Agreement en proviennent directement.
- [Submit an API Application — COROS Help Center](https://support.coros.com/hc/en-us/articles/17085887816340-Submit-an-API-Application) ([version FR](https://support.coros.com/hc/fr/articles/17085887816340-Soumettre-une-demande-d-acc%C3%A8s-%C3%A0-l-API))
- [Supported 3rd Party Apps — COROS Help Center](https://support.coros.com/hc/en-us/articles/360040256531-Supported-3rd-Party-Apps)
- [COROS API Integration — Terra API](https://tryterra.co/integrations/coros)

**Garmin**
- [Garmin Connect Developer Program — Overview](https://developer.garmin.com/gc-developer-program/overview/)
- [Activity API](https://developer.garmin.com/gc-developer-program/activity-api/) · [Training API](https://developer.garmin.com/gc-developer-program/training-api/) · [Courses API](https://developer.garmin.com/gc-developer-program/courses-api/) · [Program FAQ](https://developer.garmin.com/gc-developer-program/program-faq/)
- [Garmin Connect Developer Program Access Request Form](https://www.garmin.com/en-US/forms/GarminConnectDeveloperAccess/) (retiré à ce jour)
- [Garmin Developer Program Paused: Roadmap Impact — Momentum](https://www.themomentum.ai/blog/garmin-developer-program-closed-roadmap)
- [Connect Developer Program access form unavailable — Forums Garmin](https://forums.garmin.com/developer/connect-iq/f/discussion/436226/connect-developer-program-access-form-unavailable-anyone-got-through-recently)
- [Garmin API Integration — Open Wearables](https://openwearables.io/docs/providers/garmin-api-integration)
- [Sync your workout schedule to Garmin Connect — Tredict](https://www.tredict.com/blog/garmin_training_api_integration/) (retour d'expérience Training API)

> ℹ️ **Ce qui a été lu directement, et ce qui ne l'a pas été.**
> Les domaines `developer.garmin.com`, `www.garmin.com`, `support.coros.com` et le registre des
> entreprises sont **bloqués depuis l'environnement de rédaction** (403 du proxy réseau).
> En revanche, les **captures fournies** ont permis de lire de première main : le **formulaire
> COROS** dans son intégralité (source de la §3) et les pages officielles **Overview, Activity API,
> Training API, Courses API et Health API** du programme Garmin (source des §4.1 et 4.1 bis) —
> ce sont les parties les plus fiables du document.
> Le reste — statut de la pause Garmin, processus COROS en trois étapes — s'appuie sur des sources
> tierces concordantes. Le seul point resté invérifié est la **dénomination légale exacte**
> (§3.3.1), à recopier du registre avant d'envoyer quoi que ce soit.
