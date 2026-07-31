/**
 * Formatage d'allure. Le coureur raisonne en min/km, pas en « 47 min pour 10,2 km » : partout où
 * une distance et une durée coexistent, l'allure est l'information utile.
 */

/** Allure « m'ss » à partir de secondes par kilomètre. */
export function formatPace(secondsPerKm: number | null | undefined): string | null {
  if (secondsPerKm == null || !Number.isFinite(secondsPerKm) || secondsPerKm <= 0) {
    return null;
  }
  const total = Math.round(secondsPerKm);
  const minutes = Math.floor(total / 60);
  const seconds = total % 60;
  return `${minutes}'${seconds.toString().padStart(2, '0')}`;
}

/** Allure « m'ss » à partir d'une distance (m) et d'une durée (s). */
export function paceFrom(
  distanceM: number | null | undefined,
  durationS: number | null | undefined,
): string | null {
  if (!distanceM || !durationS || distanceM <= 0 || durationS <= 0) {
    return null;
  }
  return formatPace((durationS * 1000) / distanceM);
}
