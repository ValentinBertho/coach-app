# Registre des activités de traitement — DARI Lab

> **RGPD art. 30.** Ce registre est un **état des lieux du code**, pas un modèle rempli. Chaque
> ligne est vérifiable : les colonnes « Où » renvoient à une table, une classe ou un fichier de
> configuration. Ce qui n'est pas implémenté est écrit comme tel.
>
> **Établi le** : 7 septembre 2026 · **À partir de** : `back/src/main/java/com/coachrun/**`,
> `back/src/main/resources/application.yml`, `back/src/main/resources/db/changelog/**`,
> `.github/workflows/**`, `front/src/app/features/public/legal.component.ts`.
>
> **Méthode** : lecture des entités JPA (ce qui est stocké), des services et planificateurs (ce
> qui est fait, et pendant combien de temps), des intégrations sortantes (à qui les données sont
> transmises), et comparaison ligne à ligne avec la politique de confidentialité publiée. Le §7
> ne liste que des écarts constatés dans le code, pas des risques théoriques.

---

## 1. Responsable de traitement

| | |
|---|---|
| Nom du service | DARI Lab (`darilab.app`) |
| Responsable de traitement | `LEGAL_OWNER.name` = « DARI Lab » |
| Identité civile / raison sociale | **`LEGAL_OWNER.legalName` est vide** — cf. §7, écart n° 1 |
| Adresse postale | **`LEGAL_OWNER.address` est vide** — cf. §7, écart n° 1 |
| Contact | `contact@darilab.app` |
| Délégué à la protection des données | Non désigné (non obligatoire ici : pas d'organisme public, pas de suivi à grande échelle au sens de l'art. 37-1-b **à ce stade de la bêta**) |
| Où | `front/src/app/features/public/legal.component.ts`, `LEGAL_OWNER` |

**Un point de qualification à trancher avant l'ouverture.** Le coach saisit et consulte les
données de ses athlètes ; la plateforme fixe seule les finalités et les moyens (modèle de données,
durées, moteurs de calcul). Le registre ci-dessous est donc écrit avec **l'éditeur comme unique
responsable de traitement** et le coach comme utilisateur autorisé. Une lecture en
**responsabilité conjointe** (art. 26) est défendable dès lors qu'un coach professionnel utilise
le Service pour sa propre clientèle ; elle imposerait un accord de responsabilité conjointe. Le
code ne tranche pas cette question — les CGU non plus (`legal.component.ts` §4 décrit le rôle du
coach sans le qualifier).

---

## 2. Traitements

Un traitement par finalité. Les durées indiquées sont celles **que le code applique**, pas celles
qu'on souhaiterait appliquer.

### T-01 — Gestion des comptes et de l'authentification

| | |
|---|---|
| **Finalité** | Créer et tenir un compte, authentifier, cloisonner l'accès par club |
| **Base légale** | Exécution du contrat (art. 6-1-b) |
| **Personnes** | Coachs, athlètes, administrateurs de plateforme |
| **Données** | Nom complet, adresse e-mail, mot de passe **haché BCrypt**, rôle, club(s), statut, préférence d'unité d'allure, fuseau horaire, dates de dernière connexion et de dernière activité, dates de péremption des jetons |
| **Où** | Table `users` — `entity/User.java` |
| **Durée** | Vie du compte, puis **24 mois d'inactivité** → préavis par e-mail à J-30, puis suppression (`InactiveAccountPurgeScheduler`, `app.accounts.inactivity.*`) |
| **Destinataires** | Railway (hébergement) |
| **Mesures** | Mot de passe BCrypt · JWT stateless d'une heure, en en-tête uniquement · jetons de flux à usage unique pour SSE et pièces jointes (`StreamTokenService`) · liste noire des jetons révoqués · plafonnement des tentatives de connexion (`LoginAttemptTracker`) et des requêtes (`RateLimitFilter`) |

