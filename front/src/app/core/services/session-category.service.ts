import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SessionCategory, SessionCategoryRequest } from '../models/session-category.model';
import { AuthService } from './auth.service';

/** Catégories de la bibliothèque de séances course (CRUD scopé club). */
@Injectable({ providedIn: 'root' })
export class SessionCategoryService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/session-categories`;
  }

  list(): Observable<SessionCategory[]> {
    return this.http.get<SessionCategory[]>(this.base());
  }
  create(body: SessionCategoryRequest): Observable<SessionCategory> {
    return this.http.post<SessionCategory>(this.base(), body);
  }
  update(id: string, body: SessionCategoryRequest): Observable<SessionCategory> {
    return this.http.put<SessionCategory>(`${this.base()}/${id}`, body);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
}
