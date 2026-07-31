# Audit Release Candidate — DARI Lab (juillet 2026)

> **Question posée** : « DARI Lab donne-t-il aujourd'hui suffisamment confiance pour être
> utilisé quotidiennement par des coachs professionnels ? »
>
> **Réponse courte** : le **produit** est prêt — le fond métier est au-dessus de ce que
> proposent la plupart des outils du marché sur la physiologie. Ce qui ne l'est pas, ce sont
> **six détails de bordure** qui se voient tous dans les dix premières minutes : le formulaire
> de connexion ne dit rien quand il échoue, l'e-mail d'invitation athlète n'est pas envoyé
> (le code ne l'implémente pas), les alertes de charge crient au rouge sur tout athlète neuf,
> les e-mails transactionnels sont des fragments HTML nus, l'inscription est ouverte à tous,
> et le premier écran d'un coach affiche « Tout le monde est en forme » alors qu'il n'a aucun
> athlète.
>
> **Aucun de ces six points n'est un chantier.** Compter **2 à 3 jours** de travail ciblé.
>
> Base de l'audit : lecture du code (`back/` 583 fichiers, `front/` 200 composants et services),
> de la configuration (`application*.yml`, `vercel.json`, `angular.json`, workflows CI),
> et de la documentation (`docs/`). Audit de **validation** : il ne reprend pas ce qui a déjà
> été corrigé depuis `AUDIT-BETA-READINESS-2026-07.md`, il vérifie et cherche ce qui reste.

**Légende** : 🔴 bloquant bêta · 🟠 à faire dans les 2 semaines · 🟢 peut attendre

---

## 0. Ce qui a été validé (et qui tient)

Avant les problèmes, ce qui est réellement en place — vérifié dans le code, pas dans la doc :

| Vérifié | Constat |
|---|---|
| Design system | `styles.scss` : tokens complets, palette « instrument de labo » cohérente, polices auto-hébergées avec `unicode-range` et préchargement, notes de contraste AA argumentées (`--ink-4` remonté de 2,3:1 à 3,5:1). Ce n'est pas un thème de template. |
| Propreté du code | **0** `console.log`, **0** `TODO`/`FIXME`, **0** lorem ipsum, **0** `confirm()` natif sur ~200 fichiers front. Rare. |
| Modèle de permissions | `AthleteAccessValidator` : référent → permission explicite → repli club, athlète privé jamais partagé, repli legacy documenté. C'est le point fort du projet. |
| Moteurs physio | Daniels (VDOT), Dmax modifié (LT2), régression 2-paramètres (vitesse critique), sRPE Foster (ACWR/monotonie) : formules correctes, unités cohérentes, min/max jamais inversés entre allure et vitesse. |
| CI | Build + 151 tests + **smoke Liquibase sur PostgreSQL 18 réel** (= version prod) + build AOT front + Karma. Au-dessus du standard. |
| Sauvegardes | `.github/workflows/db-backup.yml` : `pg_dump -Fc` → vérification → AES-256 → artefact 14 j, avec garde sur secrets manquants et heartbeat. Écrit correctement. |
| Sentry | DSN frontend renseigné (`environment.ts`, région EU), backend câblé en no-op sans DSN. |
| Pages légales | `/legal/{confidentialite,mentions-legales,cgu}` publiées, clause bêta explicite, avertissement santé, consentement CGU horodaté (`terms_accepted_at`). |
| Swagger | Fermé en prod (`SWAGGER_ENABLED=false` par défaut). |
| Chiffrement | AES-256-GCM au repos sur données santé + jetons OAuth, IV aléatoire par valeur. |

---

## 1. 🔴 Bloquants avant la bêta

### B1 — L'e-mail d'invitation athlète n'existe pas dans le code

**Problème.** `AthleteService.invite()` génère le token et retourne l'URL. C'est tout.

```java
// back/src/main/java/com/coachrun/service/AthleteService.java:222
// Email d'invitation : délégué au NotificationTriggerService quand MAIL_ENABLED (à venir).
String url = frontendUrl + "/invitation/" + token;
```

`NotificationTriggerService` **n'existe pas dans le dépôt** (`grep` : une seule occurrence, ce
commentaire). `NotificationService` expose `notifyCoachInvitation()`, `notifyEmailVerification()`,
`notifyPasswordReset()` — **mais aucun `notifyAthleteInvitation()`**.

