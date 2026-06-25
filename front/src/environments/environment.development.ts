/**
 * Environnement de développement (`ng serve`).
 * Appel direct du backend Spring Boot local (context-path /api).
 */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  sentryDsn: '',
  appVersion: '0.1.0-dev',
};
