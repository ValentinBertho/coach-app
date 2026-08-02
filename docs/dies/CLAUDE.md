# CLAUDE.md — Guide de collaboration IA ↔ Développeur (**Dies**)

> **À placer à la racine du dépôt `dies/` sous le nom `CLAUDE.md`.** C'est le fichier que Claude Code lit
> en premier : il doit suffire à comprendre le produit, les conventions et les pièges, sans lire le reste.
>
> Application web **privée, mono-utilisatrice** de suivi des dossiers et échéances juridiques.
> ADN technique : **Angular standalone + Spring Boot 3 / Java 21 + PostgreSQL + Liquibase**.
> Documents de référence : `docs/Cahier-des-charges.md`, `docs/Referentiel-juridique.md`,
> `docs/Techno.md`, `docs/Design.md`.

---

## 1. Le produit en six phrases

Dies suit les **dossiers juridiques** d'une juriste et surtout leurs **échéances datées** (tenir l'AG
d'approbation des comptes, déposer les comptes au greffe, dénoncer un bail, renouveler une marque,
respecter un délai d'appel). Il **calcule** ces dates à partir de règles de délai sourcées, les
**génère automatiquement** chaque année pour chaque société suivie, et **rappelle** par e-mail selon des
paliers. Il tient aussi son **agenda** — rendez-vous, audiences, assemblées — affiché dans le même calendrier que les échéances. Une seule personne s'y connecte, avec des identifiants en variables d'environnement. Les données
sont **confidentielles** : elles concernent des entreprises tierces. **Rien ne doit être perdu, rien ne
doit fuiter, aucun rappel ne doit être manqué ni envoyé deux fois.**

---

## 2. Les six invariants — à ne jamais casser

1. **Le `DeadlineEngine` est juste, pur et testé.** Aucune dépendance JPA/réseau, tous les cas de test du
   référentiel § 5 passent. Modifier le moteur sans ajouter de test = refusé.
2. **Report asymétrique** : un délai **pour agir** qui tombe un jour non ouvrable se reporte au jour
   ouvrable **suivant** (art. 642 CPC) ; un **préavis** se reporte au jour ouvrable **précédent**.
   L'inverser fait perdre un droit.
3. **Idempotence par contrainte de base**, pas par vérification applicative :
   `UNIQUE (entite_id, exercice, code_regle)` sur `echeance`, `UNIQUE (echeance_id, palier)` sur `rappel`.
4. **Toute échéance calculée explique son calcul** (`trace_calcul` persistée + base légale + date de
   vérification de la règle). Une date sans explication ne sera pas suivie par l'utilisatrice.
5. **Une échéance n'est pas un rendez-vous.** L'échéance est une **date limite** (`LocalDate`, calculée
   par le moteur, portée par une règle) ; l'événement est un **créneau horaire** (`timestamptz`, saisi à
   la main, avec lieu et participants). Deux entités, deux rendus visuels, un seul calendrier. Les
   fusionner ferait perdre le calcul de délai ; les cloisonner à l'écran ferait perdre l'usage.
6. **Aucune donnée de dossier hors du périmètre sécurisé** : ni dans les logs, ni dans Sentry, ni dans le
   flux iCal, ni dans le corps des e-mails de rappel (intitulé + référence uniquement).

---

## 3. Modèle de domaine