### T-02 — Prescription et suivi de l'entraînement

| | |
|---|---|
| **Finalité** | Construire un programme, prescrire des séances, comparer le réalisé au prescrit |
| **Base légale** | Exécution du contrat (art. 6-1-b) |
| **Personnes** | Athlètes |
| **Données** | Identité de l'athlète (prénom, nom, e-mail, date de naissance, sexe, niveau) · séances prescrites et leurs blocs · plans, mésocycles, cycles de force · objectifs de course et résultats · performances de référence · zones d'entraînement et allures VDOT · notes de calendrier · indisponibilités (dates) |
| **Où** | `athletes`, `workouts`, `workout_steps`, `training_plans`, `plan_assignments`, `race_objectives`, `athlete_performances`, `training_zones`, `athlete_zone_values`, `athlete_vdot_paces`, `calendar_notes`, `strength_*` |
| **Durée** | Vie du compte de l'athlète — suppression en cascade (`athletes` → tout le reste, FK `ON DELETE CASCADE`) |
| **Destinataires** | Railway · le ou les coachs du club, dans la limite de leurs permissions (`AthleteCoachPermission`, `@athleteAccessValidator`) |
| **Mesures** | Cloisonnement par club et par athlète sur **chaque** route (`@clubAccessValidator`, `@athleteAccessValidator`) |

### T-03 — Données de santé (article 9)

| | |
|---|---|
| **Finalité** | Adapter la charge d'entraînement à l'état de l'athlète |
| **Base légale** | **Consentement explicite** (art. 9-2-a), révocable à tout moment |
| **Personnes** | Athlètes |
| **Données** | Mesures de lactate (`lactate_tests`, `lactate_test_steps`) · niveaux de **douleur** et de **fatigue** (retours de séance, check-in quotidien, séances de force, séries de force, activités) · **motif médical** d'indisponibilité (blessure, maladie) et ses notes · **notes médicales** du profil · seuils physiologiques (LT1, LT2, VC, FC seuils), FC max, FC repos, VMA, poids |
| **Où** | `athletes` (colonnes chiffrées), `lactate_tests`, `lactate_test_steps`, `daily_check_ins`, `workouts`, `activities`, `scheduled_strength_sessions`, `strength_results`, `athlete_unavailabilities` |
| **Recueil du consentement** | À l'acceptation de l'invitation — `athletes.health_data_consent_at` |
| **Retrait** | Depuis le profil athlète — `athletes.health_data_consent_withdrawn_at`. Le retrait **efface** les données déjà collectées : `GdprService.withdrawHealthConsent` supprime les tests de lactate et remet à `null` douleur/fatigue des check-ins, séances, séances et séries de force, ainsi que les motifs médicaux d'indisponibilité et les notes médicales |
| **Garde en écriture** | `HealthDataConsentValidator.requireConsent` — sans consentement actif, la collecte est **refusée** (403), y compris pour un athlète qui n'a jamais accepté son invitation |
| **Durée** | Vie du compte, ou jusqu'au retrait du consentement |
| **Destinataires** | Railway · le ou les coachs autorisés · GitHub (sauvegardes chiffrées, cf. T-09) |
| **Mesures** | **Chiffrement au repos AES-256-GCM**, IV aléatoire par valeur (`EncryptionService`, converters JPA) sur les colonnes physiologiques et les notes médicales |

> ⚠️ **Le chiffrement au repos ne couvre pas tout ce que l'article 9 couvre ici.** Les colonnes
> `pain` et `fatigue` de `workouts`, `activities`, `daily_check_ins`, `scheduled_strength_sessions`
> et `strength_results` sont stockées **en clair** (entiers), tout comme le motif
> d'indisponibilité. Ce sont pourtant des données de santé au sens de la politique publiée, qui
> annonce sans distinction qu'elles sont « chiffrées au repos ». Cf. §7, écart n° 2.

