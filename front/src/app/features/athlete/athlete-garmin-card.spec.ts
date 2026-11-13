import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { importProvidersFrom } from '@angular/core';
import { LucideAngularModule, Watch, RefreshCw } from 'lucide-angular';
import { AthleteSyncComponent } from './athlete-sync.component';
import { GarminStatus } from '../../core/models/garmin.model';

/**
 * La carte Garmin de Synchronisation : elle dit l'état de la liaison, et surtout ce qui manque
 * quand l'athlète a refusé l'import d'entraînements au consentement — sans quoi il attendrait
 * des séances sur sa montre qui n'arriveront jamais.
 */
describe('carte Garmin (côté athlète)', () => {
  let http: HttpTestingController;

  const garmin = (over: Partial<GarminStatus> = {}): GarminStatus => ({
    configured: true, connected: true, providerUserId: 'g-1',
    permissions: ['WORKOUT_IMPORT', 'ACTIVITY_EXPORT'],
    canSendWorkouts: true, canImportActivities: true, ...over,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        importProvidersFrom(LucideAngularModule.pick({ Watch, RefreshCw })),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function render(g: GarminStatus): HTMLElement {
    const fixture = TestBed.createComponent(AthleteSyncComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/me/strava')).flush({
      configured: true, connected: false, providerAthleteId: null, lastImportEpoch: null,
      renameOnStrava: false, canRenameOnStrava: false,
    });
    http.expectOne((r) => r.url.endsWith('/me/garmin')).flush(g);
    http.expectOne((r) => r.url.endsWith('/me/activity-exclusions')).flush([]);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('propose la connexion quand le compte n’est pas relié', () => {
    const el = render(garmin({ connected: false, permissions: [], canSendWorkouts: false }));
    expect(el.textContent).toContain('Connecter Garmin');
    expect(el.textContent).not.toContain("Tu n'as pas autorisé l'import d'entraînements");
  });

  it('affiche le compte relié et propose la déconnexion', () => {
    const el = render(garmin());
    expect(el.querySelector('[data-testid="garmin-card"]')?.textContent).toContain('Connecté');
    expect(el.textContent).toContain('Déconnecter Garmin');
  });

  it('prévient quand l’import d’entraînements a été refusé', () => {
    const el = render(garmin({ permissions: ['ACTIVITY_EXPORT'], canSendWorkouts: false }));
    expect(el.textContent).toContain("Tu n'as pas autorisé l'import d'entraînements");
  });

  it('se dit indisponible quand le serveur n’est pas configuré', () => {
    const el = render(garmin({ configured: false, connected: false }));
    expect(el.textContent).toContain('Indisponible sur ce serveur.');
    expect(el.textContent).not.toContain('Connecter Garmin');
  });
});
