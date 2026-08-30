import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  referenceRule,
  TrainingZone,
  TrainingZoneRequest,
  ZoneMetricsRequest,
  ZoneRuleRequest,
} from '../models/training-zone.model';
import { AuthService } from './auth.service';

/** Zones de travail paramétrables du club (CRUD, réordonnancement, métriques portées). */
@Injectable({ providedIn: 'root' })
export class TrainingZoneService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/training-zones`;
  }

  /**
   * Zones d'un modèle (`setId`), ou celles de l'échelle appliquée à un athlète (`athleteId`).
   * Sans paramètre : le jeu par défaut du club.
   */
  list(opts?: { setId?: string; athleteId?: string }): Observable<TrainingZone[]> {
    let params = new HttpParams();
    if (opts?.athleteId) params = params.set('athleteId', opts.athleteId);
    else if (opts?.setId) params = params.set('setId', opts.setId);
    return this.http.get<TrainingZone[]>(this.base(), { params });
  }
  create(body: TrainingZoneRequest): Observable<TrainingZone> {
    return this.http.post<TrainingZone>(this.base(), body);
  }
  update(id: string, body: TrainingZoneRequest): Observable<TrainingZone> {
    return this.http.put<TrainingZone>(`${this.base()}/${id}`, body);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base()}/${id}`);
  }
  reorder(orderedIds: string[]): Observable<void> {
    return this.http.patch<void>(`${this.base()}/reorder`, { orderedIds });
  }
  setMetrics(id: string, body: ZoneMetricsRequest): Observable<TrainingZone> {
    return this.http.put<TrainingZone>(`${this.base()}/${id}/metrics`, body);
  }
  setRule(id: string, metricId: string, body: ZoneRuleRequest): Observable<TrainingZone> {
    return this.http.put<TrainingZone>(`${this.base()}/${id}/metrics/${metricId}/rule`, body);
  }

  /**
   * Règle les deux pourcentages d'une zone.
   *
   * <p>Une zone est <b>une</b> définition physiologique exprimée en plusieurs unités : l'allure et
   * la vitesse d'un même seuil valent le même pourcentage de la même ancre. N'en corriger qu'une
   * les ferait diverger sans que rien ne le signale — le changement porte donc sur toutes les
   * métriques partageant l'ancre de référence. Et sur elles seules : la fréquence cardiaque
   * s'ancre sur la FC max avec ses propres pourcentages, lui appliquer ceux de l'allure serait
   * faux.</p>
   *
   * <p>Le serveur répercute ensuite sur les valeurs des athlètes ; l'appelant n'a qu'à relire.</p>
   *
   * @returns la zone mise à jour, une fois toutes les métriques écrites.
   */
  setPercentages(zone: TrainingZone, lowPct: number, highPct: number): Observable<TrainingZone> {
    const reference = referenceRule(zone);
    if (!reference) return throwError(() => new Error('Zone sans règle de calcul.'));

    const shared = (zone.rules ?? []).filter(
      (r) => r.anchor === reference.anchor && (r.highAnchor ?? null) === (reference.highAnchor ?? null));

    return forkJoin(
      shared.map((r) => this.setRule(zone.id, r.metricTypeId, {
        anchor: r.anchor, highAnchor: r.highAnchor, lowPct, highPct, model: r.model ?? 'CUSTOM',
      })),
    ).pipe(map((zones) => zones[zones.length - 1]));
  }
}
