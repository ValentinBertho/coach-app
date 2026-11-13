/**
 * Autorisation COROS en attente de finalisation — même mécanisme que pour Strava
 * (voir `strava-pending.storage.ts`) : sur iOS, une PWA installée ouvre le consentement dans le
 * navigateur système, la redirection y retombe sans session, et il faut mettre le code de côté
 * le temps de se reconnecter.
 */

const KEY = 'darilab.coros.pending';
/** Aligné sur le TTL du state signé (OAuthStateCodec.TTL_SECONDS = 600 s). */
const MAX_AGE_MS = 10 * 60 * 1000;

export interface PendingCorosAuth {
  code: string;
  state: string;
  storedAt: number;
}

export function savePendingCorosAuth(code: string, state: string): void {
  try {
    localStorage.setItem(KEY, JSON.stringify({ code, state, storedAt: Date.now() }));
  } catch {
    // Stockage refusé (navigation privée) : on perd la reprise, pas l'application.
  }
}

export function readPendingCorosAuth(): PendingCorosAuth | null {
  let raw: string | null = null;
  try {
    raw = localStorage.getItem(KEY);
  } catch {
    return null;
  }
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as PendingCorosAuth;
    if (!parsed?.code || !parsed?.state || Date.now() - (parsed.storedAt ?? 0) > MAX_AGE_MS) {
      clearPendingCorosAuth();
      return null;
    }
    return parsed;
  } catch {
    clearPendingCorosAuth();
    return null;
  }
}

export function clearPendingCorosAuth(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // rien à faire de plus.
  }
}
