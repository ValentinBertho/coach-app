# Plan de mise en conformité — ouverture de DARI Lab Training en bêta ouverte

> **Ce document ne réaudite rien.** Il synthétise `AUDIT-FONCTIONNEL-2026-08.md` (métier),
> `AUDIT-BETA-OUVERTE-2026-08.md` (technique), et les points restés ouverts de
> `AUDIT-TECHNIQUE-2026-08.md`, `AUDIT-BETA-OUVERTE-2026-07.md` et
> `AUDIT-BETA-READINESS-2026-07.md`. Chaque ligne est écrite pour devenir un ticket.
>
> Les points qui n'ont **pas** été creusés sont listés au §6 plutôt que devinés.

---

## 1. Vue d'ensemble

### État des lieux

| Sévérité | Nombre | Origine |
|---|---|---|
| 🔴 **Bloquant** | **10** | 5 métier (B1–B5) · 2 RGPD code · 3 hors code (identité éditeur, sauvegardes, compte admin) |
| 🟠 **Gênant** | **15** | 10 métier (G1–G10) · 5 technique (autorisations, sessions, plafonds) |
| 🟢 **À améliorer** | **16** | 8 métier (A1–A8) · 8 technique (tests, dette, doc) |

*(Les sauvegardes apparaissaient deux fois dans l'audit technique — 🔴 3b et 🟠 9 — elles sont
comptées une seule fois ici.)*

### Répartition par nature

- **Légal / RGPD / sécurité** : 11 items — consentement santé, retrait, identité de l'éditeur,
  exposition des athlètes privés, autorisations club, sessions, plafonds anti-abus.
- **Métier / UX pur** : 23 items — prescription, charge, alertes, parcours athlète, bibliothèques.
- **Exploitation** : 6 items — sauvegardes, compte administrateur, monitoring, versionnement,
  support, collecte de retours.

### Délai avant une bêta ouverte *sûre*

Pas « sans bug » : sans risque juridique, sans donnée qui induise en erreur sur l'état physique
d'un athlète, et sans perte de données irrattrapable.

| Poste | Charge |
|---|---|
| Vague 0 — développement | **4,5 j** |
| Vague 0 — exploitation | **1,5 j** |
| Vague 1 — développement | **6 j** |
| Vague 1 — exploitation | **0,5 j** |
| **Total avant ouverture** | **≈ 12,5 j** |

**Délai calendaire réaliste : 3 semaines pour une personne à plein temps**, à condition de lancer
en parallèle et **dès maintenant** les deux chemins non compressibles :

1. la collecte de l'identité civile et de l'adresse de l'éditeur (§3, L-01) — dépend d'une décision
   humaine, pas d'un développement ;
2. la relecture juridique des CGU et de la politique de confidentialité (§3, L-04) — un audit de
   code ne peut pas la remplacer.

Si ces deux chemins démarrent le jour où la vague 0 démarre, ils ne sont pas sur le chemin
critique. Sinon, ils le deviennent et repoussent l'ouverture d'autant.

---

## 2. Plan d'action séquencé par vagues

> **La vague n'est pas la sévérité.** Certains 🟠 sont en vague 0 parce qu'ils se déclenchent le
> premier jour (exposition des athlètes privés, plafond d'e-mails à l'ouverture) ; certains 🔴 sont
> en vague 1 parce qu'ils ne mordent qu'après deux semaines d'usage et se traitent en parallèle.
>
> **Critère d'entrée en vague 0** : un bêta-testeur qui s'inscrit demain matin subit le problème
> dès la première semaine et sans recours, **ou** nous sommes en faute légale dès le premier
> inscrit.
>
> Effort : **S** ≤ 0,5 j · **M** 0,5–2 j · **L** > 2 j.

---

### Vague 0 — Bloquant absolu (≈ 4,5 j dev + 1,5 j ops)

> **État au 3 août 2026 — les huit items de code sont livrés et couverts par des tests.**
> `./mvnw clean verify` : **BUILD SUCCESS, 316 tests, 0 échec** (contre 295 avant le lot).
> `npm run build` : OK · `npm test` : **64/64**.
> Restent V0-09, V0-10 et V0-11, qui ne se règlent pas dans le code.
>
> **NC-01 tranché** (condition d'ouverture n° 4) : l'interface des plans périodisés est bien
> absente — aucune route, aucun appel d'API, aucun module `plans` côté front. Elle **ne remonte
> pourtant pas en vague 0** : le besoin « planifier plusieurs semaines » est couvert par un autre
> chemin, entièrement câblé (génération de mésocycle et duplication de semaine, individuelles et
> par groupe). Ce qui reste inaccessible, c'est le module `TrainingPlan` — neuf endpoints de code
> serveur sans écran. C'est une décision à prendre (construire l'écran ou retirer le module), pas
> un blocage d'ouverture.

