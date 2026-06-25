import { HttpClient, HttpParams } from '@angular/common/http';
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

export type AlertSeverity = 'RED' | 'ORANGE';

export interface CoachAlert {
  athleteId: string;
  athleteName: string;
  discipline: 'ROUTE' | 'TRAIL';
  severity: AlertSeverity;
  type: string;
  title: string;
  detail: string;
}

@Injectable({ providedIn: 'root' })
export class CoachDashboardService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  get(): Observable<CoachDashboard> {
    return this.http.get<CoachDashboard>(`${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard`);
  }

  /** Périmètre : all (club) | mine (mes athlètes) | private (mes privés) | club (mes athlètes club). */
  form(scope: 'all' | 'mine' | 'private' | 'club' = 'all'): Observable<CoachFormDashboard> {
    const params = new HttpParams().set('scope', scope);
    return this.http.get<CoachFormDashboard>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard/form`,
      { params },
    );
  }

  /** File d'alertes actionnables (douleur, charge, séances manquées, silence). */
  alerts(scope: 'all' | 'mine' | 'private' | 'club' = 'all'): Observable<CoachAlert[]> {
    const params = new HttpParams().set('scope', scope);
    return this.http.get<CoachAlert[]>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard/alerts`,
      { params },
    );
  }
}
