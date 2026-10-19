import { IntensityZone, WorkoutRequest, WorkoutStepType, WorkoutType } from '../../core/models/workout.model';

/**
 * Fichier de plan importable (cf. docs/IMPORT-PLAN.md) : le PDF d'un coach extérieur converti en
 * JSON, une séance par entrée, chacune exactement ce qu'on aurait saisi au calendrier.
 */
export interface PlanFile {
  title?: string;
  workouts: WorkoutRequest[];
}

/** Résultat de la lecture : le plan prêt à envoyer, ou la liste de ce qui ne va pas. */
export type PlanParseResult =
  | { ok: true; plan: PlanFile }
  | { ok: false; errors: string[] };

const TYPES: WorkoutType[] = ['ENDURANCE', 'RECOVERY', 'TEMPO', 'THRESHOLD', 'INTERVALS',
  'LONG_RUN', 'RACE', 'STRENGTH', 'CROSS_TRAINING', 'REST'];
const STEP_TYPES: WorkoutStepType[] = ['WARMUP', 'STEADY', 'REPETITION', 'RECOVERY', 'COOLDOWN'];
const ZONES: IntensityZone[] = ['Z1', 'Z2', 'Z3', 'Z4', 'Z5'];
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const MAX_WORKOUTS = 500;

/**
 * Lit et vérifie un fichier de plan avant tout envoi.
 *
 * <p>Le serveur valide de toute façon ; vérifier ici sert à dire <b>quelle</b> séance pose
 * problème, dans l'aperçu, plutôt qu'un « import impossible » sans suite. Les erreurs sont
 * rapportées toutes ensemble : un fichier se corrige en une fois.</p>
 */
export function parsePlanFile(text: string): PlanParseResult {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch {
    return { ok: false, errors: ['Le fichier n’est pas un JSON valide.'] };
  }
  const list = Array.isArray(raw) ? raw : (raw as { workouts?: unknown })?.workouts;
  if (!Array.isArray(list) || list.length === 0) {
    return { ok: false, errors: ['Aucune séance trouvée (champ « workouts » attendu).'] };
  }
  if (list.length > MAX_WORKOUTS) {
    return { ok: false, errors: [`Trop de séances (${list.length}) : ${MAX_WORKOUTS} au plus par import.`] };
  }

  const errors: string[] = [];
  const workouts: WorkoutRequest[] = list.map((item, i) => {
    const w = (item ?? {}) as Record<string, unknown>;
    const where = `Séance ${i + 1}${typeof w['title'] === 'string' ? ` (« ${w['title']} »)` : ''}`;
    const date = w['scheduledDate'];
    if (typeof date !== 'string' || !ISO_DATE.test(date) || isNaN(Date.parse(date))) {
      errors.push(`${where} : date invalide, format AAAA-MM-JJ attendu.`);
    }
    if (!TYPES.includes(w['type'] as WorkoutType)) {
      errors.push(`${where} : type « ${w['type']} » inconnu.`);
    }
    if (typeof w['title'] !== 'string' || !w['title'].trim()) {
      errors.push(`${where} : titre manquant.`);
    }
    const steps = Array.isArray(w['steps']) ? w['steps'] as Record<string, unknown>[] : [];
    steps.forEach((s, j) => {
      if (!STEP_TYPES.includes(s?.['stepType'] as WorkoutStepType)) {
        errors.push(`${where}, étape ${j + 1} : type d’étape « ${s?.['stepType']} » inconnu.`);
      }
      if (s?.['zone'] != null && !ZONES.includes(s['zone'] as IntensityZone)) {
        errors.push(`${where}, étape ${j + 1} : zone « ${s['zone']} » inconnue (Z1 à Z5).`);
      }
    });
    return {
      scheduledDate: date as string,
      type: w['type'] as WorkoutType,
      title: typeof w['title'] === 'string' ? w['title'].trim() : '',
      notes: (w['notes'] as string | undefined) ?? null,
      targetDistanceM: (w['targetDistanceM'] as number | undefined) ?? null,
      targetDurationS: (w['targetDurationS'] as number | undefined) ?? null,
      steps: steps.map((s) => ({
        stepType: s['stepType'] as WorkoutStepType,
        repetitions: Math.max(1, Number(s['repetitions']) || 1),
        zone: (s['zone'] as IntensityZone | undefined) ?? null,
        distanceM: (s['distanceM'] as number | undefined) ?? null,
        durationS: (s['durationS'] as number | undefined) ?? null,
        notes: (s['notes'] as string | undefined) ?? null,
      })),
    };
  });

  if (errors.length) return { ok: false, errors };
  const title = Array.isArray(raw) ? undefined : (raw as { title?: unknown }).title;
  return {
    ok: true,
    plan: {
      title: typeof title === 'string' ? title : undefined,
      workouts: [...workouts].sort((a, b) => a.scheduledDate.localeCompare(b.scheduledDate)),
    },
  };
}
