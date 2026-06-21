/**
 * Environnement de production (build par défaut).
 * En prod, le front et l'API sont servis derrière le même hôte : l'appel relatif `/api`
 * est routé vers le backend (nginx en Docker, rewrite Vercel → Railway en ligne).
 */
export const environment = {
  production: true,
  apiUrl: '/api',
};