#### ✅ V0-01 · Corriger le RPE prescrit affiché à l'athlète 🔴 · Métier · **S**
Le domaine d'intensité est reclassé depuis la vitesse moyenne du bloc, ce qui exige LT1 **et** LT2
mesurés ; sans eux le moteur retombe sur `DOMAIN_1` et affiche RPE 2–4 sur toutes les séances.
**À faire** : dériver le domaine du référentiel et du pourcentage **prescrits** (un bloc à 105 %
VMA ou à 100 % LT2 est en domaine 3 par construction) au lieu de repasser par une classification
de vitesse. En repli, afficher le RPE saisi par le coach sur le bloc plutôt qu'un RPE calculé faux.
**Fichiers** : `back/.../engine/SessionCalculatorEngine.java:88-90,246` ·
`back/.../engine/IntensityDomainEngine.java:29-47` ·
`front/.../shared/components/course-prescription-view/course-prescription-view.component.ts:56-57`
**Critère de recette** : un athlète sans test lactate, avec un chrono 10 km, voit RPE 7–9 sur un
bloc à 105 % VMA et RPE 2–4 sur un footing.

#### ✅ V0-02 · Ajouter « séance non faite » + durée réelle à la feuille de ressenti 🔴 · Métier · **M**
Deux boutons seulement (« réalisée » / « partiellement ») : l'athlète n'a aucun moyen de clôturer
une séance non faite, et « partiellement » ne dit ni combien ni pourquoi.
**À faire** : troisième action « Pas faite » avec motif court (imprévu / maladie / fatigue /
météo) écrivant `WorkoutStatus.MISSED` ; champ durée réelle affiché quand l'athlète coche
« Partiellement ».
**Fichiers** : `front/.../shared/components/workout-feedback-sheet/*.ts:76-78` ·
`back/.../service/WorkoutService.java:416-433` · `WorkoutFeedbackRequest` · migration Liquibase
pour la durée réalisée et le motif.
**Dépendance** : à faire avant V0-03, qui consomme la durée réelle.

#### ✅ V0-03 · Calculer la charge sur la durée réalisée 🔴 · Métier · **M**
`load = RPE × durée **prescrite**`, sans regarder le statut : une sortie longue abandonnée au tiers
compte pour 100 % et fait monter l'ACWR d'un athlète qui s'est entraîné moins.
**À faire** : ordre de préférence — durée saisie par l'athlète (V0-02), sinon durée de l'activité
rapprochée (`Activity.durationS`), sinon durée prescrite. Ne compter une séance `MISSED` en aucun cas.
**Fichiers** : `back/.../service/AthleteLoadService.java:110-125,147-160`
**Note d'honnêteté** : l'ACWR n'est publié qu'à partir de 21 jours d'historique et 8 séances, donc
l'effet visible arrive en semaine 3–4. Cet item est en vague 0 **parce qu'il partage la même
surface que V0-02**, pas parce qu'il mord le premier jour.

#### ✅ V0-04 · Périmer la pastille de forme 🔴 · Métier · **M**
Le dernier retour connu est classé sans borne de fraîcheur : un rouge de novembre reste rouge en
janvier, et la date n'est affichée nulle part.
**À faire** : fenêtre de fraîcheur (7–10 j, à trancher avec le coach référent) ; au-delà, statut
« pas de signal récent » distinct du vert et du rouge ; afficher la date du dernier retour sur la
carte athlète et dans la file « à surveiller ».
**Fichiers** : `back/.../service/AthleteFeedbackService.java:48-75` ·
`back/.../service/CoachDashboardService.java:183-186` ·
`front/.../features/dashboard/dashboard.component.{ts,html}`

#### ✅ V0-05 · Brancher le garde-fou de consentement santé sur tous ses points de collecte 🔴 · RGPD · **M**
`HealthDataConsentValidator` documente quatre familles de données de l'article 9 et n'est appelé
que par les tests de lactate. Le parcours normal du coach — créer l'athlète, remplir sa fiche,
**puis** l'inviter — collecte donc des données de santé sans base légale.
**À faire** : appeler `requireConsent` avant l'écriture des notes médicales, du motif
d'indisponibilité et de la douleur/fatigue de séance.
**Fichiers** : `back/.../service/AthleteService.java:259` ·
`back/.../service/UnavailabilityService.java:43,59` ·
`back/.../service/WorkoutService.java:416` (submitFeedback) · `DailyCheckInService.save` ·
`StrengthScheduleService.submitFeedback`
**Recette** : tests d'accès sur le modèle de `HealthConsentTest`, un par point de collecte.

#### ✅ V0-06 · Compléter l'effacement au retrait de consentement 🔴 · RGPD · **S**
Le retrait efface `workout.pain` mais pas `workout.fatigue`, et ignore entièrement la préparation
physique — alors que le journal `[RGPD]` affirme le contraire.
**À faire** : effacer `workout.fatigue`, `ScheduledStrengthSession.sessionPain` /
`sessionFatigue`, `StrengthResult.pain` ; corriger le décompte journalisé.
**Fichiers** : `back/.../service/GdprService.java:149-197`

