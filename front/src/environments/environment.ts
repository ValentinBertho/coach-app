/**
 * Environnement de production (build par défaut).
 * En prod, le front et l'API sont servis derrière le même hôte : l'appel relatif `/api`
 * est routé vers le backend (nginx en Docker, rewrite Vercel → Railway en ligne).
 */
export const environment = {
  production: true,
  apiUrl: '/api',
  // DSN public par conception (il transite dans le bundle navigateur) : il n'autorise
  // que l'envoi d'événements, jamais leur lecture. Projet `darilab-frontend`, région EU.
  sentryDsn: 'https://fd7d2aa386d365ec975fa3e269196e1d@o4511829269807104.ingest.de.sentry.io/4511829289861200',
  appVersion: '0.1.0',
};
