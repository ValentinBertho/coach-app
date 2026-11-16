import { FormTrap } from '../../shared/forms/form-trap';
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
  /** Le coaching né de cette demande est toujours en cours (dernière acceptation, relation active). */
  current: boolean;
}

/** Une fin de suivi annoncée par le coach. */
export interface PlannedEnd {
  endsAt: string;
  note: string | null;
  keepFutureWorkouts: boolean;
}

/** Le coaching en cours, vu par l'athlète. */
export interface MyTestimonial {
  coachName: string;
  body: string;
  published: boolean;
}

export interface CurrentCoaching {
  coachName: string;
  since: string;
  plannedEndAt: string | null;
  plannedEndReason: string | null;
  /** Le club auquel le suivi le rattache ; absent pour un athlète privé. */
  clubName?: string | null;
}

export interface AthleteRegistration {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  birthDate: string;
  goal?: string | null;
  /** Le coach que le visiteur venait de choisir ; la demande part à la confirmation de l'e-mail. */
  coachSlug?: string | null;
  requestMessage?: string | null;
  /** Lien d'invitation depuis lequel le visiteur s'inscrit : rejoint à la confirmation de l'e-mail. */
  inviteToken?: string | null;
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
  /** La demande préparée à l'inscription, tant que l'e-mail n'est pas confirmé ; sinon nulle. */
  pendingRequest: { coachSlug: string; coachName: string | null; message: string } | null;
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

  register(body: AthleteRegistration, trap?: FormTrap): Observable<unknown> {
    return this.http.post(`${this.base}/public/athlete-registration`, body,
      { headers: trap?.headers() });
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

  /**
   * Envoie une demande. {@code switchCoach} : l'athlète, déjà suivi, confirme vouloir changer (son
   * suivi actuel s'arrêtera si ce coach accepte). {@code shareHistory} : il transmet ses sorties.
   */
  submit(coachSlug: string, message: string, offerId?: string | null,
         switchCoach = false, shareHistory = false): Observable<CoachingRequest> {
    return this.http.post<CoachingRequest>(`${this.base}/me/coaching-requests`,
      { coachSlug, message, offerId: offerId ?? null, switchCoach, shareHistory });
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

  /**
   * Le coach annonce la fin du coaching d'un de ses athlètes : 7 jours de préavis quand l'athlète
   * utilise l'application, fin immédiate sinon. Rend la date de fin.
   */
  endCoachingWith(clubId: string, athleteId: string, note: string | null,
                  keepFutureWorkouts = false): Observable<PlannedEnd> {
    return this.http.post<PlannedEnd>(
      `${this.base}/clubs/${clubId}/athletes/${athleteId}/end-relation`, { note, keepFutureWorkouts });
  }

  /** La fin annoncée, s'il y en a une (null sinon). */
  plannedEnd(clubId: string, athleteId: string): Observable<PlannedEnd | null> {
    return this.http.get<PlannedEnd | null>(`${this.base}/clubs/${clubId}/athletes/${athleteId}/end-relation`);
  }

  /** Le coach revient sur la fin annoncée. */
  cancelPlannedEnd(clubId: string, athleteId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/clubs/${clubId}/athletes/${athleteId}/end-relation`);
  }

  /** Mon coaching en cours (athlète) : coach, depuis quand, fin annoncée. Null sans coach. */
  /** Mon témoignage sur mon coach actuel, s'il y en a un. */
  myTestimonial(): Observable<MyTestimonial | null> {
    return this.http.get<MyTestimonial | null>(`${this.base}/me/coach/testimonial`);
  }

  writeTestimonial(body: string): Observable<MyTestimonial> {
    return this.http.put<MyTestimonial>(`${this.base}/me/coach/testimonial`, { body });
  }

  withdrawTestimonial(): Observable<void> {
    return this.http.delete<void>(`${this.base}/me/coach/testimonial`);
  }

  /** Quitter le club en gardant son coach (on devient son athlète privé). */
  leaveClub(): Observable<void> {
    return this.http.post<void>(`${this.base}/me/coach/leave-club`, {});
  }

  myCoach(): Observable<CurrentCoaching | null> {
    return this.http.get<CurrentCoaching | null>(`${this.base}/me/coach`);
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
