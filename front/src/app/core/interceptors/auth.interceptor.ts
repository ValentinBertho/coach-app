import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/** Routes qui ne doivent pas recevoir le Bearer (entrée publique de l'auth). */
const NO_AUTH = ['/public/', '/auth/login', '/auth/register', '/auth/refresh'];

/**
 * Ajoute le Bearer JWT et `withCredentials`, sauf sur les routes publiques.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const bypass = NO_AUTH.some((p) => req.url.includes(p));

  const token = auth.token();
  if (token && !bypass) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
      withCredentials: true,
    });
  }
  return next(req);
};
