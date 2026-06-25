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

  putStructure(
    templateId: string,
    body: { discipline?: string | null; categoryId?: string | null; favorite?: boolean; structure: SessionStructure },
  ): Observable<CourseStructureResponse> {
    return this.http.put<CourseStructureResponse>(`${this.club()}/workout-templates/${templateId}/structure`, body);
  }

  sessionCalc(
    athleteId: string,
    body: { ref: PrescriptionRef; minPct: number; maxPct: number; reps?: number | null; distanceM?: number | null; durationS?: number | null },
  ): Observable<CalculatedBlock> {
    return this.http.post<CalculatedBlock>(`${this.club()}/athletes/${athleteId}/session-calc`, body);
  }

  /** Assigne un modèle de séance course au calendrier d'un athlète (snapshot + allures figées). */
  schedule(athleteId: string, templateId: string, body: { date: string }): Observable<unknown> {
    return this.http.post(`${this.club()}/athletes/${athleteId}/workout-templates/${templateId}/schedule`, body);
  }
}
