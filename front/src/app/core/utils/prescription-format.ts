/**
 * Écriture du volume d'un **bloc de séance** : « 400 m », « 30 s », « 1'30 », « 2 × (8 × 30 s) ».
 *
 * <p><b>Pourquoi une source unique.</b> Cette logique existait en cinq exemplaires — la vue de
 * prescription partagée, l'agenda de l'athlète (deux fois : le bloc et sa récupération), la modale
 * de bibliothèque, la fiche coach — dans trois formats différents. L'un d'eux arrondissait à la
 * minute : une répétition de 30 secondes s'y lisait « 1 min », une récupération de 1'30 « 2 min ».
 * La même séance annonçait donc « 2 × (8 × 30 s) » sur un écran et « 2 × (8 × 1 min) » sur un
 * autre — un athlète qui suit le second court le double, et rien ne le lui signale.</p>
 *
 * <p><b>La convention retenue</b> est celle qu'un coureur écrit sur son carnet :</p>
 * <ul>
 *   <li>moins d'une minute → <b>30 s</b> ;</li>
 *   <li>minutes entières → <b>5 min</b> ;</li>
 *   <li>minutes et secondes → <b>1'30</b>, jamais arrondi ;</li>
 *   <li>une heure et plus → <b>1h05</b>.</li>
 * </ul>
 *
 * <p>L'arrondi est proscrit ici, et c'est tout l'objet du module : 1'30 et 2' ne sont pas la même
 * séance, et l'écart se paie en entraînement, pas en pixels. Les durées <b>totales</b> d'une séance
 * ou d'une sortie, elles, s'arrondissent sans dommage — elles relèvent d'un autre besoin.</p>
 */

/** Volume d'un bloc tel qu'il est écrit dans la prescription. */
export interface BlockVolume {
  distanceM?: number | null;
  durationS?: number | null;
  reps?: number | null;
  sets?: number | null;
}

/**
 * Durée d'un bloc ou d'une récupération, **sans jamais arrondir**.
 *
 * @example formatBlockDuration(30) → « 30 s » · (90) → « 1'30 » · (300) → « 5 min » · (3900) → « 1h05 »
 */
export function formatBlockDuration(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds <= 0) {
    return '';
  }
  const total = Math.round(seconds);
  if (total < 60) {
    return `${total} s`;
  }
  const minutes = Math.floor(total / 60);
  const rest = total % 60;
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60);
    return `${hours}h${String(minutes % 60).padStart(2, '0')}`;
  }
  return rest ? `${minutes}'${String(rest).padStart(2, '0')}` : `${minutes} min`;
}

/**
 * Distance d'un bloc : « 400 m » sous le kilomètre, « 1 km », « 1,25 km ».
 *
 * <p>Virgule française et décimales inutiles supprimées — « 1,0 km » se lit comme une précision
 * qu'on n'a pas, et « 1.25 km » comme une écriture qui n'est pas la nôtre.</p>
 */
export function formatBlockDistance(meters: number | null | undefined): string {
  if (meters == null || !Number.isFinite(meters) || meters <= 0) {
    return '';
  }
  if (meters < 1000) {
    return `${Math.round(meters)} m`;
  }
  const km = (meters / 1000).toFixed(2).replace(/\.?0+$/, '').replace('.', ',');
  return `${km} km`;
}

/**
 * Volume d'un bloc, distance d'abord. Chaîne vide si le bloc n'en porte aucun — l'appelant décide
 * alors quoi afficher à la place (le type de bloc, le plus souvent).
 */
export function formatBlockVolume(
  distanceM: number | null | undefined,
  durationS: number | null | undefined,
): string {
  return formatBlockDistance(distanceM) || formatBlockDuration(durationS);
}

/**
 * Volume complet d'un bloc, répétitions et séries comprises : « 8 × 30 s », « 2 × (8 × 30 s) ».
 *
 * <p>Les parenthèses ne sont pas décoratives : elles distinguent deux séries de six d'une seule
 * série de douze, et ce n'est pas la même séance.</p>
 *
 * @returns chaîne vide si le bloc ne porte aucun volume
 */
export function formatBlockSets(block: BlockVolume): string {
  const unit = formatBlockVolume(block.distanceM, block.durationS);
  if (!unit) {
    return '';
  }
  const reps = block.reps ?? 1;
  const sets = block.sets ?? 1;
  const repeated = reps > 1 ? `${reps} × ${unit}` : unit;
  return sets > 1 ? `${sets} × (${repeated})` : repeated;
}
