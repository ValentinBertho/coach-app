# Documentation DARI Lab — index

> **À lire en premier si vous arrivez sur le dépôt, humain ou agent.** Ce dossier mélangeait des
> documents vivants, des audits datés et des spécifications d'une stack qui n'a jamais été
> retenue. Ce tableau dit lequel fait foi, et sur quoi.

---

## Documents vivants — ils font foi

| Document | Ce qu'il tranche | Quand le mettre à jour |
|---|---|---|
| [`Cahier-des-charges.md`](./Cahier-des-charges.md) | Le **périmètre fonctionnel** promis, avec priorisation MoSCoW. Référentiel de complétude. | À chaque arbitrage de périmètre |
| [`Techno.md`](./Techno.md) | La **référence technique** : stack, architecture, conventions d'API | À chaque décision structurante |
| [`Design.md`](./Design.md) | Le **design system** : tokens, composants, états d'interface, règles d'accessibilité | À chaque évolution du système visuel |
| [`Claude.md`](./Claude.md) | Les **conventions de code**, pour les humains comme pour les agents | Quand une convention change |
| [`DEPLOIEMENT.md`](./DEPLOIEMENT.md) | Le déploiement Railway / Vercel et les variables d'environnement | À chaque changement d'infra |
| [`OPERATIONS.md`](./OPERATIONS.md) | L'**exploitation** : Sentry, sauvegardes, Actuator, CI | À chaque changement de procédure |
| [`BETA-LAUNCH-RUNBOOK.md`](./BETA-LAUNCH-RUNBOOK.md) | La **checklist de mise en service** de la bêta, pas à pas | Pendant la mise en service |

## Audits — datés, donc à lire avec leur date

Un audit n'est pas une spécification : il photographie un état à une date. Le plus récent prime
sur les précédents quand ils se contredisent.

| Document | Angle | État |
|---|---|---|
| [`PLAN-CONFORMITE-BETA-2026-08.md`](./PLAN-CONFORMITE-BETA-2026-08.md) | **Document de pilotage.** Synthèse des audits en plan d'action : 4 vagues, check-lists légale/RGPD et opérationnelle, ce qu'on livre sciemment imparfait, points non instruits | 🟡 GO conditionnel — ≈ 12,5 j avant ouverture |
| [`AUDIT-FONCTIONNEL-2026-08.md`](./AUDIT-FONCTIONNEL-2026-08.md) | Audit **métier** : trois mésocycles déroulés côté responsable de club, coach assistant et athlète. RPE prescrit faux sans test lactate, forme périmée, blessure ignorée par les alertes, charge calculée sur la durée prescrite | 🔴 5 bloquants + 10 points gênants, aucun correctif appliqué |
| [`AUDIT-BETA-OUVERTE-2026-08.md`](./AUDIT-BETA-OUVERTE-2026-08.md) | Second passage de bêta ouverte, builds exécutés : consentement santé branché sur un seul service, retrait incomplet, correctifs d'août appliqués à moitié (plafond SSE, plafond e-mail), autorisations club | 🔴 4 points à traiter avant d'ouvrir + 2 actions d'exploitation. ⚠️ Contredit l'audit technique d'août §3.1 et §3.2 |
| [`AUDIT-TECHNIQUE-2026-08.md`](./AUDIT-TECHNIQUE-2026-08.md) | Ce que les audits de juillet n'ont pas vu : chemin push devenu chemin chaud, consentement santé traité comme une case et non comme un état, back-office inatteignable faute d'administrateur, gestion d'erreurs | 🔵 Correctifs livrés. Restent 3 actions d'exploitation : compte admin, test de restauration, identité de l'éditeur |
| [`AUDIT-BETA-OUVERTE-2026-07.md`](./AUDIT-BETA-OUVERTE-2026-07.md) | Parcours coach et athlète face à une bêta ouverte : complétude, ergonomie, design, accessibilité, réglages, politique de notification | 🔵 Correctifs bloquants livrés — voir son plan d'exécution pour le reste |
| [`AUDIT-BETA-READINESS-2026-07.md`](./AUDIT-BETA-READINESS-2026-07.md) | **Exploitation** : infra, sauvegardes, monitoring, sécurité, RGPD | 🔵 En cours — suivre le runbook. ⚠️ Son §5 sous-estimait le volume d'e-mails (corrigé par l'audit de bêta ouverte §6.1) et son §4 annonce un TTL de jeton de 900 s, en réalité 3600 (cf. audit technique §1) |
| [`AUDIT-RC-2026-07.md`](./AUDIT-RC-2026-07.md) | **Code** : les lots 1 à 8 livrés avant la bêta | ✅ Lots 1-8 livrés. Lot 9 (comparaison répétition par répétition) après la bêta |
| [`archive/audit-produit-dari-lab.md`](./archive/audit-produit-dari-lab.md) | Produit et logique métier, écran par écran | 🟡 Historique — largement traité depuis |
| [`archive/audit-ui-ux-dari-lab.md`](./archive/audit-ui-ux-dari-lab.md) | UI / UX / design, écran par écran | 🟡 Historique — son §8 tient le suivi d'exécution |

