import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TrainingGroup } from '../models/training-group.model';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class TrainingGroupService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/groups`;
  }

  list(): Observable<TrainingGroup[]> {
    return this.http.get<TrainingGroup[]>(this.base());
  }
  create(name: string): Observable<TrainingGroup> {
    return this.http.post<TrainingGroup>(this.base(), { name });
  }
  rename(id: string, name: string): Observable<TrainingGroup> {
    return this.http.put<TrainingGroup>(`${this.base()}/${id}`, { name });
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
}
