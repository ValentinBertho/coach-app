import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, finalize, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  LoginRequest,
  PaceUnit,
  RegisterRequest,
  User,
} from '../models/user.model';

const ACCESS_KEY = 'darilab.accessToken';
const REFRESH_KEY = 'darilab.refreshToken';
const USER_KEY = 'darilab.user';

function readStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? (JSON.parse(raw) as User) : null;
}

/**
 * État d'authentification (stateless côté front : JWT en localStorage).
 * Expose des signals `token` / `currentUser` consommés par les guards et l'UI.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth`;

  readonly token = signal<string | null>(localStorage.getItem(ACCESS_KEY));
  readonly currentUser = signal<User | null>(readStoredUser());
  readonly isAuthenticated = computed(() => this.token() !== null);

  /** Refresh en cours partagé : évite les rafraîchissements concurrents sur une salve de 401. */
  private refreshInFlight: Observable<AuthResponse> | null = null;

  refreshTokenValue(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  /**
   * Rafraîchit silencieusement l'access token via le refresh token (rotation côté serveur).
   * Partagé entre appelants concurrents. Émet une erreur si aucun refresh token n'est disponible.
   */
  refresh(): Observable<AuthResponse> {
    if (this.refreshInFlight) {
      return this.refreshInFlight;
    }
    const refreshToken = this.refreshTokenValue();
    if (!refreshToken) {
      return throwError(() => new Error('Aucun refresh token disponible.'));
    }
    this.refreshInFlight = this.http
      .post<AuthResponse>(`${this.base}/refresh`, { refreshToken })
      .pipe(
        tap((res) => this.applySession(res)),
        shareReplay(1),
        finalize(() => { this.refreshInFlight = null; }),
      );
    return this.refreshInFlight;
  }

  /** Identifiant du club courant (scoping tenant des appels API). */
  clubId(): string | null {
    return this.currentUser()?.clubId ?? null;
  }

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

  /** Onboarding athlète par lien magique : définit identifiants + session. */
  acceptInvitation(
    token: string,
    healthDataConsent: boolean,
    email?: string,
    password?: string,
  ): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/public/invitations/${token}/accept`, {
        healthDataConsent,
        email,
        password,
      })
      .pipe(tap((res) => this.applySession(res)));
  }

  /** Demande de réinitialisation de mot de passe (réponse identique quel que soit le compte). */
  forgotPassword(email: string): Observable<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>(`${environment.apiUrl}/public/password-reset`, { email });
  }

  /** Valide un lien de réinitialisation. */
  checkReset(token: string): Observable<{ valid: boolean }> {
    return this.http.get<{ valid: boolean }>(`${environment.apiUrl}/public/password-reset/${token}`);
  }

  /** Applique le nouveau mot de passe et ouvre une session. */
  resetPassword(token: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/public/password-reset/${token}`, { password })
      .pipe(tap((res) => this.applySession(res)));
  }

  /** Confirme l'adresse e-mail à partir du jeton reçu par e-mail. */
  verifyEmail(token: string): Observable<{ verified: boolean }> {
    return this.http.post<{ verified: boolean }>(`${environment.apiUrl}/public/verify-email/${token}`, {});
  }

  /** Renvoie l'e-mail de vérification au compte courant. */
  /** Édite le profil courant (nom, e-mail, préférence d'unité d'allure). */
  updateProfile(body: { fullName: string; email: string; paceUnit?: PaceUnit }): Observable<User> {
    return this.http.patch<User>(`${environment.apiUrl}/auth/me`, body).pipe(
      tap((user) => this.currentUser.set(user)),
    );
  }

  /** Changement de mot de passe (l'actuel est exigé côté serveur). */
  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/change-password`, { currentPassword, newPassword });
  }

  /** Unité d'allure préférée, avec repli sur min/km si le profil n'est pas encore chargé. */
  paceUnit(): PaceUnit {
    return this.currentUser()?.paceUnit ?? 'PACE';
  }

  resendVerification(): Observable<void> {
    return this.http.post<void>(`${this.base}/resend-verification`, {});
  }

  /** Met à jour localement le drapeau de vérification (après confirmation). */
  markEmailVerified(): void {
    const u = this.currentUser();
    if (u) {
      const next = { ...u, emailVerified: true };
      localStorage.setItem(USER_KEY, JSON.stringify(next));
      this.currentUser.set(next);
    }
  }

  /** Onboarding coach par lien magique : définit le mot de passe et ouvre la session. */
  acceptCoachInvitation(token: string, password: string, fullName?: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/public/coach-invitations/${token}/accept`, { password, fullName })
      .pipe(tap((res) => this.applySession(res)));
  }

  /** Recharge le profil courant depuis le token (au démarrage de l'app). */
  loadCurrentUser(): Observable<User> {
    return this.http.get<User>(`${this.base}/me`).pipe(
      tap((user) => {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        this.currentUser.set(user);
      })
    );
  }

  logout(): void {
    // Révocation côté serveur (best-effort) avant purge locale.
    if (this.token()) {
      this.http.post(`${this.base}/logout`, {}).subscribe({ next: () => {}, error: () => {} });
    }
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    this.token.set(null);
    this.currentUser.set(null);
  }

  private applySession(res: AuthResponse): void {
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this.token.set(res.accessToken);
    this.currentUser.set(res.user);
  }
}
