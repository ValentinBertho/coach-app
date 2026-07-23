import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  TrainingZone,
  TrainingZoneRequest,
  ZoneMetricsRequest,
  ZoneRuleRequest,
} from '../models/training-zone.model';
import { AuthService } from './auth.service';

/** Zones de travail paramétrables du club (CRUD, réordonnancement, métriques portées). */
@Injectable({ providedIn: 'root' })
export class TrainingZoneService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/training-zones`;
  }

  list(): Observable<TrainingZone[]> {
    return this.http.get<TrainingZone[]>(this.base());
  }
  create(body: TrainingZoneRequest): Observable<TrainingZone> {
    return this.http.post<TrainingZone>(this.base(), body);
  }
  update(id: string, body: TrainingZoneRequest): Observable<TrainingZone> {
    return this.http.put<TrainingZone>(`${this.base()}/${id}`, body);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
  reorder(orderedIds: string[]): Observable<void> {
    return this.http.patch<void>(`${this.base()}/reorder`, { orderedIds });
  }
  setMetrics(id: string, body: ZoneMetricsRequest): Observable<TrainingZone> {
    return this.http.put<TrainingZone>(`${this.base()}/${id}/metrics`, body);
  }
  setRule(id: string, metricId: string, body: ZoneRuleRequest): Observable<TrainingZone> {
    return this.http.put<TrainingZone>(`${this.base()}/${id}/metrics/${metricId}/rule`, body);
  }
}
