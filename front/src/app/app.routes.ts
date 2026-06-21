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
    path: 'invitation/:token',
    loadComponent: () =>
      import('./features/public/invitation.component').then((m) => m.InvitationComponent),
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
        path: 'athletes/:athleteId/workouts/new',
        loadComponent: () =>
          import('./features/workouts/workout-form.component').then((m) => m.WorkoutFormComponent),
      },
      {
        path: 'athletes/:athleteId/workouts/:workoutId/edit',
        loadComponent: () =>
          import('./features/workouts/workout-form.component').then((m) => m.WorkoutFormComponent),
      },
    ],
  },
  {
    path: 'athlete',
    canActivate: [athleteGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'today' },
      {
        path: 'today',
        loadComponent: () =>
          import('./features/athlete/today.component').then((m) => m.TodayComponent),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/athlete/profile.component').then((m) => m.AthleteProfileComponent),
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
    ],
  },
  { path: '**', redirectTo: '' },
];