**Impact.** Activer `MAIL_ENABLED=true` (phase 2 du runbook) ne changera rien : l'athlète ne
recevra jamais rien. Le coach doit copier l'URL depuis le panneau `.invite-panel`
(`athlete-shell.component.ts:169`) et l'envoyer lui-même par WhatsApp. C'est le parcours
d'onboarding numéro un du produit, et le runbook coche pourtant *« Invitation athlète : l'email
arrive »* — la case sera cochée à tort ou le test échouera sans qu'on sache pourquoi.

**Difficulté.** Faible — 30 min. Une méthode calquée sur `notifyCoachInvitation()` + un appel
dans `invite()`. Garder le retour de l'URL dans la réponse API (utile en secours et si l'athlète
n'a pas d'e-mail renseigné).

---

### B2 — Connexion et inscription échouent en silence

**Problème.** Deux mécanismes s'additionnent pour supprimer tout retour d'erreur :

```ts
// front/src/app/core/interceptors/error.interceptor.ts:8
const SILENT_PATTERNS = [/\/auth\//, /\/oauth-callback/, /\/public\/invitations\//];
```

`/\/auth\//` neutralise **toutes** les routes `/auth/*`, y compris `login` et `register`. Et les
composants ne rattrapent rien :

```ts
// login.component.ts:39 et register.component.ts:41
error: () => this.submitting.set(false),
```

**Impact.** Mot de passe incorrect → **rien**. E-mail déjà utilisé (409 « Un compte existe déjà
avec cet email. ») → **rien**. Compte suspendu → **rien**. Rate limit 429 → **rien**. Le bouton
repasse de « Connexion… » à « Se connecter » et l'écran ne bouge pas. L'utilisateur retape,
reclique, et conclut que l'application est cassée — **sur le tout premier écran du produit**.

C'est le seul défaut de cet audit qui peut faire abandonner un coach avant qu'il ait vu quoi
que ce soit.

**Difficulté.** Très faible — 20 min. Restreindre le pattern silencieux à `/auth/refresh` et
`/auth/me` (les seuls où un 401 est normal), et afficher l'erreur dans les deux composants.

---

### B3 — L'ACWR est faux pendant les 4 premières semaines de chaque athlète

**Problème.** La charge chronique est une moyenne fixe sur 28 jours, sans fenêtre minimale :

```java
// LoadEngine.compute()
double chronicWeekly = total28 / 4.0;
Double ratio = chronicWeekly > 0 ? round2(acute / chronicWeekly) : null;
```

Un athlète qui démarre n'a qu'une semaine d'historique : `total28` = charge d'une semaine,
donc `chronicWeekly` = charge/4, donc **ratio ≈ 4,0**. Et l'alerte ne pose aucun garde-fou :

```java
// CoachDashboardService:204
if (ratio != null && ratio > 1.5) {
    alerts.add(alert(a, name, discipline, "RED", "ACWR_HIGH",
            "Charge en forte hausse", "ACWR " + ratio + " (> 1,5 : risque de blessure)."));
}
```

La branche `ACWR_LOW` (ligne 210), elle, vérifie bien `load.sessions28d() > 0`. La branche haute
n'a rien.

