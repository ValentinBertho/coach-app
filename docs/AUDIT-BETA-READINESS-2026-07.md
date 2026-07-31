# Audit de préparation à la bêta ouverte — DARI Lab (juillet 2026)

> Question à laquelle répond cet audit : **« Puis-je donner l'application à plusieurs coachs
> qui vont l'utiliser quotidiennement sans risque majeur ? »**
>
> Réponse courte : **presque**. Le socle technique est remarquablement mature pour un produit
> pré-bêta (sécurité applicative, CI, migrations, RGPD athlète). Ce qui manque n'est pas du
> code : c'est de l'**activation opérationnelle** (Sentry, backups, uptime, emails) et du
> **juridique** (politique de confidentialité, mentions légales). Comptez **3 à 5 jours de
> travail ciblé** pour lever les bloquants.
>
> Base de l'audit : lecture du code (`back/`, `front/`), de la configuration
> (`application*.yml`, `.env.example`, `ci.yml`, `vercel.json`, `docker-compose.yml`),
> des scripts (`ops/backup-db.sh`) et de la documentation (`docs/`).

**Légende** : ✅ déjà en place · ⚠️ partiellement fait · ❌ à faire · 💡 recommandation

---

## 1. Infrastructure

| Sujet | État | Détail |
|---|---|---|
| Hébergement | ✅ | Railway (back + PostgreSQL, Docker multi-stage) + Vercel (front). Déployé et fonctionnel (`vercel.json` pointe vers `coach-app-production-5674.up.railway.app`). |
| HTTPS / certificats | ✅ | Gérés automatiquement par Vercel et Railway. `forward-headers-strategy: framework` en place pour les proxys. |
| Domaine / DNS | ❌ | Aucun domaine custom. L'app vit sur `*.vercel.app` / `*.up.railway.app`. `darilab.app` apparaît dans `MAIL_FROM` mais n'est ni acheté/configuré ni vérifié. |
| Variables d'environnement | ✅ | `.env.example` exhaustif et commenté, tableau dans `docs/DEPLOIEMENT.md`, aucune valeur sensible commitée. |
| Gestion des secrets | ✅ | `StartupSecretsValidator` : **refus de démarrer en prod** si `JWT_SECRET` / `FIELD_ENCRYPTION_KEY` sont absents ou aux valeurs de dev. C'est un excellent garde-fou. |
| Config production | ✅ | Profil `prod` dédié (`application-prod.yml`) : seed désactivé, logs INFO, RAZ démo interdite par défaut. |
| Préproduction | ❌ | Aucun environnement de staging. Tout push sur `main` part directement en prod. |
| Monitoring | ⚠️ | Sentry **câblé des deux côtés** (starter Spring + `@sentry/angular-ivy`, no-op sans DSN) mais **DSN non renseignés** → aucune erreur remontée aujourd'hui. Pas d'uptime monitor actif. |
| Logs | ⚠️ | Stdout Railway uniquement. Pas de centralisation ni de rétention garantie. Suffisant pour démarrer une bêta, à surveiller. |
| Gestion des erreurs | ✅ | `GlobalExceptionHandler` centralisé, réponses JSON propres, pas de stack traces exposées. |

💡 **Recommandations**
- Acheter `darilab.app` (ou équivalent) et le brancher sur Vercel **avant** la bêta : l'URL est la première impression de crédibilité, et le domaine est un prérequis pour les emails (DKIM/SPF).
- Renseigner `SENTRY_DSN` (back) + `sentryDsn` (front) : **30 minutes de travail**, tout est déjà branché (pas-à-pas dans `docs/OPERATIONS.md` §3).
- Créer un environnement de préprod léger : un 2ᵉ service Railway + une preview Vercel branchés sur une base séparée. Peut être fait pendant la bêta, mais avant la v1.

---

## 2. Base de données

