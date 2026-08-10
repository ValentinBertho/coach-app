import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay, switchMap } from 'rxjs';
import { Activity } from '../models/activity.model';
import { Vdot } from '../models/physio.model';
import { AthletePortalService } from './athlete-portal.service';
import { PhysioService } from './physio.service';

/** Nombre de sorties récentes moyennées quand aucun VDOT n'est connu. */
const RECENT_RUNS = 10;

/** Mêmes bornes que le volume prévu : au-delà, ce n'est pas une allure de course à pied. */
const FASTEST_S_PER_KM = 120;
const SLOWEST_S_PER_KM = 900;

/**
 * Allure d'endurance de référence d'un athlète (s/km) — celle qui sert à donner un ordre de
 * grandeur de distance quand une séance n'est écrite qu'en durée.
 *
 * <p><b>Deux sources, dans cet ordre, et rien d'autre.</b> D'abord l'allure d'endurance
 * fondamentale dérivée de son VDOT (`trainingPaces`, code `EASY`) : c'est la référence que le
 * produit calcule déjà et que le coach lit pour prescrire. À défaut — un athlète sans
 * performance de référence n'a pas de VDOT — la moyenne de ses dernières sorties réelles. Si
 * aucune des deux n'existe, on rend `null` : afficher un repère tiré d'une valeur moyenne du
 * monde serait inventer une donnée sur le dos de l'athlète.</p>
 *
 * <p>Le résultat est mis en cache pour la durée de la session : c'est une donnée qui bouge à
 * l'échelle du mois, et chaque écran de calendrier la redemanderait sinon à chaque affichage.</p>
 */
@Injectable({ providedIn: 'root' })
export class PaceReferenceService {
  private readonly portal = inject(AthletePortalService);
  private readonly physio = inject(PhysioService);

  private mine$?: Observable<number | null>;
  private readonly byAthlete = new Map<string, Observable<number | null>>();

  /** Mon allure de référence (portail athlète). */
  mine(): Observable<number | null> {
    this.mine$ ??= this.portal.vdot().pipe(
      map((v) => easyPace(v)),
      catchError(() => of(null)),
      switchMap((pace) => (pace ? of(pace) : this.fromMyActivities())),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.mine$;
  }

  /**
   * Allure de référence d'un athlète vue par son coach. Pas de repli sur les sorties ici : la
   * liste d'activités d'un athlète est un appel autrement plus lourd côté coach, et une séance
   * dont le volume n'est qu'indicatif ne justifie pas de la charger.
   */
  forAthlete(athleteId: string): Observable<number | null> {
    let cached = this.byAthlete.get(athleteId);
    if (!cached) {
      cached = this.physio.vdot(athleteId).pipe(
        map((v) => easyPace(v)),
        catchError(() => of(null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
      this.byAthlete.set(athleteId, cached);
    }
    return cached;
  }

  /**
   * Allure moyenne de mes dernières sorties, calculée sur les <b>totaux</b> (distance cumulée sur
   * temps cumulé) : moyenner des allures donnerait autant de poids à un footing de 4 km qu'à une
   * sortie longue de 25.
   */
  private fromMyActivities(): Observable<number | null> {
    return this.portal.activities().pipe(
      map((list) => meanPace(list)),
      catchError(() => of(null)),
    );
  }
}

/** Allure d'endurance fondamentale du VDOT, ou `null` si l'athlète n'en a pas encore. */
function easyPace(v: Vdot | null): number | null {
  const item = v?.trainingPaces?.find((p) => p.distance === 'EASY');
  const pace = item?.paceSecPerKm ?? null;
  return plausible(pace) ? pace : null;
}

function meanPace(activities: Activity[]): number | null {
  let distanceM = 0;
  let durationS = 0;
  for (const a of activities.slice(0, RECENT_RUNS)) {
    if (a.distanceM && a.durationS && a.distanceM > 0 && a.durationS > 0) {
      distanceM += a.distanceM;
      durationS += a.durationS;
    }
  }
  if (distanceM <= 0 || durationS <= 0) {
    return null;
  }
  const pace = Math.round((durationS * 1000) / distanceM);
  return plausible(pace) ? pace : null;
}

function plausible(pace: number | null | undefined): pace is number {
  return pace != null && pace >= FASTEST_S_PER_KM && pace <= SLOWEST_S_PER_KM;
}
