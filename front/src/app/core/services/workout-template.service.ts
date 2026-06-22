import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/athlete.model';
import { WorkoutTemplate, WorkoutTemplateRequest } from '../models/workout-template.model';
import { Workout } from '../models/workout.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class WorkoutTemplateService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/workout-templates`;
  }

  list(q?: string, page = 0): Observable<PageResponse<WorkoutTemplate>> {
    let params = new HttpParams().set('page', page);
    if (q) params = params.set('q', q);
    return this.http.get<PageResponse<WorkoutTemplate>>(this.base(), { params });
  }
  get(id: string): Observable<WorkoutTemplate> {
    return this.http.get<WorkoutTemplate>(`${this.base()}/${id}`);
  }
  create(body: WorkoutTemplateRequest): Observable<WorkoutTemplate> {
    return this.http.post<WorkoutTemplate>(this.base(), body);
  }
  update(id: string, body: WorkoutTemplateRequest): Observable<WorkoutTemplate> {
    return this.http.put<WorkoutTemplate>(`${this.base()}/${id}`, body);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
  apply(id: string, athleteId: string, date: string): Observable<Workout> {
    return this.http.post<Workout>(`${this.base()}/${id}/apply`, { athleteId, date });
  }
}
