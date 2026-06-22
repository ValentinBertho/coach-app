# Audit CoachRun — identité, mobile-first, PWA, conformité CDC & roadmap

> Rapport produit après refonte identité visuelle + mobile-first + PWA. Compare le code à
> `Cahier-des-charges.md`, liste la dette technique et propose la roadmap Phase 2.

---

## 1. Identité visuelle (livrée)

| Élément | Décision | Justification |
|---|---|---|
| **Logo** | Mark « squircle » dégradé électrique (#3D5AFE→#2D45D6) + *stride* blanc ascendant + point d'arrivée vert (--accent) | Reprend l'ADN « track & data » de `Design.md` : énergie maîtrisée, un accent vif sur base sobre. Décliné en `LogoComponent` (mark + wordmark) réutilisé partout. |
| **Favicons** | `favicon.svg` (vectoriel, navigateurs modernes) + PNG 16/32 + `.ico` de repli | SVG net à toute taille ; PNG pour compatibilité. |
| **Icônes PWA** | 72→512 px **maskable** générées depuis `branding/icon-master.svg` via `sharp` (`branding/generate-icons.mjs`) | Source unique versionnée → régénérable ; full-bleed pour respecter la *safe zone* maskable (Android/iOS). |
| **Apple touch icon** | 180 px dédié + métas iOS (`apple-mobile-web-app-*`) | Rendu propre à l'ajout à l'écran d'accueil iOS. |
| **Palette / design system** | Palette « Pace » conservée et **centralisée en tokens** (`styles.scss`) + couche utilitaire globale (page-header, search-bar, status-tabs, stats-grid, avatar, data-table, zones Z1–Z5…) | Rebrandable par club via `--primary`/`--accent` ; cohérence garantie sur tous les écrans. |

---

## 2. Mobile-first (audité & corrigé)

| Sujet | État après audit |
|---|---|
| **Navigation mobile** | ✅ **Bottom-nav tactile** ajoutée à l'espace coach (Accueil/Athlètes/Calendrier), cibles ≥56px ; nav desktop masquée < 768px. App athlète déjà mono-écran mobile. |
| **Safe areas iOS** | ✅ `env(safe-area-inset-*)` sur top bars et bottom-nav ; `viewport-fit=cover`. |
| **Ergonomie tactile** | ✅ Boutons pilule 48px, inputs 16px (anti-zoom iOS), `:active scale`, RPE en 1 tap. |
| **Responsive grilles** | ✅ Calendrier semaine → 1 colonne, formulaires `form-row(-3)` → 1 colonne, tables admin en scroll horizontal < 768px. |
| **Expérience native** | ✅ Installable (voir PWA), surfaces *night* côté athlète, transitions tokenisées ≤280ms, feedback toasts. |
| **Reste à faire** | Tables admin denses (UX desktop) ; pas de gestes (swipe) ni d'appui-long pour replanifier (cf. dette). |

---

## 3. PWA (transformée)

| Critère | Implémentation |
|---|---|
| **Installation** mobile + desktop | `PwaInstallService` (capture `beforeinstallprompt`) + `app-install-button` (landing, app athlète). Manifest complet (name, short_name, theme/background, display standalone, icônes maskable). |
| **Mode offline** | Service worker Angular + `ngsw-config` : app shell en *prefetch*, polices en lazy, **`dataGroups` `freshness`** sur `/api/me|clubs|admin|public` → dernières données consultables hors ligne ; fallback SPA `index.html`. |
| **Cache intelligent** | `freshness` (réseau d'abord, cache si timeout 5s / hors ligne), `maxAge 1h`, `maxSize 150`. App shell `prefetch`/`updateMode prefetch`. |
| **Synchro retour réseau** | `NetworkStatusService` (`online` signal + `reconnected$`) + **`FeedbackQueueService`** : le RPE saisi hors ligne est mis en file (localStorage) et **rejoué automatiquement au retour du réseau**. Bannière hors ligne globale. |
| **Score Lighthouse** | Critères PWA réunis structurellement (manifest valide, SW, icônes maskable 192/512, offline, theme-color, viewport, description). ⚠️ **Non mesurable dans cet environnement** (pas de navigateur headless ici) — à exécuter sur le déploiement HTTPS (Vercel). HSTS/headers actifs côté API. |

> Note : le cache `dataGroups` couvre des réponses authentifiées — acceptable sur un appareil
> personnel (cas d'usage athlète). À réévaluer si appareils partagés (purge SW au logout = piste).

---

## 4. Conformité au Cahier des charges (Phase 1 — MVP)

| Fonctionnalité CDC | Priorité | État | Détail |
|---|---|---|---|
| Gestion athlètes (CRUD, profil physiologique) | M | ✅ | CRUD scopé club, **données de santé chiffrées AES-256-GCM**. |
| Invitation par lien (onboarding sans mot de passe) | M | ✅ | Lien magique + page publique + création compte ATHLETE. |
| Calendrier semaine/mois | M | 🟠 | Vue **semaine** ✅ ; vue **mois** et **par groupe** ❌. |
| **Glisser-déposer** pour replanifier | M | ❌ | Création/édition via formulaire seulement (dette). |
| Séance structurée (étapes, zones, statuts) | M | ✅ | Éditeur FormArray, zones Z1–Z5, machine à états validée. |
| Connexion Strava (OAuth, import auto) | M | ❌ | **Hors périmètre à la demande** (reporté). Moteur de rapprochement prêt. |
| Rapprochement prévu ↔ réalisé | M | ✅ | `MatchingService` testé (score date+distance, COMPLETED/PARTIAL) + import **manuel**. |
| Ressenti athlète (RPE) + commentaire | M | ✅ | Vue « séance du jour », RPE 1–10, offline-friendly. |
| Notifications email | M | ✅ | Resend + triggers (séance, feedback, rappel J-1), désactivé par défaut. |
| Paramétrage club / modules activables | M(club) | 🟠 | Club implicite + back office admin ✅ ; **modules activables** (`@RequiresModule`) ❌. |
| **RGPD** : consentement art. 9, export, droit à l'oubli | NFR M | 🟠 | Chiffrement ✅ ; **consentement explicite, export, suppression de compte** ❌. |

**Au-delà du MVP, déjà livré** : authentification coach + club + rôles, **back office admin complet** (CRUD clubs/users/athlètes/invitations + stats), **seed de démo riche + RAZ**, **identité + PWA + mobile-first**.

### Ce qui manque pour clôturer la Phase 1
1. **Strava OAuth + webhooks** (seule vraie feature « M » manquante — volontairement reportée).
2. **Glisser-déposer** du calendrier (M) — sinon appui-long mobile.
3. **RGPD opérationnel** : écran de consentement (collecte physio + appareils), **export des données** athlète, **suppression de compte** (purge activités + fichiers).
4. **Vue mois / par groupe** + entité `TrainingGroup`.

---

## 5. Dette technique à traiter

| Domaine | Dette | Priorité |
|---|---|---|
| **Auth** | Refresh token sans rotation ni blacklist au logout ; pas de cookie httpOnly. | Haute |
| **Sécurité** | **Rate limiting** absent (auth/import) ; Sentry non câblé ; HSTS effectif seulement derrière HTTPS. | Haute |
| **Tests front** | Seul `app.component.spec` existe ; pas de tests services/composants ; Karma non exécuté en CI. | Haute |
| **Pagination UI** | Les listes (athlètes, admin) ne chargent que la page 0 — pas de contrôles de pagination. | Moyenne |
| **Recherche** | Déclenchée sur `Entrée` uniquement (pas de debounce). | Basse |
| **Mapping** | `MapStruct` mentionné dans les conventions mais mapping manuel (`Dto.from`) — choix assumé, à documenter. | Basse |
| **Scheduler** | `@Scheduled` sans **ShedLock** → non sûr en multi-instance. | Moyenne (si scale-out) |
| **API versioning** | Pas de `/api/v1` (recommandé Techno.md §6). | Moyenne |
| **Portail athlète** | Limité à « Aujourd'hui » ; onglets Calendrier/Activités/Messages/Profil à construire. | Moyenne |
| **Observabilité** | Pas de `correlationId` exposé au front ; logs OK. | Basse |
| **Build** | `sharp` en devDependency (lourd) seulement pour générer les icônes → alourdit `npm ci` CI. | Basse |
| **Cache PWA** | `dataGroups` cache des réponses authentifiées (appareil partagé). | Basse/Moyenne |

---

## 6. Roadmap priorisée — Phase 2 (et clôture Phase 1)

> Méthode : d'abord **finir la Phase 1** proprement (P0), puis dérouler la Phase 2 du CDC §9
> (bibliothèque & plans · charge & graphes · messagerie · push · objectifs).

### P0 — Clôture Phase 1 (avant d'ouvrir la Phase 2)
1. **Strava** : OAuth2 (tokens chiffrés), webhooks signés, import asynchrone + backoff, dédup déjà en place.
2. **Glisser-déposer calendrier** (`@angular/cdk/drag-drop`) + appui-long mobile, confirmation avant/après.
3. **RGPD** : consentement explicite séparé (physio + appareils), export JSON des données athlète, suppression de compte (cascade + révocation OAuth).
4. **Qualité** : tests front (services/guards/composants clés) + job Karma headless en CI ; pagination UI + recherche debounced.

### P1 — Cœur Phase 2
5. **Bibliothèque de séances** (`WorkoutTemplate`) applicable en 1 clic + partage club.
6. **Plans périodisés** (`TrainingPlan`/`TrainingBlock`, méso/microcycles, calage sur date de course).
7. **Charge & graphes** : service `LoadService` (CTL/ATL/forme, formules documentées et testées) + volume hebdo / répartition zones ; **trancher la lib de graphes** (reco : `ngx-charts`) — décision structurante.
8. **Messagerie** coach↔athlète + commentaires de séance.

### P2 — Étendre
9. **Push WebPush** (VAPID) sur les triggers existants.
10. **Courses & objectifs** (`RaceObjective`, compte à rebours J-XX, bilan post-course).
11. **TrainingGroup** + calendrier/vue par groupe + vue mois.
12. **Détail d'activité** : parsing FIT/GPX/TCX, temps par zone, tracé carte (Leaflet), stockage brut S3 + résumé DB.

### P3 — Ultérieur (Phase 3 CDC)
13. Wellness (sommeil/HRV/poids) + corrélation charge.
14. Facturation Stripe (forfaits coaching privé).
15. Garmin/Coros, multi-coach club avancé, modules activables (`@RequiresModule`).

### Décisions techniques à acter tôt (Techno.md §6)
- **Lib graphes** (ngx-charts vs Chart.js vs D3) → reco **ngx-charts**.
- **Temps réel** messagerie/notifs : SSE/WebSocket vs push+polling.
- **`/api/v1`** dès l'ouverture Phase 2 (éviter un breaking change plus tard).
- **Signals + OnPush** sur les vues data-lourdes (calendrier/graphes) — déjà OnPush partout.

---

*Synthèse : la Phase 1 est fonctionnellement quasi complète (il manque Strava — reporté —,
le drag&drop et le volet RGPD opérationnel). Les fondations (sécurité tenant, chiffrement santé,
PWA, design system, back office, seed/RAZ) sont solides pour enchaîner la Phase 2.*

---

## 7. Journal d'avancement (itération design + P0/P1/P2)

### Design — refonte « Pace v2 » (livré)
Palette **duotone indigo→violet**, dégradés **mesh/aurora**, **glassmorphism**, ombres colorées,
boutons/cards/inputs premium, micro-interactions. Landing hero aurora + *gradient text*, pages
auth fond mesh, logo de marque partout. Tokens/classes inchangés → zéro régression.

### P0 — clôture Phase 1
| Item | État | Note |
|---|---|---|
| Glisser-déposer calendrier | ✅ | `@angular/cdk`, `PATCH /reschedule`, optimiste + rollback. |
| RGPD (consentement, export, oubli) | ✅ | Consentement art. 9 à l'onboarding, `/me/export`, `DELETE /me`. |
| Pagination UI + recherche debouncée | ✅ | `PaginatorComponent` + debounce 300ms (athlètes, admin). |
| Strava OAuth | ⏭️ | **Reporté** à la demande. |
| Tests front + Karma CI | ⚠️ | Non livré : Karma nécessite un Chrome headless **non disponible dans l'environnement de build actuel** → ajout différé pour ne pas livrer une CI invérifiable. Le backend reste couvert (25 tests). |

### P1 — Phase 2 (cœur) — partiel
| Item | État |
|---|---|
| Graphes de charge (volume prévu/réalisé, zones, adhérence) | ✅ (agrégation serveur + SVG) |
| Bibliothèque de séances (`WorkoutTemplate`) | ⏳ à faire (nécessite extraire l'éditeur d'étapes en composant partagé) |
| Plans périodisés | ⏳ à faire |
| Messagerie | ⏳ à faire |

### P2 — partiel
| Item | État |
|---|---|
| Courses/objectifs + compte à rebours | ✅ (CRUD coach + carte J-XX athlète) |
| Push WebPush | ⏳ à faire |
| TrainingGroup + vue par groupe/mois | ⏳ à faire |
| Détail activité FIT/GPX + carte | ⏳ à faire |

### Reste à faire (prioritaire)
1. **Tests front + Karma** dans une CI dotée de Chrome (`browser-actions/setup-chrome`).
2. **Bibliothèque de séances** puis **plans périodisés** (réutiliser l'éditeur d'étapes).
3. **Messagerie** coach↔athlète (+ commentaires de séance) ; **push WebPush**.
4. **TrainingGroup** (vue mois/groupe) ; **parsing FIT/GPX** + carte.
5. **Strava OAuth** (quand souhaité) — moteur de rapprochement déjà prêt.

*Cette itération a priorisé l'impact démo (design « wahou », charge/graphes, objectifs, RGPD,
drag&drop) avec build front vert et 25 tests back. Les briques P1/P2 restantes sont volumineuses
et listées ci-dessus pour la suite.*
