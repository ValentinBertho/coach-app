import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
import { athleteGuard } from './core/guards/athlete.guard';
import { coachGuard } from './core/guards/coach.guard';
import { installedAppGuard } from './core/guards/installed-app.guard';
import { unsavedChangesGuard } from './core/guards/unsaved-changes.guard';

/**
 * Routing lazy (loadComponent / loadChildren). Un garde par espace, et chacun vérifie le
 * **rôle**, pas seulement l'authentification : /app → coachGuard, /athlete → athleteGuard,
 * /admin → adminGuard. Chaque refus renvoie vers l'accueil du rôle réel (AuthService.homeRoute).
 */
export const routes: Routes = [
  {
    // La landing publique — sauf en application installée sans session, où l'on ouvre
    // directement sur la connexion (cf. installedAppGuard).
    path: '',
    canActivate: [installedAppGuard],
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'reset-password/:token',
    loadComponent: () =>
      import('./features/auth/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    path: 'verify-email/:token',
    loadComponent: () =>
      import('./features/auth/verify-email.component').then((m) => m.VerifyEmailComponent),
  },
  {
    path: 'invitation/:token',
    loadComponent: () =>
      import('./features/public/invitation.component').then((m) => m.InvitationComponent),
  },
  {
    path: 'coach-invitation/:token',
    loadComponent: () =>
      import('./features/public/coach-invitation.component').then((m) => m.CoachInvitationComponent),
  },
  {
    // Pages légales publiques : confidentialite | mentions-legales | cgu.
    path: 'legal/:page',
    loadComponent: () =>
      import('./features/public/legal.component').then((m) => m.LegalComponent),
  },
  {
    // Support public, hors authentification : le centre d'aide de l'application vit sous
    // `app/aide` et suppose un compte. Or il faut une page joignable SANS compte — pour un
    // athlète qui n'a pas encore reçu son invitation, et parce que les partenaires
    // d'intégration (COROS notamment) l'exigent pour valider un accès API.
    path: 'support',
    data: { page: 'support' },
    loadComponent: () =>
      import('./features/public/legal.component').then((m) => m.LegalComponent),
  },
  {
    // Raccourcis de l'écran d'accueil (manifeste `shortcuts`). Le manifeste est unique pour les
    // deux métiers : ces chemins neutres décident de la destination selon le rôle connecté.
    path: 'go/:target',
    loadComponent: () =>
      import('./features/public/shortcut.component').then((m) => m.ShortcutComponent),
  },
  {
    // Living styleguide des primitives UI (dev). Cf. docs/archive/ux-redesign-blueprint.md.
    // Réservé à l'équipe : en bêta ouverte, un coach qui tombe dessus voit un écran de debug.
    path: 'dev/ui-kit',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/dev/ui-kit.component').then((m) => m.UiKitComponent),
  },
  {
    // Sonde d'état de l'API : retirée du pied de page public (un prospect n'a pas à voir
    // « API injoignable »), conservée ici pour l'équipe.
    path: 'dev/api',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/dev/api-status.component').then((m) => m.ApiStatusComponent),
  },
  {
    /**
     * Retour d'autorisation Strava — **hors** de la coquille coach et **sans garde de rôle**.
     *
     * Cet écran était déclaré comme enfant de `/app`, donc protégé par `coachGuard` : un athlète
     * revenant de Strava était renvoyé vers `/athlete/today` avant même l'échange du code, et sa
     * connexion Strava ne pouvait jamais aboutir depuis la PWA. Comme l'URL de redirection est
     * figée côté Strava (`…/app/strava/callback`), on la garde telle quelle mais on la sert
     * ici — déclarée AVANT `app`, elle gagne sur ses enfants.
     *
     * Le composant décide lui-même de la suite : finalisation athlète ou coach selon le rôle,
     * détour par `/login` si la session manque (retour hors PWA).
     */
    path: 'app/strava/callback',
    loadComponent: () =>
      import('./features/athletes/strava-callback.component').then((m) => m.StravaCallbackComponent),
  },
  {
    // Même écran sur un chemin neutre : c'est celui à configurer côté Strava pour les nouveaux
    // environnements (`STRAVA_REDIRECT_URI`), l'espace coach n'ayant rien à voir là-dedans.
    path: 'strava/callback',
    loadComponent: () =>
      import('./features/athletes/strava-callback.component').then((m) => m.StravaCallbackComponent),
  },
  {
    path: 'app',
    // coachGuard, pas authGuard : ce dernier laissait entrer un athlète connecté.
    canActivate: [coachGuard],
    loadComponent: () =>
      import('./features/layout/coach-layout.component').then((m) => m.CoachLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      {
        // « Ma journée » : l'écran du matin, pensé pour un téléphone. Le cockpit (`/app`) garde
        // les KPI, la répartition de forme et les courses — ce sont deux lectures différentes.
        path: 'journee',
        loadComponent: () =>
          import('./features/dashboard/coach-day.component').then((m) => m.CoachDayComponent),
      },
      {
        path: 'athletes',
        loadComponent: () =>
          import('./features/athletes/athlete-list.component').then((m) => m.AthleteListComponent),
      },
      {
        path: 'athletes/new',
        loadComponent: () =>
          import('./features/athletes/athlete-form.component').then((m) => m.AthleteFormComponent),
      },
      {
        path: 'athletes/:athleteId/edit',
        loadComponent: () =>
          import('./features/athletes/athlete-form.component').then((m) => m.AthleteFormComponent),
      },
      {
        path: 'calendar',
        loadComponent: () =>
          import('./features/calendar/calendar.component').then((m) => m.CalendarComponent),
      },
      {
        // Messagerie : fils de binôme, entre coachs, de groupe et du club. Même écran que côté
        // athlète — le serveur rend les fils auxquels on participe, quel que soit le rôle.
        path: 'messages',
        loadComponent: () =>
          import('./features/messages/conversations.component').then((m) => m.ConversationsComponent),
      },
      {
        // File « retours à traiter » : destination du KPI du cockpit.
        path: 'feedback',
        loadComponent: () =>
          import('./features/dashboard/feedback-queue.component').then((m) => m.FeedbackQueueComponent),
      },
      {
        // Bibliothèque unique à onglets : les trois écrans existants sont montés tels quels
        // en routes enfants (course, prépa physique, éducatifs).
        path: 'library',
        loadComponent: () =>
          import('./features/library/library-shell.component').then((m) => m.LibraryShellComponent),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'course' },
          {
            path: 'course',
            loadComponent: () =>
              import('./features/templates/template-list.component').then((m) => m.TemplateListComponent),
          },
          {
            path: 'strength',
            loadComponent: () =>
              import('./features/strength/strength.component').then((m) => m.StrengthComponent),
          },
          {
            path: 'drills',
            loadComponent: () =>
              import('./features/templates/run-drills.component').then((m) => m.RunDrillsComponent),
          },
        ],
      },
      // Anciennes entrées de nav, conservées en redirection : aucun lien (favori, e-mail,
      // capture d'écran de doc) ne doit casser avec la fusion des bibliothèques.
      { path: 'templates', pathMatch: 'full', redirectTo: 'library/course' },
      { path: 'strength', pathMatch: 'full', redirectTo: 'library/strength' },
      { path: 'run-drills', pathMatch: 'full', redirectTo: 'library/drills' },
      {
        path: 'templates/:templateId/structure',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/templates/session-editor.component').then((m) => m.SessionEditorComponent),
      },
      {
        path: 'strength/sessions/:sessionId/structure',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/strength/strength-session-editor.component').then(
            (m) => m.StrengthSessionEditorComponent
          ),
      },
      // `strava/callback` vivait ici : déplacé à la racine (voir plus haut) pour que l'athlète
      // qui revient de Strava ne se heurte plus à `coachGuard`.
      {
        path: 'groups',
        loadComponent: () =>
          import('./features/groups/group-list.component').then((m) => m.GroupListComponent),
      },
      {
        path: 'groups/:id/analytics',
        loadComponent: () =>
          import('./features/groups/group-analytics.component').then((m) => m.GroupAnalyticsComponent),
      },
      {
        path: 'club',
        loadComponent: () =>
          import('./features/club/club.component').then((m) => m.ClubComponent),
      },
      {
        path: 'training-zones',
        loadComponent: () =>
          import('./features/zones/training-zones.component').then((m) => m.TrainingZonesComponent),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.component').then((m) => m.SettingsComponent),
      },
      {
        path: 'notifications',
        loadComponent: () =>
          import('./features/settings/notifications.component').then((m) => m.NotificationsComponent),
      },
      {
        path: 'aide',
        data: { audience: 'coach' },
        loadComponent: () =>
          import('./features/help/help-center.component').then((m) => m.HelpCenterComponent),
      },
      // --- Écrans plein cadre d'un athlète (hors coquille) -------------------
      // Déclarés AVANT la coquille : le routeur retient la première route qui
      // correspond, donc ces chemins littéraux gagnent sur ses enfants.
      {
        path: 'athletes/:athleteId/workouts/:workoutId/structure',
        canDeactivate: [unsavedChangesGuard],
        loadComponent: () =>
          import('./features/templates/session-editor.component').then((m) => m.SessionEditorComponent),
      },
      {
        path: 'athletes/:athleteId/workouts/:workoutId',
        loadComponent: () =>
          import('./features/workouts/workout-detail.component').then((m) => m.WorkoutDetailComponent),
      },
      {
        path: 'athletes/:athleteId/activities/:activityId/map',
        loadComponent: () =>
          import('./features/activities/activity-map.component').then((m) => m.ActivityMapComponent),
      },

      // --- Coquille d'un athlète : contexte persistant + onglets -------------
      // Les sections sont des routes ENFANTS : le bandeau d'identité et la barre
      // d'onglets, peints par la coquille, ne disparaissent plus quand on change
      // de section (cf. AthleteShellComponent).
      {
        path: 'athletes/:athleteId',
        loadComponent: () =>
          import('./features/athletes/athlete-shell.component').then((m) => m.AthleteShellComponent),
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'resume' },
          {
            path: 'resume',
            loadComponent: () =>
              import('./features/athletes/athlete-detail.component').then((m) => m.AthleteDetailComponent),
          },
          {
            path: 'programme',
            loadComponent: () =>
              import('./features/calendar/calendar.component').then((m) => m.CalendarComponent),
          },
          {
            path: 'load',
            loadComponent: () =>
              import('./features/physio/load.component').then((m) => m.LoadComponent),
          },
          {
            path: 'zones',
            loadComponent: () =>
              import('./features/athletes/athlete-zones.component').then((m) => m.AthleteZonesComponent),
          },
          {
            path: 'tests',
            loadComponent: () =>
              import('./features/physio/thresholds.component').then((m) => m.ThresholdsComponent),
          },
          {
            path: 'races',
            loadComponent: () =>
              import('./features/races/race-list.component').then((m) => m.RaceListComponent),
          },
          {
            path: 'activities',
            loadComponent: () =>
              import('./features/activities/activity-list.component').then((m) => m.ActivityListComponent),
          },
          {
            path: 'messages',
            loadComponent: () =>
              import('./features/messages/chat.component').then((m) => m.ChatComponent),
          },
          // « Charge & progression » est fusionné dans l'onglet Charge : on garde
          // l'ancienne URL en redirection pour ne casser aucun lien existant.
          { path: 'analytics', pathMatch: 'full', redirectTo: 'load' },
        ],
      },
    ],
  },
  {
    // Mode séance de force plein écran — volontairement HORS de la coquille athlète : pas de
    // bottom-nav, pas de barre supérieure. On est en séance, un exercice à la fois ; le reste
    // de l'app n'a rien à faire là (cf. audit UI/UX §3.1).
    path: 'athlete/session/:scheduledId',
    canActivate: [athleteGuard],
    loadComponent: () =>
      import('./features/athlete/strength-session.component').then((m) => m.StrengthSessionComponent),
  },
  {
    path: 'athlete',
    canActivate: [athleteGuard],
    loadComponent: () =>
      import('./features/athlete/athlete-shell.component').then((m) => m.AthleteShellComponent),
    children: [
      // Le calendrier est la porte d'entrée du portail : l'athlète ouvre son application pour
      // voir la forme de son mois, pas pour lire d'abord la séance du jour (qui est à un onglet).
      { path: '', pathMatch: 'full', redirectTo: 'calendar' },
      {
        path: 'today',
        loadComponent: () =>
          import('./features/athlete/today.component').then((m) => m.TodayComponent),
      },
      {
        path: 'calendar',
        loadComponent: () =>
          import('./features/athlete/athlete-calendar.component').then((m) => m.AthleteCalendarComponent),
      },
      {
        path: 'progress',
        loadComponent: () =>
          import('./features/athlete/athlete-progress.component').then((m) => m.AthleteProgressComponent),
      },
      {
        path: 'history',
        loadComponent: () =>
          import('./features/athlete/athlete-history.component').then((m) => m.AthleteHistoryComponent),
      },
      {
        path: 'activities',
        loadComponent: () =>
          import('./features/athlete/athlete-activities.component').then((m) => m.AthleteActivitiesComponent),
      },
      {
        // Fiche « ma séance » : prescription, réalisé, courbe, tours, tracé et débrief au même
        // endroit. Ces éléments existaient tous, éparpillés sur trois écrans dont aucun ne
        // répondait à « ma séance de samedi, elle a donné quoi ? ».
        path: 'workouts/:workoutId',
        loadComponent: () =>
          import('./features/athlete/athlete-workout-detail.component')
            .then((m) => m.AthleteWorkoutDetailComponent),
      },
      {
        path: 'lactate',
        loadComponent: () =>
          import('./features/athlete/athlete-lactate.component').then((m) => m.AthleteLactateComponent),
      },
      {
        path: 'races',
        loadComponent: () =>
          import('./features/athlete/athlete-races.component').then((m) => m.AthleteRacesComponent),
      },
      {
        path: 'sync',
        loadComponent: () =>
          import('./features/athlete/athlete-sync.component').then((m) => m.AthleteSyncComponent),
      },
      {
        path: 'performances',
        loadComponent: () =>
          import('./features/athlete/athlete-performances.component').then((m) => m.AthletePerformancesComponent),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/athlete/profile.component').then((m) => m.AthleteProfileComponent),
      },
      {
        // L'athlète a désormais plusieurs fils : un par coach, plus son groupe et le club.
        path: 'messages',
        loadComponent: () =>
          import('./features/messages/conversations.component').then((m) => m.ConversationsComponent),
      },
      {
        path: 'help',
        data: { audience: 'athlete' },
        loadComponent: () =>
          import('./features/help/help-center.component').then((m) => m.HelpCenterComponent),
      },
    ],
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/admin/admin-layout.component').then((m) => m.AdminLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/admin/admin-dashboard.component').then((m) => m.AdminDashboardComponent),
      },
      {
        path: 'clubs',
        loadComponent: () =>
          import('./features/admin/admin-clubs.component').then((m) => m.AdminClubsComponent),
      },
      {
        path: 'users',
        loadComponent: () =>
          import('./features/admin/admin-users.component').then((m) => m.AdminUsersComponent),
      },
      {
        path: 'athletes',
        loadComponent: () =>
          import('./features/admin/admin-athletes.component').then((m) => m.AdminAthletesComponent),
      },
      {
        path: 'athletes/:id/edit',
        loadComponent: () =>
          import('./features/admin/admin-athlete-edit.component').then((m) => m.AdminAthleteEditComponent),
      },
      {
        path: 'invitations',
        loadComponent: () =>
          import('./features/admin/admin-invitations.component').then((m) => m.AdminInvitationsComponent),
      },
      {
        // Consommation d'e-mails : le plan d'envoi est plafonné, et rien ne le mesurait.
        path: 'mail',
        loadComponent: () =>
          import('./features/admin/admin-mail.component').then((m) => m.AdminMailComponent),
      },
      {
        path: 'feedback',
        loadComponent: () =>
          import('./features/admin/admin-feedback.component').then((m) => m.AdminFeedbackComponent),
      },
      {
        path: 'aide',
        data: { audience: 'admin' },
        loadComponent: () =>
          import('./features/help/help-center.component').then((m) => m.HelpCenterComponent),
      },
    ],
  },
  {
    // Vraie 404 : une redirection silencieuse vers l'accueil laissait croire que le lien
    // avait fonctionné.
    path: '**',
    loadComponent: () =>
      import('./features/public/not-found.component').then((m) => m.NotFoundComponent),
  },
];
