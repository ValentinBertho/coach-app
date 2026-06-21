import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Workout, WorkoutStatus } from '../models/workout.model';

export interface WorkoutFeedback {
  status?: WorkoutStatus;
  rpe?: number | null;
  comment?: string | null;
}

/** API du portail athlète (PWA) : scoping serveur par l'athleteId du token. */
@Injectable({ providedIn: 'root' })
export class AthletePortalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/me`;

  today(date?: string): Observable<Workout[]> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<Workout[]>(`${this.base}/today`, { params });
  }

  feedback(workoutId: string, body: WorkoutFeedback): Observable<Workout> {
    return this.http.patch<Workout>(`${this.base}/workouts/${workoutId}/feedback`, body);
  }
}
