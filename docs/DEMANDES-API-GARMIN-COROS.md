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
| **Statut (août 2026)** | ✅ **Ouvert** — onboarding standardisé | ⛔ **En pause** — nouvelles demandes suspendues |
| **Voie d'entrée** | E-mail à `api@coros.com` + formulaire de candidature | Formulaire d'accès (retiré du site) / formulaire de contact développeur |
| **Qui peut demander** | Plateforme tierce avec représentant technique autorisé | **Personne morale uniquement** (société, université, hôpital, institut de recherche) — les usages personnels sont refusés |
| **Sync activités** | ✅ Activités, données journalières, sommeil, EvoLab | ✅ **Activity API** |
| **Envoi de séances vers la montre** | ⚠️ **À confirmer explicitement** dans la demande (voir §3.4) | ✅ **Training API** (séances structurées + plans, publiés dans le calendrier Garmin Connect puis synchronisés sur la montre) |
| **Délai annoncé** | Vérification d'identité puis émission Client ID / Secret | 2 jours ouvrés pour l'accusé de réception… **quand le programme est ouvert** |
| **Coût** | Termes d'usage standard, non discriminatoires | Gratuit, mais sous validation partenaire |

### Ce que ça implique concrètement

1. **COROS d'abord.** C'est la seule des deux demandes qui peut aboutir aujourd'hui. Elle est aussi
   la plus simple : COROS annonce un processus **standardisé et non discriminatoire** — toute
   plateforme qui satisfait leurs exigences de sécurité et d'exploitation obtient l'accès. Il n'y a
   pas de sélection sur le volume d'utilisateurs ou la notoriété.

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
| Raison sociale | `[À COMPLÉTER]` — dénomination légale exacte |
| Forme juridique / pays | `[À COMPLÉTER]` — ex. SAS, France |
| Numéro d'immatriculation | `[À COMPLÉTER]` — SIREN / SIRET / n° TVA intracommunautaire |
| Adresse du siège | `[À COMPLÉTER]` |
| Site web | `https://www.darilab.app` |
| Contact business | `[À COMPLÉTER]` — nom, fonction, e-mail nominatif du domaine `darilab.app` |
| Contact technique (représentant technique autorisé) | `[À COMPLÉTER]` — nom, e-mail du domaine `darilab.app` |
| Contact sécurité / incident | `[À COMPLÉTER]` — ex. `security@darilab.app` |
| DPO / contact RGPD | `[À COMPLÉTER]` — ex. `privacy@darilab.app` |

> ⚠️ **Utilisez des adresses du domaine `darilab.app`, pas une adresse Gmail.** Les deux
> programmes vérifient l'identité de l'entité ; une adresse personnelle sur un domaine grand public
> est le premier motif de rejet ou d'aller-retour. `contact@darilab.app` figure déjà dans la
> configuration (`VAPID_SUBJECT`), le domaine est donc déjà opérationnel côté e-mail.

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
| **Volumétrie estimée** | `[À COMPLÉTER]` — nombre d'athlètes attendus la 1re année (soyez réaliste : quelques centaines se défend très bien, un chiffre gonflé attire des questions) |
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
| Politique de confidentialité publique | `[À COMPLÉTER]` — **URL publique obligatoire**, elle doit citer nommément Garmin et COROS |
| CGU publiques | `[À COMPLÉTER]` — URL publique |

> 🔴 **Le point bloquant le plus fréquent : la politique de confidentialité.** Les deux programmes
> exigent une URL publique, accessible sans compte, qui mentionne **explicitement** la collecte de
> données via l'API du fournisseur, la finalité, la durée de conservation et le moyen de révoquer.
> Une page « bientôt disponible » suffit à faire rejeter le dossier. Publiez-la **avant** d'envoyer.

---

## 3. Demande COROS — à envoyer maintenant

### 3.1 Le processus officiel (3 étapes)

1. **Soumettre les détails techniques** — un représentant technique autorisé transmet les
   informations de la société, les contacts techniques et les **redirect URIs OAuth 2.0**.
2. **Accepter les conditions d'usage** — conditions standard et non discriminatoires : exigences de
   sécurité, conformité à la protection des données, limites de débit.
3. **Recevoir les identifiants** — après vérification de l'identité et des spécifications de
   sécurité, COROS émet le **Client ID** et le **Client Secret**.

### 3.2 Où envoyer

