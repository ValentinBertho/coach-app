import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RunDrill, RunDrillRequest } from '../models/run-drill.model';
import { AuthService } from './auth.service';

/** Éducatifs de course (gammes techniques) — scopé club. CDC §9. */
@Injectable({ providedIn: 'root' })
export class RunDrillService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/run-drills`;
  }

  list(): Observable<RunDrill[]> {
    return this.http.get<RunDrill[]>(this.base());
  }
  create(body: RunDrillRequest): Observable<RunDrill> {
    return this.http.post<RunDrill>(this.base(), body);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
}
