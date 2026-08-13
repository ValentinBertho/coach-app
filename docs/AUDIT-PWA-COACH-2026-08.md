# Audit & proposition — une PWA mobile pour le coach (août 2026)

> **La question posée.** L'espace coach a été conçu pour le bureau : calendrier glisser-déposer,
> bibliothèque, éditeur de structure, cockpit à quatre zones. C'est le bon choix pour *concevoir*
> un entraînement. Mais un coach ne passe pas ses journées devant un écran : il est au bord d'une
> piste, dans une salle, dans le train. Que doit-il pouvoir faire depuis son téléphone, et
> qu'est-ce qui manque aujourd'hui pour qu'il le fasse ?
>
> Ce document photographie l'état du produit au 13 août 2026 (§1 à §3), puis propose une cible et
> son découpage (§4 à §9). Comme tout audit de ce dossier : il ne fait pas foi contre les
> documents vivants, il dit ce qui est vrai à sa date.

---

## 1. Ce qui existe déjà — et qui change la nature du chantier

Le premier constat de l'audit est qu'**il n'y a pas de PWA à construire**. Elle est là, complète,
et elle marche. Ce qui manque n'est pas une application mobile : c'est ce qu'elle montre au coach
quand il l'ouvre.

**Le socle PWA est en place et exploité.**
`front/angular.json` active le service worker en production *et* dans une configuration `pwa`
dédiée (`npm run start:pwa`) ; `front/src/manifest.webmanifest` déclare huit icônes maskables de
72 à 512 px, `display: standalone`, les couleurs de thème ; `installedAppGuard`
(`core/guards/installed-app.guard.ts`) évite qu'une application lancée depuis l'écran d'accueil
ouvre sur la page de vente ; `UpdateService`, `OfflineBannerComponent` et `NetworkStatusService`
couvrent la mise à jour et la coupure réseau.

**Le web push est complet, des deux côtés.**
`PushNotificationService` (back) signe en VAPID, connaît ses appareils, sait dire s'il peut
joindre quelqu'un (`canReach`), respecte les heures de silence, les catégories coupées
(`NotificationCategory`), déduplique les rafales, et sait poser des **actions rapides** sur une
notification (`QuickAction`). Côté front, `PushService` réaligne l'abonnement au démarrage, route
le clic — action comprise — vers l'écran ciblé, et expose la liste des appareils avec un envoi
d'essai.

**Le coach est déjà notifié.** Cinq flux le visent : `ATHLETE_FEEDBACK` (push seulement si le
retour est *notable* — douleur ≥ 3, séance manquée, RPE 10), `INJURY_ALERT`, `COACH_ALERTS` (le
digest de 7 h), `ATHLETE_UNAVAILABILITY`, `HEALTH_CONSENT_WITHDRAWN`, plus `NEW_MESSAGE`. La
politique de bruit a été travaillée, elle est bonne, et ce n'est pas elle qu'il faut refaire.

**La coquille coach a déjà une peau mobile.** Sous 900 px, `coach-layout.component.html` bascule
sur une barre supérieure, une bottom-nav de quatre entrées et une feuille « Plus ».

Autrement dit : la plomberie est faite. **Le chantier est un chantier de parcours, pas de
technique** — et c'est une bonne nouvelle pour le coût.

---

## 2. Les dix constats de l'audit

### 2.1 Un seul manifeste pour deux métiers

`manifest.webmanifest` ne connaît qu'un produit : `name: "Darilab — Coaching course à pied"`,
`start_url: "./"`, aucun `id`, aucun `display_override`, **aucun `shortcuts`**, aucun
`screenshots`. Le coach installe donc la même icône, au même nom, ouvrant au même endroit que son
athlète. L'appui long sur l'icône — le geste qui, sur Android comme sur iOS 17, remplace le menu
d'accueil d'une application native — ne propose rien.

### 2.2 Le coach ne croise jamais l'invitation à installer, ni celle à s'abonner

C'est le constat le plus coûteux, parce qu'il annule tous les autres.

- `app-install-button` n'est monté qu'à deux endroits : `features/athlete/profile.component.ts` et
  la landing publique. **Jamais dans la coquille coach.**
- `app-push-prompt` — la carte « Être prévenu·e », avec sa mémoire du refus — vit dans
  `features/athlete/today.component.html`. **Elle n'existe que pour l'athlète.**