#### ✅ V0-07 · Filtrer le périmètre « Tout le club » par les droits athlète 🟠→bloquant · Sécurité/RGPD · **S**
Le cockpit renvoie tout le club sans passer par `AthleteAccessValidator` — un coach assistant voit
la fatigue et la douleur des athlètes **privés** de ses collègues. Le calendrier de groupe, lui,
filtre correctement : c'est un oubli, pas un choix.
**À faire** : filtrer `athletesInScope` par `accessValidator.effectiveLevel(coachId, athleteId)`,
sur le modèle de `TrainingGroupService.calendar:60-64`. S'applique aux trois consommateurs :
KPI, tableau de forme, file d'alertes.
**Fichiers** : `back/.../service/CoachDashboardService.java:199-215`
**Pourquoi vague 0** : se déclenche dès le premier club à deux coachs, et porte sur de la donnée
de santé.

#### ✅ V0-08 · Plafonner les routes anonymes qui envoient un e-mail 🟠→bloquant · Sécurité · **S**
Le bucket à 3 envois/h ne couvre que les routes authentifiées. `/auth/register` et
`/public/password-reset` restent à 20 req/min/IP — et **ouvrir la bêta, c'est précisément passer
`REGISTRATION_MODE=open`**, donc exposer le quota Resend (100/jour, partagé avec les
réinitialisations et les invitations) à ce plafond.
**À faire** : bucket dédié aux routes anonymes déclenchant un envoi, quelques envois par heure et
par IP.
**Fichiers** : `back/.../security/RateLimitFilter.java:75-90,118-128`
**Pourquoi vague 0** : c'est le geste d'ouverture lui-même qui arme le problème.

#### V0-09 · Renseigner l'identité et l'adresse de l'éditeur 🔴 · Légal · **S** *(hors code)*
`legalName` et `address` sont vides. L'exemption LCEN couvre les mentions légales, pas l'article 13
du RGPD, qui impose l'identité du responsable de traitement — et le service traite des données de
santé.
**À faire** : renseigner `legalName` (prénom + nom, ou raison sociale + forme juridique + SIREN) et
`address` (adresse postale complète). Les blocs s'affichent automatiquement une fois remplis.
**Fichiers** : `front/.../features/public/legal.component.ts:34-35`
**Chemin non compressible** : à lancer le premier jour de la vague 0.

#### V0-10 · Planifier les sauvegardes et **exécuter** une restauration 🔴 · Exploitation · **M**
`ops/backup-db.sh` est bien écrit et documenté ; **rien ne l'exécute** — aucun cron, aucun workflow.
Et la restauration n'a jamais été jouée.
**À faire** : (a) planifier le script (cron sur un hôte, ou job planifié) avec alerte sur code de
sortie ; (b) confirmer et documenter l'état des sauvegardes managées de l'hébergeur ;
(c) **restaurer un dump dans une base jetable et vérifier qu'on peut se connecter à l'application
dessus** — c'est la seule étape irrattrapable de tout ce plan.
**Fichiers** : `ops/backup-db.sh` · `docs/OPERATIONS.md` §5

#### V0-11 · Créer le compte administrateur de plateforme en production 🔴 · Exploitation · **S**
Sans lui, `/admin` est inatteignable : ni révocation d'invitation, ni suppression de compte coach —
c'est-à-dire **aucun moyen d'honorer une demande d'effacement**, que la politique de
confidentialité promet pourtant par e-mail.
**À faire** : poser `PLATFORM_ADMIN_EMAIL` et `PLATFORM_ADMIN_PASSWORD` (≥ 12 caractères) sur
l'hébergeur, redéployer, se connecter, changer le mot de passe depuis l'application.
**Référence** : runbook §1.1 bis · `back/.../config/PlatformAdminBootstrap.java`

---

### Vague 1 — Bloquant pour ouvrir, parallélisable (≈ 6 j dev + 0,5 j ops)

> **État au 3 août 2026 — les huit items de code sont livrés et couverts par des tests.**
> `./mvnw clean verify` : **BUILD SUCCESS, 325 tests, 0 échec** (295 avant la vague 0).
> `npm run build` : OK · `npm test` : **64/64**.
> Reste V1-09, qui relève de l'exploitation (DSN Sentry backend et tags de déploiement).

#### ✅ V1-01 · Faire taire les alertes sur un athlète déclaré indisponible 🔴 · Métier · **M**
L'indisponibilité n'écrit que des dates : les séances restent planifiées, et l'athlète blessé
cumule « séances manquées », « athlète silencieux » et ACWR bas — renvoyés **intégralement chaque
matin** par un digest sans mémoire.
**À faire** : (a) exclure les fenêtres d'indisponibilité des alertes `MISSED`, `SILENCE` et
`ACWR_LOW` ; (b) donner un état au digest (ne pas renvoyer une alerte déjà envoyée et non résolue) ;
(c) action « replanifier / vider la période » depuis l'indisponibilité, pour éviter la suppression
séance par séance.
**Fichiers** : `back/.../service/CoachDashboardService.java:267-292` ·
`back/.../scheduler/AlertDigestScheduler.java:88-105` ·
`back/.../service/UnavailabilityService.java:97-105` · `WorkoutController` (suppression en lot)
**Pourquoi vague 1** : mord à partir de la deuxième ou troisième semaine, et se construit
indépendamment du reste.

