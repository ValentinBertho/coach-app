import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Injury } from '../models/injury.model';
import { RaceObjective } from '../models/race.model';
import { AuthService } from './auth.service';

export interface CoachDashboard {
  activeAthletes: number;
  pendingInvitations: number;
  /** Retours d'athlètes (RPE / douleur / commentaire) non encore traités : taille de la file. */
  sessionsToReview: number;
  completedThisWeek: number;
  upcomingRaces: RaceObjective[];
}

/** Une ligne de la file « retours à traiter » (course et force unifiées). */
export interface FeedbackQueueItem {
  kind: 'COURSE' | 'STRENGTH';
  sessionId: string;
  athleteId: string;
  athleteName: string;
  title: string;
  sessionDate: string;
  rpe: number | null;
  fatigue: number | null;
  pain: number | null;
  /** Sensation générale 1–5 (séance course uniquement) ; distincte de la difficulté. */
  feel: number | null;
  /** Blessures nommées au débrief ; liste vide si aucune. */
  injuries: Injury[];
  comment: string | null;
}

/**
 * Une séance de course d'un jour donné, tous athlètes du périmètre confondus — la matière de
 * l'écran « Ma journée ». Le calendrier se lit athlète par athlète : il ne répond pas à
 * « qu'est-ce qui est prévu aujourd'hui, et pour qui ».
 */
export interface CoachDaySession {
  workoutId: string;
  athleteId: string;
  athleteName: string;
  title: string;
  type: string | null;
  status: 'PLANNED' | 'COMPLETED' | 'PARTIAL' | 'MISSED';
  scheduledDate: string;
  /** L'athlète a déplacé cette séance lui-même. */
  movedByAthlete: boolean;
  /** Un retour attend d'être traité sur cette séance. */
  awaitingReview: boolean;
}

export type FormStatus = 'GREEN' | 'ORANGE' | 'RED' | 'STALE';

export interface AthleteForm {
  id: string;
  firstName: string;
  lastName: string;
  discipline: 'ROUTE' | 'TRAIL';
  formStatus: FormStatus;
  fatigue: number | null;
  pain: number | null;
  lastFeedbackDate: string | null;
}

export interface CoachFormDashboard {
  total: number;
  route: number;
  trail: number;
  routeAthletes: AthleteForm[];
  trailAthletes: AthleteForm[];
}

export type AlertSeverity = 'RED' | 'ORANGE';

export interface CoachAlert {
  athleteId: string;
  athleteName: string;
  discipline: 'ROUTE' | 'TRAIL';
  severity: AlertSeverity;
  type: string;
  title: string;
  detail: string;
}

@Injectable({ providedIn: 'root' })
export class CoachDashboardService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  /**
   * Retours à traiter, partagé (badge de l'entrée de nav « Retours »), sur le modèle exact
   * du compteur de non-lus de la messagerie. C'est l'écran qu'un coach ouvre tous les matins :
   * il doit voir qu'il a du travail sans passer par le cockpit.
   */
  readonly pendingReviews = signal(0);

  /** KPI agrégés, restreints au même périmètre que la jauge de forme et les alertes. */
  get(scope: 'all' | 'mine' | 'private' | 'club' = 'all'): Observable<CoachDashboard> {
    const params = new HttpParams().set('scope', scope);
    return this.http.get<CoachDashboard>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard`, { params });
  }

  /**
   * Rafraîchit le compteur du badge. Toujours sur le périmètre complet : le badge de nav dit
   * « il te reste du travail », il ne doit pas dépendre du filtre choisi ailleurs.
   */
  refreshPendingReviews(): Observable<number> {
    return this.get('all').pipe(
      map((d) => d.sessionsToReview),
      tap((n) => this.pendingReviews.set(n)),
    );
  }

  /** Périmètre : all (club) | mine (mes athlètes) | private (mes privés) | club (mes athlètes club). */
  form(scope: 'all' | 'mine' | 'private' | 'club' = 'all'): Observable<CoachFormDashboard> {
    const params = new HttpParams().set('scope', scope);
    return this.http.get<CoachFormDashboard>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard/form`,
      { params },
    );
  }

  /** File « retours à traiter » : retours d'athlètes non encore vus, tout le périmètre confondu. */
  feedbackQueue(
    scope: 'all' | 'mine' | 'private' | 'club' = 'all',
    days = 14,
  ): Observable<FeedbackQueueItem[]> {
    const params = new HttpParams().set('scope', scope).set('days', days);
    return this.http.get<FeedbackQueueItem[]>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard/feedback`,
      { params },
    );
  }

  /** Vide la file : marque comme traités tous les retours du périmètre et de la fenêtre. */
  markAllFeedbackReviewed(
    scope: 'all' | 'mine' | 'private' | 'club' = 'all',
    days = 14,
  ): Observable<{ marked: number }> {
    const params = new HttpParams().set('scope', scope).set('days', days);
    return this.http.post<{ marked: number }>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard/feedback/review-all`,
      {},
      { params },
    );
  }

  /**
   * Les séances de course d'un jour, tous athlètes du périmètre confondus. Course seulement :
   * le renforcement a son propre écran et son propre débrief.
   */
  day(scope: 'all' | 'mine' | 'private' | 'club' = 'mine', date?: string): Observable<CoachDaySession[]> {
    let params = new HttpParams().set('scope', scope);
    if (date) params = params.set('date', date);
    return this.http.get<CoachDaySession[]>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard/day`,
      { params },
    );
  }

  /** File d'alertes actionnables (douleur, charge, séances manquées, silence). */
  alerts(scope: 'all' | 'mine' | 'private' | 'club' = 'all'): Observable<CoachAlert[]> {
    const params = new HttpParams().set('scope', scope);
    return this.http.get<CoachAlert[]>(
      `${environment.apiUrl}/clubs/${this.auth.clubId()}/dashboard/alerts`,
      { params },
    );
  }
}
