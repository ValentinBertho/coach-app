/**
 * Contenu du centre d'aide intégré, par profil (athlète / coach / admin).
 *
 * Documentation utilisateur — volontairement séparée du code des écrans pour rester
 * facile à relire et à faire évoluer sans toucher au rendu. Le composant
 * `HelpCenterComponent` consomme ces données et les rend en accordéon recherchable.
 *
 * Ton : tutoiement côté athlète (cohérent avec l'UI du portail), vouvoiement côté
 * coach et admin. Chaque section reste autonome et lisible « en diagonale ».
 */

export type HelpTone = 'tip' | 'info' | 'warn';

export interface HelpBlock {
  /** text = paragraphe · steps = liste numérotée · list = puces · callout = encart. */
  kind: 'text' | 'steps' | 'list' | 'callout';
  text?: string;
  items?: string[];
  tone?: HelpTone;
}

export interface HelpSection {
  id: string;
  /** Nom d'icône Lucide (cf. app.config.ts). */
  icon: string;
  title: string;
  /** Résumé d'une ligne, affiché sous le titre et indexé par la recherche. */
  summary: string;
  blocks: HelpBlock[];
}

export type HelpAudience = 'athlete' | 'coach' | 'admin';

export interface HelpGuide {
  audience: HelpAudience;
  title: string;
  subtitle: string;
  sections: HelpSection[];
}