### T-04 — Import d'activités depuis un appareil connecté (Strava)

| | |
|---|---|
| **Finalité** | Importer les activités enregistrées par la montre de l'athlète |
| **Base légale** | Consentement (art. 6-1-a) — la connexion est volontaire, athlète par athlète |
| **Personnes** | Athlètes ayant connecté leur compte Strava |
| **Données** | Jetons OAuth Strava (accès et rafraîchissement, **chiffrés au repos**), identifiant Strava, portée accordée · activités importées : date, titre, distance, durée, dénivelé, FC moyenne et max, cadence, puissance, calories · **tracé GPS** (`route_json`), flux seconde par seconde (`stream_json`), tours (`laps_json`) |
| **Où** | `device_connections` — `entity/DeviceConnection.java` · `activities` — `entity/Activity.java` |
| **Portée demandée** | `activity:read_all` et `activity:write` — cf. §7, écart n° 3 |
| **Durée** | Jusqu'à déconnexion Strava ou suppression du compte |
| **Destinataires** | Strava (États-Unis) — sortant : rafraîchissement de jeton, lecture d'activités, et renommage d'activité **uniquement si l'athlète l'a activé** (`strava_rename_opt_in`) |
| **Mesures** | Jetons chiffrés AES-256-GCM · `state` OAuth signé HMAC avec TTL de 10 min (`OAuthStateCodec`) · jeton de vérification partagé pour le webhook |

> Le **tracé GPS** est une donnée de localisation précise. La politique publiée annonce l'import
> des « activités sportives associées » sans le nommer. Cf. §7, écart n° 4.

### T-05 — Messagerie coach ↔ athlète

| | |
|---|---|
| **Finalité** | Échanger dans le cadre de l'accompagnement |
| **Base légale** | Exécution du contrat (art. 6-1-b) |
| **Personnes** | Coachs, athlètes |
| **Données** | Corps des messages (texte libre — susceptible de contenir des données de santé saisies par l'un ou l'autre), expéditeur, horodatage, séance rattachée, **pièces jointes** (image ou PDF, stockées en `bytea`), accusés de lecture par fil |
| **Où** | `conversations`, `messages`, `message_attachments`, `conversation_reads` |
| **Durée** | Vie du fil — supprimé avec le club ou l'athlète (cascade) ; **aucune purge propre** |
| **Destinataires** | Railway · les participants du fil |
| **Mesures** | Cloisonnement par fil (`ConversationService.requireReadable` / `requireWritable`) · quota de stockage par club (`app.storage.club-quota-mb`) · téléchargement d'une pièce jointe authentifié par en-tête, ou par jeton à usage unique d'une minute quand elle est ouverte dans un onglet |

### T-06 — Notifications (in-app, e-mail, push)

| | |
|---|---|
| **Finalité** | Prévenir d'une séance planifiée, d'un message, d'une alerte, d'un bilan |
| **Base légale** | Exécution du contrat (art. 6-1-b) pour les envois fonctionnels |
| **Personnes** | Coachs, athlètes |
| **Données** | **Centre de notifications** : type, titre, corps, lien, date de lecture (`notifications`) · **Abonnements push** : `endpoint`, clés `p256dh` et `auth`, `user_agent`, dernier succès (`push_subscriptions`) · **Préférences** : familles coupées, heures de silence, fuseau, heure habituelle de séance (`users`) |
| **Durée** | Centre de notifications : **90 jours** (`NotificationPurgeScheduler`, `app.notifications.retention-days`) · abonnement push : jusqu'à désabonnement ou échec définitif |
| **Destinataires** | Resend (e-mail) · service de notification du navigateur : Google, Mozilla ou Apple (contenu chiffré de bout en bout par VAPID, illisible par le service d'acheminement) |
| **Mesures** | Heures de silence par défaut 22 h – 7 h · aucune donnée de santé dans le corps d'une notification poussée |

### T-07 — Journal des envois d'e-mails

| | |
|---|---|
| **Finalité** | Diagnostiquer un envoi qui n'arrive pas, suivre la consommation du quota |
| **Base légale** | Intérêt légitime (art. 6-1-f) — exploitation du service |
| **Personnes** | Toute personne destinataire d'un e-mail (y compris un invité qui n'a pas de compte) |
| **Données** | Adresse du destinataire, sujet, nature de l'envoi, statut, message d'erreur, horodatage |
| **Où** | `mail_log` — `entity/MailLog.java` |
| **Durée** | **180 jours** (`MailLogPurgeScheduler`, `app.mail.log-retention-days`) |
| **Destinataires** | Railway · administrateurs de plateforme (`/admin`) |