- Le coach n'a qu'`app-push-button` : un bouton texte « Notifications », cinquième contrôle d'une
  barre supérieure mobile qui en compte cinq, et qui disparaît dès qu'un appareil est abonné.

Conséquence directe : **sur iPhone, le web push n'existe que dans une PWA installée** (le service
le dit lui-même dans `unsupportedMessage()`). Un coach sur iPhone qui n'a pas ajouté Darilab à son
écran d'accueil ne peut *pas* recevoir d'alerte blessure — et rien, nulle part, ne le lui propose.
Tout le dispositif d'alerte côté coach repose sur un opt-in que sa cible ne rencontre pas.

### 2.3 Chaque notification atterrit sur l'écran le plus lourd du produit

Le tap sur une notification est *le* geste mobile. Voici où il mène aujourd'hui :

| Notification | Destination | Ce que le coach trouve sur 375 px |
|---|---|---|
| `ATHLETE_FEEDBACK` | `/app/feedback` | deux contrôles segmentés de 4 options chacun, lignes en grille 3 colonnes, deux boutons par ligne |
| `INJURY_ALERT` | `/app/athletes/:id` | coquille à 8 onglets, bandeau de stats, popover d'export de 280 px |
| `COACH_ALERTS` (7 h) | `/app` | cockpit : périmètre, alertes, jauges de forme, rail de KPI, répartition club, courses |
| `ATHLETE_UNAVAILABILITY` | `/app/calendar` | le composant de 2 192 lignes, drag & drop, bibliothèque latérale |
| `NEW_MESSAGE` | `/app/athletes/:id/messages` | le seul atterrissage réellement utilisable au pouce |

Le produit réveille correctement le coach, puis le dépose devant un écran de bureau.

### 2.4 La barre du bas ne porte pas les gestes du coach

`Accueil · Athlètes · Calendrier · Plus` : trois destinations de consultation et un menu. Or les
deux seules choses qu'un coach fait vraiment depuis son téléphone — **traiter un retour** et
**répondre à un message** — sont derrière « Plus ». Ce sont pourtant précisément les deux entrées
qui portent un badge de non-traités dans la nav latérale.

### 2.5 Le calendrier ne se replie pas, il s'empile

Sous 768 px, `calendar.component.scss` passe la grille à `1fr` : les sept colonnes deviennent sept
blocs empilés, la ligne des jours disparaît, les totaux hebdomadaires deviennent un bandeau. Une
semaine devient un long défilement ; un mois, une trentaine de cartes à la file. Le glisser-déposer
est bien tactile (`pointerdown` / `pointermove` / `pointerup`), mais déplacer une séance dans une
pile verticale d'un mois n'a aucun sens. **Il n'existe ni vue « jour », ni semaine compacte.**

### 2.6 Rien de ce que lit le coach n'est disponible hors ligne

`ngsw-config.json` ne contient qu'un seul `dataGroup` : `athlete-today`
(`/api/me/today`, prescriptions). Aucun appel coach n'est mis en cache. Dans le métro, au bord
d'une piste sans réseau, le coach obtient le bandeau hors-ligne et une page vide — alors que sa
file du matin ne change pas d'une minute à l'autre et se prêterait parfaitement à une lecture
différée.

### 2.7 Aucune action rapide sur les notifications du coach

Le mécanisme existe et il est utilisé : le rappel de débriefing de l'athlète porte trois boutons
« Facile / Moyen / Dur » qui écrivent le RPE en un tap
(`NotificationService` §918-946). Côté coach : **zéro action**. Pas de « Traité », pas de
« Répondre » — alors que `markReviewed` et l'envoi de message sont déjà des routes d'API
existantes, et que `UndoStackService` est là pour rattraper un tap malheureux.

### 2.8 Les écrans conservés ne respectent pas la règle des 44 px

`Design.md` §10 et §11 exigent des cibles ≥ 44 px. Sur les écrans coach, plusieurs contrôles ne
les tiennent pas : le pas-à-pas de la fiche athlète (`.stepper .icon-btn { padding: 3px 5px }` —
environ 22 px), les préréglages du panneau d'export (`.ep-preset`), les chips de séance du
calendrier. Le popover d'export est ancré à droite en 280 px de large : sur 375 px, il occupe
l'écran sans en être une feuille.

### 2.9 Il n'existe nulle part de « matin du coach »

