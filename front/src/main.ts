import { bootstrapApplication } from '@angular/platform-browser';
import * as Sentry from '@sentry/angular-ivy';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { environment } from './environments/environment';

// Sentry : actif uniquement si un DSN est fourni (no-op sinon).
//
// SANS suivi de performance, et c'est un arbitrage mesuré. Le traçage navigateur pèse ~42 ko
// bruts (~12 ko transférés) dans le paquet initial — payés par chaque athlète, sur téléphone, au
// premier chargement — pour des relevés de temps de navigation que personne n'a encore ouverts.
// La CAPTURE D'ERREURS, elle, reste entière : c'est la seule fenêtre sur ce qui casse en
// production, et elle ne se négocie pas.
//
// Pour le réactiver : `tracesSampleRate: 0.1` et
// `integrations: [Sentry.browserTracingIntegration()]`. Le jour où les temps de chargement
// deviendront une question, ce sera une ligne — et le budget de paquet en rendra le coût visible.
if (environment.sentryDsn) {
  Sentry.init({
    dsn: environment.sentryDsn,
    environment: environment.production ? 'production' : 'development',
    release: environment.appVersion,
  });
}

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
