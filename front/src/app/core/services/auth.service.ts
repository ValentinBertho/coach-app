import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  User,
} from '../models/user.model';

const ACCESS_KEY = 'coachrun.accessToken';
const REFRESH_KEY = 'coachrun.refreshToken';

/**
 * État d'authentification (stateless côté front : JWT en localStorage).
 * Expose des signals `token` / `currentUser` consommés par les guards et l'UI.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth`;

  readonly token = signal<string | null>(localStorage.getItem(ACCESS_KEY));
  readonly currentUser = signal<User | null>(null);
  readonly isAuthenticated = computed(() => this.token() !== null);

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/register`, request)
      .pipe(tap((res) => this.applySession(res)));
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.base}/login`, request)
      .pipe(tap((res) => this.applySession(res)));
  }

  /** Recharge le profil courant depuis le token (au démarrage de l'app). */
  loadCurrentUser(): Observable<User> {
    return this.http
      .get<User>(`${this.base}/me`)
      .pipe(tap((user) => this.currentUser.set(user)));
  }

  logout(): void {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    this.token.set(null);
    this.currentUser.set(null);
  }

  private applySession(res: AuthResponse): void {
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
    this.token.set(res.accessToken);
    this.currentUser.set(res.user);
  }
}
