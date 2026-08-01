import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, reduce } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../models/athlete.model';
import { WorkoutTemplate, WorkoutTemplateRequest } from '../models/workout-template.model';
import { Workout } from '../models/workout.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class WorkoutTemplateService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/workout-templates`;
  }

  list(q?: string, page = 0, size = WorkoutTemplateService.PAGE_SIZE): Observable<PageResponse<WorkoutTemplate>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    return this.http.get<PageResponse<WorkoutTemplate>>(this.base(), { params });
  }

  /** Taille de page demandée : la bibliothèque s'affiche d'un bloc, sans pagination visible. */
  private static readonly PAGE_SIZE = 200;

  /**
   * Bibliothèque complète. Les écrans (bibliothèque, panneau du calendrier) n'ont jamais demandé
   * la page suivante : au-delà de la première page, les séances devenaient invisibles — d'où
   * l'impression d'un plafond. On enchaîne donc les pages jusqu'à la dernière.
   */
  listAll(q?: string): Observable<WorkoutTemplate[]> {
    return this.list(q, 0).pipe(
      expand((p) => (p.page + 1 < p.totalPages ? this.list(q, p.page + 1) : EMPTY)),
      reduce((acc: WorkoutTemplate[], p) => [...acc, ...p.content], []),
    );
  }
  get(id: string): Observable<WorkoutTemplate> {
    return this.http.get<WorkoutTemplate>(`${this.base()}/${id}`);
  }
  create(body: WorkoutTemplateRequest): Observable<WorkoutTemplate> {
    return this.http.post<WorkoutTemplate>(this.base(), body);
  }
  update(id: string, body: WorkoutTemplateRequest): Observable<WorkoutTemplate> {
    return this.http.put<WorkoutTemplate>(`${this.base()}/${id}`, body);
  }
  /** Duplique un modèle (variante d'une séance existante). Renvoie la copie « {nom} (copie) ». */
  duplicate(id: string): Observable<WorkoutTemplate> {
    return this.http.post<WorkoutTemplate>(`${this.base()}/${id}/duplicate`, {});
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
  /** Épingle / dé-épingle un modèle (favori). */
  setFavorite(id: string, favorite: boolean): Observable<WorkoutTemplate> {
    return this.http.patch<WorkoutTemplate>(`${this.base()}/${id}/favorite`, { favorite });
  }
  apply(id: string, athleteId: string, date: string): Observable<Workout> {
    return this.http.post<Workout>(`${this.base()}/${id}/apply`, { athleteId, date });
  }
  /** Applique le modèle à tout un groupe (athlètes en lecture seule ignorés côté serveur). */
  applyGroup(id: string, groupId: string, date: string): Observable<{ athletes: number; skipped: number; created: number }> {
    return this.http.post<{ athletes: number; skipped: number; created: number }>(
      `${this.base()}/${id}/apply-group`, { groupId, date });
  }
}
