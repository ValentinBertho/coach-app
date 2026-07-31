import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Activity, ActivityImportRequest, TimeInZone } from '../models/activity.model';
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

  /**
   * Rapproche l'activité d'une séance, ou la détache (`workoutId` à `null`). Un seul appel pour
   * les deux sens : l'erreur de l'algorithme n'est plus définitive.
   */
  match(athleteId: string, activityId: string, workoutId: string | null): Observable<Activity> {
    return this.http.patch<Activity>(`${this.base(athleteId)}/${activityId}/match`, { workoutId });
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

  /** Répartition du temps par zone (allure + FC) pour une activité (V2-7). */
  timeInZone(athleteId: string, activityId: string): Observable<TimeInZone> {
    return this.http.get<TimeInZone>(`${this.base(athleteId)}/${activityId}/time-in-zone`);
  }
}
