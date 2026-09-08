# Documentation DARI Lab — index

> **À lire en premier si vous arrivez sur le dépôt, humain ou agent.** Ce tableau dit quel document
> fait foi, et sur quoi.
>
> **Règle de ce dossier** : un audit clos est **retiré**, pas conservé. L'historique git garde son
> texte ; le laisser ici ferait croire qu'il décrit encore l'état du produit. C'est l'erreur que cet
> index a lui-même commise pendant plusieurs semaines (cf. la note en fin de page).

---

## Documents vivants — ils font foi

| Document | Ce qu'il tranche | Quand le mettre à jour |
|---|---|---|
| [`Cahier-des-charges.md`](./Cahier-des-charges.md) | Le **périmètre fonctionnel** promis, avec priorisation MoSCoW. Référentiel de complétude. | À chaque arbitrage de périmètre |
| [`Techno.md`](./Techno.md) | La **référence technique** : stack, architecture, conventions d'API | À chaque décision structurante |
| [`Design.md`](./Design.md) | Le **design system** : tokens, composants, états d'interface, règles d'accessibilité | À chaque évolution du système visuel |
| [`Claude.md`](./Claude.md) | Les **conventions de code**, pour les humains comme pour les agents | Quand une convention change |
| [`DEPLOIEMENT.md`](./DEPLOIEMENT.md) | Le déploiement Railway / Vercel et les variables d'environnement | À chaque changement d'infra |
| [`DEBUG.md`](./DEBUG.md) | Le **débogage au quotidien** : points d'arrêt en Docker (JDWP 5005), IntelliJ, VS Code, niveaux de journaux, `correlationId` | Quand l'outillage de développement change |
| [`OPERATIONS.md`](./OPERATIONS.md) | L'**exploitation** : Sentry, sauvegardes, Actuator, CI | À chaque changement de procédure |
| [`BETA-LAUNCH-RUNBOOK.md`](./BETA-LAUNCH-RUNBOOK.md) | La **checklist de mise en service** de la bêta, pas à pas | Pendant la mise en service |

## Plans — ce qu'on a décidé de faire, et dans quel ordre

| Document | Angle | État |
|---|---|---|
| [`PLAN-CONFORMITE-BETA-2026-08.md`](./PLAN-CONFORMITE-BETA-2026-08.md) | **Document de pilotage avant ouverture.** Synthèse des audits en plan d'action : vagues, check-lists légale/RGPD et opérationnelle, ce qu'on livre sciemment imparfait. C'est lui qui **porte le reliquat ouvert** des audits retirés — vagues 0 à 3, points non creusés (§6) | 🟡 Les items de **code** des vagues 0, 1 et 2 sont livrés. Restent cinq points qui ne sont pas du développement : identité civile de l'éditeur, sauvegardes **testées**, compte administrateur en production, DSN Sentry, relecture juridique. La vague 3 reste ouverte |
| [`PLAN-EVOLUTION-2026-08.md`](./PLAN-EVOLUTION-2026-08.md) | **Post-bêta, vu du marché** : construit sur le tableau public de demandes de Nolio (~60 demandes votées) | 🔵 Plan. Sa vague 1 est en grande partie couverte par la couche de décision (détection physio, score de réalisation, prédiction de performance) |
| [`PLAN-PRODUIT-2026-08.md`](./PLAN-PRODUIT-2026-08.md) | **Post-bêta, vu du modèle de domaine** : ce que la structure actuelle empêche ou rend pénible | 🟡 Livrés : jeu de départ à l'inscription (§1.3), « vu 👏 » du coach (§2.1), bilan hebdomadaire (§2.2), plan depuis l'objectif (§1.4). Ouverts : l'heure et le lieu d'une séance (§1.1), le club comme collectif (§1.2), la facturation (§1.5), poids et FC de repos (§1.6) |

## Audits encore ouverts — datés, donc à lire avec leur date

Un audit n'est pas une spécification : il photographie un état à une date. Le plus récent prime
sur les précédents quand ils se contredisent. **Seuls figurent ici les audits dont une partie
reste à faire** ; les autres ont été retirés (voir plus bas).

| Document | Angle | Ce qui reste ouvert |
|---|---|---|
| [`AUDIT-COACH-INDEPENDANT-2026-08.md`](./AUDIT-COACH-INDEPENDANT-2026-08.md) | **Le produit est-il prêt pour tous les coachs ?** Le mélange privé / club est déjà supporté nativement ; on parle de « club » partout à un coach qui n'en a pas | 🟡 Le coach membre de deux clubs se pilote depuis la fiche compte du back-office (cf. `AUDIT-ADMIN`) ; côté coach, l'interface n'exploite toujours pas le modèle multi-club |
| [`AUDIT-ADMIN-2026-08.md`](./AUDIT-ADMIN-2026-08.md) | **Back-office `/admin`** : passer d'une succession d'écrans CRUD à un centre de pilotage utilisable sans ouvrir psql | 🟡 P0 et P1 livrés. Ouverts : le **P2 de §3** (export CSV, fusion Athlètes/Invitations, rétention du journal d'audit, suspension d'un club propagée aux coachs) et le constat hors périmètre de **§4** — le jeton CSS `--line` n'est défini nulle part et reste utilisé par 8 composants |

## Analyses de marché — datées, elles aussi

