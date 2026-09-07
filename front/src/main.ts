import { bootstrapApplication } from '@angular/platform-browser';
import * as Sentry from '@sentry/angular-ivy';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { buildStamp } from './app/core/build-info';
import { environment } from './environments/environment';

// Sentry : actif uniquement si un DSN est fourni (no-op sinon).
if (environment.sentryDsn) {
  Sentry.init({
    dsn: environment.sentryDsn,
    environment: environment.production ? 'production' : 'development',
    // « <version>+<commit> », gravé au build : c'est ce qui rattache une erreur à un code précis.
    // La version seule ne distinguait pas deux déploiements du même numéro.
    release: buildStamp(),
    tracesSampleRate: 0.1,
    integrations: [Sentry.browserTracingIntegration()],
  });
}

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
