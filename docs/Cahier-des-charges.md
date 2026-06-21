# Cahier des charges — *(ébauche)*

## Plateforme SaaS de coaching pour la course à pied (alternative à Nolio)

> **Statut : ébauche v0.1 — à valider et compléter avec le coach.**
> Les éléments marqués _(à valider)_ sont des hypothèses de travail. La priorisation suit la méthode
> **MoSCoW** : **M** = indispensable MVP · **S** = important · **C** = confort · **W** = ultérieur.

---

# 1. Contexte & objectifs

## 1.1 Contexte

Les coachs de course à pied (indépendants et clubs) gèrent aujourd'hui l'entraînement de leurs athlètes
avec des outils dispersés (tableurs, Google Agenda, messageries, Strava) ou des plateformes existantes
(**Nolio**, TrainingPeaks, Garmin) jugées **trop chères, complexes ou peu ergonomiques** pour un usage
quotidien, en particulier sur mobile.

Le projet vise une plateforme **SaaS moderne, simple, mobile-first et orientée terrain**, permettant au
coach de **prescrire des plans d'entraînement**, à l'athlète de **suivre ses séances** (synchronisées
depuis sa montre GPS), et aux deux de **communiquer** et **piloter la performance** — pour un coach
indépendant comme pour un club multi-coachs.

## 1.2 Objectifs

### Objectifs produit
- Simplifier la **prescription** et le **suivi** de l'entraînement.
- **Centraliser** plan, activités réalisées, ressenti et communication au même endroit.
- Offrir une **expérience athlète mobile** soignée (PWA).
- Donner au coach une **vision claire de la charge et de la progression**.
- Être utilisable par un **coach solo** comme par un **club** (plusieurs coachs, groupes).

### Objectifs business
- Produit SaaS **monétisable** (abonnement coach et/ou par athlète).
- **Déploiement rapide**, base technique solide pour montée en charge progressive.
- Différenciation : **simplicité + prix + UX mobile** face à Nolio/TrainingPeaks.

---

# 2. Périmètre fonctionnel

## 2.1 Typologie des utilisateurs (rôles)

| Rôle | Description | Accès |
|---|---|---|
| **Administrateur plateforme** | Supervision globale, support, gestion des clubs/abonnements | Back-office admin |
| **Responsable de club / Head coach** | Paramètre le club, gère coachs, groupes, facturation | Back-office club |
| **Coach** | Prescrit et suit ses athlètes, construit plans et séances | Back-office coach |
| **Athlète** | Consulte son plan, logue séances & ressenti, communique | App athlète (PWA, sans mot de passe via lien) |

> **Modèle « coach solo »** _(à valider)_ : un coach indépendant = un « club implicite » (1 club, 1 coach).
> Cela uniformise le cloisonnement des données et évite un cas particulier dans tout le code.

## 2.2 Hors périmètre (à ce stade)
- Plans nutritionnels détaillés / suivi diététique (W).
- Multi-sport avancé (triathlon, cyclisme natif) — l'architecture le prévoit, mais focus **course à pied** d'abord.
- Réseau social / feed public type Strava (W).
- Application mobile native (iOS/Android store) — on commence en **PWA** (W pour le natif).

---

# 3. Fonctionnalités détaillées

## 3.1 Gestion des athlètes (M)
- Création / modification / archivage d'un athlète.
- **Invitation par lien** (onboarding sans mot de passe, type lien magique).
- **Profil sportif** : date de naissance, sexe, niveau, antécédents/blessures _(donnée de santé)_, **données physiologiques** : FC max, FC repos, VMA, allures/zones, seuils (FC/allure/puissance), poids.
- Objectifs et **course(s) cible(s)** rattachées.
- Rattachement à un ou plusieurs **groupes d'entraînement** (club).
- Recherche / filtres (groupe, statut, prochaine course).

## 3.2 Plans & séances d'entraînement (M)
### 3.2.1 Calendrier d'entraînement (M)
- Vue **semaine** et **mois**, par athlète ou par groupe.
- **Glisser-déposer** pour replanifier une séance (desktop) ; appui long (mobile).
- Total **volume hebdomadaire** (durée / distance) et répartition par type/zone.

