import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Athlete,
  AthleteInvitation,
  AthleteRequest,
  AthleteStatus,
  AthleteSummary,
  PageResponse,
  Ref,
} from '../models/athlete.model';
import { TrainingPlan } from '../models/training-plan.model';
import { StravaStatus } from '../models/strava.model';
import { Unavailability, UnavailabilityRequest } from '../models/unavailability.model';
import { AuthService } from './auth.service';

/**
 * Accès aux athlètes du club courant (scoping tenant : /clubs/{clubId}/athletes).
 */
@Injectable({ providedIn: 'root' })
export class AthleteService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes`;
  }

  list(opts: { status?: AthleteStatus; groupId?: string; q?: string; page?: number } = {}): Observable<PageResponse<AthleteSummary>> {
    let params = new HttpParams().set('page', opts.page ?? 0);
    if (opts.status) params = params.set('status', opts.status);
    if (opts.groupId) params = params.set('groupId', opts.groupId);
    if (opts.q) params = params.set('q', opts.q);
    return this.http.get<PageResponse<AthleteSummary>>(this.base(), { params });
  }

  get(id: string): Observable<Athlete> {
    return this.http.get<Athlete>(`${this.base()}/${id}`);
  }

  /** Export PDF du programme (séances course + force) sur une période. */
  exportProgram(id: string, from: string, to: string): Observable<Blob> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get(`${this.base()}/${id}/program/export.pdf`, { params, responseType: 'blob' });
  }

  // --- Strava ---
  stravaStatus(id: string): Observable<StravaStatus> {
    return this.http.get<StravaStatus>(`${this.base()}/${id}/strava`);
  }
  stravaAuthorizeUrl(id: string): Observable<{ url: string }> {
    return this.http.get<{ url: string }>(`${this.base()}/${id}/strava/authorize`);
  }
  stravaConnect(id: string, code: string): Observable<StravaStatus> {
    return this.http.post<StravaStatus>(`${this.base()}/${id}/strava/connect`, { code });
  }
  stravaImport(id: string): Observable<{ imported: number }> {
    return this.http.post<{ imported: number }>(`${this.base()}/${id}/strava/import`, {});
  }
  stravaDisconnect(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}/strava`);
  }

  // --- Indisponibilités ---
  listUnavailabilities(id: string): Observable<Unavailability[]> {
    return this.http.get<Unavailability[]>(`${this.base()}/${id}/unavailabilities`);
  }

  createUnavailability(id: string, body: UnavailabilityRequest): Observable<Unavailability> {
    return this.http.post<Unavailability>(`${this.base()}/${id}/unavailabilities`, body);
  }

  deleteUnavailability(id: string, unavailabilityId: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}/unavailabilities/${unavailabilityId}`);
  }

  create(request: AthleteRequest): Observable<Athlete> {
    return this.http.post<Athlete>(this.base(), request);
  }

  update(id: string, request: AthleteRequest): Observable<Athlete> {
    return this.http.put<Athlete>(`${this.base()}/${id}`, request);
  }

  archive(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }

  invite(id: string): Observable<AthleteInvitation> {
    return this.http.post<AthleteInvitation>(`${this.base()}/${id}/invitation`, {});
  }

  // --- Relations many-to-many ---

  /** Coachs du club assignables à un athlète. */
  assignableCoaches(): Observable<Ref[]> {
    return this.http.get<Ref[]>(`${this.base()}/assignable-coaches`);
  }

  assignCoach(athleteId: string, coachId: string): Observable<Athlete> {
    return this.http.put<Athlete>(`${this.base()}/${athleteId}/coaches/${coachId}`, {});
  }

  removeCoach(athleteId: string, coachId: string): Observable<Athlete> {
    return this.http.delete<Athlete>(`${this.base()}/${athleteId}/coaches/${coachId}`);
  }

  /** Plans d'entraînement attribués à un athlète. */
  plans(athleteId: string): Observable<TrainingPlan[]> {
    return this.http.get<TrainingPlan[]>(`${this.base()}/${athleteId}/plans`);
  }
}
