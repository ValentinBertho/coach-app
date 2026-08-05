/**
 * Autorisation Strava en attente de finalisation.
 *
 * <p>Le retour d'OAuth n'arrive pas toujours dans l'onglet d'où il est parti : sur iOS, une PWA
 * installée ouvre l'autorisation Strava dans le navigateur système, et la redirection retombe
 * dans ce navigateur — pas dans l'application, où la session vit. L'athlète y est déconnecté,
 * le `POST /connect` partait sans jeton, et le parcours s'arrêtait là.</p>
 *
 * <p>On met donc le code d'autorisation de côté le temps de se reconnecter, puis on reprend la
 * finalisation. Le `state` signé côté serveur ne vit que 10 minutes : au-delà, l'entrée est
 * périmée et il faut relancer l'autorisation — inutile de la garder.</p>
 */

const KEY = 'darilab.strava.pending';
/** Aligné sur le TTL du state signé (OAuthStateCodec.TTL_SECONDS = 600 s). */
const MAX_AGE_MS = 10 * 60 * 1000;

export interface PendingStravaAuth {
  code: string;
  state: string;
  /** Horodatage de mise en attente, pour écarter une reprise trop tardive. */
  storedAt: number;
}

export function savePendingStravaAuth(code: string, state: string): void {
  try {
    localStorage.setItem(KEY, JSON.stringify({ code, state, storedAt: Date.now() }));
  } catch {
    // Stockage plein ou refusé (navigation privée) : on perd la reprise, pas l'application.
  }
}

/** L'autorisation en attente, si elle existe et n'a pas expiré. Purge l'entrée périmée. */
export function readPendingStravaAuth(): PendingStravaAuth | null {
  let raw: string | null = null;
  try {
    raw = localStorage.getItem(KEY);
  } catch {
    return null;
  }
  if (!raw) return null;

  try {
    const parsed = JSON.parse(raw) as PendingStravaAuth;
    if (!parsed?.code || !parsed?.state) {
      clearPendingStravaAuth();
      return null;
    }
    if (Date.now() - (parsed.storedAt ?? 0) > MAX_AGE_MS) {
      clearPendingStravaAuth();
      return null;
    }
    return parsed;
  } catch {
    clearPendingStravaAuth();
    return null;
  }
}

export function clearPendingStravaAuth(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // idem : rien à faire de plus.
  }
}
