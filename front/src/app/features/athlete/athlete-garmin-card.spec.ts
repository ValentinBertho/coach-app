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
    canSendWorkouts: true, canImportActivities: true, workoutsOnGarmin: 0, workoutsInError: 0, ...over,
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
    return renderFixture(g).nativeElement as HTMLElement;
  }

  function renderFixture(g: GarminStatus) {
    const fixture = TestBed.createComponent(AthleteSyncComponent);
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/me/strava')).flush({
      configured: true, connected: false, providerAthleteId: null, lastImportEpoch: null,
      renameOnStrava: false, canRenameOnStrava: false,
    });
    http.expectOne((r) => r.url.endsWith('/me/garmin')).flush(g);
    http.expectOne((r) => r.url.endsWith('/me/coros')).flush({
      configured: false, connected: false, providerUserId: null, region: null,
      workoutsOnCoros: 0, workoutsInError: 0,
    });
    http.expectOne((r) => r.url.endsWith('/me/activity-exclusions')).flush([]);
    fixture.detectChanges();
    return fixture;
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

  it('dit combien de séances sont sur la montre', () => {
    const el = render(garmin({ workoutsOnGarmin: 3 }));
    expect(el.querySelector('[data-testid="garmin-summary"]')?.textContent)
      .toContain('3 séances à venir');
  });

  it('signale les envois en échec', () => {
    const el = render(garmin({ workoutsOnGarmin: 2, workoutsInError: 1 }));
    expect(el.querySelector('[data-testid="garmin-errors"]')?.textContent)
      .toContain('pas pu');
  });

  it('« Envoyer mes séances maintenant » appelle le serveur et affiche son bilan', () => {
    const fixture = renderFixture(garmin());
    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((b) => b.textContent?.includes('Envoyer mes séances maintenant'));
    expect(button).withContext('bouton d’envoi').toBeTruthy();
    button!.click();
    const req = http.expectOne((r) => r.url.endsWith('/me/garmin/sync-workouts'));
    expect(req.request.method).toBe('POST');
    req.flush(garmin({ workoutsOnGarmin: 5 }));
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('5 séances à venir');
  });

  it('ne propose pas l’envoi sans la permission d’import d’entraînements', () => {
    const el = render(garmin({ permissions: ['ACTIVITY_EXPORT'], canSendWorkouts: false }));
    expect(el.textContent).not.toContain('Envoyer mes séances maintenant');
  });

  it('dit que les sorties arrivent toutes seules quand le partage est accordé', () => {
    const el = render(garmin());
    expect(el.querySelector('[data-testid="garmin-activities"]')?.textContent)
      .toContain('arrivent toutes seules');
  });

  it('prévient quand le partage des activités a été refusé', () => {
    const el = render(garmin({ permissions: ['WORKOUT_IMPORT'], canImportActivities: false }));
    expect(el.querySelector('[data-testid="garmin-activities"]')?.textContent)
      .toContain("Tu n'as pas autorisé le partage de tes activités");
  });
});
