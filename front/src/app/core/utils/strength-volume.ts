import type {
  BlockFormat, SetTarget, SideMode, StrengthPrescription, VolumeType,
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
  /** Séries différentes l'une de l'autre : « 12 / 10 / 8 », à lire à la place de min–max. */
  perSet?: string;
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
  const targets = setTargetsOf(p);
  if (targets) {
    const type = effectiveVolumeType(p, blockFormat);
    const lows = targets.map((t) => t.value).filter((v): v is number => v != null);
    if (!lows.length) return null;
    const highs = targets.map((t) => t.valueMax ?? t.value).filter((v): v is number => v != null);
    return {
      label: VOLUME_PILL_LABELS[type],
      min: Math.min(...lows),
      max: Math.max(...highs),
      unit: VOLUME_UNITS[type],
      perSet: targets.map((t) => targetText(t)).join(' / ') + suffix(type),
    };
  }
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

const VOLUME_PILL_LABELS: Record<VolumeType, string> = { REPS: 'Reps', DUREE: 'Durée', DISTANCE: 'Distance' };
const VOLUME_UNITS: Record<VolumeType, string> = { REPS: '', DUREE: 's', DISTANCE: 'm' };

function suffix(type: VolumeType): string {
  return VOLUME_UNITS[type] ? ` ${VOLUME_UNITS[type]}` : '';
}

function targetText(t: SetTarget): string {
  if (t.value == null) return '—';
  return t.valueMax != null && t.valueMax !== t.value ? `${t.value}–${t.valueMax}` : `${t.value}`;
}

/**
 * Le volume propre à chaque série, quand le coach l'a réglé série par série (pyramide
 * 12 / 10 / 8). `null` quand toutes les séries sont identiques — le cas de toute prescription
 * d'avant, qui se lit alors dans les champs scalaires.
 */
export function setTargetsOf(p: StrengthPrescription | null | undefined): SetTarget[] | null {
  return p?.setTargets?.length ? p.setTargets : null;
}

/**
 * Les séries d'un exercice telles que l'éditeur les montre, une ligne par série : celles
 * réglées une à une, sinon `sets` fois le volume commun.
 */
export function setRowsOf(
  p: StrengthPrescription, blockFormat?: BlockFormat | null,
): SetTarget[] {
  const targets = setTargetsOf(p);
  if (targets) return targets.map((t) => ({ value: t.value ?? null, valueMax: t.valueMax ?? null }));
  const count = Math.max(1, p.sets ?? 1);
  const { low, high } = scalarVolume(p, effectiveVolumeType(p, blockFormat));
  return Array.from({ length: count }, () => ({ value: low, valueMax: high }));
}

/**
 * Écrit les séries saisies dans la prescription. Des séries toutes identiques redeviennent un
 * volume commun (`setTargets` absent) : la prescription reste alors lisible par tout ce qui ne
 * connaît que « 3 × 8 ». Des séries différentes sont gardées une à une, et les champs scalaires
 * reçoivent leur enveloppe min–max.
 */
export function applySetRows(
  p: StrengthPrescription, blockFormat: BlockFormat | null | undefined, rows: SetTarget[],
): void {
  const clean = rows.map((r) => {
    const value = r.value ?? null;
    const max = r.valueMax ?? null;
    return { value, valueMax: max != null && max !== value ? max : null };
  });
  if (!clean.length) clean.push({ value: null, valueMax: null });
  p.sets = clean.length;
  const type = effectiveVolumeType(p, blockFormat);
  const uniform = clean.every((r) => r.value === clean[0].value && r.valueMax === clean[0].valueMax);
  if (uniform) {
    p.setTargets = null;
    writeScalarVolume(p, type, clean[0].value, clean[0].valueMax);
    return;
  }
  p.setTargets = clean;
  const lows = clean.map((r) => r.value).filter((v): v is number => v != null);
  const highs = clean.map((r) => r.valueMax ?? r.value).filter((v): v is number => v != null);
  const low = lows.length ? Math.min(...lows) : null;
  const high = highs.length ? Math.max(...highs) : null;
  writeScalarVolume(p, type, low, high != null && high !== low ? high : null);
}

/** Le volume prévu pour la série `index` (0 = la première), prêt à afficher : « 10 », « 30 s ». */
export function setTargetLabel(
  p: StrengthPrescription | null | undefined, blockFormat: BlockFormat | null | undefined, index: number,
): string | null {
  const t = setTargetsOf(p)?.[index];
  if (!t || t.value == null) return null;
  const type = effectiveVolumeType(p, blockFormat);
  return targetText(t) + (type === 'REPS' ? ' reps' : suffix(type));
}

function scalarVolume(p: StrengthPrescription, type: VolumeType): { low: number | null; high: number | null } {
  switch (type) {
    case 'DUREE':
      return { low: p.durationSec ?? null, high: p.durationSecMax ?? null };
    case 'DISTANCE':
      return { low: p.distanceM ?? null, high: p.distanceMMax ?? null };
    default:
      return p.repsFixed != null
        ? { low: p.repsFixed, high: null }
        : { low: p.repsMin ?? null, high: p.repsMax ?? null };
  }
}

function writeScalarVolume(
  p: StrengthPrescription, type: VolumeType, low: number | null, high: number | null,
): void {
  switch (type) {
    case 'DUREE':
      p.durationSec = low;
      p.durationSecMax = high;
      break;
    case 'DISTANCE':
      p.distanceM = low;
      p.distanceMMax = high;
      break;
    default:
      if (high == null) {
        p.repsFixed = low;
        p.repsMin = null;
        p.repsMax = null;
      } else {
        p.repsFixed = null;
        p.repsMin = low;
        p.repsMax = high;
      }
  }
}
