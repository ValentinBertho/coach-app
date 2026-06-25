import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LactateStep, LactateTest, Load, LtDetection } from '../models/lactate.model';
import { AuthService } from './auth.service';

/** Tests lactate, détection LT1/LT2 et charge d'entraînement (cf. Darilab). */
@Injectable({ providedIn: 'root' })
export class LactateService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  private base(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.auth.clubId()}/athletes/${athleteId}`;
  }

  list(athleteId: string): Observable<LactateTest[]> {
    return this.http.get<LactateTest[]>(`${this.base(athleteId)}/lactate-tests`);
  }

  get(athleteId: string, testId: string): Observable<LactateTest> {
    return this.http.get<LactateTest>(`${this.base(athleteId)}/lactate-tests/${testId}`);
  }

  detect(athleteId: string, body: { lactateRest: number | null; steps: LactateStep[] }): Observable<LtDetection> {
    return this.http.post<LtDetection>(`${this.base(athleteId)}/lactate-tests/detect`, body);
  }

  create(
    athleteId: string,
    body: {
      testDate: string;
      lactateRest: number | null;
      hrRest: number | null;
      hrMax: number | null;
      applyToProfile: boolean;
      steps: LactateStep[];
    },
  ): Observable<LactateTest> {
    return this.http.post<LactateTest>(`${this.base(athleteId)}/lactate-tests`, body);
  }

  load(athleteId: string): Observable<Load> {
    return this.http.get<Load>(`${this.base(athleteId)}/load`);
  }
}
