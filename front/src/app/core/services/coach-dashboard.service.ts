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

export type FormStatus = 'GREEN' | 'ORANGE' | 'RED';

export interface AthleteForm {
  id: string;
  firstName: string;
  lastName: string;
  discipline: 'ROUTE' | 'TRAIL';
  formStatus: FormStatus;
  fatigue: number | null;
  pain: number | null;
  lastFeedbackDate: string | null;
}

export interface CoachFormDashboard {
  total: number;
  route: number;
  trail: number;
  routeAthletes: AthleteForm[];
  trailAthletes: AthleteForm[];
}

@Injectable({ providedIn: 'root' })
export class CoachDashboardService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  get(): Observable<CoachDashboard> {
    return this.http.get<CoachDashboard>(`${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard`);
  }

  form(): Observable<CoachFormDashboard> {
    return this.http.get<CoachFormDashboard>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard/form`,
    );
  }
}
