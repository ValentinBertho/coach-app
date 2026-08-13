import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, ErrorHandler, LOCALE_ID, importProvidersFrom } from '@angular/core';
import * as Sentry from '@sentry/angular-ivy';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { environment } from '../environments/environment';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
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
  EyeOff, MapPin, LineChart, Sun,
} from 'lucide-angular';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

registerLocaleData(localeFr);

const ICONS = {
  LayoutDashboard, Users, User, UsersRound, Calendar, CalendarDays, Library, GraduationCap,
  Dumbbell, Building2, RefreshCw, Settings, MessageSquare, House, TrendingUp, Footprints,
  Mountain, MountainSnow, Bike, Moon, Timer, Gauge, Flame, Zap, Wind, Target, FlaskConical, Flag,
  HeartPulse, Thermometer, Palmtree, Pin, Ban, Bell, FileText, Paperclip, Lock, Blocks, BookOpen,
  FolderOpen, Watch, Smartphone, Download, Play, Check, X, AlertTriangle, Cog, Pencil, Star,
  Move, Hand, PartyPopper, Circle, Activity, ChevronRight, ChevronsLeft, ChevronsRight, Copy, Save,
  LayoutGrid, List, PanelLeft, Menu, GripVertical, ChevronDown, ChevronUp, Type,
  LifeBuoy, Search, Lightbulb, Info, CircleHelp, Rocket, ShieldCheck, Eye, Trash2, Plus, Ellipsis, Inbox, ArrowLeft, ArrowRight, ChevronLeft, RotateCcw, Square, GripHorizontal,
  EyeOff, MapPin, LineChart, Sun,
};

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    // Sentry : remonte les erreurs non gérées (actif si DSN configuré).
    ...(environment.sentryDsn
      ? [{ provide: ErrorHandler, useValue: Sentry.createErrorHandler({ showDialog: false }) }]
      : []),
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
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
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
