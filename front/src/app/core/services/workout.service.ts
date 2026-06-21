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

  delete(athleteId: string, workoutId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(athleteId)}/${workoutId}`);
  }
}
