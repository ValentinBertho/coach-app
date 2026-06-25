import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export type ClubRole = 'OWNER' | 'COACH_PRINCIPAL' | 'COACH_ASSISTANT';
export type PermissionLevel = 'READ' | 'COMMENT' | 'WRITE';
export type Ownership = 'PRIVATE' | 'CLUB';

export interface ClubMember {
  coachId: string;
  name: string;
  clubRole: ClubRole;
}

export interface PermissionEntry {
  coachId: string;
  name: string;
  permission: PermissionLevel;
  expiresAt: string | null;
}

export interface AthleteAccess {
  ownership: Ownership;
  referentCoachId: string | null;
  referentName: string | null;
  permissions: PermissionEntry[];
}

/** Multi-coach / club : membres, statut privé/club et permissions (cf. Darilab). */
@Injectable({ providedIn: 'root' })
export class ClubService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private club(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}`;
  }

  members(): Observable<ClubMember[]> {
    return this.http.get<ClubMember[]>(`${this.club()}/members`);
  }

  access(athleteId: string): Observable<AthleteAccess> {
    return this.http.get<AthleteAccess>(`${this.club()}/athletes/${athleteId}/access`);
  }

  setOwnership(athleteId: string, ownership: Ownership): Observable<AthleteAccess> {
    return this.http.patch<AthleteAccess>(`${this.club()}/athletes/${athleteId}/ownership`, { ownership });
  }

  grant(athleteId: string, coachId: string, permission: PermissionLevel): Observable<AthleteAccess> {
    return this.http.put<AthleteAccess>(`${this.club()}/athletes/${athleteId}/permissions/${coachId}`, { permission });
  }

  revoke(athleteId: string, coachId: string): Observable<AthleteAccess> {
    return this.http.delete<AthleteAccess>(`${this.club()}/athletes/${athleteId}/permissions/${coachId}`);
  }
}
