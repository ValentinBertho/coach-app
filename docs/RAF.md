# RAF CoachRun — reste-à-faire pour une application pleinement utilisable

> Audit du **reste-à-faire (RAF)** demandé après les bugs de chargement en démo.
> Date : 2026-06-22. Périmètre : rendre l'appli **stable, démo-able et utilisable au quotidien**.
> Complète `docs/AUDIT.md` (identité, mobile-first, PWA, conformité CDC) sans le dupliquer.

---

## 0. Anomalie bloquante corrigée dans cet audit ✅

**Symptôme** : « énormément de bugs lors du chargement des athlètes, plans, etc. » — écrans
blancs / erreurs au chargement en démo.

**Cause racine** : `ERROR: function lower(bytea) does not exist` (PostgreSQL, SQLState 42883).
Les recherches JPQL utilisaient le motif `:q is null OR lower(col) like %:q%`. Quand `q`
était `null` (cas par défaut, sans filtre de recherche), PostgreSQL **typait le paramètre
lié en `bytea`** → 500 sur la liste des athlètes, des plans, le back-office admin, etc.

> Piège classique : il passait **inaperçu en test** car H2 (même en mode PostgreSQL) tolère
> ce cas. Il ne se déclenchait que sur le vrai PostgreSQL de production.

**Correctif livré** :
- Les services ne lient plus jamais `null` pour `q` ; ils passent `""` (qui matche tout via `%%`).
- La branche `:q is null OR` est retirée de `AthleteRepository.search/searchAdmin` et
  `UserRepository.searchAdmin`.
- Nouveau `SeededDemoSmokeTest` : seed → login coach démo → chargement de **tous** les écrans
  (doit rester 200 partout).
- **Vérifié de bout en bout contre un vrai PostgreSQL 16** : tous les endpoints (athlètes,
  recherche, groupes, modèles, plans, dashboard, sous-ressources athlète, back-office admin)
  renvoient **200, zéro erreur serveur**.

**Dette de méthode associée (P0 ci-dessous)** : les tests tournent sur H2 → ils ne
garantissent pas le comportement PostgreSQL. À industrialiser.

---

## 1. Grille de priorités

| Niveau | Définition |
|---|---|
| **P0** | Bloque une démo fiable ou la mise en prod. À faire avant toute démo commerciale. |
| **P1** | Nécessaire pour un usage quotidien réel par un club. |
| **P2** | Confort / différenciation, non bloquant. |

---

## 2. P0 — fiabilité & démo (avant toute démo)

