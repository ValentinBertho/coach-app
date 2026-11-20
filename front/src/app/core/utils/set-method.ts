import { SetType } from '../models/strength.model';

/**
 * Méthodes de série (drop set, myo-reps, cluster, iso…) et leurs réglages.
 *
 * <p>Le type de série se choisissait dans une liste, puis rien ne changeait : un cluster se lisait
 * comme quatre séries de six, et l'athlète ne savait ni combien de répétitions par bloc ni combien
 * de pause entre les blocs. Chaque méthode porte maintenant ses propres réglages (dans
 * {@code setConfig}, structure libre côté serveur) et une phrase qui la dit à l'athlète.</p>
 */
export interface SetMethodConfig {
  /** Drop set : nombre de baisses de charge après la série principale. */
  drops?: number | null;
  /** Drop set : baisse de charge à chaque palier, en %. */
  dropPct?: number | null;
  /** Myo-reps : répétitions de la série d'activation. */
  activationReps?: number | null;
  /** Myo-reps : nombre de mini-séries. */
  miniSets?: number | null;
  /** Myo-reps : répétitions par mini-série. */
  miniReps?: number | null;
  /** Cluster : nombre de blocs dans la série. */
  clusters?: number | null;
  /** Cluster : répétitions par bloc. */
  repsPerCluster?: number | null;
  /** Myo-reps, cluster : pause courte entre mini-séries / blocs (s). */
  pauseSec?: number | null;
  /** Iso : durée de chaque contraction (s). */
  holdSec?: number | null;
}

export interface SetMethodField {
  key: keyof SetMethodConfig;
  label: string;
  min: number;
  max: number;
}

export interface SetMethod {
  value: SetType;
  label: string;
  /** Ce que la méthode demande, en une ligne, sous le choix. */
  hint: string;
  defaults: SetMethodConfig;
  fields: SetMethodField[];
}

export const SET_METHODS: SetMethod[] = [
  { value: 'STANDARD', label: 'Standard', hint: '', defaults: {}, fields: [] },
  {
    value: 'DROP_SET', label: 'Drop set',
    hint: 'Série à l\'échec, puis on baisse la charge et on repart sans pause.',
    defaults: { drops: 2, dropPct: 20 },
    fields: [
      { key: 'drops', label: 'Baisses', min: 1, max: 5 },
      { key: 'dropPct', label: 'Baisse (%)', min: 5, max: 50 },
    ],
  },
  {
    value: 'MYO_REPS', label: 'Myo-reps',
    hint: 'Une série d\'activation, puis des mini-séries séparées de quelques respirations.',
    defaults: { activationReps: 12, miniSets: 4, miniReps: 4, pauseSec: 5 },
    fields: [
      { key: 'activationReps', label: 'Activation (reps)', min: 1, max: 30 },
      { key: 'miniSets', label: 'Mini-séries', min: 1, max: 10 },
      { key: 'miniReps', label: 'Reps / mini-série', min: 1, max: 15 },
      { key: 'pauseSec', label: 'Pause (s)', min: 0, max: 60 },
    ],
  },
  {
    value: 'CLUSTER', label: 'Cluster',
    hint: 'La série est coupée en petits blocs de répétitions, avec une courte pause entre eux.',
    defaults: { clusters: 3, repsPerCluster: 2, pauseSec: 15 },
    fields: [
      { key: 'clusters', label: 'Blocs', min: 2, max: 10 },
      { key: 'repsPerCluster', label: 'Reps / bloc', min: 1, max: 10 },
      { key: 'pauseSec', label: 'Pause (s)', min: 0, max: 120 },
    ],
  },
  {
    value: 'SUPER_SET', label: 'Enchaîné',
    hint: 'Enchaîné avec l\'exercice suivant, sans pause. Pour plusieurs exercices, utilise un bloc Superset.',
    defaults: {}, fields: [],
  },
  {
    value: 'ISO_OVERCOMING', label: 'Iso (pousser)',
    hint: 'Pousser ou tirer de toutes ses forces contre une résistance qui ne bouge pas.',
    defaults: { holdSec: 6 },
    fields: [{ key: 'holdSec', label: 'Contraction (s)', min: 1, max: 60 }],
  },
  {
    value: 'ISO_YIELDING', label: 'Iso (tenir)',
    hint: 'Tenir la position sous charge, sans céder.',
    defaults: { holdSec: 30 },
    fields: [{ key: 'holdSec', label: 'Maintien (s)', min: 1, max: 180 }],
  },
];

export function setMethod(type: SetType | null | undefined): SetMethod {
  return SET_METHODS.find((m) => m.value === type) ?? SET_METHODS[0];
}

/** Réglages lus depuis le JSON libre de l'item, complétés par les valeurs par défaut de la méthode. */
export function setMethodConfig(type: SetType | null | undefined, raw: unknown): SetMethodConfig {
  const base = setMethod(type).defaults;
  return raw && typeof raw === 'object' ? { ...base, ...(raw as SetMethodConfig) } : { ...base };
}

/**
 * La méthode en une phrase pour l'athlète (« Cluster : 3 blocs de 2 reps, 15 s de pause »),
 * ou rien pour une série standard.
 */
export function describeSetMethod(type: SetType | null | undefined, raw: unknown): string {
  const m = setMethod(type);
  const c = setMethodConfig(type, raw);
  switch (m.value) {
    case 'DROP_SET':
      return `Drop set : ${c.drops} baisse${(c.drops ?? 0) > 1 ? 's' : ''} de ${c.dropPct} %, sans pause`;
    case 'MYO_REPS':
      return `Myo-reps : ${c.activationReps} reps d'activation, puis ${c.miniSets} × ${c.miniReps} reps, ${c.pauseSec} s de pause`;
    case 'CLUSTER':
      return `Cluster : ${c.clusters} blocs de ${c.repsPerCluster} reps, ${c.pauseSec} s de pause entre les blocs`;
    case 'SUPER_SET':
      return 'Enchaîné avec l\'exercice suivant, sans pause';
    case 'ISO_OVERCOMING':
      return `Iso : pousser ${c.holdSec} s contre une résistance fixe`;
    case 'ISO_YIELDING':
      return `Iso : tenir ${c.holdSec} s sans céder`;
    default:
      return '';
  }
}
