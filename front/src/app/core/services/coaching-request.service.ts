import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type CoachingRequestStatus =
  | 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'WITHDRAWN' | 'EXPIRED';

export interface CoachingRequest {
  id: string;
  status: CoachingRequestStatus;
  statusLabel: string;
  athleteName: string;
  /** En années : le coach n'a pas à détenir une date de naissance avant d'avoir accepté. */
  athleteAge: number | null;
  athleteDiscipline: string | null;
  athleteLevel: string | null;
  athleteCity: string | null;
  athleteGoal: string | null;
  coachName: string | null;
  coachSlug: string | null;
  message: string | null;
  coachQuestion: string | null;
  athleteAnswer: string | null;
  offerLabel: string | null;
  offerAmountCents: number | null;
  declineReason: string | null;
  createdAt: string;
  decidedAt: string | null;
  expiresAt: string;
  /** La fiche créée à l'acceptation ; c'est par elle que le coach agit sur la relation. */
  createdAthleteId: string | null;
}

export interface AthleteRegistration {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  birthDate: string;
  goal?: string | null;
  termsAccepted: boolean;
  healthDataConsent: boolean;
}

export interface AthleteAccount {
  id: string;
  firstName: string;
  lastName: string;
  birthDate: string;
  sex: string | null;
  discipline: string | null;
  level: string | null;
  city: string | null;
  country: string | null;
  goal: string | null;
  lookingForCoach: boolean;
}

/**
 * L'inscription libre d'un athlète, son compte, et ses demandes de coaching.
 *
 * Les demandes vivent sous `/me` et non sous une fiche : un athlète qui n'a pas encore de coach
 * n'a pas de fiche, et c'est précisément lui qui demande.
 */
@Injectable({ providedIn: 'root' })
export class CoachingRequestService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  register(body: AthleteRegistration): Observable<unknown> {
    return this.http.post(`${this.base}/public/athlete-registration`, body);
  }

  account(): Observable<AthleteAccount> {
    return this.http.get<AthleteAccount>(`${this.base}/me/account`);
  }

  updateAccount(body: Partial<AthleteAccount>): Observable<AthleteAccount> {
    return this.http.patch<AthleteAccount>(`${this.base}/me/account`, body);
  }

  // --- Côté athlète ---

  mine(): Observable<CoachingRequest[]> {
    return this.http.get<CoachingRequest[]>(`${this.base}/me/coaching-requests`);
  }

  submit(coachSlug: string, message: string, offerId?: string | null): Observable<CoachingRequest> {
    return this.http.post<CoachingRequest>(`${this.base}/me/coaching-requests`,
      { coachSlug, message, offerId: offerId ?? null });
  }

  answer(id: string, note: string): Observable<CoachingRequest> {
    return this.http.post<CoachingRequest>(`${this.base}/me/coaching-requests/${id}/answer`, { note });
  }

  withdraw(id: string): Observable<CoachingRequest> {
    return this.http.delete<CoachingRequest>(`${this.base}/me/coaching-requests/${id}`);
  }

  /**
   * L'athlète met fin à son coaching.
   *
   * Rien n'est détruit : sa fiche reste chez son coach, qui la garde en lecture. Lui redevient un
   * compte sans coach, libre de repartir dans l'annuaire.
   */
  endMyCoaching(note: string | null): Observable<void> {
    return this.http.post<void>(`${this.base}/me/coach/end`, { note });
  }

  /** Le coach met fin au coaching d'un de ses athlètes. */
  endCoachingWith(clubId: string, athleteId: string, note: string | null): Observable<void> {
    return this.http.post<void>(
      `${this.base}/clubs/${clubId}/athletes/${athleteId}/end-relation`, { note });
  }

  // --- Côté coach ---

  received(): Observable<CoachingRequest[]> {
    return this.http.get<CoachingRequest[]>(`${this.base}/me/received-requests`);
  }

  accept(id: string): Observable<CoachingRequest> {
    return this.http.post<CoachingRequest>(`${this.base}/me/received-requests/${id}/accept`, {});
  }

  decline(id: string, note: string | null): Observable<CoachingRequest> {
    return this.http.post<CoachingRequest>(`${this.base}/me/received-requests/${id}/decline`, { note });
  }

  ask(id: string, note: string): Observable<CoachingRequest> {
    return this.http.post<CoachingRequest>(`${this.base}/me/received-requests/${id}/ask`, { note });
  }

  /** La couleur d'un état. Retirée et sans réponse restent neutres : ce ne sont pas des refus. */
  badge(status: CoachingRequestStatus): string {
    switch (status) {
      case 'ACCEPTED': return 'badge-success';
      case 'PENDING': return 'badge-warning';
      case 'DECLINED': return 'badge-danger';
      default: return 'badge-neutral';
    }
  }
}
