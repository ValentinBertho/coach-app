import { ActivityStreamPoint } from '../../../core/models/activity.model';

/** Ce qu'une portion de courbe dit, une fois sélectionnée. */
export interface SelectionStats {
  /** Distance couverte par la portion, en mètres. */
  distanceM: number;
  /** Temps écoulé sur la portion, en secondes. */
  durationS: number;
  /**
   * Allure moyenne, en secondes au kilomètre. Calculée `temps / distance` et **non** en moyennant
   * les allures instantanées : moyenner des allures donne un poids égal à chaque échantillon, si
   * bien qu'une marche de dix secondes à 12'00/km pèse autant qu'un kilomètre à 4'00. Le coureur,
   * lui, lit son chrono divisé par sa distance.
   */
  paceSPerKm: number | null;
  /**
   * FC moyenne, pondérée par le **temps** passé à chaque valeur. Les échantillons d'un flux de
   * montre ne sont pas régulièrement espacés : une moyenne simple surpondère les portions denses,
   * c'est-à-dire les plus lentes, et tire la FC vers le bas.
   */
  avgHr: number | null;
  /** Nombre d'échantillons retenus — sert à se taire quand la portion est trop courte. */
  samples: number;
}

/**
 * Statistiques d'une portion de courbe, bornée par deux distances.
 *
 * <p>Extrait du composant pour être éprouvable seul : c'est de l'arithmétique sur des séries
 * incomplètes — capteur muet, arrêt au feu rouge, échantillons irréguliers — et chacun de ces
 * cas a sa façon de produire un chiffre faux plutôt qu'une absence de chiffre.</p>
 *
 * @param fromM borne basse (mètres), incluse
 * @param toM   borne haute (mètres), incluse — les bornes peuvent arriver dans n'importe quel ordre
 */
export function selectionStats(
  points: readonly ActivityStreamPoint[],
  fromM: number,
  toM: number,
): SelectionStats | null {
  const lo = Math.min(fromM, toM);
  const hi = Math.max(fromM, toM);
  const inRange = points.filter((p) => p.distanceM >= lo && p.distanceM <= hi);
  if (inRange.length < 2) {
    return null;
  }

  const first = inRange[0];
  const last = inRange[inRange.length - 1];
  const distanceM = Math.max(0, last.distanceM - first.distanceM);
  const durationS = Math.max(0, last.elapsedS - first.elapsedS);

  // Une portion sans distance ni durée n'a pas d'allure : on ne divise pas par zéro pour
  // fabriquer un nombre qui n'aurait aucun sens.
  const paceSPerKm = distanceM > 0 && durationS > 0
    ? Math.round(durationS / (distanceM / 1000))
    : null;

  return {
    distanceM,
    durationS,
    paceSPerKm,
    avgHr: timeWeightedHr(inRange),
    samples: inRange.length,
  };
}

/**
 * FC moyenne pondérée par la durée de chaque intervalle.
 *
 * <p>Les échantillons sans FC ne comptent pas — ni au numérateur, ni au dénominateur. Les
 * traiter comme des zéros ferait chuter la moyenne à chaque seconde où la ceinture décroche,
 * exactement le moment où l'on regarde le graphique pour comprendre ce qui s'est passé.</p>
 */
function timeWeightedHr(points: readonly ActivityStreamPoint[]): number | null {
  let weighted = 0;
  let seconds = 0;
  for (let i = 0; i < points.length - 1; i++) {
    const hr = points[i].hr;
    if (hr == null || hr <= 0) {
      continue;
    }
    const dt = points[i + 1].elapsedS - points[i].elapsedS;
    if (dt <= 0) {
      continue;
    }
    weighted += hr * dt;
    seconds += dt;
  }
  if (seconds > 0) {
    return Math.round(weighted / seconds);
  }
  // Aucun intervalle exploitable (échantillons tous au même instant, ou un seul porteur de FC) :
  // on retombe sur la moyenne simple des valeurs présentes plutôt que de ne rien dire.
  const values = points.map((p) => p.hr).filter((h): h is number => h != null && h > 0);
  return values.length ? Math.round(values.reduce((a, b) => a + b, 0) / values.length) : null;
}
