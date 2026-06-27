# Audit de préparation à la bêta — DARI Lab Training

> Audit indépendant commandé le **2026-06-27**. Objectif : déterminer si l'application est
> prête pour une **phase bêta** avec de vrais utilisateurs, avec une exigence forte sur
> **l'irréprochabilité du portail athlète en usage mobile**.
>
> Méthode : lecture des documents de cadrage (`Cahier-des-charges.md`, `AUDIT.md`, `RAF.md`,
> `ATHLETE-ROADMAP.md`), revue du code réel (front Angular + back Spring), exécution des builds
> et des tests. Les constats ci-dessous priment sur les docs existantes lorsqu'ils divergent.

---

## 0. Verdict synthétique

**État global : 🟢 quasi-prêt pour une bêta fermée et encadrée.** L'application est nettement
au-dessus du niveau « prototype » : architecture saine, cœur métier physiologique sérieux et testé,
portail athlète mobile abouti, CI verte. Ce n'est PAS une coquille de démo.

**Mais ce n'est pas « plug & play » pour une bêta ouverte.** Il reste une poignée de points à
fermer avant de mettre l'app entre les mains d'athlètes réels sans accompagnement — surtout sur
le **cycle de vie du compte athlète** (mot de passe oublié, multi-séances/jour) et sur la
**configuration de prod** (mail, monitoring). Aucun de ces points n'est un chantier lourd.

| Axe | Note | Commentaire |
|---|---|---|
| Santé technique (build/tests/CI) | 🟢 | Back 151 tests verts, front build propre, CI back+front |
| Adéquation au besoin (CDC) | 🟢 | Phase 1 + 2 livrées ; va même au-delà (force, physio avancée) |
| Portail athlète mobile | 🟢 | Soigné, offline, états gérés ; 2-3 frictions à lisser |
| Robustesse / cas limites | 🟠 | Bonnes fondations, quelques trous fonctionnels |
| Sécurité | 🟠 | Solide pour une bêta ; durcissements connus à planifier |
| Prêt-à-exploiter (ops/prod) | 🟠 | Dépend de la config (mail, Sentry, sauvegardes) |
| Couverture de tests **front** | 🔴 | **2 specs seulement** — angle mort réel |

> **Recommandation : GO pour une bêta fermée (10-30 athlètes, 1-3 coachs) après avoir traité
> les 5 bloquants §3.** NO-GO pour une bêta publique en self-service tant que §3 + §6 ne sont pas
> faits.

---

## 1. Ce qui a été vérifié (preuves)

| Vérification | Résultat |
|---|---|
| `mvnw clean test` (back) | ✅ **151 tests, 0 échec, 0 erreur** (build exit 0) |
| `npm run build` (front, prod AOT) | ✅ Build complet, 24 s, uniquement des warnings de budget CSS cosmétiques |
| CI GitHub Actions | ✅ back `verify` + smoke PostgreSQL réel + front build + **Karma headless** |
| Strava (annoncé « reporté » dans AUDIT.md) | ✅ **Réellement implémenté** (`StravaService`, `StravaClient`, OAuth athlète) — la doc AUDIT.md est en retard sur le code |
| Chiffrement données santé | ✅ AES-256-GCM via converters JPA + `EncryptionService` |
| Garde-fou secrets prod | ✅ `StartupSecretsValidator` refuse de démarrer avec les secrets par défaut |
| Specs front | 🔴 **2 fichiers** (`app.component.spec`, `toast.service.spec`) — voir §6 |