- **E-mail** : **`api@coros.com`** (équipe developer operations) — c'est le canal fiable.
- **Formulaire de candidature** : l'article du centre d'aide COROS contient un lien « apply to the
  API ». ⚠️ **Le lien exact n'a pas pu être récupéré** — l'accès à `support.coros.com` est bloqué
  depuis cet environnement (403 au niveau du proxy réseau). Ouvrez l'article
  [Submit an API Application](https://support.coros.com/hc/en-us/articles/17085887816340-Submit-an-API-Application)
  (ou sa [version française](https://support.coros.com/hc/fr/articles/17085887816340-Soumettre-une-demande-d-acc%C3%A8s-%C3%A0-l-API))
  et récupérez-y le lien du formulaire. **Faites les deux** : formulaire + e-mail. L'e-mail vous
  donne une trace datée et un interlocuteur.

### 3.3 E-mail prêt à envoyer

> **À** : api@coros.com
> **Objet** : API access application — DARI Lab (coaching platform, France)

```
Hello COROS API team,

We would like to apply for COROS API access for DARI Lab, a coaching platform for
running and strength training used by coaches and their athletes.

COMPANY
  Legal entity      : [RAISON SOCIALE]
  Registration no.  : [SIREN / VAT]
  Country           : [PAYS]
  Website           : https://www.darilab.app
  Privacy policy    : https://www.darilab.app/[URL]
  Terms of use      : https://www.darilab.app/[URL]

CONTACTS
  Technical (authorised representative) : [NOM] — [EMAIL]@darilab.app
  Business                              : [NOM] — [EMAIL]@darilab.app
  Security / incident                   : security@darilab.app
  Data protection                       : privacy@darilab.app

PRODUCT
DARI Lab is a SaaS coaching platform for running and strength training. Coaches build
structured workouts from each athlete's physiological profile (LT1/LT2 lactate
thresholds, critical speed, VDOT, 1RM) and prescribe them as pace, heart-rate and RPE
ranges. Athletes follow the programme in a mobile PWA, complete the session and submit
their feedback. The platform then compares planned vs. actual training and computes
training load (ACWR, monotony, time-in-zone).

We already run a production Strava integration with the same architecture (OAuth 2.0
authorisation code flow, encrypted token storage, scheduled hourly sync), so our
integration work is well understood and can be completed quickly.

REQUESTED USE CASES
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

     Could you confirm whether the COROS API supports publishing structured workouts
     or training plans to a user's watch, and if so which endpoints and scopes cover
     it? If this is not currently available, we would still like to proceed with use
     case 1.

We are not requesting continuous health monitoring, sleep or all-day wellness data.
We only need data about deliberate training sessions.

TECHNICAL DETAILS
  OAuth 2.0 redirect URI (production)  : https://www.darilab.app/app/coros/callback
  OAuth 2.0 redirect URI (development) : http://localhost:4200/app/coros/callback
  Webhook / callback URL               : https://api.darilab.app/api/webhooks/coros
  Web origins                          : https://www.darilab.app, https://darilab.app
  Expected volume, year 1              : approx. [N] connected athletes
  Call pattern                         : hourly scheduled sync per connected athlete,
                                         plus on-demand sync; we will switch to
                                         push notifications if COROS provides them
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

We are happy to accept your standard API Terms of Use and to provide any further
documentation or a product demo.

Best regards,
[NOM] — [FONCTION], DARI Lab
[EMAIL]@darilab.app — [TÉLÉPHONE]
```

### 3.4 La question à ne pas oublier : l'envoi de séances

C'est le point à **poser noir sur blanc** (il est déjà dans l'e-mail ci-dessus). L'API COROS
officielle est bien documentée pour la **récupération** des activités et des données. En revanche,
les capacités de **publication de séances structurées vers la montre** que l'on trouve décrites en
ligne proviennent en bonne partie de projets communautaires qui exploitent l'API **non officielle**
de l'application COROS — ce n'est pas une base sur laquelle bâtir un produit, et ce n'est pas une
garantie que l'API officielle l'expose.

Demander la confirmation dans la demande initiale a trois vertus : vous obtenez une réponse
qui fait foi, vous ne construisez pas sur une hypothèse, et vous ne bloquez pas la synchronisation
des activités (cas d'usage 1) si la réponse est négative.

---

## 4. Demande Garmin — programme en pause

### 4.1 Ce qui se passe

Garmin a **temporairement suspendu la revue et l'approbation des nouvelles demandes d'accès API**
pendant qu'il modernise le programme. Le formulaire public
(`https://www.garmin.com/en-US/forms/GarminConnectDeveloperAccess/`) a été retiré, il n'y a **ni
file d'attente ni liste de notification**, et aucune date de réouverture n'est annoncée. Les
partenaires déjà intégrés ne sont pas affectés.

> ⚠️ Ces informations proviennent de sources tierces (forums Garmin, éditeurs partenaires) : les
> pages officielles `developer.garmin.com` sont **inaccessibles depuis cet environnement** (403 au
> niveau du proxy réseau). **Vérifiez vous-même l'état actuel** en ouvrant
> [developer.garmin.com/gc-developer-program](https://developer.garmin.com/gc-developer-program/)
> et en cherchant le bouton de demande d'accès. Si le formulaire est de retour, le dossier §4.3 est
> directement soumissible.

### 4.2 Que faire maintenant — dans cet ordre

1. **Vérifier** la page officielle du programme. Si le formulaire est réapparu → soumettre le
   dossier §4.3 immédiatement.
2. **Sinon, ouvrir un ticket** via le formulaire de contact développeur Garmin. Ça ne débloque pas
   l'accès, mais ça crée une trace datée et vous met dans le système — utile quand la file
   redémarre. Message court prêt à envoyer en §4.4.
3. **Recontrôler périodiquement** (mensuel suffit ; il n'y a pas de notification à attendre).
4. **En attendant, ne rien promettre dans le produit.** Le README et l'onboarding annoncent déjà
   Garmin/COROS comme « prévus » — cette formulation reste la bonne, ne la faites pas passer à
   « bientôt » tant que Garmin n'a pas rouvert.

### 4.3 Réponses au formulaire d'accès Garmin (prêtes)

Reprenez la [fiche d'identité commune](#2-fiche-didentité-commune-à-remplir-une-seule-fois) —
Garmin demande les mêmes rubriques. Les spécificités Garmin :

| Champ Garmin | Réponse |
|---|---|
| **Type d'entité** | Société commerciale. ⚠️ Garmin **exige une personne morale** — société, université, hôpital ou institut de recherche. Une demande à titre personnel est rejetée. C'est le motif de rejet n° 1. |
| **APIs demandées** | ☑️ **Activity API** (import des activités réalisées) · ☑️ **Training API** (publication des séances structurées) · ☐ Health API · ☐ Women's Health API · ☐ Courses API |
| **Pourquoi Activity API** | Cf. cas d'usage 1 en §2.3 |
| **Pourquoi Training API** | Cf. cas d'usage 2 en §2.3 — la Training API publie les séances structurées et les plans dans le calendrier Garmin Connect, l'athlète les synchronise ensuite sur sa montre compatible |
| **Pourquoi PAS la Health API** | À dire explicitement : « We do not need continuous health monitoring, sleep or all-day wellness data — only deliberate training sessions. » |
| **Modèle de notification** | Garmin fonctionne en **push** : les données sont envoyées à vos URL de callback quand l'utilisateur synchronise sa montre. Déclarez `https://api.darilab.app/api/webhooks/garmin` (Ping/Push). **Prévoyez que ce endpoint réponde en 200 rapidement** — Garmin attend un accusé immédiat et rejoue en cas d'échec. |
| **Politique de confidentialité** | URL publique obligatoire, mentionnant Garmin nommément |
| **Volumétrie** | Cf. §2.4 |

### 4.4 Message de contact prêt à envoyer (pendant la pause)

> **Objet** : Garmin Connect Developer Program — access request while applications are paused

```
Hello,

We understand that new access requests to the Garmin Connect Developer Program are
currently paused while the programme is being modernised. We would like to register
our interest so that we can be considered when applications reopen, and to ask how
best to be notified.

  Company   : [RAISON SOCIALE] ([PAYS], registration no. [SIREN])
  Product   : DARI Lab — https://www.darilab.app
  Use case  : DARI Lab is a SaaS coaching platform for running and strength training.
              We are seeking (1) the Activity API, to import athletes' completed
              training activities so coaches can compare prescribed vs. actual
              sessions, and (2) the Training API, to publish coaches' structured
              workouts to athletes' Garmin devices. We are not requesting the Health
              API — we only need data about deliberate training sessions.
  Status    : We already operate a production Strava integration using the same
              architecture (OAuth 2.0, encrypted token storage, scheduled sync), so
              we can integrate quickly once access is granted.

Could you confirm whether there is any way to be notified when the programme reopens,
or whether we should simply monitor developer.garmin.com?

Thank you,
[NOM] — [FONCTION], DARI Lab
[EMAIL]@darilab.app
```

### 4.5 Les contournements, et pourquoi je ne les recommande pas ici

| Piste | Verdict |
|---|---|
| **Agrégateur** (Terra, Spike, Open Wearables…) qui détient déjà l'accès partenaire Garmin | Débloque la **lecture** des activités sans attendre Garmin. Mais : coût récurrent au contrat, un sous-traitant de plus à déclarer au RGPD (données de santé), une dépendance sur le chemin critique du produit — et **l'envoi de séances vers la montre n'est en général pas couvert**, or c'est la moitié de votre besoin. À considérer seulement si la sync Garmin devient bloquante commercialement. |
| **API Garmin Connect non officielle** (bibliothèques communautaires) | ❌ **Non.** Violation des CGU Garmin, casse à chaque changement côté Garmin, exige de manipuler les identifiants Garmin de vos athlètes — rédhibitoire pour une plateforme qui traite des données de santé et qui vise un partenariat officiel plus tard. |
| **Connect IQ** | Ne donne pas accès aux données Connect ni à la publication de séances. Hors sujet ici. |
| **Fichiers FIT** | ✅ **La vraie bonne piste intermédiaire.** L'export d'une séance en **fichier FIT de workout** que l'athlète dépose dans Garmin Connect (ou directement dans le dossier `NEWFILES` de la montre) fonctionne **sans aucune API**, et l'import FIT dans l'autre sens complète le GPX/TCX déjà en place. C'est déjà identifié dans vos audits (`docs/AUDIT-FONCTIONNEL-2026-08.md` A7, `PLAN-CONFORMITE-BETA-2026-08.md` V3-08). Le SDK FIT Garmin est librement téléchargeable. Manuel pour l'athlète, mais réel, et le travail de modélisation est **exactement celui** que réclamera la Training API le jour venu. |

---

## 5. Checklist avant d'envoyer

À faire dans l'ordre — les quatre premiers points sont bloquants pour les deux dossiers.

- [ ] **Entité légale** : raison sociale, immatriculation, adresse confirmées `[§2.1]`
- [ ] **Politique de confidentialité publiée** à une URL publique, citant Garmin et COROS
      nommément, avec finalité, durée de conservation et moyen de révoquer `[§2.5]`
- [ ] **CGU publiées** à une URL publique
- [ ] **Adresses e-mail sur `darilab.app`** créées (technique, business, sécurité, privacy)
- [ ] **Sous-domaine `api.darilab.app`** en place et pointant sur le back (avant de figer les URL
      de callback chez les fournisseurs) `[§2.4]`
- [ ] Volumétrie année 1 chiffrée et réaliste `[§2.4]`
- [ ] Durée de conservation des données arrêtée `[§2.5]`
- [ ] Région d'hébergement Railway/Vercel vérifiée (UE) `[§2.5]`
- [ ] **COROS** : lien du formulaire récupéré depuis l'article du centre d'aide `[§3.2]`
- [ ] **COROS** : e-mail §3.3 personnalisé et envoyé à `api@coros.com`
- [ ] **GARMIN** : état du programme vérifié sur `developer.garmin.com` `[§4.1]`
- [ ] **GARMIN** : ticket ouvert au support développeur si toujours en pause `[§4.4]`

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

Ce travail est **commun** à Garmin (Training API), à COROS (si confirmé) et à l'export **FIT**
manuel — d'où l'intérêt de le commencer par le FIT pendant que Garmin est fermé : il sert dans les
trois cas.

---

## 7. Sources

**COROS**
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

> ⚠️ Les pages `developer.garmin.com`, `www.garmin.com` et `support.coros.com` n'ont **pas pu être
> lues directement** depuis l'environnement de rédaction (blocage 403 du proxy réseau). Le contenu
> de ce document s'appuie sur les extraits indexés de ces pages et sur des sources tierces
> concordantes. **Relisez les pages officielles avant d'envoyer** — en particulier l'état du
> programme Garmin (§4.1) et le lien du formulaire COROS (§3.2), les deux points les plus
> susceptibles d'avoir changé.
