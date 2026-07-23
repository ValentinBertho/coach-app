import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Activity, ActivityImportRequest } from '../models/activity.model';
import { AuthService } from './auth.service';

/** Activités réalisées d'un athlète (import + rapprochement). Scoping tenant via clubId. */
@Injectable({ providedIn: 'root' })
export class ActivityService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/activities`;
  }

  list(athleteId: string): Observable<Activity[]> {
    return this.http.get<Activity[]>(this.base(athleteId));
  }

  /** Activité réalisée rapprochée d'une séance (204 → null). */
  forWorkout(athleteId: string, workoutId: string): Observable<Activity | null> {
    return this.http.get<Activity>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/workouts/${workoutId}/activity`,
      { observe: 'response' },
    ).pipe(map((res) => res.body ?? null));
  }

  import(athleteId: string, request: ActivityImportRequest): Observable<Activity> {
    return this.http.post<Activity>(this.base(athleteId), request);
  }

  unmatch(athleteId: string, activityId: string): Observable<Activity> {
    return this.http.delete<Activity>(`${this.base(athleteId)}/${activityId}/match`);
  }

  importFile(athleteId: string, file: File): Observable<Activity> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Activity>(`${this.base(athleteId)}/import-file`, form);
  }

  route(athleteId: string, activityId: string): Observable<number[][]> {
    return this.http.get<number[][]>(`${this.base(athleteId)}/${activityId}/route`);
  }
}
