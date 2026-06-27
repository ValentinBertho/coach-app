import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
import { athleteGuard } from './core/guards/athlete.guard';
import { authGuard } from './core/guards/auth.guard';

/**
 * Routing lazy (loadComponent / loadChildren). Espace coach sous /app (authGuard).
 * L'espace athlète (PWA) viendra sous /athlete avec son propre guard.
 */
export const routes: Routes = [
  {
    path: '',
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
    // Living styleguide des primitives UI (dev). Cf. docs/ux-redesign-blueprint.md.
    path: 'dev/ui-kit',
    loadComponent: () =>
      import('./features/dev/ui-kit.component').then((m) => m.UiKitComponent),
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/layout/coach-layout.component').then((m) => m.CoachLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
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
        path: 'athletes/:id',
        loadComponent: () =>
          import('./features/athletes/athlete-detail.component').then((m) => m.AthleteDetailComponent),
      },
      {
        path: 'athletes/:id/edit',
        loadComponent: () =>
          import('./features/athletes/athlete-form.component').then((m) => m.AthleteFormComponent),
      },
      {
        path: 'calendar',
        loadComponent: () =>
          import('./features/calendar/calendar.component').then((m) => m.CalendarComponent),
      },
      {
        path: 'templates',
        loadComponent: () =>
          import('./features/templates/template-list.component').then((m) => m.TemplateListComponent),
      },
      {
        path: 'templates/:templateId/structure',
        loadComponent: () =>
          import('./features/templates/session-editor.component').then((m) => m.SessionEditorComponent),
      },
      {
        path: 'strength',
        loadComponent: () =>
          import('./features/strength/strength.component').then((m) => m.StrengthComponent),
      },
      {
        path: 'run-drills',
        loadComponent: () =>
          import('./features/templates/run-drills.component').then((m) => m.RunDrillsComponent),
      },
      {
        path: 'strength/sessions/:sessionId/structure',
        loadComponent: () =>
          import('./features/strength/strength-session-editor.component').then(
            (m) => m.StrengthSessionEditorComponent
          ),
      },
      {
        path: 'strava/callback',
        loadComponent: () =>
          import('./features/athletes/strava-callback.component').then((m) => m.StravaCallbackComponent),
      },
      {
        path: 'plans',
        loadComponent: () =>
          import('./features/plans/plan-list.component').then((m) => m.PlanListComponent),
      },
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
      {
        path: 'athletes/:athleteId/messages',
        loadComponent: () =>
          import('./features/messages/chat.component').then((m) => m.ChatComponent),
      },
      {
        path: 'athletes/:athleteId/activities',
        loadComponent: () =>
          import('./features/activities/activity-list.component').then((m) => m.ActivityListComponent),
      },
      {
        path: 'athletes/:athleteId/analytics',
        loadComponent: () =>
          import('./features/analytics/analytics.component').then((m) => m.AnalyticsComponent),
      },
      {
        path: 'athletes/:athleteId/tests',
        loadComponent: () =>
          import('./features/physio/lactate.component').then((m) => m.LactateComponent),
      },
      {
        path: 'athletes/:athleteId/load',
        loadComponent: () =>
          import('./features/physio/load.component').then((m) => m.LoadComponent),
      },
      {
        path: 'athletes/:athleteId/activities/:activityId/map',
        loadComponent: () =>
          import('./features/activities/activity-map.component').then((m) => m.ActivityMapComponent),
      },
      {
        path: 'athletes/:athleteId/races',
        loadComponent: () =>
          import('./features/races/race-list.component').then((m) => m.RaceListComponent),
      },
      {
        path: 'athletes/:athleteId/workouts/:workoutId/structure',
        loadComponent: () =>
          import('./features/templates/session-editor.component').then((m) => m.SessionEditorComponent),
      },
      {
        path: 'athletes/:athleteId/workouts/:workoutId',
        loadComponent: () =>
          import('./features/workouts/workout-detail.component').then((m) => m.WorkoutDetailComponent),
      },
    ],
  },
  {
    path: 'athlete',
    canActivate: [athleteGuard],
    loadComponent: () =>
      import('./features/athlete/athlete-shell.component').then((m) => m.AthleteShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'today' },
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
        path: 'messages',
        loadComponent: () =>
          import('./features/messages/chat.component').then((m) => m.ChatComponent),
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
        path: 'aide',
        data: { audience: 'admin' },
        loadComponent: () =>
          import('./features/help/help-center.component').then((m) => m.HelpCenterComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
