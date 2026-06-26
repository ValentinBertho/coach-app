import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MesocycleTemplate, MesocycleTemplateRequest } from '../models/mesocycle-template.model';
import { AuthService } from './auth.service';

/** Modèles de mésocycle réutilisables (« méso type »). */
@Injectable({ providedIn: 'root' })
export class MesocycleTemplateService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/mesocycle-templates`;
  }

  list(): Observable<MesocycleTemplate[]> {
    return this.http.get<MesocycleTemplate[]>(this.base());
  }
  create(body: MesocycleTemplateRequest): Observable<MesocycleTemplate> {
    return this.http.post<MesocycleTemplate>(this.base(), body);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
}
