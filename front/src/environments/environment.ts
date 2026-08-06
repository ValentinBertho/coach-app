/**
 * Environnement de production (build par défaut).
 * En prod, le front et l'API sont servis derrière le même hôte : l'appel relatif `/api`
 * est routé vers le backend (nginx en Docker, rewrite Vercel → Railway en ligne).
 */
export const environment = {
  production: true,
  apiUrl: '/api',
  /**
   * Service worker enregistré. Il porte la mise en cache hors ligne ET la réception des
   * notifications push : sans lui, aucun push n'arrive, quelles que soient les clés du serveur.
   */
  serviceWorker: true,
  // DSN public par conception (il transite dans le bundle navigateur) : il n'autorise
  // que l'envoi d'événements, jamais leur lecture. Projet `darilab-frontend`, région EU.
  sentryDsn: 'https://fd7d2aa386d365ec975fa3e269196e1d@o4511829269807104.ingest.de.sentry.io/4511829289861200',
  // Tenue à jour avec back/pom.xml et front/package.json à chaque déploiement notable.
  // Sentry s'en sert comme `release` : figée, elle rend indécidable un « ça marchait hier »,
  // puisque tous les événements portent alors la même version.
  appVersion: '0.2.0',
};