Ce que le coach cherche en ouvrant son téléphone tient en une phrase : *qui a mal, qui n'a pas fait
sa séance, qui m'a écrit, qu'est-ce que je réponds*. Ces quatre informations existent toutes —
`alerts()`, `feedbackQueue()`, `conversations()`, `form()` — mais **réparties sur trois écrans**,
chacun avec ses propres filtres de périmètre et de profondeur. Sur ordinateur, les parcourir coûte
trois clics. Sur téléphone, c'est le parcours entier.

### 2.10 Deux détails qui se voient beaucoup

Le compteur d'icône (`navigator.setAppBadge`) n'est pas utilisé, alors que les deux compteurs qui
l'alimenteraient sont déjà rafraîchis à chaque navigation par `refreshBadges()`. Et le portail
athlète a une identité mobile assumée (peau sombre forcée, `data-theme="dark"`) là où la coquille
coach mobile ressemble à un site web rétréci.

---

## 3. Ce que l'audit ne reproche pas — et ce qu'il ne faut pas porter

Trois choses sont **bien** et doivent le rester : la politique de bruit des notifications (le tri
*notable* / non-notable, le digest de 7 h, la déduplication), la séparation par coach référent, et
l'invariant « aucune donnée de santé dans le corps d'une notification ».

Et une position ferme, à assumer plutôt qu'à contourner : **on ne porte pas tout.** L'éditeur de
structure de séance, la bibliothèque, les modèles de zones, les mésocycles, l'export PDF, les
réglages de club, les tests de lactate et l'analyse fine de la charge sont des **écrans de
conception**. Ils supposent de la surface, de la précision et du temps. Une version mobile
dégradée de l'éditeur de séance serait pire que son absence : elle promettrait un geste qu'elle ne
tiendrait pas. Ils restent atteignables — rien n'est retiré — mais ils ne sont pas la cible, et
l'application doit le **dire** (« Cet écran se travaille sur ordinateur ») plutôt que de laisser
découvrir.

---

## 4. La proposition, en une phrase

> **Une seule application installée, deux coquilles. Sur mobile, le coach ne conçoit pas : il
> surveille, il répond, il ajuste.**

Quatre gestes, pas un de plus : *voir ce qui ne va pas · traiter un retour · répondre à un message ·
regarder la journée*. Tout le reste est un lien vers l'ordinateur.

---

## 5. La cible, écran par écran

### 5.1 La coquille mobile coach

La bottom-nav passe des destinations de consultation aux gestes réels :

| Aujourd'hui | Cible |
|---|---|
| Accueil · Athlètes · Calendrier · **Plus** | **Ma journée** · **Retours** (badge) · **Messages** (badge) · **Athlètes** |

« Plus » quitte la barre du bas pour l'icône de menu de la barre supérieure — c'est un tiroir, pas
une destination — et y retrouve Calendrier, Bibliothèque, Club, Mes zones, Paramètres, Aide,
Signaler un problème. Quatre onglets et non cinq : c'est l'arbitrage déjà tranché pour le portail
athlète, pour la même raison (sur 375 px, six cibles tombent à ~52 px avec des libellés de 10 px).

La barre supérieure mobile redescend de cinq contrôles à trois : marque, cloche, menu. La
recherche globale rejoint le tiroir, la déconnexion rejoint le profil.

### 5.2 « Ma journée » — l'écran qui n'existe pas et qui porte tout

Une seule liste, triée par urgence, chaque ligne portant ses actions au pouce.

1. **À traiter maintenant** — blessure déclarée, douleur ≥ 5, séance manquée, RPE maximal. Une
   carte par athlète : nom, pastille de forme, ce qui s'est passé, et trois actions de 44 px —
   **Voir · Répondre · Traité**.
2. **Retours du jour** — lignes compactes (athlète, séance, sensation, RPE, douleur), bouton
   **Traité** avec annulation par toast (`UndoStackService`).
3. **Messages non lus** — les trois derniers fils ; la réponse s'écrit dans une feuille qui monte
   du bas, sans quitter l'écran.
4. **La journée du club** — prévu / réalisé / en attente aujourd'hui, en trois nombres. Le tap
   ouvre la vue Jour (§5.4).

