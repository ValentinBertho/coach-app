import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ScheduledStrength } from '../models/strength.model';
import { TrainingGroup } from '../models/training-group.model';
import { Workout } from '../models/workout.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class TrainingGroupService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/groups`;
  }

  list(): Observable<TrainingGroup[]> {
    return this.http.get<TrainingGroup[]>(this.base());
  }
  create(name: string): Observable<TrainingGroup> {
    return this.http.post<TrainingGroup>(this.base(), { name });
  }
  rename(id: string, name: string): Observable<TrainingGroup> {
    return this.http.put<TrainingGroup>(`${this.base()}/${id}`, { name });
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
  /** Analytics agrégées d'un groupe (forme, ACWR moyen, volume, adhérence). */
  analytics(id: string, weeks = 8): Observable<GroupAnalytics> {
    return this.http.get<GroupAnalytics>(`${this.base()}/${id}/analytics?weeks=${weeks}`);
  }

  /**
   * Semaine du groupe (calendrier multi-athlètes) : un seul appel agrégé, une ligne par
   * athlète accessible, séances course et force incluses.
   */
  calendar(id: string, from: string, to: string): Observable<GroupCalendar> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<GroupCalendar>(`${this.base()}/${id}/calendar`, { params });
  }

  /**
   * Planifie une même séance pour tous les athlètes du groupe à une date.
   *
   * <p>Le serveur calcule la séance <b>pour chacun</b> — deux athlètes du même groupe n'ont pas
   * les mêmes allures — et ignore ceux sur lesquels le coach n'a pas de droit d'écriture.</p>
   */
  schedule(id: string, body: GroupScheduleRequest): Observable<GroupApplyResult> {
    return this.http.post<GroupApplyResult>(`${this.base()}/${id}/schedule`, body);
  }
}

/** Une source et une seule : séance course, ou séance de prépa physique. */
export interface GroupScheduleRequest {
  date: string;
  templateId?: string | null;
  strengthSessionId?: string | null;
}

/** Ce que la planification de groupe a réellement fait, athlètes hors périmètre compris. */
export interface GroupApplyResult {
  athletes: number;
  skipped: number;
  created: number;
}

/** Une ligne du calendrier de groupe : l'athlète et ses séances sur la plage demandée. */
export interface GroupCalendarRow {
  athleteId: string;
  firstName: string;
  lastName: string;
  canWrite: boolean;
  workouts: Workout[];
  strength: ScheduledStrength[];
}

export interface GroupCalendar {
  groupId: string;
  groupName: string;
  athletes: GroupCalendarRow[];
}

export type FormStatus = 'GREEN' | 'ORANGE' | 'RED';

export interface GroupAnalyticsRow {
  id: string;
  firstName: string;
  lastName: string;
  discipline: string;
  formStatus: FormStatus;
  fatigue: number | null;
  pain: number | null;
  acwr: number | null;
  plannedKm: number;
  realizedKm: number;
  compliancePct: number | null;
  lastFeedbackDate: string | null;
}

export interface GroupAnalytics {
  groupId: string;
  name: string;
  athleteCount: number;
  /** Répartition de forme du groupe ; `stale` = athlètes sans signal récent (comptés en vert auparavant). */
  form: { green: number; orange: number; red: number; stale: number };
  totals: { avgAcwr: number | null; plannedKm: number; realizedKm: number; compliancePct: number | null };
  athletes: GroupAnalyticsRow[];
}
