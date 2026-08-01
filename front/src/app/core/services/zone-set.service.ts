import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ZoneSet, ZoneSetRequest } from '../models/zone-set.model';
import { AuthService } from './auth.service';

/**
 * Modèles de zones du club : plusieurs échelles nommées, dont une par défaut, appliquées
 * athlète par athlète.
 */
@Injectable({ providedIn: 'root' })
export class ZoneSetService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/zone-sets`;
  }
  private athleteUrl(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}/zone-set`;
  }

  list(): Observable<ZoneSet[]> {
    return this.http.get<ZoneSet[]>(this.base());
  }
  create(body: ZoneSetRequest): Observable<ZoneSet> {
    return this.http.post<ZoneSet>(this.base(), body);
  }
  rename(id: string, name: string): Observable<ZoneSet> {
    return this.http.put<ZoneSet>(`${this.base()}/${id}`, { name });
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }

  /** Modèle appliqué à un athlète ('' = jeu par défaut du club). */
  ofAthlete(athleteId: string): Observable<string> {
    return this.http.get<{ zoneSetId: string | null }>(this.athleteUrl(athleteId))
      .pipe(map((r) => r.zoneSetId ?? ''));
  }

  /** Applique un modèle à un athlète ('' = revenir au jeu par défaut). */
  applyToAthlete(athleteId: string, zoneSetId: string): Observable<void> {
    return this.http.put<void>(this.athleteUrl(athleteId), { zoneSetId: zoneSetId || null });
  }
}
