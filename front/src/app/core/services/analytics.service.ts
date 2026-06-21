import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export interface WeekPoint {
  weekStart: string;
  plannedKm: number;
  realizedKm: number;
}
export interface Analytics {
  weeklyVolume: WeekPoint[];
  zoneDistribution: Record<string, number>;
  statusCounts: Record<string, number>;
}

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  get(athleteId: string, weeks = 8): Observable<Analytics> {
    const params = new HttpParams().set('weeks', weeks);
    return this.http.get<Analytics>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/analytics`,
      { params }
    );
  }
}
