import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
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

  import(athleteId: string, request: ActivityImportRequest): Observable<Activity> {
    return this.http.post<Activity>(this.base(athleteId), request);
  }

  unmatch(athleteId: string, activityId: string): Observable<Activity> {
    return this.http.delete<Activity>(`${this.base(athleteId)}/${activityId}/match`);
  }
}