### 3.2.2 Séance structurée (M)
- Type : endurance, récupération, tempo, seuil, intervalles (VMA), sortie longue, course, renfo, repos.
- **Étapes structurées** : échauffement / répétitions ×N / récup / retour au calme, avec **cible par zone** (FC, allure ou %VMA) et durée/distance.
- Consignes texte, durée/distance totale estimée.
- Statuts : **prévu → réalisé / partiel / manqué**.

### 3.2.3 Bibliothèque de séances (S)
- Modèles de séances réutilisables, applicables en un clic au calendrier.
- Partage des modèles au sein du club (S).

### 3.2.4 Plans périodisés (S)
- Construction d'un **plan sur N semaines** (méso/microcycles : base, spécifique, affûtage).
- **Modèles de plans** par objectif (10 km, semi, marathon, trail) (C).
- Application d'un plan à un athlète avec **calage sur la date de course** (C).

## 3.3 Suivi du réalisé & intégrations (M)
- **Connexion Strava** (OAuth) : import automatique des activités (M).
- **Garmin / Coros / Polar** (OAuth ou import) (S).
- **Import manuel** de fichiers **FIT / GPX / TCX** (S).
- **Rapprochement prévu ↔ réalisé** : association séance planifiée / activité importée, avec écart (distance, temps, allure) (M).
- **Détail d'activité** : distance, temps, allure moyenne/par km, FC, dénivelé, **temps passé par zone**, tracé GPS (C).
- **Ressenti athlète (RPE)** + commentaire sur la séance réalisée (M).

## 3.4 Performance & charge (S)
- **Charge d'entraînement** : charge aiguë / chronique / forme (type CTL/ATL/TSB) _(formules à valider)_.
- Graphiques : **volume hebdo**, **répartition par zone**, évolution des allures/FC.
- **Alertes** : surcharge, sous-charge, athlète sans activité depuis X jours (S).
- Records personnels / meilleures performances (C).

## 3.5 Journal de bien-être / wellness (C)
- Saisie quotidienne athlète : sommeil, fatigue, courbatures, humeur, FC repos, **HRV**, poids _(données de santé)_.
- Visualisation côté coach + corrélation avec la charge (C).

## 3.6 Communication (S)
- **Messagerie** coach ↔ athlète (globale + par séance).
- Commentaires/feedback du coach sur une séance réalisée (S).
- **Notifications** push + email : plan publié, séance commentée, rappel séance du lendemain, objectif J-7, athlète inactif (S).

## 3.7 Courses & objectifs (S)
- Création d'une **course cible** : date, distance, dénivelé, **chrono visé**, priorité (A/B/C).
- **Compte à rebours** (J-XX) côté athlète.
- Bilan post-course (réalisé vs visé) (C).

