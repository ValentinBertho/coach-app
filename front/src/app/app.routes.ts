import { Routes } from '@angular/router';

/**
 * Routing lazy (loadComponent partout). Les espaces coach (features/) et athlète (athlete/)
 * seront ajoutés sous leurs préfixes avec guards par rôle (cf. Techno.md §4).
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent),
  },
  { path: '**', redirectTo: '' },
];
