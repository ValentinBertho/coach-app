# Claude.md — Guide de collaboration IA ↔ Développeur

> **Blueprint réutilisable** extrait du projet *Mon Petit Atelier* (SaaS B2B de gestion pour garages).
> Ce document est conçu pour être copié à la racine d'un **nouveau projet** afin que l'assistant IA
> soit immédiatement opérationnel et reproduise l'ADN technique, produit et de collaboration.
>
> Les sections marquées _(hypothèse)_ sont des déductions issues de l'observation du code, pas des
> règles écrites explicitement dans le projet d'origine.

---

## 1. Présentation synthétique (template projet)

**[NOM DU PROJET]** est une application **SaaS [B2B / B2C]** qui [problème résolu en une phrase].
Elle centralise [domaines fonctionnels clés] dans une interface unique, moderne et responsive.

Référence ADN (projet source) : application full-stack **Angular 17 (standalone) + Spring Boot 3 / Java 21 + PostgreSQL**,
multi-tenant (scoping par entité parente), orientée métier, mobile-first, RGPD-by-design, déployée
front sur **Vercel** et back en **Docker**.

### Objectifs produit identifiés

| Objectif | Traduction concrète |
|---|---|
| **Remplacer les outils fragmentés** | Une seule app remplace Excel / papier / agenda physique |
| **Self-service client** | Portail client + réservation publique 24/7 (lien magique, sans mot de passe) |
| **Automatiser les communications** | Emails/SMS/push transactionnels déclenchés par les changements d'état métier |
| **Pilotage temps réel** | Dashboard financier et opérationnel pour le gérant |
| **Conformité** | RGPD natif (consentement granulaire, droit à l'oubli, anonymisation, audit log) |
| **Modularité commerciale** | Système de **modules activables** par client (briques fonctionnelles vendables séparément) |

### Rôles utilisateurs (template)

- **Rôle propriétaire/admin métier** : accès complet au back-office d'une entité
- **Rôle opérateur** : vue de travail quotidienne restreinte
- **Rôle client final** : portail sécurisé sans mot de passe (magic link)
- **Rôle admin plateforme** : supervision globale multi-tenants

---

## 2. Principes de développement observés

1. **Lisibilité avant performance** — code explicite > code compact. Pas d'optimisation prématurée.
2. **Pas d'over-engineering** — aucune abstraction pour un usage unique. _« Trois lignes similaires valent mieux qu'une abstraction prématurée »_.
3. **Pas de features fantômes** — implémenter exactement le périmètre demandé, rien de plus.
4. **Sécurité & intégrité par défaut** — toute route est authentifiée sauf exception explicite ; toute requête scopée par le tenant (`garageId`) ; transitions d'état métier validées en service.
5. **Migrations versionnées, jamais de DDL manuel** — le schéma n'évolue **que** via Liquibase.
6. **Conformité légale traitée comme une fonctionnalité** — RGPD, mentions légales, facturation.
7. **Feedback utilisateur systématique** — toute action déclenche un toast (succès/erreur).
8. **Mobile-first** — chaque écran est pensé responsive (≤768px) avant desktop.
9. **Nommage anglais dans le code, libellés français dans l'UI.**
10. **Idempotence des effets de bord** — emails/SMS/notifications dédupliqués (réserve-puis-envoie).

---

## 3. Conventions de code

### Backend (Java / Spring Boot)

| Sujet | Convention |
|---|---|
| Entités | Héritent de `BaseEntity` (`id` UUID, `createdAt`, `updatedAt`). **Ne jamais redéclarer** ces champs. |
| Lombok | `@Data @Builder @NoArgsConstructor @AllArgsConstructor` sur entités/DTOs. `@RequiredArgsConstructor` + `final` pour l'injection dans services/controllers. |
| Injection | Constructeur via `@RequiredArgsConstructor` (jamais `@Autowired` sur champ). |
| DTOs | Séparés : `XxxRequest` (entrée) / `XxxResponse` (sortie). Request annotés `@JsonIgnoreProperties(ignoreUnknown = true)`. |
| Validation | Annotations Jakarta (`@NotBlank`, `@NotNull`, `@Min`, `@Max`, `@Email`, `@Pattern`) sur Request DTOs, activées par `@Valid` dans le controller. |
| Controllers | `@RestController` + `@RequestMapping("/<parent>/{parentId}/<resource>")`, `@Transactional(readOnly = true)` au niveau classe, `@Transactional` (write) sur les méthodes de mutation. |
| Sécurité scoping | `@PreAuthorize("@accessValidator.hasAccess(authentication, #parentId)")` au niveau classe. |
| Modules | `@RequiresModule(MODULE.X)` sur les controllers gated par une brique commerciale. |
| Logs | `@Slf4j`. Jamais de secret (JWT, mot de passe, PII brute) dans les logs. |
| Statuts | Enums Java en `SCREAMING_SNAKE_CASE`. Transitions autorisées validées explicitement en service. |
| Exceptions | Exceptions métier dédiées (`ResourceNotFoundException`, `BadRequestException`, `ConflictException`…) gérées par un `@RestControllerAdvice` global. |
| Chiffrement | Champs sensibles (IBAN, VIN, plaque…) via converter JPA AES-256-GCM ; les recherches sur ces champs se font **en Java après déchiffrement**, jamais en SQL. |

### Frontend (Angular / TypeScript)

| Sujet | Convention |
|---|---|
| Composants | **Tous** `standalone: true`. Dépendances déclarées dans `imports[]`. |
| Injection | Pattern `inject()` : `private service = inject(MyService)` (pas le constructeur pour les nouveaux composants). |
| Routing | `loadComponent` + lazy `import()` pour **chaque** route. Guards fonctionnels (`CanActivateFn`). |
| Formulaires | `ReactiveFormsModule` (complexes) / `FormsModule` (`ngModel` simples). |
| État | **Pas de state manager global** (pas de NgRx). Chargement des données en `ngOnInit()` via les services. |
| Services | `@Injectable({ providedIn: 'root' })`, un service par ressource dans `core/services/`, `HttpClient` + `Observable`, base URL via `environment.apiUrl`. |
| Feedback | `ToastService.success()` / `.error()` sur **toute** action. Jamais `alert()`. `confirm()` natif remplacé par `ConfirmDialogService`. |
| Désabonnement | `takeUntil(destroy$)` ou `async` pipe sur les flux longue durée. |
| Styles | SCSS par composant + design tokens CSS globaux (`var(--…)`). Pas de styles globaux hors `styles.scss`. |
| i18n | `LOCALE_ID = 'fr-FR'`, libellés UI en français, pipes de mapping enum→français. |

### Règles de nommage

| Élément | Convention | Exemple |
|---|---|---|
| Entité JPA | PascalCase descriptif | `WorkshopCatalogItem` |
| DTO entrée / sortie | `XxxRequest` / `XxxResponse` | `QuoteRequest`, `QuoteResponse` |
| Enum Java | SCREAMING_SNAKE_CASE | `QuoteStatus.ACCEPTED` |
| Service Angular | `xxx.service.ts` dans `core/services/` | `quote.service.ts` |
| Composant Angular | `xxx.component.{ts,html,scss}` dans `features/xxx/` | `quote-list.component.ts` |
| Sélecteur composant | `app-xxx` | `app-searchable-select` |
| Migration DB | `NNN-description-courte.yaml` (3 chiffres) | `055-add-sms-vehicle-ready.yaml` |
| Modèle TS | `xxx.model.ts` dans `core/models/` | `quote.model.ts` |

---

## 4. Patterns récurrents

- **Multi-tenant par URL** : toutes les routes métier sont scopées `/<parent>/{parentId}/<resource>` et validées par `@PreAuthorize` côté back, propagées par les services côté front.
- **Liste → Détail → Formulaire** : chaque ressource a un trio `xxx-list` / `xxx-detail` / `xxx-form` + route `:id` et `:id/edit`.
- **Filtres par statut en onglets** (`.status-tabs`) + recherche + pagination serveur (`page`/`size`).
- **Pré-remplissage par queryParams** : `?clientId=…&vehicleId=…` pour enchaîner les écrans (ex. « Nouveau devis » depuis une fiche client).
- **Machine à états métier** : carte de transitions explicite par entité ; mutation refusée si transition interdite ; états terminaux non rétrogradables.
- **Notifications centralisées** : un `NotificationTriggerService` déclenche email + push + SMS selon les changements d'état, gardés par toggles + consentement + idempotence.
- **File d'attente + retry** pour les effets de bord fragiles (emails) avec statuts `PENDING/SENT/FAILED`.
- **Idempotence (dédup)** : table d'historique + contrainte UNIQUE + service « réserve-puis-envoie » (`REQUIRES_NEW`).
- **Génération de documents** : PDF via templates HTML → moteur de rendu, joints aux emails.
- **Composants UI partagés** : sélecteurs (`searchable-select`, `client-selector`, `vehicle-selector`), feedback (`toast`, `confirm-dialog`), placeholders (`skeleton-*`, `empty-state`), structurels (`card`, `modal`, `badge`, `stat-card`).

---

## 5. Bonnes pratiques à respecter

- ✅ Créer **un nouveau fichier de migration** Liquibase par changement de schéma, et l'inclure dans le changelog master.
- ✅ Déclarer `createdAt`/`updatedAt` explicitement dans le changeset si la table les utilise (mais jamais dans l'entité Java).
- ✅ Annoter les Request DTOs avec `@JsonIgnoreProperties(ignoreUnknown = true)`.
- ✅ Scoper **toute** requête par le tenant (`findByIdAndGarageId`), jamais `findById` seul sur une ressource métier.
- ✅ Valider les transitions d'état dans le service avant toute mutation.
- ✅ Émettre un toast pour chaque action utilisateur.
- ✅ Déclarer toutes les dépendances dans `imports[]` des standalone components.
- ✅ Échapper le HTML des champs utilisateur injectés dans les emails.
- ✅ **Incrémenter la version** à chaque session (`front/package.json` semver + `back/pom.xml` `-SNAPSHOT`) et tenir à jour la section « État actuel ».
- ✅ Garder un `.env.example` exhaustif et commenté ([PROD-REQUIS] / [DÉFAUT] / [OPTIONNEL]).

---

## 6. Anti-patterns à éviter

- ❌ `alert()` / `confirm()` natifs pour le feedback (sauf confirmations de suppression, et même là préférer `ConfirmDialogService`).
- ❌ Composants Angular `standalone: false`.
- ❌ Modifier le schéma DB hors Liquibase, ou réutiliser un numéro de migration existant.
- ❌ Redéclarer `id`/`createdAt`/`updatedAt` dans une entité ou un changeset (déjà dans `BaseEntity`).
- ❌ `findById` non scopé sur une ressource tenant → faille IDOR cross-tenant.
- ❌ Requête SQL/JPQL sur un champ chiffré au repos (tourne sur le ciphertext).
- ❌ Ignorer les transitions d'état (mutation directe d'un statut sans vérification).
- ❌ Logguer des secrets ou de la PII brute.
- ❌ Ajouter une dépendance npm/Maven **sans le mentionner explicitement**.
- ❌ Abstractions prématurées / sur-généralisation pour un seul usage.
- ❌ Oublier l'idempotence sur un envoi (risque de doublon email/SMS).

---

## 7. Comment développer une nouvelle fonctionnalité

### Feature avec changement de schéma (full-stack)

1. **Migration** : nouveau `NNN-description.yaml` dans `db/changelog/changes/` + include dans le master.
2. **Entité** : étendre/créer l'entité JPA (hérite de `BaseEntity`).
3. **DTOs** : `XxxRequest` (validé) + `XxxResponse`.
4. **Service** : logique métier, scoping tenant, validation des transitions d'état.
5. **Controller** : route scopée, `@PreAuthorize`, `@RequiresModule` si applicable, `@Valid`.
6. **(Effets de bord)** : brancher sur `NotificationTriggerService` si un changement d'état doit notifier (avec toggle + idempotence).
7. **Front — modèle** : `xxx.model.ts`.
8. **Front — service** : `xxx.service.ts` (`HttpClient`, `environment.apiUrl`).
9. **Front — composants** : `xxx-list` / `xxx-detail` / `xxx-form` standalone, routes lazy + guard.
10. **Front — UX** : toasts, skeletons/empty-state, badges de statut, responsive ≤768px.
11. **Versionner** : bump `package.json` + `pom.xml`, mettre à jour « État actuel ».

### Bug fix

`Cause identifiée → correctif ciblé`. Pas de refactor périphérique non demandé. Vérifier la régression mobile si UI.

---

## 8. Workflow recommandé pour Claude Code

1. **Lire le contexte** : ce fichier + `Design.md` + `Techno.md` + le `CLAUDE.md` racine (section « État actuel »).
2. **Explorer avant d'écrire** : repérer un module similaire existant et **copier ses conventions** (le meilleur guide de style est le code voisin).
3. **Répondre concis** : aller droit au but, code d'abord puis explication courte. Pas de préambule ni de reformulation de la demande.
4. **Signaler sans forcer** : si une demande crée une dette/risque sécurité, le dire et proposer **une** alternative si elle est nettement supérieure.
5. **Implémenter le périmètre exact** demandé, en suivant l'ordre § 7.
6. **Référencer le code** par `chemin/fichier.ts:ligne`.
7. **Vérifier** : build front (`npm run build` = typecheck AOT), build back + smoke test Liquibase (`mvn verify` + démarrage). Ne pas affirmer « fait » sans vérification.
8. **Versionner et documenter** l'évolution.
9. **Git** : développer sur la branche dédiée, commits clairs, push uniquement quand demandé ; pas de PR sans demande explicite.

---

*Template dérivé de Mon Petit Atelier — adapter les noms de domaine (`garage`, `quote`, `repair-order`…) à votre métier.*
