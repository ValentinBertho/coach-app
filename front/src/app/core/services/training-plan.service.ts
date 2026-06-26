import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TrainingPlan, TrainingPlanRequest } from '../models/training-plan.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class TrainingPlanService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/training-plans`;
  }

  list(): Observable<TrainingPlan[]> {
    return this.http.get<TrainingPlan[]>(this.base());
  }
  create(body: TrainingPlanRequest): Observable<TrainingPlan> {
    return this.http.post<TrainingPlan>(this.base(), body);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
  apply(id: string, athleteId: string, startDate: string): Observable<{ created: number }> {
    return this.http.post<{ created: number }>(`${this.base()}/${id}/apply`, { athleteId, startDate });
  }
  /** Applique le plan à tous les athlètes actifs d'un groupe (accessibles en écriture). */
  applyGroup(id: string, groupId: string, startDate: string): Observable<GroupApplyResult> {
    return this.http.post<GroupApplyResult>(`${this.base()}/${id}/apply-group`, { groupId, startDate });
  }

  /** Avancement d'un plan pour un athlète (semaine courante, % réalisé). */
  progress(id: string, athleteId: string): Observable<PlanProgress> {
    return this.http.get<PlanProgress>(`${this.base()}/${id}/athletes/${athleteId}/progress`);
  }
}

/** Avancement d'un plan pour un athlète. */
export interface PlanProgress {
  startDate: string;
  durationWeeks: number;
  currentWeek: number;
  totalSessions: number;
  completedSessions: number;
  percent: number;
  finished: boolean;
}

/** Résultat d'une application en masse (plan ou mésocycle) à un groupe. */
export interface GroupApplyResult {
  athletes: number;
  skipped: number;
  created: number;
}