| # | Sujet | RAF | Effort |
|---|---|---|---|
| P0-1 | **Tests sur vrai PostgreSQL** | Les tests `@SpringBootTest` tournent sur H2 → ils n'attrapent pas les pièges PG (typage `bytea`, fonctions, casts). Brancher **Testcontainers PostgreSQL** sur au moins `SeededDemoSmokeTest` + les tests de recherche, et le faire tourner en CI. C'est la garantie anti-régression du bug ci-dessus. | M |
| P0-2 | **Smoke E2E automatisé** | Rejouer en CI le parcours « seed → login → charge tous les écrans » côté front (les écrans qui plantaient). Une simple suite Playwright headless suffirait. | M |
| P0-3 | **Audit des autres requêtes natives/JPQL** | Vérifier qu'aucune autre requête ne lie un paramètre potentiellement `null` dans une fonction texte (`lower`, `like`, `concat`). Scan effectué pour `lower(concat(...))` : OK. À étendre aux casts de dates/enum lors de l'ajout de filtres. | S |
| P0-4 | **Données de démo réalistes & idempotentes** | Vérifier que `seed` + `RAZ` laissent toujours un jeu cohérent (pas d'orphelins, dates relatives à aujourd'hui pour un calendrier « vivant »). Confirmer que la RAZ est désactivée hors dev. | S |
| P0-5 | **Gestion d'erreur front homogène** | S'assurer qu'un 4xx/5xx affiche un état d'erreur lisible (pas d'écran blanc), avec bouton « réessayer ». L'intercepteur existe ; auditer chaque écran liste/détail pour un *empty/error state* explicite. | M |
| P0-6 | **Santé / observabilité prod** | Exposer `/actuator/health` (+ readiness DB) et brancher un log d'erreur centralisé côté Railway pour voir un 500 **avant** le client. Le bug ci-dessus aurait été visible immédiatement. | S |

---

## 3. P1 — usage quotidien réel

| # | Sujet | RAF | Effort |
|---|---|---|---|
| P1-1 | **Emails transactionnels** | Le `NotificationService` est en mode « mail désactivé » (log only). Brancher un vrai fournisseur (SMTP/Resend/Mailjet) derrière `MAIL_ENABLED` : invitation athlète, séance renseignée, reset mot de passe. | M |
| P1-2 | **Réinitialisation de mot de passe** | Parcours « mot de passe oublié » (demande → email → token → reset). Absent aujourd'hui. | M |
| P1-3 | **Push web en conditions réelles** | VAPID/SwPush en place ; valider le bout-en-bout sur device réel (iOS PWA inclus) et gérer l'expiration/nettoyage des souscriptions mortes. | M |
| P1-4 | **Pagination & recherche côté listes** | Maintenant que la recherche fonctionne, vérifier pagination + tri sur athlètes/admin (perf au-delà de ~200 lignes), et debounce de la recherche. | S |
| P1-5 | **Validation & messages métier** | Harmoniser la validation (front + back) sur les formulaires séance/plan/athlète (bornes FC, VMA, dates de course cohérentes). | M |
| P1-6 | **Permissions fines COACH vs HEAD_COACH** | Vérifier que chaque endpoint sensible (suppression, gestion d'utilisateurs, RAZ) est bien restreint et testé (anti-IDOR déjà en place via `ClubAccessValidator`). | S |
| P1-7 | **Import d'activités réel** | Parsing GPX/TCX présent ; ajouter l'upload depuis l'UI athlète/coach + rapprochement (matching) séance↔activité plus robuste. | M |

---

## 4. P2 — confort & différenciation

| # | Sujet | RAF |
|---|---|---|
| P2-1 | **Strava** | Reconnexion OAuth + sync (volontairement sorti de la phase 1). |
| P2-2 | **Gestes mobiles** | Swipe/appui-long pour déplacer une séance dans le calendrier (drag-drop desktop déjà là). |
| P2-3 | **Tables admin** | Densité desktop OK ; améliorer l'UX mobile (cartes plutôt que scroll horizontal). |
| P2-4 | **Analytics avancées** | Charges d'entraînement (ACWR), zones cumulées, prédiction de perf. |
| P2-5 | **Multi-langue** | i18n (FR seul aujourd'hui). |
| P2-6 | **Export/RGPD self-service** | Brancher l'export et le droit à l'oubli dans l'UI (services back déjà présents). |

---

## 5. État de santé actuel (post-correctif)

- ✅ Backend : compile, **32 tests verts**, démarre sur PostgreSQL 16, seed OK.
- ✅ Tous les écrans coach + back-office admin : **200, zéro 500** sur vrai PostgreSQL.
- ✅ Front : aucun stub/TODO résiduel (les seuls « placeholder » sont des attributs HTML).
- ✅ PWA, identité visuelle, mobile-first : livrés (cf. `docs/AUDIT.md`).
- ⚠️ Garde-fou manquant : tests sur vrai PostgreSQL en CI (P0-1) — **prochaine priorité**.

---

## 6. Reco : ordre d'attaque conseillé

1. **P0-1 + P0-2** (Testcontainers + smoke E2E) → on ne re-livre plus jamais un bug
   « invisible en H2 ».
2. **P0-5 + P0-6** (états d'erreur front + health/observabilité) → une démo ne casse
   plus en silence.
3. **P1-1 + P1-2** (emails + reset mot de passe) → l'appli devient autonome pour un vrai club.
4. Le reste de P1, puis P2 au fil de l'eau.