// ===========================================================================
// ATHLÈTE
// ===========================================================================
const ATHLETE: HelpGuide = {
  audience: 'athlete',
  title: 'Guide de l\'athlète',
  subtitle: 'Tout pour suivre ton entraînement au quotidien, depuis ton téléphone.',
  sections: [
    {
      id: 'demarrer',
      icon: 'rocket',
      title: 'Bien démarrer',
      summary: 'Rejoindre ton coach, installer l\'app, activer les rappels.',
      blocks: [
        { kind: 'text', text: 'Ton coach t\'envoie un lien d\'invitation (par message ou e-mail). C\'est ta porte d\'entrée : il te crée un accès personnel et sécurisé.' },
        {
          kind: 'steps', items: [
            'Ouvre le lien d\'invitation sur ton téléphone.',
            'Choisis ton e-mail et un mot de passe (8 caractères minimum) pour pouvoir te reconnecter plus tard.',
            'Coche le consentement pour le suivi de tes données physiologiques (obligatoire, exigence RGPD).',
            'Tu arrives directement sur ta séance du jour.',
          ],
        },
        { kind: 'callout', tone: 'tip', text: 'Installe l\'app sur ton écran d\'accueil : touche « Installer » dans l\'en-tête (ou « Partager → Ajouter à l\'écran d\'accueil » sur iPhone). Elle s\'ouvre alors en plein écran, comme une vraie application, et reste consultable même hors connexion.' },
        { kind: 'callout', tone: 'info', text: 'Active les notifications (icône cloche) pour être prévenu de ta séance du jour et des messages de ton coach. Sur iPhone, installe d\'abord l\'app sur l\'écran d\'accueil : les notifications n\'y fonctionnent qu\'une fois installée.' },
      ],
    },
    {
      id: 'seance-jour',
      icon: 'house',
      title: 'Ma séance du jour',
      summary: 'Lire la prescription, comprendre les fourchettes, noter ton retour.',
      blocks: [
        { kind: 'text', text: 'L\'onglet « Séance » affiche ce que tu as à faire aujourd\'hui : course, renforcement, ou les deux. Si tu as une double séance, chaque séance apparaît sur sa propre carte.' },
        { kind: 'text', text: 'Tes cibles sont données en fourchettes (allure, fréquence cardiaque, RPE) et en zones d\'intensité colorées. Reste dans la fourchette : c\'est volontairement une plage, pas une valeur unique à atteindre au centième.' },
        { kind: 'list', items: [
          'Zones colorées = intensité visée pour chaque bloc (échauffement, corps, retour au calme).',
          'Renforcement : saisis tes séries réalisées (charge, répétitions, RIR) — ta force estimée (e1RM) se met à jour toute seule.',
          'Une suggestion de progression de ton coach peut apparaître pour la prochaine fois.',
        ] },
        { kind: 'text', text: 'Une fois la séance faite, touche « Noter mon retour » :' },
        { kind: 'steps', items: [
          'Indique ton effort perçu (RPE de 1 à 10) en un tap.',
          'Renseigne ta fatigue et une éventuelle douleur.',
          'Ajoute un commentaire si tu veux (optionnel).',
          'Valide « Séance réalisée » — ou « Partiellement » si tu n\'as pas tout fait.',
        ] },
        { kind: 'callout', tone: 'info', text: 'Pas de réseau ? Aucun souci : ton retour est enregistré sur ton téléphone et envoyé automatiquement dès que la connexion revient.' },
        { kind: 'callout', tone: 'tip', text: 'Ton état de forme est calculé à partir de ta fatigue et de tes douleurs — jamais à partir du RPE. Sois honnête sur ces deux curseurs, c\'est ce qui aide ton coach à ajuster.' },
      ],
    },
    {
      id: 'agenda',
      icon: 'calendar',
      title: 'Mon agenda',
      summary: 'Voir ta semaine, déplacer une séance, signaler une indisponibilité.',
      blocks: [
        { kind: 'text', text: 'L\'onglet « Agenda » montre ta semaine, jour par jour. Tu navigues d\'une semaine à l\'autre avec les flèches.' },
        { kind: 'text', text: 'Tu peux déplacer une séance (par exemple décaler la sortie longue d\'un jour) :' },
        { kind: 'steps', items: [
          'Touche la séance à déplacer.',
          'Dans la fenêtre qui s\'ouvre, choisis le nouveau jour.',
          'C\'est fait — ton coach voit le changement.',
        ] },
        { kind: 'callout', tone: 'info', text: 'Tu peux déplacer une séance, mais pas en modifier le contenu ni la supprimer : seul ton coach construit les séances. C\'est voulu, pour garder un plan cohérent.' },
      ],
    },
    {
      id: 'sorties',
      icon: 'footprints',
      title: 'Mes sorties',
      summary: 'Enregistrer une course : à la main, par fichier GPX, ou via Strava.',
      blocks: [
        { kind: 'text', text: 'L\'onglet « Sorties » regroupe tes activités réelles. Trois façons de les ajouter :' },
        { kind: 'list', items: [
          'À la main : « Ajouter une sortie » → date, distance, durée, dénivelé.',
          'Par fichier : « Importer un GPX » (formats GPX et TCX) — le tracé s\'affiche sur une carte.',
          'Automatiquement : connecte Strava et tes sorties s\'importent toutes seules.',
        ] },
        { kind: 'text', text: 'Pour connecter ta montre, va dans Profil → « Gérer mes connexions », ou l\'onglet Synchronisation, puis « Connecter Strava ». L\'import se fait ensuite automatiquement (avec déduplication, donc pas de doublon).' },
      ],
    },
    {
      id: 'progres',
      icon: 'trending-up',
      title: 'Mes progrès',
      summary: 'VDOT, allures, charge d\'entraînement, records, historique, tests.',
      blocks: [
        { kind: 'text', text: 'L\'onglet « Progrès » est ton tableau de bord personnel pour mieux te connaître :' },
        { kind: 'list', items: [
          'VDOT et allures d\'entraînement : à quelle allure courir selon l\'intensité visée.',
          'Ma charge : ACWR (avec une bande de sécurité), fatigue/forme, monotonie — pour éviter la blessure.',
          'Mon volume hebdomadaire et mes records par distance.',
          'Mon historique de séances avec mon ressenti dans le temps.',
          'Mes tests lactate (si ton coach en réalise) avec ta courbe de profil.',
        ] },
        { kind: 'callout', tone: 'info', text: 'Une étiquette indique toujours si une valeur est « mesurée » (issue d\'un test) ou « estimée/calculée » (déduite de tes performances). Les deux sont utiles, mais ne se valent pas.' },
        { kind: 'callout', tone: 'tip', text: 'Pas encore d\'allures affichées ? Ajoute un chrono récent (5 ou 10 km) dans « Mes performances » : tes allures de travail se débloquent immédiatement.' },
      ],
    },
    {
      id: 'objectifs',
      icon: 'flag',
      title: 'Mes objectifs',
      summary: 'Tes courses cibles et le compte à rebours J-XX.',
      blocks: [
        { kind: 'text', text: 'Tes courses cibles apparaissent avec un compte à rebours (J-XX) sur ta séance du jour et dans ton profil. Elles sont priorisées A (objectif majeur), B ou C par ton coach.' },
        { kind: 'text', text: 'Tu peux aussi gérer tes propres objectifs depuis l\'onglet dédié.' },
      ],
    },
    {
      id: 'messages',
      icon: 'message-square',
      title: 'Messages',
      summary: 'Échanger avec ton coach en direct.',
      blocks: [
        { kind: 'text', text: 'L\'onglet « Messages » est ta discussion directe avec ton coach, en temps réel. Tu peux y joindre des images ou des PDF (résultat d\'examen, photo d\'une douleur, etc.).' },
        { kind: 'callout', tone: 'tip', text: 'Un doute sur une séance, une douleur, une question d\'allure ? Écris à ton coach plutôt que de deviner.' },
      ],
    },
    {
      id: 'profil',
      icon: 'lock',
      title: 'Profil & confidentialité',
      summary: 'Tes connexions, tes données (RGPD), la suppression de compte.',
      blocks: [
        { kind: 'text', text: 'Depuis l\'onglet « Profil », tu gères tes connexions (montre/Strava) et tes données personnelles.' },
        { kind: 'list', items: [
          'Exporter mes données : télécharge l\'ensemble de tes données au format JSON (portabilité RGPD).',
          'Supprimer mon compte : efface définitivement ton profil, tes séances et tes activités.',
        ] },
        { kind: 'callout', tone: 'warn', text: 'La suppression de compte est irréversible. Une fois confirmée, tes données ne peuvent pas être récupérées.' },
      ],
    },
    {
      id: 'faq',
      icon: 'circle-help',
      title: 'Questions fréquentes',
      summary: 'Mot de passe, séance invisible, allures, notifications.',
      blocks: [
        { kind: 'text', text: 'J\'ai oublié mon mot de passe. — Utilise « Mot de passe oublié » sur l\'écran de connexion si tu reçois les e-mails ; sinon demande à ton coach de te renvoyer un lien d\'invitation.' },
        { kind: 'text', text: 'Je ne vois pas de séance aujourd\'hui. — Soit c\'est un jour de repos (« Repos aujourd\'hui »), soit ton coach n\'a pas encore planifié la journée. Regarde ton agenda pour la suite de la semaine.' },
        { kind: 'text', text: 'Je n\'ai pas d\'allures d\'entraînement. — Ajoute un chrono récent dans « Mes performances » : le calcul se fait aussitôt.' },
        { kind: 'text', text: 'Je n\'ai pas de notifications sur iPhone. — Installe d\'abord l\'app sur ton écran d\'accueil (Partager → Ajouter à l\'écran d\'accueil), puis autorise les notifications.' },
        { kind: 'text', text: 'L\'app marche-t-elle sans réseau ? — Oui pour consulter ta séance et noter ton retour : tout se synchronise au retour de la connexion.' },
      ],
    },
  ],
};

