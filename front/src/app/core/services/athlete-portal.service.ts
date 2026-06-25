import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RaceObjective } from '../models/race.model';
import { Unavailability } from '../models/unavailability.model';
import { Workout, WorkoutStatus } from '../models/workout.model';
import { CalculatedStrength, E1rmHistory, MyOneRm, Progression, ScheduledStrength, StrengthResultEntry, StrengthStructure } from '../models/strength.model';

export interface WorkoutFeedback {
  status?: WorkoutStatus;
  rpe?: number | null;
  fatigue?: number | null;
  pain?: number | null;
  comment?: string | null;
}

export interface StrengthPrescriptionView {
  snapshot: StrengthStructure;
  calculated: CalculatedStrength | null;
  requiredFields: Record<string, boolean> | null;
}

export interface StrengthFeedback {
  completed?: boolean;
  sessionRpe?: number | null;
  fatigue?: number | null;
  pain?: number | null;
  comment?: string | null;
}

/** API du portail athlète (PWA) : scoping serveur par l'athleteId du token. */
@Injectable({ providedIn: 'root' })
export class AthletePortalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/me`;

  today(date?: string): Observable<Workout[]> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<Workout[]>(`${this.base}/today`, { params });
  }

  /** Prochaine course (204 → null). */
  nextRace(): Observable<RaceObjective | null> {
    return this.http.get<RaceObjective | null>(`${this.base}/next-race`);
  }

  feedback(workoutId: string, body: WorkoutFeedback): Observable<Workout> {
    return this.http.patch<Workout>(`${this.base}/workouts/${workoutId}/feedback`, body);
  }

  /** Calendrier course de l'athlète sur une plage. */
  workouts(from: string, to: string): Observable<Workout[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<Workout[]>(`${this.base}/workouts`, { params });
  }

  /** L'athlète déplace une séance course (change la date, jamais le contenu). */
  moveWorkout(workoutId: string, scheduledDate: string): Observable<Workout> {
    return this.http.patch<Workout>(`${this.base}/workouts/${workoutId}/move`, { scheduledDate });
  }

  /** L'athlète déplace une séance de force planifiée. */
  ppMove(scheduledId: string, scheduledDate: string): Observable<ScheduledStrength> {
    return this.http.patch<ScheduledStrength>(`${this.base}/pp/scheduled/${scheduledId}/move`, { scheduledDate });
  }

  /** Mes indisponibilités (en cours/à venir). */
  unavailabilities(): Observable<Unavailability[]> {
    return this.http.get<Unavailability[]>(`${this.base}/unavailabilities`);
  }

  /** Mon profil de force : 1RM courant par exercice (lecture seule). */
  my1rm(): Observable<MyOneRm[]> {
    return this.http.get<MyOneRm[]>(`${this.base}/pp/1rm`);
  }

  /** Historique e1RM d'un exercice (courbe de progression). */
  my1rmHistory(exerciseId: string): Observable<E1rmHistory[]> {
    return this.http.get<E1rmHistory[]>(`${this.base}/pp/1rm/${exerciseId}/history`);
  }

  // --- Préparation physique (séances de force planifiées) ---
  ppScheduled(from: string, to: string): Observable<ScheduledStrength[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ScheduledStrength[]>(`${this.base}/pp/scheduled`, { params });
  }

  ppPrescription(scheduledId: string): Observable<StrengthPrescriptionView> {
    return this.http.get<StrengthPrescriptionView>(`${this.base}/pp/scheduled/${scheduledId}/prescription`);
  }

  ppFeedback(scheduledId: string, body: StrengthFeedback): Observable<ScheduledStrength> {
    return this.http.patch<ScheduledStrength>(`${this.base}/pp/scheduled/${scheduledId}/feedback`, body);
  }

  ppResults(scheduledId: string, results: StrengthResultEntry[]): Observable<E1rmHistory[]> {
    return this.http.post<E1rmHistory[]>(`${this.base}/pp/scheduled/${scheduledId}/results`, results);
  }

  /** Suggestion de progression du coach après une séance de force réalisée. */
  ppProgression(scheduledId: string): Observable<Progression> {
    return this.http.get<Progression>(`${this.base}/pp/scheduled/${scheduledId}/progression`);
  }

  /** RGPD — export des données personnelles (portabilité). */
  export(): Observable<unknown> {
    return this.http.get<unknown>(`${this.base}/export`);
  }

  /** RGPD — suppression du compte et des données (droit à l'oubli). */
  deleteAccount(): Observable<void> {
    return this.http.delete<void>(this.base);
  }
}
