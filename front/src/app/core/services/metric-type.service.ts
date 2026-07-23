import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MetricType, MetricTypeRequest } from '../models/metric-type.model';
import { AuthService } from './auth.service';

/** Catalogue de métriques de zone (global + custom club). */
@Injectable({ providedIn: 'root' })
export class MetricTypeService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/metric-types`;
  }

  list(): Observable<MetricType[]> {
    return this.http.get<MetricType[]>(this.base());
  }
  create(body: MetricTypeRequest): Observable<MetricType> {
    return this.http.post<MetricType>(this.base(), body);
  }
}
