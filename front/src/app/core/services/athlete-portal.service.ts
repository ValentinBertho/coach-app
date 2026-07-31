import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { RaceObjective, RaceObjectiveRequest } from '../models/race.model';
import { WorkoutPrescription } from '../models/course.model';
import { Unavailability } from '../models/unavailability.model';
import { PhysioProfile, Performance, Vdot } from '../models/physio.model';
import { Activity } from '../models/activity.model';
import { Analytics } from './analytics.service';
import { LactateTest, Load, StrengthLoadPoint } from '../models/lactate.model';
import { Workout, WorkoutStatus, awaitsFeedback } from '../models/workout.model';
import { StravaStatus } from '../models/strava.model';
import { WellnessCheckIn, WellnessCheckInRequest } from '../models/wellness.model';
import { E1rmHistory, MyOneRm, Progression, ScheduledStrength, StrengthPrescriptionView, StrengthResultEntry } from '../models/strength.model';

// Le type vit désormais dans le modèle force (partagé coach/athlète) ; ré-export pour les
// consommateurs historiques qui l'importent depuis ce service.
export type { StrengthPrescriptionView };

export interface ActivityLog {
  activityDate: string;
  title?: string;
  distanceM?: number | null;
  durationS?: number | null;
  avgHr?: number | null;
  elevationGainM?: number | null;
}

export interface WorkoutFeedback {
  status?: WorkoutStatus;
  rpe?: number | null;
  fatigue?: number | null;
  pain?: number | null;
  comment?: string | null;
}

export interface StrengthFeedback {
  completed?: boolean;
  sessionRpe?: number | null;
  fatigue?: number | null;
  pain?: number | null;
  comment?: string | null;
}