### T-08 — Journal d'audit de l'administration

| | |
|---|---|
| **Finalité** | Tracer les actions d'administration (création, suspension, suppression de compte, impersonation) |
| **Base légale** | Intérêt légitime (art. 6-1-f) — sécurité et imputabilité |
| **Personnes** | Administrateurs de plateforme (auteurs) et comptes visés (cibles) |
| **Données** | Identifiant, **e-mail** et nom de l'auteur · action · type et identifiant de la cible, libellé de la cible (souvent une adresse e-mail) · résumé · **adresse IP** · `user_agent` · horodatage |
| **Où** | `admin_audit_log` — `entity/AdminAuditLog.java` |
| **Durée** | **Aucune — conservation illimitée.** Cf. §7, écart n° 5 |
| **Destinataires** | Railway · administrateurs de plateforme (`/admin/audit`) |
| **Mesures** | Aucune donnée de santé ni secret dans le résumé (règle tenue à la lecture des appels à `AdminAuditService.record`) |

### T-09 — Sauvegardes de la base

| | |
|---|---|
| **Finalité** | Restaurer le service après un incident |
| **Base légale** | Intérêt légitime (art. 6-1-f) |
| **Personnes** | Toutes |
| **Données** | **L'intégralité de la base**, données de santé comprises |
| **Où** | `.github/workflows/db-backup.yml` — `pg_dump -Fc` → chiffrement **AES-256** → artefact GitHub |
| **Durée** | **14 jours** (`retention-days: 14` sur l'artefact) |
| **Destinataires** | GitHub (États-Unis) — les dumps sont **chiffrés**, la clé n'est pas détenue par GitHub |
| **Fréquence** | Quotidienne, 02:30 UTC |

### T-10 — Supervision des erreurs et journaux d'exploitation

| | |
|---|---|
| **Finalité** | Détecter et corriger les anomalies |
| **Base légale** | Intérêt légitime (art. 6-1-f) |
| **Personnes** | Toutes |
| **Données** | **Sentry** : exception, pile d'appels, URL, version déployée, environnement — `send-default-pii: false` côté serveur, aucune identification par défaut côté navigateur · **Better Stack** : lignes de journal applicatives portant l'**identifiant utilisateur** (`MDC`, `LogContextFilter.USER_ID`) et un identifiant de corrélation |
| **Où** | `sentry.*` dans `application.yml`, `front/src/main.ts` · `logback-spring.xml` (appender `BETTERSTACK`, **profil `prod` uniquement**, désactivé sans jeton) |
| **Durée** | Selon la rétention du plan de chaque service — **non pilotée par le code** |
| **Destinataires** | Sentry (région **EU** pour le projet front) · Better Stack — cf. §7, écart n° 6 |
| **Mesures** | La chaîne de requête est retirée des journaux (`LogContextFilter`) : elle pouvait porter un jeton · jamais l'adresse e-mail dans le `MDC`, seulement l'identifiant |

### T-11 — Retours de bêta envoyés depuis l'application

| | |
|---|---|
| **Finalité** | Recueillir et traiter les signalements des utilisateurs |
| **Base légale** | Intérêt légitime (art. 6-1-f) |
| **Personnes** | Utilisateurs connectés qui utilisent « Signaler un problème » |
| **Données** | Identifiant de l'utilisateur, message libre (jusqu'à 4 000 caractères), écran concerné, version de l'application, `user_agent`, identifiant de corrélation, statut de traitement |
| **Où** | `beta_feedback` — `entity/BetaFeedback.java` |
| **Durée annoncée** | 12 mois après traitement |
| **Durée appliquée** | **Aucune — il n'existe aucun planificateur de purge.** Cf. §7, écart n° 7 |
| **Destinataires** | Railway · administrateurs de plateforme (`/admin/feedback`) |

