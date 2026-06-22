import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RaceObjective, RaceObjectiveRequest } from '../models/race.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class RaceService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/races`;
  }

  list(athleteId: string): Observable<RaceObjective[]> {
    return this.http.get<RaceObjective[]>(this.base(athleteId));
  }
  create(athleteId: string, body: RaceObjectiveRequest): Observable<RaceObjective> {
    return this.http.post<RaceObjective>(this.base(athleteId), body);
  }
  delete(athleteId: string, raceId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(athleteId)}/${raceId}`);
  }
}