#### ✅ V1-02 · Seeder un catalogue d'exercices et de catégories 🟠 · Métier · **L**
Seules les zones et les métriques sont provisionnées. Un préparateur physique doit saisir 40 à 60
exercices à la main avant sa première séance de force — le point d'abandon le plus probable d'un
bêta-testeur non accompagné.
**À faire** : catalogue d'exercices de préparation physique et de catégories de séance, seedé comme
les zones (idempotent, à la première lecture), dupliqué au club et éditable ensuite.
**Fichiers** : nouveau service sur le modèle de
`back/.../service/TrainingZoneSeedService.java` · `PpExerciseService` · `SessionCategoryService`

#### ✅ V1-03 · Dédoublonner les imports fichier et manuels 🟠 · Métier · **M**
La déduplication ne porte que sur `(athleteId, source, externalId)`, et `externalId` n'est jamais
posé pour un GPX/TCX ni pour une saisie manuelle. Un athlète qui importe son fichier **et** connecte
Strava a deux fois la même sortie. Le récapitulatif hebdomadaire somme tout : « 64/45 km » sur une
semaine à 32 km.
**À faire** : (a) empreinte de déduplication indépendante de la source (athlète + date + distance
et durée à une tolérance près), proposée à l'utilisateur plutôt que refusée sèchement ;
(b) ne compter dans `weekSummary` que les activités non doublonnées.
**Fichiers** : `back/.../service/ActivityService.java:95-97,175,206-210` ·
`back/.../service/AnalyticsService.java:74-81`
**Référence** : critère d'acceptation « zéro doublon » du cahier des charges §11.