## Analyses de marché — datées, elles aussi

| Document | Angle | État |
|---|---|---|
| [`ANALYSE-CONCURRENTIELLE-NOLIO-2026-08.md`](./ANALYSE-CONCURRENTIELLE-NOLIO-2026-08.md) | **Concurrence** : comparaison à Nolio (fonctionnalités, UX, UI, ergonomie), verdict de concurrence, maturité estimée, feuille de route priorisée en 4 vagues | 🟡 Août 2026. ⚠️ Le volet Nolio repose sur de la recherche web, **pas sur une prise en main** — `nolio.io` est inaccessible depuis l'environnement d'analyse. À revalider par un essai réel |

## Archive — historique, plus maintenu

`archive/` contient ce qui a servi à construire l'application et qu'on garde pour la traçabilité,
**sans le tenir à jour**. Ne vous en servez pas comme référence de l'état actuel.

| Document | Pourquoi il est là |
|---|---|
| [`archive/ux-redesign-blueprint.md`](./archive/ux-redesign-blueprint.md) | La cible UX d'origine. Largement livrée ; quelques composants la citent encore en commentaire |
| [`archive/PLAN-IMPLEMENTATION.md`](./archive/PLAN-IMPLEMENTATION.md) | Le plan de portage du cahier des charges DARI Lab sur le socle Angular/Spring. Exécuté |
| [`archive/DARI Lab Cahier des Charges.md`](./archive/DARI%20Lab%20Cahier%20des%20Charges.md) | Cahier des charges d'origine. **Sa partie fonctionnelle reste une bonne source ; sa partie technique décrit Next.js + Supabase et ne s'applique pas** |
| [`archive/DARI Lab Training Architecture.md`](./archive/DARI%20Lab%20Training%20Architecture.md) | Architecture d'origine (42 tables SQL, moteurs de calcul, parcours). Même réserve : le schéma a été porté sur Liquibase/JPA, pas repris tel quel |
| [`archive/dari-lab-wireframes.html`](./archive/dari-lab-wireframes.html) | Les 18 wireframes d'origine. Tous les écrans existent aujourd'hui |

---

## Ce qui a été supprimé, et pourquoi

Deux documents décrivaient les **contrats d'une stack jamais retenue** (Supabase : enveloppes
`{ data, error }`, policies RLS, types TypeScript miroir du schéma SQL). Ils induisaient en erreur
quiconque — humain ou agent — les lisait comme la référence d'API du projet, alors que
l'application expose une API REST Spring Boot et applique ses autorisations dans les services
(`ClubAccessValidator` / `AthleteAccessValidator`), pas en RLS Postgres.

- `Darilab/DARI Lab API et RLS.md`
- `Darilab/dari-types.ts`

La décision de ne pas les suivre était déjà tracée dans `archive/PLAN-IMPLEMENTATION.md` §0
(décisions D2, D3, D4) ; les fichiers, eux, étaient restés.

---

## Où trouver la documentation *utilisateur*

Elle n'est pas ici : elle vit **dans l'application**, par rôle —
athlète `/athlete/help`, coach `/app/aide`, admin `/admin/aide`. Le contenu est éditable sans
toucher au rendu dans
[`front/src/app/features/help/help-content.ts`](../front/src/app/features/help/help-content.ts).