## 3.8 Facturation & abonnements (C)
- Gestion des **forfaits de coaching privé** (mensuel/trimestriel) avec paiement en ligne _(Stripe à valider)_.
- Suivi des paiements, relances, statut d'abonnement.
- _(Distinct de l'abonnement SaaS du coach à la plateforme — cf. § 10.)_

## 3.9 Administration & paramétrage (M pour le club / S pour l'admin plateforme)
- Paramétrage club : coachs, groupes, logo, modules activés.
- **Modules activables** : Entraînement, Performance, Wellness, Communication, Facturation, Intégrations.
- Back-office admin plateforme : clubs, abonnements SaaS, support, supervision.
- **RGPD** : consentements, export des données, suppression de compte.

---

# 4. Parcours utilisateurs clés

### 4.1 Onboarding athlète (M)
1. Le coach crée l'athlète et envoie une **invitation par lien**.
2. L'athlète ouvre le lien, complète son profil et **connecte Strava**.
3. Il voit immédiatement **sa séance du jour**.

### 4.2 Prescription d'une semaine (M)
1. Le coach ouvre le **calendrier** de l'athlète.
2. Il glisse une séance depuis la **bibliothèque** ou en crée une **structurée**.
3. Il **publie** → l'athlète est notifié.

### 4.3 Boucle quotidienne athlète (M)
1. L'athlète consulte la **séance du jour** (mobile).
2. Il la réalise ; son activité **se synchronise** (Strava) et **se rapproche** automatiquement de la séance prévue.
3. Il saisit son **RPE** + un commentaire.
4. Le coach voit le **prévu vs réalisé** et commente.

### 4.4 Pilotage hebdomadaire coach (S)
1. Vue d'ensemble : athlètes, **charge**, séances à valider, **alertes**.
2. Ajustement des plans selon le ressenti et la charge.

---

# 5. Exigences non-fonctionnelles

## 5.1 Performance & disponibilité
- Chargement des écrans clés < 2 s sur mobile 4G _(cible à valider)_.
- Pagination serveur sur les listes ; agrégation des données de graphes côté backend.
- Disponibilité visée **99,5 %** _(à valider)_, sauvegardes DB automatiques.

## 5.2 Sécurité
- Authentification **JWT** (access court + refresh), **lien magique** pour les athlètes.
- **Gestion fine des rôles** et **cloisonnement strict des données** par club/coach (un coach ne voit que ses athlètes).
- **Chiffrement au repos** des données sensibles (données physiologiques, tokens d'intégration).
- Headers de sécurité (CSP, HSTS…), **rate limiting**, vérification de signature des webhooks externes.

## 5.3 Conformité RGPD (renforcée — données de santé)
- Les données physiologiques (FC, HRV, poids, blessures) relèvent des **catégories particulières (art. 9 RGPD)** → **consentement explicite et distinct** pour la collecte et la connexion d'appareils.
- **Droit à l'oubli** : suppression des activités, révocation des accès Strava/Garmin, purge des fichiers stockés.
- **Export** des données de l'athlète (portabilité).
- Minimisation et non-exposition des données de santé dans les logs / outils de monitoring.
- _(Hébergement : si données de santé, vérifier la nécessité d'un hébergeur conforme — **HDS non requis a priori** hors contexte médical, à confirmer juridiquement.)_

## 5.4 UX & ergonomie
- **Mobile-first** côté athlète (PWA installable, lisible d'un coup d'œil).
- Interfaces simples, temps d'action minimal, feedback systématique (toasts).
- Données chiffrées lisibles : unités claires, **zones d'intensité colorées + libellées** (accessibilité daltonisme).
- Accessibilité : contrastes, focus visibles, cibles tactiles ≥ 44 px.

## 5.5 Internationalisation
- **Français** d'abord ; architecture i18n-ready pour l'anglais ultérieurement (C).

---

# 6. Contraintes & architecture techniques

## 6.1 Stack
- **Frontend** : Angular 17+ (standalone), **PWA**.
- **Backend** : Spring Boot 3, Java 21, API REST.
- **Base de données** : PostgreSQL ; **Liquibase** pour les migrations ; JPA/Hibernate (SQL pur réservé aux requêtes lourdes : calcul de charge, agrégations).
- **Stockage fichiers** : S3-compatible (fichiers FIT/GPX, médias).
- **Hébergement** : Front sur Vercel ; Back en Docker (Railway / VPS) _(à valider)_.

## 6.2 Architecture fonctionnelle
```
Athlète (mobile PWA) / Coach (web)
            ↓
       Angular PWA
            ↓
     API REST Spring Boot  ──→  Intégrations (Strava / Garmin / Coros)
            ↓                         ↑ (OAuth + webhooks)
        PostgreSQL  +  Stockage S3 (FIT/GPX)
```

## 6.3 Intégrations externes
| Intégration | Usage | Priorité |
|---|---|---|
| **Strava** (OAuth + webhooks) | Import auto des activités | M |
| **Garmin / Coros / Polar** | Import auto / OAuth | S |
| **Import FIT/GPX/TCX** | Fallback manuel | S |
| **Email** (Resend/SMTP) | Notifications | M |
| **Push** (WebPush) | Notifications mobiles | S |
| **Stripe** _(à valider)_ | Paiement coaching privé | C |

> **Contraintes d'intégration** : respect des **rate limits** Strava (import asynchrone + backoff),
> **déduplication** des activités (pas de doublon faussant la charge), refresh automatique des tokens.

---

# 7. Modèle de données (conceptuel)

- Utilisateurs & rôles
- Clubs / Coachs
- Athlètes (+ profil physiologique)
- Groupes d'entraînement
- Plans d'entraînement / Blocs (méso-microcycles)
- Séances (prévues) + Étapes structurées
- Modèles de séances (bibliothèque)
- Activités (réalisées, importées)
- Journal de bien-être (wellness)
- Courses / Objectifs
- Messages
- Intégrations d'appareils (tokens)
- Abonnements / Paiements

---

# 8. Algorithme — rapprochement prévu / réalisé

1. Réception d'une activité (webhook Strava ou import) → **déduplication** par identifiant source.
2. Recherche d'une **séance planifiée** du même athlète sur la **même date** (± fenêtre).
3. **Scoring de correspondance** (type, distance, durée) → rapprochement automatique si confiance suffisante.
4. Sinon : activité marquée **« non rattachée »** → rapprochement manuel par l'athlète/coach.
5. Calcul des **écarts** (distance, temps, allure) et mise à jour du statut de la séance.

---

# 9. Phasage / roadmap

| Phase | Contenu | Priorité |
|---|---|---|
| **Phase 1 — MVP** | Athlètes + invitation · calendrier + séances structurées · import Strava + rapprochement · vue athlète « séance du jour » + RPE · notifications email | **M** |
| **Phase 2** | Bibliothèque & plans périodisés · charge & graphes · messagerie · push · courses/objectifs | **S** |
| **Phase 3** | Wellness · facturation coaching privé · Garmin/Coros · modèles de plans · multi-coach club avancé | **C** |
| **Phase 4** | App native · multi-sport · modèles économiques avancés · marketplace de plans | **W** |

---

# 10. Modèle économique _(à valider)_

> À distinguer du paiement « coach → athlète » (§ 3.8). Ci-dessous : abonnement **plateforme → coach/club**.

| Offre | Cible | Idée de prix | Fonctionnalités |
|---|---|---|---|
| **Solo** | Coach indépendant | ~19 €/mois _(à valider)_ | Jusqu'à N athlètes, entraînement + Strava |
| **Pro** | Coach actif | ~39 €/mois | Athlètes illimités, performance + communication |
| **Club** | Club / multi-coach | ~99 €/mois | Multi-coach, groupes, intégrations avancées, facturation |

_(Alternative : tarification **par athlète actif/mois**, fréquente sur ce marché — à arbitrer.)_

---

# 11. Critères d'acceptation & KPIs

- Un coach peut **planifier une semaine complète** et l'athlète la voit en < 5 min après publication.
- Une activité Strava se **rapproche automatiquement** de la séance prévue dans > 90 % des cas simples.
- **Zéro doublon** d'activité (intégrité de la charge).
- Adoption : % d'athlètes ayant connecté un appareil ; % de séances loguées avec RPE ; rétention coachs à 3 mois.

---

# 12. Risques & points d'attention

| Risque | Mitigation |
|---|---|
| Dépendance API Strava (rate limits, conditions d'usage, validation app) | Import asynchrone + backoff ; prévoir import FIT/GPX en fallback ; lire les CGU Strava |
| Données de santé / RGPD art. 9 | Consentement explicite, chiffrement, conseil juridique |
| Complexité du calcul de charge | Formules documentées et isolées, testées ; commencer simple |
| Volume de données d'activité (samples) | Stocker le brut sur S3 + résumé agrégé en DB |
| Adoption athlète (saisie RPE/wellness) | UX ultra-rapide (1 tap), notifications, valeur perçue immédiate |

---

# 13. Glossaire

- **RPE** : ressenti d'effort perçu (échelle 1–10).
- **Zones (Z1–Z5)** : intensités définies par FC / allure / %VMA (récupération → VO2max).
- **Charge (CTL/ATL/forme)** : modélisation de la fatigue/fitness cumulée.
- **VMA** : vitesse maximale aérobie.
- **HRV** : variabilité de la fréquence cardiaque.
- **FIT/GPX/TCX** : formats de fichiers d'activité sportive.
- **Séance structurée** : séance décrite en étapes avec cibles (échauffement, répétitions, récup…).

---

*Fin de l'ébauche du cahier des charges — document de travail à itérer avec le coach (priorisation MVP,
tarifs, choix des intégrations de la phase 1, validation juridique RGPD/Strava).*