### T-12 — Demandes de création de club

| | |
|---|---|
| **Finalité** | Instruire une demande d'entrée sur la plateforme (régime « sur demande ») |
| **Base légale** | Mesures précontractuelles (art. 6-1-b) |
| **Personnes** | Candidats coachs, y compris ceux dont la demande est refusée |
| **Données** | Nom, e-mail, nom du club souhaité, message, statut, motif de refus |
| **Où** | `club_creation_requests` |
| **Durée** | **Aucune purge**, y compris pour les demandes refusées. Cf. §7, écart n° 7 |
| **Destinataires** | Railway · administrateurs de plateforme (`/admin/club-requests`) |

### T-13 — Diffusion de l'interface web

| | |
|---|---|
| **Finalité** | Servir l'application et relayer les appels d'API |
| **Base légale** | Exécution du contrat (art. 6-1-b) |
| **Personnes** | Tout visiteur |
| **Données** | Journaux d'accès de l'hébergeur (adresse IP, URL, `user-agent`), et **le corps de tous les appels d'API**, qui transitent par le relais `/api/*` défini dans `front/vercel.json` |
| **Où** | `front/vercel.json` — `rewrites` vers Railway |
| **Durée** | Rétention des journaux Vercel, **non pilotée par le code** |
| **Destinataires** | Vercel (États-Unis) |

> La politique publiée écrit qu'« aucune donnée de santé n'y transite **en dehors des appels API
> relayés vers Railway** ». La réserve est exacte, mais elle recouvre en pratique la totalité du
> trafic : Vercel est sur le chemin de chaque appel. Cf. §7, écart n° 8.

---

## 3. Catégories de personnes concernées

| Catégorie | Origine des données | Volume (bêta) |
|---|---|---|
| Coachs et head coachs | Inscription ou invitation | Cohorte fermée (régime `REGISTRATION_MODE=request` en production) |
| Athlètes | Créés par un coach, puis invités | Par club |
| Administrateurs de plateforme | Créés au démarrage (`PlatformAdminBootstrap`) ou depuis `/admin` | Quelques comptes |
| Candidats (demandes de club) | Formulaire public | Ouvert |
| Destinataires d'invitations non acceptées | Adresse saisie par un coach | Ouvert |

**Mineurs.** Rien dans le code ne recueille l'âge à l'inscription ni ne distingue un athlète
mineur, alors que `athletes.birth_date` existe et que la course à pied encadrée concerne
couramment des mineurs. Cf. §7, écart n° 9.

---

## 4. Sous-traitants et transferts

