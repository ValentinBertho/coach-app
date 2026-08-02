import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, reduce } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/athlete.model';
import {
  Athlete1rm,
  CalculatedStrength,
  CycleStructure,
  E1rmHistory,
  E1rmResult,
  PpExercise,
  PpExerciseRequest,
  RmFormula,
  ScheduledStrength,
  StrengthCycle,
  StrengthLoadPoint,
  StrengthPrescriptionView,
  StrengthSession,
  StrengthStructure,
  StrengthTest,
  StrengthTestRequest,
} from '../models/strength.model';
import { AuthService } from './auth.service';

/** Module Préparation Physique : exercices, séances, 1RM, calculs (cf. Darilab). */
@Injectable({ providedIn: 'root' })
export class StrengthService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private club(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}`;
  }

  /**
   * Taille de page demandée : les bibliothèques (séances de force, exercices) s'affichent d'un
   * bloc, sans pagination visible. Aucun écran ne réclamait la page suivante — au-delà de la
   * première, séances et exercices devenaient tout simplement invisibles.
   */
  private static readonly PAGE_SIZE = 200;

  // --- Exercices ---
  listExercises(opts: { category?: string; level?: string; muscle?: string; equipment?: string; q?: string; page?: number } = {}): Observable<PageResponse<PpExercise>> {
    let params = new HttpParams()
      .set('page', opts.page ?? 0)
      .set('size', StrengthService.PAGE_SIZE);
    for (const k of ['category', 'level', 'muscle', 'equipment', 'q'] as const) {
      if (opts[k]) params = params.set(k, opts[k] as string);
    }
    return this.http.get<PageResponse<PpExercise>>(`${this.club()}/pp/exercises`, { params });
  }

  /** Catalogue d'exercices complet (sélecteur de l'éditeur de séance de force). */
  listAllExercises(opts: { category?: string; level?: string; muscle?: string; equipment?: string; q?: string } = {}): Observable<PpExercise[]> {
    return this.listExercises({ ...opts, page: 0 }).pipe(
      expand((p) => (p.page + 1 < p.totalPages ? this.listExercises({ ...opts, page: p.page + 1 }) : EMPTY)),
      reduce((acc: PpExercise[], p) => [...acc, ...p.content], []),
    );
  }

  createExercise(body: PpExerciseRequest): Observable<PpExercise> {
    return this.http.post<PpExercise>(`${this.club()}/pp/exercises`, body);
  }

  updateExercise(id: string, body: PpExerciseRequest): Observable<PpExercise> {
    return this.http.put<PpExercise>(`${this.club()}/pp/exercises/${id}`, body);
  }

  getExercise(id: string): Observable<PpExercise> {
    return this.http.get<PpExercise>(`${this.club()}/pp/exercises/${id}`);
  }

  // --- Séances ---
  listSessions(q?: string, page = 0): Observable<PageResponse<StrengthSession>> {
    let params = new HttpParams().set('page', page).set('size', StrengthService.PAGE_SIZE);
    if (q) params = params.set('q', q);
    return this.http.get<PageResponse<StrengthSession>>(`${this.club()}/pp/sessions`, { params });
  }

  /** Bibliothèque de force complète : on enchaîne les pages jusqu'à la dernière. */
  listAllSessions(q?: string): Observable<StrengthSession[]> {
    return this.listSessions(q, 0).pipe(
      expand((p) => (p.page + 1 < p.totalPages ? this.listSessions(q, p.page + 1) : EMPTY)),
      reduce((acc: StrengthSession[], p) => [...acc, ...p.content], []),
    );
  }

  createSession(body: { name: string; notes?: string }): Observable<StrengthSession> {
    return this.http.post<StrengthSession>(`${this.club()}/pp/sessions`, body);
  }

  /**
   * Renomme une séance de force (et ses notes / épinglage). Sans ce chemin, une séance dupliquée
   * restait « … (copie) » pour toujours : aucun écran n'exposait la mise à jour des métadonnées.
   */
  updateSession(id: string, body: { name: string; notes?: string | null; favorite?: boolean }): Observable<StrengthSession> {
    return this.http.put<StrengthSession>(`${this.club()}/pp/sessions/${id}`, body);
  }

  getSession(id: string): Observable<StrengthSession> {
    return this.http.get<StrengthSession>(`${this.club()}/pp/sessions/${id}`);
  }

  /** Duplique une séance de force (structure + notes). Renvoie la copie « {nom} (copie) ». */
  duplicateSession(id: string): Observable<StrengthSession> {
    return this.http.post<StrengthSession>(`${this.club()}/pp/sessions/${id}/duplicate`, {});
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

  // --- Tests 1RM (4 protocoles) ---
  listTests(athleteId: string, exerciseId?: string): Observable<StrengthTest[]> {
    let params = new HttpParams();
    if (exerciseId) params = params.set('exerciseId', exerciseId);
    return this.http.get<StrengthTest[]>(`${this.club()}/athletes/${athleteId}/pp/tests`, { params });
  }

  recordTest(athleteId: string, body: StrengthTestRequest): Observable<StrengthTest> {
    return this.http.post<StrengthTest>(`${this.club()}/athletes/${athleteId}/pp/tests`, body);
  }

  // --- Charge interne (UA méca/métab) ---
  loadTracking(athleteId: string): Observable<StrengthLoadPoint[]> {
    return this.http.get<StrengthLoadPoint[]>(`${this.club()}/athletes/${athleteId}/pp/load`);
  }

  calculatedSession(athleteId: string, sessionId: string): Observable<CalculatedStrength> {
    return this.http.get<CalculatedStrength>(`${this.club()}/athletes/${athleteId}/pp/sessions/${sessionId}/calculated`);
  }

  /** Aperçu live des charges d'une structure en cours d'édition (non enregistrée). */
  calculatePreview(athleteId: string, structure: StrengthStructure): Observable<CalculatedStrength> {
    return this.http.post<CalculatedStrength>(
      `${this.club()}/athletes/${athleteId}/pp/sessions/calculated-preview`, { structure });
  }

  scheduleSession(athleteId: string, sessionId: string, body: { date: string; fieldsPreset?: string }): Observable<ScheduledStrength> {
    return this.http.post<ScheduledStrength>(`${this.club()}/athletes/${athleteId}/pp/sessions/${sessionId}/schedule`, body);
  }

  scheduledCalendar(athleteId: string, from: string, to: string): Observable<ScheduledStrength[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ScheduledStrength[]>(`${this.club()}/athletes/${athleteId}/pp/scheduled`, { params });
  }

  /** Prescription figée d'une séance de force planifiée (snapshot + charges calculées). */
  scheduledPrescription(athleteId: string, scheduledId: string): Observable<StrengthPrescriptionView> {
    return this.http.get<StrengthPrescriptionView>(
      `${this.club()}/athletes/${athleteId}/pp/scheduled/${scheduledId}/prescription`);
  }

  /** Déplace une séance de force planifiée vers une autre date (glisser-déposer coach). */
  rescheduleScheduled(athleteId: string, scheduledId: string, scheduledDate: string): Observable<ScheduledStrength> {
    return this.http.patch<ScheduledStrength>(
      `${this.club()}/athletes/${athleteId}/pp/scheduled/${scheduledId}/reschedule`, { scheduledDate });
  }

  /** Accusé de lecture du retour athlète sur une séance de force (file « retours à traiter »). */
  markScheduledReviewed(athleteId: string, scheduledId: string, reviewed = true): Observable<ScheduledStrength> {
    const params = new HttpParams().set('reviewed', reviewed);
    return this.http.patch<ScheduledStrength>(
      `${this.club()}/athletes/${athleteId}/pp/scheduled/${scheduledId}/reviewed`, null, { params });
  }

  /** Déprogramme une séance de force du calendrier de l'athlète. */
  deleteScheduled(athleteId: string, scheduledId: string): Observable<void> {
    return this.http.delete<void>(`${this.club()}/athletes/${athleteId}/pp/scheduled/${scheduledId}`);
  }

  // --- Cycles ---
  listCycles(): Observable<StrengthCycle[]> {
    return this.http.get<StrengthCycle[]>(`${this.club()}/pp/cycles`);
  }

  createCycle(body: { name: string; weeks: number; objective?: string | null; structure: CycleStructure }): Observable<StrengthCycle> {
    return this.http.post<StrengthCycle>(`${this.club()}/pp/cycles`, body);
  }

  assignCycle(cycleId: string, athleteId: string, startDate: string): Observable<{ scheduled: number }> {
    return this.http.post<{ scheduled: number }>(`${this.club()}/pp/cycles/${cycleId}/assign/${athleteId}`, { startDate });
  }
}
