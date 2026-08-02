# PLAN-DEVELOPPEMENT.md — Découpage exécutable de **Dies**

> Sept lots, dans l'ordre. Chaque lot est **livrable et utilisable** en l'état, contient sa
> **définition de terminé**, et se termine par une vérification réelle (`mvn verify`, `npm run build`,
> démarrage). Le texte en bloc citation à la fin de chaque lot est **directement copiable comme
> consigne à Claude Code**.
>
> Estimations en sessions de travail assistées, à titre indicatif.

---

## Lot 0 — Socle et sécurité (≈ 2 sessions)

**Objectif** : une application vide mais **déjà sûre**, qui démarre, se déploie et refuse toute
configuration bancale.

- Dépôt `dies` : `back/` (Spring Boot 3.2, Java 21, Maven), `front/` (Angular standalone, PWA), `docker-compose.yml` (postgres + back), `.github/workflows/ci.yml`.
- `BaseEntity`, configuration Liquibase (master + `001-init.yaml`), profils `dev` / `prod`.
- **Authentification mono-utilisatrice** : `EnvUserDetailsService` (identifiant + hachage BCrypt en variables d'environnement), JWT 30 min + refresh en cookie `httpOnly` avec rotation, logout, limitation de débit sur `/auth/**`, `journal_acces`.
- `StartupSecretsValidator` : refus de démarrer en `prod` si un secret manque, si `AUTH_PASSWORD_HASH` n'est pas un BCrypt valide, si `CORS_ORIGINS` contient `localhost`.
- En-têtes de sécurité, `X-Robots-Tag: noindex`, `robots.txt` interdisant tout, CORS restreint.
- `EncryptedStringConverter` (AES-256-GCM) + test de bout en bout.
- Front : écran de connexion, layout (barre latérale + en-tête), `authInterceptor`, `errorInterceptor`, `ToastService`, `ConfirmDialogService`, **tokens du design system** (`Design.md` §§ 2-4), pipes `dateFr`, `joursRestants`.
- `env.example` complet, `README` du dépôt avec la procédure de génération du hachage.

**Terminé quand** : `docker compose up` démarre ; la connexion fonctionne avec les identifiants
d'environnement ; toute route protégée renvoie 401 sans jeton ; l'application refuse de démarrer en
`prod` sans secrets ; la CI est verte.

> **Consigne Claude Code** — « Initialise le dépôt Dies selon `docs/Techno.md` §§ 2 et 3 : squelette
> Spring Boot 3.2 / Java 21 + Angular standalone, Liquibase, docker-compose, CI GitHub Actions.
> Implémente l'authentification mono-utilisatrice par variables d'environnement (§ 3.4), le
> `StartupSecretsValidator`, les en-têtes de sécurité, le chiffrement de champ AES-256-GCM. Côté front :
> écran de connexion, layout, intercepteurs, toasts, et les tokens CSS de `docs/Design.md` §§ 2-4.
> Vérifie par `mvn verify`, `npm run build` et un démarrage réel. »

---

## Lot 1 — Dossiers et échéances manuelles (≈ 2 sessions)

**Objectif** : l'outil devient utilisable **tout de suite**, même sans moteur de calcul.

- Entités `Dossier` et `Echeance` + migrations + index (`Techno.md` § 3.2).
- Génération de la référence `AAAA-TYP-NNN` (atomique, sans collision).
- CRUD dossiers avec machine à états et motif obligatoire sur les transitions qui l'exigent.
- CRUD échéances **saisies à la main** (date libre), statuts, marquage « faite », report avec motif.
- **Tableau de bord** : en retard / aujourd'hui / cette semaine / 30 jours, dans cet ordre.
- **Vue liste** : filtres combinables (période, statut, criticité, nature, dossier), tri, pagination serveur.
- **Vue dossier** : en-tête + onglet Échéances + chronologie.
- Composants `app-echeance-row`, `app-badge-urgence`, `app-badge-criticite`, `app-date-input`.

**Terminé quand** : elle peut saisir un dossier et ses échéances, les voir dans le tableau de bord et les
marquer faites. **À ce stade, l'outil remplace déjà partiellement le tableur.**

> **Consigne Claude Code** — « Implémente le lot 1 : entités `Dossier` et `Echeance`, machines à états du
> `Techno.md` § 3.3, CRUD complets, tableau de bord, vue liste filtrable, vue dossier. Respecte
> `docs/Design.md` § 5.2 pour `app-echeance-row` et les badges. Motif obligatoire sur report et
> `SANS_OBJET`. Pagination serveur partout. »

---

## Lot 2 — Moteur de délais et référentiel (≈ 3 sessions) — **le lot critique**

**Objectif** : Dies calcule au lieu de stocker.

- **`DeadlineEngine`** (`Referentiel-juridique.md` § 2) : unités, sens, report asymétrique, jours fériés calculés (Pâques comprise), délais de distance, trace de calcul.
- **Les 30 cas de test T01→T30 écrits d'abord** (`Referentiel-juridique.md` § 5). Le lot n'avance pas tant qu'ils ne passent pas tous.
- Entités `RegleDelai`, `JourChome`, `ModeleProcedure`, `EtapeModele` + **jeu de données initial** issu du § 3 du référentiel (codes, formules, bases légales, `verifieLe`).
- Entités `Entite` et `Mandat` + fiche société + **frise de l'année sociale**.
- **Application d'un modèle en deux temps** : aperçu des échéances proposées, ajustement, confirmation.
- **Génération annuelle** (`GenerationAnnuelleScheduler`) idempotente via `UNIQUE (entite_id, exercice, code_regle)`.
- Écran **Référentiel** : règles éditables, `verifieLe` affiché, **simulateur de calcul** (`POST /regles-delai/simuler`).
- Composants `app-trace-calcul`, `app-frise-annee-sociale`, `app-application-modele`, `app-selecteur-regle`.

**Terminé quand** : créer une société avec sa date de clôture et appliquer `MOD_ANNEE_SOCIALE` produit
l'ensemble des échéances datées, chacune avec sa trace et sa base légale ; relancer la génération ne crée
aucun doublon ; les 30 tests passent en CI.

> **Consigne Claude Code** — « Implémente le lot 2. **Commence par écrire les tests T01 à T30 de
> `docs/Referentiel-juridique.md` § 5**, puis le `DeadlineEngine` qui les fait passer. Le moteur est un
> service pur, sans dépendance JPA. Attention au report asymétrique (§ 2.3) et au quantième inexistant
> (§ 2.2). Ensuite : `RegleDelai`, `ModeleProcedure`, `Entite`, `Mandat`, le jeu de données du § 3, la
> génération annuelle idempotente et l'application de modèle en deux temps (aperçu puis confirmation). »

---

## Lot 3 — Rappels, vue mois, import (≈ 2 sessions)

**Objectif** : l'outil **la prévient** et absorbe son historique. C'est ici qu'il devient indispensable.

- Entité `Rappel`, planification aux paliers de la criticité, `UNIQUE (echeance_id, palier)`.
- `RappelScheduler` horaire (réserver → envoyer hors transaction → marquer), réessai ×3, alerte Sentry sur échec.
- Envoi e-mail (Resend ou SMTP), gabarits HTML externalisés, **contenu limité à l'intitulé, la date, la référence et la société** — jamais de détail confidentiel.
- **Brief hebdomadaire du lundi 8 h**, envoyé même vide.
- **Vue mois** : `app-calendrier-mois` + `app-liste-du-mois` groupée par jour, filtres, **impression du plan du mois**.
- **Import CSV/XLSX** en deux temps (prévisualisation avec rapport ligne à ligne, puis confirmation), modèle de fichier téléchargeable.
- Écran Paramètres : adresse e-mail, heure d'envoi, paliers par défaut, jours chômés personnels.

**Terminé quand** : une échéance à J-7 déclenche un e-mail et un seul ; un redémarrage du serveur ne le
renvoie pas ; le brief du lundi arrive ; la vue mois s'imprime lisiblement ; son tableur est importé sans
perte.

> **Consigne Claude Code** — « Implémente le lot 3 : `Rappel` + `RappelScheduler` (ShedLock, réserver
> puis envoyer, idempotence par contrainte `UNIQUE`), envoi e-mail avec gabarits externalisés, brief
> hebdomadaire envoyé même vide, vue mois (calendrier + liste groupée par jour + impression), import
> CSV/XLSX en deux temps avec rapport d'erreurs. Aucun contenu confidentiel dans les e-mails. »

---

## Lot 4 — Documents, journal, contacts (≈ 2 sessions)

- Entité `Document` : téléversement chiffré, stockage objet UE, empreinte SHA-256, téléchargement par URL signée à durée courte, contrôle du type MIME et de la taille.
- **Preuve de réalisation** rattachée à une échéance passée à `FAITE`.
- `EntreeJournal` : main courante en une ligne, verrouillage après 24 h.
- `Contact` + `dossier_contact` (rôle par dossier), lien `mailto` pré-rempli avec la référence.
- Onglets *Documents / Journal / Contacts* sur la fiche dossier.
- Marquage « Confidentiel — consultation juridique » _(si le régime s'applique, cf. référentiel § 6.3)_.

> **Consigne Claude Code** — « Implémente le lot 4 : documents chiffrés au repos avec stockage objet et
> URL signées, preuve de réalisation sur les échéances, journal de dossier verrouillé après 24 h,
> contacts avec rôle par dossier. Contrôle strict des types de fichiers et de la taille. »

---

## Lot 5 — Confort et intégration à son quotidien (≈ 2 sessions)

- **Flux iCal** : `GET /ical/{token}.ics`, jeton opaque révocable, **contenu minimal** (intitulé, date, référence). Documentation de l'abonnement dans Outlook et Google Agenda.
- **Recherche globale** `Ctrl/Cmd+K` (dossiers, sociétés, contacts, échéances).
- Exports : CSV de toute liste filtrée, PDF « plan du mois » et « fiche dossier », archive ZIP complète.
- **Plan du mois** par e-mail le 1er.
- Notifications push PWA _(optionnel)_.
- Raccourcis clavier, écran d'aide, page « à propos » avec la date de dernière revue du référentiel.

> **Consigne Claude Code** — « Implémente le lot 5 : flux iCal par jeton révocable avec contenu minimal,
> recherche globale (palette de commandes), exports CSV/PDF/ZIP, e-mail « plan du mois ». »

---

## Lot 6 — Mise en service (≈ 1 session) — **à ne pas sauter**

- Déploiement : base PostgreSQL managée **UE**, back conteneurisé, front sur Vercel, domaine + HTTPS.
- Secrets générés (JWT, clé de chiffrement, hachage BCrypt) et **consignés dans un gestionnaire de mots de passe**.
- **2FA activée** si retenue.
- Sauvegarde quotidienne chiffrée hors serveur + **restauration réellement testée une fois**.
- Sentry configuré avec scrubbing ; alerte spécifique sur l'échec d'envoi de rappel.
- Reprise des données : import du tableur, création des sociétés, application des modèles d'année sociale.
- **Recette avec l'utilisatrice** : elle saisit un dossier réel, reçoit un rappel réel, imprime un plan du mois réel.
- Échéances internes créées dans l'outil : `INT_REVUE_REFERENTIEL` (annuelle), `INT_TEST_RESTAURATION` (semestrielle).

**Terminé quand** : elle utilise Dies sur ses vrais dossiers et a reçu au moins un brief du lundi.

---

## Lot 7 — Ultérieur (selon usage réel)

Suivi du temps par dossier · modèles de courriers et d'actes · statistiques d'activité · second
utilisateur · rapprochement avec le calendrier fiscal de l'expert-comptable · alertes sur indices
(ILC/ILAT) · veille sur les textes des règles marquées ⚠️ évolutif.

**Rien de tout cela ne se décide avant trois mois d'usage réel.**

---

## Ordre de priorité si le temps manque

| Rang | Élément | Pourquoi |
|---|---|---|
| 1 | Lots 0 → 3 | En dessous, l'outil ne remplace pas le tableur |
| 2 | Lot 6 (mise en service, sauvegardes) | Un outil non déployé et non sauvegardé ne sert à rien |
| 3 | Lot 4 (documents, journal) | Confort important, mais le tableur ne le faisait pas non plus |
| 4 | Lot 5 (iCal, exports) | Fort effet de levier, faible coût — à faire dès que possible |

---

## Journal de version

À tenir dans le `README` du dépôt `dies`, section « État actuel » : version, lot en cours, ce qui
fonctionne, ce qui reste, décisions prises. Incrémenter `package.json` et `pom.xml` à chaque session.

---

*PLAN-DEVELOPPEMENT.md v1.0 — mettre à jour l'état d'avancement à la fin de chaque lot.*
