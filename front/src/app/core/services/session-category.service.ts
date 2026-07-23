import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CategoryDomain, SessionCategory, SessionCategoryRequest } from '../models/session-category.model';
import { AuthService } from './auth.service';

/**
 * Catégories de bibliothèque (CRUD scopé club), unifiées sur les trois domaines (QA1).
 * Le domaine par défaut reste COURSE (rétrocompatible) ; `domain` cible prépa physique / éducatifs.
 */
@Injectable({ providedIn: 'root' })
export class SessionCategoryService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/session-categories`;
  }

  private domainParams(domain?: CategoryDomain): { params?: HttpParams } {
    return domain ? { params: new HttpParams().set('domain', domain) } : {};
  }

  list(domain?: CategoryDomain): Observable<SessionCategory[]> {
    return this.http.get<SessionCategory[]>(this.base(), this.domainParams(domain));
  }
  create(body: SessionCategoryRequest, domain?: CategoryDomain): Observable<SessionCategory> {
    return this.http.post<SessionCategory>(this.base(), body, this.domainParams(domain));
  }
  update(id: string, body: SessionCategoryRequest): Observable<SessionCategory> {
    return this.http.put<SessionCategory>(`${this.base()}/${id}`, body);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
}
