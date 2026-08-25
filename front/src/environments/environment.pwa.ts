/**
 * Environnement « PWA locale » (`npm run start:pwa`).
 *
 * <p>Le développement courant se fait avec `ng serve`, qui ne sert pas le service worker : les
 * notifications push y sont donc <b>structurellement</b> hors de portée — le navigateur n'a
 * personne pour recevoir l'événement. Cet environnement construit l'application <i>avec</i> son
 * service worker tout en parlant à l'API locale, pour pouvoir éprouver la chaîne complète —
 * abonnement, envoi, affichage, clic — sans déployer.</p>
 */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  serviceWorker: true,
  sentryDsn: '',
  appVersion: '0.3.0-pwa',
};
