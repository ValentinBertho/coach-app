import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/athlete.model';
import {
  Athlete1rm,
  CalculatedStrength,
  E1rmHistory,
  E1rmResult,
  PpExercise,
  PpExerciseRequest,
  RmFormula,
  ScheduledStrength,
  StrengthSession,
  StrengthStructure,
} from '../models/strength.model';
import { AuthService } from './auth.service';

/** Module Préparation Physique : exercices, séances, 1RM, calculs (cf. DARI Lab). */
@Injectable({ providedIn: 'root' })
export class StrengthService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private club(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}`;
  }

  // --- Exercices ---
  listExercises(opts: { category?: string; level?: string; muscle?: string; equipment?: string; q?: string; page?: number } = {}): Observable<PageResponse<PpExercise>> {
    let params = new HttpParams().set('page', opts.page ?? 0);
    for (const k of ['category', 'level', 'muscle', 'equipment', 'q'] as const) {
      if (opts[k]) params = params.set(k, opts[k] as string);
    }
    return this.http.get<PageResponse<PpExercise>>(`${this.club()}/pp/exercises`, { params });
  }

  createExercise(body: PpExerciseRequest): Observable<PpExercise> {
    return this.http.post<PpExercise>(`${this.club()}/pp/exercises`, body);
  }

  getExercise(id: string): Observable<PpExercise> {
    return this.http.get<PpExercise>(`${this.club()}/pp/exercises/${id}`);
  }

  // --- Séances ---
  listSessions(q?: string, page = 0): Observable<PageResponse<StrengthSession>> {
    let params = new HttpParams().set('page', page);
    if (q) params = params.set('q', q);
    return this.http.get<PageResponse<StrengthSession>>(`${this.club()}/pp/sessions`, { params });
  }

  createSession(body: { name: string; notes?: string }): Observable<StrengthSession> {
    return this.http.post<StrengthSession>(`${this.club()}/pp/sessions`, body);
  }

  getSession(id: string): Observable<StrengthSession> {
    return this.http.get<StrengthSession>(`${this.club()}/pp/sessions/${id}`);
  }

  putStructure(id: string, structure: StrengthStructure): Observable<StrengthSession> {
    return this.http.put<StrengthSession>(`${this.club()}/pp/sessions/${id}/structure`, { structure });
  }

  // --- Calculs ---
  calcE1rm(body: { weight: number; reps: number; rir?: number; rpe?: number; formula?: RmFormula }): Observable<E1rmResult> {
    return this.http.post<E1rmResult>(`${this.club()}/pp/calc/e1rm`, body);
  }

  // --- 1RM athlète + suivi ---
  list1rm(athleteId: string): Observable<Athlete1rm[]> {
    return this.http.get<Athlete1rm[]>(`${this.club()}/athletes/${athleteId}/pp/1rm`);
  }

  set1rm(athleteId: string, body: { exerciseId: string; rmKg: number }): Observable<Athlete1rm> {
    return this.http.put<Athlete1rm>(`${this.club()}/athletes/${athleteId}/pp/1rm`, body);
  }

  e1rmHistory(athleteId: string, exerciseId: string): Observable<E1rmHistory[]> {
    return this.http.get<E1rmHistory[]>(`${this.club()}/athletes/${athleteId}/pp/1rm/${exerciseId}/history`);
  }

  calculatedSession(athleteId: string, sessionId: string): Observable<CalculatedStrength> {
    return this.http.get<CalculatedStrength>(`${this.club()}/athletes/${athleteId}/pp/sessions/${sessionId}/calculated`);
  }

  scheduleSession(athleteId: string, sessionId: string, body: { date: string; fieldsPreset?: string }): Observable<ScheduledStrength> {
    return this.http.post<ScheduledStrength>(`${this.club()}/athletes/${athleteId}/pp/sessions/${sessionId}/schedule`, body);
  }

  scheduledCalendar(athleteId: string, from: string, to: string): Observable<ScheduledStrength[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ScheduledStrength[]>(`${this.club()}/athletes/${athleteId}/pp/scheduled`, { params });
  }
}
