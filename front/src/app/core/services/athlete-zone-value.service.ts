import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AthleteZoneValue, AthleteZoneValueRequest } from '../models/athlete-zone-value.model';
import { AuthService } from './auth.service';

/** Valeurs de zones par athlète (fiche athlète) : lecture, saisie/ajustement, resync. */
@Injectable({ providedIn: 'root' })
export class AthleteZoneValueService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/zone-values`;
  }

  list(athleteId: string): Observable<AthleteZoneValue[]> {
    return this.http.get<AthleteZoneValue[]>(this.base(athleteId));
  }
  resync(athleteId: string): Observable<AthleteZoneValue[]> {
    return this.http.post<AthleteZoneValue[]>(`${this.base(athleteId)}/resync`, {});
  }
  upsert(
    athleteId: string,
    zoneId: string,
    metricId: string,
    body: AthleteZoneValueRequest
  ): Observable<AthleteZoneValue> {
    return this.http.put<AthleteZoneValue>(`${this.base(athleteId)}/${zoneId}/${metricId}`, body);
  }
}
