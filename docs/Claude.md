# Claude.md — Guide de collaboration IA ↔ Développeur (Plateforme de coaching course à pied)

> **Blueprint d'un SaaS de coaching sportif** (alternative à Nolio / TrainingPeaks), destiné aux
> **coachs indépendants et aux clubs** de course à pied (extensible trail / triathlon / cyclisme).
> Conçu pour démarrer un nouveau projet avec un contexte IA immédiatement opérationnel.
>
> ADN technique repris d'un SaaS B2B éprouvé : **Angular 17 (standalone) + Spring Boot 3 / Java 21 +
> PostgreSQL + Liquibase**, multi-tenant, mobile-first, RGPD-by-design.
> Sections _(hypothèse)_ = recommandations de conception, à valider avec le métier.

> ⚠️ **L'application est en bêta, en production, avec de vrais coachs et de vrais athlètes.**
> Ce n'est plus un projet vierge : il y a en base des historiques d'entraînement qui ne se
> reconstituent pas. Toute évolution part de là — **§4 bis « Ne rien casser de ce qui existe »
> prime sur le reste de ce document**.

---

## 1. Présentation synthétique

**[NOM DU PROJET]** (ex. *CoachRun*, *AtelierFoulée*…) est une application **SaaS** qui permet à un
**coach** (indépendant ou au sein d'un **club**) de **prescrire des plans d'entraînement**, de suivre
les **séances réalisées** par ses **athlètes** (synchronisées depuis leur montre GPS), de **communiquer**,
et de **piloter la charge et la performance** — le tout dans une interface moderne, web + mobile (PWA).

Objectif : remplacer Nolio par un outil **plus simple, plus joli, plus abordable**, utilisable aussi bien
par un coach privé avec 15 athlètes que par un club avec plusieurs coachs et groupes d'entraînement.

### Objectifs produit

| Objectif | Traduction concrète |
|---|---|
| **Prescrire facilement** | Calendrier d'entraînement glisser-déposer, séances structurées (intervalles, allures, zones), bibliothèque de séances réutilisables, plans périodisés (méso/microcycles) |
| **Suivre le réalisé** | Import auto des activités (Strava/Garmin/Coros), comparaison prévu vs réalisé, ressenti (RPE) et feedback de l'athlète |
| **Piloter la charge** | Charge d'entraînement (CTL/ATL/forme), volume hebdo, répartition par zones, alertes surcharge/sous-charge |
| **Garder le lien** | Messagerie coach↔athlète, commentaires par séance, notifications (push/email) |
| **Objectifs & courses** | Courses cibles avec chrono visé, compte à rebours, plans construits autour de l'objectif |
| **Monétiser** | Abonnements / forfaits de coaching privé, gestion des paiements |
| **Multi-coach / multi-club** | Plusieurs coachs par club, groupes d'athlètes, supervision |

### Rôles utilisateurs

- **`PLATFORM_ADMIN`** : supervision globale de la plateforme, gestion des clubs.
- **`HEAD_COACH` / `CLUB_OWNER`** : gère le club, les coachs, les groupes, la facturation.
- **`COACH`** : prescrit et suit ses athlètes (vue de travail).
- **`ATHLETE`** : consulte son plan, logue ses séances/ressentis, communique (portail dédié, accès simple type magic link).

> Mapping avec l'ADN source (garage) : `Club` ≈ tenant (garage), `Coach` ≈ propriétaire/mécanicien,
> `Athlete` ≈ client. **Le scoping multi-tenant se fait par `clubId` (ou `coachId` pour le coaching privé sans club).**

---

## 2. Modèle de domaine (entités cœur)

| Entité | Rôle | Statuts (enum) |
|---|---|---|
| `Club` | Tenant principal (ou coach solo = club implicite) | `ACTIVE / SUSPENDED` |
| `User` | Compte (rôles ci-dessus) | — |
| `Coach` | Profil coach (rattaché à un club) | — |
| `Athlete` | Profil sportif : seuils, zones FC/allure/puissance, VMA, poids, objectifs | `ACTIVE / PAUSED / ARCHIVED` |
| `TrainingGroup` | Groupe d'entraînement d'un club | — |
| `TrainingPlan` | Plan périodisé sur N semaines | `DRAFT → ACTIVE → COMPLETED / ARCHIVED` |
| `TrainingBlock` | Méso/microcycle (phase : base, spécifique, affûtage…) | — |
| `Workout` (séance prescrite) | Type, date, durée/distance cible, étapes structurées, intensité | `PLANNED → COMPLETED / PARTIAL / MISSED` |
| `WorkoutStep` | Bloc structuré (échauffement, répétitions, récup, retour au calme) avec cible (allure/FC/zone) | — |
| `WorkoutTemplate` | Séance réutilisable (bibliothèque) | — |
| `Activity` (réalisé) | Activité importée/synchronisée (distance, temps, allure, FC, D+, puissance) | `IMPORTED / MATCHED / UNMATCHED` |
| `WellnessLog` | Journal quotidien : sommeil, fatigue, courbatures, humeur, FC repos, HRV, poids | — |
| `RaceObjective` | Course cible : date, distance, chrono visé, priorité (A/B/C) | `UPCOMING / DONE / CANCELLED` |
| `Message` | Conversation coach↔athlète (globale ou liée à une séance) | — |
| `Subscription` | Forfait de coaching (privé) | `ACTIVE / PAST_DUE / CANCELLED` |
| `DeviceIntegration` | Connexion Strava/Garmin/Coros (tokens OAuth) | `CONNECTED / EXPIRED / REVOKED` |

**Enums d'intensité** : zones `Z1..Z5` (FC ou allure), et type de séance `ENDURANCE, RECOVERY, TEMPO, THRESHOLD, INTERVALS, LONG_RUN, RACE, STRENGTH, CROSS_TRAINING, REST`.

---

## 3. Système de modules (briques activables par club/coach)

Reprend le pattern « modules » du SaaS source — chaque club active ce dont il a besoin :

| Module | Périmètre |
|---|---|
| `TRAINING` | Plans, calendrier, séances structurées, bibliothèque |
| `PERFORMANCE` | Analytics : charge (CTL/ATL/forme), zones, volumes, comparaison prévu/réalisé |
| `WELLNESS` | Journal quotidien athlète (sommeil/fatigue/HRV) + alertes |
| `COMMUNICATION` | Messagerie, commentaires de séance, notifications |
| `BILLING` | Abonnements / forfaits coaching privé, paiements |
| `INTEGRATIONS` | Sync Strava / Garmin / Coros / fichiers FIT/GPX |

Annotation backend `@RequiresModule(Module.X)` + interceptor → 403 si module désactivé.

---

## 4. Principes de développement (inchangés — ADN à conserver)

1. **Lisibilité avant performance** ; pas d'over-engineering ; pas de features fantômes.
2. **Sécurité & intégrité par défaut** : toute route authentifiée sauf exception ; **scoping tenant systématique** (`findByIdAndClubId`) ; transitions d'état validées en service.
3. **Migrations versionnées Liquibase uniquement** — jamais de DDL manuel.
4. **Feedback utilisateur systématique** (toast succès/erreur).
5. **Mobile-first** : l'athlète consulte surtout sur téléphone → PWA, bottom-nav, cibles ≥44px.
6. **Idempotence des effets de bord** (sync, notifications) : réserve-puis-envoie, dédup.
7. **RGPD & données de santé** : les données physiologiques (FC, HRV, poids) sont **sensibles** → consentement explicite, chiffrement au repos, droit à l'oubli.
8. **Nommage anglais dans le code, libellés français dans l'UI.**
9. **L'application est en bêta, avec de vrais athlètes dedans** — voir §4 bis.

---

## 4 bis. Ne rien casser de ce qui existe (bêta en production)

> **Le contexte qui commande tout le reste.** Des coachs et des athlètes réels utilisent
> l'application aujourd'hui. Leur historique d'entraînement — séances, sorties importées, ressentis,
> tests — n'est pas un jeu de données de démonstration : il n'a pas de sauvegarde côté utilisateur,
> et il ne se reconstitue pas. Une correction qui abîme l'existant coûte plus cher que le défaut
> qu'elle répare, parce que le défaut se corrige encore demain alors que la donnée perdue, non.

**Règles, de la plus contraignante à la plus souple.**

1. **Une migration est additive, nullable, non destructive.** On ajoute des colonnes, on n'en
   supprime pas, on n'en renomme pas, on ne réécrit pas de données existantes. Une colonne devenue
   inutile reste en place plutôt que d'être droppée : le coût de la garder est nul, celui de se
   tromper est irréversible.
2. **Une donnée mal calculée se réinterprète à la lecture, elle ne se réécrit pas.** Quand un
   calcul a produit des valeurs fausses (un volume de séance qui ne totalise que ses éducatifs),
   la correction est une règle appliquée à l'affichage et aux agrégats — pas un `UPDATE` de
   rattrapage. La donnée d'origine reste consultable, et un correctif qui se révèle faux à son tour
   n'a rien détruit.
3. **Les réponses d'API s'enrichissent, elles ne se réduisent pas.** Un champ s'ajoute ; il ne se
   supprime ni ne se renomme, et son type ne change pas. Le front est une **PWA avec service
   worker** : des clients tournent encore sur une version antérieure pendant des jours. Rendre un
   champ optionnel côté TypeScript (`availableKinds?`) coûte une ligne et évite un écran blanc.
4. **Ce qui est lu depuis du JSON stocké doit tolérer l'ancien format.** Les colonnes JSON
   (`laps_json`, `session_snapshot`, `injuries_json`) contiennent des lignes écrites par des
   versions précédentes : `@JsonIgnoreProperties(ignoreUnknown = true)`, valeurs par défaut, et
   repli explicite quand le contenu est illisible — jamais d'exception qui empêche de lire la séance.
5. **Restreindre un comportement automatique se réfléchit deux fois.** Durcir une règle (un seuil
   de rapprochement, une validation de saisie) peut rendre inaccessible ce qui marchait pour
   quelqu'un. Si le durcissement est juste, garder un **geste manuel** qui rattrape le cas écarté.
6. **Élargir une validation est sûr, la resserrer ne l'est pas.** Une borne qu'on abaisse
   (`@Min(1)` → `@Min(0)`) ne casse rien ; une borne qu'on relève refuse des saisies que l'écran
   propose encore. Vérifier que la validation serveur et le composant de saisie disent la même chose.
7. **Un défaut constaté en bêta se corrige à la racine ET se raconte.** Le commentaire dit ce qui
   se passait avant, pas seulement ce que fait le code : c'est ce qui empêche quelqu'un de rétablir
   le défaut six mois plus tard en « simplifiant ».

**Avant de livrer, se poser ces trois questions.**

- Qu'arrive-t-il à un athlète dont les données ont été créées **avant** ce changement ?
- Qu'arrive-t-il à un téléphone qui a encore l'**ancien front** en cache ?
- Si ce correctif est faux, qu'est-ce qui est **définitivement perdu** ?

---

## 4 ter. Une version par déploiement, et elle n'avance que dans un sens

> **La règle, en une phrase : rien ne part en production sans un numéro de version nouveau, plus
> grand que le précédent, et porté par les *trois* endroits qui le déclarent.**

**Les trois endroits.** `front/package.json`, `back/pom.xml`, et `front/src/environments/*.ts`
(`appVersion`). Le troisième s'oublie — il l'a été : il annonçait encore `0.3.0` quand les deux
autres étaient passés à `0.4.0`. C'est pourtant celui qui se voit le plus : il part dans l'en-tête
`X-App-Version` de chaque requête, sert de `release` à Sentry, et s'écrit dans les e-mails de
support. Un `appVersion` figé rend indécidable un « ça marchait hier », puisque tous les
événements portent alors la même version. Les variantes (`-dev`, `-pwa`, `-docker`) suivent le
même numéro, avec leur suffixe.

**Pourquoi c'est une règle et pas une habitude.** Le front est une **PWA avec service worker** :
des téléphones tournent sur une version antérieure pendant des jours (§4 bis). Le jour où un
athlète dit « ça ne marche pas », la première question est « tu es sur quelle version ? » — et
elle n'a de réponse que si chaque déploiement en porte une, distincte. Deux déploiements qui
partagent un numéro rendent un rapport de bug ininterprétable et une régression impossible à
dater : on ne sait plus si le défaut est arrivé avec le correctif ou avec celui d'avant.

**Le numéro se réfléchit, il ne s'incrémente pas machinalement.** Format `MAJEUR.MINEUR.CORRECTIF`,
et c'est **le contenu du déploiement** qui décide lequel bouge :

| On incrémente | Quand le déploiement… | Exemples réels |
|---|---|---|
| **CORRECTIF** (`0.4.0` → `0.4.1`) | corrige un comportement sans rien ajouter ni changer de contrat | un gabarit Strava non reconnu, un NPE dans une notification, un libellé faux |
| **MINEUR** (`0.4.1` → `0.5.0`) | ajoute une capacité visible, une migration, un champ d'API, un écran | le fil de séance, le copier-coller en vue groupe, l'enrichissement du journal d'audit |
| **MAJEUR** (`0.x` → `1.0.0`) | rompt un contrat que quelqu'un consomme, ou marque un jalon produit décidé **par une personne** | sortie de bêta, refonte d'une API publique |

Le majeur ne se décide **jamais** seul : il se propose, il ne s'applique pas.

**Les invariants, dans l'ordre où ils comptent.**

1. **Jamais en arrière, jamais à l'identique.** Un numéro déjà déployé est brûlé, même si le
   déploiement a été annulé : on repart au-dessus, on ne réutilise pas. Vérifier avant d'écrire
   (`git log -p --follow front/package.json | grep version`, ou les tags) plutôt que de supposer.
2. **Les trois endroits disent la même chose.** Un front 0.5.0 en face d'un back 0.4.2 ne se
   diagnostique pas, et un `appVersion` en retard sur `package.json` ment à Sentry et au support.
   Vérifier les trois : `grep -rn "appVersion" front/src/environments/*.ts`, `package.json`, `pom.xml`.
3. **Le bump fait partie du changement, pas d'un commit à part.** Il voyage dans le commit ou la
   PR qu'il décrit, sans quoi il s'oublie — et un déploiement sans bump est précisément le cas
   qu'on cherche à rendre impossible.
4. **Un seul bump par déploiement.** Plusieurs correctifs livrés ensemble ne font qu'un numéro ;
   c'est le déploiement qu'on numérote, pas le commit.

**Ce qu'on écrit à côté du numéro.** Une ligne dans « [État actuel](#état-actuel) », en bas de ce
fichier : ce que la version **apporte**, en une phrase compréhensible par quelqu'un qui n'a pas lu
le diff. Un numéro sans phrase ne sert qu'à trier ; c'est la phrase qui répond à « qu'est-ce qui a
changé depuis lundi ? ».

---

## 5. Conventions de code (identiques au socle technique)

### Backend (Java / Spring Boot)
- Entités héritent de `BaseEntity` (`id` UUID, `createdAt`, `updatedAt`) — ne jamais redéclarer.
- `@RequiredArgsConstructor` + champs `final` (pas d'`@Autowired` sur champ).
- DTOs séparés `XxxRequest` / `XxxResponse` ; Request annotés `@JsonIgnoreProperties(ignoreUnknown = true)` + validation Jakarta (`@NotNull`, `@Min`, `@Pattern`…).
- Controllers : `@RestController`, `@RequestMapping("/clubs/{clubId}/<resource>")`, `@Transactional(readOnly = true)` au niveau classe + `@Transactional` sur les mutations.
- Scoping : `@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")`.
- Modules : `@RequiresModule(Module.TRAINING)` sur les controllers gated.
- Statuts en enums `SCREAMING_SNAKE_CASE`, transitions validées explicitement en service.
- `@Slf4j` ; **jamais de donnée de santé/PII brute ni de token dans les logs**.

### Frontend (Angular / TypeScript)
- **Tous** les composants `standalone: true`, dépendances dans `imports[]`, injection via `inject()`.
- Routing lazy `loadComponent` partout + guards fonctionnels par rôle (`coachGuard`, `athleteGuard`, `adminGuard`…).
- `ReactiveFormsModule` (formulaires complexes : éditeur de séance) / `FormsModule` (bindings simples).
- Pas de state manager global ; données chargées en `ngOnInit()` via services `core/services/`.
- `ToastService` sur toute action ; `ConfirmDialogService` au lieu de `confirm()`.
- SCSS par composant + design tokens globaux (cf. `Design.md`).

### Nommage
| Élément | Convention | Exemple |
|---|---|---|
| Entité JPA | PascalCase descriptif | `WorkoutTemplate`, `RaceObjective` |
| DTO | `XxxRequest` / `XxxResponse` | `WorkoutRequest` |
| Enum | SCREAMING_SNAKE_CASE | `WorkoutStatus.COMPLETED` |
| Service Angular | `xxx.service.ts` dans `core/services/` | `training-plan.service.ts` |
| Composant | `xxx.component.{ts,html,scss}` dans `features/xxx/` | `workout-editor.component.ts` |
| Migration | `NNN-description.yaml` (3 chiffres) | `012-add-wellness-log.yaml` |

---

## 6. Patterns récurrents (adaptés au métier)

- **Multi-tenant par URL** : `/clubs/{clubId}/athletes/{athleteId}/workouts/...` ; coach solo = club implicite.
- **Calendrier comme objet central** : le plan d'entraînement est une grille semaine × jour de `Workout`. Glisser-déposer pour replanifier (réutiliser le pattern drag&drop de l'agenda source, snap au jour).
- **Prévu vs Réalisé** : chaque `Workout` (planifié) peut être rapproché (`MATCHED`) d'une `Activity` (importée) → écart distance/temps/allure affiché.
- **Le rapprochement commence par le sport, pas par les chiffres** : une sortie n'est pas qu'une date, une distance et une durée. `MatchingService` écarte d'abord ce qui ne peut pas réaliser la séance — une séance de renforcement ou une sortie à vélo en face d'un fractionné de course — puis seulement compare des volumes. Un sport `null` (saisie manuelle, historique d'avant la colonne) ne bloque rien : l'absence de déclaration n'est pas une déclaration d'incompatibilité. La ressemblance des **titres** ne peut que rapporter des points, jamais en retirer : la plupart des sorties portent un nom composé par Strava, qui ne prouve rien, alors qu'un « 8*(200/400) » écrit par l'athlète désigne sa séance mieux qu'aucun chiffre.
- **Sans preuve, rien n'est rattaché** : le rapprochement automatique exige des **volumes qui concordent** (`MIN_VOLUME_AGREEMENT`) ou un **titre qui désigne la séance**. La date n'est pas une preuve — elle est vraie de toutes les sorties de la journée — et le score, qui sert à dire *laquelle*, ne doit jamais dire « oui » à lui seul. Il n'y a donc plus de repli « la seule séance du jour, faute de mieux » : il donnait la séance à la première sortie importée ce jour-là. Une sortie « non rattachée » se corrige d'un geste et se voit ; une séance déclarée faite par la mauvaise sortie fausse silencieusement le prévu/réalisé, le volume de la semaine et la charge.
- **Ranger, c'est perdre — alors on garde les deux** : `Activity.sport` est la famille (elle décide du rapprochement, elle doit être grossière), `Activity.sportDetail` est la catégorie exacte déclarée par la source (elle s'affiche, elle ne pilote rien). C'est ce découplage qui permet d'accepter `sportDetail` tel quel, sans connaître d'avance tout ce que Strava et les montres sauront un jour déclarer — un type non traduit s'affiche mis en forme plutôt que de disparaître.
- **La séance revient à la meilleure sortie du jour, pas à la première importée** : `ActivityService#autoMatch` peut reprendre une séance à une sortie moins bien placée quand une meilleure arrive derrière elle (la délogée repart `UNMATCHED`, avec son ressenti). Sans quoi c'est l'ordre de synchronisation qui trancherait — et il n'a aucun rapport avec ce que l'athlète a fait. Deux choses restent intouchables : un rapprochement décidé **à la main** (`Activity#manualMatch`), parce qu'une personne s'est prononcée, et l'égalité, où le premier arrivé reste faute de raison de bouger.
- **Un bloc de séance porte un volume et une allure — ou plusieurs, en enchaînement** : `CourseBlock.steps` décrit les efforts qui se succèdent **dans** une répétition (« 8 × (200 m / 400 m), 100 m de récup entre chaque »), chacun avec sa propre cible et sa propre récupération. Un bloc enchaîné n'a **pas** de cible à son niveau : moyenner un 200 lancé et un 400 en résistance ne décrirait ni l'un ni l'autre. `steps` vide = bloc simple, et c'est ce que vaut toute séance écrite avant : leurs totaux ne bougent pas d'un mètre.
- **Séance structurée** : éditeur d'étapes (`WorkoutStep`) répétables, avec cibles par zone (FC/allure/puissance). Bibliothèque de `WorkoutTemplate` applicable en un clic (comme les « templates OR » du socle).
- **Machine à états** : `Workout` (PLANNED→COMPLETED/PARTIAL/MISSED), `TrainingPlan` (DRAFT→ACTIVE→…). Mutation refusée si transition interdite.
- **Notifications centralisées** : `NotificationTriggerService` (push + email) sur séance commentée, plan publié, objectif J-7, alerte surcharge — gardé par toggles + consentement + idempotence.
- **Le calendrier remanié se notifie, toujours et une seule fois** : tout chemin par lequel le coach **ajoute, déplace, réécrit ou supprime** une séance de l'athlète — course *et* renforcement, structure comprise — passe par `NotificationService`. Trois gardes systématiques : séance encore à faire, pas dans le passé, et **changement réel** (enregistrer sans rien toucher ne notifie pas). Modification, déplacement et annulation partagent le type `WORKOUT_UPDATED` et une anti-rafale de 30 min : remanier une semaine est un seul geste pour le coach, ce doit en rester un pour l'athlète.
- **Une question posée quelque part se répond au même endroit** : le commentaire du coach vit sur la séance (`Workout.coachComment`), la réponse de l'athlète vit dans la messagerie (`Message.workoutId`) — et les deux se lisent **sur la séance**, des deux côtés. Un second canal propre à la séance aurait donné au coach deux boîtes à surveiller, c'est-à-dire une de trop ; c'est pourquoi le fil de séance est une **vue** de la messagerie, jamais une copie. La réponse laisse en revanche une trace sur la séance (`athleteReplyAt` / `athleteReplyReadAt`, miroir exact de `coachCommentAt` / `coachCommentReadAt`) : sans elle, rien ne pouvait dire au coach qu'on lui avait répondu, et une réponse arrivée le soir même se découvrait trois jours plus tard.
- **Un message qui parle d'une séance mène à la séance** : type `WORKOUT_REPLY`, lien vers la fiche, et une destination par séance — ce qui rend son anti-rafale utile (elle dédoublonne deux réponses sur la *même* séance) là où celle de la messagerie, une seule destination pour tout un fil, avalait la réponse derrière n'importe quel autre message. Le couple (a répondu, personne n'a lu) alimente ensuite **une seule** mécanique : l'alerte du cockpit, l'écran « Ma journée » et le digest de 7 h la lisent tous les trois.
- **L'e-mail reste le repli, mais il doit exister** : sur un iPhone où l'application n'est pas installée, le push système n'existe pas. Tout flux qui porte un **mot adressé à quelqu'un** (message, commentaire de séance, réponse) retombe donc sur l'e-mail quand aucun appareil n'est joignable — trois portes fermées d'abord : catégorie coupée dans les préférences, appareil joignable, e-mail refusé. Sans ce repli, le commentaire de séance n'annonçait strictement rien.
- **Copier une séance chez un autre athlète recalcule ses cibles** : `calculatedPaces` est figé pour l'athlète source. La copie inter-athlètes (`CourseSessionService#copyToAthlete`) reprend la **structure** — les fourchettes, qui sont la prescription du coach — et la recalcule pour la cible ; chez le *même* athlète, on duplique au contraire la prescription figée telle quelle, parce que c'est l'adaptation faite pour lui.
- **Un presse-papier autoportant** : les entrées du calendrier portent leur origine (athlète, date, modèle de force), pas seulement un identifiant. Résoudre une référence en relisant les signaux de la grille liait le geste à une vue — la vue groupe n'en avait donc aucun.
- **Un effort, un ressenti** : une sortie rapprochée et sa séance décrivent la même heure de course. Le ressenti (sensation, RPE, fatigue, douleur, commentaire, blessure) appartient à la **séance** dès qu'il y en a une ; la sortie n'en garde aucune copie, et toute lecture d'activité passe par `ActivityService#toResponse`, qui rend celui de la séance. Le rapprochement et le détachement le **transfèrent** (`adoptDebrief` / `releaseDebrief`) : changer de porteur ne doit jamais faire disparaître ce que l'athlète a écrit. Les six champs ne sont énumérés qu'à un seul endroit (`Debrief`) — deux copies d'une même liste finissent toujours par diverger, et c'est précisément la divergence qu'on corrige.
- **Un statut est une déclaration, et une déclaration se corrige** : `WorkoutStatus` n'a plus de transition interdite. La machine à états refusait PARTIAL → COMPLETED, donc refusait à un athlète de corriger son propre retour, alors que le même résultat s'obtenait en deux temps via PLANNED. Une garde qu'on contourne d'un clic ne protège rien.
- **Hors ligne ≠ refusé** : la file de synchronisation (`FeedbackQueueService`) n'accueille que ce que le **réseau** a empêché de partir (statut HTTP 0). Un 4xx est une réponse : on ne le rejoue pas, on l'affiche. Toute erreur y était mise en file, ce qui annonçait « enregistré hors ligne » à quelqu'un dont le retour venait d'être rejeté, et le rejouait à chaque reconnexion.
- **Frontière coach / athlète sur une même table** : `CalendarNote` porte deux choses. Une note **de période** est un **cycle** (« bloc spécifique », « affûtage ») : elle décrit l'entraînement et s'expose à l'athlète (`GET /me/cycles`). Une note **d'un seul jour** est le carnet de travail du coach (« relancer sur le sommeil », « surveiller ce genou »), écrite en le croyant privé : elle ne franchit **jamais** la frontière. Toute route `/me/...` qui lit une entité partagée avec le coach doit expliciter ce partage, jamais le déduire de la table.
- **Sync externe idempotente** : import Strava/Garmin dédupliqué par `externalId` (contrainte UNIQUE) ; webhook/polling + retry ; jamais de doublon d'activité.
- **Pré-remplissage par queryParams** : « Nouvelle séance » depuis la fiche athlète (`?athleteId=…&date=…`).
- **Composants partagés** : sélecteurs (athlète, séance), calendrier, graphiques (charge, zones), toast, confirm-dialog, skeletons, empty-state.

---

## 7. Bonnes pratiques ✅ / Anti-patterns ❌

✅ Une migration Liquibase par changement de schéma (+ include master).
✅ Scoper **toute** requête métier par tenant (`findByIdAndClubId`) — jamais `findById` seul.
✅ Dédupliquer les imports d'activités par `externalId` (UNIQUE) + idempotence.
✅ Chiffrer au repos les données de santé (FC repos, HRV, poids, pathologies) comme le socle chiffre IBAN/VIN.
✅ Valider les transitions d'état avant mutation.
✅ Toast sur chaque action ; libellés FR ; statuts traduits.
✅ **Un numéro de version nouveau et plus grand à chaque déploiement**, le même dans `package.json`, `pom.xml` **et `environments/*.ts`** (`appVersion`), choisi selon §4 ter — et « État actuel » mis à jour dans la foulée.
✅ Migration **additive et nullable** ; corriger une donnée fausse par une règle de **lecture**, pas par un `UPDATE` (§4 bis).
✅ Champ ajouté à une réponse d'API rendu **optionnel côté TypeScript** — des PWA tournent encore sur l'ancien front.
✅ Tolérer l'ancien format en relisant du JSON stocké : `@JsonIgnoreProperties`, valeur par défaut, repli explicite.
✅ Laisser un **geste manuel** de rattrapage quand on durcit une règle automatique.

❌ `alert()` / `confirm()` natifs (sauf suppression, et encore : `ConfirmDialogService`).
❌ Supprimer/renommer une colonne, un champ de réponse ou un enum utilisé en base — on ajoute, on ne retire pas.
❌ Réécrire des données d'athlètes pour rattraper un calcul fautif : le défaut se recorrige, la donnée perdue non.
❌ Resserrer une validation sans vérifier ce que l'écran laisse encore saisir (l'inverse — élargir — est sûr).
❌ Faire dépendre un test de la date du jour ou du programme de démonstration : un échec qui varie selon le jour de la semaine fait douter du code, pas du test.
❌ Composants `standalone: false`.
❌ DDL hors Liquibase / réutiliser un numéro de migration.
❌ `findById` non scopé → IDOR cross-club (un coach voit les athlètes d'un autre).
❌ Requête SQL sur un champ chiffré au repos (tourne sur le ciphertext).
❌ Stocker des tokens OAuth (Strava/Garmin) en clair ou les logguer.
❌ Logguer des données de santé / PII.
❌ Importer une activité sans dédup → doublons de charge faussant les stats.
❌ Ajouter une dépendance npm/Maven sans le mentionner.

---

## 8. Développer une nouvelle fonctionnalité (full-stack)

0. **Existant** : qu'y a-t-il déjà en base pour cette entité, et que devient-il ? (§4 bis)
1. **Migration** : `NNN-description.yaml` + include master — **additive et nullable**.
2. **Entité** : étend/crée (hérite de `BaseEntity`).
3. **DTOs** : `XxxRequest` (validé) + `XxxResponse` — champs **ajoutés**, jamais retirés ni renommés.
4. **Service** : logique métier, scoping tenant, transitions d'état, effets de bord (notifications/sync).
5. **Controller** : route scopée, `@PreAuthorize`, `@RequiresModule`, `@Valid`.
6. **Front** : `xxx.model.ts` (nouveau champ **optionnel**) → `xxx.service.ts` → composants `list/detail/form` standalone, routes lazy + guard.
7. **UX** : toasts, skeletons/empty-state, badges de statut, responsive ≤768px.
8. **Non-régression** : un athlète créé avant ce changement lit-il encore ses écrans sans erreur ?
9. **Versionner** selon §4 ter (correctif / mineur / majeur, jamais en arrière, les deux fichiers alignés) + mettre à jour « État actuel ».

### Exemples de premières features à livrer (MVP coaching)
1. CRUD athlètes + profil physiologique (zones FC/allure) + invitation par lien.
2. Calendrier d'entraînement + création/édition de séance structurée + bibliothèque de templates.
3. Import Strava (OAuth + sync activités) + rapprochement prévu/réalisé.
4. Vue athlète (PWA) : plan de la semaine, log de ressenti (RPE) + commentaire.
5. Messagerie + notifications push/email.
6. Graphiques de charge (volume hebdo, répartition par zone) — module PERFORMANCE.

---

## 9. Workflow recommandé pour Claude Code

1. **Lire le contexte** : ce fichier + `Design.md` + `Techno.md`.
2. **Explorer avant d'écrire** : copier les conventions d'un module voisin existant.
3. **Concis** : code d'abord, explication courte ensuite ; pas de préambule.
4. **Signaler sans forcer** les risques (sécurité, données de santé, dette) + 1 alternative si nettement supérieure.
5. **Implémenter le périmètre exact**, dans l'ordre du § 8 — étape 0 comprise.
6. **Référencer le code** par `chemin/fichier.ts:ligne`.
7. **Vérifier** : `npm run build` (typecheck AOT) + `mvn verify` (+ smoke test démarrage/Liquibase). Ne pas dire « fait » sans vérif.
   Lire le **code de sortie de la commande elle-même** : `mvn verify | grep …` rend le statut de `grep`, pas celui de Maven —
   annoncer une suite verte sur cette base, c'est annoncer n'importe quoi.
8. **Un test qui échoue n'est pas coupable par défaut.** Avant de modifier son attente, vérifier s'il échouait
   déjà avant le changement (worktree sur la base : `git worktree add … <commit>`). Corriger le code si c'est
   une régression, le test s'il dépendait d'un contexte instable — jamais l'inverse par confort.
9. **Git** : branche dédiée, commits clairs, push/PR uniquement sur demande.

---

## État actuel

> Une ligne par version déployée, la plus récente en haut (§4 ter). On y écrit ce que la version
> **apporte**, pas ce qu'elle touche : la phrase doit se comprendre sans avoir lu le diff, parce
> que c'est elle qui répond à « qu'est-ce qui a changé depuis lundi ? » et à « tu es sur quelle
> version ? ».
>
> Le tableau commence à 0.3.1 : les versions antérieures n'ont pas été journalisées, et on ne les
> reconstitue pas de mémoire — un historique inventé est pire qu'un historique qui commence tard.

| Version | Ce qu'elle apporte |
|---|---|
| **0.4.0** | Le **back-office voit l'usage** : chaque fiche de compte dit sur quoi la personne travaille (mobile / ordinateur), quelle version du front elle fait tourner, si un push peut réellement l'atteindre, si sa montre est branchée, et ses signes de vie. Le **journal d'audit** dit de quel droit (rôle figé au moment du geste), qui vraiment (écritures en session empruntée consignées avec l'administrateur derrière) et par où (appel HTTP, navigateur). |
| **0.3.1** | Le **fil de séance** : la question du coach et la réponse de l'athlète se lisent sur la séance, avec alerte au cockpit et notification qui mène à la séance. Le **copier-coller en vue groupe**, y compris « coller pour tout le groupe », avec recalcul des cibles chez l'athlète cible. Reconnaissance des noms Strava français du soir (« Course à pied en soirée »). |

---

*Blueprint coaching course à pied — générique et réutilisable par d'autres coachs/clubs. Adapter le nom produit
et activer/désactiver les modules selon l'offre (coach solo vs club, avec ou sans facturation/intégrations).*
