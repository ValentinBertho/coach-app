import { registerLocaleData, isPlatformBrowser } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { PLATFORM_ID, ApplicationConfig, ErrorHandler, LOCALE_ID, importProvidersFrom, inject } from '@angular/core';
import * as Sentry from '@sentry/angular-ivy';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { environment } from '../environments/environment';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideAthleteContextReuse } from './core/routing/athlete-context-reuse.strategy';
import { provideServiceWorker } from '@angular/service-worker';
import {
  LucideAngularModule,
  LayoutDashboard, Users, User, UsersRound, Calendar, CalendarDays, Library, GraduationCap,
  Dumbbell, Building2, RefreshCw, Settings, MessageSquare, House, TrendingUp, Footprints,
  Mountain, MountainSnow, Bike, Moon, Timer, Gauge, Flame, Zap, Wind, Target, FlaskConical, Flag,
  HeartPulse, Thermometer, Palmtree, Pin, Ban, Bell, FileText, Paperclip, Lock, Blocks, BookOpen,
  FolderOpen, Watch, Smartphone, Download, Play, Check, X, AlertTriangle, Cog, Pencil, Star,
  Move, Hand, PartyPopper, Circle, Activity, ChevronRight, ChevronsLeft, ChevronsRight, Copy, Save,
  LayoutGrid, List, PanelLeft, Menu, GripVertical, ChevronDown, ChevronUp, Type,
  LifeBuoy, Search, Lightbulb, Info, CircleHelp, Rocket, ShieldCheck, Eye, Trash2, Plus, Ellipsis, Inbox, ArrowLeft, ArrowRight, ChevronLeft, RotateCcw, Square, GripHorizontal,
  EyeOff, MapPin, LineChart, Sun, WifiOff, Mail, DoorOpen,
} from 'lucide-angular';

import { routes } from './app.routes';
import { StaleChunkErrorHandler } from './core/errors/stale-chunk.error-handler';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { prerenderBaseUrlInterceptor } from './core/interceptors/prerender-base-url.interceptor';

registerLocaleData(localeFr);

/**
 * Jeu d'icônes de l'application. Exporté pour que les tests de composants puissent le fournir
 * d'un bloc : Lucide lève dès qu'une icône rendue n'a pas de fournisseur, et un test qui doit
 * énumérer à la main les icônes de son gabarit casse au premier ajout d'une icône — pour une
 * raison qui n'a rien à voir avec ce qu'il vérifie.
 */
export const ICONS = {
  LayoutDashboard, Users, User, UsersRound, Calendar, CalendarDays, Library, GraduationCap,
  Dumbbell, Building2, RefreshCw, Settings, MessageSquare, House, TrendingUp, Footprints,
  Mountain, MountainSnow, Bike, Moon, Timer, Gauge, Flame, Zap, Wind, Target, FlaskConical, Flag,
  HeartPulse, Thermometer, Palmtree, Pin, Ban, Bell, FileText, Paperclip, Lock, Blocks, BookOpen,
  FolderOpen, Watch, Smartphone, Download, Play, Check, X, AlertTriangle, Cog, Pencil, Star,
  Move, Hand, PartyPopper, Circle, Activity, ChevronRight, ChevronsLeft, ChevronsRight, Copy, Save,
  LayoutGrid, List, PanelLeft, Menu, GripVertical, ChevronDown, ChevronUp, Type,
  LifeBuoy, Search, Lightbulb, Info, CircleHelp, Rocket, ShieldCheck, Eye, Trash2, Plus, Ellipsis, Inbox, ArrowLeft, ArrowRight, ChevronLeft, RotateCcw, Square, GripHorizontal,
  EyeOff, MapPin, LineChart, Sun, WifiOff, Mail, DoorOpen,
};

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    // Un seul gestionnaire d'erreurs, en deux temps.
    //
    // Devant : l'écran chargé en différé dont le fichier a disparu du serveur — le cas d'un
    // onglet resté ouvert pendant un déploiement. Angular annule alors la navigation SANS RIEN
    // AFFICHER : le coach clique sur une séance, et rien ne se passe. On recharge à sa place
    // (cf. StaleChunkErrorHandler).
    //
    // Derrière : Sentry, qui remonte tout le reste (actif si un DSN est configuré). Le
    // chaînage est explicite plutôt que par `skipSelf` : les deux vivent dans le même
    // injecteur, et `skipSelf` sauterait justement celui qu'on veut garder en aval.
    {
      provide: ErrorHandler,
      useFactory: () => new StaleChunkErrorHandler(
        // Sentry seulement dans un navigateur. Son extracteur d'erreurs lit `ErrorEvent`, un
        // global que Node n'a pas : au pré-rendu des fiches coachs, il levait donc en essayant de
        // rapporter une autre erreur — et masquait celle-ci derrière la sienne. Il n'y a de toute
        // façon rien à remonter depuis une compilation : ce qui casse doit apparaître dans la
        // sortie du build, pas dans le journal des incidents des utilisateurs réels.
        environment.sentryDsn && isPlatformBrowser(inject(PLATFORM_ID))
          ? Sentry.createErrorHandler({ showDialog: false })
          : new ErrorHandler(),
      ),
    },
    importProvidersFrom(LucideAngularModule.pick(ICONS)),
    provideAnimationsAsync(),
    // « always » : les routes enfants héritent des paramètres du parent, donc les
    // sections d'un athlète reçoivent `athleteId` posé par la coquille sans que
    // chacune ait à le redéclarer dans son propre chemin.
    provideRouter(
      routes,
      withComponentInputBinding(),
      withRouterConfig({ paramsInheritanceStrategy: 'always' }),
    ),
    // ... et comme elles n'ont que cet héritage pour savoir de qui elles parlent, elles sont
    // recréées quand il change : sans cela, changer d'athlète depuis le bandeau renommait
    // l'en-tête et laissait dessous le programme du précédent.
    provideAthleteContextReuse(),
    // `prerenderBaseUrlInterceptor` en tête : il rend absolues les adresses relatives pendant le
    // pré-rendu, et doit donc s'appliquer avant que quiconque parte en requête. Inerte dans le
    // navigateur, où son jeton d'origine n'est jamais fourni.
    provideHttpClient(withInterceptors([
      prerenderBaseUrlInterceptor, authInterceptor, errorInterceptor,
    ])),
    // Enregistrement piloté par l'environnement, et non par `isDevMode()` : c'est la présence du
    // fichier `ngsw-worker.js` dans le build qui décide, pas le mode d'exécution. `ng serve` ne le
    // produit pas (drapeau à faux), un build « pwa » ou de production oui — et sans service worker
    // enregistré, aucune notification push ne peut arriver, quelles que soient les clés du serveur.
    provideServiceWorker('ngsw-worker.js', {
      enabled: environment.serviceWorker,
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