#### ✅ V1-04 · Contrôler l'accès athlète sur l'assignation d'un cycle de force 🟠 · Sécurité · **S**
`POST /clubs/{clubId}/pp/cycles/{id}/assign/{athleteId}` n'est protégé que par le contrôle de club :
tout coach peut planifier N semaines chez un athlète privé d'un collègue. Les deux routes
équivalentes font le contrôle.
**À faire** : ajouter `@athleteAccessValidator.canWrite(authentication, #athleteId)`.
**Fichiers** : `back/.../controller/StrengthCycleController.java:66` (+ test d'accès)

#### ✅ V1-05 · Faire respecter les rôles de club 🟠 · Sécurité · **M**
N'importe quel coach du club peut en retirer un autre (seul le propriétaire est protégé) et en
ajouter — le `ClubRole` existe en base et n'est jamais consulté en autorisation.
**À faire** : validateur de rôle club ; réserver l'ajout et le retrait de membres à
`OWNER` / `COACH_PRINCIPAL`.
**Fichiers** : `back/.../controller/ClubController.java:60,73` ·
`back/.../service/ClubMembershipService.java:129-140`

#### ✅ V1-06 · Plafonner les flux SSE de messagerie 🟠 · Sécurité/stabilité · **S**
Le plafond posé en août n'a été appliqué qu'au compteur de notifications ; la messagerie n'a ni
borne ni purge, pour la même cause (le proxy coupe mal les connexions longues, `EventSource`
rouvre seul).
**À faire** : recopier le plafond par clé et la fermeture du plus ancien.
**Fichiers** : `back/.../service/MessageStreamService.java:29`

#### ✅ V1-07 · Révoquer réellement la session au logout 🟠 · Sécurité · **M**
La déconnexion ne blackliste que l'access token ; le refresh (30 jours) reste valable côté serveur,
et la liste noire en mémoire est vidée à chaque redéploiement.
**À faire** : colonne `sessions_invalidated_at` posée au logout et lue par `TokenFreshnessValidator`,
sur le modèle de `passwordChangedAt`. Pas de dépendance externe nécessaire.
**Fichiers** : `back/.../controller/AuthController.java:84` ·
`back/.../security/TokenFreshnessValidator.java` · migration Liquibase

#### ✅ V1-08 · Garde-fou sur le test 1RM qui contredit le profil 🟠 · Métier · **S**
Un test direct écrase toujours le profil : un AMRAP mal placé fait chuter le e1RM de 15 %, toutes
les charges prescrites suivent, et la séance suivante déclenche une alerte « chute de charge »
causée par le recalcul de l'outil.
**À faire** : signaler l'écart au-delà de ±10 % et demander confirmation avant d'écraser ; tracer
la valeur précédente.
**Fichiers** : `back/.../service/StrengthTestService.java:103-115` · écran de saisie du test

#### V1-09 · Poser le DSN Sentry backend et versionner les déploiements 🟠 · Exploitation · **S**
Le DSN front est committé ; le backend n'a rien tant que `SENTRY_DSN` n'est pas posé. Et sans tag
git par déploiement, tous les événements portent la même version — un « ça marchait hier » devient
indécidable.
**À faire** : `SENTRY_DSN` + `SENTRY_ENV=production` sur l'hébergeur ; règle d'alerte « nouvelle
anomalie → e-mail » ; tag git à chaque déploiement, aligné sur `appVersion`.
**Référence** : `docs/OPERATIONS.md` · runbook §4

---

### Vague 2 — Dans les 2 à 4 semaines suivant l'ouverture

| # | Action | Fichiers | Effort | Nature |
|---|---|---|---|---|
| V2-01 | Rendre les check-ins visibles au coach (endpoint + historique 14 j, sommeil compris — aujourd'hui il n'arrive nulle part) | nouveau contrôleur coach · `DailyCheckInService` · `AthleteFeedbackService` | M | Métier |
| V2-02 | Rendre l'alerte « chute de charge » comparable : même agrégat des deux côtés (max vs max), et neutralisation pendant une semaine de décharge programmée | `ProgressionEngine.java:76-79` · `ProgressionService.java:84,116-124` | S | Métier |
| V2-03 | Agréger les alertes de force au tableau de bord (aujourd'hui : une séance à ouvrir à la main, ~50/semaine pour 25 athlètes) | `ProgressionService` · `CoachDashboardService.alerts` | M | Métier |
| V2-04 | Comparer la **structure** et non le seul volume avant de valider une séance ; refuser la validation automatique quand la séance n'a ni distance ni durée cible | `MatchingService.java:35-42,66-68` | M | Métier |
| V2-05 | Signaler au coach les séances déplacées par l'athlète (champs déjà renvoyés par l'API, absents du modèle front) + garde-fous (pas de déplacement dans le passé, alerte au-delà de N séances/jour) | `front/.../core/models/workout.model.ts` · calendrier coach · `WorkoutService.moveByAthlete:440-449` | M | Métier |
| V2-06 | Compte athlète : changement de mot de passe, de nom et d'adresse dans la PWA (les endpoints existent déjà et acceptent le rôle) | `front/.../features/athlete/profile.component.ts` | M | UX |
| V2-07 | Contrôle `canRead` sur la lecture des zones par `?athleteId=` | `TrainingZoneController.java:45-47` | S | Sécurité |
| V2-08 ✅ | Purger `emailLimiter` avec les autres fenêtres — *livré avec V0-08, même méthode* | `RateLimitFilter.java` | S | Technique |
| V2-09 | Tests des trois moteurs sans couverture (`CriticalSpeedEngine`, `PlannedLoadEngine`, `PaceUtil`) | `back/src/test` | M | Technique |
| V2-10 | Paginer le fil de messages (chargé entier aujourd'hui) | `MessageService.java:47,113` · `MessageController` | M | Technique |
| V2-11 | Remettre le README d'équerre (tests, contrôleurs, services, moteurs, migrations, endpoints, « import FIT » non implémenté) | `README.md` | S | Doc |
| V2-12 | Aligner la javadoc de `PlannedLoadEngine` sur le code (« récupérations comprises » vs exclues) | `PlannedLoadEngine.java:23,47-50` | S | Doc |

---

### Vague 3 — Amélioration continue post-bêta

| # | Action | Effort | Nature |
|---|---|---|---|
| V3-01 | Repli des cibles de FC sur un % de FC max quand LT1/LT2 manquent (aujourd'hui : aucune cible FC sans passage au labo) | M | Métier |
| V3-02 | Permettre à un athlète d'appartenir à plusieurs groupes (`Athlete.group` est un `@ManyToOne`) | L | Métier |
| V3-03 | Calendrier club global multi-athlètes (n'existe aujourd'hui que par groupe) | L | Métier |
| V3-04 | Bilan post-course (réalisé vs chrono visé) | M | Métier |
| V3-05 | Wellness étendu : HRV, humeur, poids quotidien | L | Métier |
| V3-06 | Ouvrir une séance depuis le fil de messages côté athlète | S | UX |
| V3-07 | Export de données brutes pour un préparateur physique externe (aujourd'hui : PDF seulement) | M | Métier |
| V3-08 | Import FIT, puis Garmin / COROS | L | Métier |
| V3-09 | Facturation et abonnements | L | Métier |
| V3-10 | Jetons courts signés à la place du jeton en paramètre d'URL (SSE et pièces jointes) | M | Sécurité |
| V3-11 | Pièces jointes vers un stockage objet (aujourd'hui en base, quota 200 Mo/club) | M | Technique |
| V3-12 | SSE multi-instance (pub/sub) — prérequis à tout passage à deux pods | M | Technique |
| V3-13 | Environnement de préproduction (la CI est aujourd'hui le seul filet entre un commit et la production) | M | Exploitation |
| V3-14 | Mesure d'usage produit : aucun compteur n'existe, Sentry dit ce qui casse, pas ce qui sert | M | Produit |
| V3-15 | Purge des comptes inactifs à 24 mois, annoncée par la politique de confidentialité | M | RGPD |
| V3-16 | Tests de bout en bout (aucun aujourd'hui) et Testcontainers (les tests tournent sur H2) | L | Technique |
| V3-17 | Budget de bundle front (608 kB pour 500 kB annoncés) | S | Technique |
| V3-18 | Durcir `clubLevelFallback`, qui accorde l'écriture par défaut quand la relation référente manque | S | Sécurité |

---

## 3. Check-list de mise en conformité légale et RGPD

> **Un audit de code ne valide pas un texte juridique.** La colonne « Vérifiable » distingue ce que
> le code prouve de ce qui demande une relecture humaine.

### Documents et mentions

| # | Point | État | Vérifiable par le code ? |
|---|---|---|---|
| L-01 | **Identité et adresse du responsable de traitement** (RGPD art. 13-1-a) | ❌ **Champs vides** — cf. V0-09 | Oui : champs présents, valeurs vides |
| L-02 | Mentions légales | ⚠️ Page présente, exemption LCEN éditeur non professionnel invoquée | Partiellement — l'éligibilité à l'exemption est un jugement humain |
| L-03 | CGU (dont avertissement santé et clause de bêta) | ⚠️ Page présente, acceptation horodatée côté coach **et** athlète | Le mécanisme oui, **le contenu non** |
| L-04 | Politique de confidentialité | ⚠️ Page présente, sous-traitants déclarés | Le mécanisme oui, **le contenu non** — **relecture juriste requise** |
| L-05 | CGV | ➖ Sans objet tant qu'il n'y a pas de paiement (facturation en vague 3) | — |
| L-06 | Coordonnées de contact RGPD | ✅ Adresse de contact centralisée et utilisée par le lien de support | Oui |

### Consentement et droits des personnes

| # | Point | État | Vérifiable par le code ? |
|---|---|---|---|
| L-07 | **Consentement explicite aux données de santé** (art. 9) recueilli à l'acceptation de l'invitation | ✅ Exigé côté serveur | Oui |
| L-08 | **Consentement vérifié avant chaque collecte** | ❌ **Branché sur un seul service** — cf. V0-05 | Oui |
| L-09 | **Retrait du consentement aussi simple que son octroi** (art. 7-3) | ⚠️ Endpoint et écran présents, **effacement incomplet** — cf. V0-06 | Oui |
| L-10 | Consentement distinct pour la connexion d'appareils (`deviceConsentAt`) | ✅ Champ dédié, séparé du consentement santé | Oui |
| L-11 | **Droit à l'effacement** | ⚠️ Suppression d'athlète et de compte coach possibles, **mais uniquement depuis `/admin`** — inatteignable sans V0-11 | Oui |
| L-12 | **Portabilité / export** des données de l'athlète | ✅ Export RGPD incluant les données de santé | Oui |
| L-13 | Droit de rectification | ✅ Profil éditable côté coach ; ⚠️ côté athlète, nom et adresse non éditables dans la PWA (V2-06) | Oui |
| L-14 | Traitement des **mineurs** (licenciés de club) : âge minimum, consentement du représentant légal | ❓ **Non traité par aucun audit** — cf. §6 | Non — décision produit et juridique |

### Sous-traitants, conservation, sécurité

| # | Point | État | Vérifiable par le code ? |
|---|---|---|---|
| L-15 | **Liste des sous-traitants** à jour dans la politique (hébergeur back + BDD, hébergeur front, e-mail, monitoring, Strava, forge) | ⚠️ Déclarés dans la page ; **cohérence à revérifier après chaque ajout d'outil** | Partiellement |
| L-16 | **DPA signés** avec chaque sous-traitant | ❓ **Hors code** — à collecter et archiver | Non |
| L-17 | Hébergement des données de santé dans l'UE | ⚠️ Sentry configuré en région UE ; **à confirmer pour l'hébergeur back, la BDD et l'e-mail** | Non |
| L-18 | Question HDS (hébergeur de données de santé) tranchée | ❓ Le cahier des charges dit « non requis a priori, à confirmer juridiquement » — **toujours ouvert** | Non |
| L-19 | **Registre des traitements** | ❓ **Hors code** — à rédiger | Non |
| L-20 | Durée de conservation annoncée (24 mois d'inactivité) et **appliquée** | ⚠️ Annoncée, **non implémentée** — cf. V3-15 | Oui |
| L-21 | Chiffrement au repos des données de santé et des jetons OAuth | ✅ AES-256-GCM, IV par valeur | Oui |
| L-22 | Non-exposition des données de santé dans les journaux et le monitoring | ✅ `send-default-pii: false`, journaux sans valeurs de santé | Oui |
| L-23 | Procédure de notification de violation (72 h) | ❓ **Hors code** — cf. §4, OPS-04 | Non |

**Bloquants légaux avant ouverture** : L-01, L-08, L-09, L-11 (via V0-11).
**À trancher humainement avant ouverture** : L-04 (relecture), L-14 (mineurs), L-16 (DPA),
L-17/L-18 (localisation et HDS), L-19 (registre), L-23 (procédure de violation).

---

## 4. Check-list opérationnelle de lancement

| # | Point | État | Action |
|---|---|---|---|
| OPS-01 | **Sauvegardes automatiques planifiées** | ❌ Script écrit, rien ne l'exécute | V0-10 (a) |
| OPS-02 | **Restauration testée de bout en bout** | ❌ Jamais exécutée | V0-10 (c) — **la seule étape irrattrapable** |
| OPS-03 | **Monitoring actif** (erreurs serveur + front, alerte sur nouvelle anomalie) | ⚠️ Front configuré, backend en attente du DSN | V1-09 |
| OPS-04 | **Plan de réponse à incident** : qui est prévenu, en combien de temps, comment on annonce une interruption, comment on notifie une violation en 72 h | ❓ Non formalisé | À rédiger — 0,5 j |
| OPS-05 | **Canal de support utilisateur** | ✅ Formulaire de retour en application, file de traitement côté admin, repli par e-mail | Vérifier de bout en bout après déploiement |
| OPS-06 | **Collecte des retours de bêta** | ✅ Formulaire avec contexte automatique (page, version, navigateur, identifiant de corrélation) | Définir qui dépouille la file, et à quelle fréquence |
| OPS-07 | **Compte administrateur de plateforme** | ❌ Absent en production | V0-11 |
| OPS-08 | **Variables d'environnement de production complètes** | ⚠️ Le garde-fou de démarrage couvre secrets, URL, CORS, VAPID, relais de confiance et code d'invitation ; il **ne couvre pas** le compte admin | V0-11 + revue de la liste du runbook |
| OPS-09 | **Versionnement des déploiements** (tag git ↔ version applicative) | ❌ Aucun tag | V1-09 |
| OPS-10 | **Limite du nombre de bêta-testeurs** | ⚠️ Le mode « invitation » permet une cohorte fermée ; passer en ouvert lève toute limite | Voir ci-dessous |
| OPS-11 | Environnement de préproduction | ❌ La CI est le seul filet | Vague 3 — accepté (§5) |
| OPS-12 | Tenue en charge mesurée | ❓ **Jamais mesurée** — cf. §6 | Voir OPS-10 |

### Recommandation sur le volume de bêta-testeurs

Aucun test de charge n'a été réalisé, l'application tourne sur **une seule instance**, et les flux
temps réel sont en mémoire (non répartissables). Deux garde-fous simples, sans développement :

1. **Ouvrir par paliers** — garder `REGISTRATION_MODE=invite` avec un code partagé, et le diffuser
   par vagues de 10 à 15 coachs. On garde la main sur le robinet sans code, et V0-08 reste requis
   pour le jour où l'on bascule en ouvert.
2. **Plafond indicatif à surveiller** : ~50 coachs actifs, en observant trois signaux — connexions
   à la base, mémoire de l'instance, et nombre de flux temps réel ouverts (le plafond de V1-06 les
   rend comptables). Franchir ce palier sans mesure préalable, c'est découvrir la limite en
   production.

---

## 5. Ce qu'on accepte sciemment de livrer imparfait

| Point | Justification |
|---|---|
| **SSE mono-instance** — les flux temps réel sont en mémoire, donc non répartissables | Une seule instance en bêta. Le jour où l'on passe à deux pods, la messagerie se coupe : c'est un prérequis au scale-out, pas à l'ouverture. Tenable tant que OPS-10 est respecté. |
| **Jeton de session dans l'URL** pour les flux temps réel et les pièces jointes | Restreint à deux suffixes de route et désormais compté par le rate limiting. Le jeton fuit dans les journaux d'accès et l'historique, avec une durée de vie d'1 h. Risque borné, correction non triviale (jetons signés courts) : vague 3. |
| **Fil de messages sans pagination** | Chargé entier. À la volumétrie d'une bêta — quelques dizaines de messages par athlète — sans effet perceptible. Le composant de pagination existe déjà côté front. |
| **Pièces jointes stockées en base** | Le quota par club est en place et refusé proprement. Le vrai risque est la taille du dump — donc OPS-01/02, pas le stockage objet. |
| **Tests sur H2 plutôt que PostgreSQL réel** | Le démarrage est vérifié sur PostgreSQL réel en intégration continue, migrations comprises. L'écart résiduel porte sur des comportements SQL fins, non sur le schéma (cohérence schéma/entités vérifiée : 50 tables, aucun écart). |
| **Aucun test de bout en bout** | 295 tests back et 63 front, verts. La couverture est bonne sur les moteurs et les accès ; elle manque sur les parcours. Coût élevé, valeur surtout en régression : après la bêta. |
| **Pas de préproduction** | Un environnement de plus à tenir pour une cohorte de quelques dizaines. La CI plus un déploiement réversible suffisent à ce stade. |
| **Purge des comptes inactifs non implémentée** | Annoncée à 24 mois par la politique. Aucun compte ne peut l'atteindre avant deux ans : l'écart entre le texte et le code est réel mais sans effet pratique pendant la bêta. À implémenter bien avant l'échéance. |
| **Un athlète = un seul groupe** | Gênant pour un club qui croise ses groupes (piste le mardi, sortie longue le dimanche), sans effet en coaching individuel. Contournable par un découpage unique. Changement de modèle de données : vague 3. |
| **Garmin / COROS absents** | Annoncé comme tel dans le produit et l'aide. Le repli GPX/TCX couvre l'essentiel — à condition que V1-03 (dédoublonnage) soit livré, sinon le repli fabrique des doublons. |
| **Facturation absente** | Priorité C au cahier des charges, hors périmètre d'une bêta gratuite. Rend aussi les CGV sans objet (L-05). |

---

## 6. Points non creusés — à vérifier, pas à deviner

Ces points peuvent être bloquants. Aucun audit ne les a instruits ; ils sont listés tels quels.

| # | Point | Pourquoi c'est ouvert | Coût de la vérification |
|---|---|---|---|
| NC-01 ✅ | **Interface des plans périodisés** — *vérifié le 3 août : absente, mais non bloquante (cf. vague 0)* | L'audit de juillet la donnait absente (fonctionnalité complète côté serveur, aucun écran) ; l'audit technique d'août la listait toujours ouverte. Aucun des deux passages récents ne l'a revérifiée. Indice : il n'existe pas de module `plans` côté front. **Si c'est confirmé, un pan entier du produit est inaccessible et cela remonte en vague 0.** | 30 min |
| NC-02 | **Accessibilité** | L'audit de juillet relevait deux points bloquants (curseurs du check-in non stylés sous Chrome, contraste insuffisant sur le texte secondaire en thème clair) et les déclarait corrigés. Non revérifié depuis. | 1 h |
| NC-03 | **Tenue en charge** | Jamais mesurée. Nombre d'utilisateurs simultanés supportés, comportement des flux temps réel sous charge, saturation du pool de connexions : inconnus. Conditionne OPS-10. | 0,5 j |
| NC-04 | **Comportement hors ligne réel de la PWA** | La file de retours hors ligne existe et est testée unitairement ; jamais éprouvée en conditions réelles (tunnel, perte de réseau en cours de séance) — or c'est le cas d'usage nominal d'un athlète en sortie. | 0,5 j |
| NC-05 | **Qualité juridique des textes** | CGU et politique de confidentialité lues comme du code, jamais relues par un juriste. Le mécanisme est bon ; le fond n'est pas validé. | Externe — à lancer maintenant |
| NC-06 | **Athlètes mineurs** | Aucun audit ne l'aborde. Un club de course à pied en compte presque toujours. Âge minimum, consentement du représentant légal, mentions adaptées : à trancher. | Décision produit + juridique |
| NC-07 | **Parcours de bout en bout non rejoué sur l'environnement de production** | Les audits ont lu le code et exécuté les builds ; personne n'a fait le trajet complet inscription → invitation → acceptation → prescription → retour sur la production déployée. | 0,5 j |

---

## 7. Recommandation

### 🟡 GO CONDITIONNEL

**Le produit est mûr — l'application est riche, les moteurs sont conformes à ce qu'ils annoncent,
les builds sont verts, et le cloisonnement multi-tenant tient.** Ce qui bloque n'est pas de la
dette : ce sont dix points précis, dont cinq faussent une donnée que coach et athlète prennent pour
vraie, et trois ne se règlent pas dans le code.

**Conditions d'ouverture, sans exception :**

1. **Vagues 0 et 1 livrées** (V0-01 → V0-11, V1-01 → V1-09) — ≈ 12,5 j.
2. **Une restauration de sauvegarde réellement exécutée** (OPS-02) — la seule étape dont l'échec
   est irrattrapable.
3. **Blocage légal levé** : identité de l'éditeur renseignée (L-01), consentement santé branché
   partout (L-08), retrait complet (L-09), back-office accessible pour honorer un effacement (L-11).
4. **NC-01 vérifié** (30 min) : si l'interface des plans périodisés manque toujours, elle rejoint la
   vague 0 et le délai augmente.
5. **Relecture juridique des CGU et de la politique de confidentialité lancée** (NC-05), et question
   des mineurs tranchée (NC-06).
6. **Ouverture par paliers** : rester en mode invitation et diffuser par vagues de 10 à 15 coachs
   tant que NC-03 n'est pas mesuré.

**Ce qui ferait basculer en NO-GO** : ouvrir sans la condition 2 (une bêta sans restauration prouvée
joue les données de vrais athlètes à pile ou face) ou sans la condition 3 (le service traite des
données de santé — l'exposition n'est pas proportionnée à ce que rapporte une bêta gratuite).

---

*Plan de mise en conformité — DARI Lab Training, août 2026. Synthèse des audits technique et
fonctionnel ; aucun constat nouveau, les zones non instruites sont signalées au §6.*