| Sous-traitant | Rôle | Données confiées | Localisation | Encadrement du transfert | Contrat |
|---|---|---|---|---|---|
| **Railway** | Hébergement de l'application et de la base | **Toutes**, santé comprise | États-Unis (région du projet à confirmer) | CCT / DPF | **DPA à formaliser** |
| **Vercel** | Diffusion du front, relais `/api/*` | Journaux d'accès + **tout le trafic d'API en transit** | États-Unis | CCT / DPF | **DPA à formaliser** |
| **GitHub** | Stockage des sauvegardes chiffrées | Base complète, **chiffrée AES-256** (clé non détenue par GitHub) | États-Unis | CCT / DPF | **DPA à formaliser** |
| **Resend** | Envoi des e-mails fonctionnels | Adresse e-mail, nom, contenu de l'e-mail (jamais de donnée de santé) | États-Unis | CCT / DPF | **DPA à formaliser** |
| **Sentry** | Supervision des erreurs | Exceptions, URL, version ; PII désactivées | **UE** (projet front : `ingest.de.sentry.io`) | — | **DPA à formaliser** |
| **Better Stack** | Journaux applicatifs centralisés | Lignes de journal portant l'identifiant utilisateur | Selon la source (`BETTER_STACK_INGEST_URL`) | À déterminer | **Absent de la politique publiée** — cf. §7, écart n° 6 |
| **Strava** | Import d'activités | Jetons OAuth, activités, tracés GPS | États-Unis | Relation directe athlète ↔ Strava (autorisation OAuth) | Conditions d'API Strava |
| **Google / Mozilla / Apple** | Acheminement des notifications push | `endpoint` d'abonnement ; **charge utile chiffrée de bout en bout** (VAPID) | Variable | — | — |

> **Aucun DPA (art. 28) n'est référencé dans le dépôt.** Le point est déjà ouvert au plan de
> conformité (L-16) ; ce registre ne fait que le confirmer, il ne le résout pas.

---

## 5. Durées de conservation — ce que le code applique

| Donnée | Durée annoncée | Durée appliquée | Mécanisme |
|---|---|---|---|
| Compte inactif | 24 mois, après préavis | **24 mois, préavis de 30 jours** ✅ | `InactiveAccountPurgeScheduler` |
| Données d'un compte supprimé | « sans délai » | Immédiat, en cascade | FK `ON DELETE CASCADE` |
| Données de santé après retrait du consentement | Effacement immédiat | Immédiat | `GdprService.withdrawHealthConsent` |
| Centre de notifications | non annoncée | 90 jours | `NotificationPurgeScheduler` |
| Journal des envois d'e-mails | non annoncée | 180 jours | `MailLogPurgeScheduler` |
| Sauvegardes | 14 jours | 14 jours | artefact GitHub |
| Retours de bêta | 12 mois après traitement | **aucune** ❌ | — |
| Journal d'audit d'administration | non annoncée | **aucune** ❌ | — |
| Demandes de club (y compris refusées) | non annoncée | **aucune** ❌ | — |
| Fils de messages | non annoncée | vie du compte | — |
| Journaux Sentry / Better Stack / Vercel | non annoncée | rétention des plans | hors code |

---

## 6. Mesures de sécurité (art. 32)

| Mesure | État | Où |
|---|---|---|
| Chiffrement au repos des données sensibles | ✅ AES-256-GCM, IV par valeur — **périmètre partiel**, cf. écart n° 2 | `EncryptionService`, converters JPA |
| Mots de passe | ✅ BCrypt | `SecurityConfig` |
| Jetons de session | ✅ JWT signés HS512, TTL court, **en-tête uniquement** | `JwtService`, `JwtAuthenticationFilter` |
| Jetons d'URL (SSE, pièces jointes) | ✅ Dédiés, à usage unique, une minute, portée limitée | `StreamTokenService` |
| Révocation de session | ✅ Liste noire en mémoire + horodatage en base (survit au redéploiement) | `TokenBlacklist`, `TokenFreshnessValidator` |
| Cloisonnement multi-tenant | ✅ Validateur sur chaque route club/athlète | `@clubAccessValidator`, `@athleteAccessValidator` |
| Garde de base légale (art. 9) | ✅ Refus d'écriture sans consentement actif | `HealthDataConsentValidator` |
| Plafonnement (anti-force brute, anti-abus) | ✅ Par IP et par porteur de jeton, buckets distincts | `RateLimitFilter` |
| En-têtes de sécurité | ✅ CSP, HSTS, `frame-ancestors 'none'`, `object-src 'none'`, Referrer-Policy | `SecurityConfig`, `vercel.json` |
| Traçabilité de l'administration | ✅ Toute mutation `/admin/**` consignée | `AdminAuditService` |
| Garde-fou de démarrage | ✅ Refus de démarrer en prod sur secrets par défaut | `StartupSecretsValidator` |
| Sauvegardes chiffrées et externalisées | ✅ Quotidiennes | `.github/workflows/db-backup.yml` |
| **Restauration testée de bout en bout** | ❌ **jamais exécutée** (OPS-02) | — |
| Version déployée traçable | ✅ Commit exposé (`/actuator/info`, `<meta name="dari-build">`) | `DeployedVersionContributor` |
| Procédure de notification de violation (72 h) | ❌ non formalisée (L-23 / OPS-04) | — |