// ===========================================================================
// COACH
// ===========================================================================
const COACH: HelpGuide = {
  audience: 'coach',
  title: 'Guide du coach',
  subtitle: 'Prescrire, suivre et communiquer — de la physiologie au terrain.',
  sections: [
    {
      id: 'demarrer',
      icon: 'rocket',
      title: 'Premiers pas',
      summary: 'Connexion, vérification e-mail, tour du tableau de bord.',
      blocks: [
        { kind: 'text', text: 'Connectez-vous depuis la page de connexion. Si un bandeau vous invite à confirmer votre e-mail, faites-le : cela sécurise votre compte et active les notifications.' },
        { kind: 'text', text: 'Votre espace est organisé autour du tableau de bord (vos athlètes, leur état de forme, les prochaines courses) et d\'une navigation latérale : Athlètes, Groupes, Calendrier, Bibliothèque, Éducatifs, Prépa physique, Club, Paramètres.' },
        { kind: 'callout', tone: 'info', text: 'Coach indépendant ou club : le fonctionnement est le même. Un coach solo correspond à un « club » à un seul membre — vos données sont toujours cloisonnées.' },
      ],
    },
    {
      id: 'athletes',
      icon: 'users',
      title: 'Gérer mes athlètes',
      summary: 'Créer, inviter par lien, renseigner le profil, archiver, grouper.',
      blocks: [
        { kind: 'steps', items: [
          'Athlètes → « Nouvel athlète » : renseignez l\'identité et le profil sportif.',
          'Ouvrez sa fiche → « Inviter » : un lien d\'invitation personnel est généré.',
          'Copiez ce lien et envoyez-le à l\'athlète (SMS, WhatsApp, e-mail).',
          'Dès qu\'il l\'ouvre, il crée son accès et voit sa séance du jour.',
        ] },
        { kind: 'callout', tone: 'tip', text: 'Le lien d\'invitation se copie en un clic : vous n\'avez pas besoin que l\'e-mail automatique soit activé pour faire entrer un athlète. Partagez-le par le canal que vous préférez.' },
        { kind: 'list', items: [
          'Profil sportif : discipline (route/trail), niveau, antécédents, données physiologiques.',
          'Groupes d\'entraînement : rattachez des athlètes à un groupe pour les filtrer et les suivre ensemble.',
          'Archivage : un athlète qui arrête est archivé (jamais perdu), sans encombrer vos listes.',
        ] },
      ],
    },
    {
      id: 'physio',
      icon: 'heart-pulse',
      title: 'Profil physiologique & tests',
      summary: 'VDOT, allures, seuils LT1/LT2, tests lactate, transparence des données.',
      blocks: [
        { kind: 'text', text: 'Le profil physiologique est le moteur des prescriptions. Tout est recalculé automatiquement à la sauvegarde.' },
        { kind: 'list', items: [
          'Performances de référence (5 km, 10 km…) → VDOT + table d\'allures d\'équivalence.',
          'Fréquences cardiaques (max, seuils) pour les cibles en FC.',
          'Test lactate : saisissez les paliers → détection automatique de LT1 et LT2 (Dmax modifié).',
          'Domaines d\'intensité (1/2/3) et vitesse critique dérivés du profil.',
        ] },
        { kind: 'callout', tone: 'info', text: 'L\'app distingue toujours une allure « mesurée » (test lactate) d\'une allure « estimée » (VDOT). Sans test lactate, les prescriptions en seuils retombent proprement sur le VDOT — vous restez opérationnel dès le premier chrono.' },
      ],
    },
    {
      id: 'seances',
      icon: 'library',
      title: 'Bibliothèque & séances de course',
      summary: 'Éditeur de structure en fourchettes, calculateur live, éducatifs, modèles.',
      blocks: [
        { kind: 'text', text: 'Construisez une séance structurée en trois temps (échauffement / corps / retour au calme). Chaque bloc se prescrit en fourchettes : % de LT1/LT2/VC ou d\'allure.' },
        { kind: 'list', items: [
          'Calculateur live : visualisez les cibles réelles (allure/FC/RPE) pour un athlète donné, en direct.',
          'Saisie simplifiée : bascule Distance/Durée, durée en minutes, blocs pré-remplis.',
          'Éducatifs de course (gammes, technique, amplitude) attachables aux blocs.',
          'Affichage en cartes compactes ou en liste dense, avec recherche et filtre par type.',
        ] },
        { kind: 'callout', tone: 'tip', text: 'Enregistrez vos séances récurrentes comme modèles : vous les appliquez ensuite au calendrier en un clic, pour un athlète ou un groupe.' },
      ],
    },
    {
      id: 'force',
      icon: 'dumbbell',
      title: 'Préparation physique (force)',
      summary: 'Exercices, 1RM, formats avancés, cycles, progression automatique.',
      blocks: [
        { kind: 'list', items: [
          'Bibliothèque d\'exercices : groupes musculaires, matériel, vidéo, progression/régression.',
          'Calcul de charge (1RM) : méthode Nuzzo par défaut (+ Epley / Brzycki / RIR), zones de travail.',
          'Tests 1RM (4 protocoles) → mise à jour automatique du profil de force.',
          'Formats avancés : Classique, EMOM, AMRAP, For Time, Circuit, Isométrie, Pliométrie.',
          'Types de série : drop-set, super-set, myo-reps, cluster, iso — charge et effort indépendants.',
        ] },
        { kind: 'text', text: 'Les cycles de force (progression multi-semaines) s\'assignent au calendrier. La progression est suggérée automatiquement, avec des alertes (douleur, RPE, RIR, charge) qui remontent vers vous.' },
      ],
    },
    {
      id: 'calendrier',
      icon: 'calendar-days',
      title: 'Calendrier & planification',
      summary: 'Planifier, glisser-déposer, dupliquer la semaine, mésocycle.',
      blocks: [
        { kind: 'text', text: 'Le calendrier multi-types (course / force / test / objectif / indispo) est votre poste de pilotage.' },
        { kind: 'list', items: [
          'Glissez une séance depuis la bibliothèque latérale, ou créez une séance ad hoc.',
          'Replanifiez par glisser-déposer ; la prescription est figée en snapshot au moment de la planification.',
          'Dupliquez une semaine entière ou appliquez un mésocycle d\'un coup.',
          'Vues semaine et mois ; le drag & drop est conservé entre les deux.',
        ] },
        { kind: 'callout', tone: 'info', text: 'Sur un athlète que vous consultez en lecture seule, les actions d\'écriture (planifier, dupliquer) sont désactivées — cohérent avec vos permissions.' },
      ],
    },
    {
      id: 'plans',
      icon: 'folder-open',
      title: 'Plans périodisés',
      summary: 'Construire un plan sur N semaines et le caler sur une date de course.',
      blocks: [
        { kind: 'text', text: 'Composez un plan semaine × jour à partir de vos modèles de séances, puis appliquez-le à un athlète. À l\'application, vous calez le plan sur la date de la course cible : les semaines se positionnent automatiquement.' },
      ],
    },
    {
      id: 'suivi',
      icon: 'activity',
      title: 'Suivi & charge',
      summary: 'Dashboard, pastilles de forme, ACWR/CTL/ATL, analytics, prévu vs réalisé.',
      blocks: [
        { kind: 'list', items: [
          'Tableau de bord : athlètes par discipline, pastilles de forme, prochaines courses, KPI cliquables.',
          'Pastille de forme : verte/orange/rouge, calculée sur fatigue + douleur (jamais le RPE).',
          'Charge : ACWR (ratio aigu/chronique), ATL/CTL, monotonie, répartition par domaine d\'intensité.',
          'Analytics : volume prévu/réalisé, distribution des zones, adhérence au plan.',
          'Rapprochement prévu ↔ réalisé : l\'activité importée se rattache à la séance, avec les écarts.',
        ] },
        { kind: 'callout', tone: 'tip', text: 'Surveillez les athlètes dont l\'ACWR sort de la bande de sécurité ou dont la pastille passe au rouge : ce sont vos priorités d\'ajustement de la semaine.' },
      ],
    },
    {
      id: 'communication',
      icon: 'message-square',
      title: 'Communication',
      summary: 'Messagerie temps réel, pièces jointes, notifications.',
      blocks: [
        { kind: 'text', text: 'Échangez avec chaque athlète via une messagerie en temps réel, avec pièces jointes (images, PDF). Les notifications (e-mail et push) préviennent l\'athlète d\'une nouvelle séance ou d\'un message, et vous d\'un retour renseigné.' },
        { kind: 'callout', tone: 'info', text: 'Les notifications e-mail et push s\'activent côté serveur (clé d\'envoi et clés push). Tant qu\'elles ne sont pas configurées, le partage du lien d\'invitation et la messagerie in-app restent pleinement utilisables.' },
      ],
    },
    {
      id: 'objectifs',
      icon: 'flag',
      title: 'Objectifs & courses',
      summary: 'Créer une course cible A/B/C avec compte à rebours.',
      blocks: [
        { kind: 'text', text: 'Créez les courses cibles d\'un athlète (date, distance, priorité A/B/C). Le compte à rebours J-XX s\'affiche côté athlète et oriente la planification (affûtage, calage du plan).' },
      ],
    },
    {
      id: 'integrations',
      icon: 'watch',
      title: 'Intégrations & export',
      summary: 'Strava (côté athlète), import GPX/TCX, export PDF du programme.',
      blocks: [
        { kind: 'list', items: [
          'Strava : la connexion est initiée par l\'athlète (depuis son espace) ; vous voyez l\'état en lecture seule.',
          'Import de fichiers GPX/TCX et saisie manuelle d\'activités côté athlète.',
          'Export PDF du programme d\'un athlète pour un partage hors-ligne.',
        ] },
        { kind: 'callout', tone: 'info', text: 'Garmin et COROS sont prévus mais pas encore disponibles. Le fallback GPX/TCX couvre l\'essentiel des montres.' },
      ],
    },
    {
      id: 'club',
      icon: 'building-2',
      title: 'Gérer le club (responsable)',
      summary: 'Coachs, groupes et périmètre — pour les responsables de club.',
      blocks: [
        { kind: 'text', text: 'En tant que responsable de club, vous gérez les coachs et les groupes d\'entraînement depuis l\'espace Club. Chaque coach ne voit que ses athlètes et ceux du club selon ses permissions ; les données restent cloisonnées par club.' },
      ],
    },
    {
      id: 'faq',
      icon: 'circle-help',
      title: 'Questions fréquentes',
      summary: 'Invitation, allures sans test, lecture seule.',
      blocks: [
        { kind: 'text', text: 'Mon athlète n\'a pas reçu l\'invitation par e-mail. — Ouvrez sa fiche, cliquez « Inviter » puis copiez le lien et envoyez-le vous-même (SMS/WhatsApp). L\'e-mail automatique n\'est pas indispensable.' },
        { kind: 'text', text: 'Je n\'ai pas de test lactate pour cet athlète. — Les prescriptions en seuils utilisent automatiquement le VDOT (à partir d\'un chrono). Saisissez au moins une performance récente.' },
        { kind: 'text', text: 'Pourquoi certaines actions sont-elles grisées ? — Vous consultez probablement un athlète en lecture seule. L\'écriture (planifier, dupliquer) dépend de vos permissions sur cet athlète.' },
        { kind: 'text', text: 'La pastille de forme s\'appuie-t-elle sur le RPE ? — Non, jamais. Elle combine fatigue et douleur uniquement.' },
      ],
    },
  ],
};

