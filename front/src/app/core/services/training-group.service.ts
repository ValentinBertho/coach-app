import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TrainingGroup } from '../models/training-group.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class TrainingGroupService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/groups`;
  }

  list(): Observable<TrainingGroup[]> {
    return this.http.get<TrainingGroup[]>(this.base());
  }
  create(name: string): Observable<TrainingGroup> {
    return this.http.post<TrainingGroup>(this.base(), { name });
  }
  rename(id: string, name: string): Observable<TrainingGroup> {
    return this.http.put<TrainingGroup>(`${this.base()}/${id}`, { name });
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
  /** Analytics agrégées d'un groupe (forme, ACWR moyen, volume, adhérence). */
  analytics(id: string, weeks = 8): Observable<GroupAnalytics> {
    return this.http.get<GroupAnalytics>(`${this.base()}/${id}/analytics?weeks=${weeks}`);
  }
}

export type FormStatus = 'GREEN' | 'ORANGE' | 'RED';

export interface GroupAnalyticsRow {
  id: string;
  firstName: string;
  lastName: string;
  discipline: string;
  formStatus: FormStatus;
  fatigue: number | null;
  pain: number | null;
  acwr: number | null;
  plannedKm: number;
  realizedKm: number;
  compliancePct: number | null;
  lastFeedbackDate: string | null;
}

export interface GroupAnalytics {
  groupId: string;
  name: string;
  athleteCount: number;
  form: { green: number; orange: number; red: number };
  totals: { avgAcwr: number | null; plannedKm: number; realizedKm: number; compliancePct: number | null };
  athletes: GroupAnalyticsRow[];
}
