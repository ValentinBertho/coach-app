import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Workout, WorkoutRequest, WorkoutStatus } from '../models/workout.model';
import { AuthService } from './auth.service';

/**
 * Séances d'un athlète (calendrier + éditeur). Scoping tenant via clubId courant.
 */
@Injectable({ providedIn: 'root' })
export class WorkoutService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/workouts`;
  }

  calendar(athleteId: string, from: string, to: string): Observable<Workout[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<Workout[]>(this.base(athleteId), { params });
  }

  get(athleteId: string, workoutId: string): Observable<Workout> {
    return this.http.get<Workout>(`${this.base(athleteId)}/${workoutId}`);
  }

  create(athleteId: string, request: WorkoutRequest): Observable<Workout> {
    return this.http.post<Workout>(this.base(athleteId), request);
  }

  update(athleteId: string, workoutId: string, request: WorkoutRequest): Observable<Workout> {
    return this.http.put<Workout>(`${this.base(athleteId)}/${workoutId}`, request);
  }

  updateStatus(athleteId: string, workoutId: string, status: WorkoutStatus): Observable<Workout> {
    return this.http.patch<Workout>(`${this.base(athleteId)}/${workoutId}/status`, { status });
  }

  reschedule(athleteId: string, workoutId: string, scheduledDate: string): Observable<Workout> {
    return this.http.patch<Workout>(`${this.base(athleteId)}/${workoutId}/reschedule`, { scheduledDate });
  }

  delete(athleteId: string, workoutId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(athleteId)}/${workoutId}`);
  }

  /** Duplique une semaine de séances (lundis) vers une autre semaine. Renvoie le nb créé. */
  duplicateWeek(athleteId: string, sourceWeekStart: string, targetWeekStart: string): Observable<{ created: number }> {
    return this.http.post<{ created: number }>(
      `${this.base(athleteId)}/duplicate-week`,
      { sourceWeekStart, targetWeekStart },
    );
  }

  /** Génère un mésocycle progressif à partir d'une semaine type. */
  generateMesocycle(athleteId: string, params: {
    sourceWeekStart: string; firstWeekStart: string; weeks: number;
    increasePct: number; deloadEvery: number; deloadPct: number;
  }): Observable<{ created: number; weeks: number }> {
    return this.http.post<{ created: number; weeks: number }>(
      `${this.base(athleteId)}/generate-mesocycle`, params,
    );
  }
}
