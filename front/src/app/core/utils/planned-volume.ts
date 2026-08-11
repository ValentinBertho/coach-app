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
 *   <li>un total sous 500 mètres ou sous trois minutes ne décrit aucune séance de course à pied,
 *       échauffement compris : il est écarté plutôt qu'affiché. On a d'abord jugé la crédibilité
 *       sur l'allure implicite (durée ÷ distance), mais 100 m en 20 secondes donne 3'20/km — une
 *       allure plausible pour une séance qui ne l'est pas. Le résidu ne se reconnaît pas à son
 *       incohérence, il se reconnaît à sa <b>taille</b> ;</li>
 *   <li>à défaut, la durée est convertie avec l'<b>allure d'endurance de l'athlète lui-même</b>
 *       (celle dérivée de son VDOT, ou la moyenne de ses sorties récentes). Le résultat est
 *       marqué comme une estimation — « ≈ 10 km » — et jamais comme une cible.</li>
 * </ol>
 *
 * <p>Les seuils sont ceux de {@code PlannedVolume} côté serveur : la même séance doit être lue de
 * la même façon par l'écran qui l'affiche, le rapprochement qui la cherche et le total hebdomadaire
 * qui la compte.</p>
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

/** Sous ce seuil, le total ne décrit pas une séance mais ses éducatifs. Aligné sur le serveur. */
const MIN_SESSION_DISTANCE_M = 500;
/** Idem pour la durée : aucune séance prescrite ne dure moins de trois minutes. */
const MIN_SESSION_DURATION_S = 180;

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
  if (usableDistanceM(targetDistanceM) != null) {
    return { distanceM: targetDistanceM as number, indicative: false };
  }
  const seconds = usableDurationS(targetDurationS);
  if (seconds != null && referencePaceS && referencePaceS > 0) {
    // Arrondi à la centaine de mètres : afficher « 9 743 m » donnerait à une estimation la
    // précision d'une mesure.
    const raw = (seconds / referencePaceS) * 1000;
    return { distanceM: Math.round(raw / 100) * 100, indicative: true };
  }
  return null;
}

/** Distance prévue si elle décrit bien une séance, `null` sinon. */
export function usableDistanceM(distanceM: number | null | undefined): number | null {
  return distanceM != null && distanceM >= MIN_SESSION_DISTANCE_M ? distanceM : null;
}

/** Durée prévue si elle décrit bien une séance, `null` sinon. */
export function usableDurationS(durationS: number | null | undefined): number | null {
  return durationS != null && durationS >= MIN_SESSION_DURATION_S ? durationS : null;
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