| Document | Angle | État |
|---|---|---|
| [`ANALYSE-CONCURRENTIELLE-NOLIO-2026-08.md`](./ANALYSE-CONCURRENTIELLE-NOLIO-2026-08.md) | **Concurrence** : comparaison à Nolio (fonctionnalités, UX, UI, ergonomie), verdict, maturité estimée, feuille de route | 🟡 Août 2026. ⚠️ Le volet Nolio repose sur de la recherche web, **pas sur une prise en main** — `nolio.io` est inaccessible depuis l'environnement d'analyse. À revalider par un essai réel |
| [`DEMANDES-API-GARMIN-COROS.md`](./DEMANDES-API-GARMIN-COROS.md) | Les deux dossiers d'accès aux API de montres, et ce qu'ils exigent | 🔴 Contrainte externe : le programme développeur **Garmin est fermé** (ni liste d'attente ni date) ; **COROS est ouvert**, c'est la seule porte praticable |

## Archive — historique, plus maintenu

`archive/` ne garde que ce qui est encore **cité depuis le code**. Ne vous en servez pas comme
référence de l'état actuel.

| Document | Pourquoi il est là |
|---|---|
| [`archive/ux-redesign-blueprint.md`](./archive/ux-redesign-blueprint.md) | La cible UX d'origine. Largement livrée, mais **trois fichiers front la citent encore en commentaire** (`shared/components/physiology.ts`, `features/dev/ui-kit.component.ts`, `app.routes.ts`) : la retirer rendrait ces renvois muets |

---

## Ce qui a été retiré, et pourquoi

**Des spécifications d'une stack jamais retenue.** Elles décrivaient Next.js + Supabase —
enveloppes `{ data, error }`, policies RLS, types TypeScript miroirs du schéma SQL — alors que
l'application expose une API REST Spring Boot et applique ses autorisations dans les services
(`ClubAccessValidator` / `AthleteAccessValidator`). Elles induisaient en erreur quiconque, humain
ou agent, les lisait comme la référence du projet.

- `Darilab/DARI Lab API et RLS.md` · `Darilab/dari-types.ts`
- `archive/DARI Lab Cahier des Charges.md` · `archive/DARI Lab Training Architecture.md`
- `archive/PLAN-IMPLEMENTATION.md` — le plan de portage vers Angular/Spring, exécuté. C'est lui qui
  traçait la décision de ne pas suivre les documents ci-dessus (§0, décisions D2 à D4)

**Des audits clos.** Leurs constats sont livrés, et leur contenu vit désormais dans le code et
dans les documents de pilotage ci-dessus.

*Juillet 2026 :*

- `AUDIT-RC-2026-07.md` — lots 1 à 8 livrés avant la bêta ; le lot 9, la comparaison répétition par
  répétition, est livré depuis (score de réalisation bloc par bloc)
- `AUDIT-BETA-OUVERTE-2026-07.md` — remplacé par le second passage d'août, retiré à son tour
- `AUDIT-BETA-READINESS-2026-07.md` — son reliquat vivant est tenu par `PLAN-CONFORMITE-BETA` et
  `BETA-LAUNCH-RUNBOOK` ; il portait par ailleurs deux chiffres faux (volume d'e-mails, TTL de jeton)
- `AUDIT-NOTIFICATIONS-2026-08.md` et `PLAN-NOTIFICATIONS-2026-08.md` — les lots sont en place :
  une notification unique par plan attribué, heures calmes et catégories muettes, purge des
  abonnements caducs, bilan hebdomadaire et série de retours
- `archive/audit-produit-dari-lab.md` et `archive/audit-ui-ux-dari-lab.md` — les deux audits de
  juillet, écran par écran. Traités ; ils ne décrivaient plus aucune interface existante
- `archive/dari-lab-wireframes.html` — les 18 wireframes d'origine. Tous les écrans existent

*Août 2026 :*

- `AUDIT-PRODUIT-WAHOU-2026-08.md` — les **dix** évolutions de la couche de décision sont livrées.
  Elles se lisent aujourd'hui dans les moteurs : `PhysioDetectionEngine`, `ComplianceEngine`,
  `TrajectoryEngine`, `SessionAdaptationEngine`, `PlanBuilderEngine`, `WeekOutlookEngine`. La
  contrainte qui les gouverne — **le produit ne modifie jamais une séance tout seul** — est
  reprise dans `Techno.md`
- `AUDIT-PWA-COACH-2026-08.md` — les trois vagues livrées : coquille mobile, écran « Ma journée »
  (`features/dashboard/coach-day.component.*`), notifications actionnables, vue Jour, lecture
  hors ligne
- `AUDIT-FONCTIONNEL-2026-08.md` — les 5 bloquants livrés (RPE dérivé de la prescription, forme
  périmée `STALE`, indisponibilité qui éteint les alertes, charge sur la durée réelle, motif de
  séance non faite) ; G5 levée par le score de réalisation bloc par bloc. Ses items 🟠 et 🟢
  survivent dans `PLAN-CONFORMITE-BETA` (vagues 1 à 3)
- `AUDIT-BETA-OUVERTE-2026-08.md` et `AUDIT-TECHNIQUE-2026-08.md` — correctifs livrés et couverts
  par des tests. Leur reliquat n'était **que** de l'exploitation et de la vague 3 : il est repris
  ligne à ligne par `PLAN-CONFORMITE-BETA` (V0-09 à V0-11, V1-09, vague 3) et par
  `BETA-LAUNCH-RUNBOOK`

Tout cela reste lisible dans l'historique git : `git log --diff-filter=D -- docs/`.

---

## Où trouver la documentation *utilisateur*

Elle n'est pas ici : elle vit **dans l'application**, par rôle —
athlète `/athlete/help`, coach `/app/aide`, admin `/admin/aide`. Le contenu est éditable sans
toucher au rendu dans
[`front/src/app/features/help/help-content.ts`](../front/src/app/features/help/help-content.ts).