**Impact.** Jour 1 de la bêta : chaque coach ouvre son cockpit et voit **toute** sa liste
d'athlètes en rouge, « ACWR 4.0 — risque de blessure ». Puis reçoit le digest quotidien par
e-mail (cron 7 h) avec la même chose. La fonctionnalité la plus différenciante du produit —
le pilotage physiologique — passe pour cassée auprès du public qui est précisément capable de
juger qu'elle l'est.

**Difficulté.** Faible — 1 h. Exiger une fenêtre minimale (≥ 21 jours d'historique **et**
≥ 8 séances) avant de renvoyer un `ratio`, et afficher côté front « en construction — 12/28
jours » plutôt qu'un chiffre. C'est aussi la pratique de référence en sciences du sport :
l'ACWR n'a pas de sens avant que la fenêtre chronique soit remplie.

---

### B4 — Les e-mails transactionnels sont des fragments HTML nus

**Problème.** Tout le rendu e-mail tient dans deux méthodes :

```java
// NotificationService.java:318
private String cta(String label, String url) {
    return "<p><a href=\"" + url + "\">" + esc(label) + "</a></p>";
}
```

Il n'y a **pas** de `<!doctype>`, pas de `<html>`/`<body>`, pas de table de mise en page, pas de
largeur maîtrisée, pas de logo, pas de couleur, pas de bouton, pas de pied de page, **pas de
version texte** (`ResendMailClient.send()` n'envoie que `html`), pas de `reply_to`, pas de
`List-Unsubscribe`.

**Impact — image.** Ce que reçoit un coach : un lien bleu souligné, police système, aligné à
gauche sur fond blanc. C'est le **seul artefact du produit qui sort de l'application**, et c'est
celui qui porte le lien de réinitialisation de mot de passe. L'écart avec la qualité de l'interface
(design system tenu, contrastes travaillés) est tel qu'il donne l'impression que le produit est
un prototype avec une belle façade.

**Impact — délivrabilité.** Un e-mail HTML sans alternative texte et sans `List-Unsubscribe` est
pénalisé par les filtres Gmail et Outlook. La configuration SPF/DKIM prévue par le runbook est
nécessaire mais ne compense pas ça.

**Difficulté.** Faible à moyenne — ½ journée. Un gabarit unique (table 600 px centrée, en-tête
avec le logo, bouton `<a>` stylé inline, pied de page avec l'éditeur et un lien de désinscription),
une méthode `text()` en parallèle du HTML, et `reply_to: contact@darilab.app`. C'est le meilleur
rapport gain de perception / effort de tout cet audit.

---

### B5 — L'inscription est ouverte à tout Internet

**Problème.** `/auth/register` est dans `PUBLIC_PATHS` (`SecurityConfig:38`) sans aucun gate :
pas de code d'invitation, pas d'allowlist d'e-mails, pas de file d'attente. La politique de mot
de passe est `@Size(min = 8, max = 100)` sans contrainte de complexité, et le rate limiting est
de 20 requêtes/60 s par IP sur `/auth/login` **sans verrouillage de compte** (soit ~28 800
tentatives/jour depuis une seule IP).

**Impact.** Une « bêta fermée sur invitation de 5 à 8 coachs » n'est pas fermée : n'importe qui
tombant sur `darilab.app` crée un club. On perd le contrôle de la cohorte (le point central de
la stratégie du runbook), on récolte des inscriptions parasites, et on ne sait plus si le signal
Sentry vient d'un vrai testeur.

**Difficulté.** Très faible — 1 h. Une variable `BETA_INVITE_CODE` vérifiée dans
`AuthService.register()`, plus un champ dans le formulaire. À retirer à l'ouverture publique.

---

### B6 — Le premier écran d'un coach dit « Tout le monde est en forme » alors qu'il n'a aucun athlète

**Problème.** Le cockpit ne distingue pas « aucun signal » de « aucun athlète » :

```html
<!-- dashboard.component.html:72 -->
} @else {
  <div class="card zone-ok">
    <strong>Tout le monde est en forme</strong>
    <p class="field-hint">Aucun signal de fatigue ou de douleur à traiter sur ton périmètre.</p>
```

Un coach qui vient de s'inscrire voit donc, dans l'ordre : « Bonjour X / Cockpit coach » →
« Tout le monde est en forme » → un rail de KPI à `0 / 0 / 0 / 0` → « Club : 0 en forme,
0 à surveiller, 0 vigilance » → et **tout en bas de page**, la seule action utile, la carte
« Gérer mes athlètes » (ligne 155).

**Impact.** Quatre zones vides avant la première action possible, et une affirmation absurde en
tête de page. C'est exactement la signature « application générée » : les composants sont là,
le cas du premier jour n'a pas été pensé. Or le premier jour est le seul que 100 % des
bêta-testeurs verront.

**Difficulté.** Faible — 2 h. Quand `activeAthletes === 0`, remplacer tout le cockpit par un
état de première ouverture : trois étapes numérotées (créer un athlète → renseigner son profil
physio → planifier sa première séance), chacune avec son bouton. C'est aussi l'« onboarding
guidé » réclamé par l'audit précédent — les deux besoins se règlent d'un coup.

---

## 2. 🟠 Important — dans les deux premières semaines

### Sécurité — cycle de vie des sessions

| # | Problème | Impact | Effort |
|---|---|---|---|
| **I1** | `resetPassword()` et `changePassword()` (`AuthService:324`, `:144`) **ne révoquent aucune session existante**. Le refresh token vit 30 j (`JwtService:38`). | « J'ai changé mon mot de passe » ne ferme pas l'accès d'un attaquant : il garde la main un mois. C'est le réflexe n° 1 d'un utilisateur qui se sent compromis, et il ne sert à rien. | ½ j — colonne `password_changed_at` + rejet des jetons émis avant. |
| **I2** | `TokenBlacklist` est un `ConcurrentHashMap` en mémoire. | Chaque redéploiement Railway (donc chaque push) **vide la liste** : tous les refresh tokens révoqués par rotation redeviennent valides, et tous les logouts sont annulés. La rotation, bien implémentée, perd sa garantie anti-rejeu. | Couvert par I1 (`password_changed_at` rend la révocation persistante pour le cas qui compte). |
| **I3** | `logout()` (`AuthController:84`) lit le header `Authorization`, donc révoque l'**access** token — jamais le refresh. | Le front purge le `localStorage`, l'impact pratique est faible ; mais « se déconnecter » ne déconnecte pas côté serveur. | 1 h — accepter le refresh token dans le corps du `POST /auth/logout`. |
| **I4** | `JwtAuthenticationFilter.resolveToken()` accepte `?access_token=` sur **toutes** les routes, alors que seul le flux SSE en a besoin. | Un JWT dans une URL finit dans les logs de proxy, l'historique du navigateur et l'en-tête `Referer`. Déjà connu et documenté, mais la portée est bien plus large que nécessaire. | 15 min — n'accepter le paramètre que si l'URI se termine par `/stream` ou `/attachment`. |
| **I5** | `InvitationAcceptRequest.password` n'a **aucune** validation (`String password`, pas de `@Size`), contrairement à `RegisterRequest`. | Un appel direct à `POST /public/invitations/{token}/accept` crée un compte athlète avec un mot de passe d'**un caractère**. Le front impose 8 ; le serveur, rien. | 10 min. |
| **I6** | `CoachInvitationAcceptRequest.termsAccepted` est un `Boolean` sans `@AssertTrue` (`RegisterRequest` en a un). | La preuve de consentement CGU est exigée à l'inscription mais pas à l'acceptation d'invitation coach : deux chemins d'entrée, une seule preuve RGPD. | 10 min. |
| **I10** | `vercel.json` ne contient que des rewrites : **aucun en-tête de sécurité** sur les réponses du front (CSP, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`). Les en-têtes durcis de `SecurityConfig` ne couvrent que `/api`. | La page elle-même — celle qui détient les jetons en `localStorage` — n'a aucune politique. | 30 min — un bloc `headers` dans `vercel.json`. |

### Métier — crédibilité des chiffres affichés

| # | Problème | Impact | Effort |
|---|---|---|---|
| **I7** | `SessionCalculatorEngine.hrForSpeed()` extrapole la pente LT1→LT2 vers le bas et clampe à **60 bpm** en dur. `hrRest` est présent dans `AthletePaceContext` mais **jamais utilisé** dans ce calcul (`grep` : seul `ZoneValueSyncService` s'en sert, pour les zones HRR). | Un footing prescrit sous LT1 peut afficher une cible FC de 80–95 bpm. Un coach le repère en une seconde et cesse de faire confiance aux autres chiffres. | 1 h — plancher à `hrRest + 25 %` de la réserve, ou ne rien afficher hors du domaine d'interpolation. |
| **I8** | Les alertes « séances manquées » comptent les séances `PLANNED` dont la date est passée (`CoachDashboardService`). | Un athlète qui court mais oublie de clôturer sa séance déclenche une alerte **ROUGE** « 3 séances manquées ». Combiné à B3, la file d'alertes sera saturée de faux positifs dès la semaine 1 — et une file d'alertes bruyante n'est plus lue. | ½ j — croiser avec les activités importées (Strava/GPX) avant de conclure à un manquement. |
| **I9** | `alerts()` boucle sur chaque athlète en appelant `lastFeedback()`, `loadService.load()` (recalcul ACWR complet) et une requête workouts : ~4 requêtes/athlète, rejouées à chaque changement de périmètre **et** dans le digest quotidien de chaque coach. | ~120 requêtes par affichage à 30 athlètes. Tenable en bêta, mais c'est le premier endroit qui décrochera. Sentry Performance (traces à 10 %) le confirmera. | 1 j — requête agrégée ou cache court sur les métriques de charge. |

### Image et parcours

| # | Problème | Impact | Effort |
|---|---|---|---|
| **I11** | Aucun canal de feedback intégré, aucune impersonation admin (`grep` : rien). Les deux 🟠 de l'audit précédent sont toujours ouverts. | Chaque bug se règle par échange de captures d'écran par e-mail. En bêta c'est le poste de coût numéro un. | Feedback : 2 h (lien `mailto` ou Tally dans le menu d'aide). Impersonation : 1 j. |
| **I12** | `index.html` n'a ni `og:title`, ni `og:description`, ni `og:image`, ni `twitter:card`. | Un lien `darilab.app` partagé dans WhatsApp ou Slack — le canal de recrutement naturel d'une bêta de coachs — apparaît comme une URL nue. | 30 min, dont la fabrication d'une image 1200×630. Gros gain d'image pour l'effort. |
| **I13** | `/dev/ui-kit` et `/dev/api` sont routables en production sans guard (`app.routes.ts:60` et `:67`). | Un testeur curieux tombe sur le styleguide interne, ou sur une sonde affichant « API injoignable ». | 15 min — guard admin ou exclusion du build de prod. |
| **I14** | `{ path: '**', redirectTo: '' }` : pas de page 404. | Un lien d'invitation tronqué par un client mail, un favori périmé, une faute de frappe → renvoi silencieux sur la page d'accueil marketing, sans explication. L'utilisateur croit avoir été déconnecté. | 1 h. |
| **I15** | `GlobalExceptionHandler` renvoie `message: "Requête invalide"` + une map `fieldErrors`, mais `errorInterceptor` n'affiche que `error.error?.message`. | Toute erreur de validation serveur s'affiche « Requête invalide » sans dire quel champ. | 1 h — exploiter `fieldErrors` dans le toast. |

---

## 3. 🟢 Peut attendre les premiers retours

- **Monotonie** : `LoadEngine.monotony()` renvoie `null` quand l'écart-type est nul — c'est-à-dire
  précisément le cas le plus monotone qui soit. Cas limite jamais atteint en pratique (un jour de
  repos suffit à créer de la variance), mais logiquement inversé.
- **Fil de messages non paginé** (`findByClubIdAndAthleteIdOrderByCreatedAtAsc`, sans `Pageable`).
  Impact réel faible : `body` est plafonné à 2 000 caractères et les pièces jointes sont dans une
  table séparée (`message_attachments`) référencées par id — le fil ne charge donc **pas** les
  blobs. Bon design, à paginer plus tard.
- **Couverture front** : 4 fichiers de spec (`app.component`, `toast.service`, `undo-stack.service`,
  `calendar-selection`). Aucun e2e. La CI construit en AOT, ce qui attrape déjà les régressions de
  typage.
- **Calendrier** : ~12 requêtes parallèles à l'ouverture ; la bibliothèque (templates, éducatifs,
  catégories) est refetchée à chaque montage plutôt que mise en cache.
- **Résidus de marque** : `@athlete.coachrun.local` (identifiant de repli des athlètes sans e-mail),
  `mailto:no-reply@coachrun.fr` (sujet VAPID par défaut), un commentaire « Logo CoachRun ».
  Invisibles dans le parcours nominal depuis que l'invitation exige un e-mail.
- **`appVersion: '0.1.0'`** figé à la main dans `environment.ts` → impossible de corréler « bug
  signalé » et « version déployée » dans Sentry.
- **Pas de préproduction** : la CI reste le seul filet entre un commit et la production.
- **`JWT_ACCESS_TTL`** vaut 3600 s par défaut (`application.yml:66`) et n'est pas surchargé dans
  `application-prod.yml` : le TTL réel est d'1 h, pas des 15 min supposées par la documentation.
  À aligner explicitement.

---

## 4. Réponses aux questions posées

### L'OTP est-il pertinent pour DARI Lab ?

**Non, pas maintenant — et il n'y en a aucun aujourd'hui** (vérifié : aucune trace d'OTP, TOTP ou
code à usage unique dans le dépôt).

Trois raisons de ne pas en mettre :

1. **Ça ne répond pas au risque réel.** Le risque numéro un de ce produit n'est pas l'usurpation
   de compte, c'est la perte d'accès (B1, B2) et la perte de données. Un OTP ajoute une friction
   à chaque connexion sans traiter aucun des six bloquants.
2. **La cible s'y prête mal.** Un athlète qui ouvre sa séance à 6 h 30 avant de sortir courir, en
   PWA, sur un téléphone avec 3G : un code à saisir à chaque session est le meilleur moyen de
   tuer l'usage quotidien.
3. **Il y a moins cher et plus utile.** Le refresh token à 30 jours avec rotation couvre déjà le
   confort ; ce qui manque, c'est la révocation à la modification de mot de passe (I1) — un
   correctif d'une demi-journée qui apporte plus de sécurité réelle qu'un OTP.

**Ce qui serait pertinent, dans l'ordre** : (a) corriger I1/I5/I6, (b) proposer plus tard une
**2FA TOTP optionnelle** pour les comptes coach responsables de club et pour le compte admin
plateforme (c'est là que sont les données de dizaines d'athlètes), (c) jamais d'OTP obligatoire
sur le portail athlète. Un « magic link » de connexion (un lien cliquable, pas un code à recopier)
serait un meilleur candidat pour les athlètes — l'infrastructure existe déjà, c'est le mécanisme
de l'invitation.

### Est-ce que j'ouvrirais la bêta aujourd'hui ?

**Non. Dans 2 à 3 jours, oui, sans réserve.**

Ouvrir aujourd'hui, concrètement : un coach s'inscrit, se trompe de mot de passe à sa deuxième
connexion et croit l'app cassée (B2) ; s'il passe, il arrive sur un tableau de bord qui affirme
que tout le monde va bien alors qu'il n'a personne (B6) ; il crée un athlète, clique « Inviter »
et découvre qu'il doit copier-coller un lien lui-même (B1) ; une semaine plus tard son cockpit
est rouge sur tous ses athlètes sans raison (B3) ; et le seul e-mail qu'il aura reçu ressemble à
un message de test (B4).

Rien de tout cela ne remet en cause l'architecture. Ce sont six correctifs indépendants, tous
inférieurs à une demi-journée.

### Les 10 derniers points à corriger

| # | Point | Priorité | Effort |
|---|---|---|---|
| 1 | Afficher les erreurs de connexion et d'inscription (B2) | 🔴 | 20 min |
| 2 | Envoyer réellement l'e-mail d'invitation athlète (B1) | 🔴 | 30 min |
| 3 | Fenêtre minimale sur l'ACWR + affichage « en construction » (B3) | 🔴 | 1 h |
| 4 | Gabarit e-mail complet + version texte + `reply_to` (B4) | 🔴 | ½ j |
| 5 | Écran de première ouverture du cockpit (B6) | 🔴 | 2 h |
| 6 | Code d'invitation sur `/register` (B5) | 🔴 | 1 h |
| 7 | Révoquer les sessions au changement de mot de passe (I1/I2) | 🟠 | ½ j |
| 8 | Validation serveur du mot de passe athlète + CGU coach (I5/I6) | 🟠 | 20 min |
| 9 | En-têtes de sécurité `vercel.json` + `access_token` restreint au SSE (I10/I4) | 🟠 | 45 min |
| 10 | Open Graph + page 404 + fermeture de `/dev/*` (I12/I14/I13) | 🟠 | 2 h |

Les points 1 à 6 sont le chemin critique — **environ 1,5 jour**. Les points 7 à 10 peuvent se
faire pendant la première semaine de bêta sans risque.

### Qu'est-ce qui risque le plus de faire perdre confiance à un premier utilisateur ?

**Le formulaire de connexion muet (B2)**, sans hésitation.

C'est le seul défaut de cet audit qui frappe **avant** que l'utilisateur ait vu quoi que ce soit
du produit, et le seul qui n'offre aucune sortie : il n'y a pas de message à lire, pas d'action
corrective suggérée, rien qui bouge. Un utilisateur face à un bouton qui ne fait rien ne se dit
pas « je me suis trompé de mot de passe », il se dit « c'est cassé ». Et il n'a alors aucune
raison de croire que le reste fonctionne.

Le second, plus insidieux et plus grave à moyen terme, est **l'ACWR rouge sur tout athlète neuf
(B3)** — parce qu'il ne détruit pas la confiance dans l'application, mais dans **l'expertise**.
Le public visé est précisément celui qui sait ce qu'est un ratio charge aiguë/chronique et qui
verra immédiatement qu'un athlète démarrant à 4,0 est une erreur de méthode. Pour un produit dont
l'argument est la rigueur physiologique, c'est le pire endroit possible où se tromper.

### Combien de coachs et d'athlètes l'application peut-elle supporter ?

**Techniquement : 20 à 30 coachs et 250 à 300 athlètes** sur l'architecture actuelle
(Railway mono-instance + PostgreSQL indexé — 36 des 57 migrations créent des index — + front
statique Vercel). Rien dans le code ne bloque avant ce seuil.

**Le premier point de rupture sera le cockpit coach (I9)** : ~4 requêtes par athlète et par
affichage, rejouées à chaque changement de périmètre et dans le digest quotidien. À 50 athlètes
par coach, l'ouverture du tableau de bord devient perceptiblement lente ; c'est le signal à
surveiller dans Sentry Performance.

**Deux limites structurelles à connaître avant de dépasser une instance** : le rate limiter et
la blacklist de tokens sont en mémoire, et les émetteurs SSE aussi — passer à deux pods casse
les trois. Redis est le prérequis de toute montée en charge horizontale.

**Recommandation inchangée : démarrer à 5–8 coachs.** La limite n'est pas technique, elle est
humaine — une seule personne au support. Élargir à 15–20 après deux semaines si Sentry reste
calme.

---

## 5. Notes de maturité

| Domaine | Note | Justification |
|---|---|---|
| **Produit** | **8,5/10** | Le fond métier est au-dessus du marché : prescription en fourchettes calculées par athlète, course et force unifiées dans une même charge interne, zones à ancres multiples avec détection de trous, snapshot figé de la prescription au calendrier. Nolio et TrainingPeaks ne font pas la moitié de la physiologie qui est ici. Manquent l'onboarding et le canal de feedback. |
| **UX** | **7/10** | Excellent en profondeur — calendrier avec undo/redo, raccourcis clavier, palette Cmd+K, bibliothèque latérale, mode séance de force plein écran, rattrapage des ressentis oubliés sur 7 jours. Mais trois trous béants sur les bords : login muet, cockpit du premier jour, alertes bruyantes. Le soin apporté au cœur rend ces oublis d'autant plus visibles. |
| **Design** | **8,5/10** | Le point le plus fort. Design system réel et argumenté (tokens, dégradés tonaux assumés contre l'arc-en-ciel, mono tabulaire pour la donnée), polices auto-hébergées avec préchargement, contrastes corrigés avec le ratio en commentaire. Aucune trace de template ni de placeholder. Seule ombre : les e-mails, qui ne bénéficient de rien de tout ça. |
| **Sécurité** | **7/10** | Modèle de permissions multi-tenant rare à ce stade (référent / permission explicite / privé, testé), chiffrement AES-256-GCM au repos, garde-fou de démarrage sur les secrets, Swagger fermé en prod. Pénalisé par cinq défauts de cycle de vie des sessions (I1–I5) et l'absence d'en-têtes côté front. |
| **Fiabilité** | **6/10** | Nette progression : workflow de sauvegarde chiffrée écrit avec heartbeat, Sentry frontend branché, CI alignée sur PostgreSQL 18. Reste à **activer et tester** (un backup non restauré n'existe pas), et l'invisibilité des erreurs d'auth (B2) crée un angle mort exactement là où il ne faut pas. |
| **Professionnalisme perçu** | **6,5/10** | La note la plus injuste au regard du contenu réel. L'intérieur vaut 8,5 ; ce sont les bordures qui plombent — le seul e-mail que reçoit un coach, le premier écran qu'il voit, le premier formulaire qu'il remplit. Les six correctifs bloquants amèneraient cette note à **8,5** en 2 jours. C'est l'investissement au meilleur rendement du projet. |

---

## 6. Ordre d'exécution recommandé

| Jour | Tâches | Résultat |
|---|---|---|
| **J1 matin** | B2 (erreurs de login) · B1 (e-mail d'invitation athlète) · B5 (code d'invitation) | Les trois impasses du parcours d'entrée sont levées. |
| **J1 après-midi** | B4 (gabarit e-mail + version texte) | Le seul artefact hors application devient présentable. |
| **J2 matin** | B3 (fenêtre ACWR) · B6 (cockpit premier jour) | Le produit ne se contredit plus lui-même. |
| **J2 après-midi** | Phases 2 à 5 du runbook (Resend, Sentry, uptime, backups + **test de restauration**) | Fin de la cécité opérationnelle. |
| **J3** | Phase 6 du runbook (tests de bout en bout sur `www.darilab.app`) + I5, I6, I10, I4 | Feu vert. |

Le runbook `BETA-LAUNCH-RUNBOOK.md` reste valable tel quel — avec une correction : la case
« Invitation athlète : l'email arrive » de la phase 2.4 **échouera** tant que B1 n'est pas
corrigé, et ce n'est pas un problème de configuration Resend.
