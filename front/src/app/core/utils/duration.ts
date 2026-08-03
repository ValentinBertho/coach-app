/**
 * Saisie et affichage d'un chrono (objectif de course, temps de référence).
 *
 * <p>Une même convention partout : <b>hh:mm:ss</b>, et <b>mm:ss</b> pour un chrono de moins d'une
 * heure — celle des chronomètres et des tableaux de résultats. Sans elle, « 42:30 » valait un
 * 10 km en 42 min 30 côté coach et 42 <i>heures</i> 30 côté athlète, sur le même objectif.</p>
 */

/** « hh:mm:ss », « mm:ss » ou un nombre de minutes → secondes ; `null` si vide ou illisible. */
export function parseDuration(raw: string | null | undefined): number | null {
  const text = (raw ?? '').trim();
  if (!text) return null;
  const parts = text.split(':').map((p) => Number(p.trim()));
  // Un champ vide entre deux « : » donne 0 via Number(''), ce qui laisserait passer « 3::30 ».
  if (text.split(':').some((p) => p.trim() === '') || parts.some((n) => !Number.isFinite(n) || n < 0)) {
    return null;
  }
  // Au-delà des heures, les rangs sexagésimaux doivent rester sous 60 : « 3:75 » est une faute de
  // frappe, pas 4 h 15 — mieux vaut la signaler que l'interpréter.
  if (parts.slice(1).some((n) => n >= 60)) return null;
  if (parts.length === 1) return Math.round(parts[0] * 60);
  if (parts.length === 2) return Math.round(parts[0] * 60 + parts[1]);
  if (parts.length === 3) return Math.round(parts[0] * 3600 + parts[1] * 60 + parts[2]);
  return null;
}

/** Secondes → « h:mm:ss » (ou « m:ss » sous l'heure). Réinjectable tel quel dans le champ. */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return '';
  const total = Math.round(seconds);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const ss = s.toString().padStart(2, '0');
  return h > 0 ? `${h}:${m.toString().padStart(2, '0')}:${ss}` : `${m}:${ss}`;
}
