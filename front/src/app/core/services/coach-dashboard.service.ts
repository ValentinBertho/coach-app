import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RaceObjective } from '../models/race.model';
import { AuthService } from './auth.service';

export interface CoachDashboard {
  activeAthletes: number;
  pendingInvitations: number;
  sessionsToReview: number;
  completedThisWeek: number;
  upcomingRaces: RaceObjective[];
}

@Injectable({ providedIn: 'root' })
export class CoachDashboardService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  get(): Observable<CoachDashboard> {
    return this.http.get<CoachDashboard>(`${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard`);
  }
}
