import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Athlete, AthleteRequest, PageResponse } from '../models/athlete.model';
import { AthleteStatus } from '../models/athlete.model';
import {
  AdminAthlete,
  AdminAuditAction,
  AdminAuditEntry,
  AdminOverview,
  AdminPlatform,
  AdminSearchResult,
  AdminStats,
  AdminUser,
  AdminUserCreateRequest,
  AdminUserDetail,
  AdminUserUpdateRequest,
  ClubAdmin,
  ClubDetailAdmin,
  ClubRequest,
  ClubStatus,
  InvitationAdmin,
  InvitationLink,
  MailLogEntry,
  MailStats,
  StravaSubscription,
  StravaWebhookState,
  UserStatus,
} from '../models/admin.model';
import { User, UserRole } from '../models/user.model';

/** Back office d'administration (PLATFORM_ADMIN). Appelle /admin/**. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/admin`;

  // --- Pilotage ---
  /** Compteurs bruts historiques. `overview()` les remplace à l'écran ; la route reste servie. */
  stats(): Observable<AdminStats> {
    return this.http.get<AdminStats>(`${this.base}/stats`);
  }

  /** Tableau de bord : anomalies actionnables, photographie, dynamique, intégrations, journal. */
  overview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>(`${this.base}/overview`);
  }

  /** Recherche globale : un compte, un club ou un athlète, sans choisir l'onglet d'avance. */
  search(q: string): Observable<AdminSearchResult> {
    return this.http.get<AdminSearchResult>(`${this.base}/search`, {
      params: new HttpParams().set('q', q),
    });
  }

  /** Configuration de l'instance, en lecture seule et sans aucune valeur de secret. */
  platform(): Observable<AdminPlatform> {
    return this.http.get<AdminPlatform>(`${this.base}/platform`);
  }

  // --- Journal d'audit ---
  audit(
    opts: {
      action?: string;
      targetType?: string;
      actorUserId?: string;
      targetId?: string;
      days?: number;
      q?: string;
      page?: number;
      size?: number;
    } = {},
  ): Observable<PageResponse<AdminAuditEntry>> {
    let params = new HttpParams().set('page', opts.page ?? 0).set('size', opts.size ?? 50);
    if (opts.action) params = params.set('action', opts.action);
    if (opts.targetType) params = params.set('targetType', opts.targetType);
    if (opts.actorUserId) params = params.set('actorUserId', opts.actorUserId);
    if (opts.targetId) params = params.set('targetId', opts.targetId);
    if (opts.days) params = params.set('days', opts.days);
    if (opts.q) params = params.set('q', opts.q);
    return this.http.get<PageResponse<AdminAuditEntry>>(`${this.base}/audit`, { params });
  }

  auditActions(): Observable<AdminAuditAction[]> {
    return this.http.get<AdminAuditAction[]>(`${this.base}/audit/actions`);
  }

  // --- E-mails ---
  /** Consommation d'e-mails : plafonds, histogramme quotidien, répartition par nature. */
  mailStats(days = 30): Observable<MailStats> {
    return this.http.get<MailStats>(`${this.base}/mail/stats`, {
      params: new HttpParams().set('days', days),
    });
  }

  /** Journal des envois, du plus récent au plus ancien. */
  mailLog(page = 0, size = 50): Observable<PageResponse<MailLogEntry>> {
    return this.http.get<PageResponse<MailLogEntry>>(`${this.base}/mail/log`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  // --- RAZ démo ---
  resetAvailable(): Observable<{ available: boolean }> {
    return this.http.get<{ available: boolean }>(`${this.base}/demo/reset-available`);
  }
  reset(): Observable<{ status: string; message: string }> {
    return this.http.post<{ status: string; message: string }>(`${this.base}/demo/reset`, {});
  }

  // --- Webhook Strava ---
  // Un abonnement par application Strava, posé une fois par environnement : il n'a rien à faire
  // dans un démarrage automatique (prod et préprod se voleraient le flux), d'où ces trois appels.
  stravaWebhook(): Observable<StravaWebhookState> {
    return this.http.get<StravaWebhookState>(`${this.base}/strava/webhook`);
  }
  createStravaWebhook(): Observable<StravaSubscription> {
    return this.http.post<StravaSubscription>(`${this.base}/strava/webhook`, {});
  }
  deleteStravaWebhook(id: number): Observable<{ deleted: boolean }> {
    return this.http.delete<{ deleted: boolean }>(`${this.base}/strava/webhook/${id}`);
  }

  // --- Clubs ---
  clubs(q?: string, page = 0, status?: ClubStatus, size?: number): Observable<PageResponse<ClubAdmin>> {
    let params = new HttpParams().set('page', page);
    if (q) params = params.set('q', q);
    if (status) params = params.set('status', status);
    if (size) params = params.set('size', size);
    return this.http.get<PageResponse<ClubAdmin>>(`${this.base}/clubs`, { params });
  }
  club(id: string): Observable<ClubAdmin> {
    return this.http.get<ClubAdmin>(`${this.base}/clubs/${id}`);
  }
  /** Fiche club : composition, activité, et aperçu d'impact avant suppression. */
  clubDetail(id: string): Observable<ClubDetailAdmin> {
    return this.http.get<ClubDetailAdmin>(`${this.base}/clubs/${id}/detail`);
  }
  createClub(body: ClubRequest): Observable<ClubAdmin> {
    return this.http.post<ClubAdmin>(`${this.base}/clubs`, body);
  }
  updateClub(id: string, body: ClubRequest): Observable<ClubAdmin> {
    return this.http.put<ClubAdmin>(`${this.base}/clubs/${id}`, body);
  }
  deleteClub(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/clubs/${id}`);
  }

  // --- Users ---
  users(
    opts: {
      role?: UserRole;
      status?: UserStatus;
      clubId?: string;
      /** `false` = comptes bloqués sur leur e-mail de confirmation. */
      verified?: boolean;
      q?: string;
      page?: number;
    } = {},
  ): Observable<PageResponse<AdminUser>> {
    let params = new HttpParams().set('page', opts.page ?? 0);
    if (opts.role) params = params.set('role', opts.role);
    // Le filtre existait côté serveur depuis toujours ; le front ne l'envoyait jamais, si bien
    // que la liste des statuts affichée à l'écran ne filtrait rien.
    if (opts.status) params = params.set('status', opts.status);
    if (opts.clubId) params = params.set('clubId', opts.clubId);
    if (opts.verified !== undefined) params = params.set('verified', opts.verified);
    if (opts.q) params = params.set('q', opts.q);
    return this.http.get<PageResponse<AdminUser>>(`${this.base}/users`, { params });
  }
  user(id: string): Observable<AdminUser> {
    return this.http.get<AdminUser>(`${this.base}/users/${id}`);
  }
  /** Fiche complète : vérification, activité, clubs, appareils, historique d'administration. */
  userDetail(id: string): Observable<AdminUserDetail> {
    return this.http.get<AdminUserDetail>(`${this.base}/users/${id}/detail`);
  }
  createUser(body: AdminUserCreateRequest): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}/users`, body);
  }

  /**
   * Ouvre une session au nom de cet utilisateur (impersonation), pour voir l'application comme lui.
   *
   * <p>Le jeton rendu n'a pas de rafraîchissement : la session dure le temps d'un jeton d'accès,
   * après quoi l'écran ramène l'administrateur sur son propre compte. Chaque ouverture est
   * consignée au journal d'audit.</p>
   */
  impersonate(id: string): Observable<{ accessToken: string; expiresIn: number; user: User }> {
    return this.http.post<{ accessToken: string; expiresIn: number; user: User }>(
      `${this.base}/users/${id}/impersonate`, {});
  }

  updateUser(id: string, body: AdminUserUpdateRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}/users/${id}`, body);
  }
  /** Suspend le compte ET ferme ses sessions en cours. */
  suspendUser(id: string, reason?: string): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}/users/${id}/suspend`, { reason: reason ?? null });
  }
  reactivateUser(id: string): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}/users/${id}/reactivate`, {});
  }
  /** Ferme toutes les sessions sans suspendre : « j'ai perdu mon téléphone ». */
  revokeSessions(id: string): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}/users/${id}/revoke-sessions`, {});
  }
  /** Envoie un lien de réinitialisation. L'admin ne choisit ni ne voit le mot de passe. */
  sendPasswordReset(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/users/${id}/password-reset`, {});
  }
  resendVerification(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/users/${id}/resend-verification`, {});
  }
  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }
  /** Rattache un club additionnel à un coach (modèle multi-club). */
  addUserClub(id: string, clubId: string): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}/users/${id}/clubs/${clubId}`, {});
  }
  removeUserClub(id: string, clubId: string): Observable<AdminUser> {
    return this.http.delete<AdminUser>(`${this.base}/users/${id}/clubs/${clubId}`);
  }

  // --- Athletes ---
  athletes(
    opts: { clubId?: string; status?: AthleteStatus; q?: string; page?: number } = {},
  ): Observable<PageResponse<AdminAthlete>> {
    let params = new HttpParams().set('page', opts.page ?? 0);
    if (opts.clubId) params = params.set('clubId', opts.clubId);
    if (opts.status) params = params.set('status', opts.status);
    if (opts.q) params = params.set('q', opts.q);
    return this.http.get<PageResponse<AdminAthlete>>(`${this.base}/athletes`, { params });
  }
  athlete(id: string): Observable<Athlete> {
    return this.http.get<Athlete>(`${this.base}/athletes/${id}`);
  }
  updateAthlete(id: string, body: AthleteRequest): Observable<Athlete> {
    return this.http.put<Athlete>(`${this.base}/athletes/${id}`, body);
  }
  deleteAthlete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/athletes/${id}`);
  }

  // --- Invitations ---
  invitations(page = 0): Observable<PageResponse<InvitationAdmin>> {
    const params = new HttpParams().set('page', page);
    return this.http.get<PageResponse<InvitationAdmin>>(`${this.base}/invitations`, { params });
  }
  revokeInvitation(athleteId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/invitations/${athleteId}`);
  }
  /** Régénère le lien et le renvoie : une invitation expirée n'avait aucune issue avant. */
  resendInvitation(athleteId: string): Observable<InvitationLink> {
    return this.http.post<InvitationLink>(`${this.base}/invitations/${athleteId}/resend`, {});
  }
}