// ===========================================================================
// ADMINISTRATEUR PLATEFORME
// ===========================================================================
const ADMIN: HelpGuide = {
  audience: 'admin',
  title: 'Guide de l\'administrateur',
  subtitle: 'Superviser la plateforme : clubs, comptes, conformité et exploitation.',
  sections: [
    {
      id: 'role',
      icon: 'shield-check',
      title: 'Le rôle d\'administrateur',
      summary: 'Périmètre et responsabilités de l\'admin plateforme.',
      blocks: [
        { kind: 'text', text: 'L\'administrateur plateforme supervise l\'ensemble des clubs et des comptes. Il n\'entraîne pas d\'athlète : il assure le bon fonctionnement, le support et la conformité.' },
        { kind: 'callout', tone: 'warn', text: 'Vos actions sont transverses à tous les clubs. Manipulez les suppressions de clubs, de comptes et la réinitialisation de démo avec prudence.' },
      ],
    },
    {
      id: 'dashboard',
      icon: 'layout-dashboard',
      title: 'Tableau de bord & supervision',
      summary: 'Vue d\'ensemble : clubs, utilisateurs, athlètes, activité.',
      blocks: [
        { kind: 'text', text: 'Le tableau de bord admin donne les volumes clés (clubs, coachs, athlètes, invitations) et sert de point d\'entrée vers chaque section de gestion.' },
      ],
    },
    {
      id: 'clubs',
      icon: 'building-2',
      title: 'Gérer les clubs',
      summary: 'Créer, consulter et administrer les clubs.',
      blocks: [
        { kind: 'text', text: 'Depuis « Clubs », créez et administrez les clubs de la plateforme. Chaque club est une frontière de données stricte : aucun coach ne voit au-delà de son club.' },
      ],
    },
    {
      id: 'users',
      icon: 'users',
      title: 'Gérer les utilisateurs',
      summary: 'Coachs et responsables de club, rôles et accès.',
      blocks: [
        { kind: 'text', text: 'La section « Utilisateurs » liste les comptes coach et responsable de club. Vous y gérez leur rattachement et leurs accès.' },
        { kind: 'callout', tone: 'info', text: 'Les rôles sensibles (responsable de club, admin) ouvrent des actions de gestion supplémentaires. Attribuez-les avec discernement.' },
      ],
    },
    {
      id: 'athletes',
      icon: 'user',
      title: 'Gérer les athlètes',
      summary: 'Vue transverse des athlètes, multi-club.',
      blocks: [
        { kind: 'text', text: 'La vue « Athlètes » est transverse à tous les clubs (support et supervision). L\'édition d\'un athlète depuis l\'admin reste tracée et cloisonnée à son club d\'origine.' },
      ],
    },
    {
      id: 'invitations',
      icon: 'paperclip',
      title: 'Invitations',
      summary: 'Suivre l\'état des invitations (en attente, acceptées).',
      blocks: [
        { kind: 'text', text: 'Suivez l\'état des invitations émises (en attente, acceptées, expirées) pour diagnostiquer un onboarding qui bloque.' },
      ],
    },
    {
      id: 'demo',
      icon: 'refresh-cw',
      title: 'Démo & réinitialisation',
      summary: 'Recharger un jeu de démonstration propre — hors production.',
      blocks: [
        { kind: 'text', text: 'En environnement de démonstration, vous pouvez réinitialiser les données pour repartir d\'un jeu propre et déterministe (profils, tests, séances, charge, objectifs).' },
        { kind: 'callout', tone: 'warn', text: 'La réinitialisation purge toutes les données. Elle est strictement interdite en production (refusée par le serveur) et désactivée par défaut ailleurs.' },
      ],
    },
    {
      id: 'securite',
      icon: 'lock',
      title: 'Sécurité, RGPD & exploitation',
      summary: 'Secrets, chiffrement santé, sauvegardes, monitoring.',
      blocks: [
        { kind: 'list', items: [
          'Données de santé chiffrées au repos (AES-256-GCM) ; jetons d\'intégration chiffrés également.',
          'Consentement RGPD explicite à l\'onboarding ; export et suppression de compte côté athlète.',
          'Secrets de production obligatoires : l\'application refuse de démarrer avec des valeurs par défaut.',
          'Cloisonnement multi-club systématique (anti-IDOR) sur chaque ressource.',
        ] },
        { kind: 'callout', tone: 'info', text: 'Exploitation (sauvegardes base de données, monitoring d\'erreurs, supervision de santé) : la mise en place pas-à-pas est documentée pour l\'équipe technique. Vérifiez que sauvegardes et remontée d\'erreurs sont actives avant toute montée en charge.' },
      ],
    },
    {
      id: 'faq',
      icon: 'circle-help',
      title: 'Questions fréquentes',
      summary: 'Onboarding bloqué, e-mails, périmètre des données.',
      blocks: [
        { kind: 'text', text: 'Un athlète ne reçoit pas son e-mail. — Tant que l\'envoi d\'e-mails n\'est pas configuré, le coach partage le lien d\'invitation manuellement (copie depuis la fiche athlète). Vérifiez la configuration d\'envoi pour automatiser.' },
        { kind: 'text', text: 'Un coach voit-il les athlètes d\'un autre club ? — Non. Le cloisonnement par club est strict et vérifié sur chaque accès.' },
      ],
    },
  ],
};

export const HELP_GUIDES: Record<HelpAudience, HelpGuide> = {
  athlete: ATHLETE,
  coach: COACH,
  admin: ADMIN,
};