| Entité | Rôle | Statuts |
|---|---|---|
| `Entite` | Société suivie ; sa **date de clôture** pilote la génération annuelle | `ACTIVE / EN_SOMMEIL / RADIEE` |
| `Mandat` | Mandat social (dirigeant, CAC) avec date de fin | `EN_COURS / ECHU / RENOUVELE` |
| `Dossier` | Affaire, contrat, contentieux, opération | `OUVERT → EN_COURS → EN_ATTENTE_TIERS ⇄ SUSPENDU → CLOS → ARCHIVE` |
| `Echeance` | **Objet central** : date à tenir, avec règle, criticité, preuve | `A_FAIRE → EN_COURS → FAITE` · `SANS_OBJET` · `REPORTEE` |
| `RegleDelai` | Règle réutilisable du référentiel (formule + base légale + `verifieLe`) | `actif` |
| `ModeleProcedure` / `EtapeModele` | Checklist type générant des échéances | — |
| `Evenement` | **Rendez-vous** : créneau horaire, lieu, participants, récurrence, lié à un dossier et éventuellement à une échéance | `A_CONFIRMER → CONFIRME → TENU / ANNULE` |
| `NoteJour` | Bloc-notes daté (la page d'agenda) | — |
| `CalendrierExterne` | Abonnement **lecture seule** à un agenda Outlook/Google publié | `actif` |
| `Rappel` | Envoi planifié (échéance ou événement × palier) | `PLANIFIE → ENVOYE / ECHEC` |
| `Document` | Pièce jointe chiffrée (dont **preuve de réalisation**) | — |
| `Contact` | Avocat, greffe, CAC, expert-comptable, contrepartie | — |
| `EntreeJournal` | Main courante horodatée, verrouillée après 24 h | — |
| `JourChome` | Férié légal, Alsace-Moselle, congé personnel | — |
| `JournalAcces` | Connexions et actions sensibles | — |

**Enums clés** : `TypeDossier` · `TypeEvenement` (`RENDEZ_VOUS, AUDIENCE, ASSEMBLEE, REUNION, SIGNATURE, APPEL, DEPLACEMENT, FORMATION, RAPPEL_PERSONNEL, INDISPONIBILITE`) · `NatureEcheance` (`LEGALE, REGLEMENTAIRE, JUDICIAIRE, CONTRACTUELLE, FISCALE, INTERNE`) ·
`Criticite` (`BLOQUANTE, IMPORTANTE, CONFORT`) · `UniteDelai` (`JOURS_CALENDAIRES, JOURS_FRANCS, JOURS_OUVRABLES, JOURS_OUVRES, MOIS, ANNEES`) ·
`SensDelai` (`AVANT, APRES, AUCUN`) · `FormeJuridique` · `StatutEcheance` · `PalierRappel`.

---

## 4. Principes de développement

1. **Lisibilité avant performance.** Les volumes sont minuscules (quelques milliers de lignes) : jamais de complexité pour la vitesse.
2. **Pas d'over-engineering, pas de feature fantôme.** Une seule utilisatrice : pas de rôles, pas de multi-tenant, pas d'abstraction « au cas où ».
3. **Sécurité par défaut** : toute route authentifiée sauf `/auth/login`, `/auth/refresh`, `/actuator/health`, `/ical/{token}`.
4. **Migrations Liquibase uniquement** — jamais de DDL manuel, jamais de numéro de migration réutilisé.
5. **Transitions d'état validées en service**, jamais dans le controller, jamais côté front seul.
6. **Idempotence des effets de bord** : réserver en transaction, envoyer hors transaction, marquer le résultat.
7. **Le métier vit en base**, pas dans le code : règles de délai et modèles de procédure sont des données (`seed` Liquibase), éditables dans l'UI.
8. **Nommage anglais dans le code, libellés français dans l'UI.** Exception assumée : les noms d'entités du domaine juridique restent en français (`Echeance`, `Dossier`, `Entite`, `RegleDelai`) — traduire « échéance » en « deadline » et « dossier » en « case » ferait perdre la précision métier. **Choisir une fois, s'y tenir partout.**
9. **Dates** : `LocalDate` pour les échéances (une échéance est une date civile), `Instant` réservé aux délais en heures (72 h RGPD) et aux horodatages techniques. Fuseau `Europe/Paris` fixé au démarrage.

---

## 5. Conventions de code

### Backend (Java / Spring Boot)
- Entités héritent de `BaseEntity` (`id` UUID, `createdAt`, `updatedAt`) — ne jamais redéclarer.
- `@RequiredArgsConstructor` + champs `final` ; pas d'`@Autowired` sur champ.
- DTOs séparés `XxxRequest` / `XxxResponse` ; Request annotés `@JsonIgnoreProperties(ignoreUnknown = true)` + validation Jakarta.
- Controllers : `@RestController`, `@RequestMapping("/api/v1/<ressource>")`, `@Transactional(readOnly = true)` au niveau classe, `@Transactional` sur les mutations.
- Enums en `SCREAMING_SNAKE_CASE`, transitions explicites en service.
- Champs confidentiels : `@Convert(converter = EncryptedStringConverter.class)`. **Ne jamais filtrer en SQL sur un champ chiffré** (la requête porterait sur le chiffré).
- `@Slf4j` ; **jamais de dénomination sociale, d'intitulé de dossier, de note ni de jeton dans les logs.**
- Le `DeadlineEngine` reste sans dépendance Spring autre que `@Service` — il doit être instanciable dans un test unitaire nu.

### Frontend (Angular / TypeScript)
- **Tous** les composants `standalone: true`, dépendances dans `imports[]`, injection via `inject()`.
- Routing lazy `loadComponent` partout + `authGuard` fonctionnel.
- `ReactiveFormsModule` pour les formulaires structurés, `FormsModule` pour les filtres.
- Pas de state manager global ; chargement des données via services `core/services/`.
- `ToastService` sur toute action ; `ConfirmDialogService` au lieu de `confirm()`.
- SCSS par composant + tokens globaux de `Design.md` — **aucune couleur en dur**.
- **Aucun calcul de délai côté front.** Le front affiche la date et la trace fournies par l'API. Un calcul dupliqué finirait par diverger.
- Dates via `date-fns` locale `fr`, affichage `JJ/MM/AAAA` + jour de la semaine sur les échéances.

### Nommage
| Élément | Convention | Exemple |
|---|---|---|
| Entité JPA | PascalCase, domaine en français | `Echeance`, `RegleDelai`, `ModeleProcedure` |
| DTO | `XxxRequest` / `XxxResponse` | `EcheanceRequest` |
| Enum | SCREAMING_SNAKE_CASE | `StatutEcheance.FAITE` |
| Service Angular | `xxx.service.ts` dans `core/services/` | `echeance.service.ts` |
| Composant | `features/xxx/` | `liste-du-mois.component.ts` |
| Migration | `NNN-description.yaml` | `007-modeles-procedure.yaml` |
| Code de règle | SCREAMING_SNAKE_CASE stable, **jamais renommé** | `SOC_DEPOT_ELEC` |

---

## 6. Patterns récurrents

- **Calcul puis persistance de la trace** : `DeadlineEngine` renvoie `(date, trace)` ; les deux sont stockés sur l'échéance. Un recalcul ne réécrit **jamais** une échéance dont `date_ajustee = true`.
- **Application d'un modèle en deux temps** : `POST /modeles-procedure/{id}/appliquer?apercu=true` renvoie les échéances proposées ; l'utilisatrice ajuste ; un second appel crée. **Jamais de création silencieuse en masse.**
- **Génération annuelle** : job quotidien → pour chaque entité dont l'exercice vient de clore, applique `MOD_ANNEE_SOCIALE`, protégé par la contrainte `UNIQUE` (une violation attendue est traitée comme un succès, pas comme une erreur).
- **Planification des rappels** : à la création ou au déplacement d'une échéance, les `Rappel` des paliers futurs sont (re)générés ; ceux déjà `ENVOYE` ne sont jamais recréés.
- **Regroupement des envois** : un seul e-mail par déclenchement, listant toutes les échéances concernées, trié par urgence puis criticité.
- **Brief hebdomadaire même vide** : preuve hebdomadaire que la chaîne d'alerte fonctionne.
- **Récurrence calculée, jamais matérialisée** : on stocke la `rrule` et on développe les occurrences sur la fenêtre affichée (24 mois maximum). Les occurrences déplacées ou annulées vivent dans `evenement_exception`. **Ne jamais créer 500 lignes pour un rendez-vous hebdomadaire.**
- **Événement tenu ⇒ échéance faite** : `POST /evenements/{id}/tenu` met à jour l'événement **et** l'échéance liée dans une seule transaction, puis déclenche les échéances qui en dépendent (PV, dépôt).
- **Heure locale d'abord** : les occurrences récurrentes se calculent en `Europe/Paris` puis se convertissent — un rendez-vous hebdomadaire à 14 h reste à 14 h après le changement d'heure.
- **Calendrier externe en lecture seule** : on récupère un `.ics` publié (délai d'attente court, taille plafonnée, dédup par `uid`), on l'affiche grisé, **on n'y écrit jamais**. L'URL est un secret : chiffrée, jamais journalisée.
- **Pré-remplissage par queryParams** : `?entiteId=…&exercice=…&regle=SOC_DEPOT_ELEC`.
- **Recherche** : `ILIKE` sur les champs non chiffrés (référence, intitulé, dénomination) ; les champs chiffrés ne sont **pas** cherchables — c'est un arbitrage assumé, à rappeler dans l'UI.

---

## 7. Bonnes pratiques ✅ / Anti-patterns ❌

✅ Une migration Liquibase par changement, incluse au master.
✅ Un test unitaire pour toute règle de calcul, avec la date attendue écrite en dur.
✅ Base légale + `verifieLe` renseignés sur chaque règle créée.
✅ Contrainte `UNIQUE` avant garde applicative pour toute idempotence.
✅ Motif obligatoire sur report et `SANS_OBJET`.
✅ Toast sur chaque action ; libellés FR ; vocabulaire juridique exact.
✅ Incrémenter la version (`package.json` + `pom.xml`) à chaque session et tenir à jour « État actuel ».

❌ Écrire une règle de délai en dur dans le code Java ou TypeScript.
❌ Calculer une date côté front.
❌ Utiliser `plusMonths` sans gérer le quantième inexistant (31 janvier + 1 mois).
❌ Appliquer le report « jour ouvrable suivant » à un préavis.
❌ Envoyer un rappel sans clé d'idempotence.
❌ Écraser une date ajustée manuellement lors d'un recalcul.
❌ Matérialiser les occurrences d'un événement récurrent en lignes de base.
❌ Afficher une échéance dans la grille horaire d'un agenda (elle n'a pas d'heure) ou un rendez-vous dans le bandeau « toute la journée » (il en a une).
❌ Écrire dans un calendrier externe, ou brancher Microsoft Graph / Google Calendar API en v1.
❌ Logguer une dénomination sociale, un intitulé de dossier ou un contenu de note.
❌ Ajouter une route publique « juste pour tester ».
❌ Mettre le mot de passe en clair dans une variable d'environnement (c'est un **hachage BCrypt** qu'on stocke).
❌ Ajouter une dépendance npm/Maven sans le signaler explicitement.

---

## 8. Développer une nouvelle fonctionnalité (full-stack)

1. **Migration** : `NNN-description.yaml` (+ `seed` si le référentiel change) et include au master.
2. **Entité** : hérite de `BaseEntity` ; champs confidentiels convertis en chiffré.
3. **DTOs** : `XxxRequest` validé + `XxxResponse`.
4. **Service** : métier, transitions d'état, appel au `DeadlineEngine`, effets de bord idempotents.
5. **Controller** : route `/api/v1/…`, `@Valid`, codes d'erreur normalisés.
6. **Tests** : unitaires sur le calcul et les transitions ; intégration sur l'idempotence.
7. **Front** : `xxx.model.ts` → `xxx.service.ts` → composants standalone `liste / detail / form`, route lazy.
8. **UX** : toasts, skeletons, `empty-state`, badges d'urgence et de criticité, responsive ≤ 600 px, impression si la vue mois est concernée.
9. **Versionner** + mettre à jour « État actuel » dans le README du dépôt.

---

## 9. Workflow attendu de Claude Code

1. **Lire le contexte** : ce fichier + `Referentiel-juridique.md` (le métier) + `Techno.md` + `Design.md`.
2. **Explorer avant d'écrire** : copier les conventions d'un module voisin déjà livré.
3. **Concis** : code d'abord, explication courte ensuite, pas de préambule.
4. **Signaler sans forcer** les risques (sécurité, justesse d'un délai, dette) avec **une** alternative si elle est nettement supérieure.
5. **Implémenter le périmètre exact** demandé, dans l'ordre du § 8 — ni plus, ni moins.
6. **Référencer le code** par `chemin/fichier.ts:ligne`.
7. **Vérifier avant de dire « fait »** : `npm run build` (typecheck AOT) + `mvn verify` (dont les tests du `DeadlineEngine`) + démarrage réel pour valider les migrations.
8. **Git** : branche dédiée, commits clairs en français, push/PR uniquement sur demande.

### Ce qui doit déclencher une question plutôt qu'une supposition
- Une **règle de délai** dont la base légale n'est pas certaine → demander, ne pas inventer un article.
- Un **choix de périmètre** qui ouvre le multi-utilisateur, une route publique ou un partage externe.
- Toute **dérogation** à un des six invariants du § 2.

---

## 10. Premières fonctionnalités (rappel du découpage)

1. Socle : projet, CI, Liquibase, authentification par variables d'environnement, layout, design tokens.
2. Dossiers + échéances manuelles + tableau de bord + vue liste.
3. `DeadlineEngine` + référentiel de règles + sociétés + modèles de procédure + génération annuelle.
4. Rappels e-mail + brief du lundi + vue mois / liste du mois + import du tableur existant.
5. **Agenda** : événements, vues jour/semaine/mois unifiées, récurrence, notes de journée, calque externe.
6. Documents chiffrés + preuve de réalisation + journal + contacts.
7. iCal, recherche globale, exports PDF/CSV, push PWA.

Détail exécutable : `docs/PLAN-DEVELOPPEMENT.md`.

---

*Application privée mono-utilisatrice — la simplicité est une exigence de sécurité, pas une facilité.
Chaque brique non construite est une vulnérabilité en moins.*
