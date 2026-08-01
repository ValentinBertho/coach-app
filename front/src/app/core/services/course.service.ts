import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CalculatedBlock,
  CourseStructureResponse,
  PrescriptionRef,
  SessionStructure,
} from '../models/course.model';
import { AuthService } from './auth.service';
import { Workout } from '../models/workout.model';

/** Structure Darilab des séances course (prescription en fourchettes) + calculateur. */
@Injectable({ providedIn: 'root' })
export class CourseService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private club(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}`;
  }

  getStructure(templateId: string): Observable<CourseStructureResponse> {
    return this.http.get<CourseStructureResponse>(`${this.club()}/workout-templates/${templateId}/structure`);
  }

  /**
   * Enregistre la structure et, si le payload les porte, l'identité du modèle. Les métadonnées
   * absentes sont laissées en place côté serveur ; `clearCategory` est le seul moyen de ranger
   * une séance « sans catégorie ».
   */
  putStructure(
    templateId: string,
    body: {
      name?: string | null; title?: string | null;
      discipline?: string | null; categoryId?: string | null; clearCategory?: boolean;
      favorite?: boolean; notes?: string | null; structure?: SessionStructure;
    },
  ): Observable<CourseStructureResponse> {
    return this.http.put<CourseStructureResponse>(`${this.club()}/workout-templates/${templateId}/structure`, body);
  }

  /** Verse une séance construite au calendrier dans la bibliothèque (nouveau modèle). */
  saveWorkoutAsTemplate(
    athleteId: string, workoutId: string,
    body: { title: string; name?: string | null; categoryId?: string | null },
  ): Observable<CourseStructureResponse> {
    return this.http.post<CourseStructureResponse>(
      `${this.club()}/athletes/${athleteId}/workouts/${workoutId}/save-as-template`, body);
  }

  sessionCalc(
    athleteId: string,
    body: {
      zoneId?: string | null;
      hrZoneId?: string | null;
      ref?: PrescriptionRef | null;
      minPct?: number | null;
      maxPct?: number | null;
      reps?: number | null;
      distanceM?: number | null;
      durationS?: number | null;
    },
  ): Observable<CalculatedBlock> {
    return this.http.post<CalculatedBlock>(`${this.club()}/athletes/${athleteId}/session-calc`, body);
  }

  /** Assigne un modèle de séance course au calendrier d'un athlète (snapshot + allures figées). */
  /** Planifie un modèle : la séance renvoyée porte les cibles calculées pour CET athlète. */
  schedule(athleteId: string, templateId: string, body: { date: string }): Observable<Workout> {
    return this.http.post<Workout>(
      `${this.club()}/athletes/${athleteId}/workout-templates/${templateId}/schedule`, body);
  }
}