/** Date locale au format ISO (jamais `toISOString()`, qui bascule d'un jour selon le fuseau). */
function isoDay(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** API du portail athlète (PWA) : scoping serveur par l'athleteId du token. */
@Injectable({ providedIn: 'root' })
export class AthletePortalService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/me`;

  today(date?: string): Observable<Workout[]> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<Workout[]>(`${this.base}/today`, { params });
  }

  /** Mon programme : plans attribués avec avancement. */

  /** Je consigne une sortie libre (saisie manuelle). */
  logActivity(body: ActivityLog): Observable<Activity> {
    return this.http.post<Activity>(`${this.base}/activities`, body);
  }

  /** J'importe ma propre trace (GPX/TCX). */
  importActivityFile(file: File): Observable<Activity> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<Activity>(`${this.base}/activities/import-file`, form);
  }

  /** Prochaine course (204 → null). */
  nextRace(): Observable<RaceObjective | null> {
    return this.http.get<RaceObjective | null>(`${this.base}/next-race`);
  }

  feedback(workoutId: string, body: WorkoutFeedback): Observable<Workout> {
    return this.http.patch<Workout>(`${this.base}/workouts/${workoutId}/feedback`, body);
  }

  /** Calendrier course de l'athlète sur une plage. */
  workouts(from: string, to: string): Observable<Workout[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<Workout[]>(`${this.base}/workouts`, { params });
  }

  /**
   * Séances non clôturées des `days` derniers jours, **hors aujourd'hui** (déjà porté par la
   * carte du jour). Un ressenti oublié le soir même était jusqu'ici définitivement perdu :
   * l'agenda et l'historique sont en lecture seule. C'est la donnée qui alimente la forme et
   * les alertes du coach, on lui laisse donc une fenêtre de rattrapage d'une semaine.
   */
  pendingFeedback(days = 7): Observable<Workout[]> {
    const to = new Date();
    to.setDate(to.getDate() - 1);
    const from = new Date();
    from.setDate(from.getDate() - days);
    return this.workouts(isoDay(from), isoDay(to)).pipe(map((list) => list.filter(awaitsFeedback)));
  }

  /**
   * Nombre de séances notées **d'affilée**, en remontant depuis la plus récente séance passée.
   * La série s'arrête à la première séance passée restée muette : c'est bien une série de
   * retours, pas un total. Sert à rendre visible ce que le produit demande sans rien donner en
   * retour — remplir un ressenti est un effort, il mérite un accusé de réception.
   */
  feedbackStreak(days = 90): Observable<number> {
    const today = new Date();
    const from = new Date();
    from.setDate(from.getDate() - days);
    return this.workouts(isoDay(from), isoDay(today)).pipe(
      map((list) => {
        const past = list
          .filter((w) => w.scheduledDate <= isoDay(today))
          .sort((a, b) => b.scheduledDate.localeCompare(a.scheduledDate));
        let n = 0;
        for (const w of past) {
          if (awaitsFeedback(w)) break;
          n++;
        }
        return n;
      }),
    );
  }

  // --- Check-in matinal : la forme AVANT la séance ---------------------------------------
  // Sans lui, l'état de forme ne se rafraîchissait qu'après un entraînement : une semaine de
  // coupure ou une blessure laissaient le coach avec une pastille périmée.

  /** Mon check-in d'un jour (204 → null si rien n'a été déclaré). */
  checkIn(date?: string): Observable<WellnessCheckIn | null> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<WellnessCheckIn | null>(`${this.base}/check-in`, { params });
  }

  /** J'enregistre (ou je corrige) mon check-in du jour. */
  saveCheckIn(body: WellnessCheckInRequest): Observable<WellnessCheckIn> {
    return this.http.put<WellnessCheckIn>(`${this.base}/check-in`, body);
  }

  /** Prescription calculée d'une séance course (cibles allure/FC/RPE personnalisées). */
  workoutPrescription(workoutId: string): Observable<WorkoutPrescription> {
    return this.http.get<WorkoutPrescription>(`${this.base}/workouts/${workoutId}/prescription`);
  }

  /** L'athlète déplace une séance course (change la date, jamais le contenu). */
  moveWorkout(workoutId: string, scheduledDate: string): Observable<Workout> {
    return this.http.patch<Workout>(`${this.base}/workouts/${workoutId}/move`, { scheduledDate });
  }

  /** L'athlète déplace une séance de force planifiée. */
  ppMove(scheduledId: string, scheduledDate: string): Observable<ScheduledStrength> {
    return this.http.patch<ScheduledStrength>(`${this.base}/pp/scheduled/${scheduledId}/move`, { scheduledDate });
  }

  /** Mes indisponibilités (en cours/à venir). */
  unavailabilities(): Observable<Unavailability[]> {
    return this.http.get<Unavailability[]>(`${this.base}/unavailabilities`);
  }

  // --- Phase 1 « Me connaître » (lecture seule) ---
  /** Mon profil physiologique (VDOT, LT1/LT2, VC, domaines). */
  physio(): Observable<PhysioProfile> {
    return this.http.get<PhysioProfile>(`${this.base}/physio`);
  }
  /** Mes allures d'entraînement (VDOT). */
  vdot(): Observable<Vdot> {
    return this.http.get<Vdot>(`${this.base}/vdot`);
  }
  /** Ma charge d'entraînement (ACWR, ATL/CTL, monotonie). */
  load(): Observable<Load> {
    return this.http.get<Load>(`${this.base}/load`);
  }
  /** Mes objectifs (liste complète). */
  races(): Observable<RaceObjective[]> {
    return this.http.get<RaceObjective[]>(`${this.base}/races`);
  }
  /** Je crée un objectif. */
  createRace(req: RaceObjectiveRequest): Observable<RaceObjective> {
    return this.http.post<RaceObjective>(`${this.base}/races`, req);
  }
  /** Je modifie un objectif. */
  updateRace(raceId: string, req: RaceObjectiveRequest): Observable<RaceObjective> {
    return this.http.patch<RaceObjective>(`${this.base}/races/${raceId}`, req);
  }
  /** Je supprime un objectif. */
  deleteRace(raceId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/races/${raceId}`);
  }

  // --- Synchronisation Strava (côté athlète : je connecte MA montre) ---
  stravaStatus(): Observable<StravaStatus> {
    return this.http.get<StravaStatus>(`${this.base}/strava`);
  }
  stravaAuthorizeUrl(): Observable<{ url: string }> {
    return this.http.get<{ url: string }>(`${this.base}/strava/authorize`);
  }
  stravaConnect(code: string): Observable<StravaStatus> {
    return this.http.post<StravaStatus>(`${this.base}/strava/connect`, { code });
  }
  stravaImport(): Observable<{ imported: number }> {
    return this.http.post<{ imported: number }>(`${this.base}/strava/import`, {});
  }
  stravaDisconnect(): Observable<void> {
    return this.http.delete<void>(`${this.base}/strava`);
  }

  // --- Phase 2 « Mon histoire » (lecture seule) ---
  /** Mes analytics (volume hebdo prévu/réalisé, zones, adhérence). */
  analytics(weeks = 8): Observable<Analytics> {
    const params = new HttpParams().set('weeks', weeks);
    return this.http.get<Analytics>(`${this.base}/analytics`, { params });
  }
  /** Mes activités réalisées (Strava/GPX/manuel). */
  activities(): Observable<Activity[]> {
    return this.http.get<Activity[]>(`${this.base}/activities`);
  }
  /** Tracé GPS d'une de mes activités. */
  activityRoute(activityId: string): Observable<number[][]> {
    return this.http.get<number[][]>(`${this.base}/activities/${activityId}/route`);
  }
  /** Mes performances / records par distance. */
  performances(): Observable<Performance[]> {
    return this.http.get<Performance[]>(`${this.base}/performances`);
  }
  /** Je déclare une performance de référence (bootstrap VDOT / allures). */
  addPerformance(req: { distance: string; timeSeconds: number; dateSet?: string | null }): Observable<Performance> {
    return this.http.post<Performance>(`${this.base}/performances`, req);
  }

  // --- Phase 3 « Aller plus loin » (lecture seule) ---
  /** Mes tests lactate (résumés). */
  lactateTests(): Observable<LactateTest[]> {
    return this.http.get<LactateTest[]>(`${this.base}/lactate-tests`);
  }
  /** Détail d'un test lactate (paliers pour la courbe). */
  lactateTest(testId: string): Observable<LactateTest> {
    return this.http.get<LactateTest>(`${this.base}/lactate-tests/${testId}`);
  }
  /** Ma charge de force (méca/métab, UA) par séance réalisée. */
  strengthLoad(): Observable<StrengthLoadPoint[]> {
    return this.http.get<StrengthLoadPoint[]>(`${this.base}/pp/strength-load`);
  }

  /** Mon profil de force : 1RM courant par exercice (lecture seule). */
  my1rm(): Observable<MyOneRm[]> {
    return this.http.get<MyOneRm[]>(`${this.base}/pp/1rm`);
  }

  /** Historique e1RM d'un exercice (courbe de progression). */
  my1rmHistory(exerciseId: string): Observable<E1rmHistory[]> {
    return this.http.get<E1rmHistory[]>(`${this.base}/pp/1rm/${exerciseId}/history`);
  }

  // --- Préparation physique (séances de force planifiées) ---
  ppScheduled(from: string, to: string): Observable<ScheduledStrength[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<ScheduledStrength[]>(`${this.base}/pp/scheduled`, { params });
  }

  /** Une séance de force par son id : le mode séance plein écran est une URL rechargeable. */
  ppSession(scheduledId: string): Observable<ScheduledStrength> {
    return this.http.get<ScheduledStrength>(`${this.base}/pp/scheduled/${scheduledId}`);
  }

  ppPrescription(scheduledId: string): Observable<StrengthPrescriptionView> {
    return this.http.get<StrengthPrescriptionView>(`${this.base}/pp/scheduled/${scheduledId}/prescription`);
  }

  ppFeedback(scheduledId: string, body: StrengthFeedback): Observable<ScheduledStrength> {
    return this.http.patch<ScheduledStrength>(`${this.base}/pp/scheduled/${scheduledId}/feedback`, body);
  }

  ppResults(scheduledId: string, results: StrengthResultEntry[]): Observable<E1rmHistory[]> {
    return this.http.post<E1rmHistory[]>(`${this.base}/pp/scheduled/${scheduledId}/results`, results);
  }

  /** Suggestion de progression du coach après une séance de force réalisée. */
  ppProgression(scheduledId: string): Observable<Progression> {
    return this.http.get<Progression>(`${this.base}/pp/scheduled/${scheduledId}/progression`);
  }

  /** RGPD — export des données personnelles (portabilité). */
  export(): Observable<unknown> {
    return this.http.get<unknown>(`${this.base}/export`);
  }

  /** RGPD — suppression du compte et des données (droit à l'oubli). */
  deleteAccount(): Observable<void> {
    return this.http.delete<void>(this.base);
  }
}
