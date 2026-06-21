# Claude.md — Guide de collaboration IA ↔ Développeur (Plateforme de coaching course à pied)

> **Blueprint d'un SaaS de coaching sportif** (alternative à Nolio / TrainingPeaks), destiné aux
> **coachs indépendants et aux clubs** de course à pied (extensible trail / triathlon / cyclisme).
> Conçu pour démarrer un nouveau projet avec un contexte IA immédiatement opérationnel.
>
> ADN technique repris d'un SaaS B2B éprouvé : **Angular 17 (standalone) + Spring Boot 3 / Java 21 +
> PostgreSQL + Liquibase**, multi-tenant, mobile-first, RGPD-by-design.
> Sections _(hypothèse)_ = recommandations de conception, à valider avec le métier.

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
- **Séance structurée** : éditeur d'étapes (`WorkoutStep`) répétables, avec cibles par zone (FC/allure/puissance). Bibliothèque de `WorkoutTemplate` applicable en un clic (comme les « templates OR » du socle).
- **Machine à états** : `Workout` (PLANNED→COMPLETED/PARTIAL/MISSED), `TrainingPlan` (DRAFT→ACTIVE→…). Mutation refusée si transition interdite.
- **Notifications centralisées** : `NotificationTriggerService` (push + email) sur séance commentée, plan publié, objectif J-7, alerte surcharge — gardé par toggles + consentement + idempotence.
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
✅ Incrémenter la version à chaque session (`package.json` + `pom.xml`) et tenir à jour « État actuel ».

❌ `alert()` / `confirm()` natifs (sauf suppression, et encore : `ConfirmDialogService`).
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

1. **Migration** : `NNN-description.yaml` + include master.
2. **Entité** : étend/crée (hérite de `BaseEntity`).
3. **DTOs** : `XxxRequest` (validé) + `XxxResponse`.
4. **Service** : logique métier, scoping tenant, transitions d'état, effets de bord (notifications/sync).
5. **Controller** : route scopée, `@PreAuthorize`, `@RequiresModule`, `@Valid`.
6. **Front** : `xxx.model.ts` → `xxx.service.ts` → composants `list/detail/form` standalone, routes lazy + guard.
7. **UX** : toasts, skeletons/empty-state, badges de statut, responsive ≤768px.
8. **Versionner** + mettre à jour « État actuel ».

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
5. **Implémenter le périmètre exact**, dans l'ordre du § 8.
6. **Référencer le code** par `chemin/fichier.ts:ligne`.
7. **Vérifier** : `npm run build` (typecheck AOT) + `mvn verify` (+ smoke test démarrage/Liquibase). Ne pas dire « fait » sans vérif.
8. **Git** : branche dédiée, commits clairs, push/PR uniquement sur demande.

---

*Blueprint coaching course à pied — générique et réutilisable par d'autres coachs/clubs. Adapter le nom produit
et activer/désactiver les modules selon l'offre (coach solo vs club, avec ou sans facturation/intégrations).*