**Aucun développement backend en vague 1** : les trois appels existent (`alerts`, `feedbackQueue`,
`conversations`) et se composent côté front. Un endpoint agrégé
(`GET /clubs/{id}/dashboard/mobile`) ne se justifiera que si le coût de trois allers-retours se
voit à l'usage — c'est une optimisation, pas un préalable.

### 5.3 La fiche athlète condensée

Sur mobile, la coquille à huit onglets se replie sur un écran unique : identité + pastille de
forme, les trois derniers retours, la prochaine séance, la prochaine course, et **deux boutons —
Message et Programme**. Les six autres onglets restent atteignables par un menu, avec la mention
explicite de ce qui se travaille mieux sur ordinateur.

### 5.4 Le calendrier : une vue Jour, pas une pile

Sous 768 px, `/app/calendar` s'ouvre sur **la journée**, pas sur le mois : un bandeau de sept
pastilles pour la semaine, des flèches de part et d'autre, et une carte par athlète ayant une
séance (nom, titre, statut prévu/réalisé/manqué, pastille de forme). Le glisser-déposer disparaît
— il n'a pas de sens dans une pile — et le déplacement d'une séance devient une action explicite
du menu de la carte, **« Déplacer → date »**, servie par l'API existante. La semaine empilée reste
disponible d'un bouton, pour qui veut la vue d'ensemble.

### 5.5 Les notifications deviennent actionnables

`Notification.maxActions` autorise deux boutons en pratique. Les trois flux qui comptent :

| Notification | Bouton 1 | Bouton 2 |
|---|---|---|
| `ATHLETE_FEEDBACK` (notable) | **Traité** → `/app/feedback?review=<kind>:<id>` | **Ouvrir** |
| `NEW_MESSAGE` | **Répondre** → `/app/athletes/:id/messages?reply=1` | **Ouvrir** |
| `COACH_ALERTS` (7 h) | **Ma journée** → `/app` | — |

Le patron est exactement celui du débrief athlète : l'action porte l'information dans sa *query
string*, et l'écran d'arrivée l'exécute au chargement. Deux garde-fous : l'écriture déclenchée par
une URL doit être **idempotente** (marquer deux fois un retour traité ne doit rien casser) et
**annulable** (toast + `UndoStackService`), parce qu'un tap sur un écran verrouillé se fait aussi
par erreur.

### 5.6 Installation, abonnement, badge

- Monter dans la coquille coach ce qui n'existe que côté athlète : **`app-install-button`** et la
  carte **« Être prévenu·e »**, avec sa mémoire du refus. Le composant `push-prompt` déménage de
  `features/athlete/` vers `shared/components/` — c'est le même besoin, écrit une fois.
- Sur iPhone non installé, ne pas proposer l'abonnement mais **l'installation**, avec le geste
  exact (Partager → Sur l'écran d'accueil). Le texte existe déjà dans `PushService`, il ne
  s'affiche simplement jamais au bon moment.
- **Manifeste** : ajouter `id`, `display_override: ["standalone", "minimal-ui"]`, des
  `screenshots` (`form_factor` narrow et wide, pour une invite d'installation riche sur Android),
  et des `shortcuts`. Le manifeste étant partagé entre les deux rôles, les raccourcis ne peuvent
  pas pointer vers `/app/...` : ils visent de petites **routes de redirection par rôle**
  (`/go/journee`, `/go/messages`, `/go/retours`) qui envoient le coach sur `/app/...` et l'athlète
  sur `/athlete/...`. Trois lignes de routage, et l'appui long sur l'icône devient utile pour les
  deux.
- **Badge d'application** : `navigator.setAppBadge(retours + non-lus)` branché sur le
  `refreshBadges()` existant, effacé à zéro. Supporté par Android et par iOS 16.4+ en PWA
  installée ; ailleurs, l'appel est simplement ignoré.

### 5.7 Hors ligne

Trois `dataGroups` à ajouter en stratégie `freshness` (timeout 3 s, `maxAge` 1 h) : le tableau de
bord du club, la liste des athlètes, les conversations. Le coach ouvre son application dans le
métro et voit sa file **de ce matin**, datée en toutes lettres (« données de 7 h 12 »). L'écriture
hors ligne — marquer traité, répondre — se fait sur le modèle déjà éprouvé de
`feedback-queue.storage.ts` côté athlète, et vient plus tard.

Point RGPD, vérifié : la file de retours contient de la douleur et des blessures nommées, donc de
la donnée de santé. Elle serait mise en cache sur l'appareil. **La purge existe déjà** —
`AuthService.purgeCaches()` supprime l'intégralité des caches à la déconnexion — mais elle devient
un invariant à tester, pas un effet de bord heureux.

---

## 6. Découpage en vagues

| Vague | Contenu | Backend | Estimation |
|---|---|---|---|
| **1 — Le matin du coach dans la poche** | Coquille mobile (4 onglets + tiroir), écran **Ma journée** composé des appels existants, invitation installer + notifications côté coach, manifeste (`id`, `display_override`, `shortcuts` + routes `/go/*`), badge d'icône | **aucun** | ~3–4 j |
| **2 — Répondre sans ouvrir un écran de bureau** | Feuille de réponse depuis Ma journée et depuis la file, actions rapides de notification (Traité / Répondre) + idempotence + annulation, fiche athlète condensée, passe des cibles ≥ 44 px | ~0,5 j (actions sur 3 flux) | ~3 j |
| **3 — La semaine dans la poche** | Vue Jour du calendrier + déplacement par menu, lecture hors ligne (`dataGroups` + test de purge), endpoint agrégé si le besoin se confirme | ~1 j | ~4 j |

**Vague 1 seule vaut déjà d'être livrée** : elle referme le trou le plus coûteux (le coach iPhone
qui ne peut pas être alerté) et donne un écran d'ouverture qui a du sens, sans toucher une ligne de
backend ni un écran existant.