---

## 7. Écarts entre la politique publiée et le code

Ordonnés par ce qu'ils coûtent s'ils ne sont pas traités.

### 1. L'identité du responsable de traitement n'est pas publiée — **bloquant**

`LEGAL_OWNER.legalName` et `LEGAL_OWNER.address` sont des chaînes vides, et les blocs
correspondants ne s'affichent donc pas. L'article 13-1-a impose de fournir l'identité et les
coordonnées du responsable de traitement ; un nom commercial et une adresse e-mail n'y suffisent
pas, a fortiori pour un service qui traite des données de l'article 9.
**Où** : `front/src/app/features/public/legal.component.ts`. **Correctif** : renseigner les deux
champs. Aucune ligne de code à écrire.

### 2. « Chiffrées au repos » recouvre moins que ce que la phrase laisse entendre

La politique annonce que les données de santé — « mesures de lactate, niveaux de douleur et de
fatigue, indisponibilités » — sont chiffrées au repos. Le chiffrement porte en réalité sur les
colonnes physiologiques d'`athletes`, les tests de lactate et les jetons OAuth. Les colonnes
`pain` et `fatigue` de `workouts`, `activities`, `daily_check_ins`,
`scheduled_strength_sessions`, `strength_results`, ainsi que le motif d'indisponibilité, sont en
clair.
**Deux issues, toutes deux acceptables** : étendre le chiffrement à ces colonnes (elles ne sont
ni triées ni filtrées en SQL, le converter s'y applique sans autre conséquence qu'une migration
de données), ou préciser la phrase publiée. Ce qui ne l'est pas, c'est l'écart lui-même.

### 3. La portée Strava demandée inclut l'écriture

La connexion demande `activity:read_all,activity:write`. L'écriture n'est utilisée que pour
renommer une activité, et **uniquement si l'athlète l'a activé** (`strava_rename_opt_in`) — le
code est explicite sur ce point. Mais l'écran d'autorisation Strava, lui, annonce à tout le monde
un accès en écriture, et la politique publiée ne mentionne pas cette portée.
**Correctif** : l'annoncer dans la politique (§2, « Données d'appareils connectés »).

### 4. Le tracé GPS n'est pas nommé

`activities.route_json` et `activities.stream_json` portent la trace GPS seconde par seconde d'une
sortie : c'est une donnée de localisation précise, qui dit où l'athlète habite et à quelle heure il
court. La politique parle des « activités sportives associées » et énumère « durée, distance,
allure, fréquence cardiaque ».
**Correctif** : nommer la localisation dans le §2.

### 5. Le journal d'audit d'administration est conservé sans limite

`admin_audit_log` porte l'adresse e-mail de l'auteur, celle de la cible, l'adresse IP et le
`user_agent`, sans aucune purge — alors que le journal des e-mails, comparable, en a une. Il
n'est mentionné nulle part dans la politique publiée.
**Correctif** : une durée (24 à 36 mois est usuel pour un journal de sécurité) et un
planificateur sur le modèle de `MailLogPurgeScheduler` ; puis une ligne dans la politique.