**À noter** : les anciens audits `docs/AUDIT.md` et `docs/RAF.md` sous-estimaient le produit réel
(ils dataient d'avant plusieurs gros lots : Strava, force, physio avancée, portail athlète enrichi).
Ils ont été **supprimés** car obsolètes ; le présent document les remplace, et le `README.md` (remis à
jour) fait foi pour l'état courant.

---

## 2. Adéquation au besoin initial (Cahier des charges)

Le CDC d'origine visait une **alternative simple/mobile-first à Nolio** : prescription coach,
suivi athlète, communication, pilotage de charge. Le produit livré **couvre la Phase 1 (MVP) et la
Phase 2** du CDC, et ajoute un **module force + physiologie avancée** (seuils lactate, VDOT,
domaines d'intensité, 1RM) qui n'était pas au CDC initial mais correspond au pivot « DARI Lab ».

| Besoin CDC (priorité) | État | Détail |
|---|---|---|
| Gestion athlètes + invitation lien (M) | ✅ | CRUD scopé club, lien magique, consentement RGPD art. 9 |
| Calendrier semaine/mois + séances structurées (M) | ✅ | Semaine + mois, drag&drop coach, fourchettes |
| Import Strava + rapprochement prévu/réalisé (M) | ✅ | OAuth athlète + `MatchingService` + import GPX/TCX |
| Séance du jour athlète + RPE (M) | ✅ | Écran « Aujourd'hui », offline-friendly |
| Notifications email (M) | 🟠 | Code prêt (Resend) mais **désactivé par défaut** → à configurer (cf. §3) |
| Charge & graphes (S) | ✅ | `LoadEngine` (ACWR/ATL/CTL), analytics, agrégation serveur |
| Messagerie coach↔athlète (S) | ✅ | SSE temps réel + pièces jointes |
| Courses & objectifs A/B/C (S) | ✅ | CRUD + compte à rebours J-XX |
| Wellness / HRV (C) | ❌ | Non livré (hors périmètre bêta, acceptable) |
| Facturation Stripe (C) | ❌ | Non livré (acceptable pour bêta ; voir §5) |

**Conclusion §2 : l'app dépasse le besoin minimal pour une bêta.** Le risque n'est pas le manque
de fonctionnalités, c'est plutôt l'inverse — beaucoup de surface à stabiliser.

---

## 3. 🚨 Bloquants bêta (à traiter AVANT d'ouvrir aux utilisateurs)

Ce sont les points qui, en l'état, rendraient une bêta **frustrante ou cassée** pour un vrai
utilisateur. Aucun n'est un gros chantier.

### B1 — « Mot de passe oublié » inopérant sans mail configuré 🔴
Le parcours `forgot-password` → email → reset existe **dans le code**, mais `MAIL_ENABLED=false`
par défaut : l'email n'est jamais envoyé (juste loggé). Un athlète qui oublie son mot de passe est
**bloqué dehors**, sans recours self-service.
→ **Action** : activer un vrai fournisseur mail (Resend) en prod **avant** la bêta, OU prévoir un
reset manuel par le coach/admin. Sans mail réel, le compte athlète n'est pas autonome.

### B2 — Une seule séance par jour visible sur « Aujourd'hui » ✅ CORRIGÉ
`today.component.ts` prenait `list[0]` pour la course **et** pour la force : un athlète en **double
séance** ne voyait que la première sur l'écran le plus utilisé.
→ **Livré (2026-06-27)** : l'écran « Aujourd'hui » boucle désormais sur **toutes** les séances de
course (`workouts()`) et **toutes** les séances de force (`strengthCards()`) du jour. Chaque séance est
une carte autonome avec son propre retour (RPE/fatigue/douleur), sa saisie de séries et sa progression.
Le retour offline et les prescriptions en fourchettes sont préservés par séance. Build front vert.

### B3 — Délivrance de l'invitation à clarifier pour le coach 🟠
Bon point : le coach récupère l'URL d'invitation et peut la **copier** (pas dépendant du mail). Mais
rien n'indique à l'athlète **comment se reconnecter** ensuite (l'app athlète s'ouvre sur un lien, pas
sur un écran de login évident depuis la home). Pour une bêta, documenter/afficher clairement :
« voici ton lien » côté coach + un point d'entrée login athlète visible.
→ **Action** : ajouter un CTA login explicite + un court mode d'emploi coach (« copie ce lien et
envoie-le par WhatsApp/SMS »).

### B4 — Couverture de tests front quasi nulle 🔴 (risque de régression)
Seulement 2 specs. Le portail athlète (que tu veux irréprochable) **n'a aucun test**. Chaque
évolution future risque de casser silencieusement la séance du jour / le feedback / l'offline.
→ **Action minimale bêta** : 4-6 specs sur les chemins critiques athlète (chargement séance du jour,
soumission RPE online **et** offline/queue, état d'erreur, calendrier déplacement). Pas besoin d'une
suite exhaustive, juste un filet anti-régression sur le parcours-roi.

### B5 — Observabilité prod à câbler 🟠
`SENTRY_DSN` est optionnel et probablement vide. En bêta, tu **dois** voir les 500 avant l'athlète.
Le bug historique `lower(bytea)` (cf. `RAF.md`) est l'exemple type d'erreur invisible sans
monitoring.
→ **Action** : renseigner `SENTRY_DSN` back + front, vérifier `/actuator/health`, et confirmer que
les **sauvegardes PostgreSQL** Railway sont actives (cf. `docs/OPERATIONS.md`).

---

## 4. Portail athlète mobile — analyse détaillée (le point clé)

C'est la partie que tu veux irréprochable. **Verdict : très bon niveau, parmi les meilleures parties
du produit.** Détail honnête ci-dessous.

### Ce qui est déjà irréprochable ✅
- **États gérés partout** : `loading` (skeletons), `error` (avec bouton Réessayer), `empty`
  (« Repos aujourd'hui ») sur l'écran du jour. Pas d'écran blanc.
- **Offline réel** : RPE saisi hors-ligne → file `localStorage` rejouée au retour réseau
  (`FeedbackQueueService` + `NetworkStatusService`), mise à jour optimiste, bannière hors-ligne.
  C'est rare et bien fait.
- **Ergonomie tactile** : RPE en 1 tap, bottom-sheet pour le retour, `inputmode` correct sur les
  champs numériques (clavier adapté), cibles ≥ 56px, safe-areas iOS gérées.
- **Invariant métier respecté** : l'athlète **lit / déplace / renseigne** mais ne modifie ni ne
  supprime jamais la prescription. Le calendrier athlète utilise un **agenda vertical + bottom-sheet
  de déplacement** (le bon choix mobile, pas du drag&drop fragile au doigt).
- **Prescriptions en fourchettes** lisibles, zones d'intensité colorées + libellées, distinction
  donnée « estimée » (VDOT) vs « mesurée » (lactate) via `DataOriginTag`.
- **Thème sombre immersif** scopé au portail, transitions tokenisées.

### Frictions à lisser (pas bloquantes mais visibles en usage réel) 🟠
1. **Bottom-nav à 6 onglets** (Séance / Agenda / Sorties / Progrès / Messages / Profil). Sur un écran
   de 360px, ça fait ~60px/onglet avec des libellés de 10px : c'est **dense**, à la limite haute des
   recommandations mobiles (5 max idéalement). Envisager de regrouper (ex. « Sorties » + « Progrès »
   sous un onglet, ou « Messages » en cloche d'en-tête plutôt qu'onglet).
2. ~~**Double séance/jour** (cf. B2)~~ ✅ corrigé — l'écran du jour gère désormais plusieurs séances.
3. **Fil de messages non paginé** (chargé en entier) — OK en bêta, à borner avant montée en charge.
4. **Token JWT (+ refresh) en `localStorage`** : exposition en cas de XSS. Acceptable sur appareil
   perso d'athlète en bêta, mais à migrer vers cookie httpOnly à terme (déjà listé en dette).
5. **Cache PWA de réponses authentifiées** : pensé pour appareil personnel. Prévoir une **purge du
   service worker au logout** si un athlète se déconnecte sur un appareil partagé.

### Manque côté athlète qui pourrait gêner ⚠️
- Pas de **rappel/notification** fiable « ta séance du jour » côté athlète tant que le push n'est pas
  validé bout-en-bout sur device réel (iOS PWA inclus). C'est un moteur d'engagement clé pour la
  saisie quotidienne du RPE (KPI du CDC §11). À valider en conditions réelles pendant la bêta.

---

## 5. Manques métier (features qui peuvent rendre l'usage peu pratique)

Au-delà des bloquants techniques, voici ce qui pourrait frustrer un coach ou un athlète en usage
quotidien réel :

| Manque | Impact usage | Priorité bêta |
|---|---|---|
| ~~**Double séance/jour sur « Aujourd'hui »** (B2)~~ ✅ corrigé | Athlète bi-quotidien perd une séance de vue | ~~Haute~~ Fait |
| **Reset mot de passe réel** (B1) | Compte non autonome | Haute |
| **Notification « séance du jour » fiable** | Sans rappel, la saisie RPE/feedback décroche | Moyenne-haute |
| **Vue calendrier par groupe** (coach) | Un club gère par groupes ; planifier 1-à-1 est lent | Moyenne |
| **Wellness quotidien** (sommeil/HRV/poids) | Différenciant, mais absent | Basse (post-bêta) |
| **Facturation coaching** (Stripe) | Pas nécessaire pour valider l'usage en bêta | Basse |
| **FIT (binaire Garmin)** | Seuls GPX/TCX gérés ; beaucoup d'athlètes exportent du FIT | Moyenne (selon population bêta) |

> Aucun de ces manques ne rend l'app **inutilisable**. Les deux premiers (B1, B2) sont les seuls qui
> touchent le quotidien de l'athlète et méritent d'être faits avant la bêta.

---

## 6. Sécurité, RGPD & prod-readiness

**Solide pour une bêta**, avec une dette connue et documentée (transparence saine) :
- ✅ Chiffrement santé au repos (AES-256-GCM), garde-fou secrets, CSP/headers, CORS allowlist,
  rate-limiting, anti-IDOR systématique (`@clubAccessValidator`), révocation JWT au logout + rotation
  refresh.
- ✅ RGPD opérationnel : consentement art. 9 à l'onboarding, export `/me/export`, suppression `DELETE /me`.
- 🟠 À planifier (non bloquant bêta fermée, requis avant ouverture large) :
  - JWT en `localStorage` → cookie httpOnly.
  - Jeton en **query param** pour SSE / téléchargement pièces jointes → jetons courts signés.
  - `state` OAuth Strava à signer (nonce anti-CSRF).
  - SSE mono-instance (émetteurs en mémoire) → Redis pub/sub si scale-out ; `@Scheduled` sans
    ShedLock → non sûr en multi-pod.
  - Pagination à généraliser (messages, listes non bornées).
  - Pièces jointes en `bytea` → stockage objet (S3) à plus grande échelle.

**Avant de mettre en prod** (checklist) : `JWT_SECRET` + `FIELD_ENCRYPTION_KEY` réels, `MAIL_ENABLED=true`
+ Resend, `SENTRY_DSN` back+front, `CORS_ORIGINS`/`FRONTEND_URL` corrects, `DEMO_RESET_ENABLED=false`,
sauvegardes DB vérifiées, clés VAPID si push.

---

## 7. Plan d'action priorisé

### P0 — Avant d'ouvrir la bêta (≈ 2-4 jours)
1. **B1** : activer Resend en prod + tester invitation/reset bout-en-bout (ou reset manuel admin).
2. ~~**B2** : afficher toutes les séances course/force du jour sur « Aujourd'hui ».~~ ✅ **Livré.**
3. **B5** : câbler Sentry (back+front), vérifier health + sauvegardes DB Railway.
4. **B4** : ajouter 4-6 specs front sur le parcours athlète critique (séance du jour, RPE online/offline, erreur).
5. **B3** : CTA login athlète visible + mini guide coach pour partager le lien.

### P1 — Pendant la bêta (selon retours)
6. Valider le **push « séance du jour »** sur device réel (iOS PWA inclus) + nettoyage souscriptions mortes.
7. Réduire la **bottom-nav à 5 onglets**.
8. Paginer le fil de messages.
9. Vue calendrier **par groupe** (coach).

### P2 — Post-bêta / durcissement
10. JWT cookie httpOnly, jetons SSE/pièces jointes signés, `state` Strava signé.
11. Testcontainers PostgreSQL en CI (fermer le risque H2↔PG).
12. Suite de tests front élargie + smoke E2E Playwright.
13. Wellness, FIT, facturation Stripe selon roadmap produit.

---

## 8. Conclusion

L'application est **plus mature que ce que les vieux audits laissent croire**, et la partie athlète
mobile — ta priorité — est **la mieux finie du produit** : offline, états gérés, ergonomie tactile,
invariants métier respectés. Il n'y a **pas de chantier structurel** à mener pour démarrer.

Ce qui sépare l'état actuel d'une bêta sereine tient en **5 points concrets et courts** (§3), dont
seulement deux touchent vraiment le quotidien de l'athlète (mot de passe oublié, double séance/jour).
Le vrai angle mort est la **couverture de tests front** : à combler au moins sur le parcours-roi pour
ne pas régresser pendant la bêta.

**Recommandation finale : GO pour une bêta fermée et accompagnée (cohorte réduite) après le lot P0.**
Repousser la bêta self-service/publique jusqu'à ce que P0 + le durcissement sécurité P2 prioritaire
(jetons, cookie httpOnly) soient traités.
