import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Ping } from '../models/ping.model';

/**
 * Appelle l'endpoint public de santé de l'API — preuve que front ↔ back communiquent.
 */
@Injectable({ providedIn: 'root' })
export class PingService {
  private readonly http = inject(HttpClient);

  ping(): Observable<Ping> {
    return this.http.get<Ping>(`${environment.apiUrl}/public/ping`);
  }
}