### 6. Better Stack n'est pas dans la liste des sous-traitants

L'appender `BETTERSTACK` de `logback-spring.xml` envoie les journaux applicatifs de production à
un service tiers, et ces lignes portent l'identifiant de l'utilisateur (`MDC`). La liste des
sous-traitants de la politique publiée ne le mentionne pas.
**Correctif** : l'ajouter au §4 de la politique, et à ce registre une fois la localisation
d'ingestion arrêtée. C'est le seul destinataire de ce registre qui manque à la liste publiée.

### 7. Trois durées annoncées ou attendues ne sont appliquées par rien

- **Retours de bêta** : « conservés jusqu'à 12 mois après leur traitement » — aucun planificateur.
- **Demandes de création de club** : conservées indéfiniment, y compris refusées. Une demande
  refusée n'a plus de finalité passé le délai de contestation.
- **Journal d'audit** : cf. écart n° 5.

Une seule tâche planifiée, sur le modèle de `MailLogPurgeScheduler`, couvrirait les trois.

### 8. Vercel reçoit plus que ce que la phrase suggère

« Aucune donnée de santé n'y transite **en dehors des appels API relayés vers Railway** » est
exact, mais la réserve recouvre la totalité du trafic : le relais `/api/*` de `vercel.json` fait
passer chaque appel par Vercel. La formulation minimise le rôle réel du sous-traitant.
**Correctif** : dire que Vercel relaie l'ensemble des appels d'API.

### 9. Rien ne traite le cas des mineurs

`athletes.birth_date` existe et rien ne s'en sert pour distinguer un athlète mineur, recueillir
l'autorisation parentale, ou l'interdire. La pratique encadrée de la course à pied concerne
couramment des mineurs, et il s'agit de données de santé.
**Correctif** : une décision produit (interdire, ou encadrer), pas seulement du code. Déjà ouvert
au plan de conformité (L-14).

### 10. La qualification du coach n'est pas tranchée

Cf. §1. Elle décide de l'existence d'un accord de responsabilité conjointe (art. 26). Question
juridique, pas technique — mais elle change ce que ce registre doit dire.

---

## 8. Tenue à jour

Ce registre décrit le code à la date indiquée en tête. Le mettre à jour lorsque :

- une **entité** nouvelle porte des données personnelles (une table, une colonne) ;
- un **sous-traitant** est ajouté (une intégration sortante, un appender, un relais) ;
- une **durée de conservation** change — `app.accounts.inactivity.*`, `app.notifications.*`,
  `app.mail.log-retention-days`, la rétention de l'artefact de sauvegarde ;
- une **base légale** change (une garde de consentement ajoutée ou retirée).

Les valeurs de durée sont un **engagement publié** : les modifier suppose de modifier aussi
`legal.component.ts` §5, et réciproquement.

---

## 9. Sources

| Sujet | Fichier |
|---|---|
| Données stockées | `back/src/main/java/com/coachrun/entity/**` |
| Schéma et migrations | `back/src/main/resources/db/changelog/**` |
| Durées de conservation | `back/src/main/resources/application.yml` (`app.accounts.inactivity`, `app.notifications`, `app.mail`), `back/src/main/java/com/coachrun/scheduler/**` |
| Consentement santé | `security/HealthDataConsentValidator.java`, `service/GdprService.java` |
| Chiffrement | `security/EncryptionService.java`, `security/Encrypted*Converter.java` |
| Export et effacement | `service/GdprService.java`, `controller/AthletePortalController.java` |
| Sous-traitants sortants | `integration/ResendMailClient.java`, `integration/StravaClient.java`, `resources/logback-spring.xml`, `front/vercel.json`, `.github/workflows/db-backup.yml` |
| Politique publiée | `front/src/app/features/public/legal.component.ts` |
| Plan de conformité | `docs/PLAN-CONFORMITE-BETA-2026-08.md` |
