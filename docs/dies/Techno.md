# Techno.md — Référence technique de **Dies**

> Application web privée de suivi des dossiers et échéances juridiques. **Une seule utilisatrice.**
> Reprend l'ADN technique éprouvé de DARI Lab (Spring Boot 3 / Angular 17 / PostgreSQL / Liquibase) en
> retirant tout ce qui ne sert pas : multi-tenant, rôles, inscription, intégrations OAuth, paiement.
> Sections _(hypothèse)_ = recommandations à confirmer au premier lot.

---

## 1. Stack

### Backend
| Couche | Choix | Version |
|---|---|---|
| Framework | Spring Boot | 3.2.x |
| Langage | Java | 21 |
| ORM | Spring Data JPA + Hibernate | — |
| Migrations | **Liquibase (YAML)** — jamais de DDL manuel | — |
| Base | PostgreSQL | 16 |
| Auth | Spring Security + JWT (jjwt HS512) — **compte unique en variables d'environnement** | — |
| 2FA _(S)_ | TOTP (`dev.samstevens.totp` ou équivalent) | — |
| Validation | Jakarta Validation | — |
| Mapping | MapStruct + Lombok | — |
| E-mail | **Resend** (ou SMTP) + templates HTML externalisés | — |
| Push _(S)_ | WebPush VAPID | — |
| Planification | Spring `@Scheduled` + **ShedLock** (garantit une seule exécution) | — |
| Stockage documents | AWS SDK v2 S3 (Scaleway / OVH / MinIO — **UE**) ou disque chiffré | — |
| Calendrier | **iCal4j** (flux `.ics` d'abonnement) | — |
| Import | Apache POI (XLSX) + OpenCSV | — |
| Export PDF | OpenPDF / Flying Saucer _(hypothèse)_ | — |
| Doc API | Springdoc OpenAPI — **exposé hors production uniquement** | — |
| Supervision | Sentry (`send-default-pii: false`) | — |
| Build | Maven | — |

### Frontend
| Couche | Choix | Version |
|---|---|---|
| Framework | Angular standalone | 17+ |
| Langage | TypeScript | 5.4+ |
| Réactivité | RxJS + Signals sur les vues denses | 7.8 |
| PWA | `@angular/service-worker` | — |
| Dates | **date-fns** + locale `fr` (jamais de manipulation de date maison côté front) | — |
| Calendrier | Composant maison (grille mensuelle) — **pas de dépendance lourde** | — |
| Supervision | `@sentry/angular` | — |
| Tests | Karma / Jasmine | — |

### Ce qu'on n'installe pas
Pas de store global (NgRx), pas de framework CSS (Tailwind/Bootstrap) — le design system tokenisé de
`Design.md` suffit —, pas de librairie de calendrier tierce, pas de SDK d'authentification externe.
**Toute dépendance ajoutée doit être justifiée en revue.**

---

## 2. Architecture

```
back/src/main/java/com/dies/
  ├── config/          # SecurityConfig, CorsConfig, ShedLockConfig, StartupSecretsValidator
  ├── controller/      # REST, une ressource par controller
  ├── service/         # Métier
  │   ├── deadline/    # DeadlineEngine, CalendrierChomeService, ProcedureApplicationService
  │   ├── reminder/    # ReminderPlannerService, ReminderSenderService, DigestService
  │   └── importexport/# CsvImportService, IcalFeedService, PdfExportService
  ├── entity/ + entity/enums/
  ├── repository/
  ├── dto/request/ + dto/response/
  ├── security/        # JwtService, EnvUserDetailsService, TotpService, EncryptedStringConverter
  ├── scheduler/       # RappelScheduler, GenerationAnnuelleScheduler, PurgeScheduler
  └── exception/
front/src/app/
  ├── core/            # services/, models/, guards/, interceptors/
  ├── features/
  │   ├── dashboard/   # tableau de bord
  │   ├── echeances/   # liste, mois, formulaire
  │   ├── dossiers/    # liste, détail (onglets), formulaire
  │   ├── entites/     # sociétés, année sociale, mandats
  │   ├── documents/ contacts/ journal/
  │   ├── referentiel/ # règles de délai, modèles de procédure
  │   ├── parametres/  # rappels, jours chômés, jeton iCal
  │   └── login/
  └── shared/          # components/, pipes/ (datefr, joursRestants, statut), directives/
```

### Séparation des responsabilités
Controller (routing, validation, mapping DTO) → Service (métier, transitions d'état, calcul, effets de
bord) → Repository. **Le `DeadlineEngine` est un service pur** : aucune dépendance JPA, aucun accès
réseau, seulement des `LocalDate` et un calendrier injecté. C'est ce qui le rend testable au cas près.

### Gestion de l'état
- **Back** : sans état, tout en base.
- **Front** : pas de store global. Un `BehaviorSubject` pour l'authentification, un pour les préférences.
  Brouillons de formulaires longs en `localStorage` avec bannière de restauration.

### Gestion des erreurs
- **Back** : `@RestControllerAdvice` global → `{status, message, timestamp, path}` ; 400 (+ `fieldErrors`), 401, 403, 404, 409 (transition d'état interdite), 422 (règle de délai incalculable), 500 (+ `correlationId`).
- **Front** : intercepteur HTTP → toast par code, `SILENT_PATTERNS` pour `/auth/login`, déconnexion sur 401.

---

## 3. Backend

### 3.1 API — routes `/api/v1`

**Toutes les routes sont authentifiées**, sans exception, hors `/api/v1/auth/login`,
`/api/v1/auth/refresh`, `/actuator/health` et le flux iCal (authentifié par jeton dans l'URL).

| Ressource | Routes |
|---|---|
| Auth | `POST /auth/login` · `POST /auth/refresh` · `POST /auth/logout` · `GET /auth/me` |
| Tableau de bord | `GET /dashboard` (agrégats : retards, aujourd'hui, semaine, 30 jours) |
| Échéances | `GET /echeances` (filtres : `du`, `au`, `statut`, `criticite`, `nature`, `dossierId`, `entiteId`, `q`, pagination) · `GET /echeances/mois/{annee}/{mois}` · `POST` · `PUT /{id}` · `PATCH /{id}/statut` · `POST /{id}/reporter` · `DELETE /{id}` |
| Dossiers | `GET /dossiers` · `POST` · `GET /{id}` · `PUT /{id}` · `PATCH /{id}/statut` · `POST /{id}/dupliquer` · `DELETE /{id}` |
| Entités | `GET /entites` · `POST` · `GET /{id}` · `PUT /{id}` · `GET /{id}/annee-sociale/{exercice}` · `POST /{id}/generer-annee/{exercice}` |
| Mandats | `GET /entites/{id}/mandats` · `POST` · `PUT /{mandatId}` |
| Référentiel | `GET /regles-delai` · `POST` · `PUT /{id}` · `POST /regles-delai/simuler` (calcul à blanc) |
| Modèles | `GET /modeles-procedure` · `POST` · `PUT /{id}` · `POST /modeles-procedure/{id}/appliquer` (aperçu + confirmation) |
| Documents | `POST /documents` (multipart) · `GET /documents/{id}` (URL signée) · `DELETE /{id}` |
| Contacts | CRUD `/contacts` |
| Journal | `GET /dossiers/{id}/journal` · `POST /dossiers/{id}/journal` |
| Paramètres | `GET/PUT /parametres` · `GET/POST/DELETE /parametres/jours-chomes` · `POST /parametres/ical/regenerer` |
| Import/Export | `POST /import/previsualiser` · `POST /import/confirmer` · `GET /export/echeances.csv` · `GET /export/mois/{annee}/{mois}.pdf` · `GET /export/archive.zip` |
| iCal | `GET /ical/{token}.ics` — **hors session, jeton opaque de 32 octets, révocable** |

### 3.2 Modèle de données

Toutes les entités héritent de `BaseEntity` (`id` UUID, `createdAt`, `updatedAt`).

```
entite            (denomination, forme_juridique, siren, greffe, adresse, capital,
                   cloture_jour, cloture_mois, a_cac, cac_fin_mandat, categorie_taille,
                   mode_depot, regime_fiscal, statut, notes🔒)
mandat            (entite_id, personne, fonction, date_debut, duree_annees, date_fin, statut)
dossier           (reference UNIQUE, intitule, type, entite_id?, contrepartie, statut,
                   criticite, date_ouverture, date_cloture, responsable, description🔒,
                   montant_en_jeu🔒, tags[], dossier_parent_id?)
echeance          (dossier_id?, entite_id?, code_regle?, intitule, nature, criticite,
                   date_echeance, date_source, regle_delai_id?, trace_calcul,
                   date_ajustee bool, statut, date_realisation, note🔒,
                   document_preuve_id?, exercice?, paliers_rappel[])
regle_delai       (code UNIQUE, libelle, quantite, unite, sens, report, delai_distance,
                   base_legale, source_url, criticite_defaut, paliers_defaut[],
                   nature, verifie_le, actif)
modele_procedure  (code UNIQUE, libelle, domaine, fait_generateur_libelle, actif)
etape_modele      (modele_id, ordre, libelle, regle_delai_id?, offset_libre?, obligatoire)
rappel            (echeance_id, palier, date_envoi_prevue, canal, statut, envoye_le, erreur)
document          (dossier_id?, echeance_id?, entite_id?, nom_fichier, type_document,
                   date_acte, cle_stockage, taille, sha256, confidentiel bool)
contact           (nom, structure, fonction, email, telephone, adresse🔒, note🔒)
dossier_contact   (dossier_id, contact_id, role)
entree_journal    (dossier_id, date, type, texte🔒, verrouille_le)
jour_chome        (date, libelle, type ENUM[FERIE_LEGAL, ALSACE_MOSELLE, CONGE_PERSONNEL])
journal_acces     (date, type_evenement, ip_hash, user_agent, succes, detail)
parametres        (singleton : email_destination, heure_envoi, briefs actifs,
                   ical_token🔒, options calendrier)
```
🔒 = **champ chiffré au repos** (AES-256-GCM via `EncryptedStringConverter`).

**Contraintes et index déterminants**

| Contrainte | Raison |
|---|---|
| `UNIQUE (entite_id, exercice, code_regle)` sur `echeance` | **Idempotence de la génération annuelle** — jamais de doublon d'échéance récurrente |
| `UNIQUE (echeance_id, palier)` sur `rappel` | **Idempotence des rappels** — un redémarrage ne renvoie rien deux fois |
| `UNIQUE (reference)` sur `dossier` | Référence non réutilisable |
| `UNIQUE (code)` sur `regle_delai`, `modele_procedure` | Référentiel stable |
| Index `(date_echeance, statut)` sur `echeance` | Tableau de bord, vue mois, planificateur |
| Index `(dossier_id, date_echeance)` · `(entite_id, exercice)` | Vues dossier et société |
| Index GIN `tags` sur `dossier` _(hypothèse)_ | Filtre par tag |

**Ce qui n'existe pas** : aucune table `user`, aucune table `role`, aucune colonne `tenant_id`.
L'identité vient des variables d'environnement.

### 3.3 Machines à états

```
Dossier   : OUVERT → EN_COURS → EN_ATTENTE_TIERS ⇄ SUSPENDU → CLOS → ARCHIVE
            (CLOS avec échéances A_FAIRE → avertissement explicite, jamais de blocage silencieux)
Echeance  : A_FAIRE → EN_COURS → FAITE
            A_FAIRE → SANS_OBJET (motif requis)
            A_FAIRE → REPORTEE  (nouvelle date + motif requis, ancienne date conservée)
Rappel    : PLANIFIE → ENVOYE | ECHEC (réessai ×3, puis alerte Sentry)
```
Toute transition non prévue → `409 Conflict`, validée **en service**, jamais dans le controller.

### 3.4 Authentification mono-utilisatrice — spécification précise

**Principe** : il n'existe pas de compte en base. `EnvUserDetailsService` construit l'unique
`UserDetails` à partir des variables d'environnement au démarrage.

```
AUTH_USERNAME        = juriste                      # identifiant de connexion
AUTH_PASSWORD_HASH   = $2a$12$....                  # hachage BCrypt, coût 12 — JAMAIS en clair
AUTH_TOTP_SECRET     = BASE32SECRET...              # optionnel (2FA)
```

Génération du hachage (documentée dans le `README` du dépôt) :
```bash
htpasswd -bnBC 12 "" 'MotDePasseFort' | tr -d ':\n'
```

**Garde-fous au démarrage** (`StartupSecretsValidator`, profil `prod`) — l'application **refuse de
démarrer** si :
- `AUTH_USERNAME` ou `AUTH_PASSWORD_HASH` est absent ;
- `AUTH_PASSWORD_HASH` n'est pas un hachage BCrypt valide (`$2[aby]$`) — protège contre le mot de passe collé en clair ;
- `JWT_SECRET` ou `FIELD_ENCRYPTION_KEY` est resté à sa valeur de développement, ou fait moins de 64 caractères / 64 hex ;
- `FRONTEND_URL` ou `CORS_ORIGINS` contient `localhost` ;
- `MAIL_ENABLED=true` sans clé d'envoi — **des rappels qui partent dans le vide sont pires que pas de rappels**.

**Session** : access token JWT 30 min (en-tête `Authorization`), refresh token en cookie
`httpOnly` `Secure` `SameSite=Strict` valable 7 jours, **rotation à chaque usage**, révocation au logout
(liste courte en base ou en mémoire). Déconnexion automatique après 30 min d'inactivité côté front.

**Anti-force brute** : 5 tentatives par tranche de 15 min et par IP, temporisation croissante
(1 s, 2 s, 4 s…), consignation systématique dans `journal_acces` (IP **hachée**, jamais en clair).

**Absences délibérées** : pas de `POST /auth/register`, pas de `/auth/forgot-password`, pas de
`/auth/reset-password`. Le changement de mot de passe est une **opération d'exploitation** : régénérer le
hachage, mettre à jour la variable, redéployer. C'est documenté dans `OPERATIONS` et assumé.

### 3.5 Sécurité applicative

- `@EnableMethodSecurity`, `SessionCreationPolicy.STATELESS`, tout `authenticated()` par défaut.
- En-têtes : `Content-Security-Policy` stricte (`default-src 'self'`, `frame-ancestors 'none'`), HSTS,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`,
  `Permissions-Policy` minimale, **`X-Robots-Tag: noindex, nofollow` sur toutes les réponses**.
- CORS restreint à l'origine du front, `allowCredentials: true`.
- Limitation de débit : `/auth/**` (strict), `/api/v1/**` (souple), `/ical/**` (par jeton).
- **Chiffrement au repos** AES-256-GCM (`FIELD_ENCRYPTION_KEY`, 64 hex) sur les champs 🔒 du § 3.2 et
  sur les **documents** (chiffrement côté application avant stockage objet).
- Téléversement : type MIME et extension contrôlés, taille plafonnée, **nom de fichier normalisé**,
  jamais servi depuis le domaine de l'API sans en-tête `Content-Disposition: attachment`.
- Flux iCal : jeton opaque de 32 octets, non devinable, **révocable en un clic**, ne renvoyant que
  `intitulé + date + référence de dossier` — **jamais de contenu confidentiel dans un flux non
  authentifié**.
- Journaux : jamais de nom de client, de contenu de dossier, de jeton ni de mot de passe.

### 3.6 Jobs planifiés (ShedLock)

| Job | Fréquence | Rôle |
|---|---|---|
| `RappelScheduler` | Toutes les heures | Sélectionne les rappels `PLANIFIE` échus, regroupe par destinataire, envoie **un** e-mail, marque `ENVOYE` (idempotence par `UNIQUE (echeance_id, palier)`) |
| `BriefHebdoScheduler` | Lundi 8 h | Brief de la semaine — **envoyé même s'il n'y a rien** (« tout est calme »), ce qui prouve chaque semaine que la chaîne d'alerte fonctionne |
| `PlanDuMoisScheduler` _(S)_ | 1er du mois 8 h | Plan du mois par société |
| `GenerationAnnuelleScheduler` | Quotidien 3 h | Pour chaque entité dont l'exercice vient de clore : applique `MOD_ANNEE_SOCIALE`, idempotent |
| `RecalculStatutScheduler` | Quotidien 0 h 05 | Bascule les échéances dépassées en retard, recalcule les compteurs |
| `PurgeScheduler` | Mensuel | Journal d'accès > 12 mois, rappels envoyés > 24 mois, brouillons orphelins |
| `SanteScheduler` | Quotidien | Vérifie que la chaîne e-mail répond ; alerte Sentry sinon |

**Règle d'or des effets de bord** : *réserver puis envoyer*. On marque le rappel comme pris en charge
dans la transaction, on envoie hors transaction, on marque `ENVOYE` ou `ECHEC`. Un rappel envoyé deux
fois ruine la confiance ; un rappel jamais envoyé ruine le produit.

### 3.7 Import du tableur existant

`POST /import/previsualiser` accepte CSV/XLSX, retourne un **rapport ligne à ligne** (créations,
mises à jour, erreurs, colonnes non reconnues) **sans rien écrire**. `POST /import/confirmer` rejoue le
même mappage dans une transaction unique. Modèle de fichier téléchargeable fourni. Colonnes attendues et
synonymes tolérés (`société`/`entité`/`client`, `date limite`/`échéance`…).

---

## 4. Frontend

- **100 % standalone**, `inject()`, `loadComponent` lazy sur toutes les routes, `authGuard` fonctionnel.
- `ReactiveFormsModule` pour les formulaires structurés (échéance, société, modèle de procédure) ;
  `FormsModule` pour les filtres.
- **Dates** : `date-fns` + locale `fr` ; format d'affichage `JJ/MM/AAAA` avec jour de la semaine sur les
  vues d'échéance ; saisie tolérante (`31/12/25`, `31.12.2025`, `31122025`) ; **aucun calcul de délai
  côté front** — le front affiche ce que le back a calculé, y compris la trace.
- **Pipes maison** : `joursRestants` (« dans 12 jours » / « il y a 3 jours »), `statutEcheance`,
  `criticite`, `formeJuridique`, `typeDossier`.
- **Composant calendrier maison** : grille mensuelle 7 colonnes, pastilles de criticité, panneau latéral
  du jour sélectionné, impression via `@media print`.
- **PWA** : installable, mise en cache de l'app shell ; consultation hors ligne du tableau de bord chargé
  _(hypothèse)_ ; **aucune donnée de dossier persistée hors ligne au-delà du cache HTTP**.
- Performance : `OnPush` + Signals sur la vue mois et le tableau de bord, `takeUntil(destroy$)` partout,
  pagination serveur systématique.

---

## 5. DevOps

### Déploiement _(hypothèse, alignée sur ce qui est déjà maîtrisé)_
- **Front** : Vercel (réécriture SPA), build AOT.
- **Back** : Docker sur Railway ou VPS UE ; `docker-compose` (postgres + back) en local.
- **Base** : PostgreSQL managé **hébergé dans l'UE**.
- **Documents** : Scaleway Object Storage / OVH / MinIO (UE).
- Profils Spring : `dev` (jeu de données de démonstration, Swagger actif) / `prod` (garde-fous du § 3.4, Swagger **désactivé**).
- Coût cible : **< 15 €/mois**.

### CI/CD (GitHub Actions)
Job backend `mvn -B -ntp clean verify` + **smoke test de démarrage** sur PostgreSQL éphémère (valide les
migrations Liquibase) ; job frontend `npm ci && npm run build`. Concurrence annulable.
**Le job backend échoue si les tests du `DeadlineEngine` échouent** — c'est le garde-fou métier.

### Variables d'environnement (`env.example` fourni)
```
AUTH_USERNAME · AUTH_PASSWORD_HASH · AUTH_TOTP_SECRET(optionnel)
JWT_SECRET · FIELD_ENCRYPTION_KEY(64 hex)
JDBC_DATABASE_URL · PGUSER · PGPASSWORD
FRONTEND_URL · CORS_ORIGINS
MAIL_ENABLED · RESEND_API_KEY · MAIL_FROM · MAIL_TO
STORAGE_TYPE · S3_ENDPOINT · S3_BUCKET · S3_ACCESS_KEY · S3_SECRET_KEY
VAPID_PUBLIC_KEY · VAPID_PRIVATE_KEY (si push)
SENTRY_DSN · APP_TIMEZONE=Europe/Paris · APP_LOCALE=fr-FR
```

### Sauvegarde et restauration
`pg_dump` quotidien **chiffré** (GPG ou chiffrement du fournisseur), conservé **hors du serveur
applicatif**, rétention 30 jours. Les documents sont sauvegardés avec la base.
**Une restauration doit être réellement effectuée une fois avant la mise en service**, et re-testée tous
les six mois (échéance `INT_TEST_RESTAURATION` créée dans l'application elle-même).

### Supervision
Sentry back + front, `send-default-pii: false`, scrubbing des champs `note`, `description`,
`denomination`, `intitule`. `/actuator/health` public, le reste fermé. **Alerte spécifique sur l'échec
d'envoi d'un rappel** : c'est la seule panne réellement critique de ce produit.

---

## 6. Décisions techniques et alternatives

### Pourquoi la même pile que DARI Lab pour une application mono-utilisatrice ?
Elle est **surdimensionnée en théorie, optimale en pratique** : les conventions, la CI, le
`docker-compose`, la validation des secrets, les intercepteurs et le design system existent déjà et sont
maîtrisés. Le temps gagné dépasse largement le surcoût d'un JVM à faire tourner (~512 Mo).

**Basculer sur une pile plus légère** (Next.js + SQLite/Postgres, ou Spring Boot servant directement le
front) se justifierait si l'hébergement devait descendre sous 5 €/mois ou si la maintenance devenait un
sujet. Cette bascule est **une décision de lot 0**, à ne pas rouvrir ensuite.

### Points à trancher au lot 0
- ⚠️ **Fuseau horaire** : tout en `Europe/Paris`, `LocalDate` (pas d'`Instant`) pour les échéances — une échéance est une date civile, pas un instant. Seuls les délais en **heures** (72 h RGPD) utilisent un `Instant`.
- ⚠️ **Stockage des documents** : S3 UE dès le départ, ou disque chiffré puis migration ? Recommandation : **S3 UE dès le lot 4** (la sauvegarde est incluse dans le service).
- ⚠️ **2FA** : recommandée. Si l'ergonomie gêne, alternative acceptable : mot de passe long (≥ 20 caractères) + limitation de débit stricte + alerte e-mail à chaque connexion depuis une IP nouvelle.
- ⚠️ **Version d'Angular** : 17 pour rester aligné avec DARI Lab, ou dernière LTS pour un projet neuf. Recommandation : **dernière LTS**, le partage de code étant nul.

### À conserver absolument de l'ADN DARI Lab
✅ Liquibase + smoke test CI · DTO Request/Response séparés · `BaseEntity` UUID · transitions d'état en
service · design tokens · intercepteurs HTTP + toasts · standalone + lazy routing · idempotence des
effets de bord · validation des secrets au démarrage · `env.example` exhaustif · chiffrement au repos.

### Spécifique à Dies — à soigner dès le départ
✅ `DeadlineEngine` **pur et testé au cas près** (§ 5 du référentiel) — la crédibilité du produit en dépend.
✅ **Idempotence** de la génération annuelle et des rappels (contraintes `UNIQUE`, pas de garde applicative seule).
✅ **Trace de calcul** persistée et affichée sur chaque échéance.
✅ **Report asymétrique** avant/après (préavis vs délai pour agir).
✅ **Aucune donnée confidentielle** dans les logs, Sentry, le flux iCal ou les e-mails de rappel (un e-mail contient l'intitulé et la référence, **pas le détail du dossier**).

---

*Techno.md v1.0 — à mettre à jour à chaque décision structurante.*
