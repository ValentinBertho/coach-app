/** Réponse de l'endpoint de santé GET /api/public/ping. */
export interface Ping {
  status: string;
  version: string;
}
