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

  /**
   * Écran d'accueil du rôle courant — **source unique** de cette correspondance.
   *
   * <p>Elle était recopiée dans la page de connexion et la 404, et absente partout ailleurs :
   * la landing, les gardes et la PWA renvoyaient tout le monde vers `/app`. Un athlète y
   * atterrissait donc dans le cockpit coach, qui se peuplait de « Accès refusé » puisque le
   * serveur refuse — à raison — toute la surface club à son jeton.</p>
   */
  homeRoute(): string {
    switch (this.currentUser()?.role) {
      case 'PLATFORM_ADMIN': return '/admin';
      case 'ATHLETE': return '/athlete/today';
      case 'COACH':
      case 'HEAD_COACH': return '/app';
      default: return '/login';
    }
  }

  /** Le rôle courant a-t-il accès au cockpit coach (`/app`) ? */
  isCoach(): boolean {
    const role = this.currentUser()?.role;
    return role === 'COACH' || role === 'HEAD_COACH' || role === 'PLATFORM_ADMIN';
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
  acceptCoachInvitation(token: string, password: string, fullName?: string,
                        termsAccepted?: boolean): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/public/coach-invitations/${token}/accept`,
        { password, fullName, termsAccepted })
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
    void this.purgeCaches();
  }

  /**
   * Vide les caches du service worker à la déconnexion. Sans ça, sur un appareil partagé, le
   * compte suivant peut être servi depuis les réponses API du précédent (données de santé,
   * art. 9 RGPD). Best-effort : l'API Cache Storage n'existe pas partout (Safari privé, tests).
   */
  private async purgeCaches(): Promise<void> {
    if (typeof caches === 'undefined') {
      return;
    }
    try {
      const keys = await caches.keys();
      await Promise.all(keys.map((key) => caches.delete(key)));
    } catch {
      // Purge best-effort : un échec ne doit pas bloquer la déconnexion.
    }
  }

  private applySession(res: AuthResponse): void {
    localStorage.setItem(ACCESS_KEY, res.accessToken);
    localStorage.setItem(REFRESH_KEY, res.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
    this.token.set(res.accessToken);
    this.currentUser.set(res.user);
  }
}
