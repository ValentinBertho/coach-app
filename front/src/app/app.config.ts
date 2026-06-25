import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, LOCALE_ID, importProvidersFrom, isDevMode } from '@angular/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideServiceWorker } from '@angular/service-worker';
import {
  LucideAngularModule,
  LayoutDashboard, Users, User, UsersRound, Calendar, CalendarDays, Library, GraduationCap,
  Dumbbell, Building2, RefreshCw, Settings, MessageSquare, House, TrendingUp, Footprints,
  Mountain, MountainSnow, Bike, Moon, Timer, Flame, Zap, Wind, Target, FlaskConical, Flag,
  HeartPulse, Thermometer, Palmtree, Pin, Ban, Bell, FileText, Paperclip, Lock, Blocks, BookOpen,
  FolderOpen, Watch, Smartphone, Download, Play, Check, X, AlertTriangle, Cog, Pencil, Star,
  Move, Hand, PartyPopper, Circle, Activity, ChevronRight,
} from 'lucide-angular';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

registerLocaleData(localeFr);

const ICONS = {
  LayoutDashboard, Users, User, UsersRound, Calendar, CalendarDays, Library, GraduationCap,
  Dumbbell, Building2, RefreshCw, Settings, MessageSquare, House, TrendingUp, Footprints,
  Mountain, MountainSnow, Bike, Moon, Timer, Flame, Zap, Wind, Target, FlaskConical, Flag,
  HeartPulse, Thermometer, Palmtree, Pin, Ban, Bell, FileText, Paperclip, Lock, Blocks, BookOpen,
  FolderOpen, Watch, Smartphone, Download, Play, Check, X, AlertTriangle, Cog, Pencil, Star,
  Move, Hand, PartyPopper, Circle, Activity, ChevronRight,
};

export const appConfig: ApplicationConfig = {
  providers: [
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    importProvidersFrom(LucideAngularModule.pick(ICONS)),
    provideAnimationsAsync(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideServiceWorker('ngsw-worker.js', {
      enabled: !isDevMode(),
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
};
