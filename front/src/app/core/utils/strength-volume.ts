import type {
  BlockFormat, SideMode, StrengthPrescription, VolumeType,
} from '../models/strength.model';

/**
 * Lecture du **volume d'un exercice de force** : ce que compte la série (répétitions, durée ou
 * distance), pour qui elle compte (les deux côtés, un seul, en alternance), et le repos qui suit.
 *
 * <p><b>Pourquoi une source unique.</b> Trois écrans affichent la même prescription — l'éditeur
 * du coach, la vue figée du calendrier et de la fiche athlète, et le mode séance plein écran. Tant
 * que le volume ne pouvait être que des répétitions, les trois s'en tiraient avec la même ligne de
 * code recopiée. Dès lors qu'une série se compte aussi en secondes ou en mètres, cette ligne
 * devient une décision : « 45 » ne veut rien dire, « 45 s » et « 45 m » ne sont pas la même
 * séance, et « 8 » sur une fente n'est pas « 8 par jambe ». Une prescription lue autrement d'un
 * écran à l'autre, c'est un athlète qui ne fait pas la séance prescrite — on ne recopie donc plus
 * la règle, on l'appelle.</p>
 *
 * <p><b>Compatibilité.</b> Les prescriptions d'avant n'ont pas de `volumeType` : elles se lisent
 * en répétitions, sauf dans un bloc d'isométrie où la durée était déjà le seul volume saisi.
 * Aucune séance existante ne change de sens.</p>
 */

export const VOLUME_TYPE_LABELS: Record<VolumeType, string> = {
  REPS: 'Répétitions',
  DUREE: 'Durée',
  DISTANCE: 'Distance',
};

export const SIDE_MODE_LABELS: Record<SideMode, string> = {
  BILATERAL: 'Les deux côtés',
  ALTERNE: 'Alterné G / D',
  PAR_COTE: 'Par côté',
};

/** Fourchette affichable — bornes égales quand la prescription est une valeur exacte. */
export interface VolumePill {
  label: string;
  min: number;
  max: number;
  unit: string;
}

/**
 * Unité de volume effective d'une prescription.
 *
 * <p>Sans `volumeType` (prescription d'avant), on retombe sur les répétitions — sauf dans un bloc
 * d'isométrie, où `durationSec` était déjà le seul volume que l'éditeur savait saisir.</p>
 */
export function effectiveVolumeType(
  p: StrengthPrescription | null | undefined, blockFormat?: BlockFormat | null,
): VolumeType {
  if (p?.volumeType) return p.volumeType;
  return blockFormat === 'ISOMETRIE' ? 'DUREE' : 'REPS';
}

/**
 * Le volume prescrit, prêt à afficher : « Reps 8 », « Durée 30–45 s », « Distance 30 m ».
 * `null` quand rien n'est prescrit — mieux vaut ne rien montrer qu'un « 0 ».
 */
export function volumePill(
  p: StrengthPrescription | null | undefined, blockFormat?: BlockFormat | null,
): VolumePill | null {
  if (!p) return null;
  switch (effectiveVolumeType(p, blockFormat)) {
    case 'DUREE':
      return range('Durée', p.durationSec, p.durationSecMax, 's');
    case 'DISTANCE':
      return range('Distance', p.distanceM, p.distanceMMax, 'm');
    default:
      return p.repsFixed != null
        ? { label: 'Reps', min: p.repsFixed, max: p.repsFixed, unit: '' }
        : range('Reps', p.repsMin, p.repsMax, '');
  }
}

/**
 * Mention de latéralité à accoler au volume — « par côté », « alterné G / D ».
 * `null` en bilatéral : c'est le cas par défaut, l'écrire ne dirait rien de plus.
 */
export function sideLabel(p: StrengthPrescription | null | undefined): string | null {
  const mode = p?.sideMode;
  if (!mode || mode === 'BILATERAL') return null;
  return SIDE_MODE_LABELS[mode];
}

/**
 * Le repos entre séries. Une seule borne = repos strict (« 90 s ») ; deux bornes distinctes =
 * fourchette (« 90–120 s »), qui est ce que le coach veut dire quand il laisse le choix.
 */
export function restPill(p: StrengthPrescription | null | undefined): VolumePill | null {
  return p ? range('Récup', p.restSecMin, p.restSecMax, 's') : null;
}

/** Le repos est-il prescrit en fourchette (deux bornes distinctes) ? */
export function isRestRange(p: StrengthPrescription | null | undefined): boolean {
  return p?.restSecMin != null && p.restSecMax != null && p.restSecMax !== p.restSecMin;
}

/** Fourchette à bornes tolérantes : sans borne haute, la valeur exacte fait les deux bornes. */
function range(
  label: string, min: number | null | undefined, max: number | null | undefined, unit: string,
): VolumePill | null {
  if (min == null) return null;
  return { label, min, max: max ?? min, unit };
}
