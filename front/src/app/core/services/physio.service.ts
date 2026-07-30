import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Performance, PhysioProfile, Vdot } from '../models/physio.model';
import { AuthService } from './auth.service';

/**
 * Profil physiologique Darilab d'un athlète : seuils, performances et VDOT.
 * Scoping tenant : /clubs/{clubId}/athletes/{athleteId}/...
 */
@Injectable({ providedIn: 'root' })
export class PhysioService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}`;
  }

  profile(athleteId: string): Observable<PhysioProfile> {
    return this.http.get<PhysioProfile>(`${this.base(athleteId)}/physio`);
  }

  updateProfile(athleteId: string, body: Partial<PhysioProfile>): Observable<PhysioProfile> {
    return this.http.put<PhysioProfile>(`${this.base(athleteId)}/physio`, body);
  }

  vdot(athleteId: string): Observable<Vdot> {
    return this.http.get<Vdot>(`${this.base(athleteId)}/vdot`);
  }

  /** Test de Vitesse Critique : calcule la VC (+ D') depuis plusieurs efforts. */
  vcTest(
    athleteId: string,
    body: { trials: { distanceM: number; timeS: number }[]; applyToProfile: boolean },
  ): Observable<{ vcMs: number; vcKmh: number; dPrimeM: number }> {
    return this.http.post<{ vcMs: number; vcKmh: number; dPrimeM: number }>(
      `${this.base(athleteId)}/vc-test`, body,
    );
  }

  performances(athleteId: string): Observable<Performance[]> {
    return this.http.get<Performance[]>(`${this.base(athleteId)}/performances`);
  }

  addPerformance(
    athleteId: string,
    body: { distance: string; timeSeconds: number; dateSet?: string | null },
  ): Observable<Performance> {
    return this.http.post<Performance>(`${this.base(athleteId)}/performances`, body);
  }

  /** Retire un record de l'historique (le VDOT est recalculé côté serveur). */
  /** Corrige un record (temps, date) — le VDOT et les zones se resynchronisent côté serveur. */
  updatePerformance(
    athleteId: string,
    performanceId: string,
    body: { distance: string; timeSeconds: number; dateSet?: string | null },
  ): Observable<Performance> {
    return this.http.put<Performance>(`${this.base(athleteId)}/performances/${performanceId}`, body);
  }

  deletePerformance(athleteId: string, performanceId: string): Observable<void> {
    return this.http.delete<void>(`${this.base(athleteId)}/performances/${performanceId}`);
  }
}