| Sujet | État | Détail |
|---|---|---|
| Sauvegardes automatiques | ❌ | `ops/backup-db.sh` est bien écrit (pg_dump `-Fc`, rotation, vérification d'intégrité) et documenté, mais **rien ne l'exécute** : pas de cron actif, et les backups managés Railway ne sont pas confirmés activés. **C'est le risque n° 1 de la bêta.** |
| Politique de restauration | ⚠️ | Procédure complète et sérieuse dans `docs/OPERATIONS.md` §2 (restauration complète, sélective, test trimestriel) — mais **jamais exécutée**. Un backup non testé n'existe pas. |
| Migrations | ✅ | Liquibase, 57 changelogs versionnés, `ddl-auto: none`, appliquées au démarrage, validées en CI contre un PostgreSQL réel (smoke test). Très propre. |
| Sécurité / « RLS » | ✅ | Pas de RLS (pas de Supabase) : l'équivalent est applicatif et systématique — `@PreAuthorize` + `ClubAccessValidator` / `AthleteAccessValidator` (anti-IDOR), couvert par des tests dédiés. Données santé et jetons OAuth chiffrés **AES-256-GCM au repos**. |
| Index | ✅ | 36 des 57 migrations créent des index. Volumétrie bêta largement couverte. |
| Performances | ⚠️ | Pièces jointes stockées en `bytea` dans PostgreSQL et pagination absente sur certaines listes (fil de messages…). Acceptable pour 10–30 coachs, à traiter avant de grossir. |
| Données de test | ✅ | Seed déterministe en dev uniquement (`app.seed.enabled=false` en prod), RAZ démo protégée par `DEMO_RESET_ENABLED=false`. |
| Nettoyage | ✅ | Suppressions en cascade (FK `ON DELETE CASCADE`), purge planifiée de la blacklist de tokens. |

**La stratégie de sauvegarde est-elle suffisante pour une bêta ?**
**Non, pas en l'état** — elle est *écrite* mais pas *active*. Pour la bêta il faut au minimum :
1. Activer les **backups automatiques Railway** (ou migrer la base vers un PostgreSQL managé avec PITR).
2. Planifier un `pg_dump` quotidien **externalisé** (bucket S3/R2 chiffré) via le script existant — un simple GitHub Actions scheduled workflow suffit si aucun serveur n'est disponible.
3. Faire **une restauration de bout en bout** une fois, chronométrée, avant d'accueillir le premier coach.

---

## 3. Monitoring

| Outil | Verdict | Détail |
|---|---|---|
| Sentry (back + front) | 🔴 **Indispensable, quasi gratuit à activer** | Code déjà branché, il ne manque que les DSN. Sans lui, vous êtes aveugle sur les erreurs des bêta-testeurs. |
| UptimeRobot / BetterStack (uptime) | 🔴 **Indispensable** | Un ping sur `/api/actuator/health` avec alerte email. 10 minutes de mise en place. |
| Dead man's switch backups (Healthchecks.io) | 🟠 Important | Alerte si le job de backup ne tourne plus. À faire en même temps que le cron de backup. |
| Logs applicatifs centralisés (Better Stack Logs) | 🟢 Peut attendre | Les logs Railway suffisent pour une bêta à petite échelle. |
| Grafana / métriques | 🟢 Peut attendre | Actuator expose déjà `health`/`metrics` ; inutile de monter un stack Grafana avant d'avoir du trafic. |

💡 Configurer dans Sentry une alerte « new issue » vers votre email : pendant une bêta, chaque erreur est un signal produit.

---

## 4. Authentification

| Sujet | État | Détail |
|---|---|---|
| Connexion / inscription | ✅ | JWT stateless, BCrypt, DTO validés. |
| Déconnexion | ✅ | Blacklist du token (jti) jusqu'à expiration — mais **en mémoire** : un redémarrage la vide (acceptable en mono-instance avec TTL 15 min). |
| Sessions / expiration | ✅ | Access token TTL 900 s (prod), **refresh avec rotation** (l'ancien refresh est révoqué à chaque usage). Bon niveau. |
| Mot de passe oublié | ✅ | Flux complet par lien magique, réponse toujours 200 (pas d'énumération de comptes), UI front dédiée (`/forgot-password`, `/reset-password/:token`). |
| Vérification email | ⚠️ | Flux présent (token, `/verify-email/:token`, renvoi) mais **non bloquant** : un compte non vérifié fonctionne normalement. Acceptable pour une bêta sur invitation, à durcir ensuite. |
| Sécurité | ✅ | Rate limiting par bucket IP sur login, register, refresh, password-reset, verify-email et acceptation d'invitation (clé normalisée : les routes à token partagent un compteur). |

💡 **Recommandations**
- Étendre `RateLimitFilter.shouldNotFilter()` à `/public/password-reset` et `/auth/refresh` — c'est un changement de 3 lignes.
- Le rate limiter (fenêtre fixe en mémoire) est suffisant en mono-instance ; noter qu'il devra passer sur Redis en multi-pod.

---

## 5. Emails

| Sujet | État | Détail |
|---|---|---|
| Infrastructure d'envoi | ✅ (code) / ❌ (activation) | Client **Resend** intégré (`ResendMailClient` + `NotificationService`), mais `MAIL_ENABLED=false` : **aucun email ne part aujourd'hui**, les envois sont seulement loggués. |
| Réinitialisation mot de passe | ⚠️ | Le flux backend/front existe, mais sans email actif **le lien n'arrive jamais** → un coach qui oublie son mot de passe est bloqué. C'est bloquant pour une bêta. |
| Confirmation d'inscription | ⚠️ | Même situation : flux prêt, email non envoyé. |
| Invitations athlètes / coachs | ⚠️ | Liens magiques fonctionnels ; en mode dégradé (mail off), le lien est renvoyé dans la réponse API et le coach peut le copier-coller. Utilisable, mais peu crédible en bêta. |
| Notifications importantes | ✅ (code) | Rappel séance J-1 (cron 18 h), digest quotidien d'alertes coach (7 h), routage au coach référent, jamais de donnée de santé dans les emails. Bien conçu. |

**Quelle solution choisir ?** **Rester sur Resend** — c'est déjà intégré, le plan gratuit (3 000
emails/mois, 100/jour) couvre largement une bêta de 10–30 coachs, la délivrabilité est bonne et
la vérification de domaine est simple. Postmark serait l'alternative « délivrabilité maximale »
mais ne justifie pas de réécrire l'intégration. Brevo n'apporte rien ici (orienté marketing).

💡 **Chemin critique (½ journée)** : domaine acheté → vérifié dans Resend (SPF + DKIM) →
`MAIL_ENABLED=true` + `RESEND_API_KEY` + `MAIL_FROM` → tester les 4 emails clés
(invitation athlète, invitation coach, reset mot de passe, rappel J-1).

---

## 6. Intégrations

| Intégration | État actuel | Indispensable avant bêta ? |
|---|---|---|
| **Strava** | ✅ Fonctionnelle : OAuth initié par l'athlète avec `state` signé (HMAC, TTL 10 min, vérifié à la connexion), sync par polling (cron horaire), déduplication, état visible côté coach, désactivation propre si non configurée. Webhook non implémenté (variable prévue). | **Oui** — c'est l'intégration attendue par les athlètes. Le webhook peut attendre, le polling horaire suffit. |
| **Garmin** | ❌ Enum + placeholders d'env seulement. | Non — peut attendre. L'import **GPX/TCX manuel** couvre les utilisateurs Garmin en attendant. |
| **Coros** | ❌ Idem Garmin. | Non — peut attendre. |
| **Polar** | ❌ Rien. | Non — peut attendre. |
| **Suunto** | ❌ Rien. | Non — peut attendre. |
| Import fichier GPX/TCX + saisie manuelle | ✅ | C'est le filet de sécurité universel : communiquez-le clairement aux testeurs non-Strava. |

💡 Annoncer honnêtement dans l'onboarding : « Strava aujourd'hui, Garmin/Coros bientôt, import
fichier pour tous ». Une promesse claire vaut mieux qu'une intégration à moitié faite.

---

## 7. Sécurité

| Sujet | État | Détail |
|---|---|---|
| Permissions / protection des routes | ✅ | Multi-tenant systématique : `@PreAuthorize` + validateurs anti-IDOR sur toutes les routes club, modèle coach↔athlète fin (read/comment/write), testé. C'est le point fort du projet. |
| Validation des données | ✅ | `@Valid` + DTOs Request/Response séparés partout. |
| Injection SQL | ✅ | JPA/paramétré, pas de SQL concaténé détecté. |
| XSS | ✅ | Échappement Angular par défaut + CSP, `frame-options: deny`, `object-src 'none'`. |
| CSRF | ✅ | API stateless en header Bearer → risque CSRF classique faible. Flux OAuth Strava protégé par un `state` signé (HMAC, TTL 10 min, lié à l'athlète). |
| Upload de fichiers | ✅ | Allowlist stricte de content-types (png/jpeg/gif/webp/pdf), limite 10 MB. |
| Rate limiting | ✅ | Login, register, refresh, password-reset, verify-email, invitations — clé IP:bucket normalisée ; en mémoire (OK mono-instance, Redis à prévoir en multi-pod). |
| Erreurs sensibles | ✅ | Handler global, pas de détails internes exposés, `show-details: when_authorized` sur Actuator, `send-default-pii: false` côté Sentry. |
| Jeton en query param | ⚠️ | `access_token` accepté en query string pour les flux SSE (limitation `EventSource`) → le JWT peut fuiter dans les logs des proxys. Connu et documenté ; à remplacer par des jetons courts signés à usage unique. TTL 15 min limite l'exposition. |
| Chiffrement au repos | ✅ | AES-256-GCM (IV aléatoire par valeur) sur données santé + jetons OAuth. Au-dessus du standard pour une bêta. |

💡 Les deux correctifs pré-bêta (`state` OAuth signé, extension du rate limiting) sont faits.
Le reste (jetons SSE courts, Dependabot) peut se faire pendant la bêta.

---

## 8. Performance

| Sujet | État | Détail |
|---|---|---|
| Frontend | ✅ | Angular 17 standalone + signals + OnPush, routes lazy-loadées, PWA (service worker), budgets de build (500 kB warn / 1 MB error), polices auto-hébergées, skeletons. |
| Cache | ✅ | Service worker PWA + cache navigateur ; suffisant à ce stade. |
| Images | ✅ | Peu d'images applicatives ; pièces jointes servies à la demande. |
| Requêtes / N+1 | ⚠️ | Non mesuré en conditions réelles. `open-in-view: false` (bien) ; activer le tracing Sentry (déjà à 10 %) donnera les endpoints lents dès les premiers utilisateurs. |
| Pagination | ⚠️ | Présente sur 8 contrôleurs, absente sur d'autres listes (fil de messages, résultats). Non bloquant à volumétrie bêta. |
| Pièces jointes en base | ⚠️ | `bytea` en PostgreSQL : OK pour la bêta, migration S3/R2 à prévoir avec la croissance (les variables `S3_*` sont déjà prévues). |
| Temps de démarrage / cold start | ✅ | Railway maintient l'instance ; healthcheck configuré. |

💡 Ne rien optimiser à l'aveugle : Sentry Performance + métriques Actuator diront où investir
après 2 semaines de bêta réelle.

---

## 9. Expérience utilisateur

| Sujet | État | Détail |
|---|---|---|
| États vides / loaders / erreurs | ✅ | Deux audits UX complets ont déjà été menés (`audit-produit-dari-lab.md`, `audit-ui-ux-dari-lab.md`) et une grande partie des correctifs est en place : skeletons, toasts, palette Cmd+K, undo calendrier, boîte de réception. |
| Aide intégrée | ✅ | Centre d'aide par rôle (athlète/coach/admin), recherche globale, hints contextuels, export PDF des guides. Rare à ce stade d'un produit. |
| Onboarding | ⚠️ | Pas de parcours guidé « premier jour du coach » (créer athlète → profil physio → première séance → planifier → inviter). La carte « Gérer mes athlètes » du dashboard en tient lieu. |
| Tutoriels | ⚠️ | Le centre d'aide couvre le besoin, mais rien ne pousse le coach vers les 3 premières actions clés. |
| Feedback utilisateur | ❌ | Aucun canal intégré pour qu'un bêta-testeur remonte un bug ou une idée depuis l'app. |
| Page d'erreur applicative | ⚠️ | À vérifier : que voit l'utilisateur si l'API est down (Railway redéploie ~1–2 min à chaque déploiement) ? Prévoir un message clair « maintenance en cours ». |

💡 Pour la bêta, un canal de feedback **simple** suffit : un lien « Signaler un problème »
(mailto ou formulaire Tally/Canny) dans le menu d'aide + les erreurs auto-capturées par Sentry.

---

## 10. Administration

| Sujet | État | Détail |
|---|---|---|
| Dashboard admin | ✅ | `/admin` : stats plateforme, gestion utilisateurs, clubs, athlètes. |
| Gestion des utilisateurs | ✅ | CRUD complet (`AdminUserController`), rattachement aux clubs, suppression de comptes. |
| Impersonation | ❌ | Impossible de « voir ce que voit le coach X ». En bêta, c'est l'outil de support n° 1. |
| Export de données | ⚠️ | Export RGPD athlète oui ; pas d'export global admin (peut attendre). |
| Gestion des erreurs | ⚠️ | Passera par Sentry une fois le DSN actif. |

💡 L'impersonation (lecture seule, auditée) est le meilleur investissement support avant la bêta —
sinon chaque ticket devient un échange d'écrans par email. Peut se faire en 🟠 (semaine 1 de bêta).

---

## 11. RGPD

| Sujet | État | Obligatoire avant bêta ? |
|---|---|---|
| Consentement données de santé | ✅ | `healthDataConsentAt` tracé par athlète — excellent réflexe, les données lactate/douleur sont des données de santé (art. 9 RGPD). |
| Export des données personnelles | ✅ | Portabilité self-service côté athlète (`GET /me/export`). |
| Suppression de compte (athlète) | ✅ | Droit à l'oubli self-service, purge en cascade, loggé. |
| Suppression de compte (coach) | ⚠️ | Un admin peut supprimer un utilisateur, mais pas de self-service coach. Acceptable en bêta (suppression sur demande) si documenté dans la politique de confidentialité. |
| **Politique de confidentialité** | ❌ | **Aucune page trouvée dans le front. Obligatoire avant tout utilisateur réel** — d'autant plus avec des données de santé. |
| **Mentions légales** | ❌ | Absentes. Obligatoires (LCEN) dès que le service est accessible au public, même en bêta. |
| CGU | ❌ | Fortement recommandées en bêta : cadrer « service en test, sans garantie », limiter la responsabilité. |
| Cookies / consentement | ✅ | Pas de cookie tiers ni de tracking marketing (JWT en storage, Sentry = intérêt légitime à mentionner dans la politique). **Pas de bannière cookies nécessaire en l'état** — le rester le plus longtemps possible. |
| Registre des traitements / DPO | ⚠️ | Un registre simple (tableur) suffit à cette échelle ; pas de DPO requis. À faire sans bloquer la bêta. |

💡 **Obligatoire avant la bêta** : politique de confidentialité (couvrant explicitement les données
de santé et les sous-traitants : Railway, Vercel, Resend, Sentry, Strava) + mentions légales +
case de consentement à l'inscription. Deux pages statiques + un lien en pied de page : 1 journée
avec un générateur sérieux relu attentivement.

---

## 12. Déploiement

| Sujet | État | Détail |
|---|---|---|
| CI | ✅ | GitHub Actions : build + 151 tests backend (H2) + **smoke test Liquibase sur PostgreSQL réel** + build AOT front + tests Karma. Très bon niveau. |
| CD | ✅ | Auto-deploy Railway (back) et Vercel (front) sur push. |
| Environnement de dev | ✅ | `docker compose up` reproduit toute la stack, seed de démo déterministe. |
| Préproduction | ❌ | Inexistante (cf. §1) : la CI est le seul filet entre un commit et la prod. |
| Rollback | ⚠️ | Applicatif : redéploiement d'une image précédente possible via Railway/Vercel. **Base de données : aucun plan** — une migration Liquibase appliquée n'est pas réversible sans dump préalable. |
| Migrations automatiques | ✅/⚠️ | Appliquées au démarrage — pratique, mais un échec de migration = app down jusqu'à intervention. Le smoke test CI réduit fortement ce risque. |
| Gestion des versions | ⚠️ | `appVersion: 0.1.0` manuel, pas de tags git ni de releases → difficile de corréler « bug signalé » ↔ « version déployée » (Sentry s'appuie dessus). |

💡 Règle d'or à écrire dans `OPERATIONS.md` et à respecter : **dump avant chaque déploiement
contenant une migration**. Ajouter un tag git par déploiement notable et incrémenter `appVersion`.

---

## 13. Exploitation — « que se passe-t-il si… ? »

| Scénario | Réponse actuelle | Verdict |
|---|---|---|
| **Le serveur tombe ?** | Railway redémarre l'instance (healthcheck configuré). Mais **personne n'est prévenu** (pas d'uptime monitor) et le front n'affiche pas de message dédié. Mono-instance : downtime aussi à chaque déploiement. | ⚠️ Ajouter l'uptime monitor ; accepter le reste en bêta. |
| **La base PostgreSQL est indisponible ?** | L'API tombe (health DOWN). Pas de Supabase ici — le risque équivalent est Railway. Sans backup externe actif, un incident grave côté Railway = **perte de données possible**. | ❌ C'est le scénario qui justifie les backups externalisés immédiats. |
| **Strava ne répond plus ?** | Bien géré : sync par cron isolée, échec loggé sans casser l'app, statut visible, import GPX/TCX en secours. | ✅ |
| **Un utilisateur supprime des données ?** | Suppressions en cascade, **pas de corbeille ni d'undo** (sauf pile d'annulation du calendrier, côté client). Seul recours : restauration de backup… qui n'existe pas encore. | ⚠️ Les backups quotidiens ramènent le risque à « perdre au pire 24 h ». Une confirmation forte existe sur les suppressions critiques ; une corbeille peut attendre. |
| **Une migration échoue ?** | App down au démarrage (Liquibase bloque). Le smoke test CI sur PostgreSQL réel attrape l'essentiel en amont. Sans dump pré-déploiement, retour arrière difficile. | ⚠️ Procédure « dump avant deploy » = réponse suffisante pour la bêta. |

---

## 14. Checklist « Beta Ready »

### 🔴 Indispensable avant la bêta (bloquant)
1. **Backups actifs** : backups Railway activés + `pg_dump` quotidien externalisé (script existant + cron/Action planifiée) + **un test de restauration réussi**.
2. **Emails activés** : domaine acheté et vérifié dans Resend (SPF/DKIM), `MAIL_ENABLED=true`, test des 4 emails clés (reset mot de passe, invitation athlète, invitation coach, rappel J-1). Sans cela, « mot de passe oublié » est une impasse.
3. **Sentry actif** (DSN back + front) + **uptime monitor** sur `/api/actuator/health` avec alerte.
4. **Pages légales** : politique de confidentialité (données de santé + sous-traitants), mentions légales, CGU bêta, consentement à l'inscription.
5. **Domaine custom** branché sur Vercel (prérequis de l'item 2, et crédibilité).
6. ~~**Correctifs sécurité rapides** : rate limiting sur `/public/password-reset` et `/auth/refresh` ; paramètre `state` signé sur l'OAuth Strava.~~ ✅ **Fait** (state HMAC TTL 10 min + rate limiting par bucket sur login/register/refresh/password-reset/verify-email/invitations).

### 🟠 Important — à faire dans les 2 premières semaines
7. Impersonation admin lecture seule (support bêta).
8. Canal de feedback intégré (lien « Signaler un problème » + alertes Sentry).
9. Procédure écrite et appliquée : dump avant chaque déploiement avec migration ; tags de version + `appVersion` incrémenté.
10. Onboarding guidé « premier jour du coach » (checklist 3 étapes sur le dashboard).
11. Dead man's switch sur le job de backup (Healthchecks.io).
12. Page/état front « API indisponible » propre pendant les déploiements.
13. Réparer les liens morts du README (`docs/DEMO.md`, `docs/AUDIT-BETA-2026-06.md`, `docs/ATHLETE-ROADMAP.md` référencés mais absents du dépôt).

### 🟢 Peut attendre les premiers retours
14. Environnement de préproduction complet.
15. Webhook Strava (le polling horaire suffit) ; intégrations Garmin/Coros/Polar/Suunto.
16. Jetons SSE courts signés (remplacement du `access_token` en query).
17. Pièces jointes vers S3/R2 ; généralisation de la pagination.
18. Centralisation des logs ; Grafana ; Testcontainers ; e2e Playwright.
19. Vérification d'email bloquante ; corbeille/soft-delete ; Redis (rate-limit + SSE multi-pod).

---

## 15. Vision « Beta Ready »

**Est-ce que je lancerais une bêta aujourd'hui ?**
Non — mais **dans une semaine, oui**. Aucun bloquant n'est du développement lourd : ce sont des
activations (backups, emails, Sentry, domaine) et deux pages légales. Lancer aujourd'hui
signifierait : des coachs qui ne peuvent pas récupérer leur mot de passe, des erreurs invisibles,
et des données de santé sans politique de confidentialité ni filet de sauvegarde.

**Principaux risques**
1. **Perte de données** : aucun backup actif alors que les coachs saisiraient des données quotidiennement. Risque faible en probabilité, catastrophique en impact.
2. **Impasse « mot de passe oublié »** : emails désactivés → perte d'utilisateurs dès la première semaine.
3. **Juridique** : données de santé (lactate, douleur) traitées sans politique de confidentialité publiée.
4. **Cécité opérationnelle** : sans Sentry ni uptime, le premier signal d'un bug sera un message (ou le silence) d'un testeur.

**Combien de coachs accueillir ?**
Techniquement, l'architecture (mono-instance Railway + PostgreSQL indexé + front statique Vercel)
encaisse sans problème **20 à 30 coachs et 200–300 athlètes**. La vraie limite est
opérationnelle : une seule personne pour le support. Recommandation : **démarrer à 5–8 coachs**
(cohorte 1, sur invitation), élargir à 15–20 après deux semaines si Sentry reste calme.

**Points qui pourraient nuire à la crédibilité**
- URL `*.vercel.app` et emails absents ou non authentifiés (spam) → image « projet de week-end » alors que le produit est au-dessus du marché sur le fond.
- Garmin/Coros affichés comme « prévus » : cadrer la promesse dès l'onboarding.
- Micro-coupures à chaque déploiement en journée → déployer en dehors des heures d'entraînement, ou l'assumer avec un message propre.
- Liens de documentation morts si des testeurs techniques regardent le dépôt.

**Probabilité qu'un premier utilisateur rencontre un problème important**
- En lançant aujourd'hui : **élevée (~70 %)** — quasi certaine sur le parcours email/mot de passe.
- Après la checklist 🔴 : **modérée (~25–30 %)** — des bugs surviendront (c'est le but d'une bêta), mais ils seront détectés (Sentry), récupérables (backups) et sans impasse utilisateur.

### Notes de maturité (/10)

| Domaine | Note | Justification |
|---|---|---|
| **Produit** | 8/10 | Cœur métier profond et différenciant (physiologie, fourchettes, course+force unifiées), deux audits UX déjà absorbés. Manquent l'onboarding et le canal de feedback. |
| **Technique** | 7,5/10 | Architecture propre, 151 tests, moteurs purs testés, CI avec smoke PG. Manquent tests front/e2e et Testcontainers. |
| **Infrastructure** | 5/10 | Déployé et fonctionnel, mais pas de domaine, pas de préprod, monitoring câblé non activé. |
| **Sécurité** | 7,5/10 | Anti-IDOR systématique, chiffrement au repos, garde-fous de démarrage — rare à ce stade. Points ouverts : rate limiting étroit, `state` OAuth, token SSE en query. |
| **UX** | 7/10 | Design system tenu, aide intégrée, états vides/loaders traités. Onboarding et feedback manquants. |
| **Fiabilité** | 4/10 | La note basse de l'audit : aucun backup actif, aucune alerte, aucune restauration testée. C'est aussi la moins chère à remonter (→ 7–8/10 en une semaine). |

---

## Roadmap « Beta Ready » (ordre d'exécution)

| # | Tâche | Effort | Impact |
|---|---|---|---|
| **Semaine 1 — lever les bloquants** | | | |
| 1 | Acheter le domaine, brancher Vercel, mettre à jour `CORS_ORIGINS`/`FRONTEND_URL`/redirect Strava | ½ j | Crédibilité + prérequis emails |
| 2 | Vérifier le domaine dans Resend (SPF/DKIM), activer `MAIL_ENABLED`, tester les 4 emails clés | ½ j | Débloque reset mot de passe + invitations |
| 3 | Activer les backups Railway + cron `pg_dump` externalisé (script existant) + **test de restauration complet** | 1 j | Élimine le risque n° 1 |
| 4 | Renseigner les DSN Sentry (back + front) + uptime monitor + alertes | ½ j | Fin de la cécité opérationnelle |
| 5 | Pages légales (confidentialité, mentions, CGU bêta) + consentement à l'inscription | 1 j | Conformité obligatoire (données de santé) |
| 6 | Rate limiting sur password-reset/refresh + `state` OAuth Strava signé | ½ j | Ferme les deux trous connus |
| **Semaine 2 — confort de bêta (en parallèle des premiers testeurs)** | | | |
| 7 | Impersonation admin lecture seule | 1 j | Support ×10 plus rapide |
| 8 | Lien « Signaler un problème » + procédure dump-avant-deploy + tags de version | ½ j | Boucle de feedback + rollback |
| 9 | Onboarding « premier jour du coach » (checklist 3 étapes) | 1 j | Activation des testeurs |
| 10 | État front « API indisponible » + liens README réparés + dead man's switch backups | ½ j | Finitions fiabilité |
| **Après les premiers retours** | | | |
| 11 | Préprod, webhook Strava, jetons SSE courts, S3 pour pièces jointes, pagination, e2e | — | Route vers la v1 |

**Total avant ouverture : ~4 jours de travail effectif.** Le produit est prêt ; c'est
l'exploitation qui doit le rejoindre.
