/**
 * Environnement de développement (`ng serve`).
 * Appel direct du backend Spring Boot local (context-path /api).
 */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  // `ng serve` ne sert pas ngsw-worker.js : l'enregistrer ne donnerait qu'un 404. Pour éprouver
  // les notifications push en local, passer par `npm run start:pwa` (cf. environment.pwa.ts).
  serviceWorker: false,
  sentryDsn: '',
  appVersion: '0.3.0-dev',
};
