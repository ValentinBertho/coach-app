/**
 * Environnement « stack Docker locale » (`docker compose up`).
 *
 * <p>Identique à la production sur ce qui compte pour éprouver l'application — build optimisé,
 * service worker actif, appel relatif <code>/api</code> proxifié par nginx vers le backend du
 * réseau compose — à une exception près : <b>aucun DSN Sentry</b>.</p>
 *
 * <p>Sans cette exception, chaque poste de développement alimentait le projet Sentry de
 * production, sous la release publiée (0.2.0) et avec <code>environment: production</code> :
 * une erreur locale y devenait indiscernable d'une erreur vue par un utilisateur, et la
 * release cessait de vouloir dire quelque chose.</p>
 */
export const environment = {
  production: true,
  apiUrl: '/api',
  serviceWorker: true,
  sentryDsn: '',
  appVersion: '0.2.0-docker',
};
