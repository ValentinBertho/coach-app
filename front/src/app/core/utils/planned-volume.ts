/**
 * Volume prévu d'une séance, quand la prescription ne permet pas de le calculer exactement.
 *
 * <p><b>Le problème.</b> Le total de distance d'une séance n'additionne que ce qu'il sait
 * convertir : un bloc dont l'allure se calcule (fourchette de % ou zone renseignée) donne sa
 * distance, un bloc de durée sans allure n'apporte rien. Une séance écrite « footing 55' », sans
 * allure prescrite et pour un athlète dont le profil n'en fournit aucune, ressortait donc avec
 * une distance qui ne comptait que ses éducatifs — « 0,1 km » pour une heure de course. Le
 * chiffre n'était pas approximatif : il décrivait autre chose que la séance.</p>
 *
 * <p><b>La règle retenue.</b> Deux gardes, aucune invention :</p>
 * <ol>
 *   <li>une distance dont l'<b>allure implicite</b> (durée ÷ distance) sort de ce qu'un coureur
 *       peut tenir n'est pas une distance de séance : elle est écartée plutôt qu'affichée ;</li>
 *   <li>à défaut, la durée est convertie avec l'<b>allure d'endurance de l'athlète lui-même</b>
 *       (celle dérivée de son VDOT, ou la moyenne de ses sorties récentes). Le résultat est
 *       marqué comme une estimation — « ≈ 10 km » — et jamais comme une cible.</li>
 * </ol>
 *
 * <p>Rien n'est écrit en base : la séance prescrite par le coach n'est pas modifiée, seule sa
 * lecture l'est. Sans référence d'allure propre à l'athlète, on n'affiche rien — un repère
 * inventé serait pire que pas de repère.</p>
 */

/** Volume prévu affichable. `indicative` = estimé depuis la durée, à présenter avec « ≈ ». */
export interface PlannedVolume {
  distanceM: number;
  indicative: boolean;
}

/**
 * Bornes d'allure d'un coureur, en secondes par kilomètre : record du monde du marathon d'un côté,
 * marche rapide de l'autre. Hors de cette plage, le couple durée/distance ne décrit pas une course
 * à pied — c'est un reste de saisie, pas un volume.
 */
const FASTEST_PLAUSIBLE_S_PER_KM = 120;
const SLOWEST_PLAUSIBLE_S_PER_KM = 900;

/**
 * Distance prévue d'une séance, exacte si elle est crédible, estimée sinon.
 *
 * @param targetDistanceM distance cible enregistrée (m), ou null
 * @param targetDurationS durée cible enregistrée (s), ou null
 * @param referencePaceS  allure d'endurance de l'athlète (s/km), ou null si aucune n'est connue
 * @returns le volume à afficher, ou `null` quand rien de fiable ne peut être dit
 */
export function plannedVolume(
  targetDistanceM: number | null | undefined,
  targetDurationS: number | null | undefined,
  referencePaceS: number | null | undefined,
): PlannedVolume | null {
  if (targetDistanceM && targetDistanceM > 0 && isCredible(targetDistanceM, targetDurationS)) {
    return { distanceM: targetDistanceM, indicative: false };
  }
  if (targetDurationS && targetDurationS > 0 && referencePaceS && referencePaceS > 0) {
    // Arrondi à la centaine de mètres : afficher « 9 743 m » donnerait à une estimation la
    // précision d'une mesure.
    const raw = (targetDurationS / referencePaceS) * 1000;
    return { distanceM: Math.round(raw / 100) * 100, indicative: true };
  }
  return null;
}

/**
 * La distance enregistrée décrit-elle bien cette séance ? Sans durée pour la confronter, on la
 * croit — c'est le cas d'une séance écrite en distance, où elle est justement la consigne.
 */
function isCredible(distanceM: number, durationS: number | null | undefined): boolean {
  if (!durationS || durationS <= 0) {
    return true;
  }
  const impliedPace = (durationS * 1000) / distanceM;
  return impliedPace >= FASTEST_PLAUSIBLE_S_PER_KM && impliedPace <= SLOWEST_PLAUSIBLE_S_PER_KM;
}

/**
 * « 9,7 km », précédé de « ≈ » quand la distance est estimée depuis la durée.
 *
 * <p>La décimale nulle est supprimée : sur une pastille de calendrier large de quelques
 * dizaines de pixels, « 12,0 km » coûte un caractère de plus que « 12 km » sans rien apprendre.</p>
 */
export function plannedVolumeLabel(volume: PlannedVolume | null): string {
  if (!volume) {
    return '';
  }
  const km = (volume.distanceM / 1000).toFixed(1).replace(/\.0$/, '').replace('.', ',');
  return volume.indicative ? `≈ ${km} km` : `${km} km`;
}
