import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Compliance,
  PlanFromRaceParams,
  PlanPreview,
  Proposal,
  Timeline,
  Trajectory,
  WeekOutlook,
} from '../models/decision.model';
import { AuthService } from './auth.service';

/**
 * Les lectures qui concluent, côté coach — et la file de propositions.
 *
 * <p>Une seule règle structure ce service : <b>rien ne s'applique sans `accept`</b>. Il n'existe
 * délibérément aucune méthode qui prendrait une suggestion et la poserait dans le calendrier ; la
 * garantie tient parce qu'elle n'a pas d'exception, pas parce qu'on y fait attention.</p>
 */
@Injectable({ providedIn: 'root' })
export class DecisionService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  /**
   * Nombre de propositions en attente, partagé — même rôle que le compteur de retours : le coach
   * doit voir qu'il a des décisions à prendre sans ouvrir l'écran qui les porte.
   */
  readonly pendingProposals = signal(0);

  private club(): string {
    return this.auth.clubId() ?? '';
  }

  private athleteUrl(athleteId: string): string {
    return `${environment.apiUrl}/clubs/${this.club()}/athletes/${athleteId}`;
  }

  // --- Propositions ----------------------------------------------------------

  /** Les propositions en attente sur mon périmètre. */
  proposals(scope: 'all' | 'mine' | 'private' | 'club' = 'mine'): Observable<Proposal[]> {
    const params = new HttpParams().set('scope', scope);
    return this.http
      .get<Proposal[]>(`${environment.apiUrl}/clubs/${this.club()}/proposals`, { params })
      .pipe(tap((list) => this.pendingProposals.set(list.length)));
  }

  /** J'accepte : l'effet est appliqué, et la décision porte mon nom. */
  accept(proposalId: string): Observable<Proposal> {
    return this.http
      .post<Proposal>(`${environment.apiUrl}/clubs/${this.club()}/proposals/${proposalId}/accept`, {})
      .pipe(tap(() => this.pendingProposals.update((n) => Math.max(0, n - 1))));
  }

  /** Je refuse. La proposition est conservée : la même cause ne reviendra pas. */
  dismiss(proposalId: string): Observable<Proposal> {
    return this.http
      .post<Proposal>(`${environment.apiUrl}/clubs/${this.club()}/proposals/${proposalId}/dismiss`, {})
      .pipe(tap(() => this.pendingProposals.update((n) => Math.max(0, n - 1))));
  }

  // --- Lectures qui concluent ------------------------------------------------

  /** « Séance tenue ? » — le verdict bloc par bloc. */
  compliance(athleteId: string, workoutId: string): Observable<Compliance> {
    return this.http.get<Compliance>(`${this.athleteUrl(athleteId)}/workouts/${workoutId}/compliance`);
  }

  /**
   * Trajectoire vers la prochaine course.
   *
   * <p>Le serveur répond `204` quand l'athlète n'a pas d'objectif : Angular rend alors `null`, et
   * l'appelant se tait. Une absence d'objectif n'est pas une erreur.</p>
   */
  trajectory(athleteId: string): Observable<Trajectory | null> {
    return this.http.get<Trajectory | null>(`${this.athleteUrl(athleteId)}/trajectory`);
  }

  /** La semaine prévue, jugée avant d'être courue. */
  weekOutlook(athleteId: string, week?: string): Observable<WeekOutlook> {
    let params = new HttpParams();
    if (week) params = params.set('week', week);
    return this.http.get<WeekOutlook>(`${this.athleteUrl(athleteId)}/week-outlook`, { params });
  }

  /** La frise : six mois de suivi rassemblés. */
  timeline(athleteId: string, from?: string, to?: string): Observable<Timeline> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<Timeline>(`${this.athleteUrl(athleteId)}/timeline`, { params });
  }

  // --- Le plan qui part de la course -----------------------------------------

  /** Aperçu de la trame. N'écrit rien. */
  planPreview(athleteId: string, raceId: string, params: PlanFromRaceParams): Observable<PlanPreview> {
    return this.http.post<PlanPreview>(
      `${this.athleteUrl(athleteId)}/races/${raceId}/plan/preview`, params);
  }

  /** Pose la trame dans le calendrier. Geste explicite du coach, jamais une conséquence de l'aperçu. */
  planApply(athleteId: string, raceId: string, params: PlanFromRaceParams): Observable<PlanPreview> {
    return this.http.post<PlanPreview>(
      `${this.athleteUrl(athleteId)}/races/${raceId}/plan/apply`, params);
  }

  /** Enregistre le chrono réalisé : sur une distance de référence, il devient une performance. */
  recordRaceResult(athleteId: string, raceId: string, timeSeconds: number): Observable<Trajectory | null> {
    return this.http.post<Trajectory | null>(
      `${this.athleteUrl(athleteId)}/races/${raceId}/result`, { timeSeconds });
  }

  /** Demande des propositions de report pour les séances manquées des derniers jours. */
  catchUp(athleteId: string, days = 14): Observable<Proposal[]> {
    const params = new HttpParams().set('days', days);
    return this.http.post<Proposal[]>(`${this.athleteUrl(athleteId)}/catch-up`, {}, { params });
  }
}
