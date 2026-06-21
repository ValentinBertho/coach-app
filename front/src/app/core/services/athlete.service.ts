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
} from '../models/athlete.model';
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

  list(opts: { status?: AthleteStatus; q?: string; page?: number } = {}): Observable<PageResponse<AthleteSummary>> {
    let params = new HttpParams().set('page', opts.page ?? 0);
    if (opts.status) params = params.set('status', opts.status);
    if (opts.q) params = params.set('q', opts.q);
    return this.http.get<PageResponse<AthleteSummary>>(this.base(), { params });
  }

  get(id: string): Observable<Athlete> {
    return this.http.get<Athlete>(`${this.base()}/${id}`);
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
}
