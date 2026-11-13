/**
 * Autorisation Garmin en attente de finalisation — même mécanisme que pour Strava
 * (voir `strava-pending.storage.ts`) : sur iOS, une PWA installée ouvre le consentement dans le
 * navigateur système, la redirection y retombe sans session, et il faut mettre le code de côté
 * le temps de se reconnecter.
 */

const KEY = 'darilab.garmin.pending';
/** Aligné sur le TTL du state signé (OAuthStateCodec.TTL_SECONDS = 600 s). */
const MAX_AGE_MS = 10 * 60 * 1000;

export interface PendingGarminAuth {
  code: string;
  state: string;
  storedAt: number;
}

export function savePendingGarminAuth(code: string, state: string): void {
  try {
    localStorage.setItem(KEY, JSON.stringify({ code, state, storedAt: Date.now() }));
  } catch {
    // Stockage refusé (navigation privée) : on perd la reprise, pas l'application.
  }
}

export function readPendingGarminAuth(): PendingGarminAuth | null {
  let raw: string | null = null;
  try {
    raw = localStorage.getItem(KEY);
  } catch {
    return null;
  }
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as PendingGarminAuth;
    if (!parsed?.code || !parsed?.state || Date.now() - (parsed.storedAt ?? 0) > MAX_AGE_MS) {
      clearPendingGarminAuth();
      return null;
    }
    return parsed;
  } catch {
    clearPendingGarminAuth();
    return null;
  }
}

export function clearPendingGarminAuth(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // rien à faire de plus.
  }
}
