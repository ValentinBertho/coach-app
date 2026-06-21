import { Injectable, signal } from '@angular/core';

const TOKEN_KEY = 'coachrun.token';

/**
 * État d'authentification (stateless côté front : JWT en localStorage).
 * Squelette : pas encore d'endpoint de login ; structure prête pour la feature auth.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly token = signal<string | null>(localStorage.getItem(TOKEN_KEY));

  get isAuthenticated(): boolean {
    return this.token() !== null;
  }

  setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
    this.token.set(token);
  }

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    this.token.set(null);
  }
}