---

## 7. Ce qu'on mesure pour savoir si ça a marché

- **Part des coachs disposant d'au moins un appareil abonné** (`push_subscriptions`) — l'indicateur
  qui dit si le §2.2 est refermé. C'est le seul qui compte en vague 1.
- Part des coachs ayant installé l'application (déductible du `display-mode` à la connexion).
- **Délai médian entre un retour *notable* et son passage en « traité »**, avant / après. C'est la
  promesse du produit : un coach prévenu plus tôt réagit plus tôt.
- Part des « marquer traité » effectués depuis un écran mobile.

---

## 8. Risques et arbitrages assumés

1. **iOS commande l'ordre des travaux.** Sans installation, pas de push. L'invitation à installer
   n'est donc pas un confort à ajouter en fin de chantier : c'est le préalable de tout le reste.
2. **Deux coquilles, pas deux produits.** Le risque réel est la duplication d'écrans qui
   divergeront. La proposition réutilise les mêmes services et les mêmes composants — c'est un
   ré-agencement de ce qui existe, pas un second front. Aucun écran existant n'est supprimé ni
   modifié dans son comportement de bureau (§4 bis de `Claude.md`).
3. **Le manifeste partagé** contraint les raccourcis : les routes `/go/*` sont la façon la plus
   simple de servir deux rôles sans deux applications installables. À revoir seulement si le
   produit décidait un jour de séparer les deux installations, ce qui n'est pas souhaitable.
4. **La donnée de santé en cache.** Arbitrage : on met en cache pour que le hors-ligne serve à
   quelque chose, avec un `maxAge` court et la purge de déconnexion existante — et on ajoute un
   test à cette purge plutôt que de s'en remettre à sa présence actuelle.
5. **La vue Jour change une habitude.** Un coach habitué au mois empilé pourrait la chercher : elle
   reste à un bouton, et le seuil de bascule est le seul endroit à régler si le retour terrain
   diverge.

---

## 9. Ce que ce document ne tranche pas

- **Le périmètre par défaut sur mobile.** Le cockpit propose quatre périmètres (tout le club, mes
  athlètes, privés, club) ; sur téléphone, il en faut **un**, choisi par défaut. « Mes athlètes »
  paraît le bon, mais c'est un arbitrage métier, à valider avec un coach de club multi-coachs.
- **Le sort des séances de force sur mobile.** La file de retours les unifie déjà (`kind:
  STRENGTH`) ; la vue Jour devra dire si elle les affiche au même rang.
- **Un raccourci « planifier »**. Poser une séance depuis un téléphone est possible en théorie
  (choisir un athlète, une date, un modèle de bibliothèque) mais c'est un geste de conception. À
  instruire seulement si les coachs le réclament après la vague 2.

---

*Audit du 13 août 2026 — état du dépôt à cette date. Les §1 à §3 sont vérifiés dans le code ; les
§4 à §9 sont une proposition, à arbitrer.*
