import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Athlete, AthleteRequest, PageResponse } from '../models/athlete.model';
import {
  AdminAthlete,
  AdminStats,
  AdminUser,
  AdminUserCreateRequest,
  AdminUserUpdateRequest,
  ClubAdmin,
  ClubRequest,
  InvitationAdmin,
} from '../models/admin.model';
import { UserRole } from '../models/user.model';

/** Back office d'administration (PLATFORM_ADMIN). Appelle /admin/**. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/admin`;

  // --- Dashboard / RAZ ---
  stats(): Observable<AdminStats> {
    return this.http.get<AdminStats>(`${this.base}/stats`);
  }
  resetAvailable(): Observable<{ available: boolean }> {
    return this.http.get<{ available: boolean }>(`${this.base}/demo/reset-available`);
  }
  reset(): Observable<{ status: string; message: string }> {
    return this.http.post<{ status: string; message: string }>(`${this.base}/demo/reset`, {});
  }

  // --- Clubs ---
  clubs(q?: string, page = 0): Observable<PageResponse<ClubAdmin>> {
    let params = new HttpParams().set('page', page);
    if (q) params = params.set('q', q);
    return this.http.get<PageResponse<ClubAdmin>>(`${this.base}/clubs`, { params });
  }
  club(id: string): Observable<ClubAdmin> {
    return this.http.get<ClubAdmin>(`${this.base}/clubs/${id}`);
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
  users(opts: { role?: UserRole; q?: string; page?: number } = {}): Observable<PageResponse<AdminUser>> {
    let params = new HttpParams().set('page', opts.page ?? 0);
    if (opts.role) params = params.set('role', opts.role);
    if (opts.q) params = params.set('q', opts.q);
    return this.http.get<PageResponse<AdminUser>>(`${this.base}/users`, { params });
  }
  user(id: string): Observable<AdminUser> {
    return this.http.get<AdminUser>(`${this.base}/users/${id}`);
  }
  createUser(body: AdminUserCreateRequest): Observable<AdminUser> {
    return this.http.post<AdminUser>(`${this.base}/users`, body);
  }
  updateUser(id: string, body: AdminUserUpdateRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(`${this.base}/users/${id}`, body);
  }
  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`);
  }

  // --- Athletes ---
  athletes(opts: { clubId?: string; q?: string; page?: number } = {}): Observable<PageResponse<AdminAthlete>> {
    let params = new HttpParams().set('page', opts.page ?? 0);
    if (opts.clubId) params = params.set('clubId', opts.clubId);
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
}
